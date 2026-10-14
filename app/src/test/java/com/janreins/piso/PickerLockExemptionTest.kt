package com.janreins.piso

import com.janreins.piso.data.local.PickerLockExemption
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerLockExemptionTest {
    private var elapsed = 1_000L
    private val exemption = PickerLockExemption { elapsed }

    private fun launchPicker() {
        exemption.launch { exemption.onRequestLaunched(42) }
    }

    @Test
    fun stopStartResultResumeWithinWindowKeepsUnlockedOnce() {
        launchPicker()
        assertFalse(exemption.shouldLockOnStop())
        elapsed += 60_000L
        assertFalse(exemption.shouldLockOnStart())
        exemption.onResultReturned(42)
        assertFalse(exemption.shouldLockOnResume())
        assertTrue(exemption.shouldLockOnStop())
        assertTrue(exemption.shouldLockOnStart())
        assertFalse(exemption.shouldLockOnResume())
    }

    @Test
    fun expiredWindowLocksAtStartAndResultCannotReviveExemption() {
        launchPicker()
        assertFalse(exemption.shouldLockOnStop())
        elapsed += 60_001L
        assertTrue(exemption.shouldLockOnStart())
        exemption.onResultReturned(42)
        assertFalse(exemption.shouldLockOnResume())
        assertTrue(exemption.shouldLockOnStop())
    }

    @Test
    fun failedLaunchClearsExemption() {
        assertThrows(IllegalStateException::class.java) {
            exemption.launch {
                exemption.onRequestLaunched(42)
                throw IllegalStateException("No picker")
            }
        }
        exemption.onResultReturned(42)
        assertTrue(exemption.shouldLockOnStop())
        assertTrue(exemption.shouldLockOnStart())
        assertFalse(exemption.shouldLockOnResume())
    }

    @Test
    fun homeFromPickerThenStartResumeWithoutResultLocks() {
        launchPicker()
        assertFalse(exemption.shouldLockOnStop())
        elapsed += 1_000L
        assertFalse(exemption.shouldLockOnStart())
        assertTrue(exemption.shouldLockOnResume())
    }

    @Test
    fun lateResultAfterResumeDoesNothing() {
        launchPicker()
        assertFalse(exemption.shouldLockOnStop())
        assertFalse(exemption.shouldLockOnStart())
        assertTrue(exemption.shouldLockOnResume())
        exemption.onResultReturned(42) // A late callback cannot revive the exemption.
        assertFalse(exemption.shouldLockOnResume())
        assertTrue(exemption.shouldLockOnStop())
        assertTrue(exemption.shouldLockOnStart())
    }

    @Test
    fun unrelatedResultDoesNotExemptReturn() {
        launchPicker()
        assertFalse(exemption.shouldLockOnStop())
        assertFalse(exemption.shouldLockOnStart())
        exemption.onResultReturned(99)
        assertTrue(exemption.shouldLockOnResume())
    }

    @Test
    fun secondBackgroundBeforeReturnLocks() {
        launchPicker()
        assertFalse(exemption.shouldLockOnStop())
        assertTrue(exemption.shouldLockOnStop())
        assertTrue(exemption.shouldLockOnStart())
        exemption.onResultReturned(42)
        assertFalse(exemption.shouldLockOnResume())
    }

    @Test
    fun resultBeforeAnyBackgroundDoesNotExemptNextBackground() {
        launchPicker()
        exemption.onResultReturned(42)
        assertTrue(exemption.shouldLockOnStop())
    }

    @Test
    fun startWithoutBackgroundDoesNotLock() {
        assertFalse(exemption.shouldLockOnStart())
        assertFalse(exemption.shouldLockOnResume())
    }

    @Test
    fun windowExpiringBetweenStartAndResumeLocks() {
        launchPicker()
        assertFalse(exemption.shouldLockOnStop())
        elapsed += 60_000L
        assertFalse(exemption.shouldLockOnStart())
        exemption.onResultReturned(42)
        elapsed++
        assertTrue(exemption.shouldLockOnResume())
    }

    @Test
    fun secondBackgroundWhileAwaitingResultLocksAndConsumesExemption() {
        launchPicker()
        assertFalse(exemption.shouldLockOnStop())
        assertFalse(exemption.shouldLockOnStart())
        assertTrue(exemption.shouldLockOnStop())
        assertTrue(exemption.shouldLockOnStart())
        exemption.onResultReturned(42)
        assertFalse(exemption.shouldLockOnResume())
    }

    @Test
    fun launchWithoutRequestCannotExemptBackground() {
        exemption.launch {}
        assertTrue(exemption.shouldLockOnStop())
        assertTrue(exemption.shouldLockOnStart())
        assertFalse(exemption.shouldLockOnResume())
    }

    @Test
    fun resultBeforeStartAfterBackgroundAlsoExemptsResume() {
        launchPicker()
        assertFalse(exemption.shouldLockOnStop())
        exemption.onResultReturned(42)
        assertFalse(exemption.shouldLockOnStart())
        assertFalse(exemption.shouldLockOnResume())
        assertTrue(exemption.shouldLockOnStop())
    }
}
