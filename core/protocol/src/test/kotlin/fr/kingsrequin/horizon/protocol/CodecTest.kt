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
        assertEquals(FromPc.HelloOk("pc-1", "Bureau", 1, listOf("battery", "media"), "#f97316", listOf("battery", "media", "sound")), Codec.decode(example("hello.ok.json")))
        assertTrue(Codec.decode(example("pair.accepted.json")) is FromPc.PairAccepted)
        assertEquals(FromPc.PairRejected("expired"), Codec.decode(example("pair.rejected.json")))
        assertEquals(FromPc.HelloRejected("unknown_device"), Codec.decode(example("hello.rejected.json")))
        assertEquals(FromPc.Accent("#a06cd5"), Codec.decode(example("accent.json")))
        assertEquals(FromPc.Unknown, Codec.decode(example("inconnu.json")))
        assertNull(Codec.decode("pas du json"))
        assertNull(Codec.decode("""{"x":1}"""))
    }

    @Test
    fun everyNewExampleDecodes() {
        val spotify = MediaInfo("Spotify.exe", "Spotify", "FROGZ", "SPLATA", true, 68_000, 233_000, "9f2c1e0a7b3d4c55")
        val sessions = listOf(SessionInfo("Spotify.exe", "Spotify"), SessionInfo("Chrome", "Google Chrome"))
        assertEquals(FromPc.MediaState(spotify, sessions), Codec.decode(example("media.state.json")))
        assertEquals(FromPc.MediaState(null, emptyList()), Codec.decode(example("media.state-vide.json")))
        assertEquals(FromPc.MediaArt("9f2c1e0a7b3d4c55", "/9j/4AAQSkZJRgABAQAAAQABAAD/2w=="), Codec.decode(example("media.art.json")))
        assertEquals(FromPc.VolumeState(0.5f, false), Codec.decode(example("volume.state.json")))
        assertEquals(FromPc.Rights(listOf("battery", "sound")), Codec.decode(example("rights.json")))
    }

    @Test
    fun theMixerAndActionStatesDecode() {
        val outputs = listOf(OutputInfo("out-1", "Casque", "headset"), OutputInfo("out-2", "Enceintes", "speaker"))
        val apps = listOf(AppInfo("spotify.exe", "Spotify", 0.8f, false, true), AppInfo("discord.exe", "Discord", 0.55f, true, false))
        assertEquals(FromPc.MixerState(outputs, "out-1", apps), Codec.decode(example("mixer.state.json")))
        val actions = FromPc.ActionsState(true, TimerInfo(760_000, 1_500_000, false), listOf(5, 15, 25, 45), HeadsetInfo("Casque", 92, false))
        assertEquals(actions, Codec.decode(example("actions.state.json")))
        assertEquals(FromPc.ActionsState(false, null, emptyList(), null), Codec.decode(example("actions.state-vide.json")))
    }

    @Test
    fun theMixerAndActionCommandsMatchTheExamples() {
        assertEquals(json(example("mixer.app_volume.json")), json(Codec.encode(ToPc.MixerAppVolume("spotify.exe", 0.4f))))
        assertEquals(json(example("mixer.app_mute.json")), json(Codec.encode(ToPc.MixerAppMute("discord.exe", true))))
        assertEquals(json(example("mixer.output.json")), json(Codec.encode(ToPc.MixerOutput("{0.0.0.00000000}.{5a1c}"))))
        assertEquals(json(example("action.lock.json")), json(Codec.encode(ToPc.ActionLock)))
        assertEquals(json(example("action.dnd.json")), json(Codec.encode(ToPc.ActionDnd(true))))
        assertEquals(json(example("timer.start.json")), json(Codec.encode(ToPc.TimerStart(25))))
        assertEquals(json(example("timer.pause.json")), json(Codec.encode(ToPc.TimerPause(true))))
        assertEquals(json(example("timer.stop.json")), json(Codec.encode(ToPc.TimerStop)))
    }

    @Test
    fun anOldPcHelloHasNoGrantedList() {
        assertEquals(FromPc.HelloOk("pc-1", "Bureau", 1, listOf("battery"), "#f97316", granted = null), Codec.decode(example("hello.ok-sans-droits.json")))
    }

    @Test
    fun theRemoteCommandsMatchTheExamples() {
        assertEquals(json(example("watch.json")), json(Codec.encode(ToPc.Watch(listOf("media", "volume")))))
        assertEquals(json(example("media.command.json")), json(Codec.encode(ToPc.MediaCommand("play_pause"))))
        assertEquals(json(example("media.seek.json")), json(Codec.encode(ToPc.MediaSeek(68_000))))
        assertEquals(json(example("media.select.json")), json(Codec.encode(ToPc.MediaSelect("Spotify.exe"))))
        assertEquals(json(example("volume.set.json")), json(Codec.encode(ToPc.VolumeSet(0.72f))))
        assertEquals(json(example("volume.mute.json")), json(Codec.encode(ToPc.VolumeMute(true))))
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
