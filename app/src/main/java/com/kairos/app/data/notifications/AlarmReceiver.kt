package com.kairos.app.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires when a reminder alarm goes off: posts the event's name as a notification. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(NotificationScheduler.EXTRA_TITLE)
            ?.ifBlank { null } ?: "Event reminder"
        val notifId = intent.getIntExtra(NotificationScheduler.EXTRA_NOTIF_ID, title.hashCode())
        val location = intent.getStringExtra(NotificationScheduler.EXTRA_LOCATION)
        Notifications.post(context, notifId, title, null, location)
    }
}
