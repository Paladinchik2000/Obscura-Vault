package com.obscura.autofill

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.obscura.autofill.match.CallerIdentity
import com.obscura.data.Preferences
import com.obscura.data.local.EntryLink
import com.obscura.data.local.LinkType
import com.obscura.data.local.VaultDatabase
import com.obscura.data.local.VaultEntity
import com.obscura.data.model.VaultCategory
import com.obscura.security.AuthRepository
import com.obscura.security.KeystoreCrypto
import com.obscura.security.VaultSession
import com.obscura.ui.settings.AutoLockOption
import com.obscura.ui.settings.AutoLockSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Autofill through the real system UI: our service answers, the system shows the suggestions and
 * the form in another app ends up filled.
 *
 * The form lives in the test package, so the service sees it as an unrelated app — which is what
 * makes the "not linked" case meaningful.
 */
@RunWith(AndroidJUnit4::class)
class AutofillEndToEndTest {

    private companion object {
        const val PIN = "123456"
        const val USERNAME = "octocat"
        const val PASSWORD = "s3cret-Passw0rd!"
        const val ENTRY_TITLE = "GitHub test"
        const val TEST_PACKAGE = "com.obscura.test"
        const val SERVICE = "com.obscura/com.obscura.autofill.ObscuraAutofillService"
        const val TIMEOUT_MS = 15_000L

    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device: UiDevice = UiDevice.getInstance(instrumentation)
    private var previousService: String? = null

    private var filled = CountDownLatch(1)
    @Volatile private var filledMatched = false
    private var receiverRegistered = false
    private val fillReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiver: Context?, intent: Intent?) {
            filledMatched = intent?.getBooleanExtra(TestLoginActivity.EXTRA_MATCHES, false) == true
            filled.countDown()
        }
    }

    @Before
    fun setUp() {
        previousService = shell("settings get secure autofill_service").trim()
        resetAppState()
        shell("settings put secure autofill_service $SERVICE")
    }

    @After
    fun tearDown() {
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(fillReceiver) }
            receiverRegistered = false
        }
        val previous = previousService
        if (previous.isNullOrEmpty() || previous == "null") {
            shell("settings delete secure autofill_service")
        } else {
            shell("settings put secure autofill_service $previous")
        }
        resetAppState()
        // Leave the device as we found it: the form belongs to another package and its task would
        // otherwise stay on top, where later tests cannot bring their own activity back.
        shell("am force-stop $TEST_PACKAGE")
        device.pressHome()
        device.waitForIdle()
    }

    @Test
    fun anUnlockedVaultOffersTheLinkedEntryAndFillsTheForm() {
        createVaultWithLinkedEntry()

        startLoginForm()
        tapUsernameField()
        assertTrue("the linked entry must be offered", waitForSuggestion(ENTRY_TITLE))
        device.findObject(By.text(ENTRY_TITLE)).click()

        assertFormWasFilled()
    }

    @Test
    fun anEntryThatIsNotLinkedIsNeverOffered() {
        createVaultWithLinkedEntry(link = false)

        startLoginForm()
        tapUsernameField()
        assertTrue("the manual choice must still be offered", waitForSuggestion("Search Obscura"))
        assertNull(
            "an unlinked entry must not be suggested to an unrelated app",
            device.findObject(By.text(ENTRY_TITLE))
        )
    }

    @Test
    fun aLockedVaultAsksForThePinAndThenFills() {
        createVaultWithLinkedEntry()
        runBlocking { VaultSession.lock() }

        startLoginForm()
        tapUsernameField()
        assertTrue("a locked vault offers the unlock step", waitForSuggestion("Unlock Obscura"))
        device.findObject(By.text("Unlock Obscura")).click()

        enterPinOnUnlockScreen()

        assertTrue("after unlocking the entry is offered", waitForSuggestion(ENTRY_TITLE))
        device.findObject(By.text(ENTRY_TITLE)).click()
        assertFormWasFilled()
    }

    @Test
    fun fillingStillWorksWithImmediateAutoLock() {
        createVaultWithLinkedEntry()
        AutoLockSettings(context).option = AutoLockOption.IMMEDIATELY
        runBlocking { VaultSession.lock() }

        startLoginForm()
        tapUsernameField()
        assertTrue(waitForSuggestion("Unlock Obscura"))
        device.findObject(By.text("Unlock Obscura")).click()

        enterPinOnUnlockScreen()

        assertTrue("the datasets are built before the vault can lock again", waitForSuggestion(ENTRY_TITLE))
        device.findObject(By.text(ENTRY_TITLE)).click()
        assertFormWasFilled()
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The form lives in the test package and therefore in its own process: it is started with a
     * plain intent, and it reports the fill back by broadcast.
     */
    private fun startLoginForm() {
        filled = CountDownLatch(1)
        filledMatched = false
        context.registerReceiver(
            fillReceiver,
            IntentFilter(TestLoginActivity.ACTION_FILLED),
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true

        context.startActivity(
            Intent()
                .setClassName(TEST_PACKAGE, TestLoginActivity::class.java.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(TestLoginActivity.EXTRA_EXPECTED_USERNAME, USERNAME)
                .putExtra(TestLoginActivity.EXTRA_EXPECTED_PASSWORD, PASSWORD)
        )
        assertTrue("the test form must come up", device.wait(Until.hasObject(By.pkg(TEST_PACKAGE)), TIMEOUT_MS))
    }

    /** The form asks for autofill as soon as it is up; this only waits for it to be there. */
    private fun tapUsernameField() {
        val field = device.wait(Until.findObject(By.res(TEST_PACKAGE, "username")), TIMEOUT_MS)
        assertNotNull("the test form must be on screen", field)
        field.click()
    }

    private fun waitForSuggestion(text: String): Boolean =
        device.wait(Until.hasObject(By.textContains(text)), TIMEOUT_MS) == true

    /** The unlock screen is ours: the PIN keypad is Compose, so the digits are plain text. */
    private fun enterPinOnUnlockScreen() {
        assertTrue("the unlock screen must appear", waitForSuggestion("Enter your PIN"))
        PIN.forEach { digit ->
            val key = device.wait(Until.findObject(By.text(digit.toString())), TIMEOUT_MS)
            assertNotNull("keypad digit $digit", key)
            key.click()
        }
    }

    /** The form itself compares what landed in its fields, so no secret crosses the process. */
    private fun assertFormWasFilled() {
        assertTrue(
            "the form was never filled",
            filled.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        )
        assertTrue("the form was filled with the wrong values", filledMatched)
    }

    private fun createVaultWithLinkedEntry(link: Boolean = true) = runBlocking {
        val repo = AuthRepository(context)
        val dek = repo.createVault(PIN.toCharArray())
        VaultSession.unlock(context, dek)

        VaultSession.runInSession {
            val dao = VaultSession.requireDatabase().vaultDao()
            dao.insertEntry(
                VaultEntity(
                    id = "e1",
                    title = ENTRY_TITLE,
                    category = VaultCategory.ACCOUNT.id,
                    usernameOrCardholder = USERNAME,
                    secretValue = PASSWORD
                )
            )
            if (link) {
                val identity = CallerIdentity.of(context, TEST_PACKAGE)
                assertNotNull("the test package must be installed", identity)
                dao.insertLink(
                    EntryLink(
                        entryId = "e1",
                        type = LinkType.APP.id,
                        value = TEST_PACKAGE,
                        certSha256 = identity!!.currentCertificateHash
                    )
                )
            }
        }
    }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { it.readBytes().decodeToString() }
        }

    private fun resetAppState() {
        runBlocking { VaultSession.lock() }
        context.deleteDatabase(VaultDatabase.DATABASE_NAME)
        Preferences.ALL.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        KeystoreCrypto.deleteBiometricKey()
        VaultSession.idleTimeoutMs = AutoLockOption.DEFAULT.millis
        VaultSession.onAppForegrounded()
    }
}
