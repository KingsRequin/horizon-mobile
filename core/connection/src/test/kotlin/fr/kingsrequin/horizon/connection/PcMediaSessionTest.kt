package fr.kingsrequin.horizon.connection

import androidx.media3.common.Player
import fr.kingsrequin.horizon.link.MediaNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcMediaSessionTest {
    private fun song(duration: Long = 233_000) = MediaNow("Spotify.exe", "Spotify", "FROGZ", "SPLATA", true, 68_000, duration, null, 0)

    private val media = setOf("battery", "media")

    @Test
    fun theSessionActionsFollowTheMedia() {
        assertEquals("rien ne joue", emptySet<Int>(), sessionCommands(null, media))
        assertEquals("sans le droit", emptySet<Int>(), sessionCommands(song(), setOf("battery")))
        val withSeek = sessionCommands(song(), media)
        for (command in listOf(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            assertTrue("commande $command", command in withSeek)
        }
        assertEquals("un direct ne se déplace pas", withSeek - Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, sessionCommands(song(duration = 0), media))
    }
}
