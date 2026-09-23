package com.obscura.autofill.save

import com.obscura.autofill.match.CallerIdentity
import com.obscura.autofill.match.DomainMatcher
import com.obscura.autofill.match.PublicSuffixList
import com.obscura.data.local.EntryLink
import com.obscura.data.local.LinkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** What a saved login is called and what it gets linked to. */
class SaveOriginTest {

    private companion object {
        const val APP = "ro.cineplexx.app"
        const val APP_CERT = "11aa22bb33cc44dd55ee66ff7788990011223344556677889900112233445566"
        const val CHROME = "com.android.chrome"
        const val CHROME_CERT = "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83"
        const val FAKE_CERT = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    }

    private val psl = PublicSuffixList { File("src/main/res/raw/public_suffix_list.gz").inputStream() }
    private val domains = DomainMatcher(psl)

    private fun app(label: String? = "Cineplexx") =
        SaveOrigin.fromSaveRequest(APP, CallerIdentity(APP, setOf(APP_CERT), APP_CERT), null, label)

    private fun chrome(webDomain: String) =
        SaveOrigin.fromSaveRequest(CHROME, CallerIdentity(CHROME, setOf(CHROME_CERT), CHROME_CERT), webDomain, "Chrome")

    @Test
    fun anAppIsNamedByItsLabelAndLinkedWithItsCertificate() {
        val origin = app()
        assertEquals("Cineplexx", origin.defaultTitle(psl))
        assertEquals("", origin.url())
        assertEquals(
            EntryLink(id = "x", entryId = "e1", type = LinkType.APP.id, value = APP, certSha256 = APP_CERT),
            origin.linkFor("e1", domains)?.copy(id = "x")
        )
    }

    @Test
    fun withoutALabelThePackageNameIsTheTitle() {
        assertEquals(APP, app(label = null).defaultTitle(psl))
        assertEquals(APP, app(label = "  ").defaultTitle(psl))
    }

    @Test
    fun aSiteInATrustedBrowserIsNamedByItsRegistrableDomainAndLinkedToTheSite() {
        val origin = chrome("https://login.bank.co.uk/signin")
        assertEquals("bank.co.uk", origin.defaultTitle(psl))
        assertEquals("https://login.bank.co.uk", origin.url())
        val link = origin.linkFor("e1", domains)
        assertEquals(LinkType.DOMAIN.id, link?.type)
        assertEquals("login.bank.co.uk", link?.value)
        assertEquals("", link?.certSha256)
    }

    @Test
    fun aSiteThatIsOnlyAPublicSuffixGetsNoLink() {
        assertNull(chrome("https://github.io/").linkFor("e1", domains))
    }

    @Test
    fun anUntrustedAppClaimingASiteIsTreatedAsAnApp() {
        val origin = SaveOrigin.fromSaveRequest(APP, CallerIdentity(APP, setOf(APP_CERT), APP_CERT), "bank.com", "Cineplexx")
        assertNull(origin.webHost)
        assertEquals("Cineplexx", origin.defaultTitle(psl))
        assertEquals(LinkType.APP.id, origin.linkFor("e1", domains)?.type)
    }

    @Test
    fun aBrowserWithSomeoneElsesCertificateIsNotBelievedAboutTheSite() {
        val origin = SaveOrigin.fromSaveRequest(CHROME, CallerIdentity(CHROME, setOf(FAKE_CERT), FAKE_CERT), "bank.com", null)
        assertNull(origin.webHost)
        assertEquals(LinkType.APP.id, origin.linkFor("e1", domains)?.type)
        assertEquals(FAKE_CERT, origin.linkFor("e1", domains)?.certSha256)
    }

    @Test
    fun anAppWhoseCertificateCannotBeReadGetsNoLinkAndNoTarget() {
        val origin = SaveOrigin.fromSaveRequest(APP, null, null, null)
        assertNull(origin.linkFor("e1", domains))
        assertNull(origin.target())
        assertEquals(APP, origin.defaultTitle(psl))
    }

    @Test
    fun anIdentityForAnotherPackageIsIgnored() {
        val origin = SaveOrigin.fromSaveRequest(APP, CallerIdentity("com.other", setOf(APP_CERT), APP_CERT), null, null)
        assertNull(origin.identity)
        assertNull(origin.linkFor("e1", domains))
    }

    @Test
    fun savePolicy() {
        assertTrue(SavePolicy.offersSave(28, APP, "com.obscura", hasPasswordField = true))
        assertFalse("API 26-27 cannot open the save screen", SavePolicy.offersSave(27, APP, "com.obscura", true))
        assertFalse("never inside Obscura", SavePolicy.offersSave(36, "com.obscura", "com.obscura", true))
        assertFalse("no password field, nothing to save", SavePolicy.offersSave(36, APP, "com.obscura", false))
        assertFalse(SavePolicy.offersSave(36, null, "com.obscura", true))

        assertTrue(SavePolicy.acceptsSave(36, APP, "com.obscura", "p".toCharArray()))
        assertFalse(SavePolicy.acceptsSave(36, APP, "com.obscura", CharArray(0)))
        assertFalse(SavePolicy.acceptsSave(36, APP, "com.obscura", null))
        assertFalse(SavePolicy.acceptsSave(36, "com.obscura", "com.obscura", "p".toCharArray()))
    }
}
