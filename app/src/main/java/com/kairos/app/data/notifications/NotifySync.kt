package com.kairos.app.data.notifications

import android.content.Context
import com.kairos.app.data.remote.dto.CalendarChoicesDto
import com.kairos.app.data.remote.dto.NotifyPrefsDto

/**
 * Keeps this phone's alert switches and the person's saved choices together
 * (v0.553). On start: when the server has choices, this phone takes them
 * (a new phone gets the person's settings); when it has none yet, this phone
 * sends its own switches, so nobody gains or loses alerts with the update.
 */
object NotifySync {
    suspend fun run(context: Context) {
        val container = (context.applicationContext as? com.kairos.app.KairosApp)?.container ?: return
        val res = container.sessionRepository.notifyPrefsFromBackground() ?: return
        val local = container.settingsStore.currentNotifPrefs()
        if (res.saved) {
            apply(context, res.prefs)
        } else {
            container.sessionRepository.saveNotifyPrefs(
                res.prefs.copy(
                    calendar = CalendarChoicesDto(mine = local.enabled, others = local.enabled),
                    tasks = local.tasksEnabled,
                ),
            )
        }
    }

    /** This phone's switches from the person's choices, then reschedule. */
    suspend fun apply(context: Context, prefs: NotifyPrefsDto) {
        val container = (context.applicationContext as? com.kairos.app.KairosApp)?.container ?: return
        val next = NotifPrefs(enabled = prefs.calendar.mine || prefs.calendar.others, tasksEnabled = prefs.tasks)
        if (next != container.settingsStore.currentNotifPrefs()) {
            container.settingsStore.setNotifPrefs(next)
        }
        NotificationWorker.enqueueOnce(context)
    }
}
