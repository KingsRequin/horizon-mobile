package fr.kingsrequin.horizon.design

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** L'orange d'Horizon : la couleur de départ tant que le PC n'en a pas envoyé d'autre. */
const val DEFAULT_SEED: Int = 0xFFF97316.toInt()

/** Les couleurs de l'appli (ARGB opaques), toutes tirées d'une seule couleur : voir [palette]. */
data class HorizonColors(
    val surface: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val outlineVariant: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val coverGround: Int,
)

/** Teinte (en degrés OKLCH) d'une couleur sans teinte : celle de l'orange d'Horizon. */
private const val DEFAULT_HUE = 48.0

/** En dessous de cette saturation OKLCH, une couleur est un gris : sa teinte ne veut rien dire. */
private const val GREY_CHROMA = 0.02

/** Pas de baisse de la saturation quand une couleur sort du gamut sRVB. */
private const val CHROMA_STEP = 0.002

/**
 * La palette de la charte pour la couleur `seed` (son alpha est ignoré). Chaque rôle a une
 * luminosité et une saturation fixes en OKLCH ; seule la teinte vient de `seed`, arrondie au
 * degré. Les contrastes ne dépendent donc pas de la couleur reçue. Fonction pure.
 */
fun palette(seed: Int): HorizonColors {
    val hue = hueOf(seed)
    fun role(lightness: Double, chroma: Double): Int = oklch(lightness, chroma, hue)
    return HorizonColors(
        surface = role(0.165, 0.012),
        surfaceContainer = role(0.215, 0.018),
        surfaceContainerHigh = role(0.265, 0.022),
        surfaceContainerHighest = role(0.32, 0.028),
        onSurface = role(0.93, 0.018),
        onSurfaceVariant = role(0.80, 0.03),
        outline = role(0.62, 0.03),
        outlineVariant = role(0.38, 0.022),
        primary = role(0.82, 0.11),
        onPrimary = role(0.27, 0.07),
        primaryContainer = role(0.36, 0.065),
        onPrimaryContainer = role(0.90, 0.05),
        coverGround = role(0.25, 0.06),
    )
}

/** Teinte OKLCH de `color`, arrondie au degré ; celle par défaut pour un gris. */
private fun hueOf(color: Int): Double {
    fun linear(channel: Int): Double = (channel / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    val (r, g, b) = Triple(linear(color shr 16 and 0xFF), linear(color shr 8 and 0xFF), linear(color and 0xFF))
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    val bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    if (hypot(a, bb) < GREY_CHROMA) return DEFAULT_HUE
    val degrees = Math.toDegrees(atan2(bb, a)).roundToInt()
    return ((degrees % 360 + 360) % 360).toDouble()
}

/** Couleur sRVB (ARGB opaque) d'une couleur OKLCH ; hors gamut, la saturation baisse jusqu'à y rentrer. */
private fun oklch(lightness: Double, chroma: Double, hue: Double): Int {
    var c = chroma
    while (true) {
        val rgb = linearSrgb(lightness, c, hue)
        if (c <= 0.0 || rgb.all { it in -GAMUT_SLACK..1.0 + GAMUT_SLACK }) {
            val (r, g, b) = rgb.map(::encode)
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        c = (c - CHROMA_STEP).coerceAtLeast(0.0)
    }
}

/**
 * Marge du test de gamut, en lumière linéaire : un canal à peine négatif (moins d'un millième)
 * est ramené à zéro plutôt que de faire baisser la saturation. C'est ce qui donne les valeurs de
 * la charte pour les teintes dont un canal touche le bord (cyan).
 */
private const val GAMUT_SLACK = 0.001

private fun linearSrgb(lightness: Double, chroma: Double, hue: Double): List<Double> {
    val radians = hue * PI / 180.0
    val (a, b) = chroma * cos(radians) to chroma * sin(radians)
    val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    return listOf(
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
    )
}

/** Canal linéaire → canal sRVB sur 8 bits. */
private fun encode(linear: Double): Int {
    val v = linear.coerceIn(0.0, 1.0)
    val srgb = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1.0 / 2.4) - 0.055
    return (srgb * 255.0).roundToInt().coerceIn(0, 255)
}
