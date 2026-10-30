package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.Locale

class BengaliVoiceHelper(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isBengaliSupported = MutableStateFlow(false)
    val isBengaliSupported: StateFlow<Boolean> = _isBengaliSupported.asStateFlow()

    private val _statusMessage = MutableStateFlow("ভয়েস সার্ভিস শুরু হচ্ছে...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            _statusMessage.value = "ভয়েস ইঞ্জিন চালু করা যায়নি: ${e.message}"
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupBengaliVoice()
        } else {
            isInitialized = false
            _isBengaliSupported.value = false
            _statusMessage.value = "TTS ইঞ্জিন লোড হতে ব্যর্থ হয়েছে।"
        }
    }

    private fun setupBengaliVoice() {
        val engine = tts ?: return

        // 1. Check Bangladesh Bengali (bn-BD)
        val bdLocale = Locale("bn", "BD")
        val bdAvailable = engine.isLanguageAvailable(bdLocale)

        // 2. Check General Bengali (bn)
        val generalBnLocale = Locale("bn")
        val generalBnAvailable = engine.isLanguageAvailable(generalBnLocale)

        // 3. Check India Bengali (bn-IN)
        val inBnLocale = Locale("bn", "IN")
        val inBnAvailable = engine.isLanguageAvailable(inBnLocale)

        when {
            bdAvailable >= TextToSpeech.LANG_AVAILABLE -> {
                engine.language = bdLocale
                _isBengaliSupported.value = true
                _statusMessage.value = "বাংলা (বাংলাদেশ) ভয়েস সক্রিয় রয়েছে।"
            }
            generalBnAvailable >= TextToSpeech.LANG_AVAILABLE -> {
                engine.language = generalBnLocale
                _isBengaliSupported.value = true
                _statusMessage.value = "বাংলা ভয়েস সক্রিয় রয়েছে।"
            }
            inBnAvailable >= TextToSpeech.LANG_AVAILABLE -> {
                engine.language = inBnLocale
                _isBengaliSupported.value = true
                _statusMessage.value = "বাংলা (ভারত) ভয়েস সক্রিয় রয়েছে।"
            }
            else -> {
                // Smooth fallback without crash
                engine.language = Locale.getDefault()
                _isBengaliSupported.value = false
                _statusMessage.value = "ডিভাইসে সরাসরি বাংলা ভয়েস ডাটা নেই। গুগল স্পিচ সার্ভিসেস থেকে বাংলা ইনস্টল করা যাবে (বর্তমানে সিস্টেম ডিফল্ট ব্যবহৃত হচ্ছে)।"
            }
        }
    }

    /**
     * Check if currently within quiet hours.
     * e.g., startHour = 22 (10 PM), endHour = 7 (7 AM).
     */
    fun isQuietHours(startHour: Int = 22, endHour: Int = 7): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (startHour > endHour) {
            // Spans midnight: 22 to 24 or 0 to 7
            currentHour >= startHour || currentHour < endHour
        } else {
            currentHour in startHour until endHour
        }
    }

    /**
     * Speak a reminder message. Overlaps are prevented by stopping any currently speaking utterance.
     */
    fun speak(
        text: String,
        isVoiceEnabled: Boolean = true,
        quietHoursEnabled: Boolean = true,
        quietHoursStart: Int = 22,
        quietHoursEnd: Int = 7
    ) {
        if (!isVoiceEnabled) return
        if (quietHoursEnabled && isQuietHours(quietHoursStart, quietHoursEnd)) {
            // Suppress during quiet hours
            return
        }

        val engine = tts
        if (engine == null || !isInitialized) {
            return
        }

        try {
            // Stop current playback to prevent overlapping voice
            engine.stop()
            val utteranceId = "smart_biz_reminder_${System.currentTimeMillis()}"
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (_: Exception) {
            // Never crash if TTS encounters audio track error
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
    }
}
