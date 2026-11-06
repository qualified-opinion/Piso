package com.janreins.piso

import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import androidx.test.core.app.ApplicationProvider
import com.janreins.piso.data.local.UserProfileManager
import com.janreins.piso.util.FingerprintAuth
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
class FingerprintUnlockTest {

    private lateinit var context: Context
    private lateinit var manager: UserProfileManager
    private var elapsed = 100_000L
    private val boot = 3

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        manager = UserProfileManager(context, { elapsed }, { boot })
        manager.clearProfile()
    }

    @Test
    fun cannotTurnOnWithoutAPin() {
        manager.createProfile("Jan", null)

        assertFalse(manager.setFingerprintUnlock(true))
        assertFalse(manager.isFingerprintUnlockOn())
        assertFalse(manager.userProfile.value.fingerprintUnlock)
    }

    @Test
    fun turnedOnItShowsInTheProfileAndSurvivesARestart() {
        manager.createProfile("Jan", "482916")

        assertTrue(manager.setFingerprintUnlock(true))

        assertTrue(manager.userProfile.value.fingerprintUnlock)
        val reopened = UserProfileManager(context, { elapsed }, { boot })
        assertTrue(reopened.isFingerprintUnlockOn())
        assertTrue(reopened.userProfile.value.fingerprintUnlock)
    }

    @Test
    fun fingerprintCannotOpenTheLockScreenWhenItIsOff() {
        manager.createProfile("Jan", "482916")
        manager.lock()

        assertFalse(manager.unlockWithFingerprint())
        assertTrue(manager.isLocked.value)
    }

    @Test
    fun fingerprintOpensTheLockScreenAndClearsWrongPins() {
        manager.createProfile("Jan", "482916")
        manager.setFingerprintUnlock(true)
        manager.lock()
        repeat(5) { manager.unlock("000000") }
        assertTrue(manager.getLockoutRemainingSeconds() > 0)

        assertTrue(manager.unlockWithFingerprint())

        assertFalse(manager.isLocked.value)
        assertEquals(0, manager.failedAttempts())
        assertEquals(0, manager.getLockoutRemainingSeconds())
        // The PIN still works afterwards.
        manager.lock()
        assertTrue(manager.unlock("482916"))
    }

    @Test
    fun turningItOffMeansThePinIsNeededAgain() {
        manager.createProfile("Jan", "482916")
        manager.setFingerprintUnlock(true)

        assertTrue(manager.setFingerprintUnlock(false))
        manager.lock()

        assertFalse(manager.unlockWithFingerprint())
        assertTrue(manager.isLocked.value)
    }

    @Test
    fun removingThePinTurnsFingerprintOffAndANewPinStartsWithItOff() {
        manager.createProfile("Jan", "482916")
        manager.setFingerprintUnlock(true)

        assertTrue(manager.removePin("482916"))
        assertFalse(manager.userProfile.value.fingerprintUnlock)

        manager.setPin("739105")
        assertFalse(manager.isFingerprintUnlockOn())
        manager.lock()
        assertFalse(manager.unlockWithFingerprint())
    }

    @Test
    fun aWrongPinWhenRemovingKeepsFingerprintOn() {
        manager.createProfile("Jan", "482916")
        manager.setFingerprintUnlock(true)

        assertFalse(manager.removePin("111111"))

        assertTrue(manager.isFingerprintUnlockOn())
    }

    @Test
    fun changingThePinKeepsFingerprintOn() {
        manager.createProfile("Jan", "482916")
        manager.setFingerprintUnlock(true)

        assertTrue(manager.changePin("482916", "739105"))

        assertTrue(manager.isFingerprintUnlockOn())
    }

    @Test
    fun resetAndNewProfileStartWithFingerprintOff() {
        manager.createProfile("Jan", "482916")
        manager.setFingerprintUnlock(true)

        manager.clearProfile()
        assertFalse(manager.isFingerprintUnlockOn())

        manager.createProfile("Jan", "482916")
        assertFalse(manager.isFingerprintUnlockOn())
        assertFalse(manager.userProfile.value.fingerprintUnlock)
    }

    @Test
    fun closingThePromptIsNotAnError() {
        assertTrue(FingerprintAuth.isCancel(BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED))
        assertTrue(FingerprintAuth.isCancel(FingerprintAuth.ERROR_NEGATIVE_BUTTON))
        assertTrue(FingerprintAuth.isCancel(BiometricPrompt.BIOMETRIC_ERROR_CANCELED))
        assertFalse(FingerprintAuth.isCancel(BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT))
        assertTrue(FingerprintAuth.isKeyLost(BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS))
        assertFalse(FingerprintAuth.isKeyLost(BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT))
    }

    @Test
    fun removingTheKeyIsSafeWhereThereIsNoKeyStore() {
        FingerprintAuth.deleteKey()
        assertFalse(FingerprintAuth.canUse(context))
    }
}
