package uk.co.meditationtimer

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import java.util.UUID
import java.time.*

class TimerEngine internal constructor(
    val context: Context,
    val clock: Clock = AndroidClock(context),
    val store: Store = Store(context),
    private val bell: (Boolean, String?) -> Unit = { completion, test -> BellService.start(context, completion, test) }
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    fun exactAccess() = android.os.Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    // The revision rejects callbacks already queued before pause/resume or cancellation.
    private fun pending(s: Session) = PendingIntent.getBroadcast(context, 1,
        Intent(context, TimerReceiver::class.java).setAction("complete").putExtra("id",s.id).putExtra("generation",s.generation),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun cancel(s: Session) { alarms.cancel(pending(s)); context.getSystemService(NotificationManager::class.java).cancel(1) }
    private fun schedule(s: Session) { alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,s.deadline,pending(s)) }
    @Synchronized fun start(duration: Long, test: Boolean = false, kind: String = MEDITATION): String? {
        if(kind !in setOf(MEDITATION, MINDFUL_TIMED)) return "Invalid timed session type."
        if(clock.boot()==-1) return "Boot identity is unavailable; reliable recovery cannot be guaranteed."
        if(duration !in 60000L..7200000L || (test && duration!=60000L)) return "Duration must be 1–120 minutes (reliability tests are 60 seconds)."
        if (store.active() != null) return "End the current session first."
        if (!exactAccess()) {
            if(test) store.note("test","Test not scheduled: exact-alarm special access is missing. No callback or audio attempted.")
            return "Allow Alarms & reminders in Timer Health before starting."
        }
        val s = Session(UUID.randomUUID().toString(),duration,duration,clock.elapsed()+duration,clock.boot(),test=test,expectedWall=clock.wall()+duration,kind=kind)
        return try {
            store.save(s); schedule(s); store.note("message", "")
            if(test) store.note("test", "Scheduled: ${Instant.ofEpochMilli(s.expectedWall)}\nCompletion handler: not yet observed.\nAudio: not yet attempted.")
            Notifications.active(context,s,clock)
            try { bell(false, null) } catch(e:Exception) { store.note("audio","Start bell failed: ${e.message}") }; null
        } catch(e: Exception) { cancel(s); store.clear(); store.note("test","Scheduling error: ${e.javaClass.simpleName}"); "Unable to schedule timer: ${e.message}" }
    }
    @Synchronized fun startOpen(): String? {
        if(clock.boot()==-1) return "Boot identity is unavailable."
        if(store.active()!=null) return "Finish or end the current session first."
        val s=Session(UUID.randomUUID().toString(),0,0,0,clock.boot(),kind=MINDFUL_OPEN,started=clock.elapsed())
        store.save(s);store.note("message", "")
        Notifications.active(context,s,clock)
        return null
    }
    @Synchronized fun finishOpen(): String? {
        val s=store.active() ?: return null
        if(!s.open) return "This is not an open-ended session."
        if(s.boot!=clock.boot()) { recover();return "Session discarded after reboot." }
        val elapsed=s.activeMillis(clock.elapsed())
        if(elapsed<1000) return "Take at least a second before finishing, or discard the session."
        if(store.complete(s,clock.wall(),elapsed)) { cancel(s); WellnessSync.requestSafely(context) }
        return null
    }
    @Synchronized fun pause() {
        val s=store.active() ?: return
        if(s.boot!=clock.boot() || clock.boot()==-1) {recover();return}
        if(s.state!="running") return
        val now=clock.elapsed()
        if(!s.open && s.left(now)==0L) { finish(s.id,s.generation); return }
        store.save(s.pause(now)); cancel(s); Notifications.active(context,store.active()!!,clock)
    }
    @Synchronized fun resume(): String? {
        val s=store.active() ?: return null
        if(s.boot!=clock.boot() || clock.boot()==-1) {recover();return "Session discarded after reboot."}
        if(s.state!="paused") return null
        if(!s.open && !exactAccess()) return "Exact-alarm access is required to resume."
        val next=s.copy(boot=clock.boot()).resume(clock.elapsed(),clock.wall())
        return try { store.save(next); if(!next.open) schedule(next); Notifications.active(context,next,clock); null }
        catch(e:Exception) { store.save(s); "Could not resume: ${e.message}" }
    }
    @Synchronized fun end() { store.active()?.let { store.clear(); cancel(it); if(it.test) store.note("test","Test cancelled; no completion observed.") } }
    @Synchronized fun recover(reboot: Boolean = false) {
        val s=store.active() ?: return
        if(reboot || s.boot != clock.boot() || clock.boot() == -1) {
            end(); store.note("message","Session discarded after reboot or unavailable boot identity. No completion credit awarded."); return
        }
        if(s.open) { Notifications.active(context,s,clock); return }
        if(s.state=="running") {
            if(!exactAccess()) { store.save(s.pause(clock.elapsed())); cancel(s); store.note("message","Timer paused because exact-alarm access was removed."); if(s.test) store.note("test",store.note("test")+"\nPaused: exact-alarm access removed; delivery is unavailable."); return }
            if(s.left(clock.elapsed())==0L) finish(s.id,s.generation,"foreground/system recovery") else {
                try { schedule(s); Notifications.active(context,s,clock) } catch(e:Exception) { store.note("message","Scheduling needs attention: ${e.message}") }
            }
        } else Notifications.active(context,s,clock)
    }
    // Validate monotonic evidence before the single atomic history/active-state transaction.
    @Synchronized fun finish(id: String, generation: Int, source: String = "alarm receiver") {
        val s=store.active() ?: return
        if(!s.due(clock.elapsed(),clock.boot(),id,generation)) return
        val actual=clock.wall()
        if(!store.complete(s,actual)) return
        cancel(s)
        if(s.test) store.note("test", "Expected: ${Instant.ofEpochMilli(s.expectedWall)}\nCallback: ${Instant.ofEpochMilli(actual)}\nMonotonic delay: ${clock.elapsed()-s.deadline} ms\nCompletion handler: ran via $source.\nAudio: pending invocation.")
        try { bell(true,if(s.test) s.id else null) }
        catch(e:Exception) { store.note("audio","Bell service failed: ${e.message}"); if(s.test) store.note("test",store.note("test")+"\nBell service failed: ${e.message}") }
        if(!s.test) WellnessSync.requestSafely(context)
    }
    companion object {
        @Volatile private var instance: TimerEngine? = null
        fun get(context: Context): TimerEngine = instance ?: synchronized(this) { instance ?: TimerEngine(context.applicationContext).also { instance=it } }
    }
}
object Notifications {
    const val ACTIVE="timer"; const val BELL="bell"; const val REMINDER="reminder"; const val MINDFUL="mindful"
    fun channels(c: Context) { c.getSystemService(NotificationManager::class.java).apply {
        createNotificationChannel(NotificationChannel(ACTIVE,"Active timer",NotificationManager.IMPORTANCE_LOW))
        createNotificationChannel(NotificationChannel(BELL,"Bell playback",NotificationManager.IMPORTANCE_LOW).apply { setSound(null,null) })
        createNotificationChannel(NotificationChannel(MINDFUL,"Mindful Minutes nudges",NotificationManager.IMPORTANCE_DEFAULT))
        createNotificationChannel(NotificationChannel(REMINDER,"Daily reminders",NotificationManager.IMPORTANCE_DEFAULT))
    } }
    fun open(c: Context) = PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    fun builder(c: Context,channel: String) = NotificationCompat.Builder(c,channel).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentIntent(open(c))
    fun active(c: Context,s: Session,clock: Clock) { channels(c)
        val b=builder(c,ACTIVE).setContentTitle(if(s.test) "Reliability test" else if(s.kind!=MEDITATION) "Mindful Minutes in progress" else "Meditation in progress").setOngoing(true).setSilent(true)
            .setContentText(if(s.state=="paused") "Paused · open to resume" else if(s.open) "Open-ended practice · tap to finish" else "Ends around ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(clock.wall()+s.left(clock.elapsed())))}")
        if(s.state=="running" && !s.open) b.setWhen(clock.wall()+s.left(clock.elapsed())).setUsesChronometer(true).setChronometerCountDown(true)
        try { c.getSystemService(NotificationManager::class.java).notify(1,b.build()) } catch(_:SecurityException) { }
    }
}
