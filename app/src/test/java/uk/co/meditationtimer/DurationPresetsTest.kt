package uk.co.meditationtimer

import org.junit.Assert.*
import org.junit.Test

class DurationPresetsTest {
    @Test fun validValuesKeepUserOrder() {
        assertEquals(listOf(30,1,120,7),parseDurationPresets("30, 1, 120, 7"))
        assertEquals(listOf(8),parseDurationPresets("8"))
        assertEquals((1..8).toList(),parseDurationPresets("1,2,3,4,5,6,7,8"))
    }
    @Test fun invalidDuplicateAndExcessiveEntriesAreRejected() {
        listOf("", " ", "0", "121", "5,5", "5,05", "1.5", "five", "1,", ",1", "1,,2", "1,2,3,4,5,6,7,8,9").forEach {
            assertNull("Should reject: $it",parseDurationPresets(it))
        }
    }
    @Test fun originalPresetsRemainTheDefault() {
        assertEquals(listOf(5,10,15,20,30),DEFAULT_DURATION_PRESETS)
    }
}
