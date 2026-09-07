package com.kairos.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairos.app.data.remote.dto.AttendeeDto
import com.kairos.app.ui.nav.KairosIcons

private val AttendedGreen = Color(0xFF16A34A)
private val UnknownGrey = Color(0xFF9CA3AF)

/** The per-person marker for a sport event: green check person = attended,
 *  red X person = did not attend, grey ? person = unknown. Nothing for "" (a
 *  non-sport event) — the name shows alone. */
@Composable
fun AttendanceIcon(state: String, modifier: Modifier = Modifier) {
    when (state) {
        "ATTENDED" -> Icon(KairosIcons.PersonCheck, "Attended", tint = AttendedGreen, modifier = modifier)
        "DECLINED" -> Icon(KairosIcons.PersonX, "Did not attend", tint = MaterialTheme.colorScheme.error, modifier = modifier)
        "UNKNOWN" -> Icon(KairosIcons.PersonQuestion, "Attendance unknown", tint = UnknownGrey, modifier = modifier)
    }
}

/** The "who" column for an event: one name per line, each with its attendance
 *  marker for sport events. Falls back to [fallbackLabel] (e.g. "Family") when
 *  there are no per-person entries. Right-aligned for the agenda/home cards. */
@Composable
fun AttendeesColumn(
    attendees: List<AttendeeDto>,
    fallbackLabel: String,
    modifier: Modifier = Modifier,
) {
    if (attendees.isEmpty()) {
        if (fallbackLabel.isNotBlank()) {
            Text(
                fallbackLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier,
            )
        }
        return
    }
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        attendees.forEach { a ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (a.state.isNotBlank()) {
                    AttendanceIcon(a.state, Modifier.size(16.dp).baselineAtBottom().alignByBaseline())
                }
                Text(
                    a.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
    }
}

/** Treat this composable's baseline as its bottom edge, so an icon can be
 *  baseline-aligned (bottom-of-icon to text baseline) in a Row. */
private fun Modifier.baselineAtBottom(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(
        placeable.width,
        placeable.height,
        mapOf(FirstBaseline to placeable.height, LastBaseline to placeable.height),
    ) {
        placeable.place(0, 0)
    }
}
