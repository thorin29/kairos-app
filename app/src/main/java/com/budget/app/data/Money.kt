package com.budget.app.data

import java.util.Locale
import kotlin.math.abs

/**
 * Money is whole cents (Long) everywhere, as on the server. Formatting and
 * parsing live here so they are unit-tested and the screens never do
 * floating-point arithmetic on money.
 */
object Money {
    /** 123456 → "$1,234.56"; -500 → "-$5.00". */
    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val a = abs(cents)
        val dollars = String.format(Locale.US, "%,d", a / 100)
        val rest = (a % 100).toString().padStart(2, '0')
        return "$sign$$dollars.$rest"
    }

    /** 123456 → "$1,235": rounded to whole dollars, for estimates. */
    fun whole(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val dollars = (abs(cents) + 50) / 100
        return "$sign$" + String.format(Locale.US, "%,d", dollars)
    }

    /** "$1,234.5" → 123450. Null for anything that isn't a plain amount of zero or more. */
    fun parse(input: String): Long? {
        val text = input.trim().removePrefix("$").replace(",", "").trim()
        if (!AMOUNT.matches(text)) return null
        val parts = text.split(".")
        val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
        val fraction = if (parts.size > 1) parts[1].padEnd(2, '0') else "00"
        if (whole > 99_999_999L) return null
        return whole * 100 + fraction.toLong()
    }

    /** 17240 → "172.40", the form the API takes. */
    fun toDecimal(cents: Long): String {
        require(cents >= 0) { "negative amount" }
        return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }

    private val AMOUNT = Regex("^(\\d+(\\.\\d{0,2})?|\\.\\d{1,2})$")
}
