package fr.kingsrequin.horizon.pairing

import fr.kingsrequin.horizon.R
import fr.kingsrequin.horizon.connection.LinkMode
import fr.kingsrequin.horizon.link.PairResult
import fr.kingsrequin.horizon.protocol.PairingLink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairingViewModelTest {
    private val goodLink = "horizon://pair?v=1&h=192.168.1.49&n=Bureau&p=28680&fp=${"A".repeat(43)}&t=${"b".repeat(22)}"
    private var chosen: LinkMode? = null
    private val asked = mutableListOf<PairingLink>()

    private fun TestScope.viewModel(pairResult: PairResult = PairResult.Paired("Bureau")) =
        PairingViewModel(pair = { asked += it; pairResult }, setMode = { chosen = it }, scope = this)

    @Test
    fun aWrongQrKeepsTheScannerOpenWithAHint() = runTest {
        val vm = viewModel()
        assertEquals(PairingStep.Intro, vm.step.value)
        vm.start(); vm.onCameraPermission(true)
        assertEquals(PairingStep.Scanning(null), vm.step.value)
        vm.onQr("https://exemple.fr")
        assertEquals(PairingStep.Scanning(R.string.pairing_not_horizon), vm.step.value)
        vm.onQr("horizon://pair?v=1&h=192.168.1.49")
        assertEquals(PairingStep.Scanning(R.string.pairing_malformed), vm.step.value)
        vm.onQr(goodLink)
        assertEquals(PairingStep.Connecting, vm.step.value)
        advanceUntilIdle()
        assertEquals(PairingStep.ChooseMode("Bureau"), vm.step.value)
        assertEquals("192.168.1.49", asked.single().hosts.single())
        assertNull(chosen)
        vm.choose(LinkMode.Continuous)
        assertEquals(PairingStep.Done, vm.step.value)
        assertEquals(LinkMode.Continuous, chosen)
    }

    @Test
    fun refusalsAndFailuresAreExplained() = runTest {
        val denied = viewModel(); denied.start(); denied.onCameraPermission(false)
        assertEquals(PairingStep.CameraDenied, denied.step.value)
        val outcomes = mapOf(
            PairResult.Refused("expired") to R.string.pairing_expired,
            PairResult.Refused("used") to R.string.pairing_used,
            PairResult.Refused("autre chose") to R.string.pairing_refused,
            PairResult.Unreachable to R.string.pairing_unreachable,
            PairResult.WrongPc to R.string.pairing_wrong_pc,
        )
        for ((result, message) in outcomes) {
            val vm = viewModel(pairResult = result); vm.start(); vm.onCameraPermission(true); vm.onQr(goodLink); advanceUntilIdle()
            assertEquals(result.toString(), PairingStep.Failed(message), vm.step.value)
            vm.retry()
            assertEquals("après un échec, le viseur revient", PairingStep.Scanning(null), vm.step.value)
        }
        val newer = viewModel(); newer.start(); newer.onCameraPermission(true); newer.onQr(goodLink.replace("v=1", "v=2"))
        assertEquals(PairingStep.Failed(R.string.pairing_version), newer.step.value)
    }

    @Test
    fun theSameCodeIsNotSentTwiceWhileThePcAnswers() = runTest {
        val answer = CompletableDeferred<PairResult>()
        val vm = PairingViewModel(pair = { asked += it; answer.await() }, setMode = {}, scope = this)
        vm.start(); vm.onCameraPermission(true)
        // Le viseur relit le même code à chaque image tant qu'il est devant l'objectif.
        vm.onQr(goodLink); runCurrent(); vm.onQr(goodLink); vm.onQr(goodLink); runCurrent()
        assertEquals(1, asked.size)
        answer.complete(PairResult.Paired("Bureau")); advanceUntilIdle()
        assertEquals(PairingStep.ChooseMode("Bureau"), vm.step.value)
        vm.onQr(goodLink); advanceUntilIdle()
        assertEquals("un code lu après coup ne relance rien", 1, asked.size)
    }

    @Test
    fun aLinkOpenedFromOutsideAsksBeforePairing() = runTest {
        // Une page web ou une autre appli peut ouvrir un tel lien : rien ne part sans un appui
        // de la personne, qui voit à quel PC et à quelle adresse le lien veut la relier.
        val vm = viewModel()
        vm.onLink(goodLink.replace("h=192.168.1.49", "h=192.168.1.49,10.0.0.5")); advanceUntilIdle()
        assertEquals("tout ce que le lien fera essayer est montré", PairingStep.Confirm("Bureau", listOf("192.168.1.49", "10.0.0.5"), 28680), vm.step.value)
        assertEquals("rien n'est envoyé avant l'accord", 0, asked.size)
        vm.confirm()
        assertEquals(PairingStep.Connecting, vm.step.value)
        advanceUntilIdle()
        assertEquals(PairingStep.ChooseMode("Bureau"), vm.step.value)
        assertEquals(listOf("192.168.1.49", "10.0.0.5"), asked.single().hosts)
        vm.onLink(goodLink.replace("Bureau", "Autre")); vm.confirm(); advanceUntilIdle()
        assertEquals("un lien reçu pendant le choix du mode est ignoré", PairingStep.ChooseMode("Bureau"), vm.step.value)
        assertEquals(1, asked.size)
    }

    @Test
    fun aLinkFromOutsideCanBeDeclinedOrIgnored() = runTest {
        val declined = viewModel()
        declined.onLink(goodLink)
        declined.retry()
        assertEquals(PairingStep.Intro, declined.step.value)
        declined.confirm(); advanceUntilIdle()
        assertEquals("un lien refusé ne peut plus être accepté", 0, asked.size)
        val other = viewModel()
        other.onLink("https://exemple.fr")
        assertEquals("un lien qui n'est pas d'Horizon ne change rien", PairingStep.Intro, other.step.value)
        other.confirm(); advanceUntilIdle()
        assertEquals(0, asked.size)
        val failed = viewModel(pairResult = PairResult.Unreachable)
        failed.onLink(goodLink); failed.confirm(); advanceUntilIdle()
        assertEquals(PairingStep.Failed(R.string.pairing_unreachable), failed.step.value)
        failed.retry()
        assertEquals("sans caméra accordée, on revient au début", PairingStep.Intro, failed.step.value)
    }
}
