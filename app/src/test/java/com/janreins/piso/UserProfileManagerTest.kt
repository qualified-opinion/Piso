package com.janreins.piso

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.janreins.piso.data.local.ThemeMode
import com.janreins.piso.data.local.UserProfileManager
import com.janreins.piso.data.local.isWeakPin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserProfileManagerTest {

    private lateinit var context: Context
    private lateinit var manager: UserProfileManager
    private var elapsed = 100_000L
    private var boot = 7

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        manager = UserProfileManager(context, { elapsed }, { boot })
        manager.clearProfile()
    }

    @Test
    fun testCreateProfileWithoutPin() {
        manager.createProfile("Maria", null)
        assertTrue(manager.hasProfile())
        assertFalse(manager.hasPin())
        assertEquals("Maria", manager.userProfile.value.displayName)
        assertFalse(manager.isLocked.value)
    }

    @Test
    fun testCreateProfileWithPin() {
        manager.createProfile("Juan", "987654")
        assertTrue(manager.hasProfile())
        assertTrue(manager.hasPin())
        assertEquals("Juan", manager.userProfile.value.displayName)

        // Verify correct and incorrect PINs
        assertTrue(manager.verifyPin("987654"))
        assertFalse(manager.verifyPin("000000"))
        assertFalse(manager.verifyPin("123"))

        // Lock & Unlock
        manager.lock()
        assertTrue(manager.isLocked.value)

        val unlocked = manager.unlock("987654")
        assertTrue(unlocked)
        assertFalse(manager.isLocked.value)
    }

    @Test
    fun testWeakPinDetection() {
        assertTrue(isWeakPin("000000"))
        assertTrue(isWeakPin("111111"))
        assertTrue(isWeakPin("123456"))
        assertTrue(isWeakPin("121212"))
        assertTrue(isWeakPin("654321"))

        assertFalse(isWeakPin("482910"))
        assertFalse(isWeakPin("739182"))
        assertFalse(isWeakPin("999999"))
    }

    @Test
    fun testLockoutAfterFiveFailedAttemptsAndPersistence() {
        manager.createProfile("Juan", "654987")
        assertEquals(0, manager.getLockoutRemainingSeconds())

        // 4 failed attempts: not locked out yet
        for (i in 1..4) {
            assertFalse(manager.unlock("000000"))
            assertEquals(0, manager.getLockoutRemainingSeconds())
        }

        // 5th failed attempt: triggers 30 second lockout
        assertFalse(manager.unlock("000000"))
        val remaining = manager.getLockoutRemainingSeconds()
        assertTrue("Expected remaining seconds between 1 and 30, got $remaining", remaining in 1..30)

        // Even correct PIN fails during lockout window
        assertFalse(manager.unlock("654987"))

        // Recreate manager (simulating activity rebuild / phone rotation)
        val managerRebuilt = UserProfileManager(context, { elapsed }, { boot })
        val rebuiltRemaining = managerRebuilt.getLockoutRemainingSeconds()
        assertTrue("Expected rebuilt lockout to still be active", rebuiltRemaining in 1..30)
    }

    @Test
    fun testChangeAndRemovePin() {
        manager.createProfile("Juan", "654322")

        val wrongChange = manager.changePin("999999", "567890")
        assertFalse(wrongChange)
        assertTrue(manager.verifyPin("654322"))

        val rightChange = manager.changePin("654322", "567890")
        assertTrue(rightChange)
        assertFalse(manager.verifyPin("654322"))
        assertTrue(manager.verifyPin("567890"))

        val removed = manager.removePin("567890")
        assertTrue(removed)
        assertFalse(manager.hasPin())
    }

    @Test
    fun testThemeModePersistence() {
        manager.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, manager.userProfile.value.themeMode)
    }

    @Test
    @Config(instrumentedPackages = ["com.janreins.piso"])
    @LooperMode(LooperMode.Mode.LEGACY)
    fun lockoutIgnoresWallClockChangesAndEndsWithElapsedTime() {
        manager.createProfile("Juan", "654987")
        repeat(5) { assertFalse(manager.unlock("000000")) }
        assertEquals(30, manager.getLockoutRemainingSeconds())
        // Legacy mode allows wall-clock movement in both directions independently of our clock.
        val wallClock = WallClockReader.now()
        ShadowSystemClock.setNanoTime((wallClock + 86_400_000L) * 1_000_000L)
        assertEquals(wallClock + 86_400_000L, WallClockReader.now())
        assertEquals(30, manager.getLockoutRemainingSeconds())
        ShadowSystemClock.setNanoTime((wallClock - 86_400_000L) * 1_000_000L)
        assertEquals(wallClock - 86_400_000L, WallClockReader.now())
        assertEquals(30, manager.getLockoutRemainingSeconds())
        elapsed += 29_999L
        assertEquals(1, manager.getLockoutRemainingSeconds())
        assertFalse(manager.unlock("654987"))
        elapsed += 1L
        assertEquals(0, manager.getLockoutRemainingSeconds())
        assertTrue(manager.unlock("654987"))
    }

    @Test
    fun rebootRestartsCooldownAndPersistsNewDeadline() {
        manager.createProfile("Juan", "654987")
        repeat(5) { manager.unlock("000000") }
        elapsed += 20_000L
        assertEquals(10, manager.getLockoutRemainingSeconds())
        boot++
        elapsed = 2_000L
        manager = UserProfileManager(context, { elapsed }, { boot })
        assertEquals(30, manager.getLockoutRemainingSeconds())
        elapsed += 10_000L
        manager = UserProfileManager(context, { elapsed }, { boot })
        assertEquals(20, manager.getLockoutRemainingSeconds())
        elapsed += 20_000L
        assertTrue(manager.unlock("654987"))
    }

    @Test
    fun legacyWallClockLockoutRestartsOnceOnElapsedClock() {
        manager.createProfile("Juan", "654987")
        context.getSharedPreferences("piso_user_profile", Context.MODE_PRIVATE).edit()
            .putInt("failed_attempts", 5)
            .putLong("lockout_until", Long.MAX_VALUE)
            .apply()
        assertEquals(30, manager.getLockoutRemainingSeconds())
        elapsed += 30_000L
        assertTrue(manager.unlock("654987"))
    }

    @Test
    fun changePinFailuresTriggerLockoutAndRejectCorrectPin() {
        manager.createProfile("Juan", "654987")
        repeat(5) { assertFalse(manager.changePin("000000", "987654")) }
        assertEquals(30, manager.getLockoutRemainingSeconds())
        assertFalse(manager.changePin("654987", "987654"))
        elapsed += 30_000L
        assertTrue(manager.verifyPin("654987")) // Original PIN was not changed.
        assertTrue(manager.changePin("654987", "987654"))
    }

    @Test
    fun removePinFailuresTriggerLockoutAndRejectCorrectPin() {
        manager.createProfile("Juan", "654987")
        repeat(5) { assertFalse(manager.removePin("000000")) }
        assertEquals(30, manager.getLockoutRemainingSeconds())
        assertFalse(manager.removePin("654987"))
        assertTrue(manager.hasPin())
        elapsed += 30_000L
        assertTrue(manager.removePin("654987"))
    }

    @Test
    fun settingsVerificationChangeRemoveAndUnlockShareAttemptCounter() {
        manager.createProfile("Juan", "654987")
        assertFalse(manager.verifyPin("000000")) // Change PIN's initial dialog check.
        assertFalse(manager.changePin("000000", "987654"))
        assertFalse(manager.removePin("000000"))
        assertFalse(manager.unlock("000000"))
        assertEquals(0, manager.getLockoutRemainingSeconds())
        assertFalse(manager.verifyPin("000000"))
        assertEquals(30, manager.getLockoutRemainingSeconds())
        assertFalse(manager.verifyPin("654987"))
        assertFalse(manager.unlock("654987"))
        assertFalse(manager.changePin("654987", "987654"))
        assertFalse(manager.removePin("654987"))
        elapsed += 30_000L
        assertTrue(manager.verifyPin("654987"))
        repeat(4) { assertFalse(manager.removePin("000000")) }
        assertEquals(0, manager.getLockoutRemainingSeconds()) // Success reset all failures.
    }

    @Test
    fun blockedChecksDoNotExtendOrEscalateCooldown() {
        manager.createProfile("Juan", "654987")
        repeat(5) { manager.verifyPin("000000") }
        elapsed += 10_000L
        repeat(10) { assertFalse(manager.verifyPin("000000")) }
        assertEquals(20, manager.getLockoutRemainingSeconds())
        elapsed += 20_000L
        repeat(4) {
            assertFalse(manager.verifyPin("000000"))
            assertEquals(30, manager.getLockoutRemainingSeconds())
            elapsed += 30_000L
        }
        assertFalse(manager.verifyPin("000000")) // Tenth actual failure.
        assertEquals(300, manager.getLockoutRemainingSeconds())
        boot++
        elapsed = 0L
        assertEquals(300, manager.getLockoutRemainingSeconds())
    }
}

// Robolectric excludes the JUnit class from instrumentation; use an instrumented caller
// so System.currentTimeMillis() sees the adjustable test wall clock, as app code does.
private object WallClockReader {
    fun now(): Long = System.currentTimeMillis()
}
