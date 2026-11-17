package uk.co.meditationtimer

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.time.LocalDate
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class WellnessPersistenceTest {
    private lateinit var context: Context
    private lateinit var store: Store
    private val row = Completed("legacy-id", 1800000000000, 60000, LocalDate.of(2027, 1, 15), "UTC")

    @Before
    fun before() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("wellness-test.db")
        store = Store(context, "wellness-test.db")
    }

    @After
    fun after() {
        store.close()
        context.deleteDatabase("wellness-test.db")
    }

    @Test
    fun existingHistoryBackfillsAndAcknowledgementsSurviveReopen() {
        store.importHistory(
            listOf(row, row.copy(id = "open", kind = MINDFUL_OPEN, duration = 1234))
        )
        val installation = store.wellnessInstallation()
        assertEquals(2, store.wellnessCounts(WellnessConnection.DEFAULT_SERVER).first)
        store.wellnessDelivered(row.id, WellnessConnection.DEFAULT_SERVER)
        store.close()
        store = Store(context, "wellness-test.db")
        assertEquals(installation, store.wellnessInstallation())
        assertEquals(1, store.wellnessPending(WellnessConnection.DEFAULT_SERVER).size)
        assertEquals(2, store.wellnessPending("https://other.example.com").size)
        assertEquals(ImportResult(0, 1), store.importHistory(listOf(row)))
    }

    @Test
    fun completedSessionsAreTheDurableQueueAndTestsAreExcluded() {
        val real = Session("new-session", 60000, 60000, 61000, 2)
        store.save(real)
        assertTrue(store.complete(real, row.millis))
        assertFalse(store.complete(real, row.millis))
        val test = real.copy(id = "reliability", test = true)
        store.save(test)
        assertTrue(store.complete(test, row.millis))
        assertEquals(1, store.wellnessPending(WellnessConnection.DEFAULT_SERVER).size)
    }

    @Test
    fun localDeletionAndLateAcknowledgementNeverRecreateHistory() {
        store.importHistory(listOf(row))
        store.wellnessDelivered(row.id, WellnessConnection.DEFAULT_SERVER)
        store.delete(row.id)
        store.wellnessDelivered(row.id, WellnessConnection.DEFAULT_SERVER)
        assertTrue(store.history().isEmpty())
        assertEquals(0, store.wellnessCounts(WellnessConnection.DEFAULT_SERVER).first)
        assertEquals(
            0,
            store.readableDatabase.rawQuery("SELECT count(*) FROM wellness_delivery", null).use {
                it.moveToFirst()
                it.getInt(0)
            },
        )
    }

    @Test
    fun deliveryMetadataDoesNotChangeActiveTimerOrOriginalHistory() {
        store.importHistory(listOf(row))
        val active = Session("active", 60000, 30000, 0, 2, state = "paused")
        store.save(active)
        store.wellnessDelivered(row.id, WellnessConnection.DEFAULT_SERVER, rejected = true)
        assertEquals(active, store.active())
        assertEquals(listOf(row), store.history())
        assertTrue(store.wellnessPending(WellnessConnection.DEFAULT_SERVER).isEmpty())
        store.wellnessRetryRejected(WellnessConnection.DEFAULT_SERVER)
        assertEquals(listOf(row), store.wellnessPending(WellnessConnection.DEFAULT_SERVER))
    }

    @Test
    fun v1AndV2DatabaseMigrationsPreserveSavedHistoryAndActiveState() {
        for (version in 1..2) {
            val name = "migration-$version.db"
            context.deleteDatabase(name)
            val file = context.getDatabasePath(name)
            file.parentFile!!.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                val extraSession =
                    if (version == 2) ", kind TEXT NOT NULL DEFAULT 'meditation'" else ""
                val extraActive =
                    if (version == 2)
                        ", kind TEXT NOT NULL DEFAULT 'meditation', accrued INTEGER NOT NULL DEFAULT 0, started INTEGER NOT NULL DEFAULT 0"
                    else ""
                db.execSQL(
                    "CREATE TABLE sessions (id TEXT PRIMARY KEY, millis INTEGER NOT NULL, duration INTEGER NOT NULL, date TEXT NOT NULL, zone TEXT NOT NULL$extraSession)"
                )
                db.execSQL(
                    "CREATE TABLE active (slot INTEGER PRIMARY KEY, id TEXT, duration INTEGER, remaining INTEGER, deadline INTEGER, boot INTEGER, state TEXT, test INTEGER, expected INTEGER, generation INTEGER$extraActive)"
                )
                db.execSQL("CREATE TABLE notes (key TEXT PRIMARY KEY,value TEXT NOT NULL)")
                db.execSQL(
                    "INSERT INTO sessions(id,millis,duration,date,zone) VALUES ('old',1800000000000,60000,'2027-01-15','UTC')"
                )
                db.execSQL(
                    "INSERT INTO active(slot,id,duration,remaining,deadline,boot,state,test,expected,generation) VALUES (1,'active',60000,30000,0,2,'paused',0,0,3)"
                )
                db.version = version
            }
            Store(context, name).use { upgraded ->
                assertEquals(3, upgraded.readableDatabase.version)
                assertEquals("active", upgraded.active()!!.id)
                assertEquals(3, upgraded.active()!!.generation)
                assertEquals("paused", upgraded.active()!!.state)
                assertEquals(
                    "old",
                    upgraded.wellnessPending(WellnessConnection.DEFAULT_SERVER).single().id,
                )
                assertEquals(MEDITATION, upgraded.history().single().kind)
            }
            context.deleteDatabase(name)
        }
    }
}
