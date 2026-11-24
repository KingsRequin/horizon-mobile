package fr.kingsrequin.horizon.link

import fr.kingsrequin.horizon.protocol.FromPc
import fr.kingsrequin.horizon.protocol.ToPc
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpTransportTest {
    private val recorder = Recorder()

    @Test
    fun theSessionOpensOnlyForTheExpectedKey() {
        FakePc(sessions = 2).use { pc ->
            val good = OkHttpTransport().open(pc.host, pc.port, spkiFingerprint(pc.certificate), recorder)
            assertTrue(good.send(ToPc.Hello("tel-1", "jeton", 1, "0.1.0")))
            assertEquals("hello", pc.received()["type"]!!.jsonPrimitive.content)
            val bad = Recorder()
            OkHttpTransport().open(pc.host, pc.port, "A".repeat(43), bad)
            val end = bad.awaitClosed()
            assertTrue("fin inattendue : " + generateSequence(end) { it.cause }.joinToString(" <- "), end is WrongPc)
            assertFalse("une seule fin par session", bad.closedAgain())
        }
    }

    @Test
    fun whatThePcSendsReachesTheListener() {
        FakePc().use { pc ->
            OkHttpTransport().open(pc.host, pc.port, spkiFingerprint(pc.certificate), recorder)
            pc.send("""{"type":"hello.ok","pcId":"pc-1","pcName":"Bureau","protocol":1,"features":["battery"],"accent":"#a06cd5"}""")
            pc.send("pas du json")
            pc.send("""{"type":"accent","color":"#22b8cf"}""")
            assertEquals(listOf(FromPc.HelloOk("pc-1", "Bureau", 1, listOf("battery"), "#a06cd5"), FromPc.Accent("#22b8cf")), recorder.awaitMessages(2))
        }
    }

    @Test
    fun aDeadAddressFailsWithinTheTimeout() {
        val t0 = System.nanoTime()
        val r = Recorder()
        OkHttpTransport().open("192.0.2.1", 28680, "A".repeat(43), r)
        assertNotNull(r.awaitClosed())
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 8_000)
    }

    @Test
    fun eachSideCanEndTheSession() {
        FakePc(sessions = 2).use { pc ->
            val session = OkHttpTransport().open(pc.host, pc.port, spkiFingerprint(pc.certificate), recorder)
            pc.send("""{"type":"accent","color":"#22b8cf"}""")
            recorder.awaitMessages(1)
            session.close()
            assertEquals(1000, pc.closedByPhone())
            assertNull("fermée par le téléphone : pas une erreur", recorder.awaitClosed())
            assertFalse(session.send(ToPc.Hello("tel-1", "jeton", 1, "0.1.0")))
        }
        FakePc().use { pc ->
            val r = Recorder()
            OkHttpTransport().open(pc.host, pc.port, spkiFingerprint(pc.certificate), r)
            pc.send("""{"type":"accent","color":"#22b8cf"}""")
            r.awaitMessages(1)
            pc.hangUp()
            assertNull("fermée par le PC : pas une erreur", r.awaitClosed())
            assertFalse(r.closedAgain())
        }
    }
}
