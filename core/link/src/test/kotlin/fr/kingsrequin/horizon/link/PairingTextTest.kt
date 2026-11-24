package fr.kingsrequin.horizon.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingTextTest {
    private val pairing = Pairing("pc-1", "PC Été", listOf("192.168.1.49", "10.0.0.5"), "PC Été", 28680, "AbC-_9", "tel-1", "jeton", lastHost = null)

    @Test
    fun aPairingComesBackAsItWasWritten() {
        assertEquals(pairing, pairingFromText(pairing.toText()))
        val seen = pairing.copy(lastHost = "10.0.0.5")
        assertEquals(seen, pairingFromText(seen.toText()))
    }

    @Test
    fun anUnreadableTextIsNoPairing() {
        assertNull(pairingFromText(""))
        assertNull(pairingFromText("pas du json"))
        assertNull(pairingFromText("""{"pcId":"pc-1"}"""))
    }

    @Test
    fun aFieldAddedByALaterVersionDoesNotLoseThePairing() {
        val later = pairing.toText().replaceFirst("{", """{"futur":true,""")
        assertEquals(pairing, pairingFromText(later))
    }
}
