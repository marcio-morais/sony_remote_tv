package app.sonyremote.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Result of asking GitHub for the newest published build. */
data class UpdateInfo(val latestVersion: String, val isNewer: Boolean)

/**
 * Compares the installed build with the rolling "latest" GitHub release. CI names that
 * release "SonyRemote 0.2.<build number>", so the last number says which build it is.
 */
class UpdateChecker(private val http: OkHttpClient) {

    /** Null when GitHub could not be reached or the release has no version in its name. */
    suspend fun check(installedVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val name = JSONObject(response.body?.string().orEmpty()).optString("name")
                val latest = VERSION_PATTERN.find(name)?.value ?: return@use null
                UpdateInfo(latest, buildOf(latest) > buildOf(installedVersion))
            }
        } catch (error: Exception) {
            null
        }
    }

    private fun buildOf(version: String): Int = version.substringAfterLast('.').toIntOrNull() ?: 0

    companion object {
        const val REPO = "marcio-morais/sony_remote_tv"
        const val APK_URL = "https://github.com/$REPO/releases/latest/download/SonyRemote.apk"
        const val RELEASES_URL = "https://github.com/$REPO/releases/latest"
        private val VERSION_PATTERN = Regex("""\d+\.\d+\.\d+""")
    }
}
