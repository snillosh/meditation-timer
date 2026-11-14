package uk.co.meditationtimer

import android.Manifest
import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onEach
import java.time.*
import java.time.format.DateTimeFormatter

class MeditationModel(app: Application): AndroidViewModel(app) {
    val engine=TimerEngine.get(app)
    var mindful by mutableStateOf<MindfulConfig?>(null); private set
    var invitation by mutableStateOf(false)
    fun saveMindful(config: MindfulConfig, done: () -> Unit) {
        viewModelScope.launch {
            try {
                MindfulPreferences(getApplication()).save(config)
                withContext(Dispatchers.IO) { MindfulReminders.schedule(getApplication()) }
                done()
            } catch(e:Exception) { if(e is CancellationException) throw e;error=e.message ?: "Unable to save Mindful Minutes." }
        }
    }
    fun beginMindful() {
        val config=mindful ?: return
        viewModelScope.launch {
            error=withContext(Dispatchers.IO) {
                if(config.activity=="open") engine.startOpen() else engine.start(config.durationMinutes*60000L,kind=MINDFUL_TIMED)
            }
            if(error==null) invitation=false
            refresh()
        }
    }
    var quote by mutableStateOf<Quote?>(null); private set
    private val zenQuotes=ZenQuotes()
    suspend fun observeQuotes() {
        try {
            quoteConnectivity(getApplication()).onEach { online -> if(!online) quote=null }
                .collectLatest { online -> quote=if(online) zenQuotes.load() else null }
        } finally { quote=null }
    }
    var active by mutableStateOf<Session?>(null); private set
    var history by mutableStateOf<List<Completed>>(emptyList()); private set
    var preferences by mutableStateOf(Preferences()); private set
    var diagnostic by mutableStateOf(""); private set
    var message by mutableStateOf(""); private set
    var refreshVersion by mutableLongStateOf(0); private set
    var error by mutableStateOf<String?>(null)
    var transferBusy by mutableStateOf(false); private set
    var transferMessage by mutableStateOf<String?>(null)
    var pendingImport by mutableStateOf<List<Completed>?>(null)
    private val prefs=PreferenceStore(app)
    private fun transfer(block: () -> String) {
        if(transferBusy) return
        transferBusy=true
        viewModelScope.launch {
            try { transferMessage=withContext(Dispatchers.IO) { block() }; refresh() }
            catch(e: Exception) {
                if(e is CancellationException) throw e
                transferMessage=e.message ?: "Unable to access the selected file. Please try another location."
            } finally { transferBusy=false }
        }
    }
    fun exportHistory(uri: Uri) = transfer {
        val rows=engine.store.history()
        val json=HistoryBackup.encode(rows)
        val resolver=getApplication<Application>().contentResolver
        (resolver.openOutputStream(uri,"wt") ?: error("Unable to write to this file.")).bufferedWriter(Charsets.UTF_8).use { it.write(json) }
        "Exported ${rows.size} sessions."
    }
    fun readImport(uri: Uri) {
        if(transferBusy) return
        transferBusy=true
        viewModelScope.launch {
            try {
                pendingImport=withContext(Dispatchers.IO) {
                    val resolver=getApplication<Application>().contentResolver
                    (resolver.openInputStream(uri) ?: error("Unable to open this file.")).use { HistoryBackup.read(it) }
                }
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                transferMessage=e.message ?: "Unable to read this backup."
            } finally { transferBusy=false }
        }
    }
    fun confirmImport() {
        val rows=pendingImport ?: return
        pendingImport=null
        transfer {
            val result=engine.store.importHistory(rows)
            "Imported ${result.added} sessions; skipped ${result.skipped} duplicates."
        }
    }
    init {
        viewModelScope.launch { prefs.flow.collect { preferences=it } }
        viewModelScope.launch { MindfulPreferences(app).flow.collect { mindful=it } }

    }
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val s=engine.store.active(); val h=engine.store.history(); val d=engine.store.note("test"); val m=engine.store.note("message")
        withContext(Dispatchers.Main) { refreshVersion++; active=s; history=h; diagnostic=d; message=if(m=="No test run yet.") "" else m }
    }
    fun action(block: () -> String?) { viewModelScope.launch { error=withContext(Dispatchers.IO) { block() }; refresh() } }
    fun save(p: Preferences) { viewModelScope.launch { prefs.save(p); withContext(Dispatchers.IO) { Reminders.schedule(getApplication()) } } }
    fun recover() {
        action { engine.recover(); null }
        viewModelScope.launch(Dispatchers.IO) { MindfulReminders.schedule(getApplication()) }
    }
}
class MainActivity: ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[MeditationModel::class.java] }
    private val exportDocument=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) model.exportHistory(uri)
    }
    private val importDocument=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) model.readImport(uri)
    }
    fun exportHistory() {
        try { exportDocument.launch("still-history-${LocalDate.now()}.json") }
        catch(_:Exception) { model.transferMessage="No document picker is available on this device." }
    }
    fun importHistory() {
        try { importDocument.launch(arrayOf("application/json","text/plain","application/octet-stream")) }
        catch(_:Exception) { model.transferMessage="No document picker is available on this device." }
    }
    private val notificationPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(); Notifications.channels(this)
        if(intent.action=="mindful_invitation" && savedInstanceState==null) model.invitation=true
        setContent {
            val dark=isSystemInDarkTheme()
            val colors=if(dark) darkColorScheme(primary=Color(0xFFACCDBA),onPrimary=Color(0xFF153423),surfaceContainer=Color(0xFF1C271F),background=Color(0xFF101612),surface=Color(0xFF19221C),secondaryContainer=Color(0xFF2C4435),onSecondaryContainer=Color(0xFFD4E8D9))
                else lightColorScheme(primary=Color(0xFF456B55),onPrimary=Color.White,surfaceContainer=Color(0xFFEDF2E8),background=Color(0xFFF5F8F1),surface=Color(0xFFEDF2E8),secondaryContainer=Color(0xFFD9E8DC),onSecondaryContainer=Color(0xFF243D2D))
            MaterialTheme(colorScheme=colors) { Still(model,this) }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if(intent.action=="mindful_invitation") model.invitation=true
    }
    override fun onResume() { super.onResume(); model.recover() }
    fun quoteAttribution() {
        try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(ZenQuotes.ATTRIBUTION_URL))) }
        catch(_:Exception) { /* No browser: leave the meditation screen undisturbed. */ }
    }
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
        listOf("Meditation","History","Settings").forEachIndexed { i,label -> NavigationBarItem(selected=tab==i,onClick={tab=i;m.invitation=false},icon={Text(listOf("◉","▤","⚙")[i])},label={Text(label)}) }
    } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=24.dp)) {
            Spacer(Modifier.height(24.dp)); Text("Still",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Light)
            Text(listOf("A little space to be.","Every quiet moment counts.","Make room for your practice.")[tab],color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            if(m.invitation && m.mindful!=null) {
                BackHandler {m.invitation=false}
                MindfulInvitation(m.mindful!!,m.active!=null,{tab=0;m.beginMindful()},{m.invitation=false;tab=0})
            } else when(tab) { 0 -> Meditation(m) { a.quoteAttribution() }; 1 -> History(m.history,onImport={a.importHistory()},onExport={a.exportHistory()},transferBusy=m.transferBusy) { row -> m.action {m.engine.store.delete(row.id);null} }; else -> PreferencesScreen(m,a) }
        }
    }
    m.pendingImport?.let { rows ->
        AlertDialog(onDismissRequest={m.pendingImport=null},title={Text("Import history?")},
            text={Text("Merge ${rows.size} session entries into your history? Existing sessions stay unchanged. Identical IDs are skipped; conflicting IDs reject the entire import.")},
            confirmButton={TextButton(onClick={m.confirmImport()}){Text("Import")}},
            dismissButton={TextButton(onClick={m.pendingImport=null}){Text("Cancel")}})
    }
    m.transferMessage?.let { text ->
        AlertDialog(onDismissRequest={m.transferMessage=null},title={Text("History transfer")},text={Text(text)},
            confirmButton={TextButton(onClick={m.transferMessage=null}){Text("OK")}})
    }
    m.error?.let { text -> AlertDialog(onDismissRequest={m.error=null},title={Text("Timer needs attention")},text={Text(text)},confirmButton={TextButton(onClick={m.error=null}){Text("OK")}}) }
}
@Composable fun Meditation(m: MeditationModel, openAttribution: () -> Unit) {
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { m.observeQuotes() } }
    val s=m.active; val left=s?.left(SystemClock.elapsedRealtime()) ?: (m.preferences.minutes*60000L)
    var custom by remember { mutableStateOf(false) }; var end by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0) }
    LaunchedEffect(s) { while(true) { tick=SystemClock.elapsedRealtime(); delay(250) } }
    val remaining=if(s==null) left else if(s.open) s.activeMillis(tick) else s.left(tick)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        Box(Modifier.size(260.dp),contentAlignment=Alignment.Center) {
            CircularProgressIndicator(progress={if(s==null || s.open) 1f else remaining.toFloat()/s.duration},modifier=Modifier.fillMaxSize(),strokeWidth=3.dp,trackColor=MaterialTheme.colorScheme.surfaceVariant)
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text("%02d:%02d".format((remaining+(if(s?.open==true) 0 else 999))/1000/60,(remaining+(if(s?.open==true) 0 else 999))/1000%60),fontSize=56.sp,fontWeight=FontWeight.Light)
                Text(when { s?.test==true -> "RELIABILITY TEST"; s?.state=="paused" -> "PAUSED"; s?.open==true -> "MINDFUL · COUNTING UP"; s?.kind==MINDFUL_TIMED -> "MINDFUL MINUTES"; s!=null -> "JUST BREATHE"; else -> "MINUTES OF STILLNESS" },style=MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(28.dp))
        if(s==null) {
            DurationPresetButtons(m.preferences.presets,m.preferences.minutes) { n -> m.save(m.preferences.copy(minutes=n)) }
            TextButton(onClick={custom=true}) { Text("Custom duration · ${m.preferences.minutes} min") }
            if(androidx.compose.ui.platform.LocalContext.current.getSystemService(android.media.AudioManager::class.java).getStreamVolume(android.media.AudioManager.STREAM_ALARM)==0) {
                Text("Alarm volume is muted. Raise it in sound settings to hear completion.",color=MaterialTheme.colorScheme.error)
            }
            Button(onClick={m.action { m.engine.start(m.preferences.minutes*60000L) }},modifier=Modifier.fillMaxWidth().height(56.dp)) { Text("Begin meditation") }
            if(m.message.isNotBlank()) { Spacer(Modifier.height(20.dp)); Text(m.message,style=MaterialTheme.typography.bodyLarge) }
        } else {
            Button(onClick={m.action { if(s.state=="paused") m.engine.resume() else {m.engine.pause(); null} }},modifier=Modifier.fillMaxWidth().height(56.dp)) { Text(if(s.state=="paused") "Resume" else "Pause") }
            if(s.open) Button(onClick={m.action {m.engine.finishOpen()}},modifier=Modifier.fillMaxWidth()) {Text("Finish mindful pause")}
            TextButton(onClick={end=true}) { Text(if(s.open) "Discard session" else "End session") }
            Text("Let your screen lock naturally.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        QuoteCard(m.quote,openAttribution)
        if(m.quote!=null) Spacer(Modifier.height(24.dp))
    }
    if(custom) DurationDialog(m.preferences.minutes,{custom=false}) { m.save(m.preferences.copy(minutes=it)); custom=false }
    if(end) AlertDialog(onDismissRequest={end=false},title={Text("End this session?")},text={Text("Incomplete meditation will be discarded and will not count towards your history or streak.")},confirmButton={TextButton(onClick={m.action {m.engine.end();null};end=false}){Text("End session")}},dismissButton={TextButton(onClick={end=false}){Text("Keep meditating")}})
}
@Composable fun DurationDialog(initial: Int,dismiss: ()->Unit,save: (Int)->Unit) {
    var value by remember { mutableStateOf(initial.toString()) }
    AlertDialog(onDismissRequest=dismiss,title={Text("Meditation duration")},text={OutlinedTextField(value=value,onValueChange={value=it.filter(Char::isDigit).take(3)},label={Text("Minutes · 1–120")},singleLine=true)},confirmButton={TextButton(enabled=value.toIntOrNull() in 1..120,onClick={save(value.toInt())}){Text("Save")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}
@Composable fun History(sessions: List<Completed>, onImport: () -> Unit = {}, onExport: () -> Unit = {}, transferBusy: Boolean = false, onDelete: (Completed) -> Unit) {
    val today=LocalDate.now()
    var selectedDay by rememberSaveable { mutableStateOf(today.toString()) }
    var displayedMonth by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val selectedDate=LocalDate.parse(selectedDay)
    val month=YearMonth.parse(displayedMonth)
    val dailySessions=sessions.filter { it.date==selectedDate }
    val stats=statistics(sessions,today); var deleting by remember { mutableStateOf<Completed?>(null) }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="transfer") {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick=onImport,enabled=!transferBusy,modifier=Modifier.weight(1f)) { Text("Import") }
                OutlinedButton(onClick=onExport,enabled=!transferBusy,modifier=Modifier.weight(1f)) { Text("Export") }
            }
            if(transferBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
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
            Column(Modifier.weight(1f)) { if(row.kind!=MEDITATION) Text(if(row.kind==MINDFUL_OPEN) "Mindful Minutes · Open-ended" else "Mindful Minutes · Timed",style=MaterialTheme.typography.labelMedium); Text(row.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))); Text("${Instant.ofEpochMilli(row.millis).atZone(ZoneId.of(row.zone)).format(DateTimeFormatter.ofPattern("HH:mm"))} · ${row.duration/60000} min${if(row.kind==MINDFUL_OPEN) " ${row.duration/1000%60}s" else ""}",color=MaterialTheme.colorScheme.onSurfaceVariant) }
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
    var presets by rememberSaveable { mutableStateOf(false) }
    var mindfulPage by rememberSaveable { mutableStateOf(false) }
    var health by rememberSaveable { mutableStateOf(false) }
    if(mindfulPage) {
        BackHandler {mindfulPage=false}
        val config=m.mindful
        val nm=a.getSystemService(NotificationManager::class.java)
        val ready=nm.areNotificationsEnabled() && nm.getNotificationChannel(Notifications.MINDFUL)?.importance!=NotificationManager.IMPORTANCE_NONE
        if(config!=null) MindfulSettingsScreen(config,ready,{mindfulPage=false},{a.notifications()}) {
            m.saveMindful(it) {mindfulPage=false}
        } else Text("Loading settings…")
        return
    }
    if(health) {
        BackHandler { health=false }
        TimerHealthScreen(m,a) { health=false }
        return
    }
    val p=m.preferences
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Meditation preferences",style=MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick={duration=true},modifier=Modifier.fillMaxWidth()) { Text("Default duration · ${p.minutes} min") }
        OutlinedButton(onClick={presets=true},modifier=Modifier.fillMaxWidth()) { Text("Preset durations · ${p.presets.joinToString(", ")} min") }
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Daily reminder",Modifier.weight(1f)); Switch(checked=p.reminder,onCheckedChange={m.save(p.copy(reminder=it))}) }
        OutlinedButton(onClick={time=true},modifier=Modifier.fillMaxWidth()) { Text("Reminder time · %02d:%02d".format(p.hour,p.minute)) }
        OutlinedButton(onClick={mindfulPage=true},modifier=Modifier.fillMaxWidth()) {Text("Mindful Minutes ›")}
        OutlinedButton(onClick={health=true},modifier=Modifier.fillMaxWidth()) { Text("Timer Health ›") }
        Text("Permissions, battery, bell audio and reliability tests",style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
    if(duration) DurationDialog(p.minutes,{duration=false}) { m.save(p.copy(minutes=it));duration=false }
    if(presets) DurationPresetsDialog(p.presets,{presets=false}) { m.save(m.preferences.copy(presets=it));presets=false }
    if(time) {
        DisposableEffect(Unit) {
            val dialog=TimePickerDialog(a,{_,h,min -> m.save(p.copy(hour=h,minute=min));time=false},p.hour,p.minute,true)
            dialog.setOnDismissListener {time=false};dialog.show();onDispose {dialog.dismiss()}
        }
    }
}
