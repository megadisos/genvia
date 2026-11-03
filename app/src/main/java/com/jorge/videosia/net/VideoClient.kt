package com.jorge.videosia.net

import com.jorge.videosia.data.DurationStyle
import com.jorge.videosia.data.Provider
import com.jorge.videosia.data.VideoModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

data class GenerationRequest(
    val model: VideoModel,
    val prompt: String,
    val seconds: Int,
    val aspectRatio: String, // "9:16", "16:9", "1:1"
)

/**
 * Cliente para la "cola" de fal.ai. Hugging Face enruta sus modelos de video
 * por fal.ai con el mismo protocolo, así que ambos proveedores comparten código:
 * enviar -> consultar estado -> pedir resultado -> descargar el MP4.
 */
class VideoClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val json = "application/json".toMediaType()

    suspend fun generate(
        req: GenerationRequest,
        apiKey: String,
        outFile: File,
        onStatus: (String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw ApiException("Falta la clave de ${req.model.provider.label}. Agrégala en Ajustes.")
        val auth = when (req.model.provider) {
            Provider.FAL -> "Key $apiKey"
            Provider.HUGGINGFACE -> "Bearer $apiKey"
        }

        val submitUrl = when (req.model.provider) {
            Provider.FAL -> "https://queue.fal.run/${req.model.endpoint}"
            Provider.HUGGINGFACE -> {
                onStatus("Buscando el modelo en Hugging Face…")
                val falId = resolveHfProviderId(req.model.endpoint, auth)
                "https://router.huggingface.co/fal-ai/$falId?_subdomain=queue"
            }
        }

        onStatus("Enviando a ${req.model.name}…")
        var submit = post(submitUrl, auth, buildBody(req, full = true))
        if (submit.code == 422) {
            // El modelo no aceptó algún parámetro opcional: reintenta solo con el texto.
            submit = post(submitUrl, auth, buildBody(req, full = false))
        }
        if (!submit.ok) throw ApiException(friendlyError(submit.code, submit.body))
        val sub = JSONObject(submit.body)

        val (statusUrl, resultUrl) = pollingUrls(req.model.provider, submitUrl, sub)

        var waited = 0
        while (true) {
            val st = get(statusUrl, auth)
            if (!st.ok) throw ApiException(friendlyError(st.code, st.body))
            val obj = JSONObject(st.body)
            when (val status = obj.optString("status")) {
                "COMPLETED" -> break
                "IN_QUEUE" -> onStatus("En cola… posición ${obj.optInt("queue_position", 0) + 1} (${waited}s)")
                "IN_PROGRESS" -> onStatus("Generando video… (${waited}s)")
                else -> if (obj.has("error")) throw ApiException(obj.optString("error")) else onStatus("$status (${waited}s)")
            }
            delay(3000); waited += 3
            if (waited > 20 * 60) throw ApiException("Tardó más de 20 minutos. Inténtalo de nuevo.")
        }

        val res = get(resultUrl, auth)
        if (!res.ok) throw ApiException(friendlyError(res.code, res.body))
        val videoUrl = findVideoUrl(JSONObject(res.body))
            ?: throw ApiException("La respuesta no trae video: ${res.body.take(300)}")

        onStatus("Descargando video…")
        http.newCall(Request.Builder().url(videoUrl).build()).execute().use { r ->
            if (!r.isSuccessful) throw ApiException("No se pudo descargar el video (${r.code}).")
            outFile.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
        }
        outFile
    }

    private fun buildBody(req: GenerationRequest, full: Boolean): String {
        val m = req.model
        val o = JSONObject().put("prompt", req.prompt)
        if (full) {
            if (m.sendsAspectRatio) o.put("aspect_ratio", req.aspectRatio)
            when (m.durationStyle) {
                DurationStyle.NONE -> Unit
                DurationStyle.STRING -> o.put("duration", req.seconds.toString())
                DurationStyle.SECONDS_SUFFIX -> o.put("duration", "${req.seconds}s")
                DurationStyle.INT -> o.put("duration", req.seconds)
            }
            if (m.audio) o.put("generate_audio", true)
        }
        return o.toString()
    }

    private fun pollingUrls(p: Provider, submitUrl: String, sub: JSONObject): Pair<String, String> {
        val responseUrl = sub.optString("response_url")
        if (responseUrl.isBlank()) throw ApiException("Respuesta inesperada: ${sub.toString().take(300)}")
        return when (p) {
            Provider.FAL -> sub.optString("status_url", "$responseUrl/status") to responseUrl
            Provider.HUGGINGFACE -> {
                // Igual que el cliente oficial huggingface_hub: se consulta a través del router.
                val path = URI(responseUrl).path
                val query = URI(submitUrl).rawQuery?.let { "?$it" } ?: ""
                val base = "https://router.huggingface.co/fal-ai"
                "$base$path/status$query" to "$base$path$query"
            }
        }
    }

    /** Pregunta a Hugging Face qué id usa fal.ai para este modelo. */
    private fun resolveHfProviderId(hfModel: String, auth: String): String {
        val r = get("https://huggingface.co/api/models/$hfModel?expand[]=inferenceProviderMapping", auth)
        if (!r.ok) throw ApiException(friendlyError(r.code, r.body))
        val mapping = JSONObject(r.body).opt("inferenceProviderMapping")
        val fal: JSONObject? = when (mapping) {
            is JSONObject -> mapping.optJSONObject("fal-ai")
            is JSONArray -> (0 until mapping.length()).map { mapping.getJSONObject(it) }
                .firstOrNull { it.optString("provider") == "fal-ai" }
            else -> null
        }
        return fal?.optString("providerId")?.takeIf { it.isNotBlank() }
            ?: throw ApiException("Hugging Face ya no ofrece $hfModel por fal.ai. Prueba otro modelo.")
    }

    private fun findVideoUrl(o: JSONObject): String? {
        o.optJSONObject("video")?.optString("url")?.takeIf { it.isNotBlank() }?.let { return it }
        o.optJSONArray("videos")?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }?.let { return it }
        o.optString("video_url").takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun friendlyError(code: Int, body: String): String = when (code) {
        401, 403 -> "Clave inválida o sin permisos ($code)."
        402 -> "Sin saldo o créditos agotados ($code). Recarga en el proveedor."
        404 -> "El modelo no existe o cambió de nombre ($code)."
        422 -> "El modelo rechazó los parámetros: ${body.take(300)}"
        429 -> "Demasiadas solicitudes o límite gratis alcanzado ($code). Espera un poco."
        else -> "Error $code: ${body.take(300)}"
    }

    private data class Resp(val code: Int, val body: String) { val ok get() = code in 200..299 }

    private fun post(url: String, auth: String, body: String) = exec(
        Request.Builder().url(url).header("Authorization", auth).post(body.toRequestBody(json)).build()
    )

    private fun get(url: String, auth: String) = exec(
        Request.Builder().url(url).header("Authorization", auth).get().build()
    )

    private fun exec(r: Request): Resp = http.newCall(r).execute().use { Resp(it.code, it.body?.string() ?: "") }
}
