package uk.co.meditationtimer

import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*

@Composable
fun WellnessSyncScreen(activity: MainActivity, back: () -> Unit) {
    val context = activity.applicationContext
    val settings = remember { WellnessSettings(context) }
    val initial = remember { settings.configuration() }
    var server by remember { mutableStateOf(initial.serverUrl) }
    // Credential text is deliberately not rememberSaveable or part of the ViewModel.
    var key by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var summary by remember { mutableStateOf("Loading sync status…") }
    var message by remember { mutableStateOf<String?>(null) }
    var clear by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ink = MaterialTheme.colorScheme.onSurface.toArgb()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    DisposableEffect(activity) {
        val alreadySecure =
            activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (!alreadySecure) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            summary =
                withContext(Dispatchers.IO) {
                    try {
                        val config = settings.configuration()
                        val counts = Store(context).use { it.wellnessCounts(config.serverUrl) }
                        val last = settings.lastSuccess()
                        val whenSynced =
                            if (last == 0L) "Never"
                            else
                                DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
                                    .withZone(ZoneId.systemDefault())
                                    .format(Instant.ofEpochMilli(last))
                        "${settings.status()}\nKey: ${if(settings.hasCredential()) "Saved securely" else "Not configured"}\nPending: ${counts.first} · Rejected: ${counts.second}\nLast successful sync: $whenSynced"
                    } catch (_: Exception) {
                        "Sync storage needs attention; timer/history remain local"
                    }
                }
            delay(2000)
        }
    }
    fun action(block: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                message = "Saved / queued successfully"
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message = "Check the HTTPS URL, meditation API key and local storage"
            } finally {
                busy = false
            }
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = back) { Text("‹ Back to Settings") }
        Text("Wellness Sync", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Optional uploads of completed meditation and Mindful Minutes. Timers always work offline."
        )
        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("HTTPS server URL") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "API key · leave blank to keep the saved key",
            style = MaterialTheme.typography.labelLarge,
        )
        AndroidView(
            factory = { viewContext ->
                EditText(viewContext).apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                    isSaveEnabled = false
                    setSingleLine(true)
                    setTextColor(ink)
                    setHintTextColor(muted)
                    hint = "Paste the meditation key"
                    addTextChangedListener(
                        object : android.text.TextWatcher {
                            override fun beforeTextChanged(
                                s: CharSequence?,
                                start: Int,
                                count: Int,
                                after: Int,
                            ) {}

                            override fun onTextChanged(
                                s: CharSequence?,
                                start: Int,
                                before: Int,
                                count: Int,
                            ) {
                                key = s?.toString().orEmpty()
                            }

                            override fun afterTextChanged(s: android.text.Editable?) {}
                        }
                    )
                }
            },
            update = { view ->
                view.isEnabled = !busy
                if (key.isEmpty() && view.text.isNotEmpty()) view.text.clear()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            Text("Enable background sync", Modifier.weight(1f))
            Switch(enabled, { enabled = it }, enabled = !busy)
        }
        Button(
            onClick = {
                val typedServer = server
                val typedKey = key.trim().takeIf { it.isNotEmpty() }
                val selected = enabled
                action {
                    settings.save(typedServer, typedKey, selected)
                    WellnessSync.maintain(context)
                }
                key = ""
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save connection")
        }
        OutlinedButton(
            onClick = {
                action {
                    settings.manualRetry()
                    settings.status(
                        settings.configuration(),
                        "Connection test queued; waiting for network",
                    )
                    WellnessSync.enqueue(context, true)
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Test saved connection")
        }
        OutlinedButton(
            onClick = {
                action {
                    val saved = settings.configuration()
                    check(saved.enabled)
                    settings.manualRetry()
                    Store(context).use { it.wellnessRetryRejected(saved.serverUrl) }
                    settings.status(saved, "Sync queued; waiting for network")
                    WellnessSync.maintain(context)
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sync now / retry pending")
        }
        TextButton(onClick = { clear = true }, enabled = !busy) { Text("Clear saved API key") }
        Text(summary, style = MaterialTheme.typography.bodyMedium)
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(
            "Enabling sync uploads existing saved history and future completions. Deleted local sessions are not recreated; observations already uploaded remain on the server.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Changes take effect when saved. Use a separate meditation credential, not the alarm app's key.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (clear)
        AlertDialog(
            onDismissRequest = { clear = false },
            title = { Text("Clear saved key?") },
            text = { Text("Local sessions and uploaded observations remain unchanged.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clear = false
                        key = ""
                        action {
                            settings.clearCredential()
                            WellnessSync.maintain(context)
                        }
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } },
        )
}
