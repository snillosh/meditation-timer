package uk.co.meditationtimer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun HistoryCalendar(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    sessions: List<Completed>,
    onMonthChange: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit
) {
    val totals = sessions.groupBy { it.date }.mapValues { (_, rows) -> rows.sumOf { it.duration } / 60000 }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onMonthChange(month.minusMonths(1)) },
                    modifier = Modifier.semantics { contentDescription = "Previous month" }
                ) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium)
                TextButton(
                    onClick = { onMonthChange(month.plusMonths(1)) },
                    modifier = Modifier.semantics { contentDescription = "Next month" }
                ) { Text("›", style = MaterialTheme.typography.headlineSmall) }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            calendarWeeks(month).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { date ->
                        if (date == null) {
                            Spacer(Modifier.weight(1f).height(64.dp))
                        } else {
                            val isSelected = date == selectedDate
                            val minutes = totals[date]
                            val foreground = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            Surface(
                                onClick = { onSelectDate(date) },
                                modifier = Modifier.weight(1f).heightIn(min = 64.dp).semantics {
                                    selected = isSelected
                                    contentDescription = "${date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))}, " +
                                        (minutes?.let { "$it minutes meditated" } ?: "No sessions") +
                                        if (date == today) ", Today" else ""
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                border = if (date == today && !isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${date.dayOfMonth}", color = foreground,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected || date == today) FontWeight.Bold else FontWeight.Normal)
                                    if (minutes != null) {
                                        Box(Modifier.size(4.dp).background(foreground, CircleShape))
                                        Text("${minutes}m", color = foreground, style = MaterialTheme.typography.labelSmall)
                                    } else Spacer(Modifier.height(20.dp))
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("●  Completed minutes", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onMonthChange(YearMonth.from(today)); onSelectDate(today) }) { Text("Today") }
            }
        }
    }
}
