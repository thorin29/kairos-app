package com.kairos.app.ui.reading

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import com.kairos.app.ui.theme.KairosThemeState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
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
import com.kairos.app.data.remote.dto.GoalInputDto
import com.kairos.app.data.remote.dto.UpdateBookRequest
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.LogoMenuButton
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import androidx.compose.ui.text.input.KeyboardCapitalization
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingScreen(onOpenDrawer: () -> Unit, refreshKey: Int = 0) {
    val container = rememberContainer()
    val vm: ReadingViewModel = viewModel(
        factory = viewModelFactory { initializer { ReadingViewModel(container.sessionRepository, container.payloadCache) } },
    )
    val ui by vm.ui.collectAsState()
    androidx.compose.runtime.LaunchedEffect(refreshKey) { if (refreshKey > 0) vm.load() }

    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<BookDto?>(null) }

    when {
        showAdd -> BookFormScreen(
            title = "Add a book",
            confirmLabel = "Add book",
            initial = null,
            saving = ui.saving,
            serverError = ui.saveError,
            onSubmit = { t, a, p, c, _, goals ->
                vm.add(AddBookRequest(title = t, author = a, pages = p, chapters = c, goals = goals)) { showAdd = false }
            },
            onDismiss = { showAdd = false },
        )
        editTarget != null -> {
            val b = editTarget!!
            BookFormScreen(
                title = "Edit book",
                confirmLabel = "Save",
                initial = b,
                saving = ui.saving,
                serverError = ui.saveError,
                onSubmit = { t, a, p, c, pos, goals ->
                    vm.update(UpdateBookRequest(id = b.id, title = t, author = a, pages = p, chapters = c, position = pos, goals = goals)) { editTarget = null }
                },
                onDismiss = { editTarget = null },
            )
        }
        else -> Scaffold(
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
                    else -> ReadingContent(
                        vm, ui, data,
                        onAdd = { vm.clearSaveError(); showAdd = true },
                        onEdit = { vm.clearSaveError(); editTarget = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingContent(
    vm: ReadingViewModel,
    ui: ReadingUiState,
    data: BooksDto,
    onAdd: () -> Unit,
    onEdit: (BookDto) -> Unit,
) {
    var showShelf by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<BookDto?>(null) }

    val queue = data.books.filter { !it.shelved && !it.finished }
    val toRead = data.books.filter { it.shelved && !it.finished }
    val read = data.books.filter { it.finished }
    val shelfCount = toRead.size + read.size

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
                onLog = { page -> vm.log(b.id, page) },
                onShelve = { vm.shelf(b.id, true) },
                onFinish = { vm.finish(b.id, true) },
                onEdit = { onEdit(b) },
                onDelete = { deleteTarget = b },
            )
        }

        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(KairosThemeState.accent)
                .clickable { onAdd() }
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
                ShelfGroup("Read", read, "read", ui.busy, vm) { deleteTarget = it }
            }
        }
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
    onShelve: () -> Unit,
    onFinish: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var page by remember(book.id, book.position) { mutableStateOf(if (book.position > 0) book.position.toString() else "") }
    var saved by remember(book.id) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
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
                Text(
                    if (book.unit == "PAGES") "Page you're on:" else "Chapter you're on:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    Modifier.width(72.dp).clip(RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    BasicTextField(
                        value = page,
                        onValueChange = { s -> page = s.filter { it.isDigit() }.take(6); saved = false },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        decorationBox = { inner ->
                            if (page.isEmpty()) Text("0", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        },
                    )
                }
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(if (saved) KairosThemeState.accent.copy(alpha = 0.4f) else KairosThemeState.accent)
                        .clickable(enabled = !busy) { focusManager.clearFocus(); onLog(page.toIntOrNull() ?: 0); saved = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (saved) Icon(KairosIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text(if (saved) "Saved" else "Save", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextLink("Edit") { onEdit() }
                TextLink("Shelve") { if (!busy) onShelve() }
                TextLink(if (done) "Mark finished \u2713" else "Mark finished", color = KairosThemeState.accent) { if (!busy) onFinish() }
                if (book.goals.isNotEmpty()) {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        KairosIcons.Bookmark,
                        contentDescription = "Has reading goals",
                        tint = KairosThemeState.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
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
                        val place = if (kind == "read") {
                            "Read"
                        } else if (b.position > 0) {
                            "On ${b.read} of ${b.length} ${unitLabel(b.unit, b.length)}"
                        } else {
                            "Not started"
                        }
                        Text(
                            (b.author?.takeIf { it.isNotBlank() }?.let { "$it  \u00b7  " } ?: "") + place,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextLink(if (kind == "read") "Reopen" else "Move to reading", color = KairosThemeState.accent) {
                        if (!busy) {
                            if (kind == "read") vm.finish(b.id, false) else vm.shelf(b.id, false)
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

private data class GoalDraft(
    val id: String,
    val target: Int,
    val dueDate: String,
    val completed: Boolean,
)

private fun isoFromMillis(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

private fun prettyDate(iso: String): String =
    try {
        LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
    } catch (e: Exception) {
        iso
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookFormScreen(
    title: String,
    confirmLabel: String,
    initial: BookDto?,
    saving: Boolean,
    serverError: String?,
    onSubmit: (title: String, author: String?, pages: Int?, chapters: Int?, position: Int?, goals: List<GoalInputDto>) -> Unit,
    onDismiss: () -> Unit,
) {
    var bookTitle by remember { mutableStateOf(initial?.title ?: "") }
    var author by remember { mutableStateOf(initial?.author ?: "") }
    var pages by remember { mutableStateOf(initial?.pages?.toString() ?: "") }
    var chapters by remember { mutableStateOf(initial?.chapters?.toString() ?: "") }
    var position by remember { mutableStateOf(if ((initial?.position ?: 0) > 0) initial!!.position.toString() else "") }
    var goals by remember {
        mutableStateOf<List<GoalDraft>>(
            initial?.goals?.map { GoalDraft(it.id, it.target, it.dueDate, it.completed) } ?: emptyList(),
        )
    }
    var localError by remember { mutableStateOf<String?>(null) }
    var addingGoal by remember { mutableStateOf(false) }

    val goalUnit = if (initial?.unit == "CHAPTERS") "Chapter" else "Page"

    BackHandler(enabled = true) { onDismiss() }
    Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(KairosIcons.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        Button(
                            enabled = !saving,
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                            modifier = Modifier.padding(end = 8.dp),
                            onClick = {
                                val p = pages.toIntOrNull() ?: 0
                                val c = chapters.toIntOrNull() ?: 0
                                when {
                                    bookTitle.trim().isEmpty() -> localError = "Give the book a title."
                                    p <= 0 && c <= 0 -> localError = "Enter a page or chapter count."
                                    else -> {
                                        localError = null
                                        onSubmit(
                                            bookTitle.trim(),
                                            author.trim().ifBlank { null },
                                            p.takeIf { it > 0 },
                                            c.takeIf { it > 0 },
                                            if (initial != null) (position.toIntOrNull() ?: 0) else null,
                                            goals.map {
                                                GoalInputDto(
                                                    id = it.id.ifBlank { null },
                                                    target = it.target,
                                                    dueDate = it.dueDate,
                                                )
                                            },
                                        )
                                    }
                                }
                            },
                        ) { Text(if (saving) "Saving\u2026" else confirmLabel) }
                    },
                )
            },
        ) { pad ->
            Column(
                Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LabeledField("Title", bookTitle, { bookTitle = it }, "Book title")
                LabeledField("Author (optional)", author, { author = it }, "Author")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { LabeledField("Pages", pages, { s -> pages = s.filter { it.isDigit() }.take(6) }, "0", KeyboardType.Number) }
                    Box(Modifier.weight(1f)) { LabeledField("Chapters", chapters, { s -> chapters = s.filter { it.isDigit() }.take(6) }, "0", KeyboardType.Number) }
                }
                Text("Enter pages and/or chapters \u2014 at least one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (initial != null) {
                    LabeledField(
                        if (initial.unit == "PAGES") "Page you're on" else "Chapter you're on",
                        position,
                        { s -> position = s.filter { it.isDigit() }.take(6) },
                        "0",
                        KeyboardType.Number,
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                Text("Reading goals (optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                goals.sortedBy { it.dueDate }.forEach { g ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "$goalUnit ${g.target} \u00b7 ${prettyDate(g.dueDate)}" + if (g.completed) "  \u2713" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (g.completed) Color(0xFF047857) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { goals = goals.filterNot { it === g } }) {
                            Icon(KairosIcons.Close, contentDescription = "Remove goal", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Row(
                    Modifier.clickable { addingGoal = true }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(KairosIcons.Plus, contentDescription = null, tint = KairosThemeState.accent, modifier = Modifier.size(18.dp))
                    Text("Add reading goal", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = KairosThemeState.accent)
                }

                (localError ?: serverError)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

    if (addingGoal) {
        AddGoalOverlay(
            unitLabel = goalUnit,
            onAdd = { target, dueDate ->
                goals = goals + GoalDraft("", target, dueDate, false)
                addingGoal = false
            },
            onDismiss = { addingGoal = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGoalOverlay(
    unitLabel: String,
    onAdd: (target: Int, dueDate: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var target by remember { mutableStateOf("") }
    var dueMillis by remember { mutableStateOf<Long?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dateRowFocus = remember { FocusRequester() }
    LaunchedEffect(showDate) {
        if (!showDate) {
            // The nested date picker restores focus to the last-focused text field
            // (the goal field) on close, reopening the keyboard. Deterministically
            // move focus onto the visible, non-editable "pick a date" row instead.
            keyboard?.hide()
            runCatching { dateRowFocus.requestFocus() }
        }
    }

    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = "Add reading goal",
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(onClick = {
                val t = target.toIntOrNull() ?: 0
                val iso = dueMillis?.let { isoFromMillis(it) }
                when {
                    t <= 0 -> err = "Enter the ${unitLabel.lowercase()} to reach."
                    iso == null -> err = "Pick a date."
                    else -> onAdd(t, iso)
                }
            }) { Text("Add") }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledField("Reach $unitLabel", target, { s -> target = s.filter { it.isDigit() }.take(6) }, "e.g. 100", KeyboardType.Number)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .focusRequester(dateRowFocus).focusable()
                    .clickable { focusManager.clearFocus(); showDate = true }.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(KairosIcons.Calendar, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(dueMillis?.let { prettyDate(isoFromMillis(it)) } ?: "Pick a date", style = MaterialTheme.typography.bodyMedium)
            }
            err?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dueMillis)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dueMillis = state.selectedDateMillis
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state, focusRequester = null) }
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
                .height(8.dp).clip(RoundedCornerShape(999.dp)).background(KairosThemeState.accent),
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
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = KeyboardCapitalization.Sentences),
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
