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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.ui.common.SentenceCaps
import com.kairos.app.ui.nav.KairosIcons

/** Characters a note may hold. Mirrors NOTES_MAX in the web's
 *  src/lib/calendar/event-notes.ts; the server caps it there either way. */
const val NOTES_MAX = 500

/**
 * Which events carry a free-text note. Mirrors takesNotes() in the web's
 * src/lib/calendar/event-notes.ts, which every save runs through: a note
 * belongs to an "Event" (APPOINTMENT) and a "Medical / Dental" (OTHER). A
 * custom event type such as a hockey game stores as OTHER but is its own
 * category, so it is deliberately left out.
 */
fun takesNotes(kind: String, eventTypeId: String?): Boolean {
    if (!eventTypeId.isNullOrBlank()) return false
    return kind == "APPOINTMENT" || kind == "OTHER"
}

/**
 * The event "Notes" row on the form. Exactly the shape of the Where row above
 * it — icon, one line of value, tap to open — because a form whose fields each
 * behave differently is harder to use than one where every row opens the same
 * way. Tapping it opens [NoteEditScreen].
 */
@Composable
fun NoteRow(value: String, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            KairosIcons.Pencil,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            // One line here whatever its length: the row is a summary, and the
            // whole note is a tap away. A note that wrapped to five lines would
            // push the rest of the form off the screen.
            value.ifBlank { "Add a note" }.replace('\n', ' '),
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The full-screen note editor, the same shape as the address and event-name
 * screens: its own surface, a close on the left, and the typing area filling
 * the space above the keyboard. Unlike those two it ends in Save rather than a
 * pick, because there is no list to choose from — and because a note is
 * multi-line, so Enter has to stay a newline instead of being the commit.
 *
 * Closing without Save leaves the note as it was.
 */
@Composable
fun NoteEditScreen(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    BackHandler(onBack = onDismiss)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "Note",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                TextButton(onClick = { onSave(text.trim().take(NOTES_MAX)) }) { Text("Save") }
            }

            Box(
                Modifier.fillMaxWidth().height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )

            BasicTextField(
                value = text,
                onValueChange = { text = it.take(NOTES_MAX) },
                keyboardOptions = SentenceCaps,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            "Flight AA 1234, confirmation 7XQP2, bring insurance card…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                },
            )

            Text(
                "${text.length} / $NOTES_MAX",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
