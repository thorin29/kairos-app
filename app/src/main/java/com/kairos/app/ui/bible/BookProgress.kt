package com.kairos.app.ui.bible

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kairos.app.ui.nav.KairosIcons

private val ACCENT = Color(0xFF0F5C63)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookProgress(
    vm: BibleViewModel,
    readKeys: List<String>,
    @Suppress("UNUSED_PARAMETER") color: Color,
    busy: Boolean,
) {
    val keys = remember(readKeys) { readKeys.toHashSet() }
    var openBook by remember { mutableStateOf<BibleBook?>(null) }

    fun coveredCount(b: BibleBook): Int {
        var n = 0
        for (c in 1..b.chapters) if ("${b.name}|$c" in keys) n++
        return n
    }

    val totalChapters = remember { BIBLE_BOOKS.sumOf { it.chapters } }
    val totalCovered = BIBLE_BOOKS.sumOf { coveredCount(it) }
    val allDone = totalCovered >= totalChapters

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$totalCovered of $totalChapters chapters covered",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (allDone) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        androidx.compose.material3.Icon(
                            KairosIcons.Trophy, contentDescription = null,
                            tint = Color.White, modifier = Modifier.size(14.dp),
                        )
                        Text("Whole Bible", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BulkChip("Mark Old Testament read", enabled = !busy) {
                    vm.bulkBooks(BIBLE_BOOKS.filter { it.testament == Testament.OT }.map { it.name }, true)
                }
                BulkChip("Mark New Testament read", enabled = !busy) {
                    vm.bulkBooks(BIBLE_BOOKS.filter { it.testament == Testament.NT }.map { it.name }, true)
                }
                if (totalCovered > 0) {
                    BulkChip("Clear hand-marked", enabled = !busy, danger = true) {
                        vm.bulkBooks(BIBLE_BOOKS.map { it.name }, false)
                    }
                }
            }

            BIBLE_GROUPS.forEach { group ->
                val gColor = GROUP_COLOR[group] ?: ACCENT
                val books = BIBLE_BOOKS.filter { it.group == group }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(gColor))
                        Text(group.uppercase(), style = MaterialTheme.typography.labelSmall, color = gColor, fontWeight = FontWeight.SemiBold)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        books.forEach { b ->
                            val n = coveredCount(b)
                            val state = when {
                                n >= b.chapters -> BookState.COMPLETE
                                n > 0 -> BookState.PARTIAL
                                else -> BookState.NONE
                            }
                            BookChip(b.name, state, gColor, enabled = !busy) { openBook = b }
                        }
                    }
                }
            }
        }
    }

    openBook?.let { book ->
        ChapterEditor(
            book = book,
            keys = keys,
            busy = busy,
            onDismiss = { openBook = null },
            onSave = { chapters ->
                vm.saveBook(book.name, chapters)
                openBook = null
            },
        )
    }
}

private enum class BookState { COMPLETE, PARTIAL, NONE }

@Composable
private fun BookChip(name: String, state: BookState, color: Color, enabled: Boolean, onClick: () -> Unit) {
    val bg = when (state) {
        BookState.COMPLETE -> color
        BookState.PARTIAL -> color.copy(alpha = 0.13f)
        BookState.NONE -> Color.Transparent
    }
    val fg = when (state) {
        BookState.COMPLETE -> Color.White
        BookState.PARTIAL -> color
        BookState.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = when (state) {
        BookState.COMPLETE -> color
        BookState.PARTIAL -> color.copy(alpha = 0.4f)
        BookState.NONE -> color.copy(alpha = 0.25f)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
private fun BulkChip(label: String, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val fg = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChapterEditor(
    book: BibleBook,
    keys: Set<String>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<Int>) -> Unit,
) {
    val color = GROUP_COLOR[book.group] ?: ACCENT
    val original = remember(book.name) {
        (1..book.chapters).filter { "${book.name}|$it" in keys }.toSet()
    }
    var draft by remember(book.name) { mutableStateOf(original) }
    val dirty = draft != original

    fun toggle(c: Int) {
        draft = draft.toMutableSet().apply { if (!add(c)) remove(c) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 620.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                // Header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(book.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
                        Text(
                            "${draft.size} of ${book.chapters} chapters",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("\u2715") }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                // Body (scrolls)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (book.chapters == 1) {
                        val on = 1 in draft
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (on) color else Color.Transparent)
                                .border(1.dp, if (on) color else color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .clickable { draft = if (on) emptySet() else setOf(1) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        ) {
                            Text(book.name, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..book.chapters).forEach { c ->
                                val on = c in draft
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (on) color else Color.Transparent)
                                        .border(1.dp, if (on) color else color.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                                        .clickable { toggle(c) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        c.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BulkChip("Select whole book", enabled = true) {
                            draft = (1..book.chapters).toSet()
                        }
                        BulkChip("Clear book", enabled = true) { draft = emptySet() }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendSwatch("Marked", color, color)
                        LegendSwatch("Not read", Color.Transparent, color.copy(alpha = 0.4f))
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                // Footer
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (dirty) "Unsaved changes" else "No changes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        TextButton(onClick = { onSave(draft.toList()) }, enabled = dirty && !busy) { Text("Save") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendSwatch(label: String, fill: Color, border: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(fill)
                .border(1.dp, border, RoundedCornerShape(3.dp)),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
