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
 * Autofill for an app that Obscura has no relation to.
 *
 * [AutofillEndToEndTest] fills a form in com.obscura.test, and a package and its instrumentation
 * always see each other (AppsFilterImpl), so it passed while on a real phone every request was
 * answered with nothing: Obscura could not read the caller's certificate. The form here comes
 * from :autofilltarget, a separate app installed by the test itself — as invisible to Obscura as
 * an app from the store unless the manifest's <queries> says otherwise.
 */
@RunWith(AndroidJUnit4::class)
class AutofillPackageVisibilityTest {

    private companion object {
        const val PIN = "123456"
        const val USERNAME = "octocat"
        const val PASSWORD = "s3cret-Passw0rd!"
        const val ENTRY_TITLE = "GitHub test"
        const val OTHER_TITLE = "Another account"
        const val FOREIGN_CERT = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        const val SERVICE = "com.obscura/com.obscura.autofill.ObscuraAutofillService"
        const val TIMEOUT_MS = 15_000L

        /** Has a launcher icon, so Obscura's <queries> makes it visible. */
        const val LAUNCHABLE = "com.obscura.autofilltarget"

        /** Same form without the icon: outside <queries>, so it stays invisible. */
        const val HIDDEN = "com.obscura.autofilltarget.hidden"

        const val ACTIVITY = "com.obscura.autofilltarget.LoginActivity"

        /** What the target app shows once both fields hold the expected values. */
        const val FILLED_CORRECTLY = "filled: match"
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device: UiDevice = UiDevice.getInstance(instrumentation)
    private var previousService: String? = null

    @Before
    fun setUp() {
        // Installing through the shell's stdin needs executeShellCommandRw.
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
        // Uninstalling also drops any visibility the system granted during the test.
        shell("pm uninstall $LAUNCHABLE")
        shell("pm uninstall $HIDDEN")
        device.pressHome()
        device.waitForIdle()
    }

    /**
     * The regression itself: without <queries> the package lookup fails, the linked entry is not
     * offered and only the manual choice is left — so this test fails.
     */
    @Test
    fun anAppWithALauncherIconIsRecognisedAndGetsItsLinkedEntry() {
        install("autofilltarget-launchable.apk")
        createVaultWithEntryLinkedTo(LAUNCHABLE)

        startForm(LAUNCHABLE)
        assertTrue(
            "the entry linked to this app must be offered; if only \"Search Obscura\" shows, " +
                "Obscura cannot see the package (check <queries> in the manifest)",
            waitFor(By.text(ENTRY_TITLE))
        )
        device.findObject(By.text(ENTRY_TITLE)).click()

        assertTrue("the form was not filled with the entry", waitFor(By.pkg(LAUNCHABLE).text(FILLED_CORRECTLY)))
    }

    /**
     * An app outside <queries>: in the fill request its certificate cannot be read, so nothing
     * may be offered to it even though a link exists — but the answer must not be empty, or the
     * failure is invisible. In the picker the app is visible (it started the picker for a
     * result), the certificate matches the link, and the link counts: the entry comes first and
     * is filled without asking to remember it again.
     */
    @Test
    fun aLinkToAnAppObscuraCannotSeeIsAppliedInThePickerWithoutAsking() {
        install("autofilltarget-hidden.apk")
        createVault {
            // Written first, so without the link it would come first too.
            entry("e2", OTHER_TITLE)
            entry("e1", ENTRY_TITLE)
            appLink("e1", HIDDEN, debugCertificate())
        }

        startForm(HIDDEN)
        assertTrue("the manual choice must be offered", waitFor(By.text("Search Obscura")))
        assertNull(
            "without a certificate the linked entry must not be offered in the fill request",
            device.findObject(By.text(ENTRY_TITLE))
        )

        device.findObject(By.text("Search Obscura")).click()
        val linkedRow = waitForPickerRow(ENTRY_TITLE)
        val otherRow = waitForPickerRow(OTHER_TITLE)
        assertTrue(
            "the linked entry must come first",
            linkedRow.visibleBounds.top < otherRow.visibleBounds.top
        )

        linkedRow.click()
        // A "Remember this choice?" dialog would stop the fill here.
        assertTrue("the linked entry must fill without a question", waitFor(By.pkg(HIDDEN).text(FILLED_CORRECTLY)))
        assertEquals(listOf(debugCertificate()), appLinkCertificates("e1", HIDDEN))
    }

    /**
     * A link made for another signature (another app that used the same package name) is not a
     * link to this app: the picker treats the entry as unlinked and asks. Remembering it then
     * replaces the old link instead of adding a second row.
     */
    @Test
    fun aLinkWithAForeignCertificateIsNotAppliedAndRelinkingReplacesIt() {
        install("autofilltarget-hidden.apk")
        createVault {
            entry("e1", ENTRY_TITLE)
            appLink("e1", HIDDEN, FOREIGN_CERT)
        }

        startForm(HIDDEN)
        assertTrue(waitFor(By.text("Search Obscura")))
        device.findObject(By.text("Search Obscura")).click()
        waitForPickerRow(ENTRY_TITLE).click()

        val remember = device.wait(Until.findObject(By.pkg("com.obscura").text("Remember")), TIMEOUT_MS)
        assertNotNull("a link with another certificate must not count: the picker has to ask", remember)
        remember.click()
        assertTrue("the form was not filled", waitFor(By.pkg(HIDDEN).text(FILLED_CORRECTLY)))

        assertEquals(
            "linking again must replace the link, not add a second one",
            listOf(debugCertificate()),
            appLinkCertificates("e1", HIDDEN)
        )
    }

    /**
     * The visibility the picker gets lives only in system_server: it outlasts the target app's
     * process, but an update of the target app drops it. Before the update the fill request
     * itself offers the linked entry; after it, only "Search Obscura" again, and the link still
     * works in the picker.
     */
    @Test
    fun afterAnUpdateTheAppIsInvisibleAgainAndTheLinkStillWorksInThePicker() {
        install("autofilltarget-hidden.apk")
        createVault {
            entry("e1", ENTRY_TITLE)
            appLink("e1", HIDDEN, debugCertificate())
        }

        // First fill: invisible, so through the picker; starting it makes the app visible to us.
        startForm(HIDDEN)
        assertTrue(waitFor(By.text("Search Obscura")))
        assertNull(device.findObject(By.text(ENTRY_TITLE)))
        device.findObject(By.text("Search Obscura")).click()
        waitForPickerRow(ENTRY_TITLE).click()
        assertTrue(waitFor(By.pkg(HIDDEN).text(FILLED_CORRECTLY)))

        // startForm force-stops the app first: the grant outlives its process, and the fill
        // request itself now recognises the app.
        startForm(HIDDEN)
        assertTrue(
            "while the app is visible the linked entry is offered directly",
            waitFor(By.text(ENTRY_TITLE))
        )
        device.pressBack()

        // An update of the target app drops the grant.
        install("autofilltarget-hidden.apk")

        startForm(HIDDEN)
        assertTrue(waitFor(By.text("Search Obscura")))
        assertNull("after the update the app is invisible again", device.findObject(By.text(ENTRY_TITLE)))
        device.findObject(By.text("Search Obscura")).click()
        waitForPickerRow(ENTRY_TITLE).click()
        assertTrue(
            "the link still works in the picker, without a question",
            waitFor(By.pkg(HIDDEN).text(FILLED_CORRECTLY))
        )
        assertEquals(listOf(debugCertificate()), appLinkCertificates("e1", HIDDEN))
    }

    /**
     * The picker is started by the system from the app being filled, through our immutable
     * PendingIntent. Whether it can tell who that app is decides whether the package name in the
     * intent can be checked at all — so this is measured here rather than assumed.
     */
    @Test
    fun thePickerKnowsWhichAppStartedIt() {
        install("autofilltarget-hidden.apk")
        createVaultWithEntryLinkedTo(HIDDEN)

        startForm(HIDDEN)
        assertTrue(waitFor(By.text("Search Obscura")))
        device.findObject(By.text("Search Obscura")).click()
        assertTrue("the picker must come up", waitFor(By.pkg("com.obscura").text(ENTRY_TITLE)))

        var callingActivity: ComponentName? = null
        var callingPackage: String? = null
        instrumentation.runOnMainSync {
            val picker = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<AutofillUnlockActivity>()
                .single()
            callingActivity = picker.callingActivity
            callingPackage = picker.callingPackage
        }
        Log.i("AutofillVisibilityTest", "picker: callingActivity=$callingActivity callingPackage=$callingPackage")

        assertEquals(
            "callingActivity=$callingActivity callingPackage=$callingPackage",
            HIDDEN,
            callingActivity?.packageName
        )
        assertEquals(HIDDEN, callingPackage)
    }

    // ------------------------------------------------------------------ helpers

    /** The APK comes from the test's assets and goes to this device only, through pm's stdin. */
    private fun install(asset: String) {
        val apk = instrumentation.context.assets.open(asset).use { it.readBytes() }
        val (stdout, stdin) = instrumentation.uiAutomation.executeShellCommandRw("pm install -t -r -S ${apk.size}")
        ParcelFileDescriptor.AutoCloseOutputStream(stdin).use { it.write(apk) }
        val output = ParcelFileDescriptor.AutoCloseInputStream(stdout).use { it.readBytes().decodeToString() }
        assertTrue("installing $asset failed: $output", output.contains("Success"))
    }

    /**
     * Started through the shell, not by our process: an app starting another app's activity can
     * give the two of them visibility of each other, which is what this test must not have.
     */
    private fun startForm(pkg: String) {
        shell(
            "am start -W -S -n $pkg/$ACTIVITY " +
                "--es expected_username $USERNAME --es expected_password $PASSWORD"
        )
        val field = device.wait(Until.findObject(By.pkg(pkg).res(Pattern.compile(".*:id/username"))), TIMEOUT_MS)
        assertNotNull("the form in $pkg must come up", field)
        field.click()
    }

    private fun waitFor(selector: BySelector): Boolean = device.wait(Until.hasObject(selector), TIMEOUT_MS) == true

    private fun waitForPickerRow(title: String): UiObject2 {
        val row = device.wait(Until.findObject(By.pkg("com.obscura").text(title)), TIMEOUT_MS)
        assertNotNull("the picker must list $title", row)
        return row
    }

    /**
     * The target app is built and signed like the test APKs, with the debug key: the same
     * certificate Obscura itself carries, and Obscura can always read its own.
     */
    private fun debugCertificate(): String =
        requireNotNull(CallerIdentity.of(context, context.packageName)).currentCertificateHash

    private fun createVaultWithEntryLinkedTo(pkg: String) = createVault {
        entry("e1", ENTRY_TITLE)
        appLink("e1", pkg, debugCertificate())
    }

    private class VaultContents {
        val entries = mutableListOf<VaultEntity>()
        val links = mutableListOf<EntryLink>()

        fun entry(id: String, title: String) {
            entries += VaultEntity(
                id = id,
                title = title,
                category = VaultCategory.ACCOUNT.id,
                usernameOrCardholder = USERNAME,
                secretValue = PASSWORD
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

    /** Certificates of every app link from [entryId] to [pkg]; more than one means a duplicate. */
    private fun appLinkCertificates(entryId: String, pkg: String): List<String> = runBlocking {
        VaultSession.runInSession {
            VaultSession.requireDatabase().vaultDao().linksByValue(LinkType.APP.id, pkg)
                .filter { it.entryId == entryId }
                .map { it.certSha256 }
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
