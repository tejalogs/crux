package org.crux.ui.model.mapper

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.time.Instant

internal fun parseRssDateToInstant(input: String): Instant? =
    runCatching {
        Instant.parse(input)
    }.getOrNull() ?: runCatching {
        parseIso8601LocalToInstant(input)
    }.getOrNull() ?: runCatching {
        parseRfc2822LikeToInstant(input)
    }.getOrNull()

private fun parseRfc2822LikeToInstant(input: String): Instant {
    var text = input.trim()
    val commaIdx = text.indexOf(',')
    if (commaIdx in 1..5) {
        text = text.substring(commaIdx + 1).trimStart()
    }
    val parts =
        text
            .split(' ', '\t')
            .filter { it.isNotEmpty() }

    require(parts.size >= 4) { "Invalid datetime: $input" }

    val dayStr = parts[0]
    val monStr = parts[1]
    val yearStr = parts[2]
    val timeStr = parts[3]
    // A missing zone is read as UTC, like most feed readers do.
    val zoneStr = parts.getOrNull(4)

    val day = dayStr.toIntOrNull() ?: error("Invalid day: $dayStr")
    val year = parseYear(yearStr)
    val month = parseMonthAbbr(monStr) ?: error("Unknown month: $monStr")

    val (hour, minute, seconds) = parseTimeHms(timeStr)
    val offset = zoneStr?.let(::parseZoneOffset) ?: UtcOffset.ZERO

    val ldt = LocalDateTime(year, month, day, hour, minute, seconds)
    return ldt.toInstant(offset)
}

/** RFC 5322 obsolete years: two digits are 2000-2049 or 1950-1999, three digits count from 1900. */
private fun parseYear(value: String): Int {
    val year = value.toIntOrNull() ?: error("Invalid year: $value")
    return when (value.length) {
        1, 2 -> if (year < 50) 2000 + year else 1900 + year
        3 -> 1900 + year
        else -> year
    }
}

private fun parseMonthAbbr(m: String): Month? =
    when (m.uppercase().take(3)) {
        "JAN" -> Month.JANUARY
        "FEB" -> Month.FEBRUARY
        "MAR" -> Month.MARCH
        "APR" -> Month.APRIL
        "MAY" -> Month.MAY
        "JUN" -> Month.JUNE
        "JUL" -> Month.JULY
        "AUG" -> Month.AUGUST
        "SEP" -> Month.SEPTEMBER
        "OCT" -> Month.OCTOBER
        "NOV" -> Month.NOVEMBER
        "DEC" -> Month.DECEMBER
        else -> null
    }

private fun parseTimeHms(t: String): Triple<Int, Int, Int> {
    val seg = t.split(':')
    require(seg.size == 2 || seg.size == 3) { "Invalid time: $t" }
    val h = seg[0].toIntOrNull() ?: error("Invalid hour: ${seg[0]}")
    val m = seg[1].toIntOrNull() ?: error("Invalid minute: ${seg[1]}")
    val s = if (seg.size == 3) seg[2].toIntOrNull() ?: error("Invalid second: ${seg[2]}") else 0
    require(h in 0..23 && m in 0..59 && s in 0..59) { "Out-of-range time: $t" }
    return Triple(h, m, s)
}

// RFC 822 zone names, plus the common abbreviations feeds use in practice. Military letters are
// ambiguous in the wild, so only Z is read as UTC.
private val ZONE_NAMES: Map<String, Int> =
    mapOf(
        "GMT" to 0,
        "UTC" to 0,
        "UT" to 0,
        "Z" to 0,
        "EST" to -5,
        "EDT" to -4,
        "CST" to -6,
        "CDT" to -5,
        "MST" to -7,
        "MDT" to -6,
        "PST" to -8,
        "PDT" to -7,
    )

private fun parseZoneOffset(raw: String): UtcOffset {
    val z = raw.trim().uppercase()
    ZONE_NAMES[z]?.let { return UtcOffset(hours = it) }
    require(z.isNotEmpty() && (z[0] == '+' || z[0] == '-')) { "Invalid zone: $raw" }
    val sign = if (z[0] == '-') -1 else 1
    val body = z.substring(1)
    val (h, m) =
        if (body.contains(':')) {
            val (hs, ms) = body.split(':').also { require(it.size == 2) { "Invalid zone: $raw" } }
            (hs.toIntOrNull() ?: error("Invalid zone hour: $hs")) to (ms.toIntOrNull() ?: error("Invalid zone minute: $ms"))
        } else {
            require(body.length in 2..4) { "Invalid zone: $raw" }
            val padded = body.padStart(4, '0')
            val hs = padded.substring(0, 2)
            val ms = padded.substring(2, 4)
            (hs.toIntOrNull() ?: error("Invalid zone hour: $hs")) to (ms.toIntOrNull() ?: error("Invalid zone minute: $ms"))
        }
    require(h in 0..18 && m in 0..59) { "Out-of-range zone: $raw" }
    return UtcOffset(hours = sign * h, minutes = sign * m)
}

private fun parseIso8601LocalToInstant(input: String): Instant {
    val trimmed = input.trim()
    require('T' in trimmed) { "Invalid ISO8601 local datetime: $input" }
    val local = LocalDateTime.parse(trimmed)
    return local.toInstant(UtcOffset.ZERO)
}
