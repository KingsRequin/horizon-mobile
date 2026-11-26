package fr.kingsrequin.horizon.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.kingsrequin.horizon.design.HorizonShapes
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.R
import fr.kingsrequin.horizon.design.VolumeTick
import fr.kingsrequin.horizon.design.appInitial
import fr.kingsrequin.horizon.design.volumeTick
import kotlin.math.roundToInt

/** Hauteur d'une ligne du mixeur (`row-compact`) et épaisseur de sa jauge. */
private val RowCompact = 48.dp
private val Bar = 12.dp

/**
 * Le volume d'une appli du PC, sur une ligne compacte : pastille (son initiale), nom, jauge et
 * valeur. La jauge (`on-surface` sur `surface-container-highest` : `primary` reste au volume
 * général) se règle en glissant ou en touchant n'importe où sur la ligne ; toucher la pastille
 * coupe ou rétablit le son de l'appli (jauge estompée, « Muet »). Se pose dans un [Groupe].
 */
@Composable
fun LigneMixeur(name: String, volume: Float, muted: Boolean, onVolume: (Float) -> Unit, onMute: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalHorizonColors.current
    val haptics = LocalHapticFeedback.current
    val value = volume.coerceIn(0f, 1f)
    val percent = (value * 100).roundToInt()
    val current by rememberUpdatedState(value)
    val send by rememberUpdatedState(onVolume)
    val spoken = if (muted) stringResource(R.string.volume_muted) else pluralStringResource(R.plurals.volume_spoken, percent, percent)

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

    Row(
        modifier
            .fillMaxWidth()
            .height(RowCompact)
            .clip(HorizonShapes.small)
            .background(colors.surfaceContainer)
            .semantics {
                contentDescription = name
                stateDescription = spoken
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f, steps = 19)
                setProgress { target ->
                    change(target)
                    true
                }
            }
            .pointerInput(Unit) { detectTapGestures { change(it.x / size.width) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { pointer, _ ->
                    pointer.consume()
                    change(pointer.position.x / size.width)
                }
            }
            .padding(end = HorizonSpace.s4),
        horizontalArrangement = Arrangement.spacedBy(HorizonSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(RowCompact)
                .clickable(role = Role.Button, onClickLabel = stringResource(if (muted) R.string.volume_unmute else R.string.volume_mute)) { onMute(!muted) },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(28.dp).clip(HorizonShapes.full).background(colors.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Text(appInitial(name), style = HorizonType.labelMedium, color = colors.onSurface)
            }
        }
        Text(name, style = HorizonType.bodyMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(Modifier.width(96.dp).height(Bar).clip(RoundedCornerShape(Bar / 2)).background(colors.surfaceContainerHighest).alpha(if (muted) DIMMED else 1f)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(value).background(colors.onSurface))
        }
        Text(
            if (muted) stringResource(R.string.volume_muted) else stringResource(R.string.volume_value, percent),
            style = HorizonType.numeralSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(44.dp),
        )
    }
}

@Preview(name = "Trois applis, une muette", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun LigneMixeurApercu() = HorizonTheme {
    Groupe(Modifier.padding(HorizonSpace.s4)) {
        LigneMixeur("Pear Desktop", 0.8f, muted = false, onVolume = {}, onMute = {})
        LigneMixeur("Discord", 0.55f, muted = true, onVolume = {}, onMute = {})
        LigneMixeur("Un nom d'appli beaucoup trop long", 1f, muted = false, onVolume = {}, onMute = {})
    }
}

@Preview(name = "Police à 200 %, violet", backgroundColor = 0xFF0F0D14, showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun LigneMixeurGrande() = HorizonTheme(seed = 0xFFA06CD5.toInt()) {
    Groupe(Modifier.padding(HorizonSpace.s4)) { LigneMixeur("Spotify", 0.72f, muted = false, onVolume = {}, onMute = {}) }
}
