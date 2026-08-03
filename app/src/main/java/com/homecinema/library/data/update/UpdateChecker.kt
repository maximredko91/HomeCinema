package com.homecinema.library.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/maximredko91/HomeCinema/releases/latest"

data class ReleaseInfo(val version: String, val htmlUrl: String)

/**
 * Checks GitHub Releases for a newer build than the one installed. Distribution is manual
 * (friends install the APK from a GitHub Release, there's no Play Store auto-update), so this
 * is the only way anyone finds out a new version exists. Requires the repo to be public - the
 * unauthenticated API 404s on a private repo, and embedding any kind of access token in a
 * distributed APK isn't safe to do.
 */
object UpdateChecker {
    suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            ReleaseInfo(
                version = json.getString("tag_name").removePrefix("v"),
                htmlUrl = json.getString("html_url")
            )
        }.getOrNull()
    }
}

/**
 * True if [latest] is a newer version than [current] - both "X.Y" / "X.Y.Z"-style strings,
 * compared numerically component by component (not lexicographically - "1.9" must lose to
 * "1.10"). Missing/non-numeric components are treated as 0.
 */
fun isNewerVersion(current: String, latest: String): Boolean {
    val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
    val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
        val c = currentParts.getOrElse(i) { 0 }
        val l = latestParts.getOrElse(i) { 0 }
        if (l != c) return l > c
    }
    return false
}
