package com.kairos.app.ui.reading

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.AddBookRequest
import com.kairos.app.data.remote.dto.BookDto
import com.kairos.app.data.remote.dto.BooksDto
import com.kairos.app.data.remote.dto.UpdateBookRequest
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons

private val ACCENT = Color(0xFF0F5C63)      // global teal accent (buttons, progress)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingScreen(onOpenDrawer: () -> Unit) {
    val container = rememberContainer()
    val vm: ReadingViewModel = viewModel(
        factory = viewModelFactory { initializer { ReadingViewModel(container.sessionRepository) } },
    )
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading") },
                navigationIcon = { LogoMenuButton(onClick = onOpenDrawer) },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            val data = ui.data
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ui.loadError ?: "Couldn't load reading.")
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.load() }) { Text("Retry") }
                    }
                }
                else -> ReadingContent(vm, ui, data)
            }
        }
    }
}

@Composable
private fun ReadingContent(vm: ReadingViewModel, ui: ReadingUiState, data: BooksDto) {
    var showAdd by remember { mutableStateOf(false) }
    var showShelf by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<BookDto?>(null) }
    var deleteTarget by remember { mutableStateOf<BookDto?>(null) }

    val queue = data.books.filter { !it.shelved && !it.bookmarked && !it.finished }
    val toRead = data.books.filter { it.shelved && !it.bookmarked && !it.finished }
    val bookmarked = data.books.filter { it.bookmarked && !it.finished }
    val read = data.books.filter { it.finished }
    val shelfCount = toRead.size + bookmarked.size + read.size

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (queue.isEmpty()) {
            Text("Nothing on the go right now.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        queue.forEach { b ->
            BookCard(
                book = b,
                busy = ui.busy,
                onLog = { amt -> vm.log(b.id, amt) },
                onBookmark = { vm.bookmark(b.id, true) },
                onShelve = { vm.shelf(b.id, true) },
                onFinish = { vm.finish(b.id, true) },
                onEdit = { vm.clearSaveError(); editTarget = b },
                onDelete = { deleteTarget = b },
            )
        }

        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(ACCENT)
                .clickable { vm.clearSaveError(); showAdd = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(KairosIcons.Plus, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text("Add a book", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color.White)
        }

        if (shelfCount > 0) {
            Divider()
            Row(
                Modifier.clickable { showShelf = !showShelf }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(KairosIcons.Book, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(
                    (if (showShelf) "Hide bookshelf" else "Bookshelf") + " ($shelfCount)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (showShelf) {
                ShelfGroup("To read", toRead, "toRead", ui.busy, vm) { deleteTarget = it }
                ShelfGroup("Bookmarked", bookmarked, "bookmarked", ui.busy, vm) { deleteTarget = it }
                ShelfGroup("Read", read, "read", ui.busy, vm) { deleteTarget = it }
            }
        }
    }

    if (showAdd) {
        BookFormDialog(
            title = "Add a book",
            confirmLabel = "Add book",
            initial = null,
            saving = ui.saving,
            serverError = ui.saveError,
            onSubmit = { t, a, p, c ->
                vm.add(AddBookRequest(title = t, author = a, pages = p, chapters = c)) { showAdd = false }
            },
            onDismiss = { showAdd = false },
        )
    }

    editTarget?.let { b ->
        BookFormDialog(
            title = "Edit book",
            confirmLabel = "Save",
            initial = b,
            saving = ui.saving,
            serverError = ui.saveError,
            onSubmit = { t, a, p, c ->
                vm.update(UpdateBookRequest(id = b.id, title = t, author = a, pages = p, chapters = c)) { editTarget = null }
            },
            onDismiss = { editTarget = null },
        )
    }

    deleteTarget?.let { b ->
        AnimatedDialog(
            onDismissRequest = { deleteTarget = null },
            title = "Remove book?",
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(enabled = !ui.busy, onClick = { vm.delete(b.id) { deleteTarget = null } }) {
                    Text("Remove")
                }
            },
        ) {
            Text("Remove \u201c${b.title}\u201d? This can't be undone.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BookCard(
    book: BookDto,
    busy: Boolean,
    onLog: (Int) -> Unit,
    onBookmark: () -> Unit,
    onShelve: () -> Unit,
    onFinish: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var amount by remember(book.id, book.todayAmount) { mutableStateOf(if (book.todayAmount > 0) book.todayAmount.toString() else "") }
    val pct = if (book.length > 0) (book.read * 100 / book.length) else 0
    val done = if (book.length > 0) (book.read >= book.length) else false

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    book.author?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box(Modifier.clickable { onDelete() }.padding(4.dp)) {
                    Icon(KairosIcons.Trash, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            ProgressBar(pct)
            Text(
                "${book.read} / ${book.length} ${unitLabel(book.unit, book.length)} ($pct%)" +
                    (if (book.pages != null && book.chapters != null) "  \u00b7  " + sizeLabel(book) else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Read today:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier.width(72.dp).clip(RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    BasicTextField(
                        value = amount,
                        onValueChange = { s -> amount = s.filter { it.isDigit() }.take(6) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        decorationBox = { inner ->
                            if (amount.isEmpty()) Text("0", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        },
                    )
                }
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(ACCENT)
                        .clickable(enabled = !busy) { onLog(amount.toIntOrNull() ?: 0) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("Save", style = MaterialTheme.typography.labelLarge, color = Color.White) }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextLink("Edit") { onEdit() }
                TextLink("Bookmark") { if (!busy) onBookmark() }
                TextLink("Shelve") { if (!busy) onShelve() }
                TextLink(if (done) "Mark finished \u2713" else "Mark finished", color = ACCENT) { if (!busy) onFinish() }
            }
        }
    }
}

@Composable
private fun ShelfGroup(
    title: String,
    books: List<BookDto>,
    kind: String,
    busy: Boolean,
    vm: ReadingViewModel,
    onDelete: (BookDto) -> Unit,
) {
    if (books.isEmpty()) return
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "$title (${books.size})",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        books.forEach { b ->
            val pct = if (b.length > 0) (b.read * 100 / b.length) else 0
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            b.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (b.finished) TextDecoration.LineThrough else null,
                            color = if (b.finished) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        val place = when (kind) {
                            "read" -> "Read"
                            "bookmarked" -> "On ${b.read} of ${b.length} ${unitLabel(b.unit, b.length)}"
                            else -> if (b.read > 0) "$pct%" else "Not started"
                        }
                        Text(
                            (b.author?.takeIf { it.isNotBlank() }?.let { "$it  \u00b7  " } ?: "") + place,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextLink(if (kind == "read") "Reopen" else "Move to reading", color = ACCENT) {
                        if (!busy) when (kind) {
                            "read" -> vm.finish(b.id, false)
                            "bookmarked" -> vm.bookmark(b.id, false)
                            else -> vm.shelf(b.id, false)
                        }
                    }
                    Box(Modifier.clickable { onDelete(b) }.padding(2.dp)) {
                        Icon(KairosIcons.Trash, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BookFormDialog(
    title: String,
    confirmLabel: String,
    initial: BookDto?,
    saving: Boolean,
    serverError: String?,
    onSubmit: (title: String, author: String?, pages: Int?, chapters: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var bookTitle by remember { mutableStateOf(initial?.title ?: "") }
    var author by remember { mutableStateOf(initial?.author ?: "") }
    var pages by remember { mutableStateOf(initial?.pages?.toString() ?: "") }
    var chapters by remember { mutableStateOf(initial?.chapters?.toString() ?: "") }
    var localError by remember { mutableStateOf<String?>(null) }

    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = title,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    val p = pages.toIntOrNull() ?: 0
                    val c = chapters.toIntOrNull() ?: 0
                    when {
                        bookTitle.trim().isEmpty() -> localError = "Give the book a title."
                        p <= 0 && c <= 0 -> localError = "Enter a page or chapter count."
                        else -> {
                            localError = null
                            onSubmit(bookTitle.trim(), author.trim().ifBlank { null }, p.takeIf { it > 0 }, c.takeIf { it > 0 })
                        }
                    }
                },
            ) { Text(if (saving) "Saving\u2026" else confirmLabel) }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledField("Title", bookTitle, { bookTitle = it }, "Book title")
            LabeledField("Author (optional)", author, { author = it }, "Author")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { LabeledField("Pages", pages, { s -> pages = s.filter { it.isDigit() }.take(6) }, "0", KeyboardType.Number) }
                Box(Modifier.weight(1f)) { LabeledField("Chapters", chapters, { s -> chapters = s.filter { it.isDigit() }.take(6) }, "0", KeyboardType.Number) }
            }
            Text("Enter pages and/or chapters \u2014 at least one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (localError ?: serverError)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ---- small building blocks ----

@Composable
private fun ProgressBar(pct: Int) {
    Box(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier.fillMaxWidth(fraction = (pct.coerceIn(0, 100)) / 100f)
                .height(8.dp).clip(RoundedCornerShape(999.dp)).background(ACCENT),
        )
    }
}

@Composable
private fun TextLink(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier.clickable { onClick() },
    )
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                },
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

private fun unitLabel(unit: String, n: Int): String =
    if (unit == "PAGES") (if (n == 1) "page" else "pages") else (if (n == 1) "chapter" else "chapters")

private fun sizeLabel(b: BookDto): String {
    val parts = mutableListOf<String>()
    b.pages?.let { parts.add("$it ${if (it == 1) "page" else "pages"}") }
    b.chapters?.let { parts.add("$it ${if (it == 1) "chapter" else "chapters"}") }
    return parts.joinToString("  \u00b7  ")
}
