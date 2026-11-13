package uk.co.meditationtimer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuoteCardTest {
    @get:Rule val compose=createComposeRule()

    @Test fun rendersAttributionAndDisappearsCompletelyWhenUnavailable() {
        var quote by mutableStateOf<Quote?>(Quote("A quiet moment.","Test author"))
        var opened=false
        compose.setContent { MaterialTheme { QuoteCard(quote) { opened=true } } }
        compose.onNodeWithText("A quiet moment.").assertExists()
        compose.onNodeWithText("— Test author").assertExists()
        compose.onNodeWithText("Inspirational quotes provided by ZenQuotes API").performClick()
        compose.runOnIdle { assertTrue(opened);quote=null }
        compose.onNodeWithText("A quiet moment.").assertDoesNotExist()
        compose.onNodeWithText("Inspirational quotes provided by ZenQuotes API").assertDoesNotExist()
    }
}
