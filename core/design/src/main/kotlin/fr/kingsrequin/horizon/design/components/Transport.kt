package fr.kingsrequin.horizon.design.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.kingsrequin.horizon.design.HorizonIcons
import fr.kingsrequin.horizon.design.HorizonShapes
import fr.kingsrequin.horizon.design.HorizonSize
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.R

/** Hauteur des commandes du lecteur (`control-large`). */
private val ControlLarge = 64.dp

/** Opacité d'une commande que la source ne propose pas. */
internal const val DIMMED = 0.4f

/**
 * Les trois commandes de la télécommande. Le centre est une pilule `primary` (le seul bouton
 * `primary` de l'écran) qui prend la largeur restante ; les côtés font 76 × 64. Une commande
 * impossible reste à sa place, estompée, sans réagir ; `enabled` faux : toutes (rien ne joue).
 * L'icône du centre change dès l'appui, sans attendre la réponse du PC.
 */
@Composable
fun Transport(
    playing: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHorizonColors.current
    // Ce qui est montré : l'état reçu, ou l'appui de l'utilisateur jusqu'à la réponse du PC.
    var shown by remember(playing) { mutableStateOf(playing) }
    val side = HorizonShapes.largeIncreased
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HorizonSpace.s2)) {
        Commande(HorizonIcons.SkipBackFill, stringResource(R.string.media_previous), enabled && canPrevious, colors.surfaceContainerHigh, colors.onSurface, side, Modifier.width(76.dp).height(ControlLarge), 26.dp, onPrevious)
        Commande(
            if (shown) HorizonIcons.PauseFill else HorizonIcons.PlayFill,
            stringResource(if (shown) R.string.media_pause else R.string.media_play),
            enabled,
            colors.primary,
            colors.onPrimary,
            RoundedCornerShape(ControlLarge / 2),
            Modifier.weight(1f).height(ControlLarge),
            26.dp,
        ) {
            shown = !shown
            onPlayPause()
        }
        Commande(HorizonIcons.SkipForwardFill, stringResource(R.string.media_next), enabled && canNext, colors.surfaceContainerHigh, colors.onSurface, side, Modifier.width(76.dp).height(ControlLarge), 26.dp, onNext)
    }
}

/**
 * Un bouton d'icône du lecteur : cran haptique et envoi dès l'appui. Impossible : estompé à 40 %
 * et sans réaction, mais toujours à sa place et lu par TalkBack.
 */
@Composable
internal fun Commande(
    @DrawableRes icon: Int,
    label: String,
    enabled: Boolean,
    background: Color,
    tint: Color,
    shape: Shape,
    modifier: Modifier,
    iconSize: Dp = HorizonSize.icon,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .alpha(if (enabled) 1f else DIMMED)
            .clip(shape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Preview(name = "En lecture, en pause, rien", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun TransportApercu() = HorizonTheme {
    Column(Modifier.padding(HorizonSpace.s4), verticalArrangement = Arrangement.spacedBy(HorizonSpace.s4)) {
        Transport(playing = true, canPrevious = true, canNext = true, enabled = true, onPrevious = {}, onPlayPause = {}, onNext = {})
        Transport(playing = false, canPrevious = false, canNext = true, enabled = true, onPrevious = {}, onPlayPause = {}, onNext = {})
        Transport(playing = false, canPrevious = true, canNext = true, enabled = false, onPrevious = {}, onPlayPause = {}, onNext = {})
    }
}

@Preview(name = "Violet", backgroundColor = 0xFF0F0D14, showBackground = true, widthDp = 360)
@Composable
private fun TransportViolet() = HorizonTheme(seed = 0xFFA06CD5.toInt()) {
    Transport(playing = true, canPrevious = true, canNext = true, enabled = true, onPrevious = {}, onPlayPause = {}, onNext = {}, modifier = Modifier.padding(HorizonSpace.s4))
}
