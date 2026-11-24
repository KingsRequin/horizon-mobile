package fr.kingsrequin.horizon.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingLinkTest {
    @Test
    fun aPairingLinkIsReadOrRefusedWithAReason() {
        val ok = parsePairingLink("horizon://pair?v=1&h=192.168.1.49,10.0.0.5&n=PC%20%C3%89t%C3%A9&p=28680&fp=AbC-_9&t=tok")
        assertEquals(QrResult.Ok(PairingLink(listOf("192.168.1.49", "10.0.0.5"), "PC Été", 28680, "AbC-_9", "tok")), ok)
        assertEquals(QrResult.NotHorizon, parsePairingLink("https://exemple.fr"))
        assertEquals(QrResult.NotHorizon, parsePairingLink("WIFI:S:box;T:WPA;P:x;;"))
        assertEquals(QrResult.NotHorizon, parsePairingLink(""))
        assertEquals(QrResult.Unsupported("2"), parsePairingLink("horizon://pair?v=2&h=1.2.3.4&n=a&p=1&fp=f&t=t"))
        for (bad in listOf(
            "horizon://pair?v=1",
            "horizon://pair?v=1&h=&n=a&p=28680&fp=f&t=t",
            "horizon://pair?v=1&h=1.2.3.4&n=a&p=99999&fp=f&t=t",
            "horizon://pair?v=1&h=1.2.3.4&n=a&p=0&fp=f&t=t",
            "horizon://pair?v=1&h=exemple.fr&n=a&p=28680&fp=f&t=t",
            "horizon://pair?v=1&h=1.2.3.999&n=a&p=28680&fp=f&t=t",
            "horizon://pair?v=1&h=1.2.3.4&n=a&p=28680&fp=&t=t",
            "horizon://pair?v=1&h=1.2.3.4&n=a&p=28680&fp=f",
            "horizon://pair?h=1.2.3.4&n=a&p=28680&fp=f&t=t",
            "horizon://autre?v=1",
            "horizon://pair?v=1&h=1.2.3.4&n=%ZZ&p=28680&fp=f&t=t",
        )) {
            assertEquals(bad, QrResult.Malformed, parsePairingLink(bad))
        }
    }

    @Test
    fun aPlusInTheNameStaysAPlus() {
        // Horizon écrit l'espace « %20 » et le plus « %2B » ; un « + » nu reste un plus.
        val link = parsePairingLink("horizon://pair?v=1&h=10.0.0.5&n=PC%2B1+2&p=28681&fp=f&t=t")
        assertEquals(QrResult.Ok(PairingLink(listOf("10.0.0.5"), "PC+1+2", 28681, "f", "t")), link)
    }

    @Test
    fun anAccentColourIsReadOrIgnored() {
        assertEquals(0xFFF97316.toInt(), parseColor("#f97316"))
        assertEquals(0xFFA06CD5.toInt(), parseColor("#A06CD5"))
        for (bad in listOf("", "f97316", "#f9731", "#f97316ff", "#gggggg", "rouge")) {
            assertNull(bad, parseColor(bad))
        }
    }
}
