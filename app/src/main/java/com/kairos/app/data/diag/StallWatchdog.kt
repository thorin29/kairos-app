package com.kairos.app.data.diag

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.concurrent.thread

/**
 * Watches the UI thread and records it when it stops answering.
 *
 * A frozen screen has two very different causes and they look identical to the
 * person holding the phone: the UI thread is blocked (nothing can be processed),
 * or the UI thread is fine and the *window* is drawing blank while composition,
 * navigation and network carry on underneath. The September 2026 white screen was
 * the second kind, proven only because the breadcrumbs showed navigation still
 * happening behind the blank window.
 *
 * This separates the two without having to guess next time:
 *
 *  - watchdog fires  -> the UI thread was blocked, and the breadcrumb carries the
 *                       main-thread stack, which names the blocking call.
 *  - watchdog silent -> the UI thread was alive the whole time, so a blank screen
 *                       was a render-layer problem, not a stall.
 *
 * Deliberately crude: one background thread, a ping every [PING_MS], and a
 * breadcrumb if the answer takes longer than [STALL_MS]. It records at most one
 * breadcrumb per stall (plus one when it recovers), so a long freeze can't fill
 * the ring buffer with its own noise.
 */
object StallWatchdog {
    private const val PING_MS = 2_000L
    private const val STALL_MS = 5_000L
    /** Frames to keep from the main-thread stack — enough to name the caller. */
    private const val FRAMES = 12

    @Volatile private var started = false
    @Volatile private var lastAck = 0L

    fun start() {
        if (started) return
        started = true
        val main = Handler(Looper.getMainLooper())
        lastAck = SystemClock.uptimeMillis()

        thread(name = "kairos-stall-watchdog", isDaemon = true) {
            var reported = false
            var stallStart = 0L
            while (true) {
                val sent = SystemClock.uptimeMillis()
                main.post { lastAck = SystemClock.uptimeMillis() }
                Thread.sleep(PING_MS)

                val waited = SystemClock.uptimeMillis() - lastAck
                if (waited >= STALL_MS) {
                    if (!reported) {
                        reported = true
                        stallStart = sent
                        // Snapshot the UI thread exactly where it is stuck. This is
                        // the whole point of the watchdog: the stack names the call.
                        val frames = runCatching {
                            Looper.getMainLooper().thread.stackTrace
                                .take(FRAMES)
                                .joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}" }
                        }.getOrNull() ?: "stack unavailable"
                        Breadcrumbs.drop("MAIN THREAD STALLED ${waited / 1000}s :: $frames")
                    }
                } else if (reported) {
                    reported = false
                    val total = (SystemClock.uptimeMillis() - stallStart) / 1000
                    Breadcrumbs.drop("main thread recovered after ${total}s")
                }
            }
        }
    }
}
