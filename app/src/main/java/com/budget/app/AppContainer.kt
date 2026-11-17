package com.budget.app

import android.content.Context
import com.budget.app.data.Accent
import com.budget.app.data.Appearance
import com.budget.app.data.EnrollLink
import com.budget.app.data.CardReminderLead
import com.budget.app.data.ReminderLead
import com.budget.app.data.reminders.CardReminderWorker
import com.budget.app.data.reminders.BillReminderWorker
import com.budget.app.data.ThemeMode
import com.budget.app.data.Session
import com.budget.app.data.Settings
import com.budget.app.data.budgetStore
import com.budget.app.data.local.BudgetDatabase
import com.budget.app.data.remote.ApiClient
import com.budget.app.data.remote.NetworkMonitor
import com.budget.app.data.secure.TokenStore
import com.budget.app.data.sync.LocalStore
import com.budget.app.data.sync.SyncManager
import com.budget.app.data.update.UpdateChecker
import com.budget.app.data.update.UpdateInstaller
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Everything the screens share, built once per process. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val settings = Settings(app.budgetStore)
    val tokens = TokenStore(settings)

    // The session supplies the server; the token store the token.
    private var sessionRef: Session? = null
    val api = ApiClient(serverProvider = { sessionRef?.server }, tokenProvider = { tokens.current() })

    val database = BudgetDatabase.build(app)
    val local = LocalStore(database.localDao(), app.budgetStore, api.json)
    val session = Session(settings, tokens, local, api).also { sessionRef = it }
    val sync = SyncManager(api, local, session)
    val network = NetworkMonitor(app)
    val updates = UpdateChecker(settings)
    val installer = UpdateInstaller(app)

    /** A budget://enroll link the phone was opened with, waiting for the setup screen. */
    val pendingEnrollLink = MutableStateFlow<EnrollLink?>(null)

    /** Set when the app was opened from the update notification: show the Update screen. */
    val openUpdate = MutableStateFlow(false)

    /** Set when the app was opened from a card reminder: show the Cards tab. */
    val openCards = MutableStateFlow(false)

    /** Light/dark and accent, chosen on this phone. Kept through sign-out. */
    val appearance: Flow<Appearance> =
        combine(settings.observe(Settings.THEME_MODE), settings.observe(Settings.ACCENT)) { mode, accent ->
            Appearance(ThemeMode.from(mode), Accent.from(accent))
        }

    /** Bill reminders on this phone. Off unless turned on under More. */
    val reminderLead: Flow<ReminderLead> = settings.observe(Settings.REMINDER_LEAD).map { ReminderLead.from(it) }

    suspend fun setReminderLead(lead: ReminderLead) {
        settings.set(Settings.REMINDER_LEAD, lead.key)
        // A new choice reminds afresh today if a bill qualifies.
        settings.set(Settings.REMINDER_LAST_DAY, null)
        BillReminderWorker.sync(app, lead)
    }

    /** Card payment reminders on this phone. Off unless turned on in the Cards tab or under More. */
    val cardReminderLead: Flow<CardReminderLead> = settings.observe(Settings.CARD_REMINDER_LEAD).map { CardReminderLead.from(it) }

    suspend fun setCardReminderLead(lead: CardReminderLead) {
        settings.set(Settings.CARD_REMINDER_LEAD, lead.key)
        settings.set(Settings.CARD_REMINDER_LAST_DAY, null)
        CardReminderWorker.sync(app, lead)
    }

    suspend fun setAppearance(value: Appearance) {
        settings.set(Settings.THEME_MODE, value.mode.key)
        settings.set(Settings.ACCENT, value.accent.key)
    }
}
