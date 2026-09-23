package com.obscura.autofill

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.obscura.autofill.match.CallerIdentity
import com.obscura.autofill.save.PendingSaves
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.util.regex.Pattern

/**
 * Saving a login typed by hand into another app, through the real system UI: the system's
 * "Save to Obscura?", then our own screen, then the vault.
 *
 * The forms come from :autofilltarget, a separate app installed by the test, so package
 * visibility is what a store app would get (see AutofillPackageVisibilityTest).
 */
@RunWith(AndroidJUnit4::class)
class AutofillSaveTest {

    private companion object {
        const val PIN = "123456"
        const val USERNAME = "octocat"
        const val PASSWORD = "s3cret-Passw0rd!"
        const val NEW_PASSWORD = "n3w-Passw0rd!"
        const val ENTRY_TITLE = "GitHub test"
        const val SERVICE = "com.obscura/com.obscura.autofill.ObscuraAutofillService"
        const val TIMEOUT_MS = 15_000L
        const val OBSCURA = "com.obscura"

        const val LAUNCHABLE = "com.obscura.autofilltarget"
        const val HIDDEN = "com.obscura.autofilltarget.hidden"
        const val ACTIVITY = "com.obscura.autofilltarget.LoginActivity"

        /** The label in :autofilltarget's manifest: what PackageManager calls the app. */
        const val TARGET_LABEL = "Autofill target"

        /** The system's own save prompt. */
        val SYSTEM_SAVE: BySelector = By.res("android", "autofill_save_yes")
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device: UiDevice = UiDevice.getInstance(instrumentation)
    private var previousService: String? = null

    @Before
    fun setUp() {
        // Saving needs API 28; installing the target app through pm's stdin needs 29.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        previousService = shell("settings get secure autofill_service").trim()
        resetAppState()
        shell("settings put secure autofill_service $SERVICE")
    }

    @After
    fun tearDown() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val previous = previousService
        if (previous.isNullOrEmpty() || previous == "null") {
            shell("settings delete secure autofill_service")
        } else {
            shell("settings put secure autofill_service $previous")
        }
        resetAppState()
        shell("pm uninstall $LAUNCHABLE")
        shell("pm uninstall $HIDDEN")
        device.pressHome()
        device.waitForIdle()
    }

    /**
     * The basic case, and the one a missing SaveInfo would break: a login typed into an app
     * becomes a login entry named after the app, linked to it with its certificate.
     */
    @Test
    fun aLoginTypedIntoAnAppIsSavedNamedAfterTheAppAndLinkedToIt() {
        install("autofilltarget-launchable.apk")
        createVault {}

        typeAndSignIn(LAUNCHABLE, USERNAME, PASSWORD, dismiss = "Search Obscura")
        acceptSystemSave()

        assertTrue("the title starts as the app's label", waitFor(By.pkg(OBSCURA).text(TARGET_LABEL)))

        // The save screen has no caller to confirm: the system does not start it for a result.
        val caller = resumedSaveScreenCaller()
        Log.i("AutofillSaveTest", "save screen: callingActivity=${caller.first} callingPackage=${caller.second}")
        assertNull("measured: the save screen is not started for a result", caller.first)

        tapOurs("Save")
        waitUntilOurScreenIsGone()

        val saved = entries().single()
        assertEquals(TARGET_LABEL, saved.title)
        assertEquals(VaultCategory.ACCOUNT.id, saved.category)
        assertEquals(USERNAME, saved.usernameOrCardholder)
        assertEquals(PASSWORD, saved.secretValue)
        assertEquals(listOf(saved.id to debugCertificate()), appLinks(LAUNCHABLE))
        assertTrue("the typed password must not outlive the screen", PendingSaves.isEmpty)
    }

    /**
     * Point 6 of the plan: with the vault locked the user sees what they are unlocking for, and
     * an exact duplicate is only reported after unlocking — nothing is written, nothing more is
     * asked.
     */
    @Test
    fun anExactDuplicateTypedWhileLockedIsReportedAndNotWrittenAgain() {
        install("autofilltarget-launchable.apk")
        createVault {
            entry("e1", ENTRY_TITLE, USERNAME, PASSWORD)
            appLink("e1", LAUNCHABLE, debugCertificate())
        }
        runBlocking { VaultSession.lock() }

        typeAndSignIn(LAUNCHABLE, USERNAME, PASSWORD, dismiss = "Unlock Obscura")
        acceptSystemSave()

        tapOurs("Unlock and save")
        enterPin()
        waitUntilOurScreenIsGone()

        assertEquals("a duplicate must not be written", listOf("e1"), entries().map { it.id })
        assertEquals(1, appLinks(LAUNCHABLE).size)
        assertTrue(PendingSaves.isEmpty)
    }

    /** Same username, new password: offered as an update of that entry, applied only on a tap. */
    @Test
    fun aNewPasswordForASavedUsernameIsOfferedAsAnUpdate() {
        install("autofilltarget-launchable.apk")
        createVault {
            entry("e1", ENTRY_TITLE, USERNAME, PASSWORD)
            appLink("e1", LAUNCHABLE, debugCertificate())
        }

        typeAndSignIn(LAUNCHABLE, USERNAME, NEW_PASSWORD, dismiss = ENTRY_TITLE)
        acceptSystemSave()

        tapOurs("Update “$ENTRY_TITLE”")
        waitUntilOurScreenIsGone()

        val entry = entries().single()
        assertEquals("e1", entry.id)
        assertEquals(NEW_PASSWORD, entry.secretValue)
        assertEquals(1, appLinks(LAUNCHABLE).size)
    }

    /** Locked vault, new login: "Unlock and save" is the confirmation — no second question. */
    @Test
    fun aNewLoginTypedWhileLockedIsSavedRightAfterUnlocking() {
        install("autofilltarget-launchable.apk")
        createVault {}
        runBlocking { VaultSession.lock() }

        typeAndSignIn(LAUNCHABLE, USERNAME, PASSWORD, dismiss = "Unlock Obscura")
        acceptSystemSave()

        tapOurs("Unlock and save")
        enterPin()
        waitUntilOurScreenIsGone()

        assertEquals(listOf(USERNAME), entries().map { it.usernameOrCardholder })
    }

    /** "Not now" writes nothing and does not keep the password. */
    @Test
    fun notNowWritesNothingAndWipesThePassword() {
        install("autofilltarget-launchable.apk")
        createVault {}

        typeAndSignIn(LAUNCHABLE, USERNAME, PASSWORD, dismiss = "Search Obscura")
        acceptSystemSave()
        assertTrue(waitFor(By.pkg(OBSCURA).text(TARGET_LABEL)))
        assertFalse("the password is held while the user decides", PendingSaves.isEmpty)

        tapOurs("Not now")
        waitUntilOurScreenIsGone()

        assertEquals(emptyList<VaultEntity>(), entries())
        assertTrue("cancelling must wipe the password", PendingSaves.isEmpty)
    }

    /**
     * An app Obscura cannot see: its certificate is unreadable when the save arrives, so the
     * entry is saved without a link, and the screen says so. The link comes later, from the
     * picker, where the caller is confirmed.
     */
    @Test
    fun anAppObscuraCannotSeeGetsTheEntryWithoutALinkUntilThePickerLinksIt() {
        install("autofilltarget-hidden.apk")
        createVault {}

        typeAndSignIn(HIDDEN, USERNAME, PASSWORD, dismiss = "Search Obscura")
        acceptSystemSave()

        assertTrue("without a label the package names the entry", waitFor(By.pkg(OBSCURA).text(HIDDEN)))
        assertTrue(
            "the screen must say this app will not get the login suggested",
            waitFor(By.pkg(OBSCURA).textContains("can't confirm this app"))
        )
        tapOurs("Save")
        waitUntilOurScreenIsGone()

        val saved = entries().single()
        assertEquals(HIDDEN, saved.title)
        assertEquals("no certificate, no link", emptyList<Pair<String, String>>(), appLinks(HIDDEN))

        // Next time, through the picker: the app started it, so it is confirmed and can be linked.
        startForm(HIDDEN)
        assertTrue(waitFor(By.text("Search Obscura")))
        device.findObject(By.text("Search Obscura")).click()
        val row = device.wait(Until.findObject(By.pkg(OBSCURA).text(HIDDEN)), TIMEOUT_MS)
        assertNotNull("the picker lists the saved entry", row)
        row.click()
        tapOurs("Remember")

        assertEquals(listOf(saved.id to debugCertificate()), appLinks(HIDDEN))
    }

    /**
     * Values the fill itself put in, left unchanged, are not offered for saving at all: the
     * system compares them with what it filled. Platform behaviour, checked because the whole
     * "do not save what was autofilled" rule rests on it.
     */
    @Test
    fun anAutofilledLoginLeftUnchangedIsNotOfferedForSaving() {
        install("autofilltarget-launchable.apk")
        createVault {
            entry("e1", ENTRY_TITLE, USERNAME, PASSWORD)
            appLink("e1", LAUNCHABLE, debugCertificate())
        }

        startForm(LAUNCHABLE)
        assertTrue(waitFor(By.text(ENTRY_TITLE)))
        device.findObject(By.text(ENTRY_TITLE)).click()
        assertTrue("the form was filled", waitFor(By.pkg(LAUNCHABLE).text("filled: match")))

        field(LAUNCHABLE, "submit").click()
        assertFalse(
            "an unchanged autofilled login must not be offered for saving",
            device.wait(Until.hasObject(SYSTEM_SAVE), 5_000L) == true
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun install(asset: String) {
        val apk = instrumentation.context.assets.open(asset).use { it.readBytes() }
        val (stdout, stdin) = instrumentation.uiAutomation.executeShellCommandRw("pm install -t -r -S ${apk.size}")
        ParcelFileDescriptor.AutoCloseOutputStream(stdin).use { it.write(apk) }
        val output = ParcelFileDescriptor.AutoCloseInputStream(stdout).use { it.readBytes().decodeToString() }
        assertTrue("installing $asset failed: $output", output.contains("Success"))
    }

    /** Started through the shell, force-stopped first: nothing of ours starts the target app. */
    private fun startForm(pkg: String) {
        shell(
            "am start -W -S -n $pkg/$ACTIVITY " +
                "--es expected_username $USERNAME --es expected_password $PASSWORD"
        )
        field(pkg, "username").click()
    }

    /**
     * Types a login by hand and signs in. The form asks for autofill as soon as it is up;
     * [dismiss] is the suggestion to wait for, so the typing does not race the dropdown.
     */
    private fun typeAndSignIn(pkg: String, username: String, password: String, dismiss: String) {
        startForm(pkg)
        assertTrue("our answer must arrive first", waitFor(By.text(dismiss)))
        device.pressBack()
        field(pkg, "username").text = username
        field(pkg, "password").text = password
        field(pkg, "submit").click()
    }

    private fun acceptSystemSave() {
        val save = device.wait(Until.findObject(SYSTEM_SAVE), TIMEOUT_MS)
        assertNotNull("the system must offer to save the typed login", save)
        save.click()
    }

    private fun field(pkg: String, id: String): UiObject2 {
        val found = device.wait(Until.findObject(By.pkg(pkg).res(Pattern.compile(".*:id/$id"))), TIMEOUT_MS)
        assertNotNull("$id in $pkg", found)
        return found
    }

    private fun tapOurs(text: String) {
        val button = device.wait(Until.findObject(By.pkg(OBSCURA).text(text)), TIMEOUT_MS)
        assertNotNull("\"$text\" on our screen", button)
        button.click()
    }

    private fun enterPin() {
        assertTrue(waitFor(By.pkg(OBSCURA).textContains("Enter your PIN")))
        PIN.forEach { digit ->
            val key = device.wait(Until.findObject(By.pkg(OBSCURA).text(digit.toString())), TIMEOUT_MS)
            assertNotNull("keypad digit $digit", key)
            key.click()
        }
    }

    private fun waitUntilOurScreenIsGone() {
        assertTrue(
            "our screen must close by itself",
            device.wait(Until.gone(By.pkg(OBSCURA)), TIMEOUT_MS) == true
        )
    }

    private fun waitFor(selector: BySelector): Boolean = device.wait(Until.hasObject(selector), TIMEOUT_MS) == true

    private fun resumedSaveScreenCaller(): Pair<ComponentName?, String?> {
        var result: Pair<ComponentName?, String?> = null to null
        instrumentation.runOnMainSync {
            val screen = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<AutofillSaveActivity>()
                .single()
            result = screen.callingActivity to screen.callingPackage
        }
        return result
    }

    /** The target app is signed with the debug key, like Obscura itself. */
    private fun debugCertificate(): String =
        requireNotNull(CallerIdentity.of(context, context.packageName)).currentCertificateHash

    private class VaultContents {
        val entries = mutableListOf<VaultEntity>()
        val links = mutableListOf<EntryLink>()

        fun entry(id: String, title: String, username: String, password: String) {
            entries += VaultEntity(
                id = id,
                title = title,
                category = VaultCategory.ACCOUNT.id,
                usernameOrCardholder = username,
                secretValue = password
            )
        }

        fun appLink(entryId: String, pkg: String, cert: String) {
            links += EntryLink(entryId = entryId, type = LinkType.APP.id, value = pkg, certSha256 = cert)
        }
    }

    private fun createVault(fill: VaultContents.() -> Unit) = runBlocking {
        val contents = VaultContents().apply(fill)
        val dek = AuthRepository(context).createVault(PIN.toCharArray())
        VaultSession.unlock(context, dek)
        VaultSession.runInSession {
            val dao = VaultSession.requireDatabase().vaultDao()
            contents.entries.forEach { dao.insertEntry(it) }
            contents.links.forEach { dao.insertLink(it) }
        }
    }

    private fun entries(): List<VaultEntity> = runBlocking {
        VaultSession.runInSession { VaultSession.requireDatabase().vaultDao().getAllEntriesDirect() }
    }

    /** Every app link to [pkg], as entry id to certificate. */
    private fun appLinks(pkg: String): List<Pair<String, String>> = runBlocking {
        VaultSession.runInSession {
            VaultSession.requireDatabase().vaultDao().linksByValue(LinkType.APP.id, pkg).map { it.entryId to it.certSha256 }
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
