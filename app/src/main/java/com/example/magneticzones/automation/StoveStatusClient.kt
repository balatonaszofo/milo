package com.example.magneticzones.automation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant

data class StoveStatus(val state: String, val connected: Boolean, val observedAt: Instant?, val reason: String?)

class StoveStatusClient(private val baseUrl: String) {
    suspend fun fetch(): StoveStatus = withContext(Dispatchers.IO) {
        val connection = URL("${baseUrl.trimEnd('/')}/api/status").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 3_000
            connection.readTimeout = 3_000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) error("Tracker returned HTTP ${connection.responseCode}")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val observedAt = json.optString("observed_at").takeIf { it.isNotBlank() }?.let {
                runCatching { Instant.parse(it) }.getOrNull()
            }
            val pollSeconds = json.optDouble("poll_seconds", 5.0)
            val freshFor = maxOf(30L, (pollSeconds * 4).toLong())
            val recent = observedAt?.let { Duration.between(it, Instant.now()).abs().seconds <= freshFor } == true
            StoveStatus(
                state = json.optString("state", "unknown").lowercase(),
                connected = json.optString("health") == "fresh" && recent,
                observedAt = observedAt,
                reason = json.optString("reason").takeIf { it.isNotBlank() },
            )
        } finally {
            connection.disconnect()
        }
    }
}
