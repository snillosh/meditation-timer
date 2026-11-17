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
data class Preferences(val minutes: Int = 10, val reminder: Boolean = false, val hour: Int = 9, val minute: Int = 0, val presets: List<Int> = DEFAULT_DURATION_PRESETS)
class PreferenceStore(private val context: Context) {
    private val duration = intPreferencesKey("minutes"); private val enabled = booleanPreferencesKey("reminder")
    private val hour = intPreferencesKey("hour"); private val minute = intPreferencesKey("minute")
    private val presets = stringPreferencesKey("duration_presets")
    val flow = context.preferences.data.map {
        Preferences(it[duration] ?: 10, it[enabled] ?: false, it[hour] ?: 9, it[minute] ?: 0,
            it[presets]?.let(::parseDurationPresets) ?: DEFAULT_DURATION_PRESETS)
    }
    suspend fun save(p: Preferences) {
        require(parseDurationPresets(p.presets.joinToString(",")) == p.presets) { "Invalid preset durations." }
        context.preferences.edit {
            it[duration] = p.minutes; it[enabled] = p.reminder; it[hour] = p.hour; it[minute] = p.minute
            it[presets] = p.presets.joinToString(",")
        }
    }
}
class AndroidClock(private val context: Context): Clock {
    override fun elapsed() = SystemClock.elapsedRealtime()
    override fun wall() = System.currentTimeMillis()
    override fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
}
class Store(context: Context, name: String = "still.db"): SQLiteOpenHelper(context, name, null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE active (slot INTEGER PRIMARY KEY CHECK(slot=1), id TEXT, duration INTEGER, remaining INTEGER, deadline INTEGER, boot INTEGER, state TEXT, test INTEGER, expected INTEGER, generation INTEGER, kind TEXT NOT NULL DEFAULT 'meditation', accrued INTEGER NOT NULL DEFAULT 0, started INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE sessions (id TEXT PRIMARY KEY, millis INTEGER NOT NULL, duration INTEGER NOT NULL, date TEXT NOT NULL, zone TEXT NOT NULL, kind TEXT NOT NULL DEFAULT 'meditation')")
        db.execSQL("CREATE TABLE notes (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        createWellnessDelivery(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if(old < 2) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN kind TEXT NOT NULL DEFAULT 'meditation'")
            db.execSQL("ALTER TABLE active ADD COLUMN kind TEXT NOT NULL DEFAULT 'meditation'")
            db.execSQL("ALTER TABLE active ADD COLUMN accrued INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE active ADD COLUMN started INTEGER NOT NULL DEFAULT 0")
        }
        if(old < 3) createWellnessDelivery(db)
    }
    private fun createWellnessDelivery(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE wellness_delivery (session_id TEXT NOT NULL, server TEXT NOT NULL, status TEXT NOT NULL CHECK(status IN ('acknowledged','rejected')), PRIMARY KEY(session_id,server))")
        db.execSQL("CREATE INDEX sessions_completion_idx ON sessions(millis,id)")
    }
    fun active(): Session? = readableDatabase.rawQuery("SELECT * FROM active WHERE slot=1", null).use { c ->
        if (!c.moveToFirst()) null else Session(c.getString(1),c.getLong(2),c.getLong(3),c.getLong(4),c.getInt(5),c.getString(6),c.getInt(7)==1,c.getLong(8),c.getInt(9),c.getString(10),c.getLong(11),c.getLong(12))
    }
    fun save(s: Session) { writableDatabase.insertWithOnConflict("active", null, ContentValues().apply {
        put("slot",1);put("id",s.id);put("duration",s.duration);put("remaining",s.remaining);put("deadline",s.deadline);put("boot",s.boot);put("state",s.state);put("test",if(s.test)1 else 0);put("expected",s.expectedWall);put("generation",s.generation);put("kind",s.kind);put("accrued",s.accrued);put("started",s.started)
    }, SQLiteDatabase.CONFLICT_REPLACE) }
    fun clear() { writableDatabase.delete("active",null,null) }
    fun note(key: String, value: String) { writableDatabase.insertWithOnConflict("notes",null,ContentValues().apply { put("key",key);put("value",value) },SQLiteDatabase.CONFLICT_REPLACE) }
    fun note(key: String): String = readableDatabase.rawQuery("SELECT value FROM notes WHERE key=?",arrayOf(key)).use { if(it.moveToFirst()) it.getString(0) else "No test run yet." }
    fun history(): List<Completed> = readableDatabase.rawQuery("SELECT * FROM sessions ORDER BY millis DESC",null).use { c -> buildList { while(c.moveToNext()) add(Completed(c.getString(0),c.getLong(1),c.getLong(2),LocalDate.parse(c.getString(3)),c.getString(4),c.getString(5))) } }
    fun delete(id: String) {
        val db=writableDatabase; db.beginTransaction()
        try {
            db.delete("wellness_delivery","session_id=?",arrayOf(id))
            db.delete("sessions","id=?",arrayOf(id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun wellnessExists(id: String): Boolean = readableDatabase.rawQuery("SELECT 1 FROM sessions WHERE id=?",arrayOf(id)).use { it.moveToFirst() }
    fun wellnessInstallation(): String {
        val db=writableDatabase
        db.insertWithOnConflict("notes",null,ContentValues().apply {
            put("key","wellness_installation"); put("value",java.util.UUID.randomUUID().toString())
        },SQLiteDatabase.CONFLICT_IGNORE)
        return note("wellness_installation")
    }
    fun wellnessPending(server: String, limit: Int = 10): List<Completed> = readableDatabase.rawQuery(
        "SELECT s.* FROM sessions s LEFT JOIN wellness_delivery d ON d.session_id=s.id AND d.server=? WHERE d.session_id IS NULL ORDER BY s.millis, s.id LIMIT ?",
        arrayOf(server,limit.coerceIn(1,100).toString())).use { c -> buildList {
            while(c.moveToNext()) add(Completed(c.getString(0),c.getLong(1),c.getLong(2),LocalDate.parse(c.getString(3)),c.getString(4),c.getString(5)))
        } }
    fun wellnessCounts(server: String): Pair<Int,Int> = readableDatabase.rawQuery(
        "SELECT sum(CASE WHEN d.status IS NULL OR d.status='rejected' THEN 1 ELSE 0 END), sum(CASE WHEN d.status='rejected' THEN 1 ELSE 0 END) FROM sessions s LEFT JOIN wellness_delivery d ON d.session_id=s.id AND d.server=?",arrayOf(server)).use {
        it.moveToFirst(); it.getInt(0) to it.getInt(1)
    }
    fun wellnessDelivered(id: String, server: String, rejected: Boolean = false) {
        // A local deletion during the request must never recreate history or delivery metadata.
        writableDatabase.execSQL("INSERT OR REPLACE INTO wellness_delivery (session_id,server,status) SELECT ?,?,? WHERE EXISTS (SELECT 1 FROM sessions WHERE id=?)",
            arrayOf(id,server,if(rejected) "rejected" else "acknowledged",id))
    }
    fun wellnessRetryRejected(server: String) { writableDatabase.delete("wellness_delivery","server=? AND status='rejected'",arrayOf(server)) }

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
                        put("date",row.date.toString()); put("zone",row.zone); put("kind",row.kind)
                    })
                    existing[row.id] = row
                    added++
                }
            }
            db.setTransactionSuccessful()
            return ImportResult(added, skipped)
        } finally { db.endTransaction() }
    }
    fun complete(s: Session, wall: Long, openDuration: Long? = null): Boolean {
        require(!s.open || (openDuration != null && openDuration >= 1000)) { "An open-ended session needs positive active time." }
        val db = writableDatabase; db.beginTransaction()
        try {
            if (active()?.let { it.id == s.id && it.generation == s.generation && (it.state == "running" || (s.open && it.state == "paused")) } != true) return false
            val zone = ZoneId.systemDefault()
            if (!s.test) db.insertWithOnConflict("sessions",null,ContentValues().apply {
                put("id",s.id);put("millis",wall);put("duration",openDuration ?: s.duration);put("kind",s.kind)
                put("date",Instant.ofEpochMilli(wall).atZone(zone).toLocalDate().toString());put("zone",zone.id)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            clear(); note("message", if(s.test) "Reliability test completed. Inspect Timer Health." else "Session complete. Take a quiet moment.")
            db.setTransactionSuccessful(); return true
        } finally { db.endTransaction() }
    }
}
