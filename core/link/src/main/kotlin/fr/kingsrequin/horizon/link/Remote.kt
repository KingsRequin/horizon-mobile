package fr.kingsrequin.horizon.link

import fr.kingsrequin.horizon.protocol.FromPc
import fr.kingsrequin.horizon.protocol.MediaInfo
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

/** Ce que montre la télécommande. `art` : JPEG de la pochette de [media], s'il est arrivé. */
data class RemoteView(
    val media: MediaNow? = null,
    val sessions: List<SessionInfo> = emptyList(),
    val art: ByteArray? = null,
    val volume: Float? = null,
    val muted: Boolean = false,
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
            else -> Unit
        }
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
