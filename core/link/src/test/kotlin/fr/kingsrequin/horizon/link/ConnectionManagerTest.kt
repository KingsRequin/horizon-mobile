package fr.kingsrequin.horizon.link

import fr.kingsrequin.horizon.protocol.DeviceBattery
import fr.kingsrequin.horizon.protocol.FromPc
import fr.kingsrequin.horizon.protocol.PairingLink
import fr.kingsrequin.horizon.protocol.ToPc
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PC = "192.168.1.49"

/** Un transport sans réseau : il décide quelles adresses répondent, et ce que le PC répond. */
private class FakeTransport(
    var reachable: Set<String>,
    val reply: FromPc? = null,
    val accent: String = "#f97316",
    val wrongPc: Set<String> = emptySet(),
    val pairReply: FromPc? = null,
) : Transport {
    val tried = mutableListOf<String>()
    val sent = mutableListOf<ToPc>()
    private var live: FakeSession? = null

    /** Nombre de passes : chacune commence par l'adresse du QR. */
    val rounds get() = tried.count { it == PC }
    val open get() = live?.takeIf { !it.closed } != null

    override fun open(host: String, port: Int, fingerprint: String, listener: SessionListener): Session {
        tried += host
        val session = FakeSession(listener)
        when {
            host in wrongPc -> session.end(WrongPc())
            host !in reachable -> session.end(IOException("injoignable"))
            else -> live = session
        }
        return session
    }

    fun push(msg: FromPc) = live!!.listener.onMessage(msg)

    /** La connexion tombe (Wi-Fi perdu, PC éteint). */
    fun drop() = live!!.end(IOException("coupée"))

    inner class FakeSession(val listener: SessionListener) : Session {
        var closed = false

        fun end(error: Throwable?) {
            if (!closed) {
                closed = true
                listener.onClosed(error)
            }
        }

        override fun send(msg: ToPc): Boolean {
            if (closed) return false
            sent += msg
            when (msg) {
                is ToPc.Hello -> listener.onMessage(reply ?: FromPc.HelloOk("pc-1", "Bureau", 1, listOf("battery"), accent))
                is ToPc.PairRequest -> listener.onMessage(pairReply ?: FromPc.PairAccepted("jeton-neuf", "pc-1", "Bureau"))
                is ToPc.PhoneBattery -> Unit
            }
            return true
        }

        override fun close() = end(null)
    }
}

private class MemoryStore(var pairing: Pairing?) : PairingStore {
    override suspend fun load(): Pairing? = pairing

    override suspend fun save(p: Pairing) {
        pairing = p
    }

    override suspend fun clear() {
        pairing = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionManagerTest {
    private fun paired(hosts: List<String> = listOf(PC), hostName: String = "Bureau", lastHost: String? = null) =
        MemoryStore(Pairing("pc-1", "Bureau", hosts, hostName, 28680, "empreinte", "tel-1", "jeton", lastHost))

    private val battery = ToPc.PhoneBattery(listOf(DeviceBattery("phone", "phone", "Pixel", 82, true, 0)))
    private val link = PairingLink(listOf(PC), "Bureau", 28680, "empreinte", "jeton-du-qr")

    /** Le gestionnaire tourne en tâche de fond du test : il s'arrête avec lui. */
    private fun TestScope.manager(store: PairingStore, transport: Transport) =
        ConnectionManager(store, transport, clock = { currentTime }, scope = backgroundScope, deviceName = { "Pixel" }, appVersion = "0.1.0").also { runCurrent() }

    @Test
    fun itStartsFromWhatIsStored() = runTest {
        assertEquals(LinkState.NotPaired, manager(MemoryStore(null), FakeTransport(emptySet())).state.value)
        val state = manager(paired(), FakeTransport(emptySet())).state.value
        assertEquals("appairé, réseau pas encore connu", LinkState.Offline("Bureau", OfflineReason.NoNetwork, 0), state)
    }

    @Test
    fun itTriesTheLastHostThenTheQrHostsThenTheLocalName() = runTest {
        val t = FakeTransport(reachable = setOf("BUREAU.local"))
        val store = paired(hosts = listOf(PC, "10.0.0.5"), hostName = "BUREAU", lastHost = "192.168.1.60")
        val m = manager(store, t)
        m.onNetwork(true); runCurrent()
        assertEquals(listOf("192.168.1.60", PC, "10.0.0.5", "BUREAU.local"), t.tried)
        assertTrue(m.state.value is LinkState.Connected)
        assertEquals("BUREAU.local", store.pairing!!.lastHost)
        assertEquals(ToPc.Hello("tel-1", "jeton", 1, "0.1.0"), t.sent.single())
    }

    @Test
    fun anUnreachablePcBacksOffThenWaitsForTheNetwork() = runTest {
        val t = FakeTransport(reachable = emptySet())
        val m = manager(paired(), t)
        m.onNetwork(true); runCurrent()
        assertEquals("en cours de série : « Reconnexion… »", LinkState.Connecting("Bureau"), m.state.value)
        advanceTimeBy(113_000)
        assertEquals("un essai, puis après 2, 5, 15, 30 et 60 s", 6, t.rounds)
        advanceTimeBy(3_600_000)
        assertEquals("plus rien sans événement", 6, t.rounds)
        assertEquals(LinkState.Offline("Bureau", OfflineReason.Unreachable, 0), m.state.value)
        // Le Wi-Fi revient : le PC est peut-être en train de démarrer, la série repart.
        m.onNetwork(true); advanceTimeBy(113_000)
        assertEquals(12, t.rounds)
        advanceTimeBy(3_600_000)
        assertEquals(12, t.rounds)
        m.onAppOpened(); advanceTimeBy(113_000)
        assertEquals("l'appli ouverte relance aussi une série", 18, t.rounds)
    }

    @Test
    fun aWaitingPhoneTriesOnceWhenItHasSomethingToSend() = runTest {
        val t = FakeTransport(reachable = emptySet())
        val m = manager(paired(), t)
        m.onNetwork(true); advanceTimeBy(200_000)
        assertEquals(6, t.rounds)
        // Le PC s'allume plus tard, sans qu'aucun réseau ne change : la batterie qui bouge
        // (le téléphone est déjà réveillé) est l'occasion d'un essai, un seul.
        assertFalse(m.send(battery)); advanceTimeBy(3_600_000)
        assertEquals(7, t.rounds)
        t.reachable = setOf(PC)
        assertFalse(m.send(battery)); runCurrent()
        assertTrue(m.state.value is LinkState.Connected)
        assertTrue(m.send(battery))
        assertEquals(battery, t.sent.last())
    }

    @Test
    fun aPhoneThePcForgotStopsTrying() = runTest {
        for (reason in listOf("unknown_device", "bad_token")) {
            val t = FakeTransport(reachable = setOf(PC), reply = FromPc.HelloRejected(reason))
            val m = manager(paired(), t)
            m.onNetwork(true); advanceTimeBy(3_600_000)
            assertEquals(LinkState.Rejected("Bureau"), m.state.value)
            assertEquals(1, t.rounds)
            m.send(battery); m.onNetwork(true); m.onAppOpened(); advanceTimeBy(3_600_000)
            assertEquals("seul un nouvel appairage relance", 1, t.rounds)
            assertFalse(t.open)
        }
    }

    @Test
    fun anotherProtocolVersionIsNotWorthRetrying() = runTest {
        val t = FakeTransport(reachable = setOf(PC), reply = FromPc.HelloRejected("protocol"))
        val m = manager(paired(), t)
        m.onNetwork(true); advanceTimeBy(3_600_000)
        assertEquals(LinkState.Offline("Bureau", OfflineReason.Incompatible, 0), m.state.value)
        assertEquals(1, t.rounds)
    }

    @Test
    fun noNetworkMeansNoAttempt() = runTest {
        val t = FakeTransport(reachable = setOf(PC))
        val m = manager(paired(), t)
        m.onNetwork(false); m.onAppOpened(); m.send(battery); advanceTimeBy(3_600_000)
        assertEquals(0, t.rounds)
        assertEquals(OfflineReason.NoNetwork, (m.state.value as LinkState.Offline).reason)
    }

    @Test
    fun losingTheNetworkClosesTheSessionAndItsReturnReopensIt() = runTest {
        val t = FakeTransport(reachable = setOf(PC))
        val m = manager(paired(), t)
        var connected = 0
        m.onConnected = { connected++ }
        m.onNetwork(true); advanceTimeBy(60_000)
        assertEquals(1, connected)
        m.onNetwork(false); runCurrent()
        assertFalse(t.open)
        assertEquals(LinkState.Offline("Bureau", OfflineReason.NoNetwork, 60_000), m.state.value)
        m.onNetwork(true); runCurrent()
        assertTrue(m.state.value is LinkState.Connected)
        assertEquals("chaque session neuve le dit : la batterie repart", 2, connected)
    }

    @Test
    fun aDroppedSessionIsRetriedAtOnce() = runTest {
        val t = FakeTransport(reachable = setOf(PC))
        val m = manager(paired(), t)
        m.onNetwork(true); advanceTimeBy(10_000)
        t.drop(); runCurrent()
        assertTrue(m.state.value is LinkState.Connected)
        assertEquals(2, t.rounds)
        t.reachable = emptySet()
        t.drop(); advanceTimeBy(200_000)
        assertEquals("hors ligne depuis la coupure", LinkState.Offline("Bureau", OfflineReason.Unreachable, 10_000), m.state.value)
    }

    @Test
    fun anotherMachineAtAnOldAddressDoesNotHideThePc() = runTest {
        val store = paired(lastHost = "192.168.1.60")
        val t = FakeTransport(reachable = setOf(PC), wrongPc = setOf("192.168.1.60"))
        val m = manager(store, t)
        m.onNetwork(true); runCurrent()
        assertTrue(m.state.value is LinkState.Connected)
        assertEquals(PC, store.pairing!!.lastHost)
        // Rien d'autre ne répond : le dire, plutôt que « injoignable ».
        val alone = FakeTransport(reachable = emptySet(), wrongPc = setOf(PC))
        val m2 = manager(paired(), alone)
        m2.onNetwork(true); advanceTimeBy(200_000)
        assertEquals(OfflineReason.WrongPc, (m2.state.value as LinkState.Offline).reason)
    }

    @Test
    fun theAccentOfThePcBecomesTheSeed() = runTest {
        val t = FakeTransport(reachable = setOf(PC), accent = "#a06cd5")
        val m = manager(paired(), t)
        m.onNetwork(true); runCurrent()
        assertEquals(LinkState.Connected("Bureau", 0xFFA06CD5.toInt(), setOf("battery")), m.state.value)
        t.push(FromPc.Accent("#22b8cf")); runCurrent()
        assertEquals(0xFF22B8CF.toInt(), (m.state.value as LinkState.Connected).seed)
        t.push(FromPc.Accent("n'importe quoi")); t.push(FromPc.Unknown); runCurrent()
        assertEquals("couleur illisible : on garde la précédente", 0xFF22B8CF.toInt(), (m.state.value as LinkState.Connected).seed)
    }

    @Test
    fun aPcThatNeverAnswersHelloIsGivenUpOn() = runTest {
        // Le PC accepte la connexion mais ne répond rien : on ne reste pas « Reconnexion… » sans fin.
        val t = FakeTransport(reachable = setOf(PC), reply = FromPc.Unknown)
        val m = manager(paired(), t)
        m.onNetwork(true); advanceTimeBy(9_000)
        assertEquals(LinkState.Connecting("Bureau"), m.state.value)
        advanceTimeBy(3_600_000)
        assertEquals(OfflineReason.Unreachable, (m.state.value as LinkState.Offline).reason)
        assertFalse(t.open)
    }

    @Test
    fun pairingSavesThePcThenConnects() = runTest {
        val store = MemoryStore(null)
        val t = FakeTransport(reachable = setOf(PC))
        val m = manager(store, t)
        m.onNetwork(true); runCurrent()
        assertEquals(PairResult.Paired("Bureau"), m.pair(link))
        val saved = store.pairing!!
        assertEquals(listOf("pc-1", "Bureau", "jeton-neuf", PC, "empreinte"), listOf(saved.pcId, saved.hostName, saved.deviceToken, saved.lastHost, saved.fingerprint))
        val request = t.sent.first() as ToPc.PairRequest
        assertEquals(listOf("jeton-du-qr", "Pixel", "0.1.0", saved.deviceId), listOf(request.token, request.deviceName, request.appVersion, request.deviceId))
        runCurrent()
        assertTrue("puis la session s'ouvre avec le jeton permanent", m.state.value is LinkState.Connected)
        assertEquals(ToPc.Hello(saved.deviceId, "jeton-neuf", 1, "0.1.0"), t.sent.last())
    }

    @Test
    fun aRefusedPairingSaysWhy() = runTest {
        val store = MemoryStore(null)
        val m = manager(store, FakeTransport(reachable = setOf(PC), pairReply = FromPc.PairRejected("expired")))
        m.onNetwork(true)
        assertEquals(PairResult.Refused("expired"), m.pair(link))
        assertNull(store.pairing)
        assertEquals(LinkState.NotPaired, m.state.value)
    }

    @Test
    fun aPairingThatReachesNobodyOrSomebodyElseSaysSo() = runTest {
        val nobody = manager(MemoryStore(null), FakeTransport(reachable = emptySet()))
        nobody.onNetwork(true)
        val silent = FakeTransport(reachable = setOf("Bureau.local"))
        assertEquals("un appairage n'essaie que les adresses du code, jamais un nom à résoudre", PairResult.Unreachable, manager(MemoryStore(null), silent).pair(link))
        assertEquals(listOf(PC), silent.tried)
        assertEquals(PairResult.Unreachable, nobody.pair(link))
        val somebodyElse = manager(MemoryStore(null), FakeTransport(reachable = emptySet(), wrongPc = setOf(PC)))
        somebodyElse.onNetwork(true)
        assertEquals(PairResult.WrongPc, somebodyElse.pair(link))
    }

    @Test
    fun pairingAgainReplacesThePcAndKeepsThePhoneSIdentifier() = runTest {
        val store = paired()
        val t = FakeTransport(reachable = setOf(PC), reply = FromPc.HelloRejected("unknown_device"))
        val m = manager(store, t)
        m.onNetwork(true); runCurrent()
        assertEquals(LinkState.Rejected("Bureau"), m.state.value)
        assertEquals(PairResult.Paired("Bureau"), m.pair(link))
        assertEquals("tel-1", store.pairing!!.deviceId)
        assertEquals("jeton-neuf", store.pairing!!.deviceToken)
    }

    @Test
    fun forgettingThePcClosesEverything() = runTest {
        val store = paired()
        val t = FakeTransport(reachable = setOf(PC))
        val m = manager(store, t)
        m.onNetwork(true); runCurrent()
        m.forget()
        assertNull(store.pairing)
        assertFalse(t.open)
        assertEquals(LinkState.NotPaired, m.state.value)
        m.onNetwork(true); m.onAppOpened(); advanceTimeBy(3_600_000)
        assertEquals(1, t.rounds)
    }

    @Test
    fun everyChangeOfStateIsLogged() = runTest {
        val log = LinkLog()
        val t = FakeTransport(reachable = setOf(PC))
        val m = ConnectionManager(paired(), t, clock = { currentTime }, scope = backgroundScope, deviceName = { "Pixel" }, appVersion = "0.1.0", log = log)
        runCurrent(); m.onNetwork(true); advanceTimeBy(65_000)
        t.drop(); runCurrent()
        val lines = log.lines()
        assertTrue(lines.toString(), lines.any { it.endsWith("connecté par $PC") })
        assertTrue(lines.toString(), lines.any { it.startsWith("00:01:05 ") && "coupée" in it })
        assertFalse("ni jeton ni empreinte dans le journal", lines.any { "jeton" in it || "empreinte" in it })
    }
}
