package com.jorge.videosia.data

/** Quién ejecuta el modelo. */
enum class Provider(
    val label: String,
    val keyHint: String,
    val keyUrl: String,
    /** Qué hay gratis en este proveedor, explicado para el usuario. */
    val freeInfo: String,
) {
    POLLINATIONS(
        "Pollinations",
        "Clave secreta sk_... (con permiso de saldo \"account:usage\")",
        "https://enter.pollinations.ai/keys",
        "Regala un poco de Pollen gratis al completar misiones (por ejemplo 0,25), que solo sirve en " +
            "modelos que no son solo de pago. Ya no recarga cada día. 1 Pollen rinde como 1 USD en videos " +
            "y comprarlo cuesta entre 1,04 y 1,19 USD según el paquete.",
    ),
    FAL(
        "fal.ai",
        "API key de fal.ai (con permiso de lectura de facturación)",
        "https://fal.ai/dashboard/keys",
        "No tiene plan gratis. Recargas saldo y cada video se descuenta por segundo.",
    ),
}

/** Cómo se envía la duración a fal.ai. */
enum class DurationStyle { NONE, STRING, SECONDS_SUFFIX, INT }

data class VideoModel(
    val id: String,
    val name: String,
    val company: String,
    val country: String,
    val provider: Provider,
    /** Endpoint en fal.ai o nombre de modelo en Pollinations. */
    val endpoint: String,
    /** USD por segundo de video (estimado). */
    val usdPerSecond: Double,
    val quality: String,
    /** Duraciones posibles en segundos. */
    val durations: List<Int>,
    val durationStyle: DurationStyle = DurationStyle.INT,
    val aspects: List<String> = listOf("9:16", "16:9", "1:1"),
    val audio: Boolean = false,
    /** Se puede pagar con el Pollen gratis de misiones (si es false, solo saldo pagado). */
    val freePollenOk: Boolean = false,
    /** Acepta imagen de referencia (Pollinations: sí; fal.ai: si tiene endpoint de imagen a video). */
    val imageOk: Boolean = provider == Provider.POLLINATIONS,
    /** Endpoint de fal.ai para imagen a video. */
    val imageEndpoint: String? = null,
) {
    fun estimateUsd(seconds: Int): Double = usdPerSecond * seconds
    val durationText: String
        get() = if (durations.size == 1) "${durations[0]} s" else "${durations.first()} a ${durations.last()} s"
}

/**
 * Catálogo. Precios aproximados (fal.ai y fabricantes, revisados nov. 2026).
 * Con Pollinations la app además mide el costo REAL comparando el saldo antes y después.
 */
object Catalog {
    private val P = Provider.POLLINATIONS
    private val F = Provider.FAL

    val models = listOf(
        // ---------- Pollinations: una sola clave, acepta el Pollen gratis ----------
        VideoModel("pol-wan22fast", "Wan 2.2 Fast", "Alibaba", "China", P, "alibaba/wan-2.2-fast",
            0.015, "480p, el más barato", (2..15).toList(), freePollenOk = true),
        VideoModel("pol-wan30", "Wan 3.0", "Alibaba", "China", P, "alibaba/wan-3.0",
            0.10, "720p con audio", listOf(5), audio = true),
        VideoModel("pol-kling3", "Kling 3.0 Standard", "Kuaishou", "China", P, "kwaivgi/kling-v3.0-std",
            0.084, "720p", listOf(5, 10)),
        VideoModel("pol-seed20mini", "Seedance 2.0 Mini", "ByteDance", "China", P, "bytedance/seedance-2.0-mini",
            0.07, "480p con audio", (4..10).toList(), audio = true),
        VideoModel("pol-seed20fast", "Seedance 2.0 Fast", "ByteDance", "China", P, "bytedance/seedance-2.0-fast",
            0.12, "720p con audio", listOf(4, 5), audio = true),
        VideoModel("pol-h3max", "Hailuo H3 Max", "MiniMax", "China", P, "minimax/minimax-h3-max",
            0.08, "768p con audio", listOf(5, 10, 15), audio = true),
        VideoModel("pol-happyhorse", "Happy Horse 1.1", "Alibaba", "China", P, "alibaba/happyhorse-1.1",
            0.14, "720p", listOf(5)),
        VideoModel("pol-omni", "Gemini Omni Flash 1.1", "Google", "EE. UU.", P, "google/gemini-omni-1.1-flash",
            0.10, "720p con audio", (3..10).toList(), audio = true),
        VideoModel("pol-veo", "Veo 3.1 Fast", "Google", "EE. UU.", P, "google/veo-3.1-fast",
            0.15, "720p con audio", listOf(4, 6, 8), audio = true),

        // ---------- fal.ai: pago por segundo ----------
        VideoModel("fal-wan30", "Wan 3.0", "Alibaba", "China", F, "alibaba/wan-3.0/text-to-video",
            0.10, "720p", listOf(5, 10),
            imageOk = true, imageEndpoint = "alibaba/wan-3.0/image-to-video"),
        VideoModel("fal-h3max", "Hailuo H3 Max", "MiniMax", "China", F, "minimax/h3-max/text-to-video",
            0.08, "768p", listOf(6), DurationStyle.NONE,
            imageOk = true, imageEndpoint = "minimax/h3-max/image-to-video"),
        VideoModel("fal-kling-o3", "Kling O3 4K", "Kuaishou", "China", F, "fal-ai/kling-video/o3/4k/text-to-video",
            0.42, "4K con audio", listOf(5, 10), DurationStyle.STRING, audio = true,
            imageOk = true, imageEndpoint = "fal-ai/kling-video/o3/4k/image-to-video"),
        VideoModel("fal-seed25", "Seedance 2.5", "ByteDance", "China", F, "bytedance/seedance-2.5/text-to-video",
            0.473, "720p", listOf(5, 10), DurationStyle.STRING,
            imageOk = true, imageEndpoint = "bytedance/seedance-2.5/image-to-video"),
        VideoModel("fal-veo-fast", "Veo 3.1 Fast", "Google", "EE. UU.", F, "fal-ai/veo3.1/fast",
            0.15, "720p/1080p con audio", listOf(4, 6, 8), DurationStyle.SECONDS_SUFFIX, audio = true,
            imageOk = true, imageEndpoint = "fal-ai/veo3.1/fast/image-to-video"),
        VideoModel("fal-ltx25", "LTX-2.5 Fast", "Lightricks", "Israel", F, "lightricks/ltx-2.5/text-to-video/fast",
            0.09, "720p", listOf(6, 8, 10),
            imageOk = true, imageEndpoint = "lightricks/ltx-2.5/image-to-video/fast"),
    )
}
