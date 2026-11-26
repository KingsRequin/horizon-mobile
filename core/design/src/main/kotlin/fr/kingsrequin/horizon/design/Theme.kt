package fr.kingsrequin.horizon.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** États : fixes, ils ne suivent pas la couleur du média. Jamais sans le mot ou l'icône à côté. */
val StatusOk = Color(0xFF8FDB9C)
val StatusWait = Color(0xFFF2C66D)
val StatusError = Color(0xFFFFB4AB)
val OnStatusError = Color(0xFF690005)

/** Les couleurs de [palette], prêtes pour Compose. */
@Immutable
data class HorizonScheme(
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val coverGround: Color,
    /** La couleur reçue du PC, telle quelle : les bandes de la pochette de repli. */
    val seed: Color,
)

/** Couleurs du thème en cours : `LocalHorizonColors.current.primary`. */
val LocalHorizonColors = staticCompositionLocalOf<HorizonScheme> { error("HorizonTheme absent") }

/** Fondu entre deux palettes : 700 ms, la même courbe que la barre d'Horizon. */
private val ColorFade = tween<Color>(durationMillis = 700, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f))

/**
 * Le thème de l'appli, toujours sombre, tiré de la seule couleur `seed` (celle que le PC envoie :
 * pochette pendant la lecture, sinon fond d'écran, sinon l'orange d'Horizon). Un changement de
 * `seed` fond les couleurs ; avec les animations du téléphone coupées, Compose saute le fondu.
 */
@Composable
fun HorizonTheme(seed: Int = DEFAULT_SEED, content: @Composable () -> Unit) {
    val target = remember(seed) { palette(seed) }

    @Composable
    fun fade(argb: Int): Color = animateColorAsState(Color(argb), ColorFade, label = "couleur").value

    val scheme = HorizonScheme(
        surface = fade(target.surface),
        surfaceContainer = fade(target.surfaceContainer),
        surfaceContainerHigh = fade(target.surfaceContainerHigh),
        surfaceContainerHighest = fade(target.surfaceContainerHighest),
        onSurface = fade(target.onSurface),
        onSurfaceVariant = fade(target.onSurfaceVariant),
        outline = fade(target.outline),
        outlineVariant = fade(target.outlineVariant),
        primary = fade(target.primary),
        onPrimary = fade(target.onPrimary),
        primaryContainer = fade(target.primaryContainer),
        onPrimaryContainer = fade(target.onPrimaryContainer),
        coverGround = fade(target.coverGround),
        seed = fade(seed or (0xFF shl 24)),
    )
    // Les composants Material employés tels quels (boîtes de dialogue, champs) suivent la palette.
    val material = darkColorScheme(
        primary = scheme.primary,
        onPrimary = scheme.onPrimary,
        primaryContainer = scheme.primaryContainer,
        onPrimaryContainer = scheme.onPrimaryContainer,
        background = scheme.surface,
        onBackground = scheme.onSurface,
        surface = scheme.surface,
        onSurface = scheme.onSurface,
        surfaceVariant = scheme.surfaceContainerHighest,
        onSurfaceVariant = scheme.onSurfaceVariant,
        surfaceContainerLow = scheme.surfaceContainer,
        surfaceContainer = scheme.surfaceContainer,
        surfaceContainerHigh = scheme.surfaceContainerHigh,
        surfaceContainerHighest = scheme.surfaceContainerHighest,
        outline = scheme.outline,
        outlineVariant = scheme.outlineVariant,
        error = StatusError,
        onError = OnStatusError,
    )
    CompositionLocalProvider(LocalHorizonColors provides scheme) {
        MaterialTheme(colorScheme = material, content = content)
    }
}
