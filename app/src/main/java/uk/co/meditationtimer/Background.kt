package uk.co.meditationtimer

import android.app.*
import android.content.*
import android.media.*
import android.os.*
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

class TimerReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending=goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if(intent.action=="reminder") Reminders.deliver(context)
                else TimerEngine.get(context).finish(intent.getStringExtra("id") ?: "",intent.getIntExtra("generation",-1))
            } finally { pending.finish() }
        }
    }
}
class SystemReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val pending=goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TimerEngine.get(context).recover(intent.action==Intent.ACTION_BOOT_COMPLETED)
                Reminders.schedule(context)
            } finally { pending.finish() }
        }
    }
}
object Reminders {
    private val mutex=Mutex()
    private fun pending(c: Context) = PendingIntent.getBroadcast(c,2,Intent(c,TimerReceiver::class.java).setAction("reminder"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    suspend fun schedule(c: Context) = mutex.withLock {
        // Re-read under the same lock as delivery so a disabled reminder cannot be restored by stale work.
        scheduleLocked(c,PreferenceStore(c).flow.first())
    }
    private fun scheduleLocked(c: Context,p: Preferences) {
        val am=c.getSystemService(AlarmManager::class.java); am.cancel(pending(c))
        if(p.reminder) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,nextReminder(ZonedDateTime.now(),p.hour,p.minute).toInstant().toEpochMilli(),pending(c))
    }
    suspend fun deliver(c: Context) = mutex.withLock {
        val p=PreferenceStore(c).flow.first()
        if(!p.reminder) return@withLock
        val store=TimerEngine.get(c).store
        val day=LocalDate.now().toString()
        if(store.note("reminderDay")!=day) {
            Notifications.channels(c)
            try { c.getSystemService(NotificationManager::class.java).notify(3,Notifications.builder(c,Notifications.REMINDER)
                .setContentTitle("A moment for yourself").setContentText("Your meditation timer is ready when you are.").setAutoCancel(true).build()) }
            catch(_:SecurityException) { }
            store.note("reminderDay",day)
        }
        scheduleLocked(c,p)
    }
}
class BellService: Service() {
    private var player: MediaPlayer? = null
    private val handler=Handler(Looper.getMainLooper())
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        Notifications.channels(this)
        ServiceCompat.startForeground(this,4,Notifications.builder(this,Notifications.BELL).setContentTitle("Meditation bell").setSilent(true).build(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        player?.release(); player=null; handler.removeCallbacksAndMessages(null)
        val test=intent?.getStringExtra("test")
        val completion=intent?.getBooleanExtra("completion",false)==true
        try {
            val audio=getSystemService(AudioManager::class.java)
            val notifications=getSystemService(NotificationManager::class.java)
            if(BellAudio.skip(completion,audio.ringerMode,notifications.currentInterruptionFilter)) {
                diagnostic(test,"Start bell skipped for silent/vibrate or Do Not Disturb."); stopSelf(); return START_NOT_STICKY
            }
            val resource=if(completion) R.raw.completion_bell else R.raw.start_bell
            val mp=MediaPlayer(); player=mp
            mp.setAudioAttributes(BellAudio.attributes(completion))
            resources.openRawResourceFd(resource).use { mp.setDataSource(it.fileDescriptor,it.startOffset,it.length) }
            mp.isLooping=false; mp.setOnCompletionListener { stopSelf() }; mp.setOnErrorListener { _,what,extra -> diagnostic(test,"Playback error $what/$extra"); stopSelf(); true }
            mp.prepare(); mp.start()
            val stream=if(completion) AudioManager.STREAM_ALARM else AudioManager.STREAM_NOTIFICATION
            diagnostic(test,"Playback invoked successfully; ${if(completion) "alarm" else "notification"} volume ${audio.getStreamVolume(stream)}. DND policy remains system-controlled.")
            handler.postDelayed({ stopSelf() },5000)
        } catch(e:Exception) { diagnostic(test,"Playback failed: ${e.message}"); stopSelf() }
        return START_NOT_STICKY
    }
    private fun diagnostic(test: String?, message: String) {
        val store=TimerEngine.get(this).store
        store.note("audio",message)
        if(test!=null) store.note("test",store.note("test")+"\nAudio: $message\nAPI success does not prove audibility.")
    }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); player?.release(); player=null; stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }
    companion object {
        fun start(c: Context,completion: Boolean,test: String?) {
            c.startForegroundService(Intent(c,BellService::class.java).putExtra("completion",completion).putExtra("test",test))
        }
    }
}

/** Completion is an alarm; Android applies alarm volume and the user's DND alarm exceptions. */
object BellAudio {
    fun attributes(completion: Boolean): AudioAttributes = AudioAttributes.Builder()
        .setUsage(if(completion) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    fun skip(completion: Boolean, ringer: Int, filter: Int) = !completion &&
        (ringer != AudioManager.RINGER_MODE_NORMAL || filter != NotificationManager.INTERRUPTION_FILTER_ALL)

    fun alarmsAllowed(manager: NotificationManager): Boolean? = runCatching {
        when(manager.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL, NotificationManager.INTERRUPTION_FILTER_ALARMS -> true
            NotificationManager.INTERRUPTION_FILTER_NONE -> false
            NotificationManager.INTERRUPTION_FILTER_PRIORITY ->
                manager.consolidatedNotificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS != 0
            else -> null
        }
    }.getOrNull()
}
