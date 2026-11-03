package com.jorge.videosia.net

import com.jorge.videosia.data.DurationStyle
import com.jorge.videosia.data.Provider
import com.jorge.videosia.data.VideoModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.util.Base64
import java.io.File
import java.util.concurrent.TimeUnit

class ApiException(message: String) : Exception(message)

private const val CONTENT_POLICY =
    "El filtro de contenido rechazó el prompt o la imagen. Suele pasar con violencia, armas o fotos de personas " +
        "reales en escenas de pelea. Suaviza el prompt (por ejemplo, juego o aventura sin golpes) o prueba otro modelo. " +
        "Los videos rechazados normalmente no se cobran."

/** Saldo en USD (1 Pollen ≈ 1 USD). */
data class BalanceInfo(
    val total: Double? = null,
    /** Saldo comprado: sirve para todos los modelos. */
    val paid: Double? = null,
    /** Pollen gratis de misiones: solo para modelos que no son "solo de pago". */
    val quest: Double? = null,
    val questLocked: Boolean = false,
    /** La clave tiene presupuesto propio y solo muestra lo que le queda. */
    val keyBudget: Boolean = false,
    /** Escrito a mano en Ajustes. */
    val manual: Boolean = false,
    val error: String? = null,
)

data class GenerationRequest(
    val model: VideoModel,
    val prompt: String,
    val seconds: Int,
    val aspectRatio: String,
    /** Imagen de referencia en JPEG (primer cuadro del video), opcional. */
    val imageJpeg: ByteArray? = null,
)

class VideoClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()
    private val json = "application/json".toMediaType()

    suspend fun generate(req: GenerationRequest, apiKey: String, outFile: File, onStatus: (String) -> Unit): File =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) throw ApiException("Falta la clave de ${req.model.provider.label}. Agrégala en Ajustes.")
            when (req.model.provider) {
                Provider.POLLINATIONS -> pollinations(req, apiKey, outFile, onStatus)
                Provider.FAL -> fal(req, apiKey, outFile, onStatus)
            }
            outFile
        }

    // ---------------- Saldo ----------------

    /** Saldo en USD (total), o null si la clave no permite leerlo. */
    suspend fun balanceUsd(p: Provider, apiKey: String): Double? = balanceInfo(p, apiKey).total

    /**
     * Saldo detallado. En Pollinations: pagado, de misiones (gratis) y total, como en su página.
     * Si no se puede leer, total es null y error explica por qué.
     */
    suspend fun balanceInfo(p: Provider, apiKey: String): BalanceInfo = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext BalanceInfo(error = "Falta la clave")
        runCatching {
            when (p) {
                Provider.POLLINATIONS -> {
                    val r = get("https://gen.pollinations.ai/account/balance", "Bearer $apiKey")
                    if (!r.ok) {
                        BalanceInfo(error = if (r.code == 403) "La clave no tiene el permiso account:usage" else "Error ${r.code}")
                    } else {
                        val o = JSONObject(r.body)
                        val acc = o.optJSONObject("accountBalance")
                        if (acc != null) {
                            BalanceInfo(
                                total = acc.optDouble("total").orNull() ?: o.optDouble("balance").orNull(),
                                paid = acc.optDouble("paid").orNull(),
                                quest = acc.optDouble("tier").orNull(),
                                questLocked = acc.optBoolean("questPollenRestricted", false),
                            )
                        } else {
                            // Clave con presupuesto: solo informa lo que le queda a esa clave.
                            BalanceInfo(total = o.optDouble("balance").orNull(), keyBudget = true)
                        }
                    }
                }
                Provider.FAL -> {
                    val r = get("https://api.fal.ai/v1/account/billing?expand=credits", "Key $apiKey")
                    if (!r.ok) BalanceInfo(error = if (r.code == 403) "La clave no tiene permiso de facturación" else "Error ${r.code}")
                    else {
                        val v = JSONObject(r.body).optJSONObject("credits")?.optDouble("current_balance")?.orNull()
                        BalanceInfo(total = v, paid = v, quest = 0.0)
                    }
                }
            }
        }.getOrElse { BalanceInfo(error = "Sin conexión") }
    }

    private fun Double.orNull(): Double? = if (isNaN()) null else this

    /** Pesos colombianos por dólar, de una API pública gratuita. */
    suspend fun copPerUsd(): Double? = withContext(Dispatchers.IO) {
        runCatching {
            val r = exec(Request.Builder().url("https://open.er-api.com/v6/latest/USD").build())
            if (!r.ok) null else JSONObject(r.body).getJSONObject("rates").getDouble("COP")
        }.getOrNull()
    }

    // ---------------- Pollinations ----------------

    private suspend fun pollinations(req: GenerationRequest, key: String, out: File, onStatus: (String) -> Unit) {
        val auth = "Bearer $key"
        val imageUrl = req.imageJpeg?.let {
            onStatus("Subiendo imagen de referencia…")
            uploadPollinations(it, auth)
        }
        val url = "https://gen.pollinations.ai/video/".toHttpUrl().newBuilder()
            .addPathSegment(req.prompt)
            .addQueryParameter("model", req.model.endpoint)
            .addQueryParameter("duration", req.seconds.toString())
            .addQueryParameter("aspectRatio", req.aspectRatio)
            .apply { if (req.model.audio) addQueryParameter("audio", "true") }
            .addQueryParameter("seed", (System.currentTimeMillis() % 1_000_000).toString())
            .apply { if (imageUrl != null) addQueryParameter("image", imageUrl) }
            .build()

        onStatus("Enviando a ${req.model.name}…")
        val call = http.newCall(
            Request.Builder().url(url).header("Authorization", auth).header("Prefer", "respond-async").get().build()
        ).execute()
        call.use { r ->
            if (r.code == 200 && r.header("Content-Type")?.startsWith("video") == true) {
                // Resultado inmediato (ya estaba en caché).
                out.outputStream().use { o -> r.body!!.byteStream().copyTo(o) }
                return
            }
            val body = r.body?.string() ?: ""
            if (r.code !in 200..299) throw ApiException(friendlyError(r.code, body))
            val id = JSONObject(body).optString("id")
            if (id.isBlank()) throw ApiException("Respuesta inesperada: ${body.take(300)}")

            var waited = 0
            while (true) {
                delay(5000); waited += 5
                val st = get("https://gen.pollinations.ai/v1/jobs/$id", auth)
                if (!st.ok) throw ApiException(friendlyError(st.code, st.body))
                val o = JSONObject(st.body)
                when (o.optString("status")) {
                    "succeeded" -> break
                    "failed", "error", "cancelled" -> {
                        val msg = o.optJSONObject("error")?.optString("message") ?: o.optString("error", st.body)
                        throw ApiException(if (isContentPolicy(msg)) CONTENT_POLICY else "Falló: ${msg.take(300)}")
                    }
                    else -> onStatus("Generando video… (${waited}s)")
                }
                if (waited > 20 * 60) throw ApiException("Tardó más de 20 minutos. Inténtalo de nuevo.")
            }
            onStatus("Descargando video…")
            download("https://gen.pollinations.ai/v1/jobs/$id/result", auth, out)
        }
    }

    // ---------------- fal.ai (cola) ----------------

    private suspend fun fal(req: GenerationRequest, key: String, out: File, onStatus: (String) -> Unit) {
        val auth = "Key $key"
        val endpoint = if (req.imageJpeg != null) {
            req.model.imageEndpoint ?: throw ApiException("${req.model.name} en fal.ai no acepta imagen de referencia.")
        } else req.model.endpoint
        val submitUrl = "https://queue.fal.run/$endpoint"
        onStatus("Enviando a ${req.model.name}…")
        var submit = post(submitUrl, auth, falBody(req, full = true))
        if (submit.code == 422) submit = post(submitUrl, auth, falBody(req, full = false))
        if (!submit.ok) throw ApiException(friendlyError(submit.code, submit.body))
        val sub = JSONObject(submit.body)
        val responseUrl = sub.optString("response_url")
        if (responseUrl.isBlank()) throw ApiException("Respuesta inesperada: ${submit.body.take(300)}")
        val statusUrl = sub.optString("status_url", "$responseUrl/status")

        var waited = 0
        while (true) {
            val st = get(statusUrl, auth)
            if (!st.ok) throw ApiException(friendlyError(st.code, st.body))
            val o = JSONObject(st.body)
            when (val s = o.optString("status")) {
                "COMPLETED" -> break
                "IN_QUEUE" -> onStatus("En cola… posición ${o.optInt("queue_position", 0) + 1} (${waited}s)")
                "IN_PROGRESS" -> onStatus("Generando video… (${waited}s)")
                else -> if (o.has("error")) throw ApiException(o.optString("error")) else onStatus("$s (${waited}s)")
            }
            delay(3000); waited += 3
            if (waited > 20 * 60) throw ApiException("Tardó más de 20 minutos. Inténtalo de nuevo.")
        }
        val res = get(responseUrl, auth)
        if (!res.ok) throw ApiException(friendlyError(res.code, res.body))
        val r = JSONObject(res.body)
        val videoUrl = r.optJSONObject("video")?.optString("url")?.takeIf { it.isNotBlank() }
            ?: r.optString("video_url").takeIf { it.isNotBlank() }
            ?: throw ApiException("La respuesta no trae video: ${res.body.take(300)}")
        onStatus("Descargando video…")
        download(videoUrl, null, out)
    }

    private fun falBody(req: GenerationRequest, full: Boolean): String {
        val o = JSONObject().put("prompt", req.prompt)
        req.imageJpeg?.let {
            o.put("image_url", "data:image/jpeg;base64," + Base64.encodeToString(it, Base64.NO_WRAP))
        }
        if (full) {
            o.put("aspect_ratio", req.aspectRatio)
            when (req.model.durationStyle) {
                DurationStyle.NONE -> Unit
                DurationStyle.STRING -> o.put("duration", req.seconds.toString())
                DurationStyle.SECONDS_SUFFIX -> o.put("duration", "${req.seconds}s")
                DurationStyle.INT -> o.put("duration", req.seconds)
            }
            if (req.model.audio) o.put("generate_audio", true)
        }
        return o.toString()
    }

    /** Sube la imagen a media.pollinations.ai y devuelve su URL pública. */
    private fun uploadPollinations(jpeg: ByteArray, auth: String): String {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "referencia.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val r = exec(Request.Builder().url("https://media.pollinations.ai/upload").header("Authorization", auth).post(body).build())
        if (!r.ok) throw ApiException("No se pudo subir la imagen: " + friendlyError(r.code, r.body))
        val o = runCatching { JSONObject(r.body) }.getOrNull()
        val url = o?.optString("url")?.takeIf { it.startsWith("http") }
            ?: o?.optString("id")?.takeIf { it.isNotBlank() }?.let { "https://media.pollinations.ai/$it" }
            ?: Regex("https://media\\.pollinations\\.ai/[A-Za-z0-9._-]+").find(r.body)?.value
        return url ?: throw ApiException("Respuesta inesperada al subir la imagen: ${r.body.take(200)}")
    }

    // ---------------- HTTP ----------------

    private fun download(url: String, auth: String?, out: File) {
        val b = Request.Builder().url(url)
        if (auth != null) b.header("Authorization", auth)
        http.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw ApiException("No se pudo descargar el video (${r.code}).")
            out.outputStream().use { o -> r.body!!.byteStream().copyTo(o) }
        }
    }

    private fun isContentPolicy(text: String) =
        text.contains("content_policy", true) || text.contains("flagged", true) || text.contains("moderation", true)

    private fun friendlyError(code: Int, body: String): String = if (isContentPolicy(body)) CONTENT_POLICY else when (code) {
        401, 403 -> "Clave inválida o sin permisos ($code). ${body.take(200)}"
        402 -> "Sin saldo suficiente ($code). Recarga en el proveedor."
        404 -> "El modelo no existe o cambió de nombre ($code)."
        400, 422 -> "El modelo rechazó los parámetros: ${body.take(300)}"
        429 -> "Demasiadas solicitudes ($code). Espera un poco."
        else -> "Error $code: ${body.take(300)}"
    }

    private data class Resp(val code: Int, val body: String) { val ok get() = code in 200..299 }

    private fun post(url: String, auth: String, body: String) =
        exec(Request.Builder().url(url).header("Authorization", auth).post(body.toRequestBody(json)).build())

    private fun get(url: String, auth: String) =
        exec(Request.Builder().url(url).header("Authorization", auth).get().build())

    private fun exec(r: Request): Resp = http.newCall(r).execute().use { Resp(it.code, it.body?.string() ?: "") }
}
