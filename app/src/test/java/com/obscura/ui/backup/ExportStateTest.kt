package com.obscura.ui.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules behind the export button: minimum length, matching confirmation, no double start. */
class ExportStateTest {

    @Test
    fun emptyPasswordCannotStart() {
        assertFalse(ExportState().canStart)
    }

    @Test
    fun elevenCharactersAreTooShortEvenWhenConfirmed() {
        val password = "a".repeat(MIN_BACKUP_PASSWORD_LENGTH - 1)
        val state = ExportState(password = password, confirmation = password)

        assertTrue(state.isTooShort)
        assertFalse(state.canStart)
    }

    @Test
    fun twelveMatchingCharactersCanStart() {
        val password = "correcthorse"
        assertEquals(MIN_BACKUP_PASSWORD_LENGTH, password.length)

        assertTrue(ExportState(password = password, confirmation = password).canStart)
    }

    @Test
    fun differentConfirmationBlocksStart() {
        val state = ExportState(password = "correcthorse1", confirmation = "correcthorse2")

        assertTrue(state.confirmationMismatch)
        assertFalse(state.canStart)
    }

    @Test
    fun emptyConfirmationIsNotReportedAsMismatchButStillBlocksStart() {
        val state = ExportState(password = "correcthorse1")

        assertFalse(state.confirmationMismatch)
        assertFalse(state.canStart)
    }

    @Test
    fun runningExportCannotBeStartedAgain() {
        val state = ExportState(password = "correcthorse1", confirmation = "correcthorse1", phase = ExportPhase.Running)

        assertTrue(state.isBusy)
        assertFalse(state.canStart)
    }

    @Test
    fun strengthFollowsPassword() {
        assertEquals(0, ExportState().strength.score)
        assertTrue(
            ExportState(password = "Correct-Horse-9-Battery").strength.score >
                ExportState(password = "aaaaaaaaaaaa").strength.score
        )
    }
}
