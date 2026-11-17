package com.budget.app.data

import com.budget.app.data.remote.CardDto
import com.budget.app.data.remote.CardsResponse
import com.budget.app.data.remote.PaceDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** When card payment reminders come. Each also reminds on the due date itself. Set per phone. */
enum class CardReminderLead(val key: String, val label: String, val days: Int) {
    OFF("off", "Off", -1),
    DAY_OF("0", "On the due date", 0),
    ONE_DAY("1", "1 day before, and on the day", 1),
    THREE_DAYS("3", "3 days before, and on the day", 3),
    FIVE_DAYS("5", "5 days before, and on the day", 5);

    /** Short form for a settings row. */
    val short: String get() = when (this) {
        OFF -> "Off"
        DAY_OF -> "On the day"
        else -> "$days days before".replace("1 days", "1 day")
    }

    companion object {
        fun from(key: String?): CardReminderLead = entries.firstOrNull { it.key == key } ?: OFF
    }
}

/** Which card payments to remind about, and what the notification says. Pure, so it is unit-tested. */
object CardReminderLogic {
    /** Cards with something left to pay this cycle, due [lead] days from [today] or today. */
    fun dueOn(cards: CardsResponse, today: LocalDate, lead: CardReminderLead): List<CardDto> {
        if (lead == CardReminderLead.OFF) return emptyList()
        val ahead = today.plusDays(lead.days.toLong()).toString()
        val now = today.toString()
        return cards.cards
            .filter { c -> c.status?.let { it.suggestedCents > 0 && (it.nextDue == ahead || it.nextDue == now) } == true }
            .sortedBy { it.status!!.nextDue }
    }

    fun message(due: List<CardDto>, today: LocalDate): ReminderMessage? {
        if (due.isEmpty()) return null
        fun whenText(iso: String): String {
            val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return "soon"
            return when (val days = ChronoUnit.DAYS.between(today, date)) {
                0L -> "today"
                1L -> "tomorrow"
                in 2L..6L -> date.format(DateTimeFormatter.ofPattern("EEEE", Locale.US))
                else -> "in $days days"
            }
        }
        val lines = due.map { "${CardLogic.faceName(it.name)} · ${Money.format(it.status!!.suggestedCents)} · ${whenText(it.status.nextDue)}" }
        return if (due.size == 1) {
            val c = due.first()
            val s = c.status!!
            val what = if (s.plan == "payoff") "this month's payoff payment" else "left on the statement"
            ReminderMessage(
                "${CardLogic.faceName(c.name)}: ${Money.format(s.suggestedCents)} due ${whenText(s.nextDue)}",
                "${Money.format(s.suggestedCents)} $what, due ${Dates.short(s.nextDue) ?: s.nextDue}.",
                lines,
            )
        } else {
            ReminderMessage(
                "${due.size} card payments coming due: ${Money.format(due.sumOf { it.status!!.suggestedCents })}",
                lines.joinToString(", "),
                lines,
            )
        }
    }
}

/** Plain words for the month's pace. Pure, so it is unit-tested. */
object PaceLogic {
    /** "About the same as a usual month", or so much more or less. */
    fun verdict(p: PaceDto): String {
        val diff = p.projectedCents - p.usualMonthCents
        val close = kotlin.math.abs(diff) < maxOf(2_500L, p.usualMonthCents * 3 / 100)
        return when {
            p.usualMonthCents <= 0L -> "No usual month to compare with yet"
            close -> "About the same as a usual month"
            diff > 0 -> "About ${Money.whole(diff)} more than a usual month"
            else -> "About ${Money.whole(-diff)} less than a usual month"
        }
    }

    /** Fractions of the bar: spent so far, projected, and where a usual month is by now. */
    fun bar(p: PaceDto): Triple<Float, Float, Float> {
        val scale = maxOf(p.projectedCents, p.usualMonthCents, 1L).toFloat()
        fun f(c: Long) = (c / scale).coerceIn(0f, 1f)
        return Triple(f(p.soFarCents), f(p.projectedCents), f(p.usualByNowCents))
    }
}
