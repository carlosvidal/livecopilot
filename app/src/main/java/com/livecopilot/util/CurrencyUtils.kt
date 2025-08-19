package com.livecopilot.util

import android.content.Context
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object CurrencyUtils {
    private const val PREFS = "livecopilot_prefs"
    private const val KEY_CURRENCY = "pref_currency"
    private const val KEY_LANGUAGE = "pref_language"

    fun getUserLocale(context: Context): Locale {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tag = prefs.getString(KEY_LANGUAGE, "es-MX") ?: "es-MX"
        // Locale.forLanguageTag is available API 21+
        return Locale.forLanguageTag(tag)
    }

    fun getUserCurrency(context: Context): Currency {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_CURRENCY, "USD") ?: "USD"
        return runCatching { Currency.getInstance(code) }.getOrElse { Currency.getInstance("USD") }
    }

    fun currencyFormatter(context: Context): NumberFormat {
        val locale = getUserLocale(context)
        val nf = NumberFormat.getCurrencyInstance(locale)
        // Force selected currency symbol/code even if locale implies another currency
        val userCurrency = getUserCurrency(context)
        nf.currency = userCurrency
        return nf
    }

    fun formatAmount(context: Context, amount: Double): String {
        return currencyFormatter(context).format(amount)
    }
}
