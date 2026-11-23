package com.kairos.app.data.notifications

import android.content.Context
import com.kairos.app.data.remote.dto.CalendarChoicesDto
import com.kairos.app.data.remote.dto.NotifyPrefsDto
import kotlinx.coroutines.flow.first

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
        val localDefaults = container.settingsStore.reminderDefaults.first()
        if (res.saved) {
            apply(context, res.prefs)
            // Default reminders follow the person (v0.556): the saved ones win;
            // a phone with its own and none saved yet sends them up.
            if (res.prefs.reminderDefaults.isNotEmpty()) {
                for ((kind, minutes) in res.prefs.reminderDefaults) {
                    if (localDefaults[kind] != minutes) container.settingsStore.setReminderDefault(kind, minutes)
                }
            } else if (localDefaults.isNotEmpty()) {
                container.sessionRepository.saveNotifyPrefs(res.prefs.copy(reminderDefaults = localDefaults))
            }
        } else {
            container.sessionRepository.saveNotifyPrefs(
                res.prefs.copy(
                    calendar = CalendarChoicesDto(mine = local.enabled, family = local.enabled, others = local.enabled),
                    tasks = local.tasksEnabled,
                    reminderDefaults = localDefaults,
                ),
            )
        }
    }

    /** After a default reminder changes on this phone: save it for the person. */
    suspend fun pushDefaults(context: Context) {
        val container = (context.applicationContext as? com.kairos.app.KairosApp)?.container ?: return
        val res = container.sessionRepository.notifyPrefsFromBackground() ?: return
        val localDefaults = container.settingsStore.reminderDefaults.first()
        container.sessionRepository.saveNotifyPrefs(res.prefs.copy(reminderDefaults = localDefaults))
    }

    /** This phone's switches from the person's choices, then reschedule. */
    suspend fun apply(context: Context, prefs: NotifyPrefsDto) {
        val container = (context.applicationContext as? com.kairos.app.KairosApp)?.container ?: return
        val next = NotifPrefs(
            enabled = prefs.calendar.mine || prefs.calendar.family || prefs.calendar.others,
            tasksEnabled = prefs.tasks,
        )
        if (next != container.settingsStore.currentNotifPrefs()) {
            container.settingsStore.setNotifPrefs(next)
        }
        NotificationWorker.enqueueOnce(context)
    }
}
