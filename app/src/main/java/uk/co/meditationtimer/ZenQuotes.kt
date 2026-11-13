package uk.co.meditationtimer

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class Quote(val text: String, val author: String)

/** No history, identifiers, API key or other app data is sent to this public endpoint. */
class ZenQuotes(
    private val elapsed: () -> Long = { SystemClock.elapsedRealtime() },
    private val request: () -> String? = ::requestQuote
) {
    private var cached: Quote? = null
    private var fetchedAt = Long.MIN_VALUE
    private var attemptedAt = Long.MIN_VALUE

    // Call only from the foreground connectivity collector. No background jobs or automatic retries.
    suspend fun load(): Quote? = withContext(Dispatchers.IO) {
        val now = elapsed()
        if (cached != null && now - fetchedAt < 60 * 60 * 1000L) return@withContext cached
        if (attemptedAt != Long.MIN_VALUE && now - attemptedAt < 60000) return@withContext null
        attemptedAt = now
        val result = try { request()?.let(::parse) } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
        cached = result
        if (result != null) fetchedAt = now
        result
    }

    companion object {
        const val ATTRIBUTION_URL = "https://zenquotes.io/"
        private const val MAX_BYTES = 64 * 1024

        fun parse(body: String): Quote? = try {
            if (body.toByteArray(Charsets.UTF_8).size > MAX_BYTES) null else {
                val parser = JSONTokener(body)
                val rows = parser.nextValue() as? JSONArray
                if (rows == null || rows.length() != 1 || parser.nextClean() != '\u0000') null else {
                    val row = rows.getJSONObject(0)
                    val text = (row.opt("q") as? String)?.trim().orEmpty()
                    val author = (row.opt("a") as? String)?.trim().orEmpty()
                    if (text.length !in 1..2000 || author.length !in 1..200 || author.equals("zenquotes.io", ignoreCase = true)) null
                    else Quote(text, author)
                }
            }
        } catch (_: Exception) { null }

        private fun requestQuote(): String? {
            val connection = URL("https://zenquotes.io/api/random").openConnection() as HttpURLConnection
            return try {
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) null else connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > MAX_BYTES) return null
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
            } finally { connection.disconnect() }
        }
    }
}

/** Validated internet only: captive portals and loss of connectivity hide the quote immediately. */
fun quoteConnectivity(context: Context) = callbackFlow {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    fun validated(capabilities: NetworkCapabilities?) = capabilities?.let {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } == true
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            trySend(validated(capabilities))
        }
        override fun onLost(network: Network) { trySend(false) }
    }
    try {
        manager.registerDefaultNetworkCallback(callback)
        trySend(validated(manager.getNetworkCapabilities(manager.activeNetwork)))
    } catch (_: Exception) { trySend(false) }
    awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
}.distinctUntilChanged()
