package fr.kingsrequin.horizon.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.kingsrequin.horizon.design.HorizonIcons
import fr.kingsrequin.horizon.design.HorizonSize
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.R

/** Longueur au-delà de laquelle le nom de la sortie est coupé. */
private const val MAX_NAME = 14

/**
 * La sortie audio du PC, en bouton scindé : à gauche la sortie en cours (toucher passe à la
 * suivante, comme la bascule de la barre), à droite la liste des autres. Une seule sortie : la
 * moitié gauche seule, sans action. Jamais en `primary` : elle ne doit pas concurrencer la
 * lecture.
 */
@Composable
fun BoutonSortie(name: String, headset: Boolean, several: Boolean, onNext: () -> Unit, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalHorizonColors.current
    val pill = HorizonSize.touchMin / 2
    val shown = if (name.length > MAX_NAME) name.take(MAX_NAME - 1) + "…" else name
    val current = stringResource(R.string.output_current, name)
    Row(modifier.heightIn(min = HorizonSize.touchMin), horizontalArrangement = Arrangement.spacedBy(HorizonSpace.half)) {
        val lead = if (several) RoundedCornerShape(topStart = pill, bottomStart = pill, topEnd = 4.dp, bottomEnd = 4.dp) else RoundedCornerShape(pill)
        Row(
            Modifier
                .heightIn(min = HorizonSize.touchMin)
                .clip(lead)
                .background(colors.surfaceContainerHigh)
                .then(if (several) Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.output_switch), onClick = onNext) else Modifier)
                .semantics { contentDescription = current }
                .padding(start = HorizonSpace.s4, end = HorizonSpace.s4),
            horizontalArrangement = Arrangement.spacedBy(HorizonSpace.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(if (headset) HorizonIcons.Headset else HorizonIcons.SpeakerHigh), contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(HorizonSize.icon))
            Text(shown, style = HorizonType.labelLarge, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (several) {
            Box(
                Modifier
                    .widthIn(min = HorizonSize.touchMin)
                    .heightIn(min = HorizonSize.touchMin)
                    .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = pill, bottomEnd = pill))
                    .background(colors.surfaceContainerHigh)
                    .clickable(role = Role.Button, onClick = onChoose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(HorizonIcons.CaretDown), contentDescription = stringResource(R.string.output_choose), tint = colors.onSurface, modifier = Modifier.size(HorizonSize.icon))
            }
        }
    }
}

@Preview(name = "Casque, enceintes, une seule sortie", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun BoutonSortieApercu() = HorizonTheme {
    Column(Modifier.padding(HorizonSpace.s4), verticalArrangement = Arrangement.spacedBy(HorizonSpace.s3)) {
        BoutonSortie("Casque", headset = true, several = true, onNext = {}, onChoose = {})
        BoutonSortie("Haut-parleurs (Realtek High Definition)", headset = false, several = true, onNext = {}, onChoose = {})
        BoutonSortie("Enceintes", headset = false, several = false, onNext = {}, onChoose = {})
    }
}

@Preview(name = "Police à 200 %", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun BoutonSortieGrand() = HorizonTheme { BoutonSortie("Casque", headset = true, several = true, onNext = {}, onChoose = {}, modifier = Modifier.padding(HorizonSpace.s4)) }
