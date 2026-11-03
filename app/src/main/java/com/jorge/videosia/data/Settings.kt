package com.jorge.videosia.data

import android.content.Context

/** Guarda las claves de API en el teléfono (nunca salen del dispositivo salvo hacia su proveedor). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("videosia", Context.MODE_PRIVATE)

    fun key(p: Provider): String = prefs.getString("key_${p.name}", "") ?: ""
    fun setKey(p: Provider, value: String) =
        prefs.edit().putString("key_${p.name}", value.trim()).apply()

    /** Total gastado estimado (USD), para que el usuario lleve la cuenta. */
    var spentUsd: Double
        get() = prefs.getFloat("spent", 0f).toDouble()
        set(v) = prefs.edit().putFloat("spent", v.toFloat()).apply()
}
