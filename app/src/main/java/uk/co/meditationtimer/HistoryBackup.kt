package uk.co.meditationtimer

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Versioned, portable history only: never exports preferences, active timers or diagnostics. */
object HistoryBackup {
    const val MAX_BYTES = 10 * 1024 * 1024
    const val MAX_SESSIONS = 10000

    fun encode(rows: List<Completed>): String {
        require(rows.size <= MAX_SESSIONS) { "History exceeds the 10,000 session backup limit." }
        val json=JSONObject().apply {
        put("format", "still-history")
        put("version", 2)
        put("exportedAt", Instant.now().toString())
        put("sessions", JSONArray().apply {
            rows.forEach { row -> put(JSONObject().apply {
                put("id", row.id)
                put("completedAt", row.millis)
                put("durationMillis", row.duration)
                put("localDate", row.date.toString())
                put("zoneId", row.zone)
                put("kind", row.kind)
            }) }
        })
        }.toString(2)
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup exceeds the 10 MB limit." }
        return json
    }

    fun read(input: InputStream): List<Completed> {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "Backup exceeds the 10 MB limit." }
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        return decode(bytes.toString(Charsets.UTF_8))
    }

    fun decode(text: String): List<Completed> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup exceeds the 10 MB limit." }
        try {
            val parser = JSONTokener(text)
            val root = parser.nextValue() as? JSONObject ?: throw IllegalArgumentException("Expected a history backup JSON object.")
            require(parser.nextClean() == '\u0000') { "Unexpected content after the history backup." }
            require(root.getString("format") == "still-history" && integer(root, "version") in 1L..2L) {
                "Unsupported history backup format or version."
            }
            val rows = root.getJSONArray("sessions")
            require(rows.length() <= MAX_SESSIONS) { "Backup exceeds the 10,000 session limit." }
            return (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                val id = row.getString("id")
                require(id.isNotBlank() && id.length <= 128) { "Invalid session ID at entry ${index + 1}." }
                val millis = integer(row, "completedAt")
                val duration = integer(row, "durationMillis")
                val kind=if(integer(root,"version")==1L) MEDITATION else row.getString("kind")
                require(kind in SESSION_KINDS) { "Unknown session type." }
                val validDuration=if(kind==MINDFUL_OPEN) duration in 1000L..3155760000000L else duration in 60000L..7200000L
                require(millis >= 0 && validDuration) { "Invalid time or duration at entry ${index + 1}." }
                val date = LocalDate.parse(row.getString("localDate"))
                val zone = ZoneId.of(row.getString("zoneId"))
                require(date.year in 1..9999 && Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() == date) {
                    "Completion date and time do not match at entry ${index + 1}."
                }
                Completed(id, millis, duration, date, zone.id, kind)
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid history backup. Check its JSON fields, dates and time zones.", e)
        }
    }

    private fun integer(row: JSONObject, key: String): Long {
        val value = row.get(key)
        require(value is Int || value is Long) { "$key must be a whole number." }
        return (value as Number).toLong()
    }
}

data class ImportResult(val added: Int, val skipped: Int)
