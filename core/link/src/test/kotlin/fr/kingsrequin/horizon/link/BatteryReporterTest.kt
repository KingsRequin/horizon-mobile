package fr.kingsrequin.horizon.link

import fr.kingsrequin.horizon.protocol.DeviceBattery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryReporterTest {
    @Test
    fun aReadingIsSentOnlyWhenItChanges() {
        val r = BatteryReporter { "Pixel" }
        assertNull(r.lastSentMs)
        assertEquals(DeviceBattery("phone", "phone", "Pixel", 82, true, 1_000), r.reading(82, true, 1_000)!!.devices.single())
        assertNull(r.reading(82, true, 2_000))
        assertNotNull(r.reading(82, false, 3_000))
        assertNotNull(r.reading(81, false, 4_000))
        r.reset()
        assertNotNull("nouvelle session : le PC ne sait rien", r.reading(81, false, 5_000))
        assertEquals(5_000L, r.lastSentMs)
        assertEquals(100, r.reading(250, false, 6_000)!!.devices.single().percent)
        assertEquals(0, r.reading(-3, false, 7_000)!!.devices.single().percent)
    }
}

class LinkLogTest {
    @Test
    fun itKeepsTheLastLinesWithTheirTime() {
        val log = LinkLog(capacity = 3)
        log.add(0, "démarrage")
        log.add(3_723_000, "connecté")
        log.add(86_400_000 + 59_000, "coupée")
        log.add(86_400_000 + 61_500, "reconnexion")
        assertEquals(listOf("01:02:03 connecté", "00:00:59 coupée", "00:01:01 reconnexion"), log.lines())
    }
}
