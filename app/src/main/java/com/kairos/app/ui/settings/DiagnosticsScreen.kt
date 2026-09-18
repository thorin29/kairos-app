package com.kairos.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kairos.app.BuildConfig
import com.kairos.app.data.diag.Breadcrumbs
import com.kairos.app.ui.common.rememberContainer

/**
 * Shows the recent UI breadcrumb trail (navigation, dialogs, session changes) so
 * that after a "white screen" a user can reopen the app and read — or copy and
 * send — exactly what led up to it, with no logcat access. No user data is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val clipboard = LocalClipboardManager.current
    val crumbs = remember { Breadcrumbs.snapshot().asReversed() }
    var lossReason by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        lossReason = runCatching { container.settingsStore.enrollLossReason() }.getOrNull()
    }
    val header = "Kairos ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val text = buildString {
                            appendLine(header)
                            lossReason?.let { appendLine("Last sign-out: $it") }
                            appendLine()
                            appendLine("Recent activity (newest first):")
                            crumbs.forEach { appendLine(it) }
                        }
                        clipboard.setText(AnnotatedString(text))
                    }) { Text("Copy") }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text(header, style = MaterialTheme.typography.titleSmall) }
            lossReason?.let { r ->
                item {
                    Text(
                        "Last sign-out reason: $r",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Text(
                    "Recent activity (newest first)",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (crumbs.isEmpty()) {
                item {
                    Text(
                        "No activity recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(crumbs) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
