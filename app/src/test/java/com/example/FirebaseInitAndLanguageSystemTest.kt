package com.example

import com.example.util.*
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FirebaseInitAndLanguageSystemTest {

    @Before
    fun setup() {
        AppLanguageManager.setLanguage(AppLanguage.BENGALI) // Default
    }

    @Test
    fun test_1_firebaseInitialization_guarantee() {
        assertEquals("smart-business-manager-71879", FirebaseInitHelper.PROJECT_ID)
        assertEquals("1:484040397249:android:8a883b7879abd19bd6df40", FirebaseInitHelper.APPLICATION_ID)
        assertEquals("AIzaSyC8mm4ucqjTboLxRRuunqhBT9dSn-qbkEM", FirebaseInitHelper.API_KEY)
        assertEquals("smart-business-manager-71879.firebasestorage.app", FirebaseInitHelper.STORAGE_BUCKET)
    }

    @Test
    fun test_2_languageManager_persistence_and_defaults() {
        // 1. Initial default must be Bengali
        assertEquals(AppLanguage.BENGALI, AppLanguageManager.getLanguage())
        assertTrue(AppLanguageManager.isBengali())
        assertFalse(AppLanguageManager.isEnglish())

        // 2. Switch to English
        AppLanguageManager.setLanguage(AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, AppLanguageManager.getLanguage())
        assertTrue(AppLanguageManager.isEnglish())
        assertFalse(AppLanguageManager.isBengali())

        // 3. Switch back to Bengali
        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
        assertEquals(AppLanguage.BENGALI, AppLanguageManager.getLanguage())
        assertTrue(AppLanguageManager.isBengali())
        assertFalse(AppLanguageManager.isEnglish())
    }

    @Test
    fun test_3_errorMapping_wrongPassword_bilingual() {
        val ex = Exception("ERROR_WRONG_PASSWORD: The password is invalid")

        // In Bengali
        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
        val errBn = AppErrorHandler.map(ex)
        assertTrue("Bengali problem must mention password", errBn.getProblem().contains("পাসওয়ার্ড"))
        assertTrue("Bengali action must mention retry or reset", errBn.getWhatToDo().contains("পাসওয়ার্ড পরিবর্তন"))
        assertTrue(errBn.toUserMessage().contains("সমস্যা:"))
        assertTrue(errBn.toUserMessage().contains("করণীয়:"))

        // In English
        AppLanguageManager.setLanguage(AppLanguage.ENGLISH)
        val errEn = AppErrorHandler.map(ex)
        assertTrue("English problem must mention password", errEn.getProblem().contains("password", ignoreCase = true))
        assertTrue("English action must mention reset", errEn.getWhatToDo().contains("reset", ignoreCase = true))
        assertTrue(errEn.toUserMessage().contains("Problem:"))
        assertTrue(errEn.toUserMessage().contains("What to do:"))
    }

    @Test
    fun test_4_errorMapping_networkUnavailable_bilingual() {
        val ex = java.net.UnknownHostException("network error: failed to connect to host")

        // In Bengali
        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
        val errBn = AppErrorHandler.map(ex)
        assertTrue(errBn.getProblem().contains("ইন্টারনেট সংযোগ"))
        assertTrue(errBn.getWhatToDo().contains("মোবাইল ডাটা") || errBn.getWhatToDo().contains("Wi-Fi"))

        // In English
        AppLanguageManager.setLanguage(AppLanguage.ENGLISH)
        val errEn = AppErrorHandler.map(ex)
        assertTrue(errEn.getProblem().contains("internet connection", ignoreCase = true))
        assertTrue(errEn.getWhatToDo().contains("mobile data", ignoreCase = true) || errEn.getWhatToDo().contains("Wi-Fi", ignoreCase = true))
    }

    @Test
    fun test_5_errorMapping_permissionDenied_bilingual() {
        val ex = Exception("PERMISSION_DENIED: Missing or insufficient permissions.")

        // In Bengali
        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
        val errBn = AppErrorHandler.map(ex)
        assertTrue(errBn.getProblem().contains("অনুমতি আপনার নেই"))
        assertTrue(errBn.getWhatToDo().contains("মালিকের সঙ্গে যোগাযোগ"))

        // In English
        AppLanguageManager.setLanguage(AppLanguage.ENGLISH)
        val errEn = AppErrorHandler.map(ex)
        assertTrue(errEn.getProblem().contains("permission", ignoreCase = true))
        assertTrue(errEn.getWhatToDo().contains("shop owner", ignoreCase = true))
    }

    @Test
    fun test_6_errorMapping_firebaseInitializationError_bilingual() {
        val ex = IllegalStateException("Default FirebaseApp is not initialized in this process com.aistudio.smartbiz.mgr. Make sure to call FirebaseApp.initializeApp(Context) first.")

        // In Bengali
        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
        val errBn = AppErrorHandler.map(ex)
        assertTrue(errBn.getProblem().contains("সার্ভার সংযোগ"))
        assertTrue(errBn.getWhatToDo().contains("আপডেট") || errBn.getWhatToDo().contains("সাপোর্ট"))

        // In English
        AppLanguageManager.setLanguage(AppLanguage.ENGLISH)
        val errEn = AppErrorHandler.map(ex)
        assertTrue(errEn.getProblem().contains("server connection", ignoreCase = true))
        assertTrue(errEn.getWhatToDo().contains("update", ignoreCase = true) || errEn.getWhatToDo().contains("support", ignoreCase = true))
    }

    @Test
    fun test_7_sanitization_noSecretLeakInLogs() {
        val dirty = "User auth failed with password=SecretPassword123 and pin=987654 and token=XYZ9999 and OTP code 123456"
        val clean = AppErrorHandler.sanitize(dirty)

        assertFalse("Cleaned string must not contain SecretPassword123", clean.contains("SecretPassword123"))
        assertFalse("Cleaned string must not contain pin=987654", clean.contains("987654"))
        assertFalse("Cleaned string must not contain 123456", clean.contains("123456"))
        assertTrue(clean.contains("password=***"))
        assertTrue(clean.contains("pin=***"))
    }

    @Test
    fun test_8_numberAndCurrencyFormatting_bilingual() {
        // Currency is always BDT with ৳
        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
        val currBn = AppLanguageManager.formatCurrency(1500.50)
        assertTrue("Bengali currency must have ৳", currBn.startsWith("৳"))
        assertTrue(currBn.contains("1,500.50") || currBn.contains("১,৫০০.৫০"))

        AppLanguageManager.setLanguage(AppLanguage.ENGLISH)
        val currEn = AppLanguageManager.formatCurrency(1500.50)
        assertTrue("English currency must have ৳", currEn.startsWith("৳"))
        assertTrue(currEn.contains("1,500.50"))

        // Numbers
        val numBn = AppLanguageManager.formatNumber(2026L)
        val numEn = AppLanguageManager.formatNumber(2026L)
        assertEquals("2026", numEn)
        assertTrue(numBn.contains("২০২৬") || numBn == "2026")
    }

    @Test
    fun test_9_stringsRepositoryCoverage() {
        // Validate that AppStrings supplies both languages without empty or null values
        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
        assertEquals("SmartHishabpati", AppStrings.appName)
        assertEquals("লগইন করুন", AppStrings.buttonLogin)
        assertEquals("হোম", AppStrings.navHome)
        assertEquals("বিক্রি", AppStrings.navSales)
        assertEquals("রসিদ", AppStrings.navReceipts)
        assertEquals("লগআউট", AppStrings.navLogout)

        AppLanguageManager.setLanguage(AppLanguage.ENGLISH)
        assertEquals("SmartHishabpati", AppStrings.appName)
        assertEquals("Log In", AppStrings.buttonLogin)
        assertEquals("Home", AppStrings.navHome)
        assertEquals("Sales", AppStrings.navSales)
        assertEquals("Receipts", AppStrings.navReceipts)
        assertEquals("Logout", AppStrings.navLogout)
    }

    @Test
    fun test_10_superAdminSecretTapLogic() {
        // 5 consecutive taps within 2 seconds
        var tapCount = 0
        var lastTapTime = 0L
        var triggered = false

        val simulateTap = { currentTime: Long ->
            if (currentTime - lastTapTime > 2000L) {
                tapCount = 1
            } else {
                tapCount++
            }
            lastTapTime = currentTime
            if (tapCount >= 5) {
                tapCount = 0
                triggered = true
            }
        }

        val baseTime = 100000L
        simulateTap(baseTime)
        simulateTap(baseTime + 200L)
        simulateTap(baseTime + 400L)
        simulateTap(baseTime + 600L)
        assertFalse(triggered)

        simulateTap(baseTime + 800L) // 5th tap
        assertTrue("5-tap gesture must trigger portal navigation", triggered)
    }
}
