package fr.kingsrequin.horizon.link

import fr.kingsrequin.horizon.protocol.AppInfo
import fr.kingsrequin.horizon.protocol.FromPc
import fr.kingsrequin.horizon.protocol.HeadsetInfo
import fr.kingsrequin.horizon.protocol.MediaInfo
import fr.kingsrequin.horizon.protocol.OutputInfo
import fr.kingsrequin.horizon.protocol.SessionInfo
import fr.kingsrequin.horizon.protocol.TimerInfo
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

    private fun mixer(volume: Float = 0.5f) =
        FromPc.MixerState(listOf(OutputInfo("out-1", "Casque", "headset")), "out-1", listOf(AppInfo("spotify.exe", "Spotify", volume, false, true)))

    private fun actions(timer: TimerInfo?) = FromPc.ActionsState(true, timer, listOf(5, 25), HeadsetInfo("Casque", 92, false))

    @Test
    fun theTimerCountsDownFromReception() {
        remote.apply(actions(TimerInfo(760_000, 1_500_000, false)))
        val timer = remote.view.value.actions!!.timer!!
        assertEquals(1_000L, timer.receivedAtMs)
        assertEquals(760_000L, timer.remainingAt(1_000))
        assertEquals(750_000L, timer.remainingAt(11_000))
        assertEquals("jamais sous zéro", 0L, timer.remainingAt(10_000_000))
    }

    @Test
    fun aPausedTimerDoesNotMove() {
        remote.apply(actions(TimerInfo(300_000, 1_500_000, true)))
        assertEquals(300_000L, remote.view.value.actions!!.timer!!.remainingAt(500_000))
        remote.apply(actions(null))
        assertNull("arrêté : plus de minuteur", remote.view.value.actions!!.timer)
    }

    @Test
    fun anAppEchoDoesNotUndoTheGesture() {
        remote.apply(mixer(0.5f))
        remote.localAppVolume("spotify.exe", 0.9f)
        assertEquals(0.9f, remote.view.value.mixer!!.apps.single().volume)
        now += 300
        remote.apply(mixer(0.6f))
        assertEquals("écho : le geste tient", 0.9f, remote.view.value.mixer!!.apps.single().volume)
        now += 1_500
        remote.apply(mixer(0.6f))
        assertEquals(0.6f, remote.view.value.mixer!!.apps.single().volume)
    }

    @Test
    fun theMixerAndActionsResetWithTheSession() {
        remote.apply(mixer())
        remote.apply(actions(null))
        remote.reset()
        assertNull(remote.view.value.mixer)
        assertNull(remote.view.value.actions)
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
