package uk.co.meditationtimer

import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BellAudioTest {
    @Test fun completionAndReliabilityAudioUseAlarmStream() {
        val attributes=BellAudio.attributes(true)
        assertEquals(AudioAttributes.USAGE_ALARM,attributes.usage)
        assertEquals(AudioManager.STREAM_ALARM,attributes.volumeControlStream)
    }
    @Test fun completionIsNotSkippedForSilentVibrateOrDnd() {
        for(ringer in listOf(AudioManager.RINGER_MODE_NORMAL,AudioManager.RINGER_MODE_SILENT,AudioManager.RINGER_MODE_VIBRATE)) {
            for(filter in listOf(NotificationManager.INTERRUPTION_FILTER_ALL,NotificationManager.INTERRUPTION_FILTER_PRIORITY,NotificationManager.INTERRUPTION_FILTER_ALARMS,NotificationManager.INTERRUPTION_FILTER_NONE)) {
                // Android, rather than an app-side veto, applies the user's alarm/DND policy.
                assertFalse(BellAudio.skip(true,ringer,filter))
            }
        }
    }
    @Test fun startBellKeepsQuietBehaviour() {
        assertEquals(AudioAttributes.USAGE_NOTIFICATION_EVENT,BellAudio.attributes(false).usage)
        assertTrue(BellAudio.skip(false,AudioManager.RINGER_MODE_SILENT,NotificationManager.INTERRUPTION_FILTER_ALL))
        assertTrue(BellAudio.skip(false,AudioManager.RINGER_MODE_NORMAL,NotificationManager.INTERRUPTION_FILTER_PRIORITY))
        assertFalse(BellAudio.skip(false,AudioManager.RINGER_MODE_NORMAL,NotificationManager.INTERRUPTION_FILTER_ALL))
    }
}
