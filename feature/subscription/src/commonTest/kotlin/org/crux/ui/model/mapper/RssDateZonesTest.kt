package org.crux.ui.model.mapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class RssDateZonesTest {
    @Test
    fun usZoneAbbreviationsAreHonoured() {
        assertEquals(Instant.parse("2025-11-20T15:00:00Z"), parseRssDateToInstant("Thu, 20 Nov 2025 10:00:00 EST"))
        assertEquals(Instant.parse("2025-07-20T14:00:00Z"), parseRssDateToInstant("Sun, 20 Jul 2025 10:00:00 EDT"))
        assertEquals(Instant.parse("2025-11-20T16:00:00Z"), parseRssDateToInstant("Thu, 20 Nov 2025 10:00:00 CST"))
        assertEquals(Instant.parse("2025-07-20T15:00:00Z"), parseRssDateToInstant("Sun, 20 Jul 2025 10:00:00 CDT"))
        assertEquals(Instant.parse("2025-11-20T17:00:00Z"), parseRssDateToInstant("Thu, 20 Nov 2025 10:00:00 MST"))
        assertEquals(Instant.parse("2025-07-20T16:00:00Z"), parseRssDateToInstant("Sun, 20 Jul 2025 10:00:00 MDT"))
        assertEquals(Instant.parse("2025-11-20T18:00:00Z"), parseRssDateToInstant("Thu, 20 Nov 2025 10:00:00 PST"))
        assertEquals(Instant.parse("2025-07-20T17:00:00Z"), parseRssDateToInstant("Sun, 20 Jul 2025 10:00:00 PDT"))
    }

    @Test
    fun utcSpellingsAreAllZero() {
        val expected = Instant.parse("2025-11-20T10:00:00Z")
        listOf("GMT", "UTC", "UT", "Z", "gmt", "+0000", "-00:00").forEach { zone ->
            assertEquals(expected, parseRssDateToInstant("Thu, 20 Nov 2025 10:00:00 $zone"), zone)
        }
    }

    @Test
    fun twoDigitYearsFollowTheRfcWindow() {
        assertEquals(Instant.parse("2025-11-20T10:00:00Z"), parseRssDateToInstant("Thu, 20 Nov 25 10:00:00 GMT"))
        assertEquals(Instant.parse("1999-11-20T10:00:00Z"), parseRssDateToInstant("Sat, 20 Nov 99 10:00:00 GMT"))
        assertEquals(Instant.parse("2049-01-02T03:04:05Z"), parseRssDateToInstant("2 Jan 49 03:04:05 GMT"))
        assertEquals(Instant.parse("1950-01-02T03:04:05Z"), parseRssDateToInstant("2 Jan 50 03:04:05 GMT"))
    }

    @Test
    fun missingZoneIsReadAsUtcAndFullMonthNamesWork() {
        assertEquals(Instant.parse("2025-11-20T10:00:00Z"), parseRssDateToInstant("Thu, 20 Nov 2025 10:00:00"))
        assertEquals(Instant.parse("2025-11-20T10:00:00Z"), parseRssDateToInstant("20 November 2025 10:00 GMT"))
    }

    @Test
    fun unknownZoneNamesStayInvalid() {
        assertEquals(null, parseRssDateToInstant("Thu, 20 Nov 2025 10:00:00 XYZ"))
    }
}
