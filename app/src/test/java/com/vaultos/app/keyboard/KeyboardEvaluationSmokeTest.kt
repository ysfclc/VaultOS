package com.vaultos.app.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Diagnostic smoke benchmark. Curated fixtures exercise known engine paths;
 * they are not representative language-quality measurements and impose no quality
 * threshold. Raw counts are printed to make later engine comparisons repeatable.
 */
class KeyboardEvaluationSmokeTest {
    private data class Case(
        val id: String,
        val locale: String,
        val task: String,
        val context: String,
        val input: String,
        val expected: String
    )

    @Test
    fun reportsDeterministicPerLocaleSmokeMetrics() {
        val stream = javaClass.classLoader?.getResourceAsStream("keyboard/keyboard-eval-v1.tsv")
            ?: error("Missing keyboard-eval-v1.tsv test resource")
        val rows = stream.bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map { line ->
                val fields = line.split('\t')
                require(fields.size == 7) { "Malformed evaluation row: $line" }
                Case(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5])
            }.toList()
        }
        assertEquals(25, rows.size)

        listOf("en-US", "pl-PL", "tr-TR").forEach { locale ->
            val code = locale.substringBefore('-')
            val next = rows.filter { it.locale == locale && it.task == "next" }
            val prefix = rows.filter { it.locale == locale && it.task == "prefix" }
            val corrections = rows.filter { it.locale == locale && it.task == "correction" }
            val controls = rows.filter { it.locale == locale && it.task == "noisy-control" }

            fun candidates(case: Case): List<String> = when (case.task) {
                "next" -> {
                    val words = case.context.split(' ').filter { it.isNotBlank() }
                    if (words.size >= 2) KeyboardSuggestionEngine.nextWordSuggestions(code, words[words.lastIndex - 1], words.last())
                    else KeyboardSuggestionEngine.nextWordSuggestions(code, words.lastOrNull().orEmpty())
                }
                "prefix" -> KeyboardSuggestionEngine.completePrefix(code, case.input)
                "correction" -> KeyboardSuggestionEngine.nearbyCorrections(code, case.input)
                else -> emptyList()
            }

            fun hits(cases: List<Case>, limit: Int): Int =
                cases.count { case -> candidates(case).take(limit).any { it.equals(case.expected, ignoreCase = true) } }

            val nextTop1 = hits(next, 1)
            val nextTop3 = hits(next, 3)
            val prefixTop1 = hits(prefix, 1)
            val prefixTop3 = hits(prefix, 3)
            val correctionTop1 = hits(corrections, 1)
            val correctionTop3 = hits(corrections, 3)
            val safeControls = controls.count { KeyboardSuggestionEngine.nearbyCorrections(code, it.input).isEmpty() }

            println(
                "KEYBOARD_SMOKE locale=$locale " +
                    "next_top1=$nextTop1/${next.size} next_top3=$nextTop3/${next.size} " +
                    "prefix_top1=$prefixTop1/${prefix.size} prefix_top3=$prefixTop3/${prefix.size} " +
                    "correction_top1=$correctionTop1/${corrections.size} " +
                    "correction_top3=$correctionTop3/${corrections.size} " +
                    "safe_controls=$safeControls/${controls.size}"
            )
            assertTrue("Missing next-word cases for $locale", next.isNotEmpty())
            assertTrue("Missing prefix cases for $locale", prefix.isNotEmpty())
            assertTrue("Missing correction cases for $locale", corrections.isNotEmpty())
            assertEquals("Correct-token controls must not produce corrections for $locale", controls.size, safeControls)
        }
    }
}
