package com.janreins.piso.data.local

/** A picker may defer locking only until its first return to the foreground. */
class PickerLockExemption(private val elapsedRealtime: () -> Long) {
    private var startedAt: Long? = null
    private var expectedRequestCode: Int? = null
    private var resultReturned = false
    private var backgrounded = false
    private var awaitingResult = false

    fun launch(launchPicker: () -> Unit) {
        clear()
        startedAt = elapsedRealtime()
        try {
            launchPicker()
        } catch (e: RuntimeException) {
            clear()
            throw e
        }
    }

    fun onRequestLaunched(requestCode: Int) {
        if (startedAt != null) expectedRequestCode = requestCode
    }

    fun onResultReturned(requestCode: Int) {
        if (expectedRequestCode == requestCode) {
            expectedRequestCode = null
            resultReturned = true
        }
    }

    fun shouldLockOnStop(): Boolean {
        val deferLock = !backgrounded && expectedRequestCode != null && withinWindow()
        if (!deferLock) clear()
        backgrounded = true
        return !deferLock
    }

    fun shouldLockOnStart(): Boolean {
        // Android delivers the activity result between onStart and onResume.
        awaitingResult = backgrounded && (expectedRequestCode != null || resultReturned) && withinWindow()
        if (awaitingResult) return false
        val shouldLock = backgrounded
        clear()
        return shouldLock
    }

    fun shouldLockOnResume(): Boolean {
        val shouldLock = awaitingResult && (!resultReturned || !withinWindow())
        clear() // Home/Recents consumes the exemption too; a late result cannot unlock Piso.
        return shouldLock
    }

    fun clear() {
        startedAt = null
        expectedRequestCode = null
        resultReturned = false
        backgrounded = false
        awaitingResult = false
    }

    private fun withinWindow(): Boolean {
        val start = startedAt ?: return false
        return elapsedRealtime() - start in 0L..60_000L
    }
}
