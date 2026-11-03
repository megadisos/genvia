package com.jorge.videosia.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Currency { USD, COP }

/** Ajustes y contadores guardados en el teléfono. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("videosia", Context.MODE_PRIVATE)

    fun key(p: Provider): String = prefs.getString("key_${p.name}", "") ?: ""
    fun setKey(p: Provider, value: String) = prefs.edit().putString("key_${p.name}", value.trim()).apply()

    var currency: Currency
        get() = runCatching { Currency.valueOf(prefs.getString("currency", "COP")!!) }.getOrDefault(Currency.COP)
        set(v) = prefs.edit().putString("currency", v.name).apply()

    /** Pesos colombianos por 1 USD. Se actualiza solo desde internet. */
    var copPerUsd: Double
        get() = prefs.getFloat("cop_rate", 4000f).toDouble()
        set(v) = prefs.edit().putFloat("cop_rate", v.toFloat()).putLong("cop_rate_at", System.currentTimeMillis()).apply()
    val copRateUpdatedAt: Long get() = prefs.getLong("cop_rate_at", 0L)

    /** Saldo escrito a mano (USD) para cuando la clave no deja leerlo. -1 = no hay. */
    fun manualBalance(p: Provider): Double = prefs.getFloat("manual_bal_${p.name}", -1f).toDouble()
    fun setManualBalance(p: Provider, usd: Double) = prefs.edit().putFloat("manual_bal_${p.name}", usd.toFloat()).apply()

    // ---- Contadores: videos y gasto por día y por mes ----
    private fun day() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun month() = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    fun videosToday(): Int = prefs.getInt("v_d_${day()}", 0)
    fun videosThisMonth(): Int = prefs.getInt("v_m_${month()}", 0)
    fun spentThisMonthUsd(): Double = prefs.getFloat("s_m_${month()}", 0f).toDouble()
    fun spentTotalUsd(): Double = prefs.getFloat("spent", 0f).toDouble()

    fun record(usd: Double) {
        val d = "v_d_${day()}"; val m = "v_m_${month()}"; val s = "s_m_${month()}"
        prefs.edit()
            .putInt(d, prefs.getInt(d, 0) + 1)
            .putInt(m, prefs.getInt(m, 0) + 1)
            .putFloat(s, (prefs.getFloat(s, 0f) + usd).toFloat())
            .putFloat("spent", (prefs.getFloat("spent", 0f) + usd).toFloat())
            .apply()
    }

    fun resetTotal() = prefs.edit().putFloat("spent", 0f).apply()
}
