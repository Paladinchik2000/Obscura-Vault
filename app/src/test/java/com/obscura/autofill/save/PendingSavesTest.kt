package com.obscura.autofill.save

import com.obscura.autofill.match.CallerIdentity
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The typed password lives in memory only as long as the user needs it, and is wiped after. */
class PendingSavesTest {

    private var now = 1_000_000L

    @Before
    fun setUp() {
        PendingSaves.clock = { now }
    }

    @After
    fun tearDown() {
        PendingSaves.expireStale()
        now += PendingSaves.LIFETIME_MS
        PendingSaves.expireStale()
        PendingSaves.clock = { System.currentTimeMillis() }
    }

    private fun pending(password: String = "s3cret") = PendingSave(
        origin = SaveOrigin.fromSaveRequest("com.bank", CallerIdentity("com.bank", setOf("aa"), "aa"), null, "Bank"),
        username = "ivan",
        password = password.toCharArray(),
        filledEntryIds = emptyList()
    )

    @Test
    fun theTokenFindsTheSaveAndNothingElseDoes() {
        val save = pending()
        val token = PendingSaves.put(save)

        assertSame(save, PendingSaves.get(token))
        assertNull(PendingSaves.get("0".repeat(32)))
        assertNull(PendingSaves.get(null))
    }

    @Test
    fun tokensAreRandom() {
        val first = PendingSaves.put(pending())
        val second = PendingSaves.put(pending())
        assertNotEquals(first, second)
        assertTrue("128 bits, hex", Regex("[0-9a-f]{32}").matches(second))
    }

    @Test
    fun discardingWipesThePassword() {
        val save = pending()
        val password = save.password()!!
        val token = PendingSaves.put(save)

        PendingSaves.discard(token)

        assertTrue(save.isWiped)
        assertArrayEquals(CharArray(password.size), password)
        assertNull(PendingSaves.get(token))
        assertTrue(PendingSaves.isEmpty)
    }

    @Test
    fun aNewSaveWipesThePreviousOne() {
        val first = pending("first")
        val firstToken = PendingSaves.put(first)

        PendingSaves.put(pending("second"))

        assertTrue(first.isWiped)
        assertNull(PendingSaves.get(firstToken))
    }

    @Test
    fun aSaveNobodyPicksUpExpires() {
        val save = pending()
        val token = PendingSaves.put(save)

        now += PendingSaves.LIFETIME_MS - 1
        assertSame(save, PendingSaves.get(token))

        now += 1
        assertNull(PendingSaves.get(token))
        assertTrue(save.isWiped)
        assertTrue(PendingSaves.isEmpty)
    }

    @Test
    fun anOldTokenCannotDiscardANewerSave() {
        val oldToken = PendingSaves.put(pending("first"))
        val newer = pending("second")
        val newToken = PendingSaves.put(newer)

        PendingSaves.discard(oldToken)

        assertSame(newer, PendingSaves.get(newToken))
    }
}
