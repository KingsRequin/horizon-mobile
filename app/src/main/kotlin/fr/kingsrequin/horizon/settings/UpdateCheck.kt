package fr.kingsrequin.horizon.settings

import android.content.Context
import androidx.core.content.edit
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Une version plus récente de l'appli, et où télécharger son APK. */
data class Update(val version: String, val url: String)

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Les nombres d'une version « 0.10.2 » (ce qui suit un tiret, « -dev », est ignoré) ; `null` si ce n'en est pas une. */
private fun numbers(version: String): List<Int>? = version.substringBefore('-').split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }

/**
 * La version proposée par la réponse de GitHub (« dernière Release ») si elle est strictement
 * plus récente que `current` et qu'elle a un APK à télécharger en https ; sinon `null`.
 */
fun newerVersion(current: String, releaseJson: String): Update? {
    val release = try {
        Json.parseToJsonElement(releaseJson) as? JsonObject
    } catch (_: SerializationException) {
        null
    } ?: return null
    val version = release.text("tag_name")?.removePrefix("v") ?: return null
    val (latest, mine) = (numbers(version) ?: return null) to (numbers(current) ?: return null)
    val newer = (0 until maxOf(latest.size, mine.size)).firstNotNullOfOrNull { i ->
        (latest.getOrElse(i) { 0 }.compareTo(mine.getOrElse(i) { 0 })).takeIf { it != 0 }
    }
    if (newer == null || newer < 0) return null
    val apk = (release["assets"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().firstOrNull { it.text("name")?.endsWith(".apk") == true }
    val url = apk?.text("browser_download_url")?.takeIf { it.startsWith("https://") } ?: return null
    return Update(version, url)
}

private const val LATEST = "https://api.github.com/repos/KingsRequin/horizon-mobile/releases/latest"
private const val PREFS = "mise-a-jour"
private const val KEY_CHECKED = "verifie"
private const val KEY_VERSION = "version"
private const val KEY_URL = "adresse"
private const val DAY_MS = 86_400_000L

/**
 * La mise à jour connue, en la demandant à GitHub au plus une fois par jour (à l'ouverture de
 * l'appli). L'appli ne télécharge ni n'installe rien : elle montre un lien, que le navigateur ouvre.
 */
suspend fun checkForUpdate(context: Context, current: String, nowMs: Long): Update? = withContext(Dispatchers.IO) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (nowMs - prefs.getLong(KEY_CHECKED, 0) >= DAY_MS) {
        val found = fetch()?.let { newerVersion(current, it) }
        prefs.edit {
            putLong(KEY_CHECKED, nowMs)
            putString(KEY_VERSION, found?.version)
            putString(KEY_URL, found?.url)
        }
    }
    val (version, url) = prefs.getString(KEY_VERSION, null) to prefs.getString(KEY_URL, null)
    // La version gardée peut avoir été installée depuis.
    if (version != null && url != null) newerVersion(current, """{"tag_name":"v$version","assets":[{"name":"a.apk","browser_download_url":"$url"}]}""") else null
}

private fun fetch(): String? = try {
    val connection = (URL(LATEST).openConnection() as HttpURLConnection).apply {
        connectTimeout = 5_000
        readTimeout = 5_000
        setRequestProperty("Accept", "application/vnd.github+json")
    }
    try {
        if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.inputStream.bufferedReader().use { it.readText() } else null
    } finally {
        connection.disconnect()
    }
} catch (_: IOException) {
    null
}
