package com.kairos.app.ui.workout

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kairos.app.MainActivity
import com.kairos.app.R
import com.kairos.app.data.notifications.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The phone's GPS while a walk, run, ruck or hike is tracked (v0.557), with
 * its notification: the time, distance and pace, and Finish. There's no Pause
 * on the notification: a lock-screen button can't ask first, and Pause asks.
 * Only runs while the app was opened to start it, so it needs no background
 * location permission.
 */
class TrackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var listening = false

    private val listener = LocationListener { loc: Location ->
        val kept = ActivityTrack.onLocation(
            context = this,
            lat = loc.latitude,
            lng = loc.longitude,
            accuracy = if (loc.hasAccuracy()) loc.accuracy else null,
            t = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
        if (kept) post()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val s = ActivityTrack.session.value
        if (s == null || s.endedAt != null || !hasLocation(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, build(this), type) }
            .onFailure { stopSelf(); return START_NOT_STICKY }
        listen()
        scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
        // The time ticks by itself (chronometer); this keeps distance and pace
        // fresh when no new point comes in, and stops the service at Finish.
        scope.launch {
            while (true) {
                val now = ActivityTrack.session.value
                if (now == null || now.endedAt != null) {
                    stopForegroundNow()
                    break
                }
                post()
                delay(15_000)
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun listen() {
        if (listening || !hasLocation(this)) return
        val lm = getSystemService(LocationManager::class.java) ?: return
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 0f, listener, Looper.getMainLooper())
            listening = true
        }
    }

    private fun post() {
        if (!Notifications.hasPermission(this)) return
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, build(this))
    }

    private fun stopForegroundNow() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (listening) runCatching { getSystemService(LocationManager::class.java)?.removeUpdates(listener) }
        listening = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "workout_track"
        private const val NOTIFICATION_ID = 4108

        fun hasLocation(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Redraw the notification now (paused, resumed). */
        fun refresh(context: Context) {
            if (!Notifications.hasPermission(context) || ActivityTrack.session.value == null) return
            ensureChannel(context)
            context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, build(context))
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Walk, run, ruck", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Time and distance while a walk, run, ruck or hike is tracked."
                        setShowBadge(false)
                    },
                )
            }
        }

        private fun open(context: Context, route: String, code: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                code,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(Notifications.EXTRA_OPEN, route),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun build(context: Context): Notification {
            val s = ActivityTrack.session.value
            val now = System.currentTimeMillis()
            val label = s?.let { trackLabel(it.activity) } ?: "Workout"
            val moving = s?.movingMs(now) ?: 0L
            val dist = s?.distanceM ?: 0.0
            val b = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL_ID) else Notification.Builder(context))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (s?.paused == true) "$label · Paused · ${trackClock(moving)}" else label)
                .setContentText("${trackMiles(dist)} · ${pacePerMile(moving, dist)} /mi")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open(context, "track", 41081))
                .addAction(
                    Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Finish", open(context, "track-finish", 41082)).build(),
                )
            if (s != null && !s.paused) {
                b.setUsesChronometer(true).setWhen(now - moving).setShowWhen(true)
            } else {
                b.setShowWhen(false)
            }
            if (Build.VERSION.SDK_INT >= 36) {
                runCatching {
                    b.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType).invoke(b, true)
                }
            }
            return b.build()
        }
    }
}
