package fr.kingsrequin.horizon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.kingsrequin.horizon.connection.Link
import fr.kingsrequin.horizon.design.DEFAULT_SEED
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.link.LinkState
import fr.kingsrequin.horizon.pairing.PairingFlow
import fr.kingsrequin.horizon.pairing.PairingStep
import fr.kingsrequin.horizon.pairing.PairingViewModel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Ce que l'appli montre : rien tant que l'appairage n'est pas relu, le parcours d'appairage sans
 * PC (ou pendant qu'il est en cours, ou redemandé), sinon l'accueil. La couleur est celle que le
 * PC a envoyée en dernier.
 */
@Composable
fun App(pairingLink: MutableStateFlow<String?>) {
    val state by Link.manager.state.collectAsStateWithLifecycle()
    val pairing: PairingViewModel = viewModel(factory = viewModelFactory { initializer { PairingViewModel(Link.manager::pair, { Link.mode = it }) } })
    val step by pairing.step.collectAsStateWithLifecycle()
    val link by pairingLink.collectAsStateWithLifecycle()
    // Hors ligne, l'appli garde la dernière couleur reçue.
    var seed by rememberSaveable { mutableIntStateOf(DEFAULT_SEED) }
    (state as? LinkState.Connected)?.let { seed = it.seed }
    var pairAgain by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(link) {
        link?.let {
            pairing.onLink(it)
            pairingLink.value = null
        }
    }

    HorizonTheme(seed) {
        val inPairing = state is LinkState.NotPaired || pairAgain || (step != PairingStep.Intro && step != PairingStep.Done)
        when {
            state is LinkState.Starting -> Box(Modifier.fillMaxSize().background(LocalHorizonColors.current.surface))
            inPairing -> PairingFlow(pairing) {
                pairAgain = false
                pairing.start()
            }
            else -> Home(state, onPairAgain = { pairAgain = true })
        }
    }
}
