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
import com.kairos.app.KairosApp
import com.kairos.app.MainActivity
import com.kairos.app.R
import com.kairos.app.data.notifications.Notifications
import com.kairos.app.data.remote.dto.PhonePointDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/*
 * A backpacking trip with the phone's GPS (v0.559). Runs for the whole trip,
 * easy on the battery: a fix every 30 seconds while hiking, every 5 minutes
 * at a stop, every 15 at camp for the night. The points go to Family map (the
 * phone's are used where Home Assistant's overlap), sent every few minutes
 * when there's signal and kept on the phone until then. Family map still works
 * without it; this makes the miles and the route exact.
 */

internal enum class TripMode { HIKING, STOPPED, CAMP }

internal data class TripState(val until: String, val mode: TripMode, val unsent: Int)

internal object TripTrack {
    private val _state = MutableStateFlow<TripState?>(null)
    val state: StateFlow<TripState?> = _state.asStateFlow()

    private const val PREFS = "trip"
    private val json = Json { ignoreUnknownKeys = true }
    private val fileLock = Mutex()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun buffer(context: Context) = File(context.filesDir, "trip-points.jsonl")

    fun until(context: Context): String? = prefs(context).getString("until", null)

    /** Start (or carry on) recording through `untilISO`. */
    fun start(context: Context, untilISO: String) {
        prefs(context).edit().putString("until", untilISO).apply()
        _state.value = TripState(until = untilISO, mode = _state.value?.mode ?: TripMode.HIKING, unsent = _state.value?.unsent ?: 0)
        runCatching { ContextCompat.startForegroundService(context, Intent(context, TripService::class.java)) }
    }

    /** Back after the app was closed: carry on if the trip isn't over. */
    fun restore(context: Context) {
        val until = until(context) ?: return
        if (LocalDate.now().toString() > until) {
            end(context)
            return
        }
        if (!TrackService.hasLocation(context)) return
        start(context, until)
    }

    /** Camp for the night / back to hiking (the trip tile, v0.563). */
    fun camp(context: Context) = action(context, TripService.ACTION_CAMP)
    fun hike(context: Context) = action(context, TripService.ACTION_HIKING)
    private fun action(context: Context, a: String) {
        if (_state.value == null) return
        runCatching { context.startService(Intent(context, TripService::class.java).setAction(a)) }
    }

    /** Finished on the trip's last day (v0.563): the tile stops offering buttons. */
    fun finishedToday(context: Context): Boolean = prefs(context).getString("finished", null) == LocalDate.now().toString()
    fun markFinished(context: Context) {
        prefs(context).edit().putString("finished", LocalDate.now().toString()).apply()
        end(context)
    }

    /** End trip: the points still on the phone are sent first. */
    fun end(context: Context) {
        prefs(context).edit().remove("until").apply()
        _state.value = null
        context.stopService(Intent(context, TripService::class.java))
    }

    internal fun setMode(mode: TripMode) {
        _state.value = _state.value?.copy(mode = mode)
    }

    /** One point onto the phone's unsent list (a line each, so adding is cheap). */
    internal suspend fun add(context: Context, p: PhonePointDto) = fileLock.withLock {
        runCatching { buffer(context).appendText(json.encodeToString(PhonePointDto.serializer(), p) + "\n") }
        _state.value = _state.value?.copy(unsent = (_state.value?.unsent ?: 0) + 1)
    }

    /** Send what's on the phone, 400 at a time; keeps what didn't go. */
    internal suspend fun flush(context: Context) {
        val repo = (context.applicationContext as KairosApp).container.sessionRepository
        while (true) {
            val batch = fileLock.withLock { read(context).take(400) }
            if (batch.isEmpty()) return
            val ok = runCatching { repo.sendPhonePoints(batch) }.isSuccess
            if (!ok) return
            fileLock.withLock {
                val sent = batch.map { it.t }.toSet()
                val left = read(context).filterNot { it.t in sent }
                write(context, left)
                _state.value = _state.value?.copy(unsent = left.size)
            }
        }
    }

    private fun read(context: Context): List<PhonePointDto> {
        val f = buffer(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            f.readLines().mapNotNull { line ->
                if (line.isBlank()) null else runCatching { json.decodeFromString(PhonePointDto.serializer(), line) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun write(context: Context, list: List<PhonePointDto>) {
        runCatching {
            buffer(context).writeText(list.joinToString("") { json.encodeToString(PhonePointDto.serializer(), it) + "\n" })
        }
    }
}

/** The trip's GPS and its notification ("At camp for the night?" too). */
class TripService : Service() {
    // File and network work off the main thread; location updates still
    // arrive on the main looper.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var interval = 0L
    private var anchor: Location? = null
    private var anchorAt = 0L
    private var hikingUntil = 0L
    private var askedCampOn: String? = null

    private val listener = LocationListener { loc: Location -> onFix(loc) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CAMP -> {
                cancelAsk()
                setMode(TripMode.CAMP)
                return START_STICKY
            }
            ACTION_HIKING -> {
                cancelAsk()
                hikingUntil = System.currentTimeMillis() + 30 * 60_000
                setMode(TripMode.HIKING)
                return START_STICKY
            }
        }
        val until = TripTrack.until(this)
        if (until == null || LocalDate.now().toString() > until || !TrackService.hasLocation(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannels(this)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, build(TripTrack.state.value?.mode ?: TripMode.HIKING), type) }
            .onFailure { stopSelf(); return START_NOT_STICKY }
        if (interval == 0L) setMode(TripTrack.state.value?.mode ?: TripMode.HIKING)
        scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
        scope.launch {
            while (true) {
                TripTrack.flush(this@TripService)
                if (LocalDate.now().toString() > (TripTrack.until(this@TripService) ?: "")) {
                    TripTrack.end(this@TripService)
                    break
                }
                delay(5 * 60_000)
            }
        }
        return START_STICKY
    }

    private fun onFix(loc: Location) {
        if (loc.hasAccuracy() && loc.accuracy > 50f) return
        val t = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis()
        scope.launch {
            TripTrack.add(
                this@TripService,
                PhonePointDto(
                    t = t,
                    lat = loc.latitude,
                    lng = loc.longitude,
                    acc = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null,
                    speed = if (loc.hasSpeed()) loc.speed.toDouble() else null,
                    alt = if (loc.hasAltitude()) loc.altitude else null,
                ),
            )
        }
        val a = anchor
        val moved = a != null && loc.distanceTo(a) > (if (TripTrack.state.value?.mode == TripMode.HIKING) 60f else 150f)
        if (a == null || moved) {
            anchor = loc
            anchorAt = t
            if (moved && TripTrack.state.value?.mode != TripMode.HIKING) setMode(TripMode.HIKING)
            return
        }
        // Still for 20 minutes while hiking: a stop. In the afternoon, ask
        // once a day whether it's camp for the night.
        if (TripTrack.state.value?.mode == TripMode.HIKING && t - anchorAt >= 20 * 60_000 && System.currentTimeMillis() > hikingUntil) {
            setMode(TripMode.STOPPED)
            val today = LocalDate.now().toString()
            if (LocalTime.now().hour >= 15 && askedCampOn != today) {
                askedCampOn = today
                askCamp()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setMode(mode: TripMode) {
        TripTrack.setMode(mode)
        val every = when (mode) {
            TripMode.HIKING -> 30_000L
            TripMode.STOPPED -> 5 * 60_000L
            TripMode.CAMP -> 15 * 60_000L
        }
        if (every != interval && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val lm = getSystemService(LocationManager::class.java)
            runCatching {
                lm?.removeUpdates(listener)
                lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, every, 0f, listener, Looper.getMainLooper())
                interval = every
            }
        }
        if (Notifications.hasPermission(this)) {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, build(mode))
        }
    }

    private fun askCamp() {
        if (!Notifications.hasPermission(this)) return
        val b = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, ASK_CHANNEL) else Notification.Builder(this))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("At camp for the night?")
            .setAutoCancel(true)
            .setContentIntent(open())
            .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "At camp", self(ACTION_CAMP, 1)).build())
            .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Still hiking", self(ACTION_HIKING, 2)).build())
        getSystemService(NotificationManager::class.java)?.notify(ASK_ID, b.build())
    }

    private fun cancelAsk() {
        getSystemService(NotificationManager::class.java)?.cancel(ASK_ID)
    }

    private fun self(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this,
            code,
            Intent(this, TripService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun open(): PendingIntent =
        PendingIntent.getActivity(
            this,
            41090,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(Notifications.EXTRA_OPEN, "workouts"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun build(mode: TripMode): Notification {
        val text = when (mode) {
            TripMode.HIKING -> "Recording your hike"
            TripMode.STOPPED -> "Stopped"
            TripMode.CAMP -> "At camp"
        }
        return (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Backpacking trip")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open())
            .build()
    }

    override fun onDestroy() {
        runCatching { getSystemService(LocationManager::class.java)?.removeUpdates(listener) }
        val ctx = applicationContext
        // Whatever's left goes now (if there's signal); the rest waits for the
        // next trip or app start.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { TripTrack.flush(ctx) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "backpacking_trip"
        private const val ASK_CHANNEL = "backpacking_ask"
        private const val NOTIFICATION_ID = 4109
        private const val ASK_ID = 4110
        internal const val ACTION_CAMP = "com.kairos.app.trip.CAMP"
        internal const val ACTION_HIKING = "com.kairos.app.trip.HIKING"

        private fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Backpacking trip", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Shown while a backpacking trip is being recorded."
                        setShowBadge(false)
                    },
                )
            }
            if (mgr.getNotificationChannel(ASK_CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(ASK_CHANNEL, "Backpacking questions", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "\"At camp for the night?\""
                    },
                )
            }
        }
    }
}
