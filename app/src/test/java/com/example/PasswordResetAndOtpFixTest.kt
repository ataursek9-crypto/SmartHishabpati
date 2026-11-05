package com.example

import com.example.util.AppErrorHandler
import com.example.util.AppLanguage
import com.example.util.AppLanguageManager
import com.example.util.AppStrings
import com.example.util.PhoneAuthHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordResetAndOtpFixTest {

    @Test
    fun testBangladeshPhoneNumberFormattingAndE164() {
        // Bengali digits conversion to E.164
        val rawBengali = "০১৭১১২২৩৩৪৪"
        val formatted = PhoneAuthHelper.normalizeToE164(rawBengali)
        assertEquals("+8801711223344", formatted)
        assertTrue(PhoneAuthHelper.isValidBangladeshPhone(formatted))

        // Standard 11-digit local format (018...)
        val local = "01812345678"
        val formattedLocal = PhoneAuthHelper.normalizeToE164(local)
        assertEquals("+8801812345678", formattedLocal)
        assertTrue(PhoneAuthHelper.isValidBangladeshPhone(formattedLocal))

        // Already with 880 without plus
        val withoutPlus = "8801912345678"
        val formattedWithoutPlus = PhoneAuthHelper.normalizeToE164(withoutPlus)
        assertEquals("+8801912345678", formattedWithoutPlus)
        assertTrue(PhoneAuthHelper.isValidBangladeshPhone(formattedWithoutPlus))

        // Invalid numbers
        assertFalse(PhoneAuthHelper.isValidBangladeshPhone("0123456789"))
        assertFalse(PhoneAuthHelper.isValidBangladeshPhone("12345"))
        assertFalse(PhoneAuthHelper.isValidBangladeshPhone("abcd"))
    }

    @Test
    fun testPhoneOtpQuotaCooldownProtection() {
        val testPhone = "01755667788"
        val e164 = PhoneAuthHelper.normalizeToE164(testPhone)

        // Initially no cooldown
        assertEquals(0L, PhoneAuthHelper.getRemainingCooldownSeconds(e164))

        // Trigger cooldown manually to test protection logic
        val fieldTimestamp = PhoneAuthHelper::class.java.getDeclaredField("lastOtpSentTimestamp")
        fieldTimestamp.isAccessible = true
        fieldTimestamp.set(PhoneAuthHelper, System.currentTimeMillis())

        val fieldPhone = PhoneAuthHelper::class.java.getDeclaredField("lastSentPhone")
        fieldPhone.isAccessible = true
        fieldPhone.set(PhoneAuthHelper, e164)

        val cooldown = PhoneAuthHelper.getRemainingCooldownSeconds(e164)
        assertTrue("Cooldown should be greater than 0 immediately after sending", cooldown > 0)
        assertTrue("Cooldown should not exceed 60 seconds", cooldown <= 60L)

        // Different phone number should not be affected by the cooldown of the first phone
        assertEquals(0L, PhoneAuthHelper.getRemainingCooldownSeconds("+8801899001122"))
    }

    @Test
    fun testPasswordResetAcceptedBilingualStrings() {
        // Bengali (default)
        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
        assertEquals(
            "পাসওয়ার্ড পরিবর্তনের লিংক আপনার ইমেইলে পাঠানোর অনুরোধ গ্রহণ করা হয়েছে। ইনবক্স ও Spam/Junk ফোল্ডার পরীক্ষা করুন।",
            AppStrings.passwordResetRequestAccepted
        )

        // English
        AppLanguageManager.setLanguage(AppLanguage.ENGLISH)
        assertEquals(
            "Your password reset request has been accepted. Please check your inbox and Spam/Junk folder.",
            AppStrings.passwordResetRequestAccepted
        )

        // Reset to Bengali
        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
    }

    @Test
    fun testAppErrorHandlerBilingualMapping() {
        val invalidPassEx = Exception("wrong password")

        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
        val bnError = AppErrorHandler.map(invalidPassEx)
        assertTrue(bnError.toUserMessage().contains("পাসওয়ার্ড সঠিক নয়"))

        AppLanguageManager.setLanguage(AppLanguage.ENGLISH)
        val enError = AppErrorHandler.map(invalidPassEx)
        assertTrue(enError.toUserMessage().contains("The password is incorrect"))

        // Reset to Bengali
        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
    }

    @Test
    fun testSanitizationHidesPasswordsAndOtpInLogs() {
        val logWithSecret = "User entered password=mySecretPassword123 and received token=xyzTokenSecret with otp 123456"
        val sanitized = AppErrorHandler.sanitize(logWithSecret)
        assertFalse(sanitized.contains("mySecretPassword123"))
        assertFalse(sanitized.contains("xyzTokenSecret"))
        assertFalse(sanitized.contains("123456"))
        assertTrue(sanitized.contains("password=***"))
        assertTrue(sanitized.contains("token=***"))
        assertTrue(sanitized.contains("******"))
    }
}
