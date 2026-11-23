package com.kairos.app

import android.app.Application
import com.kairos.app.di.AppContainer

class KairosApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Installed BEFORE the container: a crash while building it (as in
        // 0.350.0) happens with no handler in place otherwise, which is exactly
        // when a trace is most useful. Breadcrumbs may not be wired yet at that
        // point, so the write is best-effort.
        com.kairos.app.data.diag.CrashTrail.install()
        container = AppContainer(this)
        // Firebase from the values this server handed over last time (none are
        // built in), so a "check your inbox" ping can start the app.
        com.kairos.app.data.push.Push.startFromCache(this)
        com.kairos.app.data.diag.StallWatchdog.start()
    }
}
