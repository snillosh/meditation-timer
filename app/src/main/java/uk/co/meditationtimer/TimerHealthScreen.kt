package uk.co.meditationtimer

import android.app.*
import android.content.*
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TimerHealthScreen(m: MeditationModel, a: MainActivity, onBack: () -> Unit) {
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
        TextButton(onClick=onBack) { Text("‹ Back to Settings") }
        Text("Timer Health",style=MaterialTheme.typography.titleLarge)
        Health("Exact timer alarms",if(exact) "Ready · Required" else "Needs attention · Required","One exact wake-up alarm delivers completion while locked. Access is required to start or resume.","Alarms & reminders") {
            a.settings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${a.packageName}")))
        }
        Health("Notifications",if(notices) "Ready · Recommended" else "Recommended · Disabled","Notifications show the active timer and reminders. Bell playback is separate; a blocked bell service notification does not itself mute audio.","Notification settings") { a.notifications() }
        listOf(Notifications.ACTIVE,Notifications.BELL,Notifications.REMINDER,Notifications.MINDFUL).forEach { id ->
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
        val mindful=m.mindful
        val mindfulReady=notices && nm.getNotificationChannel(Notifications.MINDFUL)?.importance!=NotificationManager.IMPORTANCE_NONE
        val due=m.engine.store.note("mindful_due").toLongOrNull()
        Health("Mindful Minutes",if(mindful?.enabled!=true) "Not applicable · Disabled" else if(!mindfulReady || due==null) "Needs attention" else "Ready · Optional",
            if(mindful?.enabled!=true) "Enable and configure mindful nudges in Settings." else
                "${if(due!=null) "Next planned nudge: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(due))}. " else "No pending nudge. "}Nudges can be delayed or skipped by Android. ${m.engine.store.note("mindful_status")}")
        val s=m.active
        Health("Active session","Ready · Informational",s?.let { if(it.open) "Open-ended mindful pause · ${it.activeMillis(m.engine.clock.elapsed())/1000} active seconds." else if(it.state=="paused") "Paused · ${it.remaining/1000} seconds remain." else "Running · expected ${java.text.DateFormat.getTimeInstance().format(java.util.Date(m.engine.clock.wall()+it.left(m.engine.clock.elapsed())))}" } ?: "No active session.")
        Health("60-second reliability test","Informational","Uses the same alarm and bell as meditation. Lock the phone and return after the bell. Tests never count towards history.","Test Timer Reliability") { test=true }
        Health("Last test result","Informational",m.diagnostic+"\nOnly a real device test can establish whether you heard the bell.")
        Spacer(Modifier.height(24.dp))
    }
    if(test) AlertDialog(onDismissRequest={test=false},title={Text("Test locked-screen delivery")},text={Text("Start a 60-second test, then lock your phone. Return to Timer Health to inspect callback and audio diagnostics. End any current session first.")},confirmButton={TextButton(onClick={m.action {m.engine.start(60000,true)};test=false}){Text("Start test")}},dismissButton={TextButton(onClick={test=false}){Text("Cancel")}})
}
