package com.kairos.app

import android.app.Application
import com.kairos.app.di.AppContainer

class KairosApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Diagnostics for the freeze/crash class the breadcrumbs alone can't see.
        // Both are additive: they only ever append a line to the existing trail.
        // AppContainer's init wires Breadcrumbs, so these come after it.
        com.kairos.app.data.diag.CrashTrail.install()
        com.kairos.app.data.diag.StallWatchdog.start()
    }
}
