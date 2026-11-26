package fr.kingsrequin.horizon.design.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.kingsrequin.horizon.design.HorizonScheme
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.R
import fr.kingsrequin.horizon.design.coverCorner

/**
 * La pochette du morceau, carrée, coins à 27 % du côté (172 dp sur la télécommande, 64 dans la
 * carte de lecture). Sans image, la pochette de repli : jamais de carré vide. Au changement, la
 * nouvelle image fond sur l'ancienne en 700 ms, comme la couleur de l'écran. Rien ne se pose
 * dessus.
 */
@Composable
fun Pochette(image: ImageBitmap?, side: Dp, modifier: Modifier = Modifier) {
    val colors = LocalHorizonColors.current
    val label = stringResource(R.string.media_cover)
    Crossfade(
        targetState = image,
        animationSpec = tween(durationMillis = 700, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)),
        modifier = modifier
            .size(side)
            .clip(RoundedCornerShape(coverCorner(side)))
            .semantics { contentDescription = label },
        label = "pochette",
    ) { shown ->
        if (shown != null) {
            Image(shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Canvas(Modifier.fillMaxSize()) { fallback(colors) }
        }
    }
}

/** Pochette de repli, dessinée sur 100 × 100 : fond `cover-ground`, deux bandes `seed`, un disque `on-surface`. */
private fun DrawScope.fallback(colors: HorizonScheme) {
    scale(size.width / 100f, size.height / 100f, pivot = Offset.Zero) {
        drawRect(colors.coverGround)
        drawPath(band(-20f to 90f, 60f to -10f, 78f to -10f, -2f to 90f), colors.seed, alpha = 0.9f)
        drawPath(band(22f to 100f, 102f to 0f, 112f to 0f, 32f to 100f), colors.seed, alpha = 0.45f)
        drawCircle(colors.onSurface, radius = 14f, center = Offset(72f, 70f))
    }
}

private fun band(vararg corners: Pair<Float, Float>) = Path().apply {
    corners.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
    close()
}

@Preview(name = "Repli, deux tailles", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 300)
@Composable
private fun PochetteRepli() = HorizonTheme {
    Row(Modifier.padding(HorizonSpace.s4), horizontalArrangement = Arrangement.spacedBy(HorizonSpace.s4), verticalAlignment = Alignment.Bottom) {
        Pochette(null, 172.dp)
        Pochette(null, 64.dp)
    }
}

@Preview(name = "Repli, violet", backgroundColor = 0xFF0F0D14, showBackground = true, widthDp = 300)
@Composable
private fun PochetteViolet() = HorizonTheme(seed = 0xFFA06CD5.toInt()) {
    Row(Modifier.padding(HorizonSpace.s4), horizontalArrangement = Arrangement.spacedBy(HorizonSpace.s4), verticalAlignment = Alignment.Bottom) {
        Pochette(null, 172.dp)
        Pochette(null, 64.dp)
    }
}
