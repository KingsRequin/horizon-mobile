package fr.kingsrequin.horizon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.components.EtatLiaison
import fr.kingsrequin.horizon.design.components.LinkTone
import fr.kingsrequin.horizon.link.LinkState

/** L'accueil, pour l'instant réduit à l'état de la liaison (la suite : tâche 8 du plan). */
@Composable
fun Home(state: LinkState, onPairAgain: () -> Unit) {
    val (name, tone) = when (state) {
        is LinkState.Connected -> state.pcName to LinkTone.Connected
        is LinkState.Connecting -> state.pcName to LinkTone.Waiting
        is LinkState.Offline -> state.pcName to LinkTone.Offline
        is LinkState.Rejected -> state.pcName to LinkTone.Offline
        LinkState.NotPaired, LinkState.Starting -> return
    }
    Column(Modifier.fillMaxSize().background(LocalHorizonColors.current.surface).systemBarsPadding().padding(horizontal = HorizonSpace.s4)) {
        Spacer(Modifier.height(HorizonSpace.s8))
        EtatLiaison(name, state.javaClass.simpleName, tone, onStatusClick = onPairAgain)
    }
}
