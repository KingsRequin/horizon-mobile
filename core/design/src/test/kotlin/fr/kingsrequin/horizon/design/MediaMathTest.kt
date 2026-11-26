package fr.kingsrequin.horizon.design

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaMathTest {
    @Test
    fun theFallbackCoverCornerFollowsTheSide() {
        assertEquals("télécommande", 46.44f, coverCorner(172.dp).value, 0.01f)
        assertEquals("carte de lecture", 17.28f, coverCorner(64.dp).value, 0.01f)
    }

    @Test
    fun timesReadAsMinutesAndSeconds() {
        assertEquals("1:08", formatTime(68_000))
        assertEquals("3:53", formatTime(233_000))
        assertEquals("1:02:05", formatTime(3_725_000))
        assertEquals("seconde entamée : pas encore comptée", "0:59", formatTime(59_999))
        assertEquals("0:00", formatTime(0))
        assertEquals("0:00", formatTime(-5_000))
    }

    @Test
    fun theVolumeTicksEveryFivePercentAndHarderAtTheEnds() {
        assertEquals(VolumeTick.Step, volumeTick(0.48f, 0.51f))
        assertEquals(VolumeTick.Step, volumeTick(0.51f, 0.49f))
        assertEquals(VolumeTick.None, volumeTick(0.51f, 0.54f))
        assertEquals(VolumeTick.Edge, volumeTick(0.03f, 0f))
        assertEquals(VolumeTick.Edge, volumeTick(0.97f, 1f))
        assertEquals(VolumeTick.None, volumeTick(1f, 1f))
    }
}
