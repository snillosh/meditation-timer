package uk.co.meditationtimer

import android.content.Context
import java.util.UUID

class WellnessSettings(context: Context) {
    companion object {
        val lock = Any()
    }

    private val prefs = context.getSharedPreferences("wellness_sync", Context.MODE_PRIVATE)

    fun configuration(): WellnessConfiguration =
        synchronized(WellnessSettings.lock) {
            WellnessConfiguration(
                prefs.getString("server", WellnessConnection.DEFAULT_SERVER)!!,
                prefs.getBoolean("enabled", false),
                prefs.getString("revision", "initial")!!,
            )
        }

    fun save(server: String, replacement: String?, enabled: Boolean) =
        synchronized(WellnessSettings.lock) {
            val normalized = WellnessConnection.normalizeServer(server)
            require(replacement == null || WellnessConnection.validKey(replacement)) {
                "Enter a valid Wellness API key"
            }
            val encrypted = replacement?.let { WellnessCredential().encrypt(it) }
            val editor =
                prefs
                    .edit()
                    .putString("server", normalized)
                    .putBoolean("enabled", enabled)
                    .putString("revision", UUID.randomUUID().toString())
                    .remove("blocked_revision")
                    .remove("retry_at")
                    .putString(
                        "status",
                        if (enabled) "Ready to synchronise" else "Background sync disabled",
                    )
            if (encrypted != null) editor.putString("credential", encrypted)
            if (normalized != configuration().serverUrl) editor.remove("last_success")
            check(editor.commit()) { "Could not save sync settings" }
        }

    fun clearCredential() =
        synchronized(WellnessSettings.lock) {
            check(
                prefs
                    .edit()
                    .remove("credential")
                    .remove("blocked_revision")
                    .remove("retry_at")
                    .putString("revision", UUID.randomUUID().toString())
                    .putString("status", "API key cleared")
                    .commit()
            )
        }

    fun hasCredential(): Boolean = !prefs.getString("credential", null).isNullOrEmpty()

    fun credential(): String? =
        prefs.getString("credential", null)?.let { WellnessCredential().decrypt(it) }

    fun status(): String = prefs.getString("status", "Sync is optional and disabled by default")!!

    fun lastSuccess(): Long = prefs.getLong("last_success", 0)

    fun blocked(config: WellnessConfiguration): Boolean =
        prefs.getString("blocked_revision", null) == config.revision

    fun retryAt(): Long = prefs.getLong("retry_at", 0)

    fun status(
        config: WellnessConfiguration,
        message: String,
        blocked: Boolean = false,
        retryAt: Long = 0,
        acknowledged: Boolean = false,
    ) =
        synchronized(WellnessSettings.lock) {
            if (configuration().revision != config.revision) return@synchronized
            val editor = prefs.edit().putString("status", message).putLong("retry_at", retryAt)
            if (blocked) editor.putString("blocked_revision", config.revision)
            if (acknowledged) editor.putLong("last_success", System.currentTimeMillis())
            check(editor.commit())
        }

    fun manualRetry() =
        synchronized(WellnessSettings.lock) {
            check(prefs.edit().remove("blocked_revision").remove("retry_at").commit())
        }
}
