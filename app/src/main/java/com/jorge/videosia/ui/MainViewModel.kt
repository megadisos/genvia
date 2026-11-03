package com.jorge.videosia.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jorge.videosia.data.Catalog
import com.jorge.videosia.data.Gallery
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
    var seconds by mutableStateOf(Catalog.models.first().durations.first())
    var aspect by mutableStateOf("9:16")

    var busy by mutableStateOf(false)
    var status by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var resultFile by mutableStateOf<File?>(null)
    var galleryUri by mutableStateOf<Uri?>(null)
    var spent by mutableStateOf(settings.spentUsd)

    fun pick(m: VideoModel) {
        model = m
        if (seconds !in m.durations) seconds = m.durations.first()
    }

    fun generate() {
        if (prompt.isBlank()) { error = "Escribe qué video quieres."; return }
        val m = model
        error = null; resultFile = null; galleryUri = null; busy = true
        job = viewModelScope.launch {
            try {
                val dir = File(getApplication<Application>().filesDir, "videos").apply { mkdirs() }
                val out = File(dir, "genvia_${System.currentTimeMillis()}.mp4")
                val file = client.generate(
                    GenerationRequest(m, prompt.trim(), seconds, aspect),
                    settings.key(m.provider), out
                ) { status = it }
                resultFile = file
                galleryUri = runCatching { Gallery.saveToGallery(getApplication(), file) }.getOrNull()
                if (!m.isFree) {
                    settings.spentUsd = settings.spentUsd + m.estimate(seconds)
                    spent = settings.spentUsd
                }
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

    fun resetSpent() { settings.spentUsd = 0.0; spent = 0.0 }
}
