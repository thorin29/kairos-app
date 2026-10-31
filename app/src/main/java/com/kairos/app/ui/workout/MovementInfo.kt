package com.kairos.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.nav.KairosIcons
import java.net.URLEncoder

/** A video search for how a movement is done (same link as the web). */
internal fun watchUrl(name: String): String =
    "https://www.youtube.com/results?search_query=" +
        URLEncoder.encode("$name exercise how to", "UTF-8").replace("+", "%20")

/** ⓘ beside a movement name. */
@Composable
internal fun MovementInfoButton(
    name: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(onClick = onClick) {
        Icon(
            KairosIcons.Info,
            contentDescription = "How to do $name",
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The how-to, and a "Watch how" video search. */
@Composable
internal fun MovementInfoDialog(name: String, about: String?, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = name,
        confirmButton = {
            TextButton(onClick = { runCatching { uri.openUri(watchUrl(name)) } }) { Text("Watch how") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    ) {
        if (!about.isNullOrBlank()) {
            Text(about, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The how-to opened under a row in a picker list. */
@Composable
internal fun MovementAboutInline(name: String, about: String?) {
    val uri = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (!about.isNullOrBlank()) {
            Text(about, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Watch how",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = if (about.isNullOrBlank()) 0.dp else 6.dp)
                .clickable { runCatching { uri.openUri(watchUrl(name)) } },
        )
    }
}

