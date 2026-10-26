package com.rerun.tv.ui

import android.os.SystemClock
import java.util.TimeZone

/**
 * Detects wall-clock or time-zone changes without a broadcast receiver. Callers poll it at points
 * they already wake up (guide ticks, resume); a change shows up as wall time moving differently
 * from elapsed time since the previous check.
 */
class ClockWatch(
    private val wall: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val zone: () -> String = { TimeZone.getDefault().id },
    private val toleranceMs: Long = 5_000,
) {
    private var lastWall = wall()
    private var lastElapsed = elapsed()
    private var lastZone = zone()

    /** True when the clock was set, or the zone changed, since the previous call. */
    @Synchronized
    fun changed(): Boolean {
        val w = wall()
        val e = elapsed()
        val z = zone()
        val drift = (w - lastWall) - (e - lastElapsed)
        val moved = kotlin.math.abs(drift) > toleranceMs || z != lastZone
        lastWall = w
        lastElapsed = e
        lastZone = z
        return moved
    }
}
