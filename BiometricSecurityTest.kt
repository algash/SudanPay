package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.security.BiometricSecurityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BiometricSecurityTest {

    private lateinit var context: Context
    private lateinit var securityManager: BiometricSecurityManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear shared prefs
        context.getSharedPreferences("sudan_pay_biometric_security_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        securityManager = BiometricSecurityManager.getInstance(context)
        securityManager.isBiometricEnabled = true
        securityManager.isAppLocked = true
    }

    @Test
    fun testInitialSecurityState() {
        assertTrue("Biometric should be enabled by default", securityManager.isBiometricEnabled)
        assertTrue("App should be locked initially", securityManager.isAppLocked)
        assertTrue("Transfer auth should be required by default", securityManager.requireAuthOnTransfer)
    }

    @Test
    fun testUnlockAndLock() {
        securityManager.isAppLocked = false
        assertFalse("App should be unlocked after successful authentication", securityManager.isAppLocked)

        securityManager.isAppLocked = true
        assertTrue("App should be locked when requested", securityManager.isAppLocked)
    }

    @Test
    fun testDisableBiometrics() {
        securityManager.isBiometricEnabled = false
        assertFalse("Biometric should be marked disabled", securityManager.isBiometricEnabled)
        assertFalse("When biometric is disabled, app should not lock on resume", securityManager.shouldLockOnResume())
    }

    @Test
    fun testTransferAuthPreference() {
        securityManager.requireAuthOnTransfer = false
        assertFalse(securityManager.requireAuthOnTransfer)

        securityManager.requireAuthOnTransfer = true
        assertTrue(securityManager.requireAuthOnTransfer)
    }

    @Test
    fun testBackgroundAutoLockBehavior() {
        securityManager.isBiometricEnabled = true
        securityManager.isAppLocked = false

        // Simulate app backgrounding
        securityManager.onAppBackgrounded()

        // Verify should lock on resume
        assertTrue("App should require lock on resume after backgrounding", securityManager.shouldLockOnResume())
    }
}
