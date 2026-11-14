package uk.co.meditationtimer

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class MindfulModelTest {
    private val zone=ZoneId.of("Europe/London")
    private val monday=LocalDate.of(2026,11,16)
    private val config=MindfulConfig(enabled=true,days=setOf(1,2),nudges=4,seed=123)
    @Test fun daysAndDisabledSettingAreRespected() {
        assertEquals(4,mindfulTimes(config,monday,zone).size)
        assertEquals(4,mindfulTimes(config,monday.plusDays(1),zone).size)
        assertTrue(mindfulTimes(config,monday.plusDays(2),zone).isEmpty())
        assertTrue(mindfulTimes(config.copy(enabled=false),monday,zone).isEmpty())
    }
    @Test fun randomScheduleIsStableWellSpacedAndWithinWindow() {
        val times=mindfulTimes(config,monday,zone)
        assertEquals(times,mindfulTimes(config.copy(),monday,zone))
        assertNotEquals(times.map {it.toLocalTime()},mindfulTimes(config,monday.plusDays(1),zone).map {it.toLocalTime()})
        assertTrue(times.all {it.hour in 9..16})
        assertTrue(times.zipWithNext().all {(a,b) -> Duration.between(a,b).toMinutes() >=15})
    }
    @Test fun fixedTimesSortAndNextSkipsPastAndUnselectedDays() {
        val fixed=config.copy(schedule="fixed",fixedTimes=listOf(15*60,10*60))
        assertEquals(listOf(10,15),mindfulTimes(fixed,monday,zone).map {it.hour})
        val now=monday.plusDays(1).atTime(16,0).atZone(zone)
        assertEquals(monday.plusWeeks(1).atTime(10,0).atZone(zone),nextMindful(fixed,now))
    }
    @Test fun staleDuplicateAndOutOfWindowCallbacksAreRejected() {
        val due=mindfulTimes(config,monday,zone).first()
        val stamp=due.toInstant().toEpochMilli()
        assertTrue(mindfulDeliveryAllowed(config,stamp,due,0,0))
        assertFalse(mindfulDeliveryAllowed(config,stamp,due,stamp,0))
        assertFalse(mindfulDeliveryAllowed(config,stamp,due.plusHours(2),0,0))
        assertFalse(mindfulDeliveryAllowed(config,stamp,due.minusMinutes(1),0,0))
        assertFalse(mindfulDeliveryAllowed(config,stamp,due,0,stamp-60000))
        assertFalse(mindfulDeliveryAllowed(config.copy(days=setOf(2)),stamp,due,0,0))
    }
    @Test fun clockZoneAndDstTransitionsHaveValidOrderedCandidates() {
        val everyDay=config.copy(days=(1..7).toSet(),schedule="fixed",fixedTimes=listOf(90,150))
        for(date in listOf(LocalDate.of(2026,3,29),LocalDate.of(2026,10,25))) {
            val times=mindfulTimes(everyDay,date,zone)
            assertEquals(times.distinctBy {it.toInstant()},times)
            assertTrue(times.all {it.toLocalDate()==date})
        }
        val now=monday.atStartOfDay(zone)
        assertNotEquals(nextMindful(config,now)?.toInstant(),nextMindful(config,now.withZoneSameInstant(ZoneId.of("Asia/Tokyo")))?.toInstant())
    }
    @Test fun configurationValidationCatchesEmptyDaysBadTimesAndCrowdedWindows() {
        assertNotNull(config.copy(days=emptySet()).error())
        assertNotNull(config.copy(startMinute=1020,endMinute=540).error())
        assertNotNull(config.copy(nudges=8,startMinute=540,endMinute=600).error())
        assertNotNull(config.copy(schedule="fixed",fixedTimes=listOf(60,60)).error())
        assertNull(config.error())
        assertEquals(listOf(600,870),parseFixedTimes("10:00, 14:30"))
        assertNull(parseFixedTimes("24:00"));assertNull(parseFixedTimes("10:00,10:00"))
    }
    @Test fun openEndedPausesExcludePausedTimeAndRejectAlarmCompletion() {
        val running=Session("open",0,0,0,1,kind=MINDFUL_OPEN,started=1000)
        assertEquals(2000L,running.activeMillis(3000))
        val paused=running.pause(3000)
        assertEquals(2000L,paused.activeMillis(9000))
        val resumed=paused.resume(9000,100000)
        assertEquals(4000L,resumed.activeMillis(11000))
        assertFalse(resumed.due(999999,1,"open",resumed.generation))
    }
    @Test fun mindfulKindsAndShortOpenDurationsRoundTripAndLegacyBackupsStillRead() {
        val date=monday
        val row=Completed("mindful",date.atStartOfDay(zone).toInstant().toEpochMilli(),1500,date,zone.id,MINDFUL_OPEN)
        assertEquals(listOf(row),HistoryBackup.decode(HistoryBackup.encode(listOf(row))))
        val root=org.json.JSONObject(HistoryBackup.encode(listOf(row.copy(duration=60000,kind=MEDITATION)))).put("version",1)
        root.getJSONArray("sessions").getJSONObject(0).remove("kind")
        assertEquals(MEDITATION,HistoryBackup.decode(root.toString()).single().kind)
        assertEquals(1,statistics(listOf(row),date).current)
    }
}
