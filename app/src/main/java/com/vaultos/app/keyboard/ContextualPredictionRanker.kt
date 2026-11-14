package com.vaultos.app.keyboard

/**
 * Stable candidate precedence for private context prediction.
 * Exact two-word evidence leads, but a sparse trigram must not erase useful
 * adjacent-word evidence. Deduplicate while filling the remaining candidate slots.
 */
internal object ContextualPredictionRanker {
    fun rank(
        trigramCandidates: List<String>,
        bigramCandidates: List<String>,
        limit: Int = 3
    ): List<String> {
        if (limit <= 0) return emptyList()
        return (trigramCandidates.asSequence() + bigramCandidates.asSequence())
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(limit)
            .toList()
    }
}
