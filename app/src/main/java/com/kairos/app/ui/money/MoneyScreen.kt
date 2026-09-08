package com.kairos.app.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.kairos.app.ui.theme.KairosThemeState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.AddMoneyRequest
import com.kairos.app.data.remote.dto.MoneyDto
import com.kairos.app.data.remote.dto.MoneyParticipantDto
import com.kairos.app.data.remote.dto.MoneyPendingDto
import com.kairos.app.data.remote.dto.MoneyRewardCompleterDto
import com.kairos.app.data.remote.dto.MoneyRewardMonthDto
import com.kairos.app.data.remote.dto.MoneyRowDto
import com.kairos.app.data.remote.dto.PersonDto
import com.kairos.app.data.remote.dto.StartingFundsRequest
import com.kairos.app.data.remote.dto.UpdateMoneyRequest
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val MONEY = Color(0xFF15803D)    // money green (deposits, approved)
private val NEG = Color(0xFFDC2626)      // red-600 (payments, negative balances)
private val PENDING_DOT = Color(0xFFFBBF24) // amber-400 (awaiting approval marker)
private val GREEN600 = Color(0xFF16A34A) // approve button
private val AMBER = Color(0xFFD97706)    // amber-600 (reward buttons)
private val AMBER_DK = Color(0xFFB45309) // amber-700 (reward icon/accent)
private val AMBER_BG = Color(0xFFFFFBEB) // amber-50 (reward/queue banner bg)
private val AMBER_BORDER = Color(0xFFFCD34D) // amber-300 border
private val DANGER = Color(0xFFDC2626)

private data class EditTarget(
    val id: String,
    val date: String,
    val direction: String,
    val category: String?,
    val detail: String?,
    val amountCents: Long,
    val who: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyScreen(onOpenDrawer: () -> Unit, refreshKey: Int = 0) {
    val container = rememberContainer()
    val vm: MoneyViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MoneyViewModel(container.sessionRepository) }
        },
    )
    val ui by vm.ui.collectAsState()
    androidx.compose.runtime.LaunchedEffect(refreshKey) { if (refreshKey > 0) vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Money") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            val data = ui.data
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ui.loadError ?: "Couldn't load money.")
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text("Retry") }
                    }
                }
                else -> MoneyContent(vm, ui, data)
            }
        }
    }
}

@Composable
private fun MoneyContent(vm: MoneyViewModel, ui: MoneyUiState, data: MoneyDto) {
    var showAdd by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var confirmMonth by remember { mutableStateOf<MoneyRewardMonthDto?>(null) }
    var confirmBase by remember { mutableStateOf<Pair<MoneyRewardCompleterDto, String>?>(null) }
    var rowAction by remember { mutableStateOf<MoneyRowDto?>(null) }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var showStarting by remember { mutableStateOf(false) }

    val participants = data.participants.filter { it.person != null }
    val selected = participants.firstOrNull { it.person?.id == data.selectedId }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Admin: household transactions awaiting a verification mark.
        if (data.isAdmin && data.pendingApprovals.isNotEmpty()) {
            ApprovalQueue(
                pending = data.pendingApprovals,
                busy = ui.approving,
                onApprove = { vm.approve(it) },
                onApproveAll = { vm.approveAll() },
                onEdit = { p ->
                    vm.clearAddError()
                    editTarget = EditTarget(p.id, p.date, p.direction, p.category, p.detail, p.amountCents, p.userName)
                },
            )
        }

        // Admin: outstanding Bible-reward payouts to approve.
        if (data.canApproveRewards && data.rewardMonths.isNotEmpty()) {
            RewardsSection(
                months = data.rewardMonths,
                onApproveMonth = { confirmMonth = it },
                onApproveBase = { c, pk -> confirmBase = c to pk },
            )
        }

        if (participants.isEmpty()) {
            EmptyLedger(onAdd = { vm.clearAddError(); showAdd = true })
            if (data.isAdmin) {
                StartingFundsButton { vm.clearAddError(); showStarting = true }
            }
        } else {
            if (participants.size > 1) {
                PeopleSelector(participants, data.selectedId) { vm.select(it) }
            }

            if (selected != null) {
                SelectedHeader(selected)
            }

            ActionBar(
                searching = searching,
                onAdd = { vm.clearAddError(); showAdd = true },
                onToggleSearch = {
                    if (searching) query = ""
                    searching = !searching
                },
            )
            if (searching) {
                SearchField(query) { query = it }
            }

            LedgerCard(
                rows = data.rows,
                query = query,
                isAdmin = data.isAdmin,
                onRowClick = { rowAction = it },
            )

            if (data.isAdmin) {
                StartingFundsButton { vm.clearAddError(); showStarting = true }
            }
        }
    }

    if (showAdd) {
        AddMoneyDialog(
            today = data.today,
            roster = data.roster,
            defaultUserId = data.selectedId ?: data.roster.firstOrNull()?.id,
            frequentPayments = data.frequentPayments,
            adding = ui.adding,
            serverError = ui.addError,
            onSubmit = { req -> vm.addEntry(req) { showAdd = false } },
            onDismiss = { showAdd = false },
        )
    }

    if (showStarting) {
        StartingFundsDialog(
            today = data.today,
            roster = data.roster,
            adding = ui.adding,
            serverError = ui.addError,
            onSubmit = { req -> vm.setStarting(req) { showStarting = false } },
            onDismiss = { showStarting = false },
        )
    }

    rowAction?.let { r ->
        RowActionDialog(
            label = rowLabel(r),
            amountText = (if (signedCents(r) < 0) "-" else "") + formatCents(r.amountCents),
            approved = r.status == "APPROVED",
            busy = ui.approving,
            onApprove = { vm.approve(r.id) { rowAction = null } },
            onUnapprove = { vm.unapprove(r.id) { rowAction = null } },
            onEdit = {
                vm.clearAddError()
                editTarget = EditTarget(r.id, r.date, r.direction, r.category, r.detail, r.amountCents, "")
                rowAction = null
            },
            onDelete = { vm.deleteEntry(r.id) { rowAction = null } },
            onDismiss = { rowAction = null },
        )
    }

    editTarget?.let { t ->
        EditMoneyDialog(
            target = t,
            saving = ui.adding,
            serverError = ui.addError,
            onSubmit = { req -> vm.updateEntry(req) { editTarget = null } },
            onDismiss = { editTarget = null },
        )
    }

    confirmMonth?.let { m ->
        val suffix = if (m.bonusAvailable) " (+${formatDollars(m.bonusCents)} bonus each)" else ""
        AnimatedDialog(
            onDismissRequest = { confirmMonth = null },
            title = "Approve rewards?",
            dismissButton = { TextButton(onClick = { confirmMonth = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(
                    enabled = !ui.approving,
                    onClick = { vm.approveRewardMonth(m.periodKey) { confirmMonth = null } },
                ) { Text(if (ui.approving) "Approving\u2026" else "Approve") }
            },
        ) {
            Column {
                Text(
                    "Pay ${m.label} rewards to ${m.completers.size} " +
                        (if (m.completers.size == 1) "person" else "people") + suffix + ".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ui.approveError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    confirmBase?.let { (c, pk) ->
        AnimatedDialog(
            onDismissRequest = { confirmBase = null },
            title = "Approve reward?",
            dismissButton = { TextButton(onClick = { confirmBase = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(
                    enabled = !ui.approving,
                    onClick = { vm.approveRewardBase(c.userId, pk) { confirmBase = null } },
                ) { Text(if (ui.approving) "Approving\u2026" else "Approve") }
            },
        ) {
            Column {
                Text(
                    "Pay ${formatDollars(c.baseCents)} to ${c.name}?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ui.approveError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ---- Approval queue (admin) ----

@Composable
private fun ApprovalQueue(
    pending: List<MoneyPendingDto>,
    busy: Boolean,
    onApprove: (String) -> Unit,
    onApproveAll: () -> Unit,
    onEdit: (MoneyPendingDto) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AMBER_BG)
            .border(1.dp, AMBER_BORDER, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Awaiting approval",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                pending.size.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = AMBER_DK,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(AMBER_BORDER.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        pending.forEach { p ->
            val out = p.direction != "DEPOSIT"
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        p.userName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${formatShortYear(p.date)}  \u00b7  " + pendingLabel(p),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    (if (out) "-" else "") + formatCents(p.amountCents),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (out) NEG else MONEY,
                )
                Row(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (busy) GREEN600.copy(alpha = 0.5f) else GREEN600)
                        .clickable(enabled = !busy) { onApprove(p.id) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(KairosIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Text("Approve", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        .clickable { onEdit(p) }
                        .padding(6.dp),
                ) {
                    Icon(KairosIcons.Pencil, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                }
            }
        }
        if (pending.size > 1) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (busy) AMBER.copy(alpha = 0.5f) else AMBER)
                    .clickable(enabled = !busy) { onApproveAll() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("Approve all", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}

// ---- People selector ----

@Composable
private fun PeopleSelector(
    participants: List<MoneyParticipantDto>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        participants.forEach { p ->
            val person = p.person ?: return@forEach
            val active = person.id == selectedId
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) KairosThemeState.accent.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable { onSelect(person.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(parseHex(person.color)))
                Text(
                    person.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (active) KairosThemeState.accent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatAmountGrouped(p.balanceCents),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        p.balanceCents < 0 -> NEG
                        active -> KairosThemeState.accent
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectedHeader(p: MoneyParticipantDto) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                p.person?.name ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatDollars(p.balanceCents),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (p.balanceCents < 0) NEG else MaterialTheme.colorScheme.onSurface,
            )
        }
        Divider()
    }
}

// ---- Action bar ----

@Composable
private fun ActionBar(searching: Boolean, onAdd: () -> Unit, onToggleSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FilledButton("Add transaction", KairosIcons.Plus, onAdd)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(
                    1.dp,
                    if (searching) KairosThemeState.accent else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(8.dp),
                )
                .clickable { onToggleSearch() }
                .padding(8.dp),
        ) {
            Icon(
                KairosIcons.Search,
                contentDescription = "Search transactions",
                tint = if (searching) KairosThemeState.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun FilledButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(KairosThemeState.accent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color.White)
    }
}

@Composable
private fun StartingFundsButton(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Row(
            Modifier.clickable { onClick() }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(KairosIcons.Dollar, contentDescription = null, tint = KairosThemeState.accent, modifier = Modifier.size(16.dp))
            Text("Set starting funds", style = MaterialTheme.typography.labelLarge, color = KairosThemeState.accent)
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        "Search categories, notes, amounts",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
        )
    }
}

// ---- Ledger ----

@Composable
private fun LedgerCard(
    rows: List<MoneyRowDto>,
    query: String,
    isAdmin: Boolean,
    onRowClick: (MoneyRowDto) -> Unit,
) {
    val q = query.trim().lowercase()
    val filtered = if (q.isEmpty()) rows else rows.filter { r ->
        listOf(
            formatShortYear(r.date),
            rowLabel(r),
            categoryLabel(r.category),
            r.detail ?: "",
            formatCents(r.amountCents),
        ).joinToString(" ").lowercase().contains(q)
    }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
                Text("Category / details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("Amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(28.dp))
            }
            if (filtered.isEmpty()) {
                Divider()
                Text(
                    if (rows.isEmpty()) "No transactions yet." else "Nothing matches that search.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                filtered.forEach { r ->
                    Divider()
                    LedgerRow(r, isAdmin, onRowClick)
                }
            }
        }
    }
}

@Composable
private fun LedgerRow(r: MoneyRowDto, isAdmin: Boolean, onRowClick: (MoneyRowDto) -> Unit) {
    val out = signedCents(r) < 0
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (isAdmin) Modifier.clickable { onRowClick(r) } else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatShortYear(r.date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Text(
            rowLabel(r),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            (if (out) "-" else "") + formatCents(r.amountCents),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (out) NEG else MONEY,
        )
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (r.status == "APPROVED") {
                Icon(KairosIcons.Check, contentDescription = "Approved", tint = MONEY, modifier = Modifier.size(16.dp))
            } else {
                Box(Modifier.size(8.dp).clip(CircleShape).background(PENDING_DOT))
            }
        }
    }
}

// ---- Row action (admin) ----

@Composable
private fun RowActionDialog(
    label: String,
    amountText: String,
    approved: Boolean,
    busy: Boolean,
    onApprove: () -> Unit,
    onUnapprove: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmingDelete by remember { mutableStateOf(false) }
    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = label,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(amountText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (confirmingDelete) {
                Text("Delete this transaction?", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionRow("Cancel", null, MaterialTheme.colorScheme.outline, filled = false, enabled = true) { confirmingDelete = false }
                    ActionRow("Delete", KairosIcons.Trash, DANGER, filled = true, enabled = !busy, onClick = onDelete)
                }
            } else {
                if (approved) {
                    ActionRow("Unapprove", null, AMBER_DK, filled = false, enabled = !busy, onClick = onUnapprove)
                } else {
                    ActionRow("Approve", KairosIcons.Check, GREEN600, filled = true, enabled = !busy, onClick = onApprove)
                }
                ActionRow("Edit", KairosIcons.Pencil, KairosThemeState.accent, filled = false, enabled = true, onClick = onEdit)
                ActionRow("Delete", KairosIcons.Trash, DANGER, filled = false, enabled = true) { confirmingDelete = true }
            }
        }
    }
}

@Composable
private fun ActionRow(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    color: Color,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (filled) (if (enabled) color else color.copy(alpha = 0.5f)) else Color.Transparent
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(if (filled) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (filled) Color.White else color, modifier = Modifier.size(16.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (filled) Color.White else color)
    }
}

// ---- Empty state ----

@Composable
private fun EmptyLedger(onAdd: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(KairosIcons.Dollar, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            Text("No money tracked yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Add a deposit or payment for someone and they\u2019ll appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            FilledButton("Add transaction", KairosIcons.Plus, onAdd)
        }
    }
}

// ---- Bible reward approvals (admin) ----

@Composable
private fun RewardsSection(
    months: List<MoneyRewardMonthDto>,
    onApproveMonth: (MoneyRewardMonthDto) -> Unit,
    onApproveBase: (MoneyRewardCompleterDto, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(KairosIcons.Bible, contentDescription = null, tint = AMBER_DK, modifier = Modifier.size(20.dp))
            Text("Bible reading rewards", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        months.forEach { m ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AMBER_BG)
                    .border(1.dp, AMBER_BORDER, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            "${m.completers.size} " +
                                (if (m.completers.size == 1) "person" else "people") + " finished" +
                                (if (m.bonusAvailable) " \u2014 everyone finished, bonus available" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (m.bonusAvailable) MONEY else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AMBER)
                            .clickable { onApproveMonth(m) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            if (m.bonusAvailable) "Approve all + bonus" else "Approve all",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                }
                if (!m.bonusAvailable) {
                    m.completers.forEach { c ->
                        Divider()
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(c.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(formatDollars(c.baseCents), style = MaterialTheme.typography.bodyMedium, color = MONEY)
                            if (c.needsBase) {
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AMBER)
                                        .clickable { onApproveBase(c, m.periodKey) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    Text("Approve", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(KairosIcons.Check, contentDescription = null, tint = MONEY, modifier = Modifier.size(14.dp))
                                    Text("Paid", style = MaterialTheme.typography.labelMedium, color = MONEY)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Add / Edit / Starting forms ----

@Composable
private fun AddMoneyDialog(
    today: String,
    roster: List<PersonDto>,
    defaultUserId: String?,
    frequentPayments: List<String>,
    adding: Boolean,
    serverError: String?,
    onSubmit: (AddMoneyRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var forId by remember { mutableStateOf(defaultUserId ?: roster.firstOrNull()?.id ?: "") }
    var direction by remember { mutableStateOf("DEPOSIT") }
    var dateIso by remember { mutableStateOf(today.ifBlank { LocalDate.now().toString() }) }
    var category by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var openSelector by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }

    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = "Add transaction",
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(
                enabled = !adding,
                onClick = {
                    val cents = parseAmountToCents(amount)
                    when {
                        forId.isBlank() -> localError = "Pick who this is for."
                        cents == null || cents <= 0 -> localError = "Enter an amount over $0.00."
                        direction == "DEPOSIT" && category.isBlank() -> localError = "Pick a category for the deposit."
                        else -> {
                            localError = null
                            onSubmit(
                                AddMoneyRequest(
                                    userId = forId,
                                    direction = direction,
                                    amountCents = cents,
                                    category = if (direction == "DEPOSIT") category else null,
                                    detail = detail.trim().ifBlank { null },
                                    date = dateIso,
                                ),
                            )
                        }
                    }
                },
            ) { Text(if (adding) "Saving\u2026" else "Submit") }
        },
    ) {
        Column(
            Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (roster.size > 1) {
                SelectRow("For", roster.firstOrNull { it.id == forId }?.name ?: "") { openSelector = "for" }
            }
            DateRow(dateIso) { showDate = true }
            SelectRow("Type", directionLabel(direction)) { openSelector = "type" }
            if (direction == "DEPOSIT") {
                SelectRow("Category", categoryLabel(category)) { openSelector = "category" }
            }
            if (direction == "PAYMENT" && frequentPayments.isNotEmpty()) {
                SelectRow("Frequently used", "", placeholder = "Pick a common payment\u2026") { openSelector = "frequent" }
            }
            FormField(
                label = if (direction == "DEPOSIT") "Details (optional)" else "Details",
                value = detail,
                onValueChange = { detail = it },
                placeholder = if (direction == "DEPOSIT") "e.g. from Grandma" else "e.g. bought a game",
            )
            FormField(
                label = "Amount (USD)",
                value = amount,
                onValueChange = { amount = it },
                placeholder = "0.00",
                keyboardType = KeyboardType.Decimal,
            )
            val err = localError ?: serverError
            if (err != null) {
                Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    when (openSelector) {
        "for" -> OptionsDialog("For", roster.map { it.id to it.name }, forId, { forId = it }, { openSelector = null })
        "type" -> OptionsDialog("Type", DIRECTIONS, direction, { direction = it }, { openSelector = null })
        "category" -> OptionsDialog("Category", DEPOSIT_CATEGORIES, category, { category = it }, { openSelector = null })
        "frequent" -> OptionsDialog("Frequently used", frequentPayments.map { it to it }, null, { detail = it }, { openSelector = null })
    }

    if (showDate) {
        DatePickerOverlay(dateIso, onPick = { dateIso = it; showDate = false }, onDismiss = { showDate = false })
    }
}

@Composable
private fun EditMoneyDialog(
    target: EditTarget,
    saving: Boolean,
    serverError: String?,
    onSubmit: (UpdateMoneyRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var direction by remember { mutableStateOf(target.direction) }
    var dateIso by remember { mutableStateOf(target.date) }
    var category by remember { mutableStateOf(target.category ?: "") }
    var detail by remember { mutableStateOf(target.detail ?: "") }
    var amount by remember { mutableStateOf(formatCents(target.amountCents)) }
    var localError by remember { mutableStateOf<String?>(null) }
    var openSelector by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }

    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = if (target.who.isBlank()) "Edit transaction" else "Edit \u2014 ${target.who}",
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    val cents = parseAmountToCents(amount)
                    when {
                        cents == null || cents <= 0 -> localError = "Enter an amount over $0.00."
                        direction == "DEPOSIT" && category.isBlank() -> localError = "Pick a category for the deposit."
                        else -> {
                            localError = null
                            onSubmit(
                                UpdateMoneyRequest(
                                    id = target.id,
                                    direction = direction,
                                    amountCents = cents,
                                    category = if (direction == "DEPOSIT") category else null,
                                    detail = detail.trim().ifBlank { null },
                                    date = dateIso,
                                ),
                            )
                        }
                    }
                },
            ) { Text(if (saving) "Saving\u2026" else "Save changes") }
        },
    ) {
        Column(
            Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DateRow(dateIso) { showDate = true }
            SelectRow("Type", directionLabel(direction)) { openSelector = "type" }
            if (direction == "DEPOSIT") {
                SelectRow("Category", categoryLabel(category)) { openSelector = "category" }
            }
            FormField(label = "Details", value = detail, onValueChange = { detail = it }, placeholder = "")
            FormField(label = "Amount (USD)", value = amount, onValueChange = { amount = it }, placeholder = "0.00", keyboardType = KeyboardType.Decimal)
            val err = localError ?: serverError
            if (err != null) {
                Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    when (openSelector) {
        "type" -> OptionsDialog("Type", DIRECTIONS, direction, { direction = it }, { openSelector = null })
        "category" -> OptionsDialog("Category", DEPOSIT_CATEGORIES, category, { category = it }, { openSelector = null })
    }
    if (showDate) {
        DatePickerOverlay(dateIso, onPick = { dateIso = it; showDate = false }, onDismiss = { showDate = false })
    }
}

@Composable
private fun StartingFundsDialog(
    today: String,
    roster: List<PersonDto>,
    adding: Boolean,
    serverError: String?,
    onSubmit: (StartingFundsRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var forId by remember { mutableStateOf(roster.firstOrNull()?.id ?: "") }
    var dateIso by remember { mutableStateOf(today.ifBlank { LocalDate.now().toString() }) }
    var amount by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var openSelector by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }

    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = "Set starting funds",
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(
                enabled = !adding,
                onClick = {
                    val cents = parseAmountToCents(amount)
                    when {
                        forId.isBlank() -> localError = "Pick who this is for."
                        cents == null || cents <= 0 -> localError = "Enter an amount over $0.00."
                        else -> {
                            localError = null
                            onSubmit(StartingFundsRequest(userId = forId, amountCents = cents, date = dateIso))
                        }
                    }
                },
            ) { Text(if (adding) "Saving\u2026" else "Submit") }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "The balance already in hand before this ledger begins. Shown as a \u201cStarting funds\u201d line and approved automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (roster.size > 1) {
                SelectRow("For", roster.firstOrNull { it.id == forId }?.name ?: "") { openSelector = "for" }
            }
            DateRow(dateIso) { showDate = true }
            FormField(label = "Amount (USD)", value = amount, onValueChange = { amount = it }, placeholder = "0.00", keyboardType = KeyboardType.Decimal)
            val err = localError ?: serverError
            if (err != null) {
                Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (openSelector == "for") {
        OptionsDialog("For", roster.map { it.id to it.name }, forId, { forId = it }, { openSelector = null })
    }
    if (showDate) {
        DatePickerOverlay(dateIso, onPick = { dateIso = it; showDate = false }, onDismiss = { showDate = false })
    }
}

// ---- Form building blocks ----

@Composable
private fun SelectRow(label: String, value: String, placeholder: String = "Choose\u2026", onClick: () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value.ifBlank { placeholder },
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(KairosIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DateRow(dateIso: String, onClick: () -> Unit) {
    Column {
        Text("Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(formatLongDate(dateIso), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun OptionsDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = title,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            options.forEach { (key, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(key); onDismiss() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (key == selectedKey) KairosThemeState.accent else MaterialTheme.colorScheme.outline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == selectedKey) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(KairosThemeState.accent))
                        }
                    }
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerOverlay(dateIso: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = isoToUtcMillis(dateIso))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(utcMillisToIso(it)) } }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

// ---- labels / formatting ----

private val DIRECTIONS: List<Pair<String, String>> = listOf(
    "DEPOSIT" to "Deposit (money in)",
    "PAYMENT" to "Payment (money out)",
)

private fun directionLabel(d: String): String =
    if (d == "PAYMENT") "Payment (money out)" else "Deposit (money in)"

private val DEPOSIT_CATEGORIES: List<Pair<String, String>> = listOf(
    "BIRTHDAY" to "Birthday",
    "GIFT" to "Gift",
    "HOLIDAY" to "Holiday",
    "EARNINGS" to "Earnings",
    "BIBLE" to "Bible reading",
    "OTHER" to "Other",
)

private fun categoryLabel(c: String?): String = when (c) {
    "BIRTHDAY" -> "Birthday"
    "GIFT" -> "Gift"
    "HOLIDAY" -> "Holiday"
    "EARNINGS" -> "Earnings"
    "BIBLE" -> "Bible reading"
    "OTHER" -> "Other"
    null, "" -> ""
    else -> c
}

private fun rowLabel(r: MoneyRowDto): String = labelFor(r.kind, r.direction, r.category, r.detail)

private fun pendingLabel(p: MoneyPendingDto): String = labelFor(p.kind, p.direction, p.category, p.detail)

private fun labelFor(kind: String, direction: String, category: String?, detail: String?): String {
    if (kind == "STARTING") return "Starting funds"
    if (kind == "BIBLE_REWARD") return "Bible reading reward"
    if (kind == "BIBLE_BONUS") return "Bible reading bonus"
    if (direction == "DEPOSIT") {
        val cat = categoryLabel(category)
        if (cat.isNotBlank() && !detail.isNullOrBlank()) return "$cat \u2014 $detail"
        if (cat.isNotBlank()) return cat
        if (!detail.isNullOrBlank()) return detail
        return "Deposit"
    }
    return detail?.takeIf { it.isNotBlank() } ?: "Payment"
}

private fun signedCents(r: MoneyRowDto): Long =
    if (r.direction == "DEPOSIT") r.amountCents else -r.amountCents

private fun formatCents(cents: Long): String {
    val a = abs(cents)
    return "${a / 100}.${(a % 100).toString().padStart(2, '0')}"
}

private fun formatDollars(cents: Long): String {
    val a = abs(cents)
    val body = "${a / 100}.${(a % 100).toString().padStart(2, '0')}"
    return (if (cents < 0) "-$" else "$") + body
}

private fun formatAmountGrouped(cents: Long): String {
    val a = abs(cents)
    val whole = groupThousands(a / 100)
    val cc = (a % 100).toString().padStart(2, '0')
    return (if (cents < 0) "-" else "") + "$whole.$cc"
}

private fun groupThousands(n: Long): String {
    val s = n.toString()
    val rem = s.length % 3
    val sb = StringBuilder()
    for (i in s.indices) {
        if (i != 0 && (i - rem) % 3 == 0) sb.append(',')
        sb.append(s[i])
    }
    return sb.toString()
}

private fun parseAmountToCents(input: String): Long? {
    val cleaned = input.trim().replace(",", "").removePrefix("$").trim()
    if (cleaned.isEmpty()) return null
    val neg = cleaned.startsWith("-")
    val body = cleaned.removePrefix("-")
    if (body.isEmpty() || body == ".") return null
    if (body.count { it == '.' } > 1) return null
    val dot = body.indexOf('.')
    val wholeStr = if (dot < 0) body else body.substring(0, dot)
    val fracStr = if (dot < 0) "" else body.substring(dot + 1)
    if (fracStr.length > 2) return null
    if (wholeStr.any { !it.isDigit() } || fracStr.any { !it.isDigit() }) return null
    val whole = (if (wholeStr.isEmpty()) "0" else wholeStr).toLongOrNull() ?: return null
    val frac = (fracStr + "00").substring(0, 2).toLongOrNull() ?: return null
    val cents = whole * 100 + frac
    return if (neg) -cents else cents
}

private val SHORT_YEAR = DateTimeFormatter.ofPattern("M/d/yy")
private val LONG_DATE = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE

private fun formatShortYear(iso: String): String =
    try { LocalDate.parse(iso).format(SHORT_YEAR) } catch (_: Exception) { iso }

private fun formatLongDate(iso: String): String =
    try { LocalDate.parse(iso).format(LONG_DATE) } catch (_: Exception) { iso }

private fun isoToUtcMillis(iso: String): Long =
    try { LocalDate.parse(iso, ISO_DATE).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    catch (_: Exception) { Instant.now().toEpochMilli() }

private fun utcMillisToIso(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().format(ISO_DATE)

private fun parseHex(hex: String?): Color {
    val s = hex?.trim()?.removePrefix("#") ?: return Color(0xFF64748B)
    return try {
        when (s.length) {
            6 -> Color(("FF$s").toLong(16))
            8 -> Color(s.toLong(16))
            else -> Color(0xFF64748B)
        }
    } catch (_: NumberFormatException) {
        Color(0xFF64748B)
    }
}
