package dev.lelonio.square.playback

import android.util.Log
import dev.lelonio.square.data.Quality

/**
 * What the connection can actually carry, judged by listening rather than asking.
 *
 * The system's own estimate answers a different question: it reports the speed
 * of the radio link, which stays high behind a router that is throttling, a
 * captive network that is shaping, or a plan that has run out of fast data. A
 * phone can sit on a 300 Mbps wi-fi and receive 200 kbps, and the setting that
 * exists precisely for that case never noticed.
 *
 * So this watches what happens to the music. Two facts, both free:
 *
 * A **stall** is the honest one. If the sound stops while the player still
 * means to be playing, the buffer ran dry: whatever anyone estimated, it was
 * too much. One stall steps down immediately.
 *
 * A **slow load** is the early warning. The time between asking for a track and
 * hearing it is mostly the time spent fetching the first seconds of it, so a
 * load that takes longer than a track's worth of patience says the link is
 * behind before the music has had to stop.
 *
 * Coming back up is deliberately reluctant: several tracks in a row have to
 * load quickly and play through before the quality rises, because a link that
 * has just failed to carry 320 will happily fail again, and a listener notices
 * oscillation far more than they notice one step of bitrate.
 *
 * Nothing here rebuilds anything. The engine reads its bitrate when a track
 * loads, so a decision costs a message and is heard on the next song.
 */
class BandwidthWatch(
    private val onStep: (Int) -> Unit,
    private val onUnstable: (() -> Unit)? = null,
) {
    /** The step in use, in kbps: one of [Quality]'s three fixed values. */
    var current: Int = Quality.High.kbps
        private set

    private var loadStartedAt = 0L
    private var loadingUri: String? = null

    /** Tracks that have loaded quickly and played through since the last fall. */
    private var goodRun = 0

    /** Number of buffer stalls recorded. */
    private var stallCount = 0
    private var lastStallTime = 0L

    /** Where the ladder starts, when playback begins with no history. */
    fun start(from: Int) {
        current = from
        goodRun = 0
        stallCount = 0
    }

    /** The engine has begun loading [uri]. */
    fun loading(uri: String, now: Long = System.currentTimeMillis()) {
        if (loadingUri == uri) return
        loadingUri = uri
        loadStartedAt = now
    }

    /** [uri] is now playing: whatever it took to get here is the measurement. */
    fun playing(uri: String, now: Long = System.currentTimeMillis()) {
        val started = loadStartedAt
        if (loadingUri != uri || started == 0L) return
        loadingUri = null
        val took = now - started
        if (took >= UNSTABLE_LOAD_MS) {
            Log.w(TAG, "load took ${took}ms (unstable network): triggering cache/offline fallback")
            onUnstable?.invoke()
        } else if (took >= SLOW_LOAD_MS) {
            Log.i(TAG, "load took ${took}ms, stepping down")
            stepDown()
        } else {
            goodRun++
            if (goodRun >= 2) stallCount = 0
            if (goodRun >= GOOD_TRACKS_BEFORE_RISING) stepUp()
        }
    }

    /**
     * A track load failed over the network (e.g. timeout or unavailable).
     */
    fun loadFailed(uri: String? = null) {
        loadingUri = null
        loadStartedAt = 0L
        stallCount++
        Log.w(TAG, "track load failed over network ($uri), triggering cache/offline fallback")
        onUnstable?.invoke()
    }

    /**
     * The music stopped while it meant to be playing.
     *
     * Reported by whoever is watching the position rather than measured here:
     * the player is the only thing that knows the difference between a buffer
     * that ran dry and a listener who pressed pause.
     */
    fun stalled(now: Long = System.currentTimeMillis()) {
        val timeSinceLast = now - lastStallTime
        lastStallTime = now
        stallCount++
        Log.i(TAG, "the buffer ran dry (stallCount=$stallCount, timeSinceLast=${timeSinceLast}ms), stepping down")

        // If already at lowest bitrate (96 kbps) and stalled again,
        // or if multiple stalls occur in a short time frame (< 60s),
        // the network cannot sustain streaming: fall back to cache/downloads.
        if (current == Quality.Low.kbps || (stallCount >= 2 && timeSinceLast < 60_000L)) {
            Log.w(TAG, "connection cannot sustain streaming: notifying unstable network")
            onUnstable?.invoke()
            return
        }

        stepDown()
    }

    private fun stepDown() {
        goodRun = 0
        val next = when (current) {
            Quality.High.kbps -> Quality.Medium.kbps
            Quality.Medium.kbps -> Quality.Low.kbps
            else -> return
        }
        apply(next)
    }

    private fun stepUp() {
        goodRun = 0
        val next = when (current) {
            Quality.Low.kbps -> Quality.Medium.kbps
            Quality.Medium.kbps -> Quality.High.kbps
            else -> return
        }
        apply(next)
    }

    private fun apply(kbps: Int) {
        if (kbps == current) return
        current = kbps
        Log.i(TAG, "asking for $kbps kbps from the next track")
        onStep(kbps)
    }

    private companion object {
        const val TAG = "SquareBandwidth"

        /**
         * A load slower than this is taken as a link that cannot keep up.
         *
         * Generous on purpose: it also covers the access point answering, the
         * key exchange and the first seconds of audio, and none of that is
         * instant even on a good connection. What it must not do is fire on a
         * link that is merely ordinary.
         */
        const val SLOW_LOAD_MS = 6_000L

        /** A load taking 10s or more means the link is actively degraded/unstable. */
        const val UNSTABLE_LOAD_MS = 10_000L

        /** How many quick, unbroken tracks it takes to try the step above. */
        const val GOOD_TRACKS_BEFORE_RISING = 4
    }
}
