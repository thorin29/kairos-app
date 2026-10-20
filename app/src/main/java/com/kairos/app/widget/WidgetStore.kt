package com.kairos.app.widget

import android.content.Context
import com.kairos.app.KairosApp
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.dto.WidgetDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the home-screen widget draws: the last feed from the server plus the
 * phone's own settings (theme, color, event style, whose events). One copy per
 * process, read by every placed widget; the feed is also kept in DataStore so
 * a widget can draw straight away, or offline.
 */
object WidgetStore {

    data class State(
        val data: WidgetDto?,
        /** "SOLID" filled boxes, or "BAR" (shown as Accent): a color bar, outlined tasks. */
        val style: String,
        /** "FAMILY" or "MINE". */
        val scope: String,
        /** "SYSTEM", "LIGHT" or "DARK": the widget's own setting, or the
         *  app's when the widget is set to match it. */
        val themeMode: String,
        /** A ThemeScheme name. */
        val scheme: String,
        /** Tasks ticked on the widget whose completion hasn't come back yet. */
        val done: Set<String> = emptySet(),
    )

    val state = MutableStateFlow<State?>(null)
    private val lock = Mutex()
    private var lastFetchAt = 0L

    private fun container(context: Context) =
        (context.applicationContext as KairosApp).container

    /** The current state, seeded once from settings and the cached feed. */
    suspend fun ensure(context: Context): State {
        state.value?.let { return it }
        val s = container(context).settingsStore
        val cached = s.widgetCache()?.let { json ->
            runCatching { ApiClient.json.decodeFromString(WidgetDto.serializer(), json) }.getOrNull()
        }
        val seeded = State(
            data = cached,
            style = s.widgetStyle.first(),
            scope = s.widgetScope.first(),
            themeMode = widgetMode(s),
            scheme = s.currentThemeScheme(),
        )
        state.compareAndSet(null, seeded)
        return state.value ?: seeded
    }

    /**
     * Re-read the settings and fetch the feed. Without [force], a fetch made in
     * the last minute is reused — placing a widget and the app's own refresh
     * would otherwise ask the server twice. Offline, the last feed stays up.
     */
    suspend fun refresh(context: Context, force: Boolean = false) = lock.withLock {
        ensure(context)
        val c = container(context)
        val s = c.settingsStore
        val scope = s.widgetScope.first()
        val scopeChanged = state.value?.scope != scope
        state.update {
            it?.copy(
                style = s.widgetStyle.first(),
                scope = scope,
                themeMode = widgetMode(s),
                scheme = s.currentThemeScheme(),
            )
        }
        val now = System.currentTimeMillis()
        if (!force && !scopeChanged && now - lastFetchAt < 60_000L) return@withLock
        val fresh = withTimeoutOrNull(15_000L) {
            c.sessionRepository.loadWidgetFromBackground(if (scope == "MINE") "mine" else "family")
        } ?: return@withLock
        lastFetchAt = now
        runCatching {
            s.setWidgetCache(ApiClient.json.encodeToString(WidgetDto.serializer(), fresh))
        }
        state.update { it?.copy(data = fresh, done = emptySet()) }
    }

    private suspend fun widgetMode(s: com.kairos.app.data.settings.SettingsStore): String {
        val own = s.widgetTheme.first()
        return if (own == "APP") s.currentThemeMode() else own
    }

    /** A task ticked on the widget: show it done before the server confirms. */
    fun markDone(taskId: String) {
        state.update { it?.copy(done = it.done + taskId) }
    }
}
