package com.kairos.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kairos.app.ui.nav.KairosIcons

/**
 * A full page over the current screen (v0.491): the same top bar, back arrow
 * and background as a page reached by navigation, so a flow opened from a
 * screen (pick a workout, add cardio, a routine) looks like every other page.
 * The app's one pattern for anything you search, fill in or step through;
 * small centred pop-ups are only for short choices, confirmations and ⓘ.
 *
 * [scroll] wraps [content] in a padded scrolling column; pass false when the
 * content scrolls itself. [footer] stays pinned under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(KairosIcons.ArrowBack, contentDescription = "Back") }
                    },
                    actions = actions,
                )
            },
            bottomBar = { if (footer != null) Column(Modifier.navigationBarsPadding()) { footer() } },
        ) { inner ->
            if (scroll) {
                Column(
                    Modifier
                        .padding(inner)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                    Spacer(Modifier.height(24.dp))
                }
            } else {
                Column(Modifier.padding(inner).fillMaxSize()) { content() }
            }
        }
    }
}

/** A page's first block: search and filters on one white card (v0.491). */
@Composable
fun PageCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}
