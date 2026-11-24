package fr.kingsrequin.horizon.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.kingsrequin.horizon.design.HorizonShapes
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors

/**
 * Un bouton sur toute la largeur. `primary` : l'action principale de l'écran, en `primary` (une
 * seule par écran) ; sinon un bouton secondaire en `surface-container-high`.
 */
@Composable
fun Bouton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true) {
    val colors = LocalHorizonColors.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(HorizonShapes.largeIncreased)
            .background(if (primary) colors.primary else colors.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = HorizonSpace.s5, vertical = HorizonSpace.s3),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HorizonType.labelLarge, color = if (primary) colors.onPrimary else colors.onSurface, textAlign = TextAlign.Center)
    }
}

@Preview(name = "Principal et secondaire", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun BoutonApercu() = HorizonTheme {
    Column(Modifier.padding(HorizonSpace.s4), verticalArrangement = Arrangement.spacedBy(HorizonSpace.s2)) {
        Bouton("Scanner le code", {})
        Bouton("Ouvrir les réglages", {}, primary = false)
    }
}

@Preview(name = "Police à 200 %", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun BoutonGrand() = HorizonTheme { Bouton("Ouvrir les réglages du téléphone", {}, Modifier.padding(HorizonSpace.s4)) }
