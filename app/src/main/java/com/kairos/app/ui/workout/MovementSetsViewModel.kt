package com.kairos.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairos.app.data.remote.ApiClient
import com.kairos.app.data.remote.ApiException
import com.kairos.app.data.remote.dto.MovementLogDto
import com.kairos.app.data.remote.dto.MovementSetDto
import com.kairos.app.data.remote.dto.FeedbackRequest
import com.kairos.app.data.remote.dto.PoolExerciseDto
import com.kairos.app.data.remote.dto.RepRecordDto
import com.kairos.app.data.remote.dto.SaveSetsRequest
import com.kairos.app.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** One set row. Typed text lives in w/r/s; "" shows the greyed hint. */
data class SetRow(
    val key: Int,
    val w: String = "",
    val r: String = "",
    val s: String = "",
    val hintW: Double? = null,
    val hintR: Int? = null,
    val hintS: Int? = null,
    /** Ticked and sent to the server (or queued, offline). */
    val saved: Boolean = false,
    /** Added today: no "last time" of its own. */
    val added: Boolean = false,
    /** The program's top set (work up to a top set). */
    val top: Boolean = false,
)

data class MovementSetsUi(
    val loading: Boolean = true,
    val loadError: String? = null,
    val rows: List<SetRow> = emptyList(),
    val lastSets: List<MovementSetDto> = emptyList(),
    val lastDate: String? = null,
    /** Shift applied to the hints of rows not entered yet. */
    val shift: Double = 0.0,
    /** A swap chosen (or already logged) for the day. */
    val doingId: String? = null,
    val doingName: String? = null,
    val skipped: Boolean = false,
    /** The saved row a remove is waiting on (asks first). */
    val confirmKey: Int? = null,
    val status: String = "",
    val leaving: Boolean = false,
    val done: Boolean = false,
    val pool: List<PoolExerciseDto> = emptyList(),
    /** Best weight at each rep count before today, for the movement being done. */
    val records: List<RepRecordDto> = emptyList(),
    /** How it felt (EASY / SOLID / HARD / TOO_HARD) and where it hurt. */
    val effort: String? = null,
    val hurt: String? = null,
    /** "Work up to a top set" and "Strength waves · Week 2 of 4", when the
     *  planned movement has a set scheme. */
    val targetScheme: String = "",
    val targetTitle: String = "",
)

/**
 * Every set of one planned movement for a day — the phone side of the web's
 * set logger, with the same rules: last time's sets are hints, never values;
 * ✓ saves straight away by sending the movement's saved sets (the whole list
 * replaces what the movement had, so a write queued offline is safe to
 * replay); removing a saved set asks first; Log and Back both keep anything
 * typed but not ticked.
 */
class MovementSetsViewModel(
    private val session: SessionRepository,
    private val date: String,
    private val plannedWorkoutId: String,
    private val slotId: String,
    private val metric: String,
    private val unit: String,
    private val late: Boolean,
) : ViewModel() {

    private val _ui = MutableStateFlow(MovementSetsUi())
    val ui: StateFlow<MovementSetsUi> = _ui.asStateFlow()
    private var nextKey = 100

    val step: Double = if (unit == "kg") 2.5 else 5.0

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val log = try {
                session.loadMovementSets(date = date, slot = slotId)
            } catch (e: ApiException) {
                // Offline with nothing cached: start from an empty row; anything
                // queued for this movement still shows below.
                MovementLogDto()
            }
            val queued = pendingSets()
            val today = queued ?: log.sets
            val last = log.last?.sets ?: emptyList()
            val fb = pendingFeedback() ?: log.feedback?.let { FeedbackRequest(date = date, slot = slotId, effort = it.effort, hurt = it.hurt) }
            _ui.update {
                it.copy(
                    loading = false,
                    rows = rowsFrom(today, last, log.target?.sets ?: emptyList()),
                    targetScheme = log.target?.scheme ?: "",
                    targetTitle = log.target?.title ?: "",
                    lastSets = last,
                    lastDate = log.last?.date,
                    skipped = log.skipped,
                    doingId = log.loggedAs?.poolExerciseId,
                    doingName = log.loggedAs?.name,
                    records = log.records,
                    effort = fb?.effort,
                    hurt = fb?.hurt,
                )
            }
        }
    }

    /** The newest queued save for this movement, if one is still waiting to
     *  reach the server: it's what was last entered here. */
    private suspend fun pendingSets(): List<MovementSetDto>? {
        val writes = runCatching { session.pendingWrites() }.getOrDefault(emptyList())
        return writes
            .filter { it.url.substringAfter("/api/v1/", "").substringBefore("?") == "workouts/sets" }
            .mapNotNull { w ->
                w.body?.let { runCatching { ApiClient.json.decodeFromString(SaveSetsRequest.serializer(), it) }.getOrNull() }
            }
            .lastOrNull { it.date == date && it.slotId == slotId && it.plannedWorkoutId == plannedWorkoutId }
            ?.sets
    }

    /** A feedback answer still queued offline for this movement and day. */
    private suspend fun pendingFeedback(): FeedbackRequest? {
        val writes = runCatching { session.pendingWrites() }.getOrDefault(emptyList())
        return writes
            .filter { it.url.substringAfter("/api/v1/", "").substringBefore("?") == "workouts/feedback" }
            .mapNotNull { w ->
                w.body?.let { runCatching { ApiClient.json.decodeFromString(FeedbackRequest.serializer(), it) }.getOrNull() }
            }
            .lastOrNull { it.date == date && it.slot == slotId }
    }

    // --- records and how it felt ----------------------------------------------

    /** The few records shown at the top: the usual rep counts first, up to four
     *  (records.ts recordChips). */
    fun recordChips(): List<RepRecordDto> {
        val usual = listOf(1, 2, 3, 5, 8, 10)
        val recs = _ui.value.records
        return (recs.filter { it.reps in usual } + recs.filter { it.reps !in usual })
            .take(4)
            .sortedBy { it.reps }
    }

    /** Today's heaviest at each exact rep count, from what's saved or typed. */
    fun todayAt(shift: Double = _ui.value.shift): Map<Int, Double> {
        val out = mutableMapOf<Int, Double>()
        _ui.value.rows.forEach { r ->
            if (!r.saved && !typed(r)) return@forEach
            val v = valueOf(r, shift)
            val w = v.weight ?: return@forEach
            val reps = v.reps ?: return@forEach
            out[reps] = maxOf(out[reps] ?: 0.0, w)
        }
        return out
    }

    /** Nothing earlier as heavy for as many reps or more (records.ts isNewBest).
     *  No history at all: nothing is a "best". */
    fun isNewBest(r: SetRow): Boolean {
        val recs = _ui.value.records
        if (recs.isEmpty() || metric != "WEIGHT") return false
        val v = valueOf(r)
        val w = v.weight ?: return false
        val reps = v.reps ?: return false
        if (w <= 0 || reps <= 0) return false
        val ceiling = recs.filter { it.reps >= reps }.maxOfOrNull { it.weight } ?: 0.0
        return w > ceiling
    }

    /** How it felt / where it hurt: the whole answer each time. */
    fun setFeedback(effort: String?, hurt: String?) {
        _ui.update { it.copy(effort = effort, hurt = hurt) }
        viewModelScope.launch {
            runCatching {
                session.saveFeedback(FeedbackRequest(date = date, slot = slotId, effort = effort, hurt = hurt))
            }
        }
    }

    /** Today's sets, else the targets, else last time's: the targets fill the
     *  hints in place of last time, and a target with no weight yet (no
     *  training max) takes last time's weight. */
    private fun rowsFrom(
        today: List<MovementSetDto>,
        last: List<MovementSetDto>,
        target: List<com.kairos.app.data.remote.dto.TargetSetDto> = emptyList(),
    ): List<SetRow> {
        var k = 0
        if (today.isNotEmpty()) {
            return today.mapIndexed { i, s ->
                val t = target.getOrNull(i)
                SetRow(
                    key = k++,
                    w = s.weight?.let { fmt(it) } ?: "",
                    r = s.reps?.toString() ?: "",
                    s = s.seconds?.toString() ?: "",
                    hintW = t?.weight ?: last.getOrNull(i)?.weight,
                    hintR = t?.reps ?: last.getOrNull(i)?.reps,
                    hintS = last.getOrNull(i)?.seconds,
                    saved = true,
                    added = i >= maxOf(last.size, target.size),
                    top = t?.top ?: false,
                )
            }
        }
        if (target.isNotEmpty()) {
            return target.mapIndexed { i, t ->
                SetRow(
                    key = k++,
                    hintW = t.weight ?: last.getOrNull(i)?.weight,
                    hintR = t.reps,
                    hintS = last.getOrNull(i)?.seconds,
                    top = t.top,
                )
            }
        }
        if (last.isNotEmpty()) {
            return last.map { s -> SetRow(key = k++, hintW = s.weight, hintR = s.reps, hintS = s.seconds) }
        }
        return listOf(SetRow(key = 0, added = true))
    }

    // --- reading a row -------------------------------------------------------

    fun hintW(r: SetRow, shift: Double = _ui.value.shift): Double? =
        if (r.hintW != null && r.w.isBlank() && !r.saved) r.hintW + shift else r.hintW

    private fun num(v: String): Double? = v.trim().toDoubleOrNull()?.takeIf { it > 0 }

    fun valueOf(r: SetRow, shift: Double = _ui.value.shift): MovementSetDto = MovementSetDto(
        weight = if (metric == "WEIGHT") num(r.w) ?: hintW(r, shift) else null,
        reps = if (metric == "WEIGHT" || metric == "REPS") num(r.r)?.toInt() ?: r.hintR else null,
        seconds = if (metric == "DURATION") num(r.s)?.toInt() ?: r.hintS else null,
    )

    private fun complete(r: SetRow): Boolean {
        val v = valueOf(r)
        return when (metric) {
            "WEIGHT" -> v.weight != null
            "REPS" -> v.reps != null
            else -> v.seconds != null
        }
    }

    private fun typed(r: SetRow) = r.w.isNotBlank() || r.r.isNotBlank() || r.s.isNotBlank()

    fun anyEntered(): Boolean = _ui.value.rows.any { it.saved || typed(it) }
    fun canLog(): Boolean = _ui.value.rows.any { it.saved || (typed(it) && complete(it)) }
    fun hasHints(): Boolean = _ui.value.rows.any { it.hintW != null || it.hintR != null || it.hintS != null }

    // --- editing ---------------------------------------------------------------

    private fun clean(v: String, decimal: Boolean): String =
        if (decimal) v.filter { it.isDigit() || it == '.' }.take(6) else v.filter { it.isDigit() }.take(4)

    fun onWeight(key: Int, v: String) = edit(key) { it.copy(w = clean(v, true), saved = false) }
    fun onReps(key: Int, v: String) = edit(key) { it.copy(r = clean(v, false), saved = false) }
    fun onSeconds(key: Int, v: String) = edit(key) { it.copy(s = clean(v, false), saved = false) }

    private fun edit(key: Int, f: (SetRow) -> SetRow) =
        _ui.update { u -> u.copy(rows = u.rows.map { if (it.key == key) f(it) else it }) }

    /** ✓: save this set (filling it from the hints), or un-tick it. */
    fun tick(key: Int) {
        val rows = _ui.value.rows
        val r = rows.firstOrNull { it.key == key } ?: return
        val next = if (r.saved) {
            rows.map { if (it.key == key) it.copy(saved = false) else it }
        } else {
            if (!complete(r)) return
            val v = valueOf(r)
            rows.map {
                if (it.key == key) {
                    it.copy(
                        saved = true,
                        w = v.weight?.let { w -> fmt(w) } ?: it.w,
                        r = v.reps?.toString() ?: it.r,
                        s = v.seconds?.toString() ?: it.s,
                    )
                } else it
            }
        }
        _ui.update { it.copy(rows = next) }
        persist(next)
    }

    /** ×: a saved set asks first; anything else just goes. */
    fun askRemove(key: Int) {
        val r = _ui.value.rows.firstOrNull { it.key == key } ?: return
        if (r.saved) _ui.update { it.copy(confirmKey = key) } else remove(key)
    }

    fun keep() = _ui.update { it.copy(confirmKey = null) }

    fun remove(key: Int) {
        val r = _ui.value.rows.firstOrNull { it.key == key } ?: return
        val next = _ui.value.rows.filter { it.key != key }
        _ui.update { it.copy(rows = next, confirmKey = null) }
        if (r.saved) persist(next)
    }

    /** Add a set at the end, its hint copied from the set above. */
    fun addSet() {
        val u = _ui.value
        val prev = u.rows.lastOrNull()
        val v = prev?.let { valueOf(it) }
        _ui.update {
            it.copy(
                rows = it.rows + SetRow(
                    key = nextKey++,
                    hintW = v?.weight?.let { w -> w - u.shift },
                    hintR = v?.reps,
                    hintS = v?.seconds,
                    added = true,
                ),
            )
        }
    }

    fun sameAsLast() {
        val next = _ui.value.rows.map { r ->
            if (!complete(r)) r
            else {
                val v = valueOf(r)
                r.copy(
                    saved = true,
                    w = v.weight?.let { fmt(it) } ?: "",
                    r = v.reps?.toString() ?: "",
                    s = v.seconds?.toString() ?: "",
                )
            }
        }
        _ui.update { it.copy(rows = next) }
        persist(next)
    }

    fun shiftBy(d: Double) = _ui.update { it.copy(shift = it.shift + d) }

    /** The calculator's weight, into the next set not ticked yet. */
    fun useWeight(w: String) {
        val target = _ui.value.rows.firstOrNull { !it.saved } ?: return
        edit(target.key) { it.copy(w = clean(w, true), saved = false) }
    }

    fun nextUnsavedIndex(): Int = _ui.value.rows.indexOfFirst { !it.saved }.let { if (it < 0) _ui.value.rows.size else it } + 1

    // --- server ----------------------------------------------------------------

    private fun request(rows: List<SetRow>) = SaveSetsRequest(
        date = date,
        plannedWorkoutId = plannedWorkoutId,
        slotId = slotId,
        loggedId = _ui.value.doingId ?: slotId,
        metric = metric,
        unit = unit,
        sets = rows.filter { it.saved }.map { valueOf(it) },
        completedOn = if (late) LocalDate.now().toString() else null,
    )

    private val sending = Mutex()

    /** Saves are sent one at a time, each with the newest rows, so two quick
     *  ticks can never land out of order and leave the older list behind. */
    @Suppress("UNUSED_PARAMETER")
    private fun persist(rows: List<SetRow>, then: (() -> Unit)? = null) {
        _ui.update { it.copy(status = "Saving…") }
        viewModelScope.launch {
            val ok = try {
                sending.withLock { session.saveMovementSets(request(_ui.value.rows)) }
                true
            } catch (e: ApiException) {
                false
            }
            _ui.update { it.copy(status = if (ok) "Saved" else "Not saved. Try again.") }
            then?.invoke()
        }
    }

    /** Log or Back: keep anything typed but not ticked, then leave. */
    fun leave() {
        if (_ui.value.leaving) return
        val cur = _ui.value.rows
        val next = cur.map { if (!it.saved && typed(it) && complete(it)) it.copy(saved = true) else it }
        val changed = next.zip(cur).any { (a, b) -> a.saved != b.saved }
        _ui.update { it.copy(rows = next, leaving = true) }
        if (changed) persist(next) { _ui.update { it.copy(done = true) } }
        else _ui.update { it.copy(done = true) }
    }

    fun toggleSkip() {
        val want = !_ui.value.skipped
        _ui.update { it.copy(skipped = want) }
        viewModelScope.launch {
            try {
                session.workoutSkip(date, plannedWorkoutId, slotId, want)
            } catch (e: ApiException) {
                _ui.update { it.copy(skipped = !want) }
            }
        }
    }

    fun loadPool() {
        if (_ui.value.pool.isNotEmpty()) return
        viewModelScope.launch {
            val items = runCatching { session.loadWorkoutPool().exercises }.getOrDefault(emptyList())
            _ui.update { it.copy(pool = items) }
        }
    }

    /** Swap for the day (or undo with null): hints follow the movement being done. */
    fun swapTo(p: PoolExerciseDto?) {
        _ui.update { it.copy(doingId = p?.id, doingName = p?.name) }
        // Sets already saved move with the swap.
        if (_ui.value.rows.any { it.saved }) persist(_ui.value.rows)
        viewModelScope.launch {
            val log = runCatching { session.loadMovementSets(date = date, slot = slotId, doing = p?.id ?: slotId) }.getOrNull()
                ?: return@launch
            val last = log.last?.sets ?: emptyList()
            _ui.update { u ->
                u.copy(
                    lastSets = last,
                    lastDate = log.last?.date,
                    records = log.records,
                    rows = u.rows.mapIndexed { i, r ->
                        if (r.saved || typed(r)) r
                        else r.copy(hintW = last.getOrNull(i)?.weight, hintR = last.getOrNull(i)?.reps, hintS = last.getOrNull(i)?.seconds)
                    },
                )
            }
        }
    }

    companion object {
        fun fmt(d: Double): String =
            if (d % 1.0 == 0.0) d.toLong().toString() else ((d * 10).toLong() / 10.0).toString()
        /** "185 × 5", "12 reps", "45 s" — the web's lastText. */
        fun fmtSet(s: MovementSetDto, metric: String): String = when (metric) {
            "WEIGHT" -> (s.weight?.let { fmt(it) } ?: "\u2014") + (s.reps?.let { " \u00d7 $it" } ?: "")
            "DURATION" -> s.seconds?.let { "$it s" } ?: "\u2014"
            else -> s.reps?.let { "$it reps" } ?: "\u2014"
        }
    }
}
