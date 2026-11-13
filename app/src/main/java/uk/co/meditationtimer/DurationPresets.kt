package uk.co.meditationtimer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun DurationPresetButtons(presets: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { minutes ->
            FilterChip(selected = selected == minutes, onClick = { onSelect(minutes) },
                modifier = Modifier.semantics { contentDescription = "$minutes minute preset" },
                label = { Text("$minutes") })
        }
    }
}

@Composable
fun DurationPresetsDialog(initial: List<Int>, dismiss: () -> Unit, save: (List<Int>) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.joinToString(", ")) }
    val parsed = parseDurationPresets(text)
    AlertDialog(onDismissRequest = dismiss, title = { Text("Preset durations") }, text = {
        OutlinedTextField(value = text, onValueChange = { text = it },
            label = { Text("Minutes, separated by commas") },
            supportingText = { Text("Choose 1–8 unique durations, each from 1 to 120 minutes. Your order is kept.") },
            isError = parsed == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
    }, confirmButton = {
        TextButton(enabled = parsed != null, onClick = { parsed?.let(save) }) { Text("Save") }
    }, dismissButton = {
        Row {
            TextButton(onClick = { text = DEFAULT_DURATION_PRESETS.joinToString(", ") }) { Text("Reset") }
            TextButton(onClick = dismiss) { Text("Cancel") }
        }
    })
}
