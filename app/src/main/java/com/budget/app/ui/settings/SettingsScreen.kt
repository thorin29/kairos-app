package com.budget.app.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.app.AppContainer
import com.budget.app.BuildConfig
import com.budget.app.data.Accent
import com.budget.app.data.Appearance
import com.budget.app.data.ReminderLead
import com.budget.app.data.CardReminderLead
import com.budget.app.ui.cards.CardReminderSheet
import com.budget.app.ui.cards.rememberCardReminderSetter
import com.budget.app.data.reminders.BillReminderWorker
import com.budget.app.data.SessionState
import com.budget.app.data.ThemeMode
import com.budget.app.ui.common.Banner
import com.budget.app.ui.common.SectionLabel
import com.budget.app.ui.theme.Budget
import com.budget.app.ui.theme.Ph
import com.budget.app.ui.theme.PhIcon
import com.budget.app.ui.theme.swatchOf
import com.budget.app.ui.theme.UpdateDot
import com.budget.app.ui.theme.UpdatePill
import kotlinx.coroutines.launch

/** The More tab: account, what's waiting, appearance, updates, sign out. */
@Composable
fun MoreScreen(container: AppContainer, session: SessionState.SignedIn, onOpenUpdate: () -> Unit, onOpenPlan: () -> Unit = {}) {
    val c = Budget.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val appearance by container.appearance.collectAsState(initial = Appearance())
    val update by container.updates.available.collectAsState()
    val pending by container.local.queue.collectAsState(initial = emptyList())
    val problems by container.local.problems.collectAsState(initial = emptyList())
    var confirmSignOut by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showReminders by remember { mutableStateOf(false) }
    val reminderLead by container.reminderLead.collectAsState(initial = ReminderLead.OFF)
    var pendingLead by remember { mutableStateOf<ReminderLead?>(null) }
    // Reminders need notifications; ask when they're turned on, if not allowed yet.
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingLead?.let { lead -> scope.launch { container.setReminderLead(lead) } }
        pendingLead = null
    }
    var showWaiting by remember { mutableStateOf(false) }
    var showCardReminders by remember { mutableStateOf(false) }
    val cardLead by container.cardReminderLead.collectAsState(initial = CardReminderLead.OFF)
    val setCardLead = rememberCardReminderSetter(container)

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("More", color = c.ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))

        Row(
            Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(18.dp)).border(1.dp, c.line, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                Text(session.username.take(1).uppercase().ifEmpty { "?" }, color = c.accent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(session.username, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${Uri.parse(session.server).host ?: session.server} · ${session.deviceName}",
                    color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            PhIcon(Ph.SHIELD, null, c.accent)
        }

        if (problems.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier.fillMaxWidth().background(c.dangerSoft, RoundedCornerShape(18.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Not saved", color = c.danger, fontWeight = FontWeight.Bold)
                problems.forEach { p -> Text("${p.label}: ${p.message}", color = c.ink, fontSize = 14.sp) }
                TextButton(onClick = { scope.launch { container.local.clearProblems() } }) { Text("Dismiss", color = c.danger) }
            }
        }

        SectionLabel("Budget", c.muted)
        Group {
            Item(Ph.CHART, "What can I pay?", value = "Plan", onClick = onOpenPlan)
            Divider()
            Item(Ph.CHART_BAR, "Year & reports", value = "Web", trailing = Ph.OPEN_OUT, onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(session.server))) }
            })
            Item(Ph.SEND, "Waiting to send", value = pending.size.toString(), onClick = { showWaiting = !showWaiting })
            if (showWaiting) {
                Column(Modifier.padding(start = 54.dp, end = 16.dp, bottom = 12.dp)) {
                    if (pending.isEmpty()) Text("Everything has been sent.", color = c.muted, fontSize = 14.sp)
                    pending.forEach { Text(it.label, color = c.ink, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }
        }

        SectionLabel("This phone", c.muted)
        Group {
            Item(Ph.CONTRAST, "Appearance", value = "${appearance.mode.label} · ${appearance.accent.label}", onClick = { showAppearance = true })
            Divider()
            Item(Ph.BELL, "Bill reminders", value = reminderLead.label, onClick = { showReminders = true })
            Divider()
            Item(Ph.CARD, "Card reminders", value = cardLead.short, onClick = { showCardReminders = true })
        }

        SectionLabel("App", c.muted)
        Group {
            if (update != null) {
                Item(Ph.DOWNLOAD, "Update available", iconTint = UpdateDot, onClick = onOpenUpdate)
                Divider()
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable(role = Role.Button, onClick = onOpenUpdate)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhIcon(Ph.DOWNLOAD, null, c.muted)
                Spacer(Modifier.width(14.dp))
                Text("App version", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (update != null) {
                    Text(
                        "New",
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(UpdatePill, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                } else {
                    Text(BuildConfig.VERSION_NAME, color = c.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(6.dp))
                PhIcon(Ph.CARET_RIGHT, null, c.muted, size = 20.dp)
            }
            Divider()
            Item(Ph.SIGN_OUT, if (confirmSignOut) "Tap again to sign out" else "Sign out this phone", tint = c.danger, trailing = null, onClick = {
                if (confirmSignOut) scope.launch { container.session.signOut() }
                confirmSignOut = true
            })
            if (confirmSignOut && pending.isNotEmpty()) {
                Text(
                    "${pending.size} change${if (pending.size == 1) " hasn't" else "s haven't"} been sent and will be lost.",
                    color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(start = 54.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }

        Text(
            "To sign this phone out from elsewhere, use Settings → Sign-in & people on the web. Changing your password there signs out every phone.",
            color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 10.dp).navigationBarsPadding(),
        )
    }

    if (showReminders) {
        ReminderSheet(
            current = reminderLead,
            notificationsBlocked = reminderLead != ReminderLead.OFF && !BillReminderWorker.canPost(context),
            onPick = { lead ->
                showReminders = false
                if (lead != ReminderLead.OFF && Build.VERSION.SDK_INT >= 33 && !BillReminderWorker.canPost(context)) {
                    pendingLead = lead
                    notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scope.launch { container.setReminderLead(lead) }
                }
            },
            onDismiss = { showReminders = false },
        )
    }

    if (showCardReminders) {
        CardReminderSheet(
            current = cardLead,
            onPick = {
                showCardReminders = false
                setCardLead(it)
            },
            onDismiss = { showCardReminders = false },
        )
    }

    if (showAppearance) {
        AppearanceSheet(
            current = appearance,
            onPick = { scope.launch { container.setAppearance(it) } },
            onDismiss = { showAppearance = false },
        )
    }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    val c = Budget.colors
    Column(
        Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(18.dp)).border(1.dp, c.line, RoundedCornerShape(18.dp)),
        content = content,
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Budget.colors.line))
}

@Composable
private fun Item(
    icon: Ph,
    label: String,
    value: String = "",
    tint: Color = Budget.colors.ink,
    trailing: Ph? = Ph.CARET_RIGHT,
    iconTint: Color? = null,
    onClick: (() -> Unit)?,
) {
    val c = Budget.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhIcon(icon, null, iconTint ?: if (tint == c.ink) c.muted else tint)
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, color = c.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            PhIcon(trailing, null, c.muted, size = 20.dp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSheet(current: Appearance, onPick: (Appearance) -> Unit, onDismiss: () -> Unit) {
    val c = Budget.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Appearance", color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)

            Text("Theme", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().background(c.raised, RoundedCornerShape(14.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    val on = mode == current.mode
                    Box(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(if (on) c.surface else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable(role = Role.RadioButton) { onPick(current.copy(mode = mode)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(mode.label, color = if (on) c.ink else c.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("Accent", color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Accent.entries.forEach { accent ->
                    val on = accent == current.accent
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .border(if (on) 3.dp else 0.dp, if (on) c.ink else Color.Transparent, CircleShape)
                                .padding(5.dp)
                                .background(swatchOf(accent), CircleShape)
                                .clickable(role = Role.RadioButton) { onPick(current.copy(accent = accent)) }
                                .semantics { contentDescription = accent.label },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) PhIcon(Ph.CHECK, null, Color.White, size = 20.dp)
                        }
                        Text(accent.label, color = if (on) c.ink else c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSheet(current: ReminderLead, notificationsBlocked: Boolean, onPick: (ReminderLead) -> Unit, onDismiss: () -> Unit) {
    val c = Budget.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Bill reminders", color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "A notification around 8 AM for unpaid bills. Bills you've already recorded are skipped.",
                color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp),
            )
            ReminderLead.entries.forEach { lead ->
                val on = lead == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(if (on) c.accentSoft else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable(role = Role.RadioButton) { onPick(lead) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PhIcon(if (on) Ph.CHECK_CIRCLE_FILL else Ph.CIRCLE, null, if (on) c.accent else c.muted)
                    Spacer(Modifier.width(12.dp))
                    Text(lead.label, color = c.ink, fontSize = 16.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold)
                }
            }
            if (notificationsBlocked) {
                Text(
                    "Notifications are off for Budget, so reminders can't show. Turn them on in the phone's settings.",
                    color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
