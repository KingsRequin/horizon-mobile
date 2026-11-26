package fr.kingsrequin.horizon.link

import fr.kingsrequin.horizon.protocol.AppInfo
import fr.kingsrequin.horizon.protocol.FromPc
import fr.kingsrequin.horizon.protocol.HeadsetInfo
import fr.kingsrequin.horizon.protocol.MediaInfo
import fr.kingsrequin.horizon.protocol.OutputInfo
import fr.kingsrequin.horizon.protocol.SessionInfo
import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** La lecture en cours, telle que reçue ; `receivedAtMs` : horloge du téléphone à la réception. */
data class MediaNow(
    val appId: String,
    val appName: String,
    val title: String,
    val artist: String,
    val playing: Boolean,
    val positionMs: Long,
    /** 0 : durée inconnue (direct). */
    val durationMs: Long,
    val artKey: String?,
    val receivedAtMs: Long,
)

/**
 * Position à l'instant `nowMs` (horloge du téléphone) : celle reçue, plus le temps écoulé depuis
 * la réception pendant la lecture. Jamais au-delà de la durée quand elle est connue. L'horloge du
 * PC n'entre pas en compte : les deux horloges peuvent différer de plusieurs secondes.
 */
fun MediaNow.positionAt(nowMs: Long): Long {
    if (!playing) return positionMs
    val position = positionMs + (nowMs - receivedAtMs).coerceAtLeast(0)
    return if (durationMs > 0) position.coerceAtMost(durationMs) else position
}

/** Le mixeur du PC tel que reçu. */
data class MixerNow(val outputs: List<OutputInfo>, val defaultOutput: String?, val apps: List<AppInfo>)

/** Minuteur du PC ; `receivedAtMs` : horloge du téléphone à la réception. */
data class TimerNow(val remainingMs: Long, val totalMs: Long, val paused: Boolean, val receivedAtMs: Long)

/** Temps restant à `nowMs` : décompté depuis la réception, figé en pause, jamais sous zéro. */
fun TimerNow.remainingAt(nowMs: Long): Long =
    if (paused) remainingMs else (remainingMs - (nowMs - receivedAtMs).coerceAtLeast(0)).coerceAtLeast(0)

/** Les actions rapides du PC telles que reçues. */
data class ActionsNow(val dnd: Boolean, val timer: TimerNow?, val presets: List<Int>, val headset: HeadsetInfo?)

/**
 * Ce que montrent la télécommande et l'accueil. `art` : JPEG de la pochette de [media], s'il est
 * arrivé ; `mixer` et `actions` : nuls tant que le PC ne les a pas envoyés (écran fermé, PC 0.9).
 */
data class RemoteView(
    val media: MediaNow? = null,
    val sessions: List<SessionInfo> = emptyList(),
    val art: ByteArray? = null,
    val volume: Float? = null,
    val muted: Boolean = false,
    val mixer: MixerNow? = null,
    val actions: ActionsNow? = null,
)

/**
 * État de la télécommande, nourri par les messages du PC ([apply]) et par les gestes de
 * l'utilisateur ([localVolume]). Les méthodes s'appellent de n'importe quel fil.
 */
class Remote(private val clock: () -> Long) {
    private val current = MutableStateFlow(RemoteView())
    val view: StateFlow<RemoteView> = current.asStateFlow()

    /** Dernière pochette reçue et sa clé : montrée seulement avec la lecture qui porte cette clé. */
    private var art: Pair<String, ByteArray>? = null

    /** Heure du dernier geste sur le volume : le volume d'un écho du PC reçu juste après est ignoré. */
    private var gestureAtMs: Long? = null

    /** Même chose pour la jauge de chaque appli du mixeur (par clé). */
    private val appGestures = mutableMapOf<String, Long>()

    @Synchronized
    fun apply(msg: FromPc) {
        when (msg) {
            is FromPc.MediaState -> {
                val media = msg.media?.let { now(it) }
                if (media == null) art = null
                current.value = current.value.copy(media = media, sessions = msg.sessions, art = artFor(media))
            }
            is FromPc.MediaArt -> {
                val bytes = try {
                    Base64.Default.decode(msg.jpeg)
                } catch (_: IllegalArgumentException) {
                    null
                }
                art = bytes?.let { msg.artKey to it }
                current.value = current.value.copy(art = artFor(current.value.media))
            }
            is FromPc.VolumeState -> {
                // Pendant le geste, seul le volume reste celui du doigt : le muet, lui, passe.
                val gesture = gestureAtMs
                val echo = gesture != null && clock() - gesture < ECHO_MS
                current.value = current.value.copy(volume = if (echo) current.value.volume else msg.volume, muted = msg.muted)
            }
            is FromPc.MixerState -> {
                val time = clock()
                appGestures.values.removeAll { time - it >= ECHO_MS }
                val shown = current.value.mixer?.apps.orEmpty().associateBy { it.key }
                // Une jauge que l'utilisateur vient de régler garde sa valeur ; le muet, lui, passe.
                val apps = msg.apps.map { app -> shown[app.key]?.takeIf { app.key in appGestures }?.let { app.copy(volume = it.volume) } ?: app }
                current.value = current.value.copy(mixer = MixerNow(msg.outputs, msg.defaultOutput, apps))
            }
            is FromPc.ActionsState -> {
                val timer = msg.timer?.let { TimerNow(it.remainingMs, it.totalMs, it.paused, clock()) }
                current.value = current.value.copy(actions = ActionsNow(msg.dnd, timer, msg.timerPresets, msg.headset))
            }
            else -> Unit
        }
    }

    /** L'utilisateur vient de régler la jauge de l'appli `key` : affichée tout de suite, elle fait foi un instant. */
    @Synchronized
    fun localAppVolume(key: String, volume: Float) {
        val mixer = current.value.mixer ?: return
        appGestures[key] = clock()
        current.value = current.value.copy(mixer = mixer.copy(apps = mixer.apps.map { if (it.key == key) it.copy(volume = volume) else it }))
    }

    /** L'utilisateur vient de régler le volume : affiché tout de suite, et il fait foi un instant. */
    @Synchronized
    fun localVolume(volume: Float) {
        gestureAtMs = clock()
        current.value = current.value.copy(volume = volume)
    }

    /** La session est finie : plus rien de ce que le PC avait dit ne vaut. */
    @Synchronized
    fun reset() {
        art = null
        gestureAtMs = null
        appGestures.clear()
        current.value = RemoteView()
    }

    private fun now(m: MediaInfo) = MediaNow(m.appId, m.appName, m.title, m.artist, m.playing, m.positionMs, m.durationMs, m.artKey, clock())

    private fun artFor(media: MediaNow?): ByteArray? {
        val (key, bytes) = art ?: return null
        return bytes.takeIf { media?.artKey == key }
    }

    private companion object {
        /** Durée pendant laquelle le geste de l'utilisateur l'emporte sur le volume reçu. */
        const val ECHO_MS = 1_500L
    }
}
