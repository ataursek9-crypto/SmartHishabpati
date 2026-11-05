package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.storage.SavedAccount
import com.example.data.storage.SavedAccountManager
import com.example.viewmodel.BusinessViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RememberMePermanentFixTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        // Reset SharedPreferences
        val prefs = app.getSharedPreferences("smart_biz_saved_accounts_prefs", Application.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_1_savedAccountSecurity_noPasswordsOrPins() {
        // Ensure SavedAccount structure contains zero sensitive credential fields
        val fields = SavedAccount::class.java.declaredFields.map { it.name }
        assertFalse("SavedAccount must NOT store password", fields.contains("password"))
        assertFalse("SavedAccount must NOT store passwordHash", fields.contains("passwordHash"))
        assertFalse("SavedAccount must NOT store pin", fields.contains("pin"))
        assertFalse("SavedAccount must NOT store pinHash", fields.contains("pinHash"))
        assertFalse("SavedAccount must NOT store secret", fields.contains("secret"))

        // Check masked phone
        val acc = SavedAccount(phone = "01812345678")
        assertEquals("018****5678", acc.maskedPhone)
    }

    @Test
    fun test_2_gmailLogin_withRememberMeOn_andOff() {
        val manager = SavedAccountManager(app)

        // 1. Remember Me ON -> save email
        val testEmail = "merchant@hishabpati.com"
        manager.saveEmail(testEmail)

        assertEquals("merchant@hishabpati.com", manager.getRememberedEmail())
        assertEquals("merchant@hishabpati.com", manager.rememberedEmail.value)
        assertTrue(manager.isRememberMeEnabled())
        assertTrue(manager.rememberMeState.value)

        // 2. Simulate App Restart (new instance from disk)
        val restartedManager = SavedAccountManager(app)
        assertEquals("merchant@hishabpati.com", restartedManager.getRememberedEmail())
        assertEquals("merchant@hishabpati.com", restartedManager.rememberedEmail.value)
        assertTrue(restartedManager.isRememberMeEnabled())

        // 3. Remember Me OFF -> clear email
        manager.setRememberMe(false)
        assertEquals("", manager.getRememberedEmail())
        assertEquals("", manager.rememberedEmail.value)
        assertFalse(manager.isRememberMeEnabled())

        // 4. Simulate App Restart after unchecking Remember Me
        val restartedManagerOff = SavedAccountManager(app)
        assertEquals("", restartedManagerOff.getRememberedEmail())
        assertFalse(restartedManagerOff.isRememberMeEnabled())
    }

    @Test
    fun test_3_phoneLogin_withRememberMeOn_andOff() {
        val manager = SavedAccountManager(app)

        // 1. Remember Me ON -> save phone
        manager.saveAccount("01812345678")
        assertEquals(1, manager.savedAccounts.value.size)
        assertEquals("01812345678", manager.savedAccounts.value.first().phone)

        // 2. Simulate App Restart
        val restartedManager = SavedAccountManager(app)
        assertEquals(1, restartedManager.savedAccounts.value.size)
        assertEquals("01812345678", restartedManager.savedAccounts.value.first().phone)

        // 3. Remember Me OFF -> remove phone
        manager.removeAccount("01812345678")
        assertTrue(manager.savedAccounts.value.none { it.phone == "01812345678" })

        // 4. Simulate App Restart
        val restartedManagerRemoved = SavedAccountManager(app)
        assertTrue(restartedManagerRemoved.savedAccounts.value.none { it.phone == "01812345678" })
    }

    @Test
    fun test_4_logoutBehavior_separateFromRememberedEmail() {
        val manager = SavedAccountManager(app)
        manager.saveEmail("user@example.com")
        manager.saveAccount("01700000000")

        // User logs out with keepOnLogout = true
        manager.setKeepOnLogout(true)
        manager.handleLogout()

        // Remembered email and accounts are preserved across logout for convenience
        assertEquals("user@example.com", manager.getRememberedEmail())
        assertEquals(1, manager.savedAccounts.value.size)

        // If user set keepOnLogout = false and logs out, phone accounts wipe but email preference stays decoupled
        manager.setKeepOnLogout(false)
        manager.handleLogout()
        assertEquals(0, manager.savedAccounts.value.size)
        assertEquals("user@example.com", manager.getRememberedEmail())
    }

    @Test
    fun test_5_viewModel_integration() = runTest {
        val vm = BusinessViewModel(app)

        // Save email via ViewModel
        vm.saveRememberedEmail("test@smartbiz.com")
        assertEquals("test@smartbiz.com", vm.rememberedEmail.value)

        // Clear email via ViewModel
        vm.clearRememberedEmail()
        assertEquals("", vm.rememberedEmail.value)

        // Set preference via ViewModel
        vm.setRememberMePreference(false)
        assertFalse(vm.rememberMeState.value)
        vm.setRememberMePreference(true)
        assertTrue(vm.rememberMeState.value)
    }
}
