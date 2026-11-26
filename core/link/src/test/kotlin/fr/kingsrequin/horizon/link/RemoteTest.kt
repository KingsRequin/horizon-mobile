package fr.kingsrequin.horizon.link

import fr.kingsrequin.horizon.protocol.FromPc
import fr.kingsrequin.horizon.protocol.MediaInfo
import fr.kingsrequin.horizon.protocol.SessionInfo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteTest {
    private var now = 1_000L
    private val remote = Remote { now }

    private fun song(title: String = "FROGZ", key: String? = "cle-a", playing: Boolean = true, position: Long = 68_000, duration: Long = 233_000) =
        MediaInfo("Spotify.exe", "Spotify", title, "SPLATA", playing, position, duration, key)

    private fun state(media: MediaInfo?) = FromPc.MediaState(media, listOf(SessionInfo("Spotify.exe", "Spotify")))

    /** Trois octets de JPEG, et leur base64 tel qu'il passe sur le réseau. */
    private val cover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val cover64 = "/9j/"

    @Test
    fun thePositionCountsFromReceptionNotFromThePcClock() {
        remote.apply(state(song()))
        val media = remote.view.value.media!!
        assertEquals(1_000L, media.receivedAtMs)
        assertEquals(68_000L, media.positionAt(1_000))
        assertEquals(78_000L, media.positionAt(11_000))
        assertEquals("bornée à la durée", 233_000L, media.positionAt(1_000_000))
        remote.apply(state(song(playing = false)))
        assertEquals("en pause : elle ne bouge pas", 68_000L, remote.view.value.media!!.positionAt(50_000))
    }

    @Test
    fun aLiveStreamHasNoEnd() {
        remote.apply(state(song(duration = 0)))
        assertEquals(1_068_000L, remote.view.value.media!!.positionAt(1_001_000))
    }

    @Test
    fun nothingPlayingClearsTheMediaAndItsCover() {
        remote.apply(state(song()))
        remote.apply(FromPc.MediaArt("cle-a", cover64))
        remote.apply(FromPc.MediaState(null, emptyList()))
        val view = remote.view.value
        assertNull(view.media)
        assertNull(view.art)
        assertEquals(emptyList<SessionInfo>(), view.sessions)
    }

    @Test
    fun theCoverIsKeptOnlyForItsTrack() {
        remote.apply(state(song()))
        remote.apply(FromPc.MediaArt("cle-a", cover64))
        assertArrayEquals(cover, remote.view.value.art)
        remote.apply(state(song(position = 90_000)))
        assertArrayEquals("même morceau : même pochette", cover, remote.view.value.art)
        remote.apply(state(song(title = "Autre", key = "cle-b")))
        assertNull("nouveau morceau : pas l'ancienne pochette", remote.view.value.art)
        remote.apply(FromPc.MediaArt("cle-a", cover64))
        assertNull("pochette d'un autre morceau : ignorée", remote.view.value.art)
        remote.apply(FromPc.MediaArt("cle-b", "pas du base64 !"))
        assertNull("illisible : pas de pochette", remote.view.value.art)
    }

    @Test
    fun aVolumeEchoDoesNotUndoTheGesture() {
        remote.apply(FromPc.VolumeState(0.5f, false))
        remote.localVolume(0.8f)
        assertEquals(0.8f, remote.view.value.volume)
        now += 300
        remote.apply(FromPc.VolumeState(0.6f, false))
        assertEquals("écho d'une étape du geste : ignoré", 0.8f, remote.view.value.volume)
        now += 1_500
        remote.apply(FromPc.VolumeState(0.8f, true))
        assertEquals(0.8f, remote.view.value.volume)
        assertEquals(true, remote.view.value.muted)
    }

    @Test
    fun aMuteDuringTheEchoWindowIsKept() {
        remote.apply(FromPc.VolumeState(0.5f, false))
        remote.localVolume(0.8f)
        now += 300
        remote.apply(FromPc.VolumeState(0.6f, true))
        assertEquals("le volume du geste tient", 0.8f, remote.view.value.volume)
        assertEquals("le muet, lui, passe", true, remote.view.value.muted)
    }

    @Test
    fun aNewSessionStartsFromNothing() {
        remote.apply(state(song()))
        remote.apply(FromPc.MediaArt("cle-a", cover64))
        remote.apply(FromPc.VolumeState(0.5f, true))
        remote.reset()
        assertEquals(RemoteView(), remote.view.value)
    }
}
