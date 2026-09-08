package com.kairos.app.di

import android.content.Context
import coil.ImageLoader
import com.kairos.app.data.appDataStore
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.AuthInterceptor
import com.kairos.app.data.remote.NetworkMonitor
import com.kairos.app.data.remote.SyncManager
import com.kairos.app.data.remote.WriteQueue
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
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

    /** Tracks connectivity; drives the offline banner and the read-through cache. */
    val networkMonitor = NetworkMonitor(context.applicationContext, appScope)

    /** Disk cache for GET responses so screens still render their last-synced
     *  data when offline. 15 MB is plenty for JSON. */
    private val httpCache = Cache(File(context.applicationContext.cacheDir, "api-cache"), 15L * 1024 * 1024)

    /** Writes made while offline, persisted and replayed on reconnect. */
    val writeQueue = WriteQueue(dataStore, ApiClient.json)

    /** Replays [writeQueue] when connectivity returns; exposes sync status. */
    val syncManager = SyncManager(
        queue = writeQueue,
        monitor = networkMonitor,
        cache = httpCache,
        tokenProvider = { tokenStore.current() },
        scope = appScope,
    )

    val sessionRepository = SessionRepository(
        settings = settingsStore,
        tokens = tokenStore,
        appScope = appScope,
        httpCache = httpCache,
        networkMonitor = networkMonitor,
        writeQueue = writeQueue,
    )

    /** Coil loader for device-authed avatars: reuses the same bearer token as
     *  the API so photos behind /api/v1 load with the right Authorization. */
    val imageLoader: ImageLoader = ImageLoader.Builder(context.applicationContext)
        .okHttpClient(
            OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor { tokenStore.current() })
                .build(),
        )
        .build()

    /** Nav rail expanded/collapsed, session-scoped: survives navigation and
     *  drawer open/close, resets to collapsed when the app is relaunched. */
    val navExpanded = MutableStateFlow(false)
}
