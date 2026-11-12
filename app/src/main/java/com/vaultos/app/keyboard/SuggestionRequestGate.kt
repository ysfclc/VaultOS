package com.vaultos.app.keyboard

/**
 * Main-thread-owned generation gate for asynchronous keyboard candidate work.
 * A new render or editor lifecycle transition invalidates all earlier requests.
 */
internal class SuggestionRequestGate {
    private var generation: Long = 0L

    fun issue(): Long {
        generation += 1L
        return generation
    }

    fun invalidate(): Long {
        generation += 1L
        return generation
    }

    fun isCurrent(token: Long): Boolean = token == generation
}
