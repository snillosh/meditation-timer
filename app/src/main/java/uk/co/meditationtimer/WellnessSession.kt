package uk.co.meditationtimer

import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.json.JSONObject

/** Stable across retries and portable-history imports. No guessed start time. */
object WellnessSession {
    const val TYPE = "meditation.session.completed"

    fun eventId(sessionId: String): String =
        UUID.nameUUIDFromBytes(
                ("still:meditation.session.completed:v1:" + sessionId).toByteArray(Charsets.UTF_8)
            )
            .toString()

    fun event(row: Completed, installation: String): JSONObject {
        val zone = ZoneId.of(row.zone)
        val at = Instant.ofEpochMilli(row.millis)
        return JSONObject().apply {
            put("eventId", eventId(row.id))
            put("sourceApplication", "meditation")
            put("installationId", installation)
            put("eventType", TYPE)
            put("schemaVersion", 1)
            put("occurredAt", at.toString())
            if (zone.id in ZoneId.getAvailableZoneIds()) put("zoneId", zone.id)
            put("offsetSeconds", zone.rules.getOffset(at).totalSeconds)
            put(
                "payload",
                JSONObject().apply {
                    put("sessionId", row.id)
                    put("durationMillis", row.duration)
                    put("localDate", row.date.toString())
                    put("kind", row.kind)
                },
            )
        }
    }
}
