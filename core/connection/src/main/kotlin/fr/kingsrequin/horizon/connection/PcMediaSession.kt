package fr.kingsrequin.horizon.connection

import android.content.Context
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import fr.kingsrequin.horizon.link.MediaNow
import fr.kingsrequin.horizon.link.positionAt
import fr.kingsrequin.horizon.protocol.ToPc

/**
 * Ce que le système peut demander au lecteur du PC (commandes de `Player`) : rien sans lecture
 * ni sans le droit « Médias » ; sinon lecture et pause, morceaux précédent et suivant, et la
 * position quand la durée est connue (pas pour un direct). Fonction pure.
 */
fun sessionCommands(media: MediaNow?, granted: Set<String>): Set<Int> {
    if (media == null || "media" !in granted) return emptySet()
    val commands = mutableSetOf(
        Player.COMMAND_PLAY_PAUSE,
        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
        Player.COMMAND_GET_METADATA,
        Player.COMMAND_GET_TIMELINE,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    )
    if (media.durationMs > 0) commands += Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
    return commands
}

/**
 * La lecture du PC présentée au système, comme le fait KDE Connect : commandes dans le volet, sur
 * l'écran verrouillé et sur un casque Bluetooth. Le volume reste celui du téléphone (les touches
 * de volume ne règlent le PC que sur l'écran Télécommande, voir [Link.volumeKey]). Sur le fil
 * principal.
 */
@UnstableApi
class PcMediaSession(context: Context, send: (ToPc) -> Unit) {
    private val player = PcPlayer(send)
    val session: MediaSession = MediaSession.Builder(context, player).setId("horizon-pc").build()

    /** Vrai tant qu'une lecture est présentée au système. */
    val active: Boolean get() = player.shown != null

    /** Montre `media` avec ces commandes (aucune : plus rien) ; `cover` : la pochette en JPEG. */
    fun update(media: MediaNow?, commands: Set<Int>, cover: ByteArray?) = player.show(media.takeIf { commands.isNotEmpty() }, commands, cover)

    fun release() {
        session.release()
        player.release()
    }
}

/**
 * Le lecteur du PC vu par Media3. La liste de lecture a trois éléments, le morceau en cours au
 * milieu : sans voisins, Media3 ignorerait « précédent » et « suivant », que seul le PC sait
 * faire.
 */
@UnstableApi
private class PcPlayer(private val send: (ToPc) -> Unit) : SimpleBasePlayer(Looper.getMainLooper()) {
    var shown: MediaNow? = null
        private set
    private var commands: Set<Int> = emptySet()
    private var cover: ByteArray? = null

    fun show(media: MediaNow?, commands: Set<Int>, cover: ByteArray?) {
        shown = media
        this.commands = commands
        this.cover = cover
        invalidateState()
    }

    override fun getState(): State {
        val media = shown ?: return State.Builder().setAvailableCommands(Player.Commands.EMPTY).setPlaybackState(Player.STATE_IDLE).build()
        val available = Player.Commands.Builder().addAll(*commands.toIntArray()).build()
        val metadata = MediaMetadata.Builder()
            .setTitle(media.title.ifBlank { media.appName })
            .setArtist(media.artist)
            .setDisplayTitle(media.title.ifBlank { media.appName })
            .setSubtitle(media.appName)
            .apply { cover?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) } }
            .build()
        val live = media.durationMs <= 0
        val current = MediaItemData.Builder("pc:${media.appId}:${media.title}")
            .setMediaMetadata(metadata)
            .setDurationUs(if (live) C.TIME_UNSET else media.durationMs * 1000)
            .setIsSeekable(!live)
            .setIsDynamic(live)
            .build()
        // La position avance seule pendant la lecture, comptée depuis la réception.
        val position = PositionSupplier.getExtrapolating(media.positionAt(System.currentTimeMillis()), if (media.playing) 1f else 0f)
        return State.Builder()
            .setAvailableCommands(available)
            .setPlaylist(listOf(neighbour("avant"), current, neighbour("apres")))
            .setCurrentMediaItemIndex(1)
            .setPlayWhenReady(media.playing, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
            .setPlaybackState(Player.STATE_READY)
            .setContentPositionMs(position)
            .build()
    }

    private fun neighbour(uid: String) = MediaItemData.Builder(uid).build()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        send(ToPc.MediaCommand("play_pause"))
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> send(ToPc.MediaCommand("next"))
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> send(ToPc.MediaCommand("previous"))
            else -> if (positionMs != C.TIME_UNSET) send(ToPc.MediaSeek(positionMs.coerceAtLeast(0)))
        }
        return Futures.immediateVoidFuture()
    }
}
