package fr.kingsrequin.horizon.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les exemples sont ceux du dépôt d'Horizon (`tools/exemples.ps1`) : les deux côtés s'y mesurent. */
class CodecTest {
    private fun example(name: String): String =
        checkNotNull(javaClass.getResource("/phone/$name")) { "exemple absent : $name" }.readText()

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun everyExampleFromThePcDecodes() {
        assertEquals(FromPc.HelloOk("pc-1", "Bureau", 1, listOf("battery"), "#f97316"), Codec.decode(example("hello.ok.json")))
        assertTrue(Codec.decode(example("pair.accepted.json")) is FromPc.PairAccepted)
        assertEquals(FromPc.PairRejected("expired"), Codec.decode(example("pair.rejected.json")))
        assertEquals(FromPc.HelloRejected("unknown_device"), Codec.decode(example("hello.rejected.json")))
        assertEquals(FromPc.Accent("#a06cd5"), Codec.decode(example("accent.json")))
        assertEquals(FromPc.Unknown, Codec.decode(example("inconnu.json")))
        assertNull(Codec.decode("pas du json"))
        assertNull(Codec.decode("""{"x":1}"""))
    }

    @Test
    fun aKnownMessageWithAMissingFieldIsUnreadable() {
        assertNull(Codec.decode("""{"type":"hello.ok","pcId":"pc-1"}"""))
        assertNull(Codec.decode("""{"type":7}"""))
        assertNull(Codec.decode("""["hello.ok"]"""))
    }

    @Test
    fun whatThePhoneSendsMatchesTheExamples() {
        val sent = Codec.encode(ToPc.PhoneBattery(listOf(DeviceBattery("phone", "phone", "Pixel", 82, true, 1_764_000_000_000))))
        assertEquals(json(example("phone.battery.json"))["devices"]!!.jsonArray[0], json(sent)["devices"]!!.jsonArray[0])
        assertEquals("phone.battery", json(sent)["type"]!!.jsonPrimitive.content)
        assertEquals("pair.request", json(Codec.encode(ToPc.PairRequest("t", "d", "n", "0.1.0")))["type"]!!.jsonPrimitive.content)
        val hello = ToPc.Hello("7c1e2f0a-3b4d-4e5f-8a9b-0c1d2e3f4a5b", "3qX8mZ1kLr5vYt0aBn7cWd2eFg4hJi6oPs9uQx_-AbC", PROTOCOL, "0.1.0")
        assertEquals(json(example("hello.json")), json(Codec.encode(hello)))
    }
}
