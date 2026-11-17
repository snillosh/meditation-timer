package uk.co.meditationtimer

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class WellnessWorkTest {
    private lateinit var context: Context

    @Before
    fun initialize() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("wellness_sync", 0).edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .build(),
        )
    }

    @Test
    fun uniqueNetworkConstrainedWorkDoesNotMultiplyAndDisableCancelsIt() {
        WellnessSettings(context).save(WellnessConnection.DEFAULT_SERVER, null, true)
        WellnessSync.maintain(context)
        WellnessSync.maintain(context)
        val manager = WorkManager.getInstance(context)
        val drain = manager.getWorkInfosForUniqueWork("wellness-sync-drain").get()
        val periodic = manager.getWorkInfosForUniqueWork("wellness-sync-recovery").get()
        assertEquals(1, drain.count { !it.state.isFinished })
        assertEquals(1, periodic.count { !it.state.isFinished })
        WellnessSettings(context).save(WellnessConnection.DEFAULT_SERVER, null, false)
        WellnessSync.maintain(context)
        assertTrue(
            manager.getWorkInfosForUniqueWork("wellness-sync-drain").get().all {
                it.state.isFinished
            }
        )
        assertTrue(
            manager.getWorkInfosForUniqueWork("wellness-sync-recovery").get().all {
                it.state.isFinished
            }
        )
    }
}
