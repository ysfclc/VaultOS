package com.vaultos.app.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Repeatable JVM microbenchmark for the KBD-1 correction hot path.
 *
 * This records distributions rather than asserting a wall-clock threshold, so
 * noisy shared CI machines do not create flaky failures. Run with:
 * ./gradlew :app:testDebugUnitTest --tests '*KeyboardSuggestionEngineBenchmarkTest'
 */
class KeyboardSuggestionEngineBenchmarkTest {
    @Test
    fun correctionRankingLatencyWithMaximumPersonalVocabulary() {
        val personalVocabulary = buildList {
            add("recommendations")
            repeat(499) { index ->
                add("personalword" + index.toString().padStart(3, '0'))
            }
        }
        val samples = LongArray(1_000)

        repeat(150) {
            KeyboardSuggestionEngine.nearbyCorrections(
                language = "en",
                input = "recommendatins",
                additionalWords = personalVocabulary
            )
        }
        samples.indices.forEach { index ->
            val started = System.nanoTime()
            KeyboardSuggestionEngine.nearbyCorrections(
                language = "en",
                input = "recommendatins",
                additionalWords = personalVocabulary
            )
            samples[index] = System.nanoTime() - started
        }

        samples.sort()
        val p50Micros = samples[samples.size / 2] / 1_000
        val p95Micros = samples[(samples.size * 95) / 100] / 1_000
        println("KBD1_BENCHMARK correction_500_words p50_us=$p50Micros p95_us=$p95Micros")
        assertEquals(1_000, samples.size)
    }
}
