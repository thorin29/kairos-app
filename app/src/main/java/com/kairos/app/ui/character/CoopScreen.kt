package com.kairos.app.ui.character

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.CoopChildDto
import com.kairos.app.data.remote.dto.CoopDto
import com.kairos.app.data.remote.dto.CoopProposalDto
import com.kairos.app.ui.common.rememberContainer


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoopScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val vm: CoopViewModel = viewModel(
        factory = viewModelFactory { initializer { CoopViewModel(container.sessionRepository) } },
    )
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family goal") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            val data = ui.data
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ui.error ?: "Couldn't load the family goal.")
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text("Retry") }
                    }
                }
                else -> CoopContent(data, ui, vm)
            }
        }
    }
}

@Composable
private fun CoopContent(data: CoopDto, ui: CoopUiState, vm: CoopViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Progress toward the gate
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Season \u00b7 ${data.seasonLabel}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (data.gateMet) "Everyone reached the goal! \uD83C\uDF89"
                    else "${data.childrenMeeting}/${data.childrenTotal} kids at Tier ${data.floor}+",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val frac = if (data.childrenTotal > 0) data.childrenMeeting.toFloat() / data.childrenTotal else 0f
                Bar(frac, if (data.gateMet) Color(0xFF10B981) else KairosThemeState.accent, Modifier.fillMaxWidth())
                data.children.forEach { c -> ChildRow(c, data.floor) }
            }
        }

        ui.message?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF047857), modifier = Modifier.fillMaxWidth())
        }

        // Proposals
        Text("Rewards", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (data.proposals.isEmpty()) {
            Text("No rewards proposed yet \u2014 add one below.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        data.proposals.forEach { p -> ProposalCard(p, data, ui.busy, vm) }

        // Propose form
        ProposeForm(ui.busy) { title, detail -> vm.propose(title, detail) }
    }
}

@Composable
private fun ChildRow(c: CoopChildDto, floor: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(999.dp)).background(parseChildColor(c.color)))
        Text(c.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            if (c.meets) "Tier ${c.tier} \u2713" else "Tier ${c.tier} / $floor",
            style = MaterialTheme.typography.labelMedium,
            color = if (c.meets) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProposalCard(p: CoopProposalDto, data: CoopDto, busy: Boolean, vm: CoopViewModel) {
    val granted = p.status == "GRANTED"
    val selected = p.status == "SELECTED"
    OutlinedCard(
        Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (granted) StatusPill("Earned", Color(0xFF10B981))
                else if (selected) StatusPill("Selected", KairosThemeState.accent)
            }
            if (!p.detail.isNullOrBlank()) {
                Text(p.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Proposed by ${p.proposedByName} \u00b7 ${p.votes} vote${if (p.votes == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.vote(p.id) }, enabled = !busy) {
                    Text(if (p.iVoted) "Voted \u2713" else "Vote")
                }
                if (data.isAdmin && !granted) {
                    if (selected) {
                        OutlinedButton(onClick = { vm.grant(p.id) }, enabled = !busy) { Text("Grant") }
                    } else {
                        OutlinedButton(onClick = { vm.select(p.id) }, enabled = !busy) { Text("Select") }
                    }
                    OutlinedButton(onClick = { vm.remove(p.id) }, enabled = !busy) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProposeForm(busy: Boolean, onPropose: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Propose a reward", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Field(title, { title = it.take(80) }, "Reward (e.g. movie night)")
            Field(detail, { detail = it.take(200) }, "Details (optional)")
            OutlinedButton(
                onClick = { onPropose(title.trim(), detail.trim()); title = ""; detail = "" },
                enabled = !busy && title.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Propose") }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            },
        )
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val f = fraction.coerceIn(0f, 1f)
    Box(modifier.height(6.dp).clip(RoundedCornerShape(999.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(f).height(6.dp).clip(RoundedCornerShape(999.dp)).background(color))
    }
}

private fun parseChildColor(hex: String?): Color {
    val s = hex?.trim()?.removePrefix("#") ?: return Color(0xFF94A3B8)
    return try {
        when (s.length) {
            6 -> Color(("FF$s").toLong(16))
            8 -> Color(s.toLong(16))
            else -> Color(0xFF94A3B8)
        }
    } catch (_: NumberFormatException) {
        Color(0xFF94A3B8)
    }
}
