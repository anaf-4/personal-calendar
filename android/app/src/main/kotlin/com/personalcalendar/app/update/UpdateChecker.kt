package com.personalcalendar.app.update

import com.personalcalendar.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String
)

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val assets: List<GithubAsset> = emptyList(),
    @SerialName("html_url") val htmlUrl: String = ""
) {
    val versionName: String get() = tagName.removePrefix("v")
    val apkAsset: GithubAsset? get() = assets.find { it.name.endsWith(".apk") }
}

object UpdateChecker {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkLatest(currentVersionName: String): GithubRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 8000
                readTimeout = 8000
            }
            connection.inputStream.use { stream ->
                val body = stream.bufferedReader().readText()
                val release = json.decodeFromString<GithubRelease>(body)
                if (isNewer(release.versionName, currentVersionName)) release else null
            }
        }.getOrNull()
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }
}
