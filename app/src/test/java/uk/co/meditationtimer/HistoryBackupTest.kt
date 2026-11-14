package uk.co.meditationtimer

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class HistoryBackupTest {
    private val row=Completed("backup-id",Instant.parse("2026-11-12T15:00:01Z").toEpochMilli(),600000,LocalDate.of(2026,11,13),"Asia/Tokyo")
    private fun altered(change: (JSONObject) -> Unit): String {
        val root=JSONObject(HistoryBackup.encode(listOf(row)))
        change(root.getJSONArray("sessions").getJSONObject(0))
        return root.toString()
    }
    @Test fun roundTripPreservesIdentityTimeZoneAndCompletionDay() {
        assertEquals(listOf(row),HistoryBackup.read(HistoryBackup.encode(listOf(row)).byteInputStream()))
        assertEquals(emptyList<Completed>(),HistoryBackup.decode(HistoryBackup.encode(emptyList())))
    }
    @Test fun unsupportedVersionAndMalformedFilesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { HistoryBackup.decode("not JSON") }
        assertThrows(IllegalArgumentException::class.java) { HistoryBackup.decode(HistoryBackup.encode(listOf(row))+" trailing") }
        val root=JSONObject(HistoryBackup.encode(listOf(row))).put("version",3)
        assertThrows(IllegalArgumentException::class.java) { HistoryBackup.decode(root.toString()) }
    }
    @Test fun invalidEntriesAreRejectedBeforeDatabaseWrites() {
        listOf<(JSONObject) -> Unit>(
            {it.put("id","")}, {it.put("durationMillis",0)}, {it.put("durationMillis",60000.5)},
            {it.put("completedAt",-1)}, {it.put("zoneId","Invalid/Zone")},
            {it.put("localDate","2026-11-12")}, {it.remove("completedAt")}
        ).forEach { change ->
            assertThrows(IllegalArgumentException::class.java) { HistoryBackup.decode(altered(change)) }
        }
    }
    @Test fun oversizedFilesAreRejectedWhileReading() {
        assertThrows(IllegalArgumentException::class.java) {
            HistoryBackup.read(ByteArray(HistoryBackup.MAX_BYTES+1).inputStream())
        }
    }
    @Test fun excessiveSessionCountIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            HistoryBackup.encode(List(HistoryBackup.MAX_SESSIONS+1) { row })
        }
    }
}
