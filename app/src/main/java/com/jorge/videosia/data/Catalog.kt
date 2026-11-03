package com.jorge.videosia.data

/** Quién ejecuta el modelo. */
enum class Provider(val label: String, val keyHint: String, val keyUrl: String) {
    HUGGINGFACE(
        "Hugging Face (gratis con créditos mensuales)",
        "Token hf_... con permiso \"Inference Providers\"",
        "https://huggingface.co/settings/tokens"
    ),
    FAL(
        "fal.ai (pago por segundo)",
        "API key de fal.ai",
        "https://fal.ai/dashboard/keys"
    ),
}

/** Cómo se envía la duración al modelo. */
enum class DurationStyle {
    /** No se envía; el modelo usa su duración por defecto. */
    NONE,
    /** "5" (string) */
    STRING,
    /** "8s" */
    SECONDS_SUFFIX,
    /** 5 (número) */
    INT,
}

data class VideoModel(
    val id: String,
    val name: String,
    val company: String,
    val country: String,
    val provider: Provider,
    /** Endpoint en fal.ai, o id de modelo en Hugging Face. */
    val endpoint: String,
    /** USD por segundo de video. 0 = gratis (consume créditos gratis). */
    val usdPerSecond: Double,
    val priceNote: String,
    val durations: List<Int>,
    val durationStyle: DurationStyle,
    val sendsAspectRatio: Boolean = true,
    val audio: Boolean = false,
) {
    val isFree get() = usdPerSecond == 0.0
    fun estimate(seconds: Int): Double = usdPerSecond * seconds
}

/**
 * Catálogo de modelos. Precios de la página oficial de fal.ai y de los
 * fabricantes, revisados en noviembre de 2026. Son aproximados: la app
 * siempre muestra el costo estimado ANTES de generar.
 */
object Catalog {
    val models = listOf(
        // ---------- GRATIS (créditos mensuales de Hugging Face) ----------
        VideoModel(
            id = "hf-wan22", name = "Wan 2.2 5B", company = "Alibaba", country = "China",
            provider = Provider.HUGGINGFACE, endpoint = "Wan-AI/Wan2.2-TI2V-5B",
            usdPerSecond = 0.0, priceNote = "Gratis con los créditos mensuales de Hugging Face",
            durations = listOf(5), durationStyle = DurationStyle.NONE, sendsAspectRatio = false,
        ),
        VideoModel(
            id = "hf-ltx", name = "LTX-Video 13B (rápido)", company = "Lightricks", country = "Israel",
            provider = Provider.HUGGINGFACE, endpoint = "Lightricks/LTX-Video-0.9.8-13B-distilled",
            usdPerSecond = 0.0, priceNote = "Gratis con los créditos mensuales de Hugging Face",
            durations = listOf(5), durationStyle = DurationStyle.NONE, sendsAspectRatio = false,
        ),

        // ---------- CHINAS (pago, vía fal.ai) ----------
        VideoModel(
            id = "fal-wan30", name = "Wan 3.0", company = "Alibaba", country = "China",
            provider = Provider.FAL, endpoint = "alibaba/wan-3.0/text-to-video",
            usdPerSecond = 0.10, priceNote = "720p · 480p cuesta $0.05/s",
            durations = listOf(5, 10), durationStyle = DurationStyle.INT,
        ),
        VideoModel(
            id = "fal-minimax-h3max", name = "Hailuo H3 Max", company = "MiniMax", country = "China",
            provider = Provider.FAL, endpoint = "minimax/h3-max/text-to-video",
            usdPerSecond = 0.08, priceNote = "768p · 480p cuesta $0.05/s",
            durations = listOf(6), durationStyle = DurationStyle.NONE,
        ),
        VideoModel(
            id = "fal-kling-o3", name = "Kling O3 4K", company = "Kuaishou", country = "China",
            provider = Provider.FAL, endpoint = "fal-ai/kling-video/o3/4k/text-to-video",
            usdPerSecond = 0.42, priceNote = "4K, con o sin audio",
            durations = listOf(5, 10), durationStyle = DurationStyle.STRING, audio = true,
        ),
        VideoModel(
            id = "fal-seedance25", name = "Seedance 2.5", company = "ByteDance", country = "China",
            provider = Provider.FAL, endpoint = "bytedance/seedance-2.5/text-to-video",
            usdPerSecond = 0.473, priceNote = "720p · 480p cuesta ~$0.22/s",
            durations = listOf(5, 10), durationStyle = DurationStyle.STRING,
        ),
        VideoModel(
            id = "fal-happyhorse", name = "Happy Horse 1.1", company = "Alibaba", country = "China",
            provider = Provider.FAL, endpoint = "alibaba/happy-horse/v1.1/text-to-video",
            usdPerSecond = 0.14, priceNote = "720p",
            durations = listOf(5), durationStyle = DurationStyle.NONE,
        ),

        // ---------- OTRAS (pago, vía fal.ai) ----------
        VideoModel(
            id = "fal-omni-flash", name = "Gemini Omni Flash 1.1", company = "Google", country = "EE. UU.",
            provider = Provider.FAL, endpoint = "google/gemini-omni-flash/v1.1/text-to-video",
            usdPerSecond = 0.10, priceNote = "720p · 360p cuesta $0.03/s",
            durations = listOf(5), durationStyle = DurationStyle.NONE,
        ),
        VideoModel(
            id = "fal-veo31-fast", name = "Veo 3.1 Fast (con audio)", company = "Google", country = "EE. UU.",
            provider = Provider.FAL, endpoint = "fal-ai/veo3.1/fast",
            usdPerSecond = 0.15, priceNote = "720p/1080p con audio",
            durations = listOf(4, 6, 8), durationStyle = DurationStyle.SECONDS_SUFFIX, audio = true,
        ),
        VideoModel(
            id = "fal-ltx25-fast", name = "LTX-2.5 Fast", company = "Lightricks", country = "Israel",
            provider = Provider.FAL, endpoint = "lightricks/ltx-2.5/text-to-video/fast",
            usdPerSecond = 0.09, priceNote = "720p",
            durations = listOf(6, 8, 10), durationStyle = DurationStyle.INT,
        ),
    )

    fun byId(id: String) = models.firstOrNull { it.id == id }
}
