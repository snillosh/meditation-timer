package uk.co.meditationtimer

import java.net.URI
import java.time.Instant
import java.util.Locale
import org.json.JSONObject

object WellnessConnection {
    const val DEFAULT_SERVER = "https://wellness-server.bsalter.online"

    fun normalizeServer(value: String): String {
        val uri = URI(value.trim())
        require(
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null &&
                (uri.port == -1 || uri.port in 1..65535) &&
                (uri.rawPath ?: "").matches(Regex("(/[A-Za-z0-9._~-]+)*/*")) &&
                uri.normalize() == uri
        ) {
            "Use an HTTPS server URL without credentials, query or fragment"
        }
        return URI(
                "https",
                null,
                uri.host.lowercase(Locale.ROOT),
                if (uri.port == 443) -1 else uri.port,
                uri.path.orEmpty().trimEnd('/'),
                null,
                null,
            )
            .toASCIIString()
    }

    fun retryAfter(value: String?, now: Instant = Instant.now()): Long {
        if (value == null) return 0
        value.toLongOrNull()?.let {
            return it.coerceIn(0, 86_400)
        }
        return try {
            java.time.Duration.between(
                    now,
                    java.time.ZonedDateTime.parse(
                            value,
                            java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME,
                        )
                        .toInstant(),
                )
                .seconds
                .coerceIn(0, 86_400)
        } catch (_: Exception) {
            0
        }
    }

    fun validKey(value: String): Boolean =
        value.matches(
            Regex(
                "wk_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.[A-Za-z0-9_-]{43}"
            )
        )

    fun acknowledged(code: Int, body: String, eventId: String): Boolean =
        try {
            val json = JSONObject(body)
            val expected = if (code == 201) "accepted" else if (code == 200) "duplicate" else ""
            expected.isNotEmpty() &&
                json.getString("eventId") == eventId &&
                json.getString("status") == expected &&
                Instant.parse(json.getString("receivedAt")) != null
        } catch (_: Exception) {
            false
        }
}

data class WellnessConfiguration(val serverUrl: String, val enabled: Boolean, val revision: String)
