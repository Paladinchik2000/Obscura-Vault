package com.obscura.autofill.save

import com.obscura.data.local.VaultEntity
import com.obscura.data.model.VaultCategory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** New entry, password change or "already saved" — and never a silent overwrite. */
class SavePlannerTest {

    private val defaultLocale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun login(id: String, username: String, password: String, category: VaultCategory = VaultCategory.ACCOUNT) =
        VaultEntity(id = id, title = id, category = category.id, usernameOrCardholder = username, secretValue = password)

    @Test
    fun nothingWithThisUsernameIsANewEntry() {
        val plan = SavePlanner.plan("ivan", "s3cret".toCharArray(), listOf(login("e1", "petr", "s3cret")))
        assertEquals(SavePlan.NewEntry, plan)
    }

    @Test
    fun theSameUsernameAndPasswordIsAlreadySaved() {
        val entry = login("e1", "ivan", "s3cret")
        assertEquals(SavePlan.AlreadySaved(entry), SavePlanner.plan("ivan", "s3cret".toCharArray(), listOf(entry)))
    }

    @Test
    fun theSameUsernameWithAnotherPasswordIsAPasswordChange() {
        val entry = login("e1", "ivan", "old")
        assertEquals(SavePlan.PasswordChange(listOf(entry)), SavePlanner.plan("ivan", "new".toCharArray(), listOf(entry)))
    }

    @Test
    fun severalEntriesWithTheUsernameAreAllOfferedForTheChange() {
        val a = login("a", "ivan", "one")
        val b = login("b", "ivan", "two")
        val other = login("c", "petr", "three")
        assertEquals(SavePlan.PasswordChange(listOf(a, b)), SavePlanner.plan("ivan", "new".toCharArray(), listOf(a, other, b)))
    }

    @Test
    fun anExactMatchWinsOverAChangeForAnotherEntry() {
        val old = login("a", "ivan", "old")
        val current = login("b", "ivan", "new")
        assertEquals(SavePlan.AlreadySaved(current), SavePlanner.plan("ivan", "new".toCharArray(), listOf(old, current)))
    }

    @Test
    fun usernamesCompareWithoutSpacesAndCase() {
        val entry = login("e1", "Ivan@Example.com", "s3cret")
        assertTrue(SavePlanner.plan("  ivan@example.COM ", "s3cret".toCharArray(), listOf(entry)) is SavePlan.AlreadySaved)
    }

    @Test
    fun caseIsFoldedTheSameInATurkishLocale() {
        // In tr-TR "I".lowercase() is a dotless "ı": with the device locale "ADMIN" would not be "admin".
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        val entry = login("e1", "admin", "s3cret")
        assertTrue(SavePlanner.plan("ADMIN", "s3cret".toCharArray(), listOf(entry)) is SavePlan.AlreadySaved)
    }

    @Test
    fun passwordsCompareExactly() {
        val entry = login("e1", "ivan", "S3cret")
        assertTrue(SavePlanner.plan("ivan", "s3cret".toCharArray(), listOf(entry)) is SavePlan.PasswordChange)
    }

    @Test
    fun onlyLoginEntriesCount() {
        val card = login("c1", "ivan", "1234", VaultCategory.BANK_CARD)
        assertEquals(SavePlan.NewEntry, SavePlanner.plan("ivan", "1234".toCharArray(), listOf(card)))
    }

    @Test
    fun theSameEntryListedTwiceIsOneCandidate() {
        val entry = login("e1", "ivan", "old")
        assertEquals(SavePlan.PasswordChange(listOf(entry)), SavePlanner.plan("ivan", "new".toCharArray(), listOf(entry, entry)))
    }
}
