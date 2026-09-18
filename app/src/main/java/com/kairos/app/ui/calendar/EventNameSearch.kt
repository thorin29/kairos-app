package com.kairos.app.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.common.SentenceCaps

/**
 * The event-name field opens this full-screen search (the same shape as the
 * address search): a search box at the top and the remembered names as a
 * scrollable list. Pick one, or type a new name and use it — the typed value is
 * remembered for next time when the event saves. Alphabetical.
 */
@Composable
fun EventNameSearchScreen(
    initial: String,
    names: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val q = query.trim().lowercase()
    val matches =
        if (q.isEmpty()) {
            names.sortedBy { it.lowercase() }
        } else {
            names
                .filter { it.lowercase().contains(q) }
                .sortedWith(
                    compareByDescending<String> { it.lowercase().startsWith(q) }
                        .thenBy { it.lowercase() },
                )
        }
    val exact = q.isNotEmpty() && names.any { it.trim().lowercase() == q }

    fun useTyped() {
        val v = query.trim()
        if (v.isNotEmpty()) onPick(v) else onDismiss()
    }

    BackHandler(onBack = onDismiss)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("\u2715", style = MaterialTheme.typography.titleMedium)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    keyboardOptions = SentenceCaps.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { useTyped() }),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "Search or type an event name",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
            }

            Box(
                Modifier.fillMaxWidth().height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )

            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                if (query.trim().isNotEmpty() && !exact) {
                    item {
                        Text(
                            "\uFF0B Use \"${query.trim()}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { useTyped() }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                }
                items(matches, key = { it }) { n ->
                    Text(
                        n,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onPick(n) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
