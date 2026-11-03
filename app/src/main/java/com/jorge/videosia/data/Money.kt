package com.jorge.videosia.data

import java.text.NumberFormat
import java.util.Locale

/** Formatea montos en la moneda elegida. */
class Money(private val currency: Currency, private val copPerUsd: Double) {
    fun fmt(usd: Double): String = when (currency) {
        Currency.USD -> "US$ " + String.format(Locale.US, if (usd < 1) "%.3f" else "%.2f", usd)
        Currency.COP -> {
            val cop = usd * copPerUsd
            val nf = NumberFormat.getIntegerInstance(Locale("es", "CO"))
            "$ " + nf.format(Math.round(cop)) + " COP"
        }
    }
}
