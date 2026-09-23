package com.obscura.autofill.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The one rule both autofill screens use to decide whom they are working for. */
class ConfirmedCallerTest {

    private companion object {
        const val APP = "com.bank.android"
        const val CERT = "11aa22bb33cc44dd55ee66ff7788990011223344556677889900112233445566"
    }

    private val readable: (String) -> CallerIdentity? = { pkg -> CallerIdentity(pkg, setOf(CERT), CERT) }

    @Test
    fun theAppThatStartedTheScreenAndCanBeReadIsConfirmed() {
        val caller = ConfirmedCaller.confirm(APP, startedBy = APP, readIdentity = readable)
        assertEquals(APP, caller?.identity?.packageName)
        assertEquals(setOf(CERT), caller?.identity?.certificateHashes)
    }

    @Test
    fun aScreenStartedBySomeoneElseIsNotConfirmed() {
        assertNull(
            "the package in the intent must be the app that started the screen",
            ConfirmedCaller.confirm(APP, startedBy = "com.evil.app", readIdentity = readable)
        )
    }

    @Test
    fun aScreenNobodyStartedForAResultIsNotConfirmed() {
        assertNull(ConfirmedCaller.confirm(APP, startedBy = null, readIdentity = readable))
    }

    @Test
    fun anAppWhoseCertificateCannotBeReadIsNotConfirmed() {
        assertNull(ConfirmedCaller.confirm(APP, startedBy = APP, readIdentity = { null }))
        assertNull(
            ConfirmedCaller.confirm(APP, startedBy = APP, readIdentity = { CallerIdentity(it, emptySet(), "") })
        )
    }

    @Test
    fun anEmptyClaimIsNotConfirmed() {
        assertNull(ConfirmedCaller.confirm("", startedBy = "", readIdentity = readable))
    }
}
