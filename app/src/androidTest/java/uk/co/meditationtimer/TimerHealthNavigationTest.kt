package uk.co.meditationtimer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.flow.first

class TimerHealthNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun diagnosticsAreInSubmenuWithBothBackActions() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Timer Health ›").assertExists()
        compose.onNodeWithText("Exact timer alarms").assertDoesNotExist()
        compose.onNodeWithText("Last test result").assertDoesNotExist()
        compose.onNodeWithText("Timer Health ›").performClick()
        compose.onNodeWithText("Exact timer alarms").assertExists()
        compose.onNodeWithText("Test Timer Reliability").assertExists()
        compose.onNodeWithText("Last test result").assertExists()
        compose.onNodeWithText("‹ Back to Settings").performClick()
        compose.onNodeWithText("Timer Health ›").assertExists()
        compose.onNodeWithText("Last test result").assertDoesNotExist()
        compose.onNodeWithText("Timer Health ›").performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Timer Health ›").assertExists()
        compose.onNodeWithText("Exact timer alarms").assertDoesNotExist()
    }

    @Test fun mindfulNotificationOpensPromptAndNeverStartsOrSavesASession() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val model=androidx.lifecycle.ViewModelProvider(compose.activity)[MeditationModel::class.java]
        val original=kotlinx.coroutines.runBlocking {context.preferences.data.first()}
        val before=model.engine.store.history()
        val manager=context.getSystemService(android.app.NotificationManager::class.java)
        try {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").use { descriptor ->
                    java.io.FileInputStream(descriptor.fileDescriptor).use {it.readBytes()}
                }
            val now=java.time.ZonedDateTime.now()
            val config=MindfulConfig(enabled=true,activity="prompt",schedule="fixed",days=setOf(now.dayOfWeek.value),fixedTimes=listOf(now.hour*60+now.minute))
            kotlinx.coroutines.runBlocking {MindfulPreferences(context).save(config)}
            compose.waitUntil(5000) {model.mindful?.activity=="prompt"}
            val due=now.withSecond(0).withNano(0).toInstant().toEpochMilli()
            model.engine.store.note("mindful_due",due.toString())
            model.engine.store.note("mindful_last_slot","0")
            model.engine.store.note("mindful_day",now.toLocalDate().toString())
            model.engine.store.note("mindful_count","0")
            kotlinx.coroutines.runBlocking {MindfulReminders.deliver(context,due)}
            val notification=manager.activeNotifications.first {it.id==5}
            notification.notification.contentIntent.send()
            compose.waitUntil(5000) {model.invitation}
            compose.onNodeWithText("A moment to be present").assertExists()
            compose.onNodeWithText("Done").performClick()
            compose.runOnIdle {
                org.junit.Assert.assertNull(model.engine.store.active())
                org.junit.Assert.assertEquals(before,model.engine.store.history())
            }
        } finally {
            kotlinx.coroutines.runBlocking {context.preferences.updateData {original};MindfulReminders.schedule(context)}
            manager.cancel(5)
        }
    }
}
