package com.kairos.app.ui.workout

/**
 * The conditioning timer's rules (the web's src/lib/workouts/timer.ts; keep
 * them in step): how each format runs, what the clock shows, and when it
 * beeps. Times are whole seconds since the start, pauses left out.
 */
internal data class TimerSegment(val label: String, val sec: Int, val work: Boolean)

internal data class TimerPlan(
    /** COUNTDOWN, STOPWATCH or INTERVALS. */
    val kind: String,
    /** Total length; null for an open stopwatch. */
    val totalSec: Int?,
    val segments: List<TimerSegment>,
    /** ROUNDS, TIME, REPS or MINUTES: what the result box asks for. */
    val result: String,
    /** Shows the "+1 round" button. */
    val rounds: Boolean,
    val title: String,
)

internal data class TimerAt(val clock: Int, val done: Boolean, val index: Int, val label: String, val work: Boolean)

internal val TIMER_LEADS = listOf(0, 5, 10, 15)
private const val DEFAULT_AMRAP_SEC = 12 * 60
private const val DEFAULT_EMOM_SEC = 10 * 60
private const val DEFAULT_STATION_SEC = 60
private const val TABATA_WORK = 20
private const val TABATA_REST = 10
private const val TABATA_ROUNDS = 8

private fun mins(sec: Int) = "${Math.round(sec / 60.0)} min"

internal fun timerPlanFor(type: String, capSec: Int?, movements: List<String>): TimerPlan {
    val moves = movements.ifEmpty { listOf("") }
    fun label(vararg parts: String) = parts.filter { it.isNotBlank() }.joinToString(" · ")
    return when (type) {
        "AMRAP" -> {
            val total = if (capSec != null && capSec > 0) capSec else DEFAULT_AMRAP_SEC
            TimerPlan(
                kind = "COUNTDOWN",
                totalSec = total,
                segments = listOf(TimerSegment(label = "", sec = total, work = true)),
                result = "ROUNDS",
                rounds = true,
                title = "AMRAP · ${mins(total)}",
            )
        }
        "EMOM" -> {
            val total = if (capSec != null && capSec >= 60) Math.round(capSec / 60.0).toInt() * 60 else DEFAULT_EMOM_SEC
            val n = total / 60
            TimerPlan(
                kind = "INTERVALS",
                totalSec = total,
                segments = (0 until n).map { i ->
                    TimerSegment(label = label("Minute ${i + 1} of $n", moves[i % moves.size]), sec = 60, work = true)
                },
                result = "MINUTES",
                rounds = false,
                title = "EMOM · ${mins(total)}",
            )
        }
        "TABATA" -> {
            val segs = buildList {
                moves.forEach { m ->
                    for (r in 1..TABATA_ROUNDS) {
                        add(TimerSegment(label = label(m, "round $r of $TABATA_ROUNDS"), sec = TABATA_WORK, work = true))
                        add(TimerSegment(label = "Rest", sec = TABATA_REST, work = false))
                    }
                }
            }
            val total = segs.sumOf { it.sec }
            TimerPlan(kind = "INTERVALS", totalSec = total, segments = segs, result = "REPS", rounds = false, title = "Tabata · ${mins(total)}")
        }
        "TIMED_STATIONS" -> {
            val each = if (capSec != null && capSec > 0) capSec else DEFAULT_STATION_SEC
            TimerPlan(
                kind = "INTERVALS",
                totalSec = each * moves.size,
                segments = moves.mapIndexed { i, m ->
                    TimerSegment(label = label("Station ${i + 1} of ${moves.size}", m), sec = each, work = true)
                },
                result = "REPS",
                rounds = false,
                title = "Timed stations · $each s each",
            )
        }
        "FOR_REPS", "MAX_SETS" -> TimerPlan(
            kind = "STOPWATCH",
            totalSec = null,
            segments = emptyList(),
            result = "REPS",
            rounds = false,
            title = if (type == "FOR_REPS") "For reps" else "Max sets",
        )
        else -> TimerPlan(
            kind = "STOPWATCH",
            totalSec = null,
            segments = emptyList(),
            result = "TIME",
            rounds = false,
            title = when (type) {
                "STATIONS" -> "Stations"
                "PYRAMID" -> "Pyramid"
                else -> "For time"
            },
        )
    }
}

internal fun timerAt(plan: TimerPlan, elapsed: Int): TimerAt {
    val e = maxOf(0, elapsed)
    val total = plan.totalSec
    val done = total != null && e >= total
    if (plan.kind == "STOPWATCH") {
        return TimerAt(clock = if (total != null) minOf(e, total) else e, done = done, index = 0, label = "", work = true)
    }
    if (plan.kind == "COUNTDOWN") {
        return TimerAt(clock = maxOf(0, (total ?: 0) - e), done = done, index = 0, label = plan.segments.firstOrNull()?.label ?: "", work = true)
    }
    var start = 0
    plan.segments.forEachIndexed { i, s ->
        if (e < start + s.sec) return TimerAt(clock = start + s.sec - e, done = false, index = i, label = s.label, work = s.work)
        start += s.sec
    }
    val last = plan.segments.lastOrNull()
    return TimerAt(clock = 0, done = true, index = plan.segments.size - 1, label = last?.label ?: "", work = last?.work ?: true)
}

/** TICK, CHANGE, HALF or END for the second just reached (timer.ts cuesAt). */
internal fun cuesAt(plan: TimerPlan, second: Int): List<String> {
    if (second <= 0) return emptyList()
    val total = plan.totalSec
    if (total != null && second == total) return listOf("END")
    if (total != null && second > total) return emptyList()
    if (plan.kind == "STOPWATCH") return emptyList()
    if (plan.kind == "COUNTDOWN") {
        val t = total ?: 0
        val out = mutableListOf<String>()
        val left = t - second
        if (left in 1..3) out.add("TICK")
        if (t >= 120 && t % 2 == 0 && second == t / 2) out.add("HALF")
        return out
    }
    var start = 0
    for (s in plan.segments) {
        val end = start + s.sec
        if (second == start && start > 0) return listOf("CHANGE")
        if (second in (start + 1) until end) {
            return if (end - second in 1..3) listOf("TICK") else emptyList()
        }
        start = end
    }
    return emptyList()
}

/** "12:00", "0:07", "1:02:05". */
internal fun clockText(sec: Int): String {
    val t = maxOf(0, sec)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** The result when the timer ends; null when it has to be typed. */
internal fun resultFromTimer(plan: TimerPlan, elapsed: Int, rounds: Int): Int? = when (plan.result) {
    "ROUNDS" -> rounds.takeIf { it > 0 }
    "TIME" -> elapsed.takeIf { it > 0 }
    "MINUTES" -> minOf(plan.segments.size, elapsed / 60).takeIf { it > 0 }
    else -> null
}
