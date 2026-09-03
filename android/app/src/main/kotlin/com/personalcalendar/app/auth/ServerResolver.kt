package com.personalcalendar.app.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * The API server is reachable at more than one address: the LAN IP (fast, works at home)
 * and a port-forwarded public IP (works away from home). Each address is probed with a
 * short-timeout /health check, in order, and whichever answers first is cached and reused
 * for the rest of the process — this makes the same app build work both at home and away
 * from it without the user having to pick an address.
 */
object ServerResolver {
    private val candidates = listOf(
        "http://192.168.45.250:4000",
        "http://211.208.252.113:4000"
    )

    @Volatile
    private var resolved: String? = null

    suspend fun resolve(): String {
        resolved?.let { return it }
        for (base in candidates) {
            if (probeHealth(base)) {
                resolved = base
                return base
            }
        }
        return candidates.first()
    }

    fun defaultServerUrl(): String = candidates.first()

    private suspend fun probeHealth(base: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("$base/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
            }
            connection.responseCode in 200..299
        }.getOrDefault(false)
    }
}
