package com.kairos.app.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.kairos.app.KairosApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The "Complete" action on a task alert: checks the task off in the background
 * and dismisses the notification. Only that occurrence is completed, so a
 * recurring task returns on its next date. May run in a freshly woken process,
 * so completion goes through the repository's self-priming path.
 */
class CompleteTaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = (context.applicationContext as? KairosApp)?.container
                container?.sessionRepository?.completeTaskFromNotification(taskId)
                NotificationManagerCompat.from(context).cancel(notifId)
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_NOTIF_ID = "notifId"
    }
}
