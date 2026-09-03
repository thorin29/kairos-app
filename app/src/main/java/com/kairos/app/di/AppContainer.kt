package com.kairos.app.di

import android.content.Context
import com.kairos.app.data.appDataStore
import com.kairos.app.data.secure.TokenStore
import com.kairos.app.data.session.SessionRepository
import com.kairos.app.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Manual dependency container, built once in [com.kairos.app.KairosApp]. Chosen
 * over Hilt while the graph is small and codegen-free; revisit if/when
 * background work (WorkManager/FCM) needs injection.
 */
class AppContainer(context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dataStore = context.applicationContext.appDataStore

    val settingsStore = SettingsStore(dataStore)
    val tokenStore = TokenStore(dataStore)

    val sessionRepository = SessionRepository(
        settings = settingsStore,
        tokens = tokenStore,
        appScope = appScope,
    )

    /** Nav rail expanded/collapsed, session-scoped: survives navigation and
     *  drawer open/close, resets to collapsed when the app is relaunched. */
    val navExpanded = MutableStateFlow(false)
}
