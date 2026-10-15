package com.kairos.app.ui.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/*
 * Turning ONE calendar file (an .ics attachment opened from an email) into a
 * draft for the New event form. The file is read here, on the phone.
 *
 * MIRRORS the web's src/lib/calendar/ics-import.ts, which the web's
 * "Import .ics" button uses. The two must agree exactly — the same file gives
 * the same draft on both — and the web repo's scripts/ics-parity/ checks them
 * against a shared set of files. Change one, change the other, re-run it.
 * The rules are written out at the top of the web file.
 */

data class IcsDraft(
    val title: String,
    val allDay: Boolean,
    val startDate: String,
    val startTime: String?,
    val endDate: String,
    val endTime: String?,
    val location: String?,
    val notes: String?,
    val repeat: String,
    val repeatDropped: Boolean,
)

sealed class IcsImport {
    data class Event(val draft: IcsDraft) : IcsImport()
    data class Many(val count: Int) : IcsImport()
    data object Cancelled : IcsImport()
    data object None : IcsImport()
}

const val ICS_TITLE_MAX = 120
const val ICS_LOCATION_MAX = 200
const val ICS_MAX_BYTES = 1_000_000

private val WINDOWS_TZ = mapOf(
    "Eastern Standard Time" to "America/New_York",
    "Central Standard Time" to "America/Chicago",
    "Mountain Standard Time" to "America/Denver",
    "US Mountain Standard Time" to "America/Phoenix",
    "Pacific Standard Time" to "America/Los_Angeles",
    "Alaskan Standard Time" to "America/Anchorage",
    "Hawaiian Standard Time" to "Pacific/Honolulu",
    "Atlantic Standard Time" to "America/Halifax",
    "Central Standard Time (Mexico)" to "America/Mexico_City",
    "GMT Standard Time" to "Europe/London",
    "W. Europe Standard Time" to "Europe/Berlin",
    "Romance Standard Time" to "Europe/Paris",
    "UTC" to "UTC",
    "Coordinated Universal Time" to "UTC",
)

private class IcsField(val value: String, val params: Map<String, String>)

private const val WS = " \t\r\n\u000c\u000b ﻿"

private fun trimWs(s: String): String {
    var a = 0
    var b = s.length
    while (a < b && WS.indexOf(s[a]) >= 0) a++
    while (b > a && WS.indexOf(s[b - 1]) >= 0) b--
    return s.substring(a, b)
}

/** First [max] UTF-16 units, never ending on half of a surrogate pair. */
private fun clip(s: String, max: Int): String {
    if (s.length <= max) return s
    return s.substring(0, if (Character.isHighSurrogate(s[max - 1])) max - 1 else max)
}

private fun unescapeText(v: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < v.length) {
        val c = v[i]
        if (c == '\\' && i + 1 < v.length) {
            val n = v[i + 1]
            if (n == 'n' || n == 'N') out.append('\n') else out.append(n)
            i += 2
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}

private fun unfold(raw: String): List<String> {
    val lines = ArrayList<String>()
    for (line in raw.split(Regex("\r\n|\n|\r"))) {
        if ((line.startsWith(" ") || line.startsWith("\t")) && lines.isNotEmpty()) {
            lines[lines.size - 1] = lines[lines.size - 1] + line.substring(1)
        } else {
            lines.add(line)
        }
    }
    return lines
}

private fun parseLine(line: String): Pair<String, IcsField>? {
    var inQuote = false
    var colon = -1
    for (i in line.indices) {
        val c = line[i]
        if (c == '"') inQuote = !inQuote
        else if (c == ':' && !inQuote) { colon = i; break }
    }
    if (colon <= 0) return null
    val head = line.substring(0, colon)
    val value = line.substring(colon + 1)
    val parts = ArrayList<String>()
    val cur = StringBuilder()
    inQuote = false
    for (c in head) {
        if (c == '"') inQuote = !inQuote
        if (c == ';' && !inQuote) { parts.add(cur.toString()); cur.setLength(0) } else cur.append(c)
    }
    parts.add(cur.toString())
    val name = trimWs(parts[0]).uppercase()
    if (name.isEmpty()) return null
    val params = LinkedHashMap<String, String>()
    for (p in parts.drop(1)) {
        val eq = p.indexOf('=')
        if (eq <= 0) continue
        var v = p.substring(eq + 1)
        if (v.length >= 2 && v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length - 1)
        params[trimWs(p.substring(0, eq)).uppercase()] = v
    }
    return name to IcsField(value, params)
}

private val ZONE_SHAPE = Regex("^[A-Za-z][A-Za-z0-9_+\\-]*(/[A-Za-z0-9_+\\-]+)*$")

private fun isZone(tz: String): Boolean {
    if (!ZONE_SHAPE.matches(tz)) return false
    return runCatching { ZoneId.of(tz) }.isSuccess
}

private fun resolveZone(tzid: String?): String? {
    if (tzid == null) return null
    val t = trimWs(tzid)
    WINDOWS_TZ[t]?.let { return it }
    if (isZone(t)) return t
    val segs = t.split("/").filter { it.isNotEmpty() }
    if (segs.size >= 2) {
        val tail = "${segs[segs.size - 2]}/${segs[segs.size - 1]}"
        if (isZone(tail)) return tail
    }
    return null
}

private fun pad(n: Int) = n.toString().padStart(2, '0')

private sealed class Moment {
    data class Day(val date: String) : Moment()
    data class At(val epochMs: Long) : Moment()
}

private val DT = Regex("^(\\d{4})(\\d{2})(\\d{2})(?:T(\\d{2})(\\d{2})(\\d{2})?(Z)?)?$")

/** Same two-pass resolution as the web's zonedToUtc, so DST gaps land alike. */
private fun offsetMs(epochMs: Long, tz: String): Long =
    ZoneId.of(tz).rules.getOffset(Instant.ofEpochMilli(epochMs)).totalSeconds * 1000L

private fun zonedToUtc(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int, tz: String): Long {
    val guess = LocalDateTime.of(y, mo, d, h, mi, s).toEpochSecond(ZoneOffset.UTC) * 1000L
    val first = offsetMs(guess, tz)
    val second = offsetMs(guess - first, tz)
    return guess - second
}

private fun parseMoment(f: IcsField, targetTz: String): Moment? {
    val m = DT.matchEntire(trimWs(f.value)) ?: return null
    val g = m.groupValues
    val y = g[1].toInt(); val mo = g[2].toInt(); val d = g[3].toInt()
    // Real invites only; also keeps every date four digits after a zone shift.
    if (y < 1900 || y > 2199) return null
    if (runCatching { LocalDate.of(y, mo, d) }.isFailure) return null
    val date = "${g[1]}-${g[2]}-${g[3]}"
    val hasTime = g[4].isNotEmpty()
    if (f.params["VALUE"]?.uppercase() == "DATE" || !hasTime) return Moment.Day(date)
    val h = g[4].toInt(); val mi = g[5].toInt()
    val s = if (g[6].isEmpty()) 0 else g[6].toInt()
    if (h > 23 || mi > 59 || s > 59) return null
    if (g[7].isNotEmpty()) {
        return Moment.At(LocalDateTime.of(y, mo, d, h, mi, s).toEpochSecond(ZoneOffset.UTC) * 1000L)
    }
    val zone = resolveZone(f.params["TZID"]) ?: targetTz
    return Moment.At(zonedToUtc(y, mo, d, h, mi, s, zone))
}

private fun wallClock(epochMs: Long, tz: String): Pair<String, String> {
    val local = Instant.ofEpochMilli(epochMs + offsetMs(epochMs, tz)).atOffset(ZoneOffset.UTC)
    return "${local.year}-${pad(local.monthValue)}-${pad(local.dayOfMonth)}" to
        "${pad(local.hour)}:${pad(local.minute)}"
}

private val DURATION = Regex("^([+-])?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$")

private fun parseDuration(v: String): Pair<Long, Long>? {
    val m = DURATION.matchEntire(trimWs(v).uppercase()) ?: return null
    val g = m.groupValues
    fun n(i: Int) = if (g[i].isEmpty()) 0L else g[i].toLong()
    val sign = if (g[1] == "-") -1L else 1L
    val days = (n(2) * 7 + n(3)) * sign
    val secs = n(4) * 3600 + n(5) * 60 + n(6)
    return (days * 86400 + secs * sign) * 1000L to days
}

private val DAY_TOKENS = listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")

private fun addDays(iso: String, n: Long) = LocalDate.parse(iso).plusDays(n).toString()

private fun mapRepeat(ev: Map<String, IcsField>, startDate: String, hasOverrides: Boolean): Pair<String, Boolean> {
    val rule = ev["RRULE"]?.value
    if (rule.isNullOrEmpty()) return "NONE" to false
    val drop = "NONE" to true
    if (ev.containsKey("EXDATE") || ev.containsKey("RDATE") || hasOverrides) return drop
    val parts = LinkedHashMap<String, String>()
    for (p in rule.split(";")) {
        val eq = p.indexOf('=')
        if (eq > 0) parts[trimWs(p.substring(0, eq)).uppercase()] = trimWs(p.substring(eq + 1)).uppercase()
    }
    val freq = parts["FREQ"]
    if (freq != "DAILY" && freq != "WEEKLY" && freq != "MONTHLY" && freq != "YEARLY") return drop
    val start = LocalDate.parse(startDate)
    val dow = DAY_TOKENS[start.dayOfWeek.value % 7]
    for ((k, v) in parts) {
        if (k == "FREQ" || k == "WKST") continue
        if (k == "INTERVAL" && v == "1") continue
        if (k == "BYDAY" && freq == "WEEKLY" && v == dow) continue
        if (k == "BYMONTHDAY" && (freq == "MONTHLY" || freq == "YEARLY") && v == start.dayOfMonth.toString()) continue
        if (k == "BYMONTH" && freq == "YEARLY" && v == start.monthValue.toString()) continue
        return drop
    }
    return freq to false
}

private fun buildDraft(ev: Map<String, IcsField>, targetTz: String, hasOverrides: Boolean): IcsDraft? {
    val start = ev["DTSTART"]?.let { parseMoment(it, targetTz) } ?: return null
    val title = trimWs(
        Regex("[\\r\\n\\t ]+").replace(unescapeText(ev["SUMMARY"]?.value ?: ""), " "),
    ).let { clip(it, ICS_TITLE_MAX) }
    val loc = trimWs(Regex("[\\r\\n]+").replace(unescapeText(ev["LOCATION"]?.value ?: ""), ", "))
    val notes = clip(trimWs(unescapeText(ev["DESCRIPTION"]?.value ?: "")), NOTES_MAX)
    val end = ev["DTEND"]?.let { parseMoment(it, targetTz) }
    val dur = if (end == null) ev["DURATION"]?.let { parseDuration(it.value) } else null

    val allDay: Boolean
    val startDate: String
    val startTime: String?
    val endDate: String
    val endTime: String?
    when (start) {
        is Moment.Day -> {
            var lastDay = start.date
            if (end != null) {
                val endDay = when (end) {
                    is Moment.Day -> end.date
                    is Moment.At -> wallClock(end.epochMs, targetTz).first
                }
                lastDay = addDays(endDay, -1)
            } else if (dur != null && dur.second > 0) {
                lastDay = addDays(start.date, dur.second - 1)
            }
            if (LocalDate.parse(lastDay).isBefore(LocalDate.parse(start.date))) lastDay = start.date
            allDay = true; startDate = start.date; startTime = null; endDate = lastDay; endTime = null
        }
        is Moment.At -> {
            var endMs: Long? = when {
                end is Moment.Day -> {
                    val ld = LocalDate.parse(end.date)
                    zonedToUtc(ld.year, ld.monthValue, ld.dayOfMonth, 0, 0, 0, targetTz)
                }
                end is Moment.At -> end.epochMs
                dur != null -> start.epochMs + dur.first
                else -> null
            }
            if (endMs == null || endMs <= start.epochMs) endMs = start.epochMs + 3_600_000L
            val s = wallClock(start.epochMs, targetTz)
            val e = wallClock(endMs, targetTz)
            allDay = false; startDate = s.first; startTime = s.second; endDate = e.first; endTime = e.second
        }
    }
    val (repeat, dropped) = mapRepeat(ev, startDate, hasOverrides)
    return IcsDraft(
        title = title,
        allDay = allDay,
        startDate = startDate,
        startTime = startTime,
        endDate = endDate,
        endTime = endTime,
        location = loc.takeIf { it.isNotEmpty() }?.let { clip(it, ICS_LOCATION_MAX) },
        notes = notes.takeIf { it.isNotEmpty() },
        repeat = repeat,
        repeatDropped = dropped,
    )
}

/** Never throws: a file it can't make sense of is simply "no event". */
fun readIcsFile(raw: String, householdTz: String): IcsImport =
    try {
        read(raw, householdTz)
    } catch (e: Exception) {
        IcsImport.None
    }

private fun read(raw: String, householdTz: String): IcsImport {
    val targetTz = if (isZone(householdTz)) householdTz else "UTC"
    val text = if (raw.startsWith("﻿")) raw.substring(1) else raw
    val stack = ArrayList<String>()
    val events = ArrayList<Map<String, IcsField>>()
    var method = ""
    var current: LinkedHashMap<String, IcsField>? = null

    for (line in unfold(text)) {
        val (name, field) = parseLine(line) ?: continue
        if (name == "BEGIN") {
            val comp = trimWs(field.value).uppercase()
            stack.add(comp)
            if (comp == "VEVENT") current = LinkedHashMap()
            continue
        }
        if (name == "END") {
            val comp = if (stack.isEmpty()) null else stack.removeAt(stack.size - 1)
            if (comp == "VEVENT" && current != null) {
                events.add(current)
                current = null
            }
            continue
        }
        val top = stack.lastOrNull()
        val cur = current
        if (top == "VCALENDAR" && name == "METHOD" && method.isEmpty()) {
            method = trimWs(field.value).uppercase()
        } else if (top == "VEVENT" && cur != null && !cur.containsKey(name)) {
            cur[name] = field
        }
    }

    if (method == "CANCEL") return IcsImport.Cancelled
    fun cancelled(e: Map<String, IcsField>) = trimWs(e["STATUS"]?.value ?: "").uppercase() == "CANCELLED"
    val live = events.filter { it.containsKey("DTSTART") && !cancelled(it) }
    val masters = live.filter { !it.containsKey("RECURRENCE-ID") }
    val overrides = live.filter { it.containsKey("RECURRENCE-ID") }

    val pool = if (masters.isNotEmpty()) masters else overrides
    val seen = HashSet<String>()
    val distinct = ArrayList<Map<String, IcsField>>()
    for (e in pool) {
        val uid = trimWs(e["UID"]?.value ?: "")
        val key = if (masters.isNotEmpty()) uid else "$uid|${e["RECURRENCE-ID"]?.value ?: ""}"
        if (uid.isNotEmpty() && seen.contains(key)) continue
        if (uid.isNotEmpty()) seen.add(key)
        distinct.add(e)
    }

    if (distinct.isEmpty()) {
        return if (events.any { cancelled(it) }) IcsImport.Cancelled else IcsImport.None
    }
    if (distinct.size > 1) return IcsImport.Many(distinct.size)

    val only = distinct[0]
    val uid = trimWs(only["UID"]?.value ?: "")
    val hasOverrides = masters.isNotEmpty() && uid.isNotEmpty() &&
        overrides.any { trimWs(it["UID"]?.value ?: "") == uid }
    val draft = buildDraft(only, targetTz, hasOverrides) ?: return IcsImport.None
    return IcsImport.Event(draft)
}
