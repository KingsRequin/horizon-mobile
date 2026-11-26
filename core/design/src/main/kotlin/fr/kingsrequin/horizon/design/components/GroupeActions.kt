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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.kingsrequin.horizon.design.HorizonIcons
import fr.kingsrequin.horizon.design.HorizonShapes
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors

/** Une action rapide : `on` nul pour une action simple, vrai ou faux pour une bascule. */
class ActionRapide(@param:DrawableRes val icon: Int, val label: String, val on: Boolean?, val onClick: () -> Unit)

/** Hauteur d'une action et taille de son icône (`icon-small`). */
private val ActionHeight = 68.dp
private val IconSmall = 20.dp

/**
 * Trois actions rapides sur le PC au plus, soudées en une rangée : icône au-dessus de deux
 * mots. Une bascule allumée passe en `primary-container`. Chaque appui fait un cran haptique ;
 * une action sans retour (verrouiller) part tout de suite, l'appelant dit ensuite ce qui s'est
 * passé.
 */
@Composable
fun GroupeActions(actions: List<ActionRapide>, modifier: Modifier = Modifier) {
    val colors = LocalHorizonColors.current
    val haptics = LocalHapticFeedback.current
    Row(modifier.fillMaxWidth().clip(HorizonShapes.extraLarge), horizontalArrangement = Arrangement.spacedBy(HorizonSpace.s1)) {
        actions.take(3).forEach { action ->
            val lit = action.on == true
            val press = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                action.onClick()
            }
            val tap = if (action.on != null) {
                Modifier.toggleable(value = lit, role = Role.Switch) { press() }
            } else {
                Modifier.clickable(role = Role.Button, onClick = press)
            }
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = ActionHeight)
                    .clip(HorizonShapes.small)
                    .background(if (lit) colors.primaryContainer else colors.surfaceContainer)
                    .then(tap)
                    .padding(horizontal = HorizonSpace.s1, vertical = HorizonSpace.s3),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HorizonSpace.s1, Alignment.CenterVertically),
            ) {
                val ink = if (lit) colors.onPrimaryContainer else colors.onSurface
                Icon(painterResource(action.icon), contentDescription = null, tint = ink, modifier = Modifier.size(IconSmall))
                Text(action.label, style = HorizonType.labelMedium.copy(fontFeatureSettings = "tnum"), color = ink, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

private fun apercu(dnd: Boolean, timer: String?) = listOf(
    ActionRapide(HorizonIcons.Lock, "Verrouiller", null) {},
    ActionRapide(HorizonIcons.Moon, "Ne pas déranger", dnd) {},
    ActionRapide(HorizonIcons.Timer, timer ?: "Minuteur", timer != null) {},
)

@Preview(name = "Au repos, puis NPD et minuteur", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun GroupeActionsApercu() = HorizonTheme {
    Column(Modifier.padding(HorizonSpace.s4), verticalArrangement = Arrangement.spacedBy(HorizonSpace.s4)) {
        GroupeActions(apercu(dnd = false, timer = null))
        GroupeActions(apercu(dnd = true, timer = "12:40"))
    }
}

@Preview(name = "Police à 200 %, violet", backgroundColor = 0xFF0F0D14, showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun GroupeActionsGrand() = HorizonTheme(seed = 0xFFA06CD5.toInt()) { GroupeActions(apercu(dnd = true, timer = null), Modifier.padding(HorizonSpace.s4)) }
