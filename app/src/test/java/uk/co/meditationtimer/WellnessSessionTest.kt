package uk.co.meditationtimer

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WellnessSessionTest {
    private val row =
        Completed(
            "legacy-session",
            Instant.parse("2026-10-25T01:30:00Z").toEpochMilli(),
            600000,
            LocalDate.of(2026, 10, 25),
            "Europe/London",
        )

    @Test
    fun backfillIdsAreStableForLegacyAndImportedHistory() {
        assertEquals(WellnessSession.eventId(row.id), WellnessSession.eventId(row.copy().id))
        assertNotEquals(WellnessSession.eventId(row.id), WellnessSession.eventId("another-session"))
        val json = WellnessSession.event(row, "installation")
        assertEquals("meditation", json.getString("sourceApplication"))
        assertEquals("meditation.session.completed", json.getString("eventType"))
        assertEquals(WellnessSession.eventId(row.id), json.getString("eventId"))
        assertEquals("2026-10-25T01:30:00Z", json.getString("occurredAt"))
        assertEquals(0, json.getInt("offsetSeconds"))
        assertFalse(json.getJSONObject("payload").has("startedAt"))
    }

    @Test
    fun allPracticeKindsPreserveTheirExistingDurationAndDate() {
        for (kind in SESSION_KINDS) {
            val payload =
                WellnessSession.event(
                        row.copy(
                            kind = kind,
                            duration = if (kind == MINDFUL_OPEN) 1234 else row.duration,
                        ),
                        "installation",
                    )
                    .getJSONObject("payload")
            assertEquals(kind, payload.getString("kind"))
            assertEquals(row.date.toString(), payload.getString("localDate"))
        }
    }

    @Test
    fun httpsAndAcknowledgementRulesRejectUntrustedDestinationsAndWrongIds() {
        assertEquals(
            "https://example.com",
            WellnessConnection.normalizeServer("HTTPS://Example.com:443/"),
        )
        for (url in
            listOf(
                "http://example.com",
                "https://secret@example.com",
                "https://example.com?key=x",
                "https://example.com/#x",
            )) {
            assertThrows(Exception::class.java) { WellnessConnection.normalizeServer(url) }
        }
        val response =
            """{"eventId":"id","status":"accepted","receivedAt":"2026-11-17T07:00:00Z"}"""
        assertTrue(WellnessConnection.acknowledged(201, response, "id"))
        assertFalse(WellnessConnection.acknowledged(201, response, "wrong"))
    }
}
