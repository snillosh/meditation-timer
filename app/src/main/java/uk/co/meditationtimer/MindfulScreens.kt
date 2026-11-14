package uk.co.meditationtimer

import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MindfulSettingsScreen(initial: MindfulConfig, notificationsReady: Boolean, onBack: () -> Unit,
                          openNotifications: () -> Unit, onSave: (MindfulConfig) -> Unit) {
    var enabled by rememberSaveable(initial) { mutableStateOf(initial.enabled) }
    var activity by rememberSaveable(initial) { mutableStateOf(initial.activity) }
    var duration by rememberSaveable(initial) { mutableStateOf(initial.durationMinutes.toString()) }
    var schedule by rememberSaveable(initial) { mutableStateOf(initial.schedule) }
    var days by rememberSaveable(initial) { mutableStateOf(initial.days.sorted().joinToString(",")) }
    var times by rememberSaveable(initial) { mutableStateOf(initial.fixedTimes.joinToString(", ",transform=::minuteLabel)) }
    var start by rememberSaveable(initial) { mutableIntStateOf(initial.startMinute) }
    var end by rememberSaveable(initial) { mutableIntStateOf(initial.endMinute) }
    var count by rememberSaveable(initial) { mutableIntStateOf(initial.nudges) }
    val selectedDays=days.split(',').mapNotNull {it.toIntOrNull()}.toSet()
    val fixed=parseFixedTimes(times)
    val config=initial.copy(enabled=enabled,activity=activity,durationMinutes=duration.toIntOrNull() ?: 0,
        schedule=schedule,days=selectedDays,fixedTimes=fixed ?: emptyList(),startMinute=start,endMinute=end,nudges=count)
    val error=config.error()
    val context=LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        TextButton(onClick=onBack) { Text("‹ Back to Settings") }
        Text("Mindful Minutes",style=MaterialTheme.typography.headlineSmall)
        Text("A gentle invitation to pause. Nothing starts until you choose to begin.")
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Enable mindful nudges",Modifier.weight(1f))
            Switch(enabled,onCheckedChange={enabled=it},modifier=Modifier.semantics {contentDescription="Enable mindful nudges"})
        }
        Text("Activity",style=MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("timed" to "Timed pause","open" to "Open-ended","prompt" to "Prompt only").forEach { (key,label) ->
                FilterChip(activity==key,onClick={activity=key},label={Text(label)})
            }
        }
        when(activity) {
            "timed" -> OutlinedTextField(duration,{duration=it.filter(Char::isDigit).take(3)},label={Text("Duration · 1–120 minutes")},singleLine=true)
            "open" -> Text("Count up until you tap Finish. Paused time is excluded. Finish saves history and streak credit, with no completion bell.")
            else -> Text("A short prompt and a Done button. No timer, bell, history or streak credit.")
        }
        Text("Days",style=MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            (1..7).forEach { day ->
                val name=DayOfWeek.of(day)
                FilterChip(day in selectedDays,onClick={days=(if(day in selectedDays) selectedDays-day else selectedDays+day).sorted().joinToString(",")},
                    modifier=Modifier.semantics {contentDescription=name.getDisplayName(TextStyle.FULL,Locale.getDefault())},
                    label={Text(name.getDisplayName(TextStyle.SHORT,Locale.getDefault()))})
            }
        }
        Text("Schedule",style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(schedule=="fixed",onClick={schedule="fixed"},label={Text("Fixed times")})
            FilterChip(schedule=="random",onClick={schedule="random"},label={Text("Random")})
        }
        if(schedule=="fixed") {
            OutlinedTextField(times,{times=it},label={Text("Fixed times")},supportingText={Text("1–8 times in 24-hour format, separated by commas. Example: 10:00, 14:30")})
        } else {
            Text("$count nudges per day")
            Slider(count.toFloat(),{count=it.toInt()},valueRange=1f..8f,steps=6,
                modifier=Modifier.semantics {contentDescription="Nudges per day"})
            OutlinedButton(onClick={TimePickerDialog(context,{_,h,m -> start=h*60+m},start/60,start%60,true).show()},modifier=Modifier.fillMaxWidth()) {Text("Window starts · ${minuteLabel(start)}")}
            OutlinedButton(onClick={TimePickerDialog(context,{_,h,m -> end=h*60+m},end/60,end%60,true).show()},modifier=Modifier.fillMaxWidth()) {Text("Window ends · ${minuteLabel(end)}")}
            Text("Same-day window; allow at least 30 minutes per nudge. Nudges are spread out internally. Android may delay or skip reminders; they never catch up in a burst.")
        }
        if(!notificationsReady) {
            Text("Notifications or the Mindful Minutes channel are disabled.",color=MaterialTheme.colorScheme.error)
            TextButton(onClick=openNotifications) {Text("Notification settings")}
        }
        Text("Nudges respect silent mode and Do Not Disturb. Completed timed and open-ended pauses count towards shared history and streaks. Ignoring a nudge has no penalty.")
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Button(onClick={onSave(config)},enabled=error==null,modifier=Modifier.fillMaxWidth()) {Text("Save Mindful Minutes")}
        TextButton(onClick=onBack) {Text("Cancel")}
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun MindfulInvitation(config: MindfulConfig, busy: Boolean, begin: () -> Unit, close: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical=24.dp),
        verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text("A moment to be present",style=MaterialTheme.typography.headlineMedium)
        Text("Pause for a moment. Relax your shoulders and take a few deliberate breaths. Notice what you can hear and feel around you.",style=MaterialTheme.typography.bodyLarge)
        if(config.activity=="prompt") {
            Text("There is nothing to achieve here. Stay for as long as feels right.")
            Button(onClick=close,modifier=Modifier.fillMaxWidth()) {Text("Done")}
        } else {
            Text(if(config.activity=="timed") "${config.durationMinutes} mindful minutes, ending with one bell." else "An open-ended pause. Tap Finish when you are ready; no completion bell.")
            if(busy) Text("You already have a session in progress. Return to it before starting another pause.")
            Button(onClick=begin,enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text("Begin mindful pause")}
            TextButton(onClick=close) {Text(if(busy) "Return to session" else "Not now")}
        }
    }
}
