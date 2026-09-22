package com.obscura.autofill.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Domain matching is the phishing defence: a form on the wrong site must never see a password.
 * These run against the real Public Suffix List that ships in the APK.
 */
class DomainMatchingTest {

    private val psl = PublicSuffixList { File("src/main/res/raw/public_suffix_list.gz").inputStream() }
    private val matcher = DomainMatcher(psl)

    // ------------------------------------------------------------------ look-alikes

    @Test
    fun aLookalikeDomainDoesNotMatch() {
        assertFalse(matcher.matches("bank.com", "evil-bank.com"))
        assertFalse(matcher.matches("bank.com", "bankk.com"))
        assertFalse(matcher.matches("bank.com", "bank.com.co"))
    }

    @Test
    fun theSavedDomainMustBeTheSuffixOfTheObservedHostOnALabelBoundary() {
        assertFalse("the saved domain is only part of a longer name", matcher.matches("bank.com", "bank.com.evil.com"))
        assertFalse(matcher.matches("bank.com", "notbank.com"))
        assertTrue(matcher.matches("bank.com", "bank.com"))
        assertTrue(matcher.matches("bank.com", "login.bank.com"))
        assertTrue(matcher.matches("bank.com", "a.b.bank.com"))
    }

    @Test
    fun aSavedSubdomainDoesNotCoverItsParent() {
        assertTrue(matcher.matches("login.bank.com", "login.bank.com"))
        assertTrue(matcher.matches("login.bank.com", "eu.login.bank.com"))
        assertFalse(matcher.matches("login.bank.com", "bank.com"))
        assertFalse(matcher.matches("login.bank.com", "other.bank.com"))
    }

    // ------------------------------------------------------------------ public suffixes

    @Test
    fun aPublicSuffixMatchesNothing() {
        assertFalse("co.uk belongs to everyone", matcher.matches("co.uk", "bank.co.uk"))
        assertFalse(matcher.matches("github.io", "someone.github.io"))
        assertFalse(matcher.matches("com", "bank.com"))
        assertFalse(matcher.matches("uk", "bank.co.uk"))
    }

    @Test
    fun aDomainUnderAPublicSuffixWorksNormally() {
        assertTrue(matcher.matches("bank.co.uk", "bank.co.uk"))
        assertTrue(matcher.matches("bank.co.uk", "login.bank.co.uk"))
        assertFalse(matcher.matches("bank.co.uk", "evil.co.uk"))

        assertTrue(matcher.matches("someone.github.io", "someone.github.io"))
        assertFalse(matcher.matches("someone.github.io", "attacker.github.io"))
    }

    @Test
    fun theListsWildcardAndExceptionRulesAreHonoured() {
        // *.ck makes every second-level name a suffix, !www.ck takes www.ck back out of it.
        assertEquals("bar.ck", psl.publicSuffixOf("foo.bar.ck"))
        assertEquals("ck", psl.publicSuffixOf("www.ck"))
        assertTrue(psl.isPublicSuffix("bar.ck"))
        assertFalse(psl.isPublicSuffix("www.ck"))
    }

    @Test
    fun registrableDomainsAreTheSuffixPlusOneLabel() {
        assertEquals("bank.com", psl.registrableDomainOf("login.bank.com"))
        assertEquals("bank.co.uk", psl.registrableDomainOf("secure.bank.co.uk"))
        assertEquals("someone.github.io", psl.registrableDomainOf("blog.someone.github.io"))
        assertNull("a public suffix has no registrable domain", psl.registrableDomainOf("co.uk"))
        assertNull(psl.registrableDomainOf("com"))
    }

    // ------------------------------------------------------------------ parsing and normalising

    @Test
    fun hostsAreNormalisedBeforeComparison() {
        assertTrue(matcher.matches("BANK.com", "login.Bank.COM"))
        assertTrue("a trailing dot is the same host", matcher.matches("bank.com.", "login.bank.com"))
        assertTrue("a port is not part of the host", matcher.matches("bank.com", "login.bank.com:8443"))
    }

    @Test
    fun storedValuesAreReducedToAHost() {
        assertEquals("github.com", DomainMatcher.hostOf("https://github.com/login?next=1"))
        assertEquals("github.com", DomainMatcher.hostOf("github.com"))
        assertEquals("github.com", DomainMatcher.hostOf("github.com/login"))
        assertEquals("github.com", DomainMatcher.hostOf("https://user@github.com:443/x"))
        assertNull(DomainMatcher.hostOf(""))
        assertNull(DomainMatcher.hostOf("not a host at all"))
        assertNull(DomainMatcher.hostOf("card number 4532 1111 2222 3333"))
    }

    @Test
    fun onlyRealDomainsCanBeStored() {
        assertEquals("github.com", matcher.storableHost("https://github.com/login"))
        assertEquals("someone.github.io", matcher.storableHost("someone.github.io"))
        assertNull("a public suffix is not a site", matcher.storableHost("github.io"))
        assertNull(matcher.storableHost("co.uk"))
        assertNull(matcher.storableHost("com"))
        assertNull(matcher.storableHost("Обычная заметка"))
    }

    @Test
    fun addressesMatchOnlyThemselves() {
        assertTrue(matcher.matches("192.168.1.10", "192.168.1.10"))
        assertFalse(matcher.matches("192.168.1.10", "sub.192.168.1.10"))
        assertFalse(matcher.matches("192.168.1.10", "192.168.1.11"))
    }
}
