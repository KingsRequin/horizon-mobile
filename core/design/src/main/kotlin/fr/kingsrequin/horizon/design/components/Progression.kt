package fr.kingsrequin.horizon.design.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.kingsrequin.horizon.design.HorizonSize
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.R
import fr.kingsrequin.horizon.design.formatTime
import kotlin.math.PI
import kotlin.math.sin

/** Épaisseur de la ligne, longueur d'onde et hauteur de l'ondulation. */
private val Line = 4.dp
private val Wavelength = 16.dp
private val Amplitude = 3.dp

/** Écart entre la ligne et le curseur, de chaque côté. */
private val Gap = 4.dp

/**
 * L'avancée du morceau : la partie lue (`primary`) ondule pendant la lecture, redevient droite en
 * pause ; le curseur fait 4 × 16 dp, sa zone tactile toute la hauteur `touch-min`. Glisser ou
 * toucher déplace la lecture au relâchement seulement (`onSeek`, en ms). Sans durée (radio,
 * direct), le mot « En direct » remplace la barre. L'ondulation est la seule animation continue
 * de l'appli : arrêtée en pause, pendant un glissé et quand les animations du téléphone sont
 * coupées.
 */
@Composable
fun Progression(positionMs: Long, durationMs: Long, playing: Boolean, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalHorizonColors.current
    if (durationMs <= 0) {
        Text(stringResource(R.string.media_live), style = HorizonType.labelLarge, color = colors.onSurfaceVariant, modifier = modifier.padding(vertical = HorizonSpace.s3))
        return
    }
    // Position que l'utilisateur tient du doigt, en fraction ; nulle hors d'un glissé.
    var held by remember { mutableStateOf<Float?>(null) }
    val seek by rememberUpdatedState(onSeek)
    val duration by rememberUpdatedState(durationMs)
    val fraction = held ?: (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val shownMs = (fraction * durationMs).toLong()
    val waving = playing && held == null && animationsOn()
    val label = stringResource(R.string.media_progress)
    val spoken = stringResource(R.string.media_progress_state, formatTime(shownMs), formatTime(durationMs))
    val phase = if (waving) {
        rememberInfiniteTransition(label = "ondulation")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(durationMillis = 2_000, easing = LinearEasing), RepeatMode.Restart), label = "phase").value
    } else {
        0f
    }
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(HorizonSize.touchMin)
                .semantics {
                    contentDescription = label
                    stateDescription = spoken
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { target ->
                        seek((target.coerceIn(0f, 1f) * duration).toLong())
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset -> seek((offset.x / size.width).coerceIn(0f, 1f).let { (it * duration).toLong() }) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> held = (offset.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            held?.let { seek((it * duration).toLong()) }
                            held = null
                        },
                        onDragCancel = { held = null },
                    ) { change, _ ->
                        change.consume()
                        held = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                },
        ) {
            val (line, gap, mid) = Triple(Line.toPx(), Gap.toPx(), size.height / 2)
            val thumbX = (fraction * size.width).coerceIn(line / 2, size.width - line / 2)
            val playedEnd = thumbX - line / 2 - gap - line / 2
            if (playedEnd > line / 2) {
                if (waving) {
                    val (wave, amp) = Wavelength.toPx() to Amplitude.toPx()
                    val path = Path().apply {
                        fun y(x: Float) = mid + amp * sin(2 * PI * (x / wave - phase)).toFloat()
                        var x = line / 2
                        moveTo(x, y(x))
                        while (x < playedEnd) {
                            x = (x + 2f).coerceAtMost(playedEnd)
                            lineTo(x, y(x))
                        }
                    }
                    drawPath(path, colors.primary, style = Stroke(width = line, cap = StrokeCap.Round))
                } else {
                    drawLine(colors.primary, Offset(line / 2, mid), Offset(playedEnd, mid), strokeWidth = line, cap = StrokeCap.Round)
                }
            }
            val restStart = thumbX + line / 2 + gap + line / 2
            if (restStart < size.width - line / 2) {
                drawLine(colors.surfaceContainerHighest, Offset(restStart, mid), Offset(size.width - line / 2, mid), strokeWidth = line, cap = StrokeCap.Round)
            }
            val thumbH = 16.dp.toPx()
            drawRoundRect(colors.primary, Offset(thumbX - line / 2, mid - thumbH / 2), Size(line, thumbH), CornerRadius(line / 2))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val times = HorizonType.bodySmall.copy(fontFeatureSettings = "tnum")
            Text(formatTime(shownMs), style = times, color = colors.onSurfaceVariant)
            Text(formatTime(durationMs), style = times, color = colors.onSurfaceVariant)
        }
    }
}

/** Faux quand les animations sont coupées dans les réglages du téléphone (accessibilité, économie). */
@Composable
private fun animationsOn(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
}

@Preview(name = "En lecture, en pause, en direct", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun ProgressionApercu() = HorizonTheme {
    Column(Modifier.padding(HorizonSpace.s4)) {
        Progression(68_000, 233_000, playing = true, onSeek = {})
        Progression(68_000, 233_000, playing = false, onSeek = {})
        Progression(68_000, 0, playing = true, onSeek = {})
    }
}

@Preview(name = "Police à 200 %, violet", backgroundColor = 0xFF0F0D14, showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun ProgressionGrande() = HorizonTheme(seed = 0xFFA06CD5.toInt()) {
    Progression(3_725_000, 7_200_000, playing = false, onSeek = {}, modifier = Modifier.padding(HorizonSpace.s4))
}
