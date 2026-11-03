package com.jorge.videosia.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jorge.videosia.data.Catalog
import com.jorge.videosia.data.Currency
import com.jorge.videosia.data.Gallery
import com.jorge.videosia.data.Money
import com.jorge.videosia.data.Provider
import com.jorge.videosia.data.Settings
import com.jorge.videosia.data.VideoModel
import com.jorge.videosia.net.GenerationRequest
import com.jorge.videosia.net.VideoClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val settings = Settings(app)
    private val client = VideoClient()
    private var job: Job? = null

    var model by mutableStateOf<VideoModel>(Catalog.models.first())
    var prompt by mutableStateOf("")
    var seconds by mutableStateOf(5)
    var aspect by mutableStateOf("9:16")

    /** Imagen de referencia (ya reducida y en JPEG) y su miniatura. */
    var imageJpeg by mutableStateOf<ByteArray?>(null)
    var imagePreview by mutableStateOf<Bitmap?>(null)

    var currency by mutableStateOf(settings.currency)
    var copPerUsd by mutableStateOf(settings.copPerUsd)
    val money get() = Money(currency, copPerUsd)

    /** Saldo leído de cada proveedor (USD). Ausente = desconocido. */
    val balances = mutableStateMapOf<Provider, Double>()
    var stats by mutableStateOf(readStats())

    var busy by mutableStateOf(false)
    var status by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var resultFile by mutableStateOf<File?>(null)
    var galleryUri by mutableStateOf<Uri?>(null)
    var lastInfo by mutableStateOf<String?>(null)

    data class Stats(val today: Int, val month: Int, val spentMonthUsd: Double, val spentTotalUsd: Double)
    private fun readStats() = Stats(settings.videosToday(), settings.videosThisMonth(), settings.spentThisMonthUsd(), settings.spentTotalUsd())

    init {
        pick(model)
        refreshRate()
        refreshBalances()
    }

    fun pick(m: VideoModel) {
        model = m
        if (seconds !in m.durations) seconds = m.durations.firstOrNull { it >= 5 } ?: m.durations.first()
        if (aspect !in m.aspects) aspect = m.aspects.first()
    }

    fun setImage(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val cr = getApplication<Application>().contentResolver
                val src = cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }!!
                val scale = minOf(1f, 1280f / maxOf(src.width, src.height))
                val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
                val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
                imageJpeg = bytes; imagePreview = bmp
                // Ajusta el formato a la forma de la imagen.
                aspect = when {
                    bmp.height > bmp.width * 1.2 -> "9:16"
                    bmp.width > bmp.height * 1.2 -> "16:9"
                    else -> "1:1"
                }.let { if (it in model.aspects) it else aspect }
            }.onFailure { error = "No se pudo abrir la imagen." }
        }
    }

    fun clearImage() { imageJpeg = null; imagePreview = null }

    fun setCurrency(c: Currency) { currency = c; settings.currency = c }

    fun refreshRate() = viewModelScope.launch {
        client.copPerUsd()?.let { settings.copPerUsd = it; copPerUsd = it }
    }

    fun refreshBalances() = viewModelScope.launch {
        Provider.entries.forEach { p ->
            val live = client.balanceUsd(p, settings.key(p))
            val manual = settings.manualBalance(p)
            when {
                live != null -> balances[p] = live
                manual >= 0 -> balances[p] = manual
                else -> balances.remove(p)
            }
        }
    }

    /** Cuántos videos de este modelo y duración alcanzan con el saldo conocido. */
    fun videosLeft(m: VideoModel, secs: Int): Int? {
        val bal = balances[m.provider] ?: return null
        val cost = m.estimateUsd(secs)
        return if (cost <= 0) null else (bal / cost).toInt()
    }

    fun generate() {
        if (prompt.isBlank()) { error = "Escribe qué video quieres."; return }
        val m = model; val secs = seconds; val img = imageJpeg
        if (img != null && !m.imageOk) { error = "${m.name} no acepta imagen de referencia. Elige otro modelo o quita la imagen."; return }
        error = null; resultFile = null; galleryUri = null; lastInfo = null; busy = true
        job = viewModelScope.launch {
            val key = settings.key(m.provider)
            val before = client.balanceUsd(m.provider, key)
            val start = System.currentTimeMillis()
            try {
                val dir = File(getApplication<Application>().filesDir, "videos").apply { mkdirs() }
                val out = File(dir, "genvia_${System.currentTimeMillis()}.mp4")
                val file = client.generate(GenerationRequest(m, prompt.trim(), secs, aspect, img), key, out) { status = it }
                val took = (System.currentTimeMillis() - start) / 1000
                resultFile = file
                galleryUri = runCatching { Gallery.saveToGallery(getApplication(), file) }.getOrNull()

                val after = client.balanceUsd(m.provider, key)
                val real = if (before != null && after != null && before - after > 0) before - after else null
                val cost = real ?: m.estimateUsd(secs)
                settings.record(cost)
                if (after == null && settings.manualBalance(m.provider) >= 0) {
                    settings.setManualBalance(m.provider, (settings.manualBalance(m.provider) - cost).coerceAtLeast(0.0))
                }
                stats = readStats()
                refreshBalances()
                lastInfo = "Video de ${secs}s · tardó ${took}s en generarse · costó " +
                    (if (real != null) money.fmt(cost) else "~" + money.fmt(cost) + " (estimado)")
                status = "¡Listo! Guardado en Galería › Movies/GenVia"
            } catch (e: Exception) {
                error = e.message ?: e.toString()
                status = ""
            } finally {
                busy = false
            }
        }
    }

    fun cancel() { job?.cancel(); busy = false; status = "Cancelado" }
    fun resetTotal() { settings.resetTotal(); stats = readStats() }
}
