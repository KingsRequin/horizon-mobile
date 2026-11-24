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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.kingsrequin.horizon.design.HorizonShapes
import fr.kingsrequin.horizon.design.HorizonSize
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.StatusOk
import fr.kingsrequin.horizon.design.StatusWait

/** Où en est la liaison avec le PC. */
enum class LinkTone { Connected, Waiting, Offline }

/**
 * Le nom du PC en très grand, et l'état de la liaison dans une pastille juste dessous : la
 * première chose lue sur l'accueil. Un seul par écran. `status` porte le mot (« Connecté ·
 * réseau local », « Reconnexion… », « Hors ligne · vu il y a 12 min ») : l'état ne se lit jamais
 * à la couleur du point seule. Appuyer sur la pastille ouvre le détail de la liaison.
 */
@Composable
fun EtatLiaison(name: String, status: String, tone: LinkTone, onStatusClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalHorizonColors.current
    // Un nom plus large que l'écran passe au style du dessous plutôt que d'être coupé.
    var shrunk by remember(name) { mutableStateOf(false) }
    Column(modifier.padding(horizontal = HorizonSpace.s2), verticalArrangement = Arrangement.spacedBy(HorizonSpace.s3 - CHIP_TOUCH_MARGIN)) {
        Text(
            text = name,
            style = if (shrunk) HorizonType.headlineMedium else HorizonType.displayLarge,
            color = if (tone == LinkTone.Offline) colors.onSurfaceVariant else colors.onSurface,
            maxLines = if (shrunk) 3 else 1,
            softWrap = shrunk,
            onTextLayout = { if (!shrunk && it.hasVisualOverflow) shrunk = true },
        )
        // La pastille fait 32 dp de haut ; sa zone tactile, 48.
        Box(
            Modifier.heightIn(min = HorizonSize.touchMin).clip(HorizonShapes.large).clickable(role = Role.Button, onClick = onStatusClick),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.heightIn(min = 32.dp).background(colors.surfaceContainerHigh, HorizonShapes.large).padding(start = HorizonSpace.s3, end = 14.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(HorizonSpace.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val dot = when (tone) {
                    LinkTone.Connected -> StatusOk
                    LinkTone.Waiting -> StatusWait
                    LinkTone.Offline -> colors.outline
                }
                Box(Modifier.size(8.dp).background(dot, HorizonShapes.full))
                Text(status, style = HorizonType.bodySmall, fontWeight = FontWeight(600), color = colors.onSurface)
            }
        }
    }
}

/** Ce que la zone tactile de la pastille ajoute au-dessus d'elle (48 dp pour 32 dessinés). */
private val CHIP_TOUCH_MARGIN = 8.dp

@Preview(name = "Connecté", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun EtatLiaisonConnecte() = HorizonTheme { EtatLiaison("Bureau", "Connecté · réseau local", LinkTone.Connected, {}) }

@Preview(name = "Reconnexion", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun EtatLiaisonReconnexion() = HorizonTheme { EtatLiaison("Bureau", "Reconnexion…", LinkTone.Waiting, {}) }

@Preview(name = "Hors ligne", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun EtatLiaisonHorsLigne() = HorizonTheme { EtatLiaison("Bureau", "Hors ligne · vu il y a 12 min", LinkTone.Offline, {}) }

@Preview(name = "Nom long", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun EtatLiaisonNomLong() = HorizonTheme { EtatLiaison("DESKTOP-KQ4R7P2", "Connecté · réseau local", LinkTone.Connected, {}) }

@Preview(name = "Police à 200 %", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun EtatLiaisonGrand() = HorizonTheme { EtatLiaison("Bureau", "Hors ligne · vu il y a 12 min", LinkTone.Offline, {}) }
