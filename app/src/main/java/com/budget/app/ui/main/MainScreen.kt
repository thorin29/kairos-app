package com.budget.app.ui.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import com.budget.app.data.Settings
import com.budget.app.ui.settings.UpdateScreen
import com.budget.app.ui.theme.UpdateDot
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budget.app.AppContainer
import com.budget.app.data.Money
import com.budget.app.data.MonthLogic
import com.budget.app.data.SessionState
import com.budget.app.data.remote.MonthEntry
import com.budget.app.data.sync.Overlay
import com.budget.app.ui.month.MonthScreen
import com.budget.app.ui.month.MonthViewModel
import com.budget.app.ui.plan.PlanScreen
import com.budget.app.ui.plan.PlanViewModel
import com.budget.app.ui.record.RecordSheet
import com.budget.app.ui.settings.MoreScreen
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon

enum class Tab(val label: String, val icon: Ph, val iconOn: Ph) {
    TODAY("Today", Ph.HOUSE, Ph.HOUSE_FILL),
    MONTH("Month", Ph.CALENDAR, Ph.CALENDAR_FILL),
    CARDS("Cards", Ph.CARD, Ph.CARD_FILL),
    INSIGHTS("Insights", Ph.PIE, Ph.PIE_FILL),
    MORE("More", Ph.LIST, Ph.LIST_FILL),
}

/** Which month a record sheet works on, and what it opened on. */
private data class RecordRequest(val vm: MonthViewModel, val entry: MonthEntry?)

/** Signed in: five tabs along the bottom (Plan lives under More), and the record sheet over any of them. */
@Composable
fun MainScreen(container: AppContainer, session: SessionState.SignedIn) {
    val c = Budget.colors
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    val todayVm: MonthViewModel = viewModel(key = "today") { MonthViewModel(container) }
    val monthVm: MonthViewModel = viewModel(key = "month") { MonthViewModel(container) }
    val planVm: PlanViewModel = viewModel(key = "plan") { PlanViewModel(container) }
    val extrasVm: com.budget.app.ui.today.ExtrasViewModel = viewModel(key = "extras") { com.budget.app.ui.today.ExtrasViewModel(container) }
    val extras by extrasVm.ui.collectAsState()
    var showReview by rememberSaveable { mutableStateOf(false) }
    val update by container.updates.available.collectAsState()

    var record by remember { mutableStateOf<RecordRequest?>(null) }
    var editingBalanceOf by remember { mutableStateOf<MonthViewModel?>(null) }
    var showUpdate by rememberSaveable { mutableStateOf(false) }
    // "What can I pay?" opens over More, from More or from Today's balance card.
    var showPlan by rememberSaveable { mutableStateOf(false) }
    var planFromToday by rememberSaveable { mutableStateOf(false) }

    // Opened from a card reminder: straight to the Cards tab.
    val openCards by container.openCards.collectAsState()
    LaunchedEffect(openCards) {
        if (openCards) {
            tab = Tab.CARDS
            container.openCards.value = false
        }
    }

    // Opened from the update notification: straight to the Update screen.
    val openUpdate by container.openUpdate.collectAsState()
    LaunchedEffect(openUpdate) {
        if (openUpdate) {
            tab = Tab.MORE
            showUpdate = true
            container.openUpdate.value = false
        }
    }

    // Android 13+: ask once for notifications, for "Update available".
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && container.settings.get(Settings.ASKED_NOTIFICATIONS) == null) {
            container.settings.set(Settings.ASKED_NOTIFICATIONS, "1")
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Showing a tab refreshes it, so a change made on one shows on the others.
    LaunchedEffect(tab) {
        when (tab) {
            Tab.TODAY -> {
                todayVm.load()
                extrasVm.load()
            }
            Tab.MONTH -> monthVm.load()
            Tab.CARDS, Tab.INSIGHTS -> extrasVm.load()
            Tab.MORE -> if (showPlan) planVm.load() else Unit
        }
    }

    BackHandler(enabled = tab != Tab.TODAY && !showUpdate) { tab = Tab.TODAY }
    BackHandler(enabled = tab == Tab.MORE && showPlan) {
        showPlan = false
        if (planFromToday) tab = Tab.TODAY
    }
    BackHandler(enabled = tab == Tab.TODAY && showReview) {
        showReview = false
        todayVm.load()
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.TODAY -> if (showReview) {
                    com.budget.app.ui.review.ReviewScreen(extrasVm, onBack = {
                        showReview = false
                        todayVm.load()
                    })
                } else {
                    com.budget.app.ui.today.TodayScreen(
                        vm = todayVm,
                        onRecord = { record = RecordRequest(todayVm, it) },
                        onEditBalance = { editingBalanceOf = todayVm },
                        onOpenMonth = { tab = Tab.MONTH },
                        onOpenPlan = {
                            showPlan = true
                            planFromToday = true
                            showUpdate = false
                            tab = Tab.MORE
                            planVm.load()
                        },
                        onOpenMore = { tab = Tab.MORE },
                        extras = extras,
                        onOpenReview = { showReview = true },
                        onOpenCards = { tab = Tab.CARDS },
                        onOpenInsights = { tab = Tab.INSIGHTS },
                    )
                }
                Tab.MONTH -> MonthScreen(monthVm, onRecord = { record = RecordRequest(monthVm, it) })
                Tab.CARDS -> com.budget.app.ui.cards.CardsScreen(container, extras)
                Tab.INSIGHTS -> com.budget.app.ui.insights.InsightsScreen(extras)
                Tab.MORE -> when {
                    showUpdate -> UpdateScreen(container, onBack = { showUpdate = false })
                    showPlan -> PlanScreen(planVm, onEditBalance = { editingBalanceOf = todayVm }, onBack = {
                        showPlan = false
                        if (planFromToday) tab = Tab.TODAY
                    })
                    else -> MoreScreen(container, session, onOpenUpdate = { showUpdate = true }, onOpenPlan = {
                        showPlan = true
                        planFromToday = false
                        planVm.load()
                    })
                }
            }
        }
        BottomBar(tab, updateAvailable = update != null, onSelect = {
            if (it == Tab.MORE && (it == tab || planFromToday)) {
                showUpdate = false
                showPlan = false
            }
            tab = it
        })
    }

    record?.let { r ->
        val ui by r.vm.ui.collectAsState()
        val pending by r.vm.pending.collectAsState()
        val view = remember(ui.view, pending) { ui.view?.let { Overlay.apply(it, pending) } }
        RecordSheet(
            candidates = view?.let { MonthLogic.dueSoon(it) } ?: emptyList(),
            everything = view?.let { MonthLogic.everything(it) } ?: emptyList(),
            initial = r.entry,
            defaultPaidOn = r.vm::defaultPaidOn,
            onDismiss = { record = null },
            onConfirm = { entry, cents, day ->
                r.vm.record(entry, cents, day)
                record = null
            },
            onClear = { entry ->
                r.vm.clear(entry)
                record = null
            },
        )
    }

    editingBalanceOf?.let { vm ->
        val ui by vm.ui.collectAsState()
        BalanceDialog(
            current = ui.view?.billsBalanceCents,
            accountName = ui.view?.billsAccountName,
            onDismiss = { editingBalanceOf = null },
            onSave = {
                vm.setBalance(it)
                editingBalanceOf = null
                if (showPlan) planVm.load()
            },
        )
    }
}

@Composable
private fun BottomBar(current: Tab, updateAvailable: Boolean, onSelect: (Tab) -> Unit) {
    val c = Budget.colors
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp)) {
            Tab.entries.forEach { t ->
                val on = t == current
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(role = Role.Tab) { onSelect(t) }
                        .semantics { selected = on },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .width(56.dp)
                            .height(32.dp)
                            .background(if (on) c.accentSoft else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PhIcon(if (on) t.iconOn else t.icon, null, if (on) c.accent else c.muted)
                        // The update dot, on every screen: light blue with a ring in the bar color.
                        if (t == Tab.MORE && updateAvailable) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 2.dp, end = 12.dp)
                                    .size(14.dp)
                                    .background(c.surface, CircleShape)
                                    .padding(2.dp)
                                    .background(UpdateDot, CircleShape)
                                    .semantics { contentDescription = "Update available" },
                            )
                        }
                    }
                    Text(
                        t.label,
                        color = if (on) c.ink else c.muted,
                        fontSize = 12.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceDialog(current: Long?, accountName: String?, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val c = Budget.colors
    var text by remember { mutableStateOf(current?.let { Money.toDecimal(maxOf(it, 0L)) } ?: "") }
    val cents = Money.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("In ${accountName ?: "the bills account"} now", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                prefix = { Text("$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = text.isNotEmpty() && cents == null,
            )
        },
        confirmButton = {
            Button(
                onClick = { cents?.let(onSave) },
                enabled = cents != null,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.muted) } },
    )
}
