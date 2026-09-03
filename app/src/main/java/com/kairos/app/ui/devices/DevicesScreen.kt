package com.kairos.app.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.DeviceDto
import com.kairos.app.ui.common.rememberContainer
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val vm: DevicesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DevicesViewModel(container.sessionRepository) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Devices") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Box(
            Modifier
                .padding(inner)
                .fillMaxSize(),
        ) {
            when {
                ui.loading && ui.devices.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                ui.devices.isEmpty() && ui.error != null -> {
                    Text(
                        ui.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(ui.devices, key = { it.id }) { d ->
                            DeviceRow(d, ui.busyIds.contains(d.id)) { vm.revoke(d.id) }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DeviceDto, busy: Boolean, onRevoke: () -> Unit) {
    val active = device.status == "active"
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            device.name ?: "Unnamed device",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        val line = buildString {
            append(statusLabel(device))
            append(" · enrolled ")
            append(shortDate(device.enrolledAt))
            if (device.status == "active") {
                append(" · active ")
                append(relative(device.lastSeenAt))
            }
        }
        Text(
            line,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )

        if (device.current) {
            Text(
                "This device",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else if (active) {
            if (busy) {
                CircularProgressIndicator(
                    Modifier
                        .padding(top = 4.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                TextButton(
                    onClick = onRevoke,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text("Revoke", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun statusLabel(d: DeviceDto): String = when (d.status) {
    "active" -> "Active"
    "revoked" -> "Revoked"
    "expired" -> "Expired"
    else -> d.status
}

/** "2026-09-02T…" -> "9/2". */
private fun shortDate(iso: String): String = try {
    val date = iso.substringBefore("T").split("-")
    "${date[1].toInt()}/${date[2].toInt()}"
} catch (e: Exception) {
    iso
}

private fun relative(iso: String?): String {
    if (iso == null) return "never"
    return try {
        val secs = Duration.between(Instant.parse(iso), Instant.now()).seconds
        when {
            secs < 60 -> "just now"
            secs < 3600 -> "${secs / 60}m ago"
            secs < 86400 -> "${secs / 3600}h ago"
            else -> "${secs / 86400}d ago"
        }
    } catch (e: Exception) {
        "recently"
    }
}
