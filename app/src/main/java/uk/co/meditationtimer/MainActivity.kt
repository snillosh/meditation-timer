package uk.co.meditationtimer

import android.Manifest
import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter

class MeditationModel(app: Application): AndroidViewModel(app) {
    val engine=TimerEngine.get(app)
    var active by mutableStateOf<Session?>(null); private set
    var history by mutableStateOf<List<Completed>>(emptyList()); private set
    var preferences by mutableStateOf(Preferences()); private set
    var diagnostic by mutableStateOf(""); private set
    var message by mutableStateOf(""); private set
    var refreshVersion by mutableLongStateOf(0); private set
    var error by mutableStateOf<String?>(null)
    private val prefs=PreferenceStore(app)
    init {
        viewModelScope.launch { prefs.flow.collect { preferences=it } }

    }
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val s=engine.store.active(); val h=engine.store.history(); val d=engine.store.note("test"); val m=engine.store.note("message")
        withContext(Dispatchers.Main) { refreshVersion++; active=s; history=h; diagnostic=d; message=if(m=="No test run yet.") "" else m }
    }
    fun action(block: () -> String?) { viewModelScope.launch { error=withContext(Dispatchers.IO) { block() }; refresh() } }
    fun save(p: Preferences) { viewModelScope.launch { prefs.save(p); withContext(Dispatchers.IO) { Reminders.schedule(getApplication()) } } }
    fun recover() { action { engine.recover(); null } }
}
class MainActivity: ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[MeditationModel::class.java] }
    private val notificationPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(); Notifications.channels(this)
        setContent {
            val dark=isSystemInDarkTheme()
            val colors=if(dark) darkColorScheme(primary=Color(0xFFACCDBA),onPrimary=Color(0xFF153423),surfaceContainer=Color(0xFF1C271F),background=Color(0xFF101612),surface=Color(0xFF19221C),secondaryContainer=Color(0xFF2C4435),onSecondaryContainer=Color(0xFFD4E8D9))
                else lightColorScheme(primary=Color(0xFF456B55),onPrimary=Color.White,surfaceContainer=Color(0xFFEDF2E8),background=Color(0xFFF5F8F1),surface=Color(0xFFEDF2E8),secondaryContainer=Color(0xFFD9E8DC),onSecondaryContainer=Color(0xFF243D2D))
            MaterialTheme(colorScheme=colors) { Still(model,this) }
        }
    }
    override fun onResume() { super.onResume(); model.recover() }
    fun notifications() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else settings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
    }
    fun settings(intent: Intent) {
        try { startActivity(intent) } catch(_:Exception) {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))) }
            catch(_:Exception) { model.error="This settings page is unavailable on this device." }
        }
    }
}
@Composable fun Still(m: MeditationModel,a: MainActivity) {
    val version=m.refreshVersion
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while(isActive) { m.refresh(); delay(1000) } } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(bottomBar={ NavigationBar {
        listOf("Meditation","History","Settings").forEachIndexed { i,label -> NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("◉","▤","⚙")[i])},label={Text(label)}) }
    } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=24.dp)) {
            Spacer(Modifier.height(24.dp)); Text("Still",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Light)
            Text(listOf("A little space to be.","Every quiet moment counts.","Make room for your practice.")[tab],color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            when(tab) { 0 -> Meditation(m); 1 -> History(m.history) { row -> m.action {m.engine.store.delete(row.id);null} }; else -> PreferencesScreen(m,a) }
        }
    }
    m.error?.let { text -> AlertDialog(onDismissRequest={m.error=null},title={Text("Timer needs attention")},text={Text(text)},confirmButton={TextButton(onClick={m.error=null}){Text("OK")}}) }
}
@Composable fun Meditation(m: MeditationModel) {
    val s=m.active; val left=s?.left(SystemClock.elapsedRealtime()) ?: (m.preferences.minutes*60000L)
    var custom by remember { mutableStateOf(false) }; var end by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0) }
    LaunchedEffect(s) { while(true) { tick=SystemClock.elapsedRealtime(); delay(250) } }
    val remaining=if(s==null) left else s.left(tick)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        Box(Modifier.size(260.dp),contentAlignment=Alignment.Center) {
            CircularProgressIndicator(progress={if(s==null) 1f else remaining.toFloat()/s.duration},modifier=Modifier.fillMaxSize(),strokeWidth=3.dp,trackColor=MaterialTheme.colorScheme.surfaceVariant)
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text("%02d:%02d".format((remaining+999)/1000/60,(remaining+999)/1000%60),fontSize=56.sp,fontWeight=FontWeight.Light)
                Text(when { s?.test==true -> "RELIABILITY TEST"; s?.state=="paused" -> "PAUSED"; s!=null -> "JUST BREATHE"; else -> "MINUTES OF STILLNESS" },style=MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(28.dp))
        if(s==null) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                listOf(5,10,15,20,30).forEach { n -> FilterChip(selected=m.preferences.minutes==n,onClick={m.save(m.preferences.copy(minutes=n))},label={Text("$n")}) }
            }
            TextButton(onClick={custom=true}) { Text("Custom duration · ${m.preferences.minutes} min") }
            if(androidx.compose.ui.platform.LocalContext.current.getSystemService(android.media.AudioManager::class.java).getStreamVolume(android.media.AudioManager.STREAM_ALARM)==0) {
                Text("Alarm volume is muted. Raise it in sound settings to hear completion.",color=MaterialTheme.colorScheme.error)
            }
            Button(onClick={m.action { m.engine.start(m.preferences.minutes*60000L) }},modifier=Modifier.fillMaxWidth().height(56.dp)) { Text("Begin meditation") }
            if(m.message.isNotBlank()) { Spacer(Modifier.height(20.dp)); Text(m.message,style=MaterialTheme.typography.bodyLarge) }
        } else {
            Button(onClick={m.action { if(s.state=="paused") m.engine.resume() else {m.engine.pause(); null} }},modifier=Modifier.fillMaxWidth().height(56.dp)) { Text(if(s.state=="paused") "Resume" else "Pause") }
            TextButton(onClick={end=true}) { Text("End session") }
            Text("Let your screen lock naturally.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
    if(custom) DurationDialog(m.preferences.minutes,{custom=false}) { m.save(m.preferences.copy(minutes=it)); custom=false }
    if(end) AlertDialog(onDismissRequest={end=false},title={Text("End this session?")},text={Text("Incomplete meditation will be discarded and will not count towards your history or streak.")},confirmButton={TextButton(onClick={m.action {m.engine.end();null};end=false}){Text("End session")}},dismissButton={TextButton(onClick={end=false}){Text("Keep meditating")}})
}
@Composable fun DurationDialog(initial: Int,dismiss: ()->Unit,save: (Int)->Unit) {
    var value by remember { mutableStateOf(initial.toString()) }
    AlertDialog(onDismissRequest=dismiss,title={Text("Meditation duration")},text={OutlinedTextField(value=value,onValueChange={value=it.filter(Char::isDigit).take(3)},label={Text("Minutes · 1–120")},singleLine=true)},confirmButton={TextButton(enabled=value.toIntOrNull() in 1..120,onClick={save(value.toInt())}){Text("Save")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}
@Composable fun History(sessions: List<Completed>, onDelete: (Completed) -> Unit) {
    val today=LocalDate.now()
    var selectedDay by rememberSaveable { mutableStateOf(today.toString()) }
    var displayedMonth by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val selectedDate=LocalDate.parse(selectedDay)
    val month=YearMonth.parse(displayedMonth)
    val dailySessions=sessions.filter { it.date==selectedDate }
    val stats=statistics(sessions,today); var deleting by remember { mutableStateOf<Completed?>(null) }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="calendar") {
            HistoryCalendar(month,selectedDate,today,sessions,
                onMonthChange={ next ->
                    displayedMonth=next.toString()
                    selectedDay=next.atDay(minOf(selectedDate.dayOfMonth,next.lengthOfMonth())).toString()
                },
                onSelectDate={ selectedDay=it.toString() })
        }
        item(key="statistics") { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
            Text("Your practice",style=MaterialTheme.typography.titleLarge)
            Text("${stats.count} sessions · ${stats.minutes} minutes",style=MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp)); Text("Current streak: ${stats.current} days\nLongest streak: ${stats.longest} days")
            Spacer(Modifier.height(16.dp)); Text("Last 7 days · ${stats.week.sum()} minutes")
            Row(Modifier.fillMaxWidth().height(115.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.Bottom) {
                stats.week.forEachIndexed { i,value -> Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("$value",style=MaterialTheme.typography.labelSmall)
                    Box(Modifier.width(20.dp).height((value.toFloat()/maxOf(1L,stats.week.max())*65).dp.coerceAtLeast(2.dp)).background(MaterialTheme.colorScheme.primary,RoundedCornerShape(5.dp)))
                    Text(LocalDate.now().minusDays((6-i).toLong()).dayOfWeek.name.take(1),style=MaterialTheme.typography.labelSmall)
                } }
            }
        } } }
        item(key="day-heading") {
            Column(Modifier.padding(top=8.dp)) {
                Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),style=MaterialTheme.typography.titleMedium)
                Text("${dailySessions.size} sessions · ${dailySessions.sumOf { it.duration }/60000} minutes",color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(dailySessions.isEmpty()) item { Text("No sessions this day.",modifier=Modifier.padding(vertical=16.dp)) }
        items(dailySessions,key={it.id}) { row -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(row.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))); Text("${Instant.ofEpochMilli(row.millis).atZone(ZoneId.of(row.zone)).format(DateTimeFormatter.ofPattern("HH:mm"))} · ${row.duration/60000} min",color=MaterialTheme.colorScheme.onSurfaceVariant) }
            TextButton(onClick={deleting=row}) { Text("Delete") }
        } } }
        item { Spacer(Modifier.height(16.dp)) }
    }
    deleting?.let { row -> AlertDialog(onDismissRequest={deleting=null},title={Text("Delete this session?")},text={Text("Statistics and streaks will update from the remaining history.")},confirmButton={TextButton(onClick={onDelete(row);deleting=null}){Text("Delete")}},dismissButton={TextButton(onClick={deleting=null}){Text("Cancel")}}) }
}
@Composable fun Health(title: String,status: String,detail: String,action: String?=null,run: ()->Unit={}) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
        Text(title,style=MaterialTheme.typography.titleMedium); Text(status,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
        Text(detail,style=MaterialTheme.typography.bodyMedium)
        if(action!=null) TextButton(onClick=run) { Text(action) }
    } }
}
@Composable fun PreferencesScreen(m: MeditationModel,a: MainActivity) {
    var duration by remember { mutableStateOf(false) }; var time by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf(false) }
    val p=m.preferences
    val refresh=m.refreshVersion
    // Re-evaluated on the ViewModel's foreground refresh, including return from Settings.
    val nm=a.getSystemService(NotificationManager::class.java)
    val notices=nm.areNotificationsEnabled()
    val exact=m.engine.exactAccess()
    val reminderChannelEnabled=nm.getNotificationChannel(Notifications.REMINDER)?.importance!=NotificationManager.IMPORTANCE_NONE
    val power=a.getSystemService(PowerManager::class.java)
    val exempt=power.isIgnoringBatteryOptimizations(a.packageName)
    val restricted=a.getSystemService(ActivityManager::class.java).isBackgroundRestricted
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Meditation preferences",style=MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick={duration=true},modifier=Modifier.fillMaxWidth()) { Text("Default duration · ${p.minutes} min") }
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Daily reminder",Modifier.weight(1f)); Switch(checked=p.reminder,onCheckedChange={m.save(p.copy(reminder=it))}) }
        OutlinedButton(onClick={time=true},modifier=Modifier.fillMaxWidth()) { Text("Reminder time · %02d:%02d".format(p.hour,p.minute)) }
        Text("Timer Health",style=MaterialTheme.typography.titleLarge)
        Health("Exact timer alarms",if(exact) "Ready · Required" else "Needs attention · Required","One exact wake-up alarm delivers completion while locked. Access is required to start or resume.","Alarms & reminders") {
            a.settings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${a.packageName}")))
        }
        Health("Notifications",if(notices) "Ready · Recommended" else "Recommended · Disabled","Notifications show the active timer and reminders. Bell playback is separate; a blocked bell service notification does not itself mute audio.","Notification settings") { a.notifications() }
        listOf(Notifications.ACTIVE,Notifications.BELL,Notifications.REMINDER).forEach { id ->
            val channel=nm.getNotificationChannel(id)
            Health("Channel · ${channel?.name ?: id}",if(channel!=null && channel.importance!=NotificationManager.IMPORTANCE_NONE) "Ready · Recommended" else "Recommended · Disabled","Channel visibility is controlled by Android.","Channel settings") {
                a.settings(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,id))
            }
        }
        Health("Battery behaviour",when { restricted -> "Needs attention · Restricted"; exempt -> "Ready · Optimisation exemption verified"; else -> "Recommended · Normal optimisation applies" },
            "Restricted can delay background work. Optimised uses normal Doze limits. Unrestricted can improve delivery. Android does not expose the precise selected battery mode; an exemption alone does not prove Unrestricted. Inspect app battery settings if delivery is delayed.","App battery settings") {
            a.settings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${a.packageName}")))
        }
        val service=a.packageManager.getServiceInfo(android.content.ComponentName(a,BellService::class.java),0)
        Health("Bell foreground service",if(service.foregroundServiceType and android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK!=0) "Ready · Informational" else "Needs attention","Only runs during the short bell. No continuously running timer service and no user-grantable service permission screen.")
        val resourcesReady=remember { runCatching { listOf(R.raw.start_bell,R.raw.completion_bell).forEach { id -> a.resources.openRawResourceFd(id).use { check(it.length>0) } }; true }.getOrDefault(false) }
        Health("Bell audio",if(resourcesReady) "Ready · Required" else "Needs attention · Required","Local placeholder bells are available. Completion plays once using alarm volume, including silent/vibrate. DND must allow alarms. The start bell still respects silent mode. ${m.engine.store.note("audio").takeUnless { it=="No test run yet." } ?: "Test to check loading and playback."}","Test bell") { m.action { try {BellService.start(a,true,null);null} catch(e:Exception) {e.message} } }
        val alarmVolume=a.getSystemService(android.media.AudioManager::class.java).getStreamVolume(android.media.AudioManager.STREAM_ALARM)
        Health("Alarm volume",if(alarmVolume==0) "Needs attention · Muted" else "Ready · $alarmVolume",
            "Completion and test bells use your alarm volume. The app never changes it. A zero alarm volume will silence the bell.","Sound settings") {
            a.settings(Intent(Settings.ACTION_SOUND_SETTINGS))
        }
        val dndAllowsAlarms=BellAudio.alarmsAllowed(nm)
        Health("Do Not Disturb",when(dndAllowsAlarms) { true -> "Ready · Alarms currently allowed"; false -> "Needs attention · Alarms blocked"; null -> "Recommended · Check alarm exceptions" },
            "Allow alarms in each DND mode you use. Future scheduled modes may apply different rules. The app does not bypass DND or request permission to change it.","Do Not Disturb settings") {
            a.settings(Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS))
        }
        Health("Reminder",if(!p.reminder) "Not applicable · Disabled" else if(!notices || !reminderChannelEnabled) "Needs attention" else "Ready · Optional",if(p.reminder) "Daily at %02d:%02d local time. Delivery may be delayed. ${if(!notices || !reminderChannelEnabled) "Notification permission or reminder channel is disabled." else "Notifications enabled."}".format(p.hour,p.minute) else "Disabled by default. No exact access needed.")
        val s=m.active
        Health("Active session","Ready · Informational",s?.let { if(it.state=="paused") "Paused · ${it.remaining/1000} seconds remain." else "Running · expected ${java.text.DateFormat.getTimeInstance().format(java.util.Date(m.engine.clock.wall()+it.left(m.engine.clock.elapsed())))}" } ?: "No active session.")
        Health("60-second reliability test","Informational","Uses the same alarm and bell as meditation. Lock the phone and return after the bell. Tests never count towards history.","Test Timer Reliability") { test=true }
        Health("Last test result","Informational",m.diagnostic+"\nOnly a real device test can establish whether you heard the bell.")
        Spacer(Modifier.height(24.dp))
    }
    if(duration) DurationDialog(p.minutes,{duration=false}) { m.save(p.copy(minutes=it));duration=false }
    if(time) {
        DisposableEffect(Unit) {
            val dialog=TimePickerDialog(a,{_,h,min -> m.save(p.copy(hour=h,minute=min));time=false},p.hour,p.minute,true)
            dialog.setOnDismissListener {time=false};dialog.show();onDispose {dialog.dismiss()}
        }
    }
    if(test) AlertDialog(onDismissRequest={test=false},title={Text("Test locked-screen delivery")},text={Text("Start a 60-second test, then lock your phone. Return to Timer Health to inspect callback and audio diagnostics. End any current session first.")},confirmButton={TextButton(onClick={m.action {m.engine.start(60000,true)};test=false}){Text("Start test")}},dismissButton={TextButton(onClick={test=false}){Text("Cancel")}})
}
