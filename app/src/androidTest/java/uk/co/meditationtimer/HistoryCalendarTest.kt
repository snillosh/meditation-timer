package uk.co.meditationtimer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class HistoryCalendarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dailyTotalsSelectionNavigationAndToday() {
        val today=LocalDate.of(2026,11,13)
        val rows=listOf(
            Completed("one",0,600000,today,"Europe/London"),
            Completed("two",0,300000,today,"Europe/London"),
            Completed("other",0,120000,today.minusDays(1),"Europe/London")
        )
        compose.setContent {
            var month by remember { mutableStateOf(YearMonth.from(today)) }
            var day by remember { mutableStateOf(today) }
            MaterialTheme {
                HistoryCalendar(month,day,today,rows,{ month=it; day=it.atDay(minOf(day.dayOfMonth,it.lengthOfMonth())) },{day=it})
            }
        }
        compose.onNodeWithContentDescription("Friday, 13 November 2026, 15 minutes meditated, Today").assertIsSelected()
        compose.onNodeWithContentDescription("Thursday, 12 November 2026, 2 minutes meditated").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Wednesday, 11 November 2026, No sessions").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Previous month").performClick()
        compose.onNodeWithText("October 2026").assertExists()
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithText("November 2026").assertExists()
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithContentDescription("Friday, 13 November 2026, 15 minutes meditated, Today").assertIsSelected()
    }

    @Test fun historyShowsOnlySelectedDayAndEmptyState() {
        val today=LocalDate.now()
        val yesterday=today.minusDays(1)
        val rows=listOf(
            Completed("today",today.atTime(12,0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli(),600000,today,"UTC"),
            Completed("yesterday",yesterday.atTime(9,0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli(),300000,yesterday,"UTC")
        )
        compose.setContent { MaterialTheme { History(rows) {} } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("12:00 · 10 min"))
        compose.onNodeWithText("12:00 · 10 min").assertExists()
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        // Dates in a different month are selected by navigating to that month first.
        if(java.time.YearMonth.from(yesterday)!=java.time.YearMonth.from(today)) {
            compose.onNodeWithContentDescription("Previous month").performClick()
        }
        val description=yesterday.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))+", 5 minutes meditated"
        compose.onNodeWithContentDescription(description).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("09:00 · 5 min"))
        compose.onNodeWithText("09:00 · 5 min").assertExists()
        compose.onNodeWithText("12:00 · 10 min").assertDoesNotExist()
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        val empty=(1..java.time.YearMonth.from(yesterday).lengthOfMonth()).map { yesterday.withDayOfMonth(it) }.first { it!=today && it!=yesterday }
        compose.onNodeWithContentDescription(empty.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))+", No sessions"+(if(empty==today) ", Today" else "")).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("No sessions this day."))
        compose.onNodeWithText("No sessions this day.").assertExists()
    }
}
