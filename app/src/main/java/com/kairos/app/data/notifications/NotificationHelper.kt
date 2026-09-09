package com.kairos.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kairos.app.R

/**
 * One channel for calendar + birthday reminders, so the OS gives the user the
 * final say (Android's own notification settings). Everything here is safe to
 * call before permission is granted — posting is a no-op without it.
 */
object Notifications {
    const val CHANNEL_ID = "calendar_reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Calendar reminders",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Reminders for calendar events and birthdays."
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    /** Post a reminder. No-op if the OS permission hasn't been granted. */
    fun post(context: Context, id: Int, title: String, text: String? = null) {
        ensureChannel(context)
        if (!hasPermission(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (!text.isNullOrBlank()) {
            builder.setContentText(text)
        }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}
