package com.kairos.app.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Re-runs the scheduler in the background so newly added / changed events get
 *  their alarms, and to extend the window as time passes. */
class NotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { NotificationScheduler.refresh(applicationContext) }
        runCatching {
            val container = (applicationContext as? com.kairos.app.KairosApp)?.container
            val checker = container?.updateChecker
            checker?.check()
            val avail = checker?.available?.value
            val settings = container?.settingsStore
            // Notify once per new version (icon dot + tray), never re-nagging the
            // same one every couple of hours.
            if (avail != null && settings != null && avail.versionCode > settings.lastUpdateNotified()) {
                Notifications.postUpdate(applicationContext, avail.versionName)
                settings.setLastUpdateNotified(avail.versionCode)
            }
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "notif-refresh-periodic"

        /** A safety-net periodic refresh (WorkManager's minimum granularity is
         *  coarse; exact alarms do the actual firing). */
        fun enqueuePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<NotificationWorker>(2, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun enqueueOnce(context: Context) {
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<NotificationWorker>().build())
        }
    }
}
