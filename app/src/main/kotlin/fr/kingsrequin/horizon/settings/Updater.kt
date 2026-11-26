package fr.kingsrequin.horizon.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Où en est la mise à jour de l'appli. */
sealed interface UpdateState {
    data object Idle : UpdateState

    /** `percent` : de 0 à 100, ou `null` si la taille n'est pas connue. */
    data class Downloading(val percent: Int?) : UpdateState

    /** L'APK est vérifié ; Android doit encore permettre à l'appli d'installer des applis. */
    data class NeedsPermission(val apk: File) : UpdateState

    /** L'APK est vérifié et l'écran d'installation d'Android a été ouvert. */
    data class Ready(val apk: File) : UpdateState

    data object Failed : UpdateState
}

/**
 * La mise à jour dans l'appli : télécharge l'APK de la Release dans le cache, vérifie sa somme
 * SHA-256 contre le `SHA256SUMS` de la même Release, puis ouvre l'écran d'installation d'Android
 * (qui demande toujours la confirmation de l'utilisateur, et refuse un APK signé par une autre
 * clé que celle de l'appli installée).
 */
object Updater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val current = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = current.asStateFlow()

    /** Le bouton « Mettre à jour » : télécharge, ou relance l'installation d'un APK déjà vérifié. */
    fun start(context: Context, update: Update) {
        val app = context.applicationContext
        when (val s = current.value) {
            is UpdateState.Downloading -> return
            is UpdateState.Ready -> return install(app, s.apk)
            is UpdateState.NeedsPermission -> return install(app, s.apk)
            UpdateState.Idle, UpdateState.Failed -> Unit
        }
        current.value = UpdateState.Downloading(null)
        scope.launch {
            val apk = try {
                download(app, update)
            } catch (_: IOException) {
                null
            }
            if (apk == null) {
                current.value = UpdateState.Failed
            } else {
                install(app, apk)
            }
        }
    }

    /** À chaque retour dans l'appli : si l'autorisation vient d'être donnée, l'installation part. */
    fun resumed(context: Context) {
        val s = current.value
        if (s is UpdateState.NeedsPermission && canInstall(context)) install(context.applicationContext, s.apk)
    }

    /** L'APK téléchargé et vérifié, ou `null` si sa somme ne correspond pas. */
    private fun download(context: Context, update: Update): File? {
        val name = update.url.substringAfterLast('/')
        val expected = expectedSha256(String(fetch(update.sumsUrl, MAX_SUMS), Charsets.UTF_8), name) ?: return null
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val apk = File(dir, name.takeIf { it.endsWith(".apk") && '/' !in it } ?: "horizon-mobile.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        open(update.url).let { connection ->
            try {
                val total = connection.contentLengthLong
                var read = 0L
                connection.inputStream.use { input ->
                    apk.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            read += n
                            if (read > MAX_APK) throw IOException("APK trop gros")
                            current.value = UpdateState.Downloading(if (total > 0) (read * 100 / total).toInt() else null)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        if (sha256Hex(digest.digest()) != expected) {
            apk.delete()
            return null
        }
        return apk
    }

    private fun install(context: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            current.value = UpdateState.NeedsPermission(apk)
            // Réglage « installer des applis inconnues » de cette appli : au retour, [resumed] reprend.
            launch(context, Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.maj", apk)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        current.value = if (launch(context, intent)) UpdateState.Ready(apk) else UpdateState.Failed
    }

    /** Android 8 et plus demande une autorisation par appli ; avant, le réglage général suffit. */
    private fun canInstall(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "HorizonMobile")
        if (responseCode != HttpURLConnection.HTTP_OK) throw IOException("réponse $responseCode")
    }

    /** Un petit fichier entier en mémoire, borné à `max` octets. */
    private fun fetch(url: String, max: Int): ByteArray {
        val connection = open(url)
        try {
            val bytes = connection.inputStream.use { it.readNBytesCompat(max + 1) }
            if (bytes.size > max) throw IOException("fichier trop gros")
            return bytes
        } finally {
            connection.disconnect()
        }
    }

    /** `readNBytes` n'existe pas avant Android 13 : lecture bornée à la main. */
    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** Dossier du cache où l'APK est posé (déclaré dans `res/xml/maj_paths.xml`). */
    private const val DIR = "maj"
    private const val MAX_SUMS = 16 * 1024
    private const val MAX_APK = 64L * 1024 * 1024
}
