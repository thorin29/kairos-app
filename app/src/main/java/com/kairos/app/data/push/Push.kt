package com.kairos.app.data.push

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kairos.app.R
import com.kairos.app.data.notifications.Notifications
import com.kairos.app.data.remote.dto.PushFirebaseDto
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Notifications from Kairos (v0.550; server 0.759+). The server keeps each
 * notification in this person's inbox and, when it has Firebase, sends a
 * content-free "check your inbox" ping; the app then collects the inbox and
 * shows what's there. Nothing about Firebase is built into the app: the
 * server hands over its Firebase values (GET /push/config) and the app starts
 * Firebase with them, remembering them so a ping can wake the app later.
 * Without Firebase (or without Google Play services) the app checks the inbox
 * every 15 minutes instead.
 */
object Push {
    private const val PREFS = "kairos_push"
    const val CHANNEL_ID = "places"
    private const val INBOX_WORK = "push-inbox"
    private const val INBOX_NOW = "push-inbox-now"
    private const val SYNC_WORK = "push-sync"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun optionsOf(c: PushFirebaseDto): FirebaseOptions =
        FirebaseOptions.Builder()
            .setProjectId(c.projectId)
            .setApplicationId(c.applicationId)
            .setApiKey(c.apiKey)
            .setGcmSenderId(c.senderId)
            .build()

    private fun cached(context: Context): PushFirebaseDto? {
        val p = prefs(context)
        val c = PushFirebaseDto(
            projectId = p.getString("projectId", "") ?: "",
            applicationId = p.getString("applicationId", "") ?: "",
            apiKey = p.getString("apiKey", "") ?: "",
            senderId = p.getString("senderId", "") ?: "",
        )
        return c.takeIf { it.projectId.isNotBlank() && it.applicationId.isNotBlank() && it.apiKey.isNotBlank() && it.senderId.isNotBlank() }
    }

    private fun remember(context: Context, c: PushFirebaseDto?) {
        prefs(context).edit().apply {
            if (c == null) {
                clear()
            } else {
                putString("projectId", c.projectId)
                putString("applicationId", c.applicationId)
                putString("apiKey", c.apiKey)
                putString("senderId", c.senderId)
            }
        }.apply()
    }

    /** Start Firebase from the remembered values. Called from Application.onCreate,
     *  so a ping that starts the app finds Firebase ready. Never throws. */
    fun startFromCache(context: Context): Boolean {
        val c = cached(context) ?: return false
        return runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context, optionsOf(c))
            true
        }.getOrDefault(false)
    }

    /** Ask the server how it pushes and register this phone (in the background). */
    fun sync(context: Context) {
        val req = OneTimeWorkRequestBuilder<PushSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SYNC_WORK, ExistingWorkPolicy.REPLACE, req)
    }

    /** Collect the inbox now (a ping arrived, or the app opened). */
    fun checkNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<InboxWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(INBOX_NOW, ExistingWorkPolicy.KEEP, req)
    }

    /** The regular check: every 15 minutes without pings, every 2 hours as a
     *  safety net with them. */
    internal fun schedule(context: Context, pings: Boolean) {
        val req = PeriodicWorkRequestBuilder<InboxWorker>(if (pings) 120L else 15L, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(INBOX_WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    internal suspend fun configure(context: Context): Boolean {
        val repo = (context.applicationContext as? com.kairos.app.KairosApp)?.container?.sessionRepository ?: return false
        val config = repo.pushConfigFromBackground() ?: return false
        val fb = config.firebase?.takeIf {
            it.projectId.isNotBlank() && it.applicationId.isNotBlank() && it.apiKey.isNotBlank() && it.senderId.isNotBlank()
        }
        if (fb == null) {
            val had = cached(context) != null
            remember(context, null)
            if (had) repo.registerPushTokenFromBackground(null)
            schedule(context, pings = false)
            return true
        }
        remember(context, fb)
        // A changed Firebase project takes effect the next time the app starts.
        val started = startFromCache(context)
        val token = if (started) firebaseToken() else null
        val registered = token != null && repo.registerPushTokenFromBackground(token)
        schedule(context, pings = registered)
        return true
    }

    private suspend fun firebaseToken(): String? = runCatching {
        suspendCancellableCoroutine<String?> { cont ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                cont.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }.getOrNull()

    private fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        // Created or renamed (the name was "Places" in 0.550–0.551); the
        // phone keeps the person's own settings for the channel.
        mgr.createNotificationChannel(
            android.app.NotificationChannel(CHANNEL_ID, "Family map", android.app.NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When someone arrives at or leaves a place."
            },
        )
    }

    internal fun show(context: Context, id: String, title: String, body: String, link: String?) {
        ensureChannel(context)
        if (!Notifications.hasPermission(context)) return
        val open = if (link != null && link.startsWith("/places")) "places" else "home"
        val tap = android.content.Intent(context, com.kairos.app.MainActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(Notifications.EXTRA_OPEN, open)
        }
        val notifId = ("push$id").hashCode()
        val pi = android.app.PendingIntent.getActivity(
            context,
            notifId,
            tap,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body.ifBlank { null })
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notifId, n) }
    }
}

/** Asks the server how it pushes, starts Firebase and registers this phone. */
class PushSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (Push.configure(applicationContext)) Result.success() else Result.retry()
}

/** Collects the inbox and shows each notification once. */
class InboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as? com.kairos.app.KairosApp)?.container?.sessionRepository ?: return Result.retry()
        val messages = repo.takePushInboxFromBackground() ?: return Result.retry()
        for (m in messages) Push.show(applicationContext, id = m.id, title = m.title, body = m.body, link = m.link)
        return Result.success()
    }

    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        // Expedited work on Android 11 and older runs as a short foreground job.
        val n = NotificationCompat.Builder(applicationContext, Push.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Checking for updates")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        return androidx.work.ForegroundInfo(990431, n)
    }
}

/** Firebase delivery: a ping means "check the inbox"; a new token is re-registered. */
class KairosMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["k"] == "inbox") Push.checkNow(applicationContext)
    }

    override fun onNewToken(token: String) {
        Push.sync(applicationContext)
    }
}
