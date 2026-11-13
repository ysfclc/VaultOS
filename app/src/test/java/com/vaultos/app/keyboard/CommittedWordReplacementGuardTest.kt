package com.vaultos.app.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommittedWordReplacementGuardTest {
    @Test
    fun allowsReplacementWhenWordAndCursorAnchorStillMatch() {
        assertTrue(CommittedWordReplacementGuard.canReplace("I recieved ", "", "recieved", 12, 12))
    }

    @Test
    fun rejectsWhenCursorHasMovedAwayFromCheckedWord() {
        assertFalse(CommittedWordReplacementGuard.canReplace("I recieved today", "", "recieved", 12, 19))
    }

    @Test
    fun rejectsWhenSameWordOccursAtAnotherCursorPosition() {
        assertFalse(CommittedWordReplacementGuard.canReplace("I recieved ", "", "recieved", 12, 35))
    }

    @Test
    fun rejectsWhenTextIsSelected() {
        assertFalse(CommittedWordReplacementGuard.canReplace("I recieved ", "rec", "recieved", 12, 12))
    }

    @Test
    fun rejectsWhenExpectedWordDoesNotMatchCurrentText() {
        assertFalse(CommittedWordReplacementGuard.canReplace("I received ", "", "recieved", 12, 12))
    }

    @Test
    fun rejectsWhenCursorAnchorCannotBeVerified() {
        assertFalse(CommittedWordReplacementGuard.canReplace("I recieved ", "", "recieved", null, 12))
        assertFalse(CommittedWordReplacementGuard.canReplace("I recieved ", "", "recieved", 12, null))
    }

    @Test
    fun rejectsMissingOrInvalidExpectedWord() {
        assertFalse(CommittedWordReplacementGuard.canReplace(null, null, "", 12, 12))
        assertFalse(CommittedWordReplacementGuard.canReplace("I word ", null, "two words", 12, 12))
    }

    @Test
    fun comparisonIsCaseInsensitiveForLocaleIndependentLatinText() {
        assertTrue(CommittedWordReplacementGuard.canReplace("I RECIEVED ", null, "recieved", 12, 12))
    }
}
