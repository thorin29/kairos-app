package com.kairos.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kairos.app.ui.theme.KairosThemeState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.nav.KairosIcons

/** A slim bar at the bottom that reports connectivity + offline-change sync:
 *  offline (with any pending count), or "syncing N changes" once back online.
 *  Screens keep showing their last-synced data behind it. */
@Composable
fun OfflineBanner(online: Boolean, pending: Int, syncing: Boolean, modifier: Modifier = Modifier) {
    val visible = !online || syncing || pending > 0
    val changes = if (pending == 1) "1 change" else "$pending changes"
    val message = when {
        syncing -> "Syncing $changes\u2026"
        !online && pending > 0 -> "Offline \u2014 $changes will sync when you reconnect"
        !online -> "You're offline \u2014 showing saved data"
        pending > 0 -> "$changes waiting to sync"
        else -> ""
    }
    val color = if (!online) Color(0xFF334155) else KairosThemeState.accent
    val icon = if (syncing) KairosIcons.Repeat else KairosIcons.Globe

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(color)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(
                message,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
