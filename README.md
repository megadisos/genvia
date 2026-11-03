# GenVia: videos con IA para redes

App Android (APK) que genera videos con inteligencia artificial a partir de un texto,
lista para TikTok, Reels o YouTube. Te muestra el costo de cada video **antes** de generarlo.

## Descargar el APK

Cada cambio en `main` compila el APK automáticamente. Para descargarlo desde el celular:
**Releases → la versión más reciente → `GenVia.apk`**. Al instalar, Android pedirá permitir
"instalar apps de origen desconocido".

## Modelos incluidos

| Modelo | Empresa | Costo aprox. | Proveedor |
|---|---|---|---|
| Wan 2.2 5B | Alibaba 🇨🇳 | **Gratis** (créditos mensuales) | Hugging Face |
| LTX-Video 13B | Lightricks | **Gratis** (créditos mensuales) | Hugging Face |
| Wan 3.0 | Alibaba 🇨🇳 | $0.10/s (720p) | fal.ai |
| Hailuo H3 Max | MiniMax 🇨🇳 | $0.08/s (768p) | fal.ai |
| Kling O3 4K | Kuaishou 🇨🇳 | $0.42/s | fal.ai |
| Seedance 2.5 | ByteDance 🇨🇳 | ~$0.47/s (720p) | fal.ai |
| Happy Horse 1.1 | Alibaba 🇨🇳 | $0.14/s | fal.ai |
| Gemini Omni Flash 1.1 | Google | $0.10/s (720p) | fal.ai |
| Veo 3.1 Fast (audio) | Google | $0.15/s | fal.ai |
| LTX-2.5 Fast | Lightricks | $0.09/s | fal.ai |

Precios revisados en noviembre de 2026 en [fal.ai](https://fal.ai/learn/tools/ai-video-generators).
Cambian seguido; se editan en `app/src/main/java/com/jorge/videosia/data/Catalog.kt`.

## Claves que necesitas (en Ajustes dentro de la app)

- **Hugging Face** (gratis): crea un token en https://huggingface.co/settings/tokens con el
  permiso *"Make calls to Inference Providers"*.
- **fal.ai** (pago): crea una clave en https://fal.ai/dashboard/keys y recarga saldo.

Las claves se guardan solo en el teléfono.

## Cómo funciona

- Kotlin + Jetpack Compose, minSdk 26 (Android 8).
- `net/VideoClient.kt`: usa la cola de fal.ai (enviar → consultar estado → descargar). Hugging Face
  enruta sus modelos de video por fal.ai con el mismo protocolo.
- Los videos se guardan en **Galería › Movies/GenVia** y se pueden compartir directo a redes.
