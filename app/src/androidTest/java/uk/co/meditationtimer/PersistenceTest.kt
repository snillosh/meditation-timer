package uk.co.meditationtimer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: Store
    @Before fun before() { context.deleteDatabase("still.db");store=Store(context) }
    @After fun after() { store.close();context.deleteDatabase("still.db") }
    private fun session(test: Boolean=false)=Session("fixed-id",60000,60000,61000,2,test=test)
    @Test fun historyPersistsAndCompletionIsExactlyOnce() {
        val s=session();store.save(s);assertTrue(store.complete(s,1800000000000));assertFalse(store.complete(s,1800000000001))
        store.close();store=Store(context);assertEquals(1,store.history().size);assertNull(store.active())
        store.delete(s.id);assertTrue(store.history().isEmpty())
    }
    @Test fun earlyTerminationAndStaleCallbacksCannotComplete() { val s=session();store.save(s);store.clear();assertFalse(store.complete(s,1800000000000));assertTrue(store.history().isEmpty()) }
    @Test fun pausedRevisionRejectsOldCompletion() { val s=session();store.save(s.pause(10000));assertFalse(store.complete(s,1800000000000)) }
    @Test fun diagnosticSessionsDoNotEnterHistory() { val s=session(true);store.save(s);assertTrue(store.complete(s,1800000000000));assertTrue(store.history().isEmpty()) }
    @Test fun completionDateUsesLocalMidnightAndSurvivesZoneChange() {
        val original=java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"))
            val s=session();store.save(s)
            store.complete(s,java.time.Instant.parse("2026-11-12T15:00:01Z").toEpochMilli())
            assertEquals(java.time.LocalDate.of(2026,11,13),store.history().single().date)
            assertEquals("Asia/Tokyo",store.history().single().zone)
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(java.time.LocalDate.of(2026,11,13),store.history().single().date)
        } finally { java.util.TimeZone.setDefault(original) }
    }
    @Test fun historyImportMergesAndSkipsDuplicatesAcrossReopen() {
        val row=Completed("imported",1800000000000,60000,java.time.LocalDate.of(2027,1,15),"UTC")
        val backup=HistoryBackup.encode(listOf(row))
        assertEquals(ImportResult(1,1),store.importHistory(HistoryBackup.decode(HistoryBackup.encode(listOf(row,row)))))
        store.close();store=Store(context)
        assertEquals(ImportResult(0,1),store.importHistory(HistoryBackup.decode(backup)))
        assertEquals(listOf(row),store.history())
    }
    @Test fun conflictingImportRollsBackAllRowsAndPreservesActiveTimer() {
        val row=Completed("existing",1800000000000,60000,java.time.LocalDate.of(2027,1,15),"UTC")
        store.importHistory(listOf(row))
        val active=session();store.save(active)
        assertThrows(IllegalArgumentException::class.java) {
            store.importHistory(listOf(row.copy(id="new"),row.copy(duration=120000)))
        }
        assertEquals(listOf(row),store.history());assertEquals(active,store.active())
        assertThrows(IllegalArgumentException::class.java) { store.importHistory(listOf(row.copy(id=active.id))) }
        assertEquals(listOf(row),store.history());assertEquals(active,store.active())
    }
    @Test fun activeStateSurvivesReopen() { val s=session().pause(5000);store.save(s);store.close();store=Store(context);assertEquals(s,store.active()) }
}

@RunWith(AndroidJUnit4::class)
class EngineTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var engine: TimerEngine
    private class FakeClock: Clock {
        var time=1000L; var bootId=7
        override fun elapsed()=time
        override fun wall()=1800000000000L+time
        override fun boot()=bootId
    }
    private lateinit var clock: FakeClock
    @Before fun before() { context.deleteDatabase("still.db");clock=FakeClock();engine=TimerEngine(context,clock) { _, _ -> } }
    @After fun after() { engine.end();engine.store.close();context.deleteDatabase("still.db") }
    private fun running()=Session("engine-id",60000,60000,61000,7)
    @Test fun completionRequiresFullActiveTimeAndIsIdempotent() {
        val s=running();engine.store.save(s);engine.finish(s.id,0);assertTrue(engine.store.history().isEmpty())
        clock.time=61000;engine.finish(s.id,0);engine.finish(s.id,0);assertEquals(1,engine.store.history().size)
    }
    @Test fun cancelledSessionCannotComplete() { val s=running();engine.store.save(s);engine.end();clock.time=90000;engine.finish(s.id,0);assertTrue(engine.store.history().isEmpty()) }
    @Test fun pausedSessionCannotCompleteFromStaleAlarm() { val s=running();engine.store.save(s);engine.pause();clock.time=90000;engine.finish(s.id,0);assertTrue(engine.store.history().isEmpty());assertEquals("paused",engine.store.active()?.state) }
    @Test fun recoveryAfterRebootDiscardsWithoutCredit() { engine.store.save(running());clock.bootId=8;engine.recover();assertNull(engine.store.active());assertTrue(engine.store.history().isEmpty()) }
}
