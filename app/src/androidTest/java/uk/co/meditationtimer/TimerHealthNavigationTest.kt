package uk.co.meditationtimer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

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
}
