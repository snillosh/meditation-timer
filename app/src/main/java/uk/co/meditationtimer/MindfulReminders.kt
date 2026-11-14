package uk.co.meditationtimer

import android.app.*
import android.content.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

object MindfulReminders {
    private val mutex=Mutex()
    private fun pending(c: Context,due: Long=0) = PendingIntent.getBroadcast(c,5,
        Intent(c,TimerReceiver::class.java).setAction("mindful_nudge").putExtra("due",due),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    suspend fun schedule(c: Context) = mutex.withLock { scheduleLocked(c,MindfulPreferences(c).flow.first()) }
    private fun scheduleLocked(c: Context,config: MindfulConfig) {
        val store=TimerEngine.get(c).store
        val alarms=c.getSystemService(AlarmManager::class.java)
        alarms.cancel(pending(c))
        val last=store.note("mindful_last_slot").toLongOrNull() ?: 0
        val now=ZonedDateTime.now()
        val count=if(store.note("mindful_day")==now.toLocalDate().toString()) store.note("mindful_count").toIntOrNull() ?: 0 else 0
        val limit=if(config.schedule=="random") config.nudges else config.fixedTimes.size
        val search=if(count>=limit) now.toLocalDate().plusDays(1).atStartOfDay(now.zone).minusNanos(1) else now
        val next=nextMindful(config,search,last)
        if(next==null) {
            store.note("mindful_status",if(config.enabled) "No available slot." else "Disabled")
            store.note("mindful_due", "");c.getSystemService(NotificationManager::class.java).cancel(5);return
        }
        val due=next.toInstant().toEpochMilli()
        store.note("mindful_due",due.toString())
        try { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,pending(c,due));store.note("mindful_status","Scheduled") }
        catch(e:Exception) { store.note("mindful_due", "");store.note("mindful_status","Unable to schedule: ${e.message}") }
    }
    suspend fun deliver(c: Context,due: Long) = mutex.withLock {
        val config=MindfulPreferences(c).flow.first()
        val store=TimerEngine.get(c).store
        if(store.note("mindful_due").toLongOrNull()!=due) return@withLock
        val now=ZonedDateTime.now()
        if(now.toInstant().toEpochMilli()<due) {scheduleLocked(c,config);return@withLock}
        val last=store.note("mindful_last_slot").toLongOrNull() ?: 0
        val delivered=store.note("mindful_delivered").toLongOrNull() ?: 0
        val allowed=mindfulDeliveryAllowed(config,due,now,last,delivered)
        // Mark handled before posting: a crash may lose one nudge, but cannot produce duplicates.
        store.note("mindful_last_slot",maxOf(last,due).toString())
        val count=if(store.note("mindful_day")==now.toLocalDate().toString()) store.note("mindful_count").toIntOrNull() ?: 0 else 0
        val limit=if(config.schedule=="random") config.nudges else config.fixedTimes.size
        if(allowed && count<limit && TimerEngine.get(c).store.active()==null) {
            Notifications.channels(c)
            val manager=c.getSystemService(NotificationManager::class.java)
            val channel=manager.getNotificationChannel(Notifications.MINDFUL)
            if(manager.areNotificationsEnabled() && channel?.importance!=NotificationManager.IMPORTANCE_NONE) {
                val intent=Intent(c,MainActivity::class.java).setAction("mindful_invitation")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val open=PendingIntent.getActivity(c,5,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                try {
                    manager.notify(5,Notifications.builder(c,Notifications.MINDFUL).setContentTitle("A moment to be present")
                        .setContentText(if(config.activity=="timed") "Take ${config.durationMinutes} mindful minutes. Tap when you're ready." else "Pause, breathe and notice this moment. Tap when you're ready.")
                        .setContentIntent(open).setAutoCancel(true).setTimeoutAfter(60*60*1000L).build())
                    store.note("mindful_day",now.toLocalDate().toString());store.note("mindful_count",(count+1).toString())
                    store.note("mindful_delivered",now.toInstant().toEpochMilli().toString())
                } catch(_:SecurityException) { store.note("mindful_status","Notifications are disabled.") }
            }
        }
        scheduleLocked(c,config)
    }
}
