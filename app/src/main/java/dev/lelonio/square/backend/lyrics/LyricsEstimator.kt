package dev.lelonio.square.backend.lyrics

import dev.lelonio.square.data.LyricLine
import dev.lelonio.square.data.Lyrics

/**
 * Converts plain unsynced lyrics text into auto-advancing lyrics by estimating
 * line timestamps based on song duration, word count, syllable density, punctuation,
 * and stanza musical pauses.
 */
object LyricsEstimator {

    private val VOWELS_REGEX = Regex("[aeiouyáéíóúüàèìòùâêîôûäëïöüãõ]+", RegexOption.IGNORE_CASE)
    private val ACCENTED_REGEX = Regex("[áéíóúüàèìòùâêîôûäëïöüãõ]", RegexOption.IGNORE_CASE)
    private val STRIP_REGEX = Regex("[^a-zA-ZáéíóúüàèìòùâêîôûäëïöüãõñÁÉÍÓÚÜÀÈÌÒÙÂÊÎÔÛÄËÏÖÜÃÕÑ]")

    private fun countSyllables(word: String): Int {
        val clean = STRIP_REGEX.replace(word, "").lowercase()
        if (clean.isEmpty()) return 1

        val matches = VOWELS_REGEX.findAll(clean).count()
        var count = if (matches == 0) 1 else matches

        // English silent 'e' heuristic: e.g. 'love', 'make', but keep 'me', 'the', 'little'
        if (clean.endsWith("e") && !clean.endsWith("le") && count > 1 && !ACCENTED_REGEX.containsMatchIn(clean)) {
            count--
        }
        return count.coerceAtLeast(1)
    }

    private data class ParsedLine(
        val text: String,
        val syllables: Int,
        val words: Int,
        val weight: Double,
        val isNewStanza: Boolean,
    )

    fun estimate(plainText: String, durationMs: Long): Lyrics {
        val rawLines = plainText.lines()
        val parsed = mutableListOf<ParsedLine>()
        var isNewStanza = false

        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) {
                isNewStanza = true
                continue
            }
            // Skip section headers like [Chorus], [Verse 1], but mark stanza pause
            if ((trimmed.startsWith('[') && trimmed.endsWith(']')) ||
                (trimmed.startsWith('(') && trimmed.endsWith(')') && trimmed.length < 25)
            ) {
                isNewStanza = true
                continue
            }

            val words = trimmed.split(Regex("\\s+")).filter { w -> w.any { it.isLetterOrDigit() } }
            val wordCount = words.size.coerceAtLeast(1)
            val syllables = words.sumOf { countSyllables(it) }

            var weight = wordCount * 1.5 + syllables * 2.5 + 4.0
            if (trimmed.endsWith('.') || trimmed.endsWith('?') || trimmed.endsWith('!') || trimmed.endsWith("...")) {
                weight += 1.5
            }

            parsed.add(
                ParsedLine(
                    text = trimmed,
                    syllables = syllables,
                    words = wordCount,
                    weight = weight,
                    isNewStanza = isNewStanza,
                ),
            )
            isNewStanza = false
        }

        if (parsed.isEmpty()) {
            return Lyrics(emptyList(), synced = false)
        }

        // If duration is unknown or non-positive, return standard unsynced lyrics.
        if (durationMs <= 0) {
            val lines = parsed.map { LyricLine(startTimeMs = null, text = it.text) }
            return Lyrics(lines, synced = false)
        }

        val totalWeight = parsed.sumOf { it.weight }.coerceAtLeast(1.0)
        val numStanzaBreaks = parsed.indices.count { i -> i > 0 && parsed[i].isNewStanza }

        // Dynamic margins based on track duration:
        // ~6.5% intro (between 5s and 16s)
        val introMs = (durationMs * 0.065).toLong().coerceIn(5000L, 16000L)
        // ~5.5% outro (between 4s and 14s)
        val outroMs = (durationMs * 0.055).toLong().coerceIn(4000L, 14000L)
        // Instrumental pauses between stanzas / verses (1.8s to 3.2s)
        val stanzaPauseMs = (durationMs * 0.015).toLong().coerceIn(1800L, 3200L)
        val totalStanzaPauses = numStanzaBreaks * stanzaPauseMs

        val vocalSpanMs = (durationMs - introMs - outroMs - totalStanzaPauses).coerceAtLeast(10000L)

        var currentMs = introMs
        val lines = parsed.mapIndexed { index, item ->
            if (index > 0 && item.isNewStanza) {
                currentMs += stanzaPauseMs
            }
            val lineDuration = ((item.weight / totalWeight) * vocalSpanMs).toLong().coerceIn(1200L, 8500L)
            val startTime = currentMs
            currentMs += lineDuration

            LyricLine(
                startTimeMs = startTime,
                text = item.text,
            )
        }

        return Lyrics(lines, synced = true)
    }
}
