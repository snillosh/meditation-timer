package uk.co.meditationtimer

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject

enum class SyncOutcome {
    SUCCESS,
    RETRY,
}

class WellnessSynchronizer(
    private val context: Context,
    private val request: (String, String, JSONObject?) -> WellnessHttpClient.Response =
        WellnessHttpClient()::request,
    private val readCredential: () -> String? = WellnessSettings(context)::credential,
    private val stopped: () -> Boolean = { false },
) {
    fun run(testing: Boolean = false): SyncOutcome {
        val settings = WellnessSettings(context)
        val config = settings.configuration()
        if ((!config.enabled && !testing) || (!testing && settings.blocked(config)))
            return SyncOutcome.SUCCESS
        if (System.currentTimeMillis() < settings.retryAt()) return SyncOutcome.RETRY
        try {
            val credential =
                try {
                    readCredential()
                } catch (_: Exception) {
                    settings.status(config, "Replace the unreadable saved API key", blocked = true)
                    return SyncOutcome.SUCCESS
                }
            if (credential == null) {
                settings.status(config, "Add a meditation API key to synchronise", blocked = true)
                return SyncOutcome.SUCCESS
            }
            if (!current(settings, config, testing)) return SyncOutcome.SUCCESS
            if (testing) {
                val response = request(config.serverUrl, credential, null)
                if (response.code != 200) return handle(response, settings, config)
                val body = JSONObject(response.body)
                val permissions = body.optJSONArray("permissions")
                val scopes =
                    if (permissions == null) emptyList()
                    else (0 until permissions.length()).map { permissions.optString(it) }
                val versions =
                    body.optJSONObject("supportedEvents")?.optJSONArray(WellnessSession.TYPE)
                val compatible =
                    body.optString("application") == "meditation" &&
                        "meditation:write" in scopes &&
                        versions != null &&
                        (0 until versions.length()).any { versions.optInt(it) == 1 }
                settings.status(
                    config,
                    if (compatible) "Connection verified; meditation uploads supported"
                    else "Check the meditation key permissions and update the backend",
                    blocked = !compatible,
                )
                return SyncOutcome.SUCCESS
            }
            return Store(context).use { history ->
                val installation = history.wellnessInstallation()
                val rows = history.wellnessPending(config.serverUrl)
                if (rows.isEmpty()) {
                    settings.status(
                        config,
                        "No eligible pending uploads; rejected records need manual retry",
                    )
                    return@use SyncOutcome.SUCCESS
                }
                for (row in rows) {
                    if (!current(settings, config, false)) return@use SyncOutcome.SUCCESS
                    if (!history.wellnessExists(row.id)) continue
                    val response =
                        request(
                            config.serverUrl,
                            credential,
                            WellnessSession.event(row, installation),
                        )
                    if (!current(settings, config, false)) return@use SyncOutcome.SUCCESS
                    if (
                        WellnessConnection.acknowledged(
                            response.code,
                            response.body,
                            WellnessSession.eventId(row.id),
                        )
                    ) {
                        synchronized(WellnessSettings.lock) {
                            if (current(settings, config, false)) {
                                history.wellnessDelivered(row.id, config.serverUrl)
                                settings.status(
                                    config,
                                    "Synchronised successfully",
                                    acknowledged = true,
                                )
                            }
                        }
                    } else if (response.code in setOf(400, 409, 413, 415, 422)) {
                        history.wellnessDelivered(row.id, config.serverUrl, rejected = true)
                        settings.status(
                            config,
                            "Some sessions were rejected; local history is retained",
                        )
                    } else return@use handle(response, settings, config)
                }
                if (history.wellnessPending(config.serverUrl).isNotEmpty()) SyncOutcome.RETRY
                else SyncOutcome.SUCCESS
            }
        } catch (_: Exception) {
            try {
                settings.status(config, "Sync unavailable; history remains on this device")
            } catch (_: Exception) {}
            return SyncOutcome.RETRY
        }
    }

    private fun current(
        settings: WellnessSettings,
        original: WellnessConfiguration,
        testing: Boolean,
    ): Boolean {
        val now = settings.configuration()
        return !stopped() && now.revision == original.revision && (testing || now.enabled)
    }

    private fun handle(
        response: WellnessHttpClient.Response,
        settings: WellnessSettings,
        config: WellnessConfiguration,
    ): SyncOutcome {
        if (response.code == 401 || response.code == 403) {
            settings.status(
                config,
                "Meditation key rejected; check credentials/permissions",
                blocked = true,
            )
            return SyncOutcome.SUCCESS
        }
        if (response.code in 300..399 || response.code == 404) {
            settings.status(
                config,
                "Check the HTTPS server URL; redirects are not followed",
                blocked = true,
            )
            return SyncOutcome.SUCCESS
        }
        settings.status(
            config,
            "Server unavailable or unexpected response; will retry",
            retryAt = System.currentTimeMillis() + maxOf(30, response.retryAfterSeconds) * 1000,
        )
        return SyncOutcome.RETRY
    }
}

class WellnessSyncWorker(context: Context, parameters: WorkerParameters) :
    Worker(context, parameters) {
    companion object {
        private val networkLock = java.util.concurrent.locks.ReentrantLock()
    }

    override fun doWork(): Result {
        if (!networkLock.tryLock()) return Result.retry()
        return try {
            when (
                WellnessSynchronizer(applicationContext, stopped = { isStopped })
                    .run(inputData.getBoolean("test", false))
            ) {
                SyncOutcome.SUCCESS -> Result.success()
                SyncOutcome.RETRY -> Result.retry()
            }
        } finally {
            networkLock.unlock()
        }
    }
}
