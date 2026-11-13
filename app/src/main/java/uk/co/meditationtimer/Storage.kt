package uk.co.meditationtimer

import android.content.*
import android.database.sqlite.*
import android.os.SystemClock
import android.provider.Settings
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import java.time.*

val Context.preferences by preferencesDataStore("preferences")
data class Preferences(val minutes: Int = 10, val reminder: Boolean = false, val hour: Int = 9, val minute: Int = 0)
class PreferenceStore(private val context: Context) {
    private val duration = intPreferencesKey("minutes"); private val enabled = booleanPreferencesKey("reminder")
    private val hour = intPreferencesKey("hour"); private val minute = intPreferencesKey("minute")
    val flow = context.preferences.data.map { Preferences(it[duration] ?: 10, it[enabled] ?: false, it[hour] ?: 9, it[minute] ?: 0) }
    suspend fun save(p: Preferences) { context.preferences.edit { it[duration] = p.minutes; it[enabled] = p.reminder; it[hour] = p.hour; it[minute] = p.minute } }
}
class AndroidClock(private val context: Context): Clock {
    override fun elapsed() = SystemClock.elapsedRealtime()
    override fun wall() = System.currentTimeMillis()
    override fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
}
class Store(context: Context): SQLiteOpenHelper(context, "still.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE active (slot INTEGER PRIMARY KEY CHECK(slot=1), id TEXT, duration INTEGER, remaining INTEGER, deadline INTEGER, boot INTEGER, state TEXT, test INTEGER, expected INTEGER, generation INTEGER)")
        db.execSQL("CREATE TABLE sessions (id TEXT PRIMARY KEY, millis INTEGER NOT NULL, duration INTEGER NOT NULL, date TEXT NOT NULL, zone TEXT NOT NULL)")
        db.execSQL("CREATE TABLE notes (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
    fun active(): Session? = readableDatabase.rawQuery("SELECT * FROM active WHERE slot=1", null).use { c ->
        if (!c.moveToFirst()) null else Session(c.getString(1),c.getLong(2),c.getLong(3),c.getLong(4),c.getInt(5),c.getString(6),c.getInt(7)==1,c.getLong(8),c.getInt(9))
    }
    fun save(s: Session) { writableDatabase.insertWithOnConflict("active", null, ContentValues().apply {
        put("slot",1);put("id",s.id);put("duration",s.duration);put("remaining",s.remaining);put("deadline",s.deadline);put("boot",s.boot);put("state",s.state);put("test",if(s.test)1 else 0);put("expected",s.expectedWall);put("generation",s.generation)
    }, SQLiteDatabase.CONFLICT_REPLACE) }
    fun clear() { writableDatabase.delete("active",null,null) }
    fun note(key: String, value: String) { writableDatabase.insertWithOnConflict("notes",null,ContentValues().apply { put("key",key);put("value",value) },SQLiteDatabase.CONFLICT_REPLACE) }
    fun note(key: String): String = readableDatabase.rawQuery("SELECT value FROM notes WHERE key=?",arrayOf(key)).use { if(it.moveToFirst()) it.getString(0) else "No test run yet." }
    fun history(): List<Completed> = readableDatabase.rawQuery("SELECT * FROM sessions ORDER BY millis DESC",null).use { c -> buildList { while(c.moveToNext()) add(Completed(c.getString(0),c.getLong(1),c.getLong(2),LocalDate.parse(c.getString(3)),c.getString(4))) } }
    fun delete(id: String) { writableDatabase.delete("sessions","id=?", arrayOf(id)) }
    /** Merge atomically. A conflicting ID aborts the entire import rather than overwriting history. */
    fun importHistory(rows: List<Completed>): ImportResult {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existing = history().associateBy { it.id }.toMutableMap()
            val activeId = active()?.id
            var added = 0
            var skipped = 0
            rows.forEach { row ->
                require(row.id != activeId) { "Backup conflicts with the active timer. End it before importing." }
                val previous = existing[row.id]
                if (previous != null) {
                    require(previous == row) { "A session ID has different contents. Nothing was imported." }
                    skipped++
                } else {
                    db.insertOrThrow("sessions", null, ContentValues().apply {
                        put("id",row.id); put("millis",row.millis); put("duration",row.duration)
                        put("date",row.date.toString()); put("zone",row.zone)
                    })
                    existing[row.id] = row
                    added++
                }
            }
            db.setTransactionSuccessful()
            return ImportResult(added, skipped)
        } finally { db.endTransaction() }
    }
    fun complete(s: Session, wall: Long): Boolean {
        val db = writableDatabase; db.beginTransaction()
        try {
            if (active()?.let { it.id == s.id && it.generation == s.generation && it.state == "running" } != true) return false
            val zone = ZoneId.systemDefault()
            if (!s.test) db.insertWithOnConflict("sessions",null,ContentValues().apply {
                put("id",s.id);put("millis",wall);put("duration",s.duration)
                put("date",Instant.ofEpochMilli(wall).atZone(zone).toLocalDate().toString());put("zone",zone.id)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            clear(); note("message", if(s.test) "Reliability test completed. Inspect Timer Health." else "Session complete. Take a quiet moment.")
            db.setTransactionSuccessful(); return true
        } finally { db.endTransaction() }
    }
}
