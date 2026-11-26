package fr.kingsrequin.horizon.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.kingsrequin.horizon.design.HorizonIcons
import fr.kingsrequin.horizon.design.HorizonSize
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.VolumeTick
import fr.kingsrequin.horizon.design.volumeTick
import kotlin.math.roundToInt

/** Hauteur de la glissière (celle du curseur) et des deux moitiés. */
private val Height = 56.dp
private val HalfHeight = 48.dp
private val Gap = 6.dp

/** La moitié active garde toujours la place de l'icône (zone tactile du muet). */
private val MinOn = HorizonSize.touchMin + HorizonSpace.s2

/** La moitié éteinte garde toujours la place de « 100 % ». */
private val MinRest = 64.dp

/**
 * Le volume général du PC : glissière épaisse en deux moitiés, l'icône du haut-parleur dans la
 * partie active (la toucher coupe ou rétablit le son), la valeur dans la partie éteinte. Glisser
 * ou toucher règle le volume (`onVolume`, de 0 à 1), avec un cran tous les 5 % et un plus net
 * aux extrémités. Muet : partie active éteinte, icône barrée, le mot « Muet ». Pour TalkBack :
 * une glissière, « 72 pour cent », réglable par les gestes de volume.
 */
@Composable
fun GlissiereVolume(volume: Float, muted: Boolean, onVolume: (Float) -> Unit, onMute: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalHorizonColors.current
    val haptics = LocalHapticFeedback.current
    val value = volume.coerceIn(0f, 1f)
    val percent = (value * 100).roundToInt()
    val current by rememberUpdatedState(value)
    val send by rememberUpdatedState(onVolume)

    fun change(target: Float) {
        val next = (target.coerceIn(0f, 1f) * 100).roundToInt() / 100f
        if (next == current) return
        when (volumeTick(current, next)) {
            VolumeTick.Edge -> haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            VolumeTick.Step -> haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            VolumeTick.None -> Unit
        }
        send(next)
    }

    BoxWithConstraints(modifier.fillMaxWidth().height(Height)) {
        val density = LocalDensity.current
        val track = maxWidth - Gap * 2 - 4.dp - MinOn - MinRest
        val onWidth = MinOn + track * value

        /** Volume sous le doigt à `x` (en px depuis le bord gauche). */
        fun at(x: Float): Float = with(density) { ((x - MinOn.toPx() - Gap.toPx()) / track.toPx()).coerceIn(0f, 1f) }

        Row(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .semantics {
                    contentDescription = "Volume général"
                    stateDescription = if (muted) "Muet" else "$percent pour cent"
                    progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f, steps = 19)
                    setProgress { target ->
                        change(target)
                        true
                    }
                }
                .pointerInput(track) { detectTapGestures { change(at(it.x)) } }
                .pointerInput(track) {
                    detectHorizontalDragGestures { pointer, _ ->
                        pointer.consume()
                        change(at(pointer.position.x))
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(Gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(onWidth)
                    .height(HalfHeight)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(if (muted) colors.surfaceContainerHighest else colors.primary),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .padding(start = HorizonSpace.s1)
                        .size(HorizonSize.touchMin)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClickLabel = if (muted) "Rétablir le son" else "Couper le son") { onMute(!muted) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (muted) HorizonIcons.SpeakerSlash else HorizonIcons.SpeakerHigh),
                        contentDescription = if (muted) "Son coupé" else "Son",
                        tint = if (muted) colors.onSurfaceVariant else colors.onPrimary,
                        modifier = Modifier.size(HorizonSize.icon),
                    )
                }
            }
            Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(if (muted) colors.outline else colors.primary))
            Box(
                Modifier
                    .weight(1f)
                    .height(HalfHeight)
                    .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp))
                    .background(colors.surfaceContainerHigh)
                    .padding(end = 14.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(if (muted) "Muet" else "$percent %", style = HorizonType.numeralSmall, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Preview(name = "72 %, muet, 0 et 100 %", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun GlissiereApercu() = HorizonTheme {
    Column(Modifier.padding(HorizonSpace.s4), verticalArrangement = Arrangement.spacedBy(HorizonSpace.s4)) {
        GlissiereVolume(0.72f, muted = false, onVolume = {}, onMute = {})
        GlissiereVolume(0.72f, muted = true, onVolume = {}, onMute = {})
        GlissiereVolume(0f, muted = false, onVolume = {}, onMute = {})
        GlissiereVolume(1f, muted = false, onVolume = {}, onMute = {})
    }
}

@Preview(name = "Police à 200 %, violet", backgroundColor = 0xFF0F0D14, showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun GlissiereGrande() = HorizonTheme(seed = 0xFFA06CD5.toInt()) {
    GlissiereVolume(0.72f, muted = false, onVolume = {}, onMute = {}, modifier = Modifier.padding(HorizonSpace.s4))
}
