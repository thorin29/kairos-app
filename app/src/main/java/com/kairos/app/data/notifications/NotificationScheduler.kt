package com.kairos.app.data.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kairos.app.KairosApp

/**
 * Reconciles calendar reminders into exact alarms. On each run it fetches the
 * events with reminders in the next weeks, works out each reminder's fire time
 * (event start minus its minutes), schedules an exact alarm for the future ones,
 * and cancels any alarm no longer wanted (event moved, deleted, or reminder
 * removed). Gated by the master switch and the OS permission, so it's a no-op —
 * and clears everything — when the person hasn't opted in.
 */
object NotificationScheduler {

    suspend fun refresh(context: Context) {
        val container = (context.applicationContext as? KairosApp)?.container ?: return
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val settings = container.settingsStore

        val prefs = settings.currentNotifPrefs()
        val previous = settings.currentScheduledCodes()

        if ((!prefs.enabled && !prefs.tasksEnabled) || !Notifications.hasPermission(context)) {
            previous.forEach { cancel(context, am, it) }
            settings.setScheduledCodes(emptySet())
            return
        }

        val upcoming = runCatching { container.sessionRepository.loadUpcoming() }.getOrNull() ?: return
        val now = System.currentTimeMillis()

        val desired = HashMap<Int, Alarm>()
        if (prefs.enabled) {
            for (e in upcoming.events) {
                for (r in e.reminders) {
                    val at = e.startMs - r * 60_000L
                    if (at > now + 10_000L) {
                        desired[("${e.id}|$r").hashCode()] = Alarm(at, e.title, e.location, "calendar")
                    }
                }
            }
        }
        if (prefs.tasksEnabled) {
            for (t in upcoming.tasks) {
                val at = try {
                    java.time.LocalDate.parse(t.dueISO)
                        .atTime(t.minute / 60, t.minute % 60)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                } catch (_: Exception) {
                    continue
                }
                if (at > now + 10_000L) {
                    desired[("task|${t.id}").hashCode()] = Alarm(at, t.title, null, "tasks")
                }
            }
        }

        desired.forEach { (code, a) -> schedule(context, am, code, a) }
        (previous - desired.keys).forEach { cancel(context, am, it) }
        settings.setScheduledCodes(desired.keys)
    }

    private data class Alarm(val at: Long, val title: String, val location: String?, val route: String = "calendar")

    private fun intentFor(context: Context, code: Int, title: String, location: String?, route: String): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_NOTIF_ID, code)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_ROUTE, route)
            if (!location.isNullOrBlank()) putExtra(EXTRA_LOCATION, location)
        }

    private fun pending(context: Context, code: Int, title: String, location: String?, route: String, create: Boolean): PendingIntent? {
        val flags = (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
            PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, code, intentFor(context, code, title, location, route), flags)
    }

    private fun schedule(context: Context, am: AlarmManager, code: Int, a: Alarm) {
        val pi = pending(context, code, a.title, a.location, a.route, create = true) ?: return
        val at = a.at
        val canExact =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(context: Context, am: AlarmManager, code: Int) {
        pending(context, code, "", null, "calendar", create = false)?.let {
            am.cancel(it)
            it.cancel()
        }
    }

    const val EXTRA_NOTIF_ID = "notifId"
    const val EXTRA_TITLE = "title"
    const val EXTRA_LOCATION = "location"
    const val EXTRA_ROUTE = "route"
}
