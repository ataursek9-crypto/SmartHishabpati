package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.backend.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HybridBackendArchitectureTest {

    @Test
    fun testSupabaseConfig_rejectsServiceRoleKey() {
        // Enforce strict rule: service_role key must NEVER be allowed in client code
        try {
            SupabaseConfig.setCredentials(
                url = "https://example.supabase.co",
                anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.service_role.fake_secret"
            )
            fail("Expected SecurityException when injecting service_role key")
        } catch (e: SecurityException) {
            assertTrue("Exception message indicates service role forbidden", e.message?.contains("Service Role") == true)
        }
    }

    @Test
    fun testSupabaseConfig_phoneFormattingE164() {
        assertEquals("+8801711223344", SupabaseConfig.formatToE164("01711223344"))
        assertEquals("+8801811223344", SupabaseConfig.formatToE164("8801811223344"))
        assertEquals("+8801911223344", SupabaseConfig.formatToE164("+8801911223344"))
    }

    @Test
    fun testSupabaseAuthClient_unverifiedPhoneConstraint() = runTest {
        // Rule 4: Phone without SMS confirmation MUST NOT be reported as verified
        val client = SupabaseAuthClient()
        val result = client.signUpWithPhone("01711223344", "test_pin_1234")

        assertTrue("Must report unverified or unconfigured",
            result is BackendAuthResult.UnverifiedPhone || result is BackendAuthResult.Error)

        if (result is BackendAuthResult.UnverifiedPhone) {
            assertEquals("+8801711223344", result.phone)
            assertTrue("Message informs user about SMS/verification requirement",
                result.message.contains("যাচাই") || result.message.contains("ভেরিফাইড নয়"))
        }
    }

    @Test
    fun testSessionManager_stableDeviceIdGeneration() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val sessionManager = FirestoreSessionManager(app)

        val devId1 = sessionManager.getLocalDeviceId()
        val devId2 = sessionManager.getLocalDeviceId()

        assertNotNull(devId1)
        assertTrue(devId1.startsWith("dev_"))
        assertEquals("Device ID must be stable and cached per app installation", devId1, devId2)
    }

    @Test
    fun testBackendModels_userProviderIntegrity() {
        val user = BackendUser(
            id = "user_fb_123",
            email = "owner@gmail.com",
            phone = "01711223344",
            isEmailVerified = true,
            isPhoneVerified = false,
            primaryProvider = AuthProviderType.FIREBASE,
            shopId = "biz_001",
            role = "ADMIN"
        )

        assertEquals("user_fb_123", user.id)
        assertEquals(AuthProviderType.FIREBASE, user.primaryProvider)
        assertFalse("Phone unverified without SMS", user.isPhoneVerified)
        assertTrue("Email verified", user.isEmailVerified)
    }
}
