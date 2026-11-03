package com.jorge.videosia.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/** Un video guardado por la app. */
data class SavedVideo(
    val uri: Uri,
    val name: String,
    /** Carpeta de la IA que lo generó, por ejemplo "Kling 3.0 Standard". */
    val folder: String,
    /** Ruta legible, por ejemplo "Movies/GenVia/Kling 3.0 Standard/genvia_20261103_041500.mp4". */
    val path: String,
    val sizeBytes: Long,
    val dateMs: Long,
)

/**
 * Guarda los videos en la galería, en una carpeta por IA:
 * Movies/GenVia/<nombre de la IA>/archivo.mp4
 */
object Gallery {
    const val ROOT = "GenVia"

    /** Nombre de carpeta seguro a partir del nombre del modelo. */
    fun folderFor(model: VideoModel): String =
        model.name.replace(Regex("[^A-Za-z0-9 ._-]"), "").trim().ifBlank { "Otros" }

    fun save(context: Context, file: File, folder: String): SavedVideo? {
        return if (Build.VERSION.SDK_INT >= 29) saveMediaStore(context, file, folder) else saveLegacy(context, file, folder)
    }

    private fun saveMediaStore(context: Context, file: File, folder: String): SavedVideo? {
        val rel = "${Environment.DIRECTORY_MOVIES}/$ROOT/$folder"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "$rel/")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return null
        return SavedVideo(uri, file.name, folder, "$rel/${file.name}", file.length(), System.currentTimeMillis())
    }

    /** Android 8 y 9: carpeta de la app (Android/data/com.jorge.videosia/files/Movies/GenVia/<IA>). */
    private fun saveLegacy(context: Context, file: File, folder: String): SavedVideo? {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "$ROOT/$folder").apply { mkdirs() }
        val dest = File(dir, file.name)
        file.copyTo(dest, overwrite = true)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", dest)
        return SavedVideo(uri, dest.name, folder, dest.absolutePath, dest.length(), dest.lastModified())
    }

    /** Todos los videos que guardó la app, del más nuevo al más viejo. */
    fun list(context: Context): List<SavedVideo> =
        runCatching { if (Build.VERSION.SDK_INT >= 29) listMediaStore(context) else listLegacy(context) }
            .getOrDefault(emptyList())

    private fun listMediaStore(context: Context): List<SavedVideo> {
        val prefix = "${Environment.DIRECTORY_MOVIES}/$ROOT/"
        val cols = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DATE_ADDED,
        )
        val out = mutableListOf<SavedVideo>()
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cols,
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?", arrayOf("$prefix%"),
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                val rel = c.getString(2) ?: prefix
                val folder = rel.removePrefix(prefix).trimEnd('/').ifBlank { "Otros" }
                out += SavedVideo(
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                    name, folder, rel + name, c.getLong(3), c.getLong(4) * 1000
                )
            }
        }
        return out
    }

    private fun listLegacy(context: Context): List<SavedVideo> {
        val root = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), ROOT)
        return root.listFiles().orEmpty().filter { it.isDirectory }.flatMap { dir ->
            dir.listFiles().orEmpty().filter { it.extension == "mp4" }.map { f ->
                SavedVideo(
                    FileProvider.getUriForFile(context, "${context.packageName}.files", f),
                    f.name, dir.name, f.absolutePath, f.length(), f.lastModified()
                )
            }
        }.sortedByDescending { it.dateMs }
    }

    fun delete(context: Context, v: SavedVideo): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 29) context.contentResolver.delete(v.uri, null, null) > 0 else File(v.path).delete()
    }.getOrDefault(false)

    fun share(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartir video").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
