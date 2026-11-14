package uk.co.meditationtimer

import android.database.sqlite.SQLiteDatabase
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.*

class MindfulScreensTest {
    @get:Rule val compose=createComposeRule()
    @Test fun weekdaySelectionActivityAndScheduleSave() {
        var saved: MindfulConfig?=null
        compose.setContent { MaterialTheme {
            MindfulSettingsScreen(MindfulConfig(),true,{}, {}) {saved=it}
        } }
        compose.onNodeWithContentDescription("Enable mindful nudges").performClick()
        compose.onNodeWithText("Open-ended").performClick()
        for(day in listOf("Wednesday","Thursday","Friday","Saturday","Sunday")) {
            compose.onNodeWithContentDescription(day).performScrollTo().performClick()
        }
        compose.onNodeWithText("Fixed times",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Save Mindful Minutes").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(setOf(1,2),saved?.days);assertEquals("open",saved?.activity)
            assertEquals("fixed",saved?.schedule);assertTrue(saved?.enabled==true)
        }
    }
    @Test fun promptOnlyShowsDoneWithoutStartingOrTracking() {
        var began=false;var done=false
        compose.setContent { MaterialTheme {MindfulInvitation(MindfulConfig(activity="prompt"),false,{began=true},{done=true})} }
        compose.onNodeWithText("Pause for a moment.",substring=true).assertExists()
        compose.onNodeWithText("Begin mindful pause").assertDoesNotExist()
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle {assertTrue(done);assertFalse(began)}
    }
    @Test fun invitationCannotReplaceRunningSession() {
        compose.setContent {MaterialTheme {MindfulInvitation(MindfulConfig(),true,{}, {})} }
        compose.onNodeWithText("Begin mindful pause").assertIsNotEnabled()
        compose.onNodeWithText("Return to session").assertExists()
    }
}

class MindfulPersistenceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var engine: TimerEngine
    private class FakeClock: Clock {
        var time=1000L;var bootId=7
        override fun elapsed()=time
        override fun wall()=1800000000000L+time
        override fun boot()=bootId
    }
    private lateinit var clock: FakeClock
    private var bells=0
    @Before fun before() {context.deleteDatabase("test-mindful.db");clock=FakeClock();bells=0;engine=TimerEngine(context,clock,Store(context,"test-mindful.db")) {_,_->bells++} }
    @After fun after() {engine.end();engine.store.close();context.deleteDatabase("test-mindful.db")}
    @Test fun openEndedFinishPersistsPausedTimeCorrectlyAndOnlyOnceWithoutBell() {
        assertNull(engine.startOpen());assertNotNull(engine.startOpen())
        clock.time=61000;engine.pause();clock.time=210000
        assertNull(engine.resume());clock.time=212000
        assertNull(engine.finishOpen());assertNull(engine.finishOpen())
        val row=engine.store.history().single()
        assertEquals(62000L,row.duration);assertEquals(MINDFUL_OPEN,row.kind);assertEquals(0,bells)
        assertEquals(1,statistics(listOf(row),row.date).current)
    }
    @Test fun timedMindfulCompletionUsesNormalAlarmHandlerAndEarlyEndDoesNotCount() {
        val s=Session("timed",120000,120000,121000,7,kind=MINDFUL_TIMED)
        engine.store.save(s);clock.time=120000;engine.finish(s.id,0)
        assertTrue(engine.store.history().isEmpty())
        clock.time=121000;engine.finish(s.id,0);engine.finish(s.id,0)
        assertEquals(MINDFUL_TIMED,engine.store.history().single().kind);assertEquals(1,bells)
        engine.store.save(s.copy(id="discarded"));engine.end();engine.finish("discarded",0)
        assertEquals(1,engine.store.history().size)
    }
    @Test fun rebootDiscardsOpenSessionWithoutCredit() {
        engine.startOpen();clock.time=61000;clock.bootId=8;engine.recover()
        assertNull(engine.store.active());assertTrue(engine.store.history().isEmpty())
    }
    @Test fun pausedOpenCannotBeResumedAcrossReboot() {
        engine.startOpen();clock.time=61000;engine.pause();clock.bootId=8
        assertNotNull(engine.resume());assertNull(engine.store.active())
        assertTrue(engine.store.history().isEmpty())
    }
    @Test fun migrationPreservesOldHistoryAndActiveSession() {
        engine.store.close();context.deleteDatabase("test-mindful.db")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("test-mindful.db"),null).use { db ->
            db.execSQL("CREATE TABLE active (slot INTEGER PRIMARY KEY, id TEXT, duration INTEGER, remaining INTEGER, deadline INTEGER, boot INTEGER, state TEXT, test INTEGER, expected INTEGER, generation INTEGER)")
            db.execSQL("CREATE TABLE sessions (id TEXT PRIMARY KEY, millis INTEGER, duration INTEGER, date TEXT, zone TEXT)")
            db.execSQL("CREATE TABLE notes (key TEXT PRIMARY KEY, value TEXT)")
            db.execSQL("INSERT INTO sessions VALUES ('old',1800000000000,60000,'2027-01-15','UTC')")
            db.execSQL("INSERT INTO active VALUES (1,'running',60000,60000,61000,7,'running',0,0,0)")
            db.version=1
        }
        engine=TimerEngine(context,clock,Store(context,"test-mindful.db")) {_,_->}
        assertEquals(MEDITATION,engine.store.history().single().kind)
        assertEquals(MEDITATION,engine.store.active()?.kind)
        assertEquals("running",engine.store.active()?.id)
    }
    @Test fun mindfulPreferencesPersistWeekdaysAndKeepRandomSeed() = runBlocking {
        val original=context.preferences.data.first()
        try {
            context.preferences.edit {it.clear()}
            val store=MindfulPreferences(context)
            assertFalse(store.flow.first().enabled)
            store.save(MindfulConfig(enabled=true,days=setOf(1,2),activity="open",nudges=3))
            val saved=store.flow.first()
            assertEquals(setOf(1,2),MindfulPreferences(context).flow.first().days)
            assertNotEquals(0L,saved.seed)
            store.save(saved.copy(durationMinutes=3))
            assertEquals(saved.seed,store.flow.first().seed)
        } finally {context.preferences.updateData {original}}
    }
}
