package com.kairos.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.BrowseWorkoutDto
import com.kairos.app.data.remote.dto.SharePersonDto
import com.kairos.app.data.session.SessionRepository
import com.kairos.app.data.local.PayloadCacheStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowseUiState(
    val loading: Boolean = true,
    val items: List<BrowseWorkoutDto> = emptyList(),
    val people: List<SharePersonDto> = emptyList(),
    val error: String? = null,
    val shared: Boolean = false,
)

class BrowseWorkoutsViewModel(
    private val session: SessionRepository,
    private val cache: PayloadCacheStore,
) : ViewModel() {
    private val _ui = MutableStateFlow(BrowseUiState())
    val ui: StateFlow<BrowseUiState> = _ui.asStateFlow()

    init { reload(first = true) }

    private fun reload(first: Boolean = false) {
        viewModelScope.launch {
            val pid = session.currentPersonId() ?: PayloadCacheStore.HOUSEHOLD
            // Cold-start seed of the browse catalog so previously-loaded workouts
            // stay viewable offline; share/delete remain online-only.
            if (first && _ui.value.items.isEmpty()) {
                val ci = runCatching { cache.readAs("workout-browse", "main", pid, ListSerializer(BrowseWorkoutDto.serializer())) }.getOrNull()
                val cp = runCatching { cache.readAs("workout-browse-people", "main", pid, ListSerializer(SharePersonDto.serializer())) }.getOrNull()
                if (ci != null) _ui.update { it.copy(loading = false, items = ci, people = cp ?: it.people) }
            }
            try {
                val items = session.loadBrowse()
                val people = if (first) runCatching { session.loadWorkoutBuilder().people }.getOrDefault(emptyList()) else _ui.value.people
                _ui.update { it.copy(loading = false, items = items, people = people) }
                launch { runCatching { cache.writeAs("workout-browse", "main", pid, ListSerializer(BrowseWorkoutDto.serializer()), items) } }
                if (first) launch { runCatching { cache.writeAs("workout-browse-people", "main", pid, ListSerializer(SharePersonDto.serializer()), people) } }
            } catch (e: ApiException) {
                _ui.update {
                    if (it.items.isEmpty()) it.copy(loading = false, error = e.error.message)
                    else it.copy(loading = false)
                }
            }
        }
    }

    fun share(workoutId: String, targetUserIds: List<String>, onDone: () -> Unit) {
        if (targetUserIds.isEmpty()) { onDone(); return }
        viewModelScope.launch {
            var any = false
            for (t in targetUserIds) {
                runCatching { session.shareWorkout(workoutId, t) }.onSuccess { any = true }
            }
            if (any) _ui.update { it.copy(shared = true) }
            onDone()
        }
    }

    fun clearShared() = _ui.update { it.copy(shared = false) }

    fun delete(workoutId: String) {
        viewModelScope.launch {
            runCatching { session.deletePersonalWorkout(workoutId) }
            reload()
        }
    }
}
