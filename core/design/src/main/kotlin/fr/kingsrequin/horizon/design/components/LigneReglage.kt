package fr.kingsrequin.horizon.design.components

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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import fr.kingsrequin.horizon.design.HorizonIcons
import fr.kingsrequin.horizon.design.HorizonShapes
import fr.kingsrequin.horizon.design.HorizonSize
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors

/**
 * Une ligne de réglage : son nom, ce qu'elle implique en dessous, et une coche quand c'est le
 * choix en cours (`selected`). Sans `onClick`, elle ne fait qu'informer. Se pose dans un [Groupe].
 */
@Composable
fun LigneReglage(label: String, modifier: Modifier = Modifier, detail: String? = null, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val colors = LocalHorizonColors.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(HorizonShapes.small)
            .background(colors.surfaceContainer)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { this.selected = selected }
            .heightIn(min = HorizonSize.row)
            .padding(start = HorizonSpace.s4, end = 20.dpEnd, top = HorizonSpace.s3, bottom = HorizonSpace.s3),
        horizontalArrangement = Arrangement.spacedBy(HorizonSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HorizonSpace.half)) {
            Text(label, style = HorizonType.labelLarge, color = colors.onSurface)
            if (detail != null) Text(detail, style = HorizonType.bodyMedium, color = colors.onSurfaceVariant)
        }
        if (selected) Icon(painterResource(HorizonIcons.Check), contentDescription = null, Modifier.size(HorizonSize.icon), tint = colors.primary)
    }
}

private val Int.dpEnd get() = androidx.compose.ui.unit.Dp(this.toFloat())

@Preview(name = "Choix, information, action", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun LigneReglageApercu() = HorizonTheme {
    Groupe(Modifier.padding(HorizonSpace.s4)) {
        LigneReglage("En continu (recommandé)", detail = "La barre suit ta batterie même quand l’appli est fermée.", selected = true, onClick = {})
        LigneReglage("Seulement quand l’appli est ouverte", detail = "Rien ne tourne en arrière-plan.", onClick = {})
        LigneReglage("Version 0.1.0")
    }
}
