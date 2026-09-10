package com.kairos.app.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires when a reminder alarm goes off: posts the event's name as a notification. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val route = intent.getStringExtra(NotificationScheduler.EXTRA_ROUTE) ?: "calendar"
        val title = intent.getStringExtra(NotificationScheduler.EXTRA_TITLE)
            ?.ifBlank { null } ?: if (route == "tasks") "Task reminder" else "Event reminder"
        val notifId = intent.getIntExtra(NotificationScheduler.EXTRA_NOTIF_ID, title.hashCode())
        val location = intent.getStringExtra(NotificationScheduler.EXTRA_LOCATION)
        val taskId = intent.getStringExtra(NotificationScheduler.EXTRA_TASK_ID)
        Notifications.post(context, notifId, title, null, location, route, taskId)
    }
}
