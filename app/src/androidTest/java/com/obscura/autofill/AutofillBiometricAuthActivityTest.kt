package com.obscura.autofill

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The autofill prompt is portrait-only and translucent. On Android 8.0 that combination makes
 * Activity.onCreate throw "Only fullscreen opaque activities can request orientation", so the
 * window is opaque there (values-v26 / values-v27). Run this on an API 26 device as well.
 */
@RunWith(AndroidJUnit4::class)
class AutofillBiometricAuthActivityTest {

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun intent(autoPrompt: Boolean) = AutofillBiometricAuthActivity.createIntent(
        context = context,
        entryId = "entry",
        entryTitle = "Example",
        username = "user@example.com",
        secretValue = "not-a-real-secret",
        autoPrompt = autoPrompt
    )

    @Test
    fun startsPortraitOnlyWithoutCrashing() {
        ActivityScenario.launch<AutofillBiometricAuthActivity>(intent(autoPrompt = false)).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.requestedOrientation)
            }
        }
    }

    @Test
    fun windowIsOpaqueOnlyOnApi26() {
        ActivityScenario.launch<AutofillBiometricAuthActivity>(intent(autoPrompt = false)).use { scenario ->
            scenario.onActivity { activity ->
                val attrs = activity.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsTranslucent))
                val translucent = try {
                    attrs.getBoolean(0, false)
                } finally {
                    attrs.recycle()
                }
                assertEquals(
                    "windowIsTranslucent on API ${Build.VERSION.SDK_INT}",
                    Build.VERSION.SDK_INT != Build.VERSION_CODES.O,
                    translucent
                )
            }
        }
    }

    /** The real launch path: the biometric prompt is requested straight from the first composition. */
    @Test
    fun startsWithBiometricPromptWithoutCrashing() {
        ActivityScenario.launch<AutofillBiometricAuthActivity>(intent(autoPrompt = true)).use { scenario ->
            // Give the prompt time to show or to fail (no enrolled biometrics or lock screen on the emulator).
            val until = SystemClock.uptimeMillis() + TIMEOUT_MS / 2
            while (SystemClock.uptimeMillis() < until) {
                SystemClock.sleep(100)
            }
            val state = scenario.state
            assertTrue(
                "unexpected state $state",
                state == Lifecycle.State.RESUMED || state == Lifecycle.State.STARTED ||
                    state == Lifecycle.State.CREATED || state == Lifecycle.State.DESTROYED
            )
        }
    }
}
