package uk.co.meditationtimer

import java.net.URL
import javax.net.ssl.HttpsURLConnection

class WellnessHttpClient {
    data class Response(val code: Int, val body: String, val retryAfterSeconds: Long = 0)

    fun request(server: String, credential: String, event: org.json.JSONObject? = null): Response {
        val base = WellnessConnection.normalizeServer(server)
        val path = if (event == null) "/api/v1/connection" else "/api/v1/events"
        val connection = URL(base + path).openConnection() as HttpsURLConnection
        try {
            // Use platform trust/hostname checks. Never follow a credential-bearing redirect.
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = if (event == null) "GET" else "POST"
            connection.setRequestProperty("Authorization", "Bearer $credential")
            connection.setRequestProperty("Accept", "application/json")
            if (event != null) {
                val bytes = event.toString().toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            // Error bodies are deliberately ignored. They can contain arbitrary proxy/server text.
            val body =
                if (code in 200..299)
                    connection.inputStream.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(1024)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            check(output.size() + count <= 32_768) { "Unexpected server response" }
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    }
                else ""
            return Response(
                code,
                body,
                WellnessConnection.retryAfter(connection.getHeaderField("Retry-After")),
            )
        } finally {
            connection.disconnect()
        }
    }
}
