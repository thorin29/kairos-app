package com.kairos.app.ui.workout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kairos.app.MainActivity
import com.kairos.app.R
import com.kairos.app.ui.common.rememberContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * A running timer outside the timer screen (v0.438): a notification with a
 * live countdown (the notification shade, the lock screen, and on Android 16+
 * a chip in the status bar), and a floating picture-in-picture window with just
 * the time. The timer screens publish what's running; the notification's
 * countdown ticks by itself, so they only publish when the step changes.
 * Running as a foreground service also keeps the timer's beeps going while
 * the app is in the background.
 */

/** What a running timer shows outside the app. */
internal data class LiveTimer(
    /** The exercise or workout ("Front plank", "Cindy"). */
    val title: String,
    /** "Go", "Get ready", "Rest", "Paused". */
    val status: String,
    val next: String? = null,
    /** The time as shown now, for a paused timer. */
    val clock: String,
    /** Wall-clock millis the current countdown reaches 0 (a running countdown). */
    val endAt: Long? = null,
    /** Wall-clock millis a running stopwatch started from. */
    val startedAt: Long? = null,
    val running: Boolean,
)

internal object TimerLive {
    private val _current = MutableStateFlow<LiveTimer?>(null)
    val current: StateFlow<LiveTimer?> = _current.asStateFlow()

    /** The app is showing as the floating window: timer screens draw only the time. */
    val inPip = MutableStateFlow(false)

    /** BOTH, NOTIFY, FLOAT or OFF (SettingsStore.timerAway), kept current by the timer screens. */
    val away = MutableStateFlow("BOTH")

    fun publish(context: Context, t: LiveTimer) {
        _current.value = t
        val notify = away.value == "BOTH" || away.value == "NOTIFY"
        if (notify && TimerService.canPost(context)) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
            }
        }
    }

    fun clear(context: Context) {
        if (_current.value == null) return
        _current.value = null
        runCatching { context.stopService(Intent(context, TimerService::class.java)) }
    }

    /** Wall-clock millis for a moment on the elapsed-realtime clock the timers use. */
    fun wallAt(elapsedRealtimeMs: Long): Long =
        System.currentTimeMillis() + (elapsedRealtimeMs - SystemClock.elapsedRealtime())
}

/** The notification for a running timer; stops itself when the timer clears. */
class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val t = TimerLive.current.value
        if (t == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, build(this, t), type) }
            .onFailure { stopSelf(); return START_NOT_STICKY }
        scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
        scope.launch {
            TimerLive.current.collect { now ->
                if (now == null) {
                    ServiceCompat.stopForeground(this@TimerService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, build(this@TimerService, now))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "workout_timer"
        private const val NOTIFICATION_ID = 4107

        fun canPost(context: Context): Boolean = com.kairos.app.data.notifications.Notifications.hasPermission(context)

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                // Silent: the timer itself beeps; the notification just shows it.
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Workout timer", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "A running workout timer while you're in another app."
                        setShowBadge(false)
                    },
                )
            }
        }

        private fun build(context: Context, t: LiveTimer): Notification {
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val b = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL_ID) else Notification.Builder(context))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (t.running) "${t.title} · ${t.status}" else "${t.title} · ${t.clock} · ${t.status}")
                .setContentText(t.next?.let { "Next: $it" } ?: "")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
            when {
                t.running && t.endAt != null -> {
                    b.setUsesChronometer(true).setWhen(t.endAt).setShowWhen(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) b.setChronometerCountDown(true)
                }
                t.running && t.startedAt != null -> b.setUsesChronometer(true).setWhen(t.startedAt).setShowWhen(true)
                else -> b.setShowWhen(false)
            }
            // Android 16+: ask for a Live Update (the status-bar chip). Called by
            // name, so older SDKs and phones simply skip it.
            if (Build.VERSION.SDK_INT >= 36) {
                runCatching {
                    b.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType).invoke(b, true)
                }
                if (!t.running) {
                    runCatching { b.javaClass.getMethod("setShortCriticalText", String::class.java).invoke(b, t.clock) }
                }
            }
            return b.build()
        }
    }
}

/** Number styles for the timer (SettingsStore.timerFont). */
internal val TIMER_FONTS = listOf(
    "STANDARD" to "Standard",
    "CONDENSED" to "Condensed",
    "MONO" to "Monospace",
    "SERIF" to "Serif",
)

/** Number colours for the timer (SettingsStore.timerColor). */
internal val TIMER_COLORS = listOf(
    "PLAIN" to "Plain",
    "ACCENT" to "Theme colour",
    "AMBER" to "Amber",
    "GREEN" to "Green",
    "RED" to "Red",
    "BLUE" to "Blue",
)

/** What a running timer does when you leave the app (SettingsStore.timerAway). */
internal val TIMER_AWAY = listOf(
    "BOTH" to "Notification and floating window",
    "NOTIFY" to "Notification",
    "FLOAT" to "Floating window",
    "OFF" to "Nothing",
)

/** The timer's number font and colour, from Settings → Appearance. */
internal data class TimerDigits(val font: FontFamily, val color: Color?)

@Composable
internal fun timerDigits(pal: TimerPalette): TimerDigits {
    val settings = rememberContainer().settingsStore
    val font by settings.timerFont.collectAsState(initial = "STANDARD")
    val color by settings.timerColor.collectAsState(initial = "PLAIN")
    val away by settings.timerAway.collectAsState(initial = "BOTH")
    androidx.compose.runtime.SideEffect { TimerLive.away.value = away }
    val family = when (font) {
        "CONDENSED" -> FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
        "MONO" -> FontFamily.Monospace
        "SERIF" -> FontFamily.Serif
        else -> FontFamily.Default
    }
    val tint = when (color) {
        "ACCENT" -> pal.accent
        "AMBER" -> Color(0xFFF59E0B)
        "GREEN" -> Color(0xFF22C55E)
        "RED" -> Color(0xFFEF4444)
        "BLUE" -> Color(0xFF3B82F6)
        else -> null
    }
    return TimerDigits(font = family, color = tint)
}

/** The floating window: just the time, as large as the window allows, and a
 *  word of status under it. A floating window can't be see-through (Android
 *  draws it as a solid tile), so it's kept to the digits on the timer's colour. */
@Composable
internal fun PipTimer(clock: String, status: String, color: Color, pal: TimerPalette, digits: TimerDigits) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize().background(pal.bg),
        contentAlignment = Alignment.Center,
    ) {
        val byWidth = maxWidth.value / (clock.length * 0.62f)
        val byHeight = maxHeight.value * 0.55f
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                clock,
                color = digits.color ?: color,
                fontFamily = digits.font,
                fontSize = minOf(byWidth, byHeight).coerceAtLeast(12f).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (status.isNotBlank() && maxHeight.value > 90f) {
                Text(status, color = pal.soft, fontSize = 12.sp)
            }
        }
    }
}
