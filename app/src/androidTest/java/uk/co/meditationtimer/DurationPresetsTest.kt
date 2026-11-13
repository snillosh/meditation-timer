package uk.co.meditationtimer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DurationPresetsUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun editorValidatesSavesAndCanResetWithoutSaving() {
        var saved: List<Int>?=null
        var dismissed=false
        compose.setContent { MaterialTheme { DurationPresetsDialog(listOf(3,7),{dismissed=true}) {saved=it} } }
        compose.onNode(hasSetTextAction()).performTextReplacement("0, 7")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("3, 7, 120")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(listOf(3,7,120),saved);saved=null }
        compose.onNodeWithText("Reset").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("5, 10, 15, 20, 30")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(dismissed);assertNull(saved) }
    }
    @Test fun customizedButtonsWrapAndSelectCorrectDuration() {
        var selected by mutableIntStateOf(7)
        compose.setContent { MaterialTheme { DurationPresetButtons(listOf(1,3,7,20,40,60,90,120),selected) {selected=it} } }
        compose.onNodeWithContentDescription("7 minute preset").assertIsSelected()
        compose.onNodeWithContentDescription("120 minute preset").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("7 minute preset").assertIsNotSelected()
    }
}

class DurationPresetPersistenceTest {
    @Test fun savedPresetOrderSurvivesNewPreferenceStoreAndCorruptValuesFallBack() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val original=context.preferences.data.first()
        try {
            context.preferences.edit { it.clear() }
            assertEquals(DEFAULT_DURATION_PRESETS,PreferenceStore(context).flow.first().presets)
            val custom=Preferences(minutes=42,reminder=true,hour=18,minute=30,presets=listOf(3,42,120))
            PreferenceStore(context).save(custom)
            assertEquals(custom,PreferenceStore(context).flow.first())
            context.preferences.edit { it[stringPreferencesKey("duration_presets")]="0,121" }
            assertEquals(DEFAULT_DURATION_PRESETS,PreferenceStore(context).flow.first().presets)
            assertEquals(42,PreferenceStore(context).flow.first().minutes)
        } finally {
            context.preferences.updateData { original }
        }
    }
}
