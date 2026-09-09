package com.kairos.app.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the calendar shows a 24-hour clock. Snapshot-backed so the time axis
 * and labels re-compose when it changes. Written by MainActivity from the
 * per-device setting; read by the calendar's time formatters.
 */
object TimeFmt {
    var military by mutableStateOf(false)

    /** "13" / "00" (24h) or "1 PM" / "12 AM" (12h) for the hour axis. */
    fun hour(h: Int): String =
        if (military) "%02d".format(h)
        else when {
            h == 0 -> "12 AM"
            h < 12 -> "$h AM"
            h == 12 -> "12 PM"
            else -> "${h - 12} PM"
        }

    /** "14:05" (24h) or "2:05 PM" (12h) for a minute-of-day. */
    fun clock(minuteOfDay: Int): String {
        val h = (minuteOfDay / 60) % 24
        val m = minuteOfDay % 60
        return if (military) {
            "%02d:%02d".format(h, m)
        } else {
            val ampm = if (h < 12) "AM" else "PM"
            val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
            "%d:%02d %s".format(h12, m, ampm)
        }
    }
}
