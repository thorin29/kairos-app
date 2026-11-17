package com.budget.app.data.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.budget.app.BudgetApp
import com.budget.app.MainActivity
import com.budget.app.R
import com.budget.app.data.CardReminderLead
import com.budget.app.data.CardReminderLogic
import com.budget.app.data.ReminderMessage
import com.budget.app.data.SessionState
import com.budget.app.data.Settings
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Card payment reminders. Runs about hourly; the first run after 8 AM each day
 * asks the server for the cards and posts one notification for the payments
 * due on the chosen lead day or today. A card already paid for this cycle has
 * nothing left to pay, so it is skipped. Needs the server: offline, it tries
 * again next run.
 */
class CardReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as BudgetApp).container
        val lead = CardReminderLead.from(c.settings.get(Settings.CARD_REMINDER_LEAD))
        if (lead == CardReminderLead.OFF) return Result.success()

        val now = LocalDateTime.now()
        if (now.hour < MORNING_HOUR) return Result.success()
        val today = now.toLocalDate()
        if (c.settings.get(Settings.CARD_REMINDER_LAST_DAY) == today.toString()) return Result.success()

        if (c.session.state.value is SessionState.Loading) c.session.load()
        if (c.session.state.value !is SessionState.SignedIn) return Result.success()

        val cards = try {
            c.api.cards()
        } catch (_: Exception) {
            return Result.success()
        }
        c.settings.set(Settings.CARD_REMINDER_LAST_DAY, today.toString())
        CardReminderLogic.message(CardReminderLogic.dueOn(cards, today, lead), today)?.let { post(applicationContext, it) }
        return Result.success()
    }

    companion object {
        private const val NAME = "card-reminders"
        private const val CHANNEL = "card-reminders"
        private const val NOTIFICATION_ID = 2002
        private const val MORNING_HOUR = 8

        /** Starts or stops the hourly job to match the setting. */
        fun sync(context: Context, lead: CardReminderLead) {
            val wm = WorkManager.getInstance(context)
            if (lead == CardReminderLead.OFF) {
                wm.cancelUniqueWork(NAME)
            } else {
                val request = PeriodicWorkRequestBuilder<CardReminderWorker>(1, TimeUnit.HOURS).build()
                wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }

        private fun post(context: Context, message: ReminderMessage) {
            if (!BillReminderWorker.canPost(context)) return
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Card payment reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Card payments coming due, if you turn them on in the Cards tab"
                    },
                )
            }
            val open = PendingIntent.getActivity(
                context, 2,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_TAB, "cards")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val style = NotificationCompat.InboxStyle().also { s -> message.lines.forEach { s.addLine(it) } }
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_budget)
                .setContentTitle(message.title)
                .setContentText(message.body)
                .setStyle(if (message.lines.size > 1) style else NotificationCompat.BigTextStyle().bigText(message.body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                // Permission withdrawn; nothing to do.
            }
        }
    }
}
