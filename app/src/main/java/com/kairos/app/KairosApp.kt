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
        com.kairos.app.data.diag.StallWatchdog.start()
    }
}
