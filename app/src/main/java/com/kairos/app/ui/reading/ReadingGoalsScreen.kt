package com.kairos.app.ui.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairos.app.data.remote.dto.ReadingGoalItemDto
import com.kairos.app.data.session.SessionRepository
import com.kairos.app.data.local.PayloadCacheStore
import kotlinx.serialization.builtins.ListSerializer
import com.kairos.app.ui.common.AnimatedDialog
import com.kairos.app.ui.common.rememberContainer
import com.kairos.app.ui.nav.KairosIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ReadingGoalsUiState(
    val loading: Boolean = true,
    val items: List<ReadingGoalItemDto> = emptyList(),
    val error: String? = null,
    val busy: Boolean = false,
)

class ReadingGoalsViewModel(
    private val session: SessionRepository,
    private val cache: PayloadCacheStore,
) : ViewModel() {
    private val _ui = MutableStateFlow(ReadingGoalsUiState())
    val ui: StateFlow<ReadingGoalsUiState> = _ui.asStateFlow()

    init { load() }

    private fun pid(): String = session.currentPersonId() ?: PayloadCacheStore.HOUSEHOLD

    fun load() {
        _ui.update { it.copy(loading = it.items.isEmpty(), error = null) }
        viewModelScope.launch {
            val pid = pid()
            // Cold-start seed of the goals list so it shows offline (read screen,
            // like Browse); the progress-edit action stays online-only.
            if (_ui.value.items.isEmpty()) {
                runCatching { cache.readAs("reading-goals", "main", pid, ListSerializer(ReadingGoalItemDto.serializer())) }
                    .getOrNull()?.let { seed -> _ui.update { if (it.items.isEmpty()) it.copy(items = seed, loading = false) else it } }
            }
            try {
                val data = session.loadReadingGoals()
                _ui.update { it.copy(loading = false, items = data.items) }
                launch { runCatching { cache.writeAs("reading-goals", "main", pid, ListSerializer(ReadingGoalItemDto.serializer()), data.items) } }
            } catch (e: Exception) {
                _ui.update {
                    if (it.items.isEmpty()) it.copy(loading = false, error = e.message ?: "Couldn't load your reading goals.")
                    else it.copy(loading = false)
                }
            }
        }
    }

    fun saveProgress(bookId: String, page: Int, onDone: () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                session.logBook(bookId, page)
                val data = session.loadReadingGoals()
                _ui.update { it.copy(busy = false, items = data.items) }
                launch { runCatching { cache.writeAs("reading-goals", "main", pid(), ListSerializer(ReadingGoalItemDto.serializer()), data.items) } }
                onDone()
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, error = e.message ?: "Couldn't save that.") }
            }
        }
    }
}

private fun fmtGoalDate(iso: String): String =
    try {
        LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
    } catch (e: Exception) {
        iso
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingGoalsScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val vm: ReadingGoalsViewModel = viewModel(
        factory = viewModelFactory { initializer { ReadingGoalsViewModel(container.sessionRepository, container.payloadCache) } },
    )
    val ui by vm.ui.collectAsState()
    var editing by remember { mutableStateOf<ReadingGoalItemDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading goals") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(KairosIcons.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {
            when {
                ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                ui.error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(ui.error ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                ui.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No reading goals right now. Add some when you add or edit a book.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ui.items.forEach { item ->
                        GoalItemCard(item) { editing = item }
                    }
                }
            }
        }
    }

    editing?.let { item ->
        ProgressDialog(
            item = item,
            busy = ui.busy,
            onSave = { page -> vm.saveProgress(item.bookId, page) { editing = null } },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun GoalItemCard(item: ReadingGoalItemDto, onClick: () -> Unit) {
    val unit = if (item.unit == "CHAPTERS") "Chapter" else "Page"
    val toGo = (item.target - item.position).coerceAtLeast(0)
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.bookTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (item.upcoming) {
                    Text(
                        "Coming up",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "Reach ${unit.lowercase()} ${item.target} by ${fmtGoalDate(item.dueDate)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "You're on $unit ${item.position} of ${item.length} \u2014 $toGo to go. Tap to update.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProgressDialog(
    item: ReadingGoalItemDto,
    busy: Boolean,
    onSave: (page: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val unit = if (item.unit == "CHAPTERS") "chapter" else "page"
    var page by remember { mutableStateOf(if (item.position > 0) item.position.toString() else "") }

    AnimatedDialog(
        onDismissRequest = onDismiss,
        title = "Update progress",
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { onSave(page.toIntOrNull() ?: 0) }) {
                Text(if (busy) "Saving\u2026" else "Save")
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(item.bookTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "What $unit are you on now?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = page,
                onValueChange = { s -> page = s.filter { it.isDigit() }.take(6) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if ((page.toIntOrNull() ?: 0) >= item.target) {
                Text(
                    "That reaches this goal \u2014 nice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF047857),
                )
            }
        }
    }
}
