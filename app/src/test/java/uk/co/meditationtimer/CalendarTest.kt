package uk.co.meditationtimer

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class CalendarTest {
    @Test fun monthBeginsInCorrectMondayFirstColumn() {
        val weeks=calendarWeeks(YearMonth.of(2026,11))
        assertTrue(weeks.first().take(6).all { it==null })
        assertEquals(LocalDate.of(2026,11,1),weeks.first().last())
        assertTrue(weeks.all { it.size==7 })
        assertEquals((1..30).toList(),weeks.flatten().filterNotNull().map { it.dayOfMonth })
    }
    @Test fun leapDayAndSixWeekMonthAreNotTruncated() {
        assertEquals(29,calendarWeeks(YearMonth.of(2028,2)).flatten().filterNotNull().size)
        val weeks=calendarWeeks(YearMonth.of(2026,3))
        assertEquals(6,weeks.size)
        assertEquals(LocalDate.of(2026,3,31),weeks.flatten().filterNotNull().last())
    }
    @Test fun mondayStartDoesNotAddEmptyWeek() {
        val weeks=calendarWeeks(YearMonth.of(2021,2))
        assertEquals(4,weeks.size)
        assertFalse(weeks.flatten().any { it==null })
    }
}
