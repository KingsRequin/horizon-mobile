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

/** Une version plus récente de l'appli, où télécharger son APK et le fichier de ses sommes. */
data class Update(val version: String, val url: String, val sumsUrl: String)

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Les nombres d'une version « 0.10.2 » (ce qui suit un tiret, « -dev », est ignoré) ; `null` si ce n'en est pas une. */
private fun numbers(version: String): List<Int>? = version.substringBefore('-').split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }

/**
 * La version proposée par la réponse de GitHub (« dernière Release ») si elle est strictement
 * plus récente que `current` et qu'elle a un APK et son `SHA256SUMS` à télécharger en https
 * depuis ce dépôt ; sinon `null` (sans somme à vérifier, rien n'est proposé).
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
    val assets = (release["assets"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
    // Seulement des fichiers des versions de ce dépôt.
    fun link(match: (String) -> Boolean) = assets.firstOrNull { it.text("name")?.let(match) == true }?.text("browser_download_url")?.takeIf { it.startsWith(DOWNLOADS) }
    val url = link { it.endsWith(".apk") } ?: return null
    val sums = link { it == "SHA256SUMS" } ?: return null
    return Update(version, url, sums)
}

/**
 * La somme SHA-256 attendue pour `fileName` dans un fichier `SHA256SUMS` (lignes
 * « somme *nom » ou « somme  nom ») ; `null` si le fichier n'y est pas ou que la somme est mal formée.
 */
fun expectedSha256(sums: String, fileName: String): String? = sums.lineSequence()
    .map { it.trim() }
    .mapNotNull { line ->
        val hash = line.substringBefore(' ')
        val name = line.substringAfter(' ').trimStart(' ', '*')
        hash.lowercase().takeIf { name == fileName && it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }
    .firstOrNull()

/** Une empreinte écrite en hexadécimal minuscule. */
fun sha256Hex(digest: ByteArray): String = digest.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

private const val LATEST = "https://api.github.com/repos/KingsRequin/horizon-mobile/releases/latest"
private const val DOWNLOADS = "https://github.com/KingsRequin/horizon-mobile/releases/download/"
private const val PREFS = "mise-a-jour"
private const val KEY_CHECKED = "verifie"
private const val KEY_VERSION = "version"
private const val KEY_URL = "adresse"
private const val KEY_SUMS = "sommes"

/** Écart entre deux questions à GitHub : une version publiée est vue dans la demi-journée. */
private const val CHECK_EVERY_MS = 6 * 3_600_000L

/**
 * La mise à jour connue, en la demandant à GitHub au plus toutes les 6 heures (à l'ouverture de
 * l'appli). Le téléchargement et l'installation se font à la demande ([Updater]).
 */
suspend fun checkForUpdate(context: Context, current: String, nowMs: Long): Update? = withContext(Dispatchers.IO) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (nowMs - prefs.getLong(KEY_CHECKED, 0) >= CHECK_EVERY_MS) {
        val found = fetch(current)?.let { newerVersion(current, it) }
        prefs.edit {
            putLong(KEY_CHECKED, nowMs)
            putString(KEY_VERSION, found?.version)
            putString(KEY_URL, found?.url)
            putString(KEY_SUMS, found?.sumsUrl)
        }
    }
    val (version, url) = prefs.getString(KEY_VERSION, null) to prefs.getString(KEY_URL, null)
    val sums = prefs.getString(KEY_SUMS, null)
    // La version gardée peut avoir été installée depuis.
    if (version != null && url != null && sums != null) Update(version, url, sums).takeIf { newerVersion(current, """{"tag_name":"v$version","assets":[{"name":"a.apk","browser_download_url":"$url"},{"name":"SHA256SUMS","browser_download_url":"$sums"}]}""") != null } else null
}

private fun fetch(current: String): String? = try {
    val connection = (URL(LATEST).openConnection() as HttpURLConnection).apply {
        connectTimeout = 5_000
        readTimeout = 5_000
        setRequestProperty("Accept", "application/vnd.github+json")
        // Un agent fixe : celui du système dirait le modèle du téléphone.
        setRequestProperty("User-Agent", "HorizonMobile/$current")
    }
    try {
        if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.inputStream.bufferedReader().use { it.readText() } else null
    } finally {
        connection.disconnect()
    }
} catch (_: IOException) {
    null
}
