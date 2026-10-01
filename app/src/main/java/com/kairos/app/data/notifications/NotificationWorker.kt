package com.kairos.app.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
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
        val container = (applicationContext as? com.kairos.app.KairosApp)?.container
        // Cold start: if the session is still initializing, don't run authenticated
        // work now (it would just fail to refresh and report success). Retry shortly,
        // by which point bootstrap has finished.
        if (container?.sessionRepository?.state?.value
            is com.kairos.app.data.session.SessionState.Loading
        ) {
            return Result.retry()
        }
        runCatching { NotificationScheduler.refresh(applicationContext) }
        // Drain anything queued while Kairos was unreachable. The in-process
        // triggers cover a running app; this covers the app being killed with
        // writes still waiting.
        runCatching { container?.syncManager?.replayAll() }
        // Keep TODAY's Home page on the phone while the server is reachable, so an
        // outage later in the day doesn't leave Home with nothing for today. The
        // day is the phone's own date and is both the request parameter and the
        // cache key, so this also handles the midnight rollover: the first run
        // after midnight fetches and stores the new day.
        runCatching {
            val session = container?.sessionRepository
            val cache = container?.payloadCache
            if (session != null && cache != null && session.isOnline()) {
                val day = java.time.LocalDate.now().toString()
                val person = session.currentPersonId()
                    ?: com.kairos.app.data.local.PayloadCacheStore.HOUSEHOLD
                val dash = session.loadDashboard(day)
                cache.writeAs(
                    "home",
                    day,
                    person,
                    com.kairos.app.data.remote.dto.DashboardDto.serializer(),
                    dash,
                )
            }
        }
        runCatching {
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
            val req = PeriodicWorkRequestBuilder<NotificationWorker>(2, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun enqueueOnce(context: Context) {
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<NotificationWorker>().build())
        }
    }
}
