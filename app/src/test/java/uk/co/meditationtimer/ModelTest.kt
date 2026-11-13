package uk.co.meditationtimer

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ModelTest {
    private val session=Session("id",60000,60000,61000,3,expectedWall=100000)
    private fun row(day: String,id: String=day, duration: Long=600000)=Completed(id,0,duration,LocalDate.parse(day),"Europe/London")
    @Test fun countdownUsesMonotonicDeadline() { assertEquals(45000L,session.left(16000)); assertEquals(0L,session.left(70000)) }
    @Test fun pauseFreezesRemainingTime() { val paused=session.pause(21000); assertEquals(40000L,paused.left(90000));assertFalse(paused.due(90000,3,"id",1)) }
    @Test fun resumeExcludesPausedTime() { val resumed=session.pause(21000).resume(90000,1000000); assertEquals(130000L,resumed.deadline);assertEquals(1040000L,resumed.expectedWall);assertEquals(40000L,resumed.left(90000)) }
    @Test fun completionRequiresFullDuration() { assertFalse(session.due(60999,3,"id",0));assertTrue(session.due(61000,3,"id",0)) }
    @Test fun staleCallbackAfterPauseResumeRejected() { val resumed=session.pause(21000).resume(30000,100000);assertFalse(resumed.due(100000,3,"id",0));assertTrue(resumed.due(100000,3,"id",2)) }
    @Test fun otherSessionCallbackRejected() { assertFalse(session.due(99999,3,"cancelled-id",0)) }
    @Test fun recoveryRequiresSameBoot() { assertFalse(session.due(99999,4,"id",0));assertTrue(session.copy().due(99999,3,"id",0)) }
    @Test fun persistedPausedStateCanResume() { val paused=session.pause(11000).copy();assertEquals(50000L,paused.resume(99000,200000).left(99000)) }
    @Test fun emptyHistoryHasZeroStats() { assertEquals(Stats(0,0,0,0,List(7){0L}),statistics(emptyList(),LocalDate.parse("2026-11-13"))) }
    @Test fun yesterdayKeepsCurrentStreak() { val s=statistics(listOf(row("2026-11-11"),row("2026-11-12")),LocalDate.parse("2026-11-13"));assertEquals(2,s.current);assertEquals(2,s.longest) }
    @Test fun todayExtendsStreakAndDuplicateDayCountsOnce() { val s=statistics(listOf(row("2026-11-12"),row("2026-11-13","a"),row("2026-11-13","b")),LocalDate.parse("2026-11-13"));assertEquals(2,s.current);assertEquals(3,s.count);assertEquals(30L,s.minutes) }
    @Test fun missedDayClearsCurrentButNotLongest() { val s=statistics(listOf(row("2026-11-09"),row("2026-11-10")),LocalDate.parse("2026-11-13"));assertEquals(0,s.current);assertEquals(2,s.longest) }
    @Test fun deletionRecalculatesStreaks() { val rows=listOf(row("2026-11-11"),row("2026-11-12"),row("2026-11-13"));assertEquals(3,statistics(rows,LocalDate.parse("2026-11-13")).current);assertEquals(1,statistics(rows.filter {it.date.dayOfMonth!=12},LocalDate.parse("2026-11-13")).current) }
    @Test fun sevenDaysIncludesTodayAndExcludesEighthDay() { val s=statistics(listOf(row("2026-11-06"),row("2026-11-07"),row("2026-11-13")),LocalDate.parse("2026-11-13"));assertEquals(20L,s.week.sum());assertEquals(10L,s.week.first());assertEquals(10L,s.week.last()) }
    @Test fun storedLocalCompletionDateSurvivesZoneChanges() { val instant=Instant.parse("2026-11-13T00:15:00Z");val date=instant.atZone(ZoneId.of("Europe/London")).toLocalDate();assertNotEquals(date,instant.atZone(ZoneId.of("America/Los_Angeles")).toLocalDate());assertEquals(1,statistics(listOf(Completed("id",instant.toEpochMilli(),60000,date,"Europe/London")),date).current) }
    @Test fun midnightCompletionBelongsToNewDay() { val completion=ZonedDateTime.of(2026,11,13,0,0,1,0,ZoneId.of("Europe/London"));assertEquals(LocalDate.of(2026,11,13),completion.toLocalDate()) }
    @Test fun reminderSchedulesTomorrowAfterChosenTime() { val now=ZonedDateTime.parse("2026-11-13T10:00:00Z[Europe/London]");assertEquals(LocalDate.of(2026,11,14),nextReminder(now,9,0).toLocalDate()) }
    @Test fun reminderSchedulesTodayBeforeChosenTime() { val now=ZonedDateTime.parse("2026-11-13T08:00:00Z[Europe/London]");assertEquals(1L,Duration.between(now,nextReminder(now,9,0)).toHours()) }
    @Test fun reminderResolvesDstGapAndOverlap() {
        val zone=ZoneId.of("Europe/London")
        val spring=LocalDate.of(2026,3,29).atStartOfDay(zone)
        assertTrue(nextReminder(spring,1,30).isAfter(spring));assertEquals(2,nextReminder(spring,1,30).hour)
        val autumn=LocalDate.of(2026,10,25).atStartOfDay(zone)
        assertEquals(1,nextReminder(autumn,1,30).hour)
    }
    @Test fun reminderUsesCurrentZone() { val now=Instant.parse("2026-11-13T08:00:00Z");val london=nextReminder(now.atZone(ZoneId.of("Europe/London")),9,0);val tokyo=nextReminder(now.atZone(ZoneId.of("Asia/Tokyo")),9,0);assertNotEquals(london.toInstant(),tokyo.toInstant()) }
}
