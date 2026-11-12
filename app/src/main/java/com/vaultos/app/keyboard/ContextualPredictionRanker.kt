package com.vaultos.app.keyboard

/**
 * Deterministic precedence rule for private context prediction.
 * An exact two-word context wins when it has evidence; otherwise retain v1 bigram
 * behavior. Candidate lists are bounded and deduplicated before reaching the IME.
 */
internal object ContextualPredictionRanker {
    fun rank(
        trigramCandidates: List<String>,
        bigramCandidates: List<String>,
        limit: Int = 3
    ): List<String> {
        if (limit <= 0) return emptyList()
        val preferred = trigramCandidates.takeIf { it.isNotEmpty() } ?: bigramCandidates
        return preferred.asSequence().distinct().take(limit).toList()
    }
}
