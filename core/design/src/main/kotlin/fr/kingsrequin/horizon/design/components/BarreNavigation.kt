package fr.kingsrequin.horizon.design.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import fr.kingsrequin.horizon.design.HorizonIcons
import fr.kingsrequin.horizon.design.HorizonShapes
import fr.kingsrequin.horizon.design.HorizonSize
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors

/** Un onglet de la barre de navigation. */
data class Onglet(@DrawableRes val icon: Int, val label: String)

/**
 * Les écrans de l'appli, toujours visibles en bas. La barre couvre de sa couleur la zone des
 * gestes du système et ne se cache jamais. L'onglet courant se lit à sa pastille et à la graisse
 * de son libellé, pas à la couleur seule. Trois onglets au plus.
 */
@Composable
fun BarreNavigation(tabs: List<Onglet>, current: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalHorizonColors.current
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
            .navigationBarsPadding()
            .heightIn(min = HorizonSize.navHeight)
            .padding(horizontal = HorizonSpace.s4)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == current
            Column(
                Modifier
                    .weight(1f)
                    .clip(HorizonShapes.large)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(HorizonSpace.s1),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(width = 64.dp, height = 32.dp).background(if (selected) colors.primaryContainer else Color.Transparent, HorizonShapes.large),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(tab.icon), contentDescription = null, Modifier.size(HorizonSize.icon), tint = if (selected) colors.primary else colors.onSurfaceVariant)
                }
                // Un libellé d'onglet ne grossit que jusqu'à 130 % : au-delà, trois onglets ne
                // tiennent plus côte à côte et un mot serait coupé en deux.
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(MAX_LABEL_SCALE))) {
                    Text(
                        tab.label,
                        style = HorizonType.labelMedium,
                        fontWeight = if (selected) FontWeight(700) else FontWeight(600),
                        color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private const val MAX_LABEL_SCALE = 1.3f

private val apercu = listOf(Onglet(HorizonIcons.House, "Accueil"), Onglet(HorizonIcons.Play, "Télécommande"), Onglet(HorizonIcons.GearSix, "Réglages"))

@Preview(name = "Accueil choisi", widthDp = 390)
@Composable
private fun BarreNavigationAccueil() = HorizonTheme { BarreNavigation(apercu, current = 0, onSelect = {}) }

@Preview(name = "Réglages choisis, cyan", widthDp = 390)
@Composable
private fun BarreNavigationReglages() = HorizonTheme(seed = 0xFF22B8CF.toInt()) { BarreNavigation(apercu, current = 2, onSelect = {}) }

@Preview(name = "Deux onglets", widthDp = 390)
@Composable
private fun BarreNavigationDeux() = HorizonTheme { BarreNavigation(listOf(apercu[0], apercu[2]), current = 0, onSelect = {}) }

@Preview(name = "Police à 200 %", widthDp = 390, fontScale = 2f)
@Composable
private fun BarreNavigationGrand() = HorizonTheme { BarreNavigation(apercu, current = 1, onSelect = {}) }
