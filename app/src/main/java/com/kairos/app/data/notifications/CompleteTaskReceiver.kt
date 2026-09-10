package com.kairos.app.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/**
 * The "Complete" action on a task alert. Dismisses the alert right away for
 * feedback, then hands the actual completion to [CompleteTaskWorker], which
 * completes it reliably in the background. Only that occurrence completes, so a
 * recurring task returns on its next date.
 */
class CompleteTaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        if (notifId != 0) {
            runCatching { NotificationManagerCompat.from(context).cancel(notifId) }
        }
        CompleteTaskWorker.enqueue(context, taskId)
    }

    companion object {
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_NOTIF_ID = "notifId"
    }
}
