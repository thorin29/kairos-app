package com.kairos.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.kairos.app.KairosApp
import com.kairos.app.di.AppContainer

/** Pulls the app-wide manual DI container inside composables. */
@Composable
fun rememberContainer(): AppContainer {
    val app = LocalContext.current.applicationContext as KairosApp
    return app.container
}
