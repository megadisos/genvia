package com.jorge.videosia.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.ByteArrayOutputStream

/**
 * Coloca la imagen de referencia COMPLETA y centrada en un lienzo con la forma del video
 * (9:16, 16:9 o 1:1). Así la IA no la recorta ni la amplía, que es lo que deformaba los logos.
 */
object ImageFrame {
    /** Parte del lienzo que puede ocupar la imagen (deja margen alrededor). */
    private const val FILL = 0.8f

    fun fit(src: Bitmap, aspect: String): Bitmap {
        val (w, h) = when (aspect) {
            "9:16" -> 720 to 1280
            "16:9" -> 1280 to 720
            else -> 1024 to 1024
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(backgroundOf(src))
        val scale = minOf(w * FILL / src.width, h * FILL / src.height)
        val dw = src.width * scale; val dh = src.height * scale
        val left = (w - dw) / 2f; val top = (h - dh) / 2f
        canvas.drawBitmap(src, null, RectF(left, top, left + dw, top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    fun toJpeg(b: Bitmap): ByteArray =
        ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()

    /** Usa el color de las esquinas de la imagen como fondo; si son transparentes, negro. */
    private fun backgroundOf(src: Bitmap): Int {
        val pts = listOf(0 to 0, src.width - 1 to 0, 0 to src.height - 1, src.width - 1 to src.height - 1)
        val px = pts.map { (x, y) -> src.getPixel(x, y) }
        if (px.any { Color.alpha(it) < 128 }) return Color.BLACK
        val r = px.map { Color.red(it) }.average().toInt()
        val g = px.map { Color.green(it) }.average().toInt()
        val b = px.map { Color.blue(it) }.average().toInt()
        return Color.rgb(r, g, b)
    }
}
