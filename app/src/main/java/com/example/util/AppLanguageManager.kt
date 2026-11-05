package com.example.util

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppLanguage(val code: String, val displayName: String, val flag: String) {
    BENGALI("bn", "বাংলা", "🇧🇩"),
    ENGLISH("en", "English", "🇬🇧")
}

/**
 * Centralized Language Preference and Localization Manager.
 * Persists user language choice and provides localized formatting for numbers, dates, and currency.
 */
object AppLanguageManager {
    private const val PREF_NAME = "smartbiz_language_prefs"
    private const val KEY_SELECTED_LANGUAGE = "selected_app_language"

    private var sharedPrefs: SharedPreferences? = null
    private val _currentLanguage = MutableStateFlow(AppLanguage.BENGALI)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPrefs = prefs
        val savedCode = prefs.getString(KEY_SELECTED_LANGUAGE, AppLanguage.BENGALI.code) ?: AppLanguage.BENGALI.code
        val lang = if (savedCode == AppLanguage.ENGLISH.code) AppLanguage.ENGLISH else AppLanguage.BENGALI
        _currentLanguage.value = lang
    }

    fun setLanguage(language: AppLanguage) {
        _currentLanguage.value = language
        sharedPrefs?.edit()?.putString(KEY_SELECTED_LANGUAGE, language.code)?.apply()
    }

    fun isEnglish(): Boolean = _currentLanguage.value == AppLanguage.ENGLISH
    fun isBengali(): Boolean = _currentLanguage.value == AppLanguage.BENGALI

    fun getLanguage(): AppLanguage = _currentLanguage.value

    // Currency is always BDT, shown with ৳ symbol as required
    fun formatCurrency(amount: Double): String {
        return if (isEnglish()) {
            "৳ " + String.format(Locale.US, "%,.2f", amount)
        } else {
            "৳ " + String.format(Locale.US, "%,.2f", amount)
        }
    }

    fun formatNumber(number: Long): String {
        return if (isEnglish()) {
            number.toString()
        } else {
            BengaliFormatter.formatNumber(number)
        }
    }

    fun formatNumber(number: Double): String {
        return if (isEnglish()) {
            String.format(Locale.US, "%.2f", number)
        } else {
            BengaliFormatter.formatNumber(number)
        }
    }

    fun formatNumber(number: Int): String {
        return if (isEnglish()) {
            number.toString()
        } else {
            BengaliFormatter.formatNumber(number)
        }
    }

    fun formatDate(timestamp: Long): String {
        return if (isEnglish()) {
            val sdf = SimpleDateFormat("dd MMM, yyyy - hh:mm a", Locale.ENGLISH)
            sdf.format(Date(timestamp))
        } else {
            BengaliFormatter.formatDate(timestamp)
        }
    }

    fun formatDateOnly(timestamp: Long): String {
        return if (isEnglish()) {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
            sdf.format(Date(timestamp))
        } else {
            BengaliFormatter.formatDateOnly(timestamp)
        }
    }
}
