package com.obscura.autofill.match

import com.obscura.data.local.EntryLink
import com.obscura.data.local.LinkType
import com.obscura.data.local.VaultEntity
import com.obscura.data.model.VaultCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Phishing scenarios: what must never be offered, and what legitimately must. */
class AutofillMatcherTest {

    private companion object {
        const val BANK_APP = "com.bank.android"
        const val REAL_CERT = "11aa22bb33cc44dd55ee66ff7788990011223344556677889900112233445566"
        const val FAKE_CERT = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        const val CHROME = "com.android.chrome"
        const val CHROME_CERT = "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83"
        const val SAMSUNG = "com.sec.android.app.sbrowser"
        const val SAMSUNG_CERT = "34df0e7a9f1cf1892e45c056b4973cd81ccf148a4050d11aea4ac5a65f900a42"
        const val SAMSUNG_NON_GALAXY_CERT = "0a012131b1bdf9e80ef97d37f3b48362be363a464c8445ecf83627ebe8493a1e"
    }

    private val psl = PublicSuffixList { File("src/main/res/raw/public_suffix_list.gz").inputStream() }
    private val matcher = AutofillMatcher(DomainMatcher(psl))

    private fun login(id: String, url: String = "") = VaultEntity(
        id = id,
        title = "Bank",
        category = VaultCategory.ACCOUNT.id,
        usernameOrCardholder = "ivan",
        secretValue = "s3cret",
        urlOrCardNumber = url
    )

    private fun appLink(entryId: String, packageName: String, cert: String) =
        EntryLink(id = "l-$entryId", entryId = entryId, type = LinkType.APP.id, value = packageName, certSha256 = cert)

    private fun domainLink(entryId: String, domain: String) =
        EntryLink(id = "d-$entryId", entryId = entryId, type = LinkType.DOMAIN.id, value = domain)

    private fun fromApp(pkg: String, cert: String) = FillTarget(pkg, setOf(cert))

    private fun fromChrome(host: String) = FillTarget(CHROME, setOf(CHROME_CERT), webHost = host)

    /** Goes through the same rule the service uses, so the domain survives only if it may. */
    private fun asked(pkg: String, cert: String, webDomain: String) =
        FillTarget.of(pkg, setOf(cert), webDomain)

    // ------------------------------------------------------------------ apps

    @Test
    fun anAppWithTheRightPackageButTheWrongCertificateGetsNothing() {
        val candidate = EntryWithLinks(login("e1"), listOf(appLink("e1", BANK_APP, REAL_CERT)))

        assertTrue(matcher.matches(fromApp(BANK_APP, REAL_CERT), candidate))
        assertFalse(
            "a repackaged app keeps the name but not the signature",
            matcher.matches(fromApp(BANK_APP, FAKE_CERT), candidate)
        )
    }

    @Test
    fun anAppWithoutALinkGetsNothingEvenIfTheEntryHasAMatchingSite() {
        val candidate = EntryWithLinks(login("e1", url = "https://bank.com"))
        assertFalse(matcher.matches(fromApp(BANK_APP, REAL_CERT), candidate))
    }

    @Test
    fun aLinkWithoutACertificateNeverMatches() {
        val candidate = EntryWithLinks(login("e1"), listOf(appLink("e1", BANK_APP, cert = "")))
        assertFalse(matcher.matches(fromApp(BANK_APP, REAL_CERT), candidate))
    }

    @Test
    fun aRotatedSigningKeyStillMatchesTheOldLink() {
        val candidate = EntryWithLinks(login("e1"), listOf(appLink("e1", BANK_APP, REAL_CERT)))
        // signingCertificateHistory reports both the old and the new certificate.
        val afterRotation = FillTarget(BANK_APP, setOf(FAKE_CERT, REAL_CERT))
        assertTrue(matcher.matches(afterRotation, candidate))
    }

    // ------------------------------------------------------------------ sites

    @Test
    fun aDomainFromAnUntrustedAppIsIgnored() {
        val candidate = EntryWithLinks(login("e1", url = "https://bank.com"))

        // The evil app claims to be showing bank.com; it is not a browser we trust, so the claim
        // is dropped and it is matched as an app — for which it has no link.
        val evilApp = FillTarget("com.evil.app", setOf(FAKE_CERT), webHost = null)
        assertFalse(matcher.matches(evilApp, candidate))
        assertTrue("the same site from a real browser does match", matcher.matches(fromChrome("bank.com"), candidate))
    }

    @Test
    fun lookalikeSitesDoNotMatch() {
        val candidate = EntryWithLinks(login("e1", url = "https://bank.com"))

        assertFalse(matcher.matches(fromChrome("evil-bank.com"), candidate))
        assertFalse(matcher.matches(fromChrome("bank.com.evil.com"), candidate))
        assertTrue(matcher.matches(fromChrome("login.bank.com"), candidate))
    }

    @Test
    fun anEntrySavedForAPublicSuffixMatchesNothing() {
        val viaUrl = EntryWithLinks(login("e1", url = "https://github.io"))
        val viaLink = EntryWithLinks(login("e2"), listOf(domainLink("e2", "github.io")))

        assertFalse(matcher.matches(fromChrome("someone.github.io"), viaUrl))
        assertFalse(matcher.matches(fromChrome("someone.github.io"), viaLink))
    }

    @Test
    fun aDomainLinkWorksWhenTheEntryHasNoUrl() {
        val candidate = EntryWithLinks(login("e1"), listOf(domainLink("e1", "bank.co.uk")))

        assertTrue(matcher.matches(fromChrome("bank.co.uk"), candidate))
        assertTrue(matcher.matches(fromChrome("secure.bank.co.uk"), candidate))
        assertFalse(matcher.matches(fromChrome("evil.co.uk"), candidate))
    }

    @Test
    fun junkInTheUrlColumnIsNotTreatedAsASite() {
        val candidate = EntryWithLinks(login("e1", url = "my bank, second account"))
        assertFalse(matcher.matches(fromChrome("bank.com"), candidate))
    }

    @Test
    fun samsungInternetIsBelievedOnlyWhenItIsReallySamsungInternet() {
        val candidate = EntryWithLinks(login("e1", url = "https://bank.com"))

        assertTrue(
            "the browser on the phone must be able to fill its own sites",
            matcher.matches(asked(SAMSUNG, SAMSUNG_CERT, "https://bank.com/login"), candidate)
        )
        assertTrue(
            "non-Galaxy phones get the same browser signed with another Samsung key",
            matcher.matches(asked(SAMSUNG, SAMSUNG_NON_GALAXY_CERT, "https://bank.com/login"), candidate)
        )
        // Same package name, someone else's signature: the domain is dropped and what is left is
        // an app with no link to this entry.
        assertFalse(
            "an app that only calls itself Samsung Internet must get nothing",
            matcher.matches(asked(SAMSUNG, FAKE_CERT, "https://bank.com/login"), candidate)
        )
    }

    // ------------------------------------------------------------------ categories

    @Test
    fun onlyLoginEntriesAreEverOffered() {
        val card = VaultEntity(
            id = "c1",
            title = "Visa",
            category = VaultCategory.BANK_CARD.id,
            urlOrCardNumber = "https://bank.com"
        )
        val note = VaultEntity(id = "n1", title = "Note", category = VaultCategory.SECURE_NOTE.id)
        val apiKey = VaultEntity(id = "k1", title = "Key", category = VaultCategory.API_KEY.id)

        listOf(card, note, apiKey).forEach { entry ->
            val candidate = EntryWithLinks(entry, listOf(domainLink(entry.id, "bank.com"), appLink(entry.id, BANK_APP, REAL_CERT)))
            assertFalse("${entry.category} must never be offered", matcher.matches(fromChrome("bank.com"), candidate))
            assertFalse("${entry.category} must never be offered", matcher.matches(fromApp(BANK_APP, REAL_CERT), candidate))
        }
    }

    @Test
    fun candidatesKeepsOnlyTheMatchingEntries() {
        val entries = listOf(
            EntryWithLinks(login("e1", url = "https://bank.com")),
            EntryWithLinks(login("e2", url = "https://other.com")),
            EntryWithLinks(login("e3"), listOf(domainLink("e3", "bank.com")))
        )

        assertEquals(listOf("e1", "e3"), matcher.candidates(fromChrome("bank.com"), entries).map { it.id })
        assertEquals(emptyList<String>(), matcher.candidates(fromChrome("nothing.com"), entries).map { it.id })
    }
}
