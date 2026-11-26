package fr.kingsrequin.horizon.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.kingsrequin.horizon.R
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.components.Bouton

/**
 * Le bouton de mise à jour : « Mettre à jour vers x », puis l'avancée du téléchargement, l'échec
 * (réessayer), et au besoin ce qu'Android attend (autoriser l'installation d'applis).
 */
@Composable
fun UpdateButton(update: Update, state: UpdateState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val text = when (state) {
        is UpdateState.Downloading -> state.percent?.let { stringResource(R.string.update_downloading_percent, it) } ?: stringResource(R.string.update_downloading)
        UpdateState.Failed -> stringResource(R.string.update_failed)
        else -> stringResource(R.string.update_available, update.version)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HorizonSpace.s2)) {
        Bouton(text, onClick)
        if (state is UpdateState.NeedsPermission) {
            Text(stringResource(R.string.update_permission), style = HorizonType.bodySmall, color = LocalHorizonColors.current.onSurfaceVariant)
        }
    }
}
