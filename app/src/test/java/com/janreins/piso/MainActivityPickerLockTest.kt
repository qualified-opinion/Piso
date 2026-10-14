package com.janreins.piso

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ApplicationProvider
import com.janreins.piso.data.local.UserProfileManager
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Exercises MainActivity's actual picker launch/result hooks in Android's return order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityPickerLockTest {
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var manager: UserProfileManager
    private var requestCode = 0
    private var resultCallbacks = 0

    @Before
    fun setup() {
        manager = UserProfileManager.getInstance(ApplicationProvider.getApplicationContext())
        manager.pickerLockExemption.clear()
        manager.createProfile("Juan", "987654")
        controller = Robolectric.buildActivity(MainActivity::class.java)
            .create().start().resume().postResume()
        val activity = controller.get()
        val launcher = activity.activityResultRegistry.register(
            "picker-lock-test", ActivityResultContracts.GetContent()
        ) { resultCallbacks++ }
        manager.pickerLockExemption.launch { launcher.launch("application/json") }
        requestCode = shadowOf(activity).nextStartedActivityForResult.requestCode
        controller.pause().stop()
        assertFalse(manager.isLocked.value)
    }

    @After
    fun tearDown() {
        controller.close()
        manager.pickerLockExemption.clear()
        manager.clearProfile()
    }

    @Test
    fun startResultResumeKeepsUnlockedAndNextBackgroundLocks() {
        controller.restart().start()
        assertFalse(manager.isLocked.value)
        deliverResult(Activity.RESULT_OK, Intent().setData(Uri.parse("content://test/backup")))
        assertEquals(1, resultCallbacks)
        controller.resume().postResume()
        assertFalse(manager.isLocked.value)
        controller.pause().stop()
        assertTrue(manager.isLocked.value)
    }

    @Test
    fun cancelledPickerResultAlsoKeepsUnlocked() {
        controller.restart().start()
        deliverResult(Activity.RESULT_CANCELED)
        controller.resume().postResume()
        assertEquals(1, resultCallbacks)
        assertFalse(manager.isLocked.value)
    }

    @Test
    fun startResumeWithoutResultLocksBeforeResumeObserversAndLateResultCannotUnlock() {
        var lockedAtResume: Boolean? = null
        controller.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) lockedAtResume = manager.isLocked.value
        })
        controller.restart().start()
        assertFalse(manager.isLocked.value)
        controller.resume().postResume()
        assertEquals(true, lockedAtResume)
        assertTrue(manager.isLocked.value)
        deliverResult(Activity.RESULT_OK, Intent().setData(Uri.parse("content://test/backup")))
        assertEquals(1, resultCallbacks)
        assertTrue(manager.isLocked.value)
    }

    @Test
    fun expiredWindowLocksAtStartEvenWhenResultFollows() {
        ShadowSystemClock.advanceBy(Duration.ofMillis(60_001))
        controller.restart().start()
        assertTrue(manager.isLocked.value)
        deliverResult(Activity.RESULT_CANCELED)
        controller.resume().postResume()
        assertTrue(manager.isLocked.value)
    }

    @Test
    fun secondBackgroundBeforeResultLocksAndResultCannotUnlock() {
        controller.restart().start().resume().postResume()
        controller.pause().stop()
        assertTrue(manager.isLocked.value)
        controller.restart().start()
        deliverResult(Activity.RESULT_CANCELED)
        controller.resume().postResume()
        assertTrue(manager.isLocked.value)
    }

    private fun deliverResult(resultCode: Int, data: Intent? = null) {
        ReflectionHelpers.callInstanceMethod<Unit>(
            controller.get(), "onActivityResult",
            ClassParameter.from(Int::class.javaPrimitiveType, requestCode),
            ClassParameter.from(Int::class.javaPrimitiveType, resultCode),
            ClassParameter.from(Intent::class.java, data)
        )
    }
}
