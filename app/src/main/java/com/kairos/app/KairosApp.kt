package com.kairos.app

import android.app.Application
import com.kairos.app.di.AppContainer

class KairosApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
