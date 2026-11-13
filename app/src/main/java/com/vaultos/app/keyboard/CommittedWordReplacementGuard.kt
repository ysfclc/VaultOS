package com.vaultos.app.keyboard

/**
 * Prevents a delayed spell-check callback from replacing text at a cursor position
 * different from the one where the checked word was committed.
 */
internal object CommittedWordReplacementGuard {
    fun canReplace(
        textBeforeCursor: CharSequence?,
        selectedText: CharSequence?,
        expectedWord: String
    ): Boolean {
        if (expectedWord.isBlank() || expectedWord.any(Char::isWhitespace)) return false
        if (!selectedText.isNullOrEmpty()) return false
        val expectedSuffix = "$expectedWord "
        return textBeforeCursor?.toString()?.endsWith(expectedSuffix, ignoreCase = true) == true
    }
}
