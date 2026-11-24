package fr.kingsrequin.horizon.design.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.kingsrequin.horizon.design.HorizonIcons
import fr.kingsrequin.horizon.design.HorizonShapes
import fr.kingsrequin.horizon.design.HorizonSize
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.StatusWait

/** Où en est un envoi vers le PC. */
enum class SendState { Sent, Waiting, Off }

/**
 * Ce que le téléphone envoie au PC, et quand il l'a envoyé pour la dernière fois. Jamais de
 * pourcentage : la barre d'Horizon est là pour ça. `detail` porte le mot (« Il y a 2 min »,
 * « En attente du PC », « Désactivé »). Se pose dans un [Groupe]. Appuyer ouvre son réglage.
 */
@Composable
fun LigneEnvoi(@DrawableRes icon: Int, label: String, detail: String, state: SendState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalHorizonColors.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(HorizonShapes.small)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = HorizonSize.row)
            .padding(start = HorizonSpace.s4, end = 20.dp, top = HorizonSpace.s3, bottom = HorizonSpace.s3),
        horizontalArrangement = Arrangement.spacedBy(HorizonSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(HorizonSize.icon), tint = if (state == SendState.Off) colors.outline else colors.onSurface)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HorizonSpace.half)) {
            Text(label, style = HorizonType.labelLarge, color = colors.onSurface)
            Text(detail, style = HorizonType.bodySmall, color = colors.onSurfaceVariant)
        }
        when (state) {
            SendState.Sent -> Icon(painterResource(HorizonIcons.Check), contentDescription = null, Modifier.size(HorizonSize.icon), tint = colors.primary)
            SendState.Waiting -> Icon(painterResource(HorizonIcons.Timer), contentDescription = null, Modifier.size(HorizonSize.icon), tint = StatusWait)
            SendState.Off -> Unit
        }
    }
}

@Preview(name = "Les trois états", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun LigneEnvoiEtats() = HorizonTheme {
    Groupe(Modifier.padding(HorizonSpace.s4)) {
        LigneEnvoi(HorizonIcons.DeviceMobile, "Batterie du téléphone", "Il y a 2 min", SendState.Sent, {})
        LigneEnvoi(HorizonIcons.Watch, "Batterie de la montre", "Montre hors de portée", SendState.Waiting, {})
        LigneEnvoi(HorizonIcons.Headset, "Batterie des écouteurs", "Désactivé", SendState.Off, {})
    }
}

@Preview(name = "Ligne seule", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun LigneEnvoiSeule() = HorizonTheme {
    Groupe(Modifier.padding(HorizonSpace.s4)) { LigneEnvoi(HorizonIcons.DeviceMobile, "Batterie du téléphone", "En attente du PC", SendState.Waiting, {}) }
}

@Preview(name = "Violet", backgroundColor = 0xFF0F0D13, showBackground = true, widthDp = 360)
@Composable
private fun LigneEnvoiViolet() = HorizonTheme(seed = 0xFFA06CD5.toInt()) {
    Groupe(Modifier.padding(HorizonSpace.s4)) { LigneEnvoi(HorizonIcons.DeviceMobile, "Batterie du téléphone", "Il y a 2 min", SendState.Sent, {}) }
}

@Preview(name = "Police à 200 %", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun LigneEnvoiGrand() = HorizonTheme {
    Groupe(Modifier.padding(HorizonSpace.s4)) {
        LigneEnvoi(HorizonIcons.DeviceMobile, "Batterie du téléphone", "Il y a 2 min", SendState.Sent, {})
        LigneEnvoi(HorizonIcons.Watch, "Batterie de la montre", "Montre hors de portée", SendState.Waiting, {})
    }
}
