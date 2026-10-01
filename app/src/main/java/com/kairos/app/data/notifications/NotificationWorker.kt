package com.kairos.app.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
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
        // Keep Home on the phone while the server is reachable, so an outage later
        // doesn't leave Home with nothing to show.
        //
        // TODAY **and TOMORROW**: this job runs about every two hours, so "the next
        // run picks up the new day" leaves a gap of up to two hours after midnight
        // in which the server could go down and the phone would hold no page for
        // the new day at all. Caching tomorrow alongside today closes that gap —
        // the 11pm run already has the new day stored before it begins.
        //
        // Tomorrow's page is a real server-built day for that date: from web 0.531
        // the schedule, chore badges, reading progress and the admin reward banner
        // are all computed for the day that was ASKED for. What a future day still
        // omits is the handful of sections that only mean something "now" —
        // get-ahead, school progress/get-ahead, up-for-grabs, always-open chores
        // and the overdue-workout count — which fill in on the first live load of
        // the day. Nothing on the page is another day's data wearing this date.
        runCatching {
            val session = container?.sessionRepository
            val cache = container?.payloadCache
            if (session != null && cache != null && session.isOnline()) {
                val person = session.currentPersonId()
                    ?: com.kairos.app.data.local.PayloadCacheStore.HOUSEHOLD
                val today = java.time.LocalDate.now()
                for (day in listOf(today, today.plusDays(1))) {
                    val iso = day.toString()
                    // Per-day so a failure on tomorrow can't discard today.
                    runCatching {
                        val dash = session.loadDashboard(iso)
                        cache.writeAs(
                            "home",
                            iso,
                            person,
                            com.kairos.app.data.remote.dto.DashboardDto.serializer(),
                            dash,
                        )
                    }
                }
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
        // Re-arm the just-after-midnight run — but never from the midnight run
        // itself. enqueueUniqueWork(REPLACE) cancels whatever currently holds that
        // unique name, and when this IS that work, it would be cancelling itself
        // mid-execution. The periodic job (every ~2h) and app launch re-arm it, so
        // it is always re-armed within a couple of hours of firing.
        if (!tags.contains(MIDNIGHT_TAG)) {
            runCatching { enqueueAfterMidnight(applicationContext) }
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "notif-refresh-periodic"
        private const val MIDNIGHT = "notif-refresh-midnight"
        /** Tag on the midnight request so a run can tell it IS the midnight work
         *  and not re-arm (and so cancel) itself. */
        internal const val MIDNIGHT_TAG = "notif-midnight-run"

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

        /**
         * A run shortly after the next local midnight, so the new day is fetched
         * and cached as the day turns rather than up to two hours later. Unique
         * and REPLACEd, so re-arming it (every run, every launch) keeps exactly
         * one pending. WorkManager may run it late if the phone is dozing; the
         * today+tomorrow prefetch above is what makes a late run harmless.
         */
        fun enqueueAfterMidnight(context: Context) {
            val now = java.time.LocalDateTime.now()
            val fireAt = now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(2)
            val delayMs = java.time.Duration.between(now, fireAt).toMillis().coerceAtLeast(60_000L)
            val req = OneTimeWorkRequestBuilder<NotificationWorker>()
                .addTag(MIDNIGHT_TAG)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(MIDNIGHT, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
