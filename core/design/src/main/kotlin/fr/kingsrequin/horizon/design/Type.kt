package fr.kingsrequin.horizon.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Une seule famille, Roboto Flex, en coupes figées (tools/polices.ps1). La largeur fait la voix :
// 118 % pour le nom du PC, 114 % pour les titres, 112 % pour les grands chiffres, 100 % ailleurs.
private val Flex = FontFamily(
    Font(R.font.roboto_flex_w400, FontWeight(400)),
    Font(R.font.roboto_flex_w600, FontWeight(600)),
    Font(R.font.roboto_flex_w650, FontWeight(650)),
    Font(R.font.roboto_flex_w700, FontWeight(700)),
)
private val Flex112 = FontFamily(Font(R.font.roboto_flex_w750_wd112, FontWeight(750)))
private val Flex114 = FontFamily(Font(R.font.roboto_flex_w750_wd114, FontWeight(750)))
private val Flex118 = FontFamily(Font(R.font.roboto_flex_w750_wd118, FontWeight(750)))

/** Chiffres de même largeur : une valeur qui change ne fait pas bouger le texte autour. */
private const val TABULAR = "tnum"

/** Les styles de texte de la charte, par leur nom. Tailles en `sp` : le texte suit le réglage du téléphone. */
object HorizonType {
    /** Le nom du PC en tête de l'accueil, une seule fois par écran. */
    val displayLarge = TextStyle(fontFamily = Flex118, fontWeight = FontWeight(750), fontSize = 56.sp, lineHeight = 54.sp, letterSpacing = (-0.02).em)

    /** Titre du morceau sur la télécommande ; repli d'un nom de PC trop long. */
    val headlineMedium = TextStyle(fontFamily = Flex114, fontWeight = FontWeight(750), fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em)

    /** Titre d'un écran qui n'est pas l'accueil. */
    val titleLarge = TextStyle(fontFamily = Flex114, fontWeight = FontWeight(750), fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.01).em)

    /** Pourcentage d'une ligne de niveau. */
    val numeralLarge = TextStyle(fontFamily = Flex112, fontWeight = FontWeight(750), fontSize = 30.sp, lineHeight = 32.sp, fontFeatureSettings = TABULAR)

    val titleMedium = TextStyle(fontFamily = Flex, fontWeight = FontWeight(650), fontSize = 17.sp, lineHeight = 22.sp)

    /** Libellé d'une ligne d'envoi ou de réglage, texte d'un bouton. */
    val labelLarge = TextStyle(fontFamily = Flex, fontWeight = FontWeight(650), fontSize = 15.sp, lineHeight = 20.sp)

    /** Texte courant : explications de l'appairage et des réglages. */
    val bodyLarge = TextStyle(fontFamily = Flex, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp)
    val bodyMedium = TextStyle(fontFamily = Flex, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp)

    /** Heure d'un envoi, titre d'un groupe de lignes (en graisse 650). */
    val bodySmall = TextStyle(fontFamily = Flex, fontWeight = FontWeight(400), fontSize = 13.sp, lineHeight = 18.sp)

    /** Libellé d'un onglet. */
    val labelMedium = TextStyle(fontFamily = Flex, fontWeight = FontWeight(600), fontSize = 12.sp, lineHeight = 16.sp)
    val numeralSmall = TextStyle(fontFamily = Flex, fontWeight = FontWeight(700), fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TABULAR)
}
