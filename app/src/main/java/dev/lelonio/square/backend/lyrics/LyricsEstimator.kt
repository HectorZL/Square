package dev.lelonio.square.backend.lyrics

import dev.lelonio.square.data.LyricLine
import dev.lelonio.square.data.Lyrics

/**
 * Converts plain unsynced lyrics text into auto-advancing lyrics by estimating
 * line timestamps based on song duration, character density, and typical song structure.
 */
object LyricsEstimator {

    fun estimate(plainText: String, durationMs: Long): Lyrics {
        val rawLines = plainText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (rawLines.isEmpty()) {
            return Lyrics(emptyList(), synced = false)
        }

        // If duration is unknown or non-positive, return standard unsynced lyrics.
        if (durationMs <= 0) {
            val lines = rawLines.map { LyricLine(startTimeMs = null, text = it) }
            return Lyrics(lines, synced = false)
        }

        // Music intro and outro margins:
        // ~5% intro (clamped between 3s and 10s)
        val introMs = (durationMs * 0.05).toLong().coerceIn(3000L, 10000L)
        // ~6% outro (clamped between 4s and 12s)
        val outroMs = (durationMs * 0.06).toLong().coerceIn(4000L, 12000L)
        val vocalSpanMs = (durationMs - introMs - outroMs).coerceAtLeast(10000L)

        // Base weight per line (15 chars worth of phrase pause / cadence) + character count.
        val weights = rawLines.map { 15 + it.length }
        val totalWeight = weights.sum().toDouble()

        var currentMs = introMs
        val lines = rawLines.mapIndexed { index, text ->
            val lineDuration = ((weights[index] / totalWeight) * vocalSpanMs).toLong().coerceAtLeast(1200L)
            val startTime = currentMs
            currentMs += lineDuration
            LyricLine(
                startTimeMs = startTime,
                text = text,
            )
        }

        return Lyrics(lines, synced = true)
    }
}
