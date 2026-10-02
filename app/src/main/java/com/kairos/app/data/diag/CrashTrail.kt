package com.kairos.app.data.diag

/**
 * Writes an uncaught exception into the breadcrumb trail before letting the
 * process die, so a crash leaves the same evidence a freeze does: it shows up
 * under Settings -> Diagnostics on the next launch, next to the navigation that
 * led to it.
 *
 * Without this, a crash on a background thread is indistinguishable from a
 * freeze after the fact — both end with the user reopening the app — and the
 * September investigation wasted time on exactly that ambiguity.
 *
 * The previous handler is always called, so Android's own crash reporting and
 * the system log are unaffected.
 */
object CrashTrail {
    @Volatile private var installed = false

    fun install() {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val top = error.stackTrace.take(6).joinToString(" <- ") {
                    "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
                }
                Breadcrumbs.drop(
                    "CRASH on ${thread.name}: ${error::class.java.simpleName}: " +
                        "${error.message.orEmpty().take(120)} :: $top",
                )
                // Breadcrumbs persists asynchronously and the process is about
                // to go; give that write a moment rather than losing the one line
                // that explains the next "it just closed". The process is dying
                // either way, so pausing the main thread here costs nothing.
                Thread.sleep(250)
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
