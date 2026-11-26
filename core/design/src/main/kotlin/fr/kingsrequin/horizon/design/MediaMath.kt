package fr.kingsrequin.horizon.design

import androidx.compose.ui.unit.Dp
import java.util.Locale

/** Rayon des coins d'une pochette (`shape-cover`) : 27 % de son côté, jamais fixé en dp. */
fun coverCorner(side: Dp): Dp = side * 0.27f

/** Une durée lue comme sur un lecteur : « 1:08 », « 1:02:05 ». La seconde entamée ne compte pas. */
fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val (h, m, s) = Triple(total / 3600, total / 60 % 60, total % 60)
    // Chiffres latins quelle que soit la langue du téléphone, comme sur la barre d'Horizon.
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/** Un temps restant, la seconde entamée comptée (« 5:00 » au départ, comme la barre d'Horizon). */
fun formatCountdown(ms: Long): String = formatTime(ms.coerceAtLeast(0) + 999)

/** L'initiale d'une appli (sa pastille dans le mixeur quand Horizon n'envoie pas d'icône). */
fun appInitial(name: String): String = name.trim().firstOrNull()?.uppercase() ?: "?"

/** Ce que la glissière de volume fait sentir. */
enum class VolumeTick { None, Step, Edge }

/** Cran haptique en passant de `before` à `after` (0 à 1) : tous les 5 %, plus net à 0 et à 100 %. */
fun volumeTick(before: Float, after: Float): VolumeTick {
    if (after != before && (after <= 0f || after >= 1f)) return VolumeTick.Edge
    return if (step(before) != step(after)) VolumeTick.Step else VolumeTick.None
}

/** Le cran de 5 % où se trouve `volume`. */
private fun step(volume: Float): Int = Math.floorDiv(Math.round(volume * 1000), 50)
