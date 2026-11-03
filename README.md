# GenVia: videos con IA para redes

App Android (APK) que genera videos con inteligencia artificial a partir de un texto,
lista para TikTok, Reels o YouTube. Te muestra el costo de cada video **antes** de generarlo.

## Descargar el APK

Cada cambio en `main` compila el APK automáticamente. Para descargarlo desde el celular:
**Releases → la versión más reciente → `GenVia.apk`**. Al instalar, Android pedirá permitir
"instalar apps de origen desconocido".

## Qué muestra la app de cada IA

- Duración del video que genera (por ejemplo 5 a 15 s) y que cada generación da 1 video.
- Precio por segundo y costo del video, en **COP o USD** (la tasa se actualiza sola).
- Tu saldo y cuántos videos te alcanzan con él; videos hechos hoy y este mes, y lo gastado.
- Con Pollinations mide el costo real (saldo antes y después); en fal.ai lee el saldo si la clave lo permite.
- Puedes agregar una **imagen de referencia**, que se usa como primer cuadro del video.

## Modelos incluidos

| Modelo | Empresa | Precio aprox. | Duración | Proveedor |
|---|---|---|---|---|
| Wan 2.2 Fast | Alibaba 🇨🇳 | $0.015/s | 2 a 15 s | Pollinations (acepta Pollen gratis) |
| Wan 3.0 | Alibaba 🇨🇳 | $0.10/s | 5 s | Pollinations |
| Kling 3.0 Standard | Kuaishou 🇨🇳 | $0.084/s | 5 o 10 s | Pollinations |
| Seedance 2.0 Mini / Fast | ByteDance 🇨🇳 | $0.07 / $0.12/s | 4 a 10 s | Pollinations |
| Hailuo H3 Max | MiniMax 🇨🇳 | $0.08/s | 5, 10 o 15 s | Pollinations |
| Happy Horse 1.1 | Alibaba 🇨🇳 | $0.14/s | 5 s | Pollinations |
| Gemini Omni Flash / Veo 3.1 Fast | Google | $0.10 / $0.15/s | 3 a 10 s | Pollinations |
| Wan 3.0, Hailuo H3 Max, Kling O3 4K, Seedance 2.5, Veo 3.1 Fast, LTX-2.5 | varios | $0.08 a $0.47/s | 4 a 10 s | fal.ai |

Precios revisados en noviembre de 2026; cambian seguido y se editan en
`app/src/main/java/com/jorge/videosia/data/Catalog.kt`.

## Lo gratis, sin letra pequeña

- **Pollinations**: regala Pollen una sola vez al completar misiones (unos 3 Pollen ≈ 3 USD).
  Ya no recarga cada día. Con Wan 2.2 Fast eso da para muchos videos cortos.
- **fal.ai**: no tiene plan gratis.
- Kling, Hailuo y Dreamina tienen créditos diarios gratis solo en sus apps, no por API.

## Claves (en Ajustes dentro de la app)

- **Pollinations**: https://enter.pollinations.ai/keys (clave `sk_`, dale permiso `account:usage`
  para que la app pueda leer tu saldo).
- **fal.ai**: https://fal.ai/dashboard/keys (con permiso de lectura de facturación para ver el saldo).

## Cómo funciona

- Kotlin + Jetpack Compose, minSdk 26 (Android 8).
- `net/VideoClient.kt`: Pollinations (trabajo asíncrono) y la cola de fal.ai, más lectura de saldo y tasa COP.
- Los videos se guardan en la galería en una carpeta por IA: **Movies/GenVia/<nombre de la IA>/genvia_fecha_hora.mp4**. La pestaña *Mis videos* los muestra agrupados por IA, con su ruta, para verlos, compartirlos o borrarlos.
