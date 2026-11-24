package fr.kingsrequin.horizon.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkModeTest {
    @Test
    fun theServiceRunsOnlyWhenItIsUseful() {
        assertTrue(shouldRun(LinkMode.Continuous, paired = true, appVisible = false))
        assertFalse(shouldRun(LinkMode.Continuous, paired = false, appVisible = true))
        assertTrue(shouldRun(LinkMode.WhenOpen, paired = true, appVisible = true))
        assertFalse(shouldRun(LinkMode.WhenOpen, paired = true, appVisible = false))
    }

    @Test
    fun theModeSurvivesAnUnknownStoredValue() {
        assertEquals(LinkMode.WhenOpen, linkModeOf("WhenOpen"))
        assertEquals(LinkMode.Continuous, linkModeOf(null))
        assertEquals(LinkMode.Continuous, linkModeOf("autre"))
    }

    @Test
    fun aBatteryBroadcastBecomesAPercentageAndACharge() {
        assertEquals(82 to true, batteryOf(level = 82, scale = 100, plugged = 1))
        assertEquals(50 to false, batteryOf(level = 2000, scale = 4000, plugged = 0))
        assertEquals(100 to true, batteryOf(level = 255, scale = 255, plugged = 4))
        assertNull("mesure absente", batteryOf(level = -1, scale = 100, plugged = 0))
        assertNull(batteryOf(level = 50, scale = 0, plugged = 0))
    }
}
