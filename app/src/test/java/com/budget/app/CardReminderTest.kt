package com.budget.app

import com.budget.app.data.CardReminderLead
import com.budget.app.data.CardReminderLogic
import com.budget.app.data.Money
import com.budget.app.data.PaceLogic
import com.budget.app.data.remote.CardDto
import com.budget.app.data.remote.CardStatusDto
import com.budget.app.data.remote.CardsResponse
import com.budget.app.data.remote.PaceDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Card names and amounts are invented.
class CardReminderTest {
    private val today = LocalDate.parse("2026-12-05")
    private fun card(name: String, due: String, cents: Long, plan: String = "statement") =
        CardDto(id = name, name = name, status = CardStatusDto(nextDue = due, suggestedCents = cents, plan = plan))
    private val cards = CardsResponse(
        cards = listOf(
            card("Shopping Card - 5521", "2026-12-08", 38_600),
            card("Travel Card", "2026-12-05", 82_200),
            card("Paid Card", "2026-12-08", 0),
            card("Later Card", "2026-12-20", 10_000),
        ),
    )

    @Test fun picksTheLeadDayAndToday() {
        val due = CardReminderLogic.dueOn(cards, today, CardReminderLead.THREE_DAYS)
        assertEquals(listOf("Travel Card", "Shopping Card - 5521"), due.map { it.name })
    }

    @Test fun offRemindsNothing() {
        assertTrue(CardReminderLogic.dueOn(cards, today, CardReminderLead.OFF).isEmpty())
        assertNull(CardReminderLogic.message(emptyList(), today))
    }

    @Test fun oneCardReadsPlainly() {
        val due = CardReminderLogic.dueOn(cards, today, CardReminderLead.DAY_OF)
        val m = CardReminderLogic.message(due, today)!!
        assertEquals("Travel Card: \$822.00 due today", m.title)
    }

    @Test fun severalCardsAreTotaled() {
        val m = CardReminderLogic.message(CardReminderLogic.dueOn(cards, today, CardReminderLead.THREE_DAYS), today)!!
        assertEquals("2 card payments coming due: \$1,208.00", m.title)
        assertEquals("Shopping Card · \$386.00 · Tuesday", m.lines[1])
    }

    @Test fun paceVerdicts() {
        val p = PaceDto(day = 16, daysInMonth = 30, soFarCents = 124_000, usualByNowCents = 115_000, usualMonthCents = 206_000, projectedCents = 215_000)
        assertEquals("About \$90 more than a usual month", PaceLogic.verdict(p))
        assertEquals("About the same as a usual month", PaceLogic.verdict(p.copy(projectedCents = 207_000)))
        val (spent, projected, usual) = PaceLogic.bar(p)
        assertEquals(124_000f / 215_000f, spent, 0.001f)
        assertEquals(1f, projected, 0.001f)
        assertEquals(115_000f / 215_000f, usual, 0.001f)
        assertEquals("\$1,235", Money.whole(123_456))
    }
}
