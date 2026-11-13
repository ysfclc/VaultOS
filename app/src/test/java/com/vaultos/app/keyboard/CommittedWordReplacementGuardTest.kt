package com.vaultos.app.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommittedWordReplacementGuardTest {
    @Test
    fun allowsReplacementWhenExpectedWordAndSpaceAreImmediatelyBeforeCursor() {
        assertTrue(CommittedWordReplacementGuard.canReplace("I recieved ", "", "recieved"))
    }

    @Test
    fun rejectsWhenCursorHasMovedAwayFromCheckedWord() {
        assertFalse(CommittedWordReplacementGuard.canReplace("I recieved today", "", "recieved"))
    }

    @Test
    fun rejectsWhenTextIsSelected() {
        assertFalse(CommittedWordReplacementGuard.canReplace("I recieved ", "rec", "recieved"))
    }

    @Test
    fun rejectsWhenExpectedWordDoesNotMatchCurrentText() {
        assertFalse(CommittedWordReplacementGuard.canReplace("I received ", "", "recieved"))
    }

    @Test
    fun rejectsMissingOrInvalidExpectedWord() {
        assertFalse(CommittedWordReplacementGuard.canReplace(null, null, ""))
        assertFalse(CommittedWordReplacementGuard.canReplace("I word ", null, "two words"))
    }

    @Test
    fun comparisonIsCaseInsensitiveForLocaleIndependentLatinText() {
        assertTrue(CommittedWordReplacementGuard.canReplace("I RECIEVED ", null, "recieved"))
    }
}
