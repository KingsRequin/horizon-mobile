package fr.kingsrequin.horizon.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Les formes de la charte. Pas d'ombre ni de bordure : les niveaux de surface détachent les éléments. */
object HorizonShapes {
    /** Coins qui se font face dans un contrôle en deux moitiés. */
    val extraSmall = RoundedCornerShape(4.dp)

    /** Coins qui se font face entre les éléments soudés d'un groupe. */
    val small = RoundedCornerShape(8.dp)

    /** Pastille d'état, pastille de l'onglet choisi. */
    val large = RoundedCornerShape(16.dp)

    /** Boutons carrés. */
    val largeIncreased = RoundedCornerShape(20.dp)

    /** Carte seule et extrémités d'un groupe. */
    val extraLarge = RoundedCornerShape(28.dp)
    val full = CircleShape
}

/** Les espacements de la charte. */
object HorizonSpace {
    val half = 2.dp

    /** Entre deux éléments soudés d'un même groupe ; entre l'icône et le libellé d'un onglet. */
    val s1 = 4.dp
    val s2 = 8.dp

    /** Entre l'icône et le texte d'une ligne ; entre le titre et la pastille d'état. */
    val s3 = 12.dp

    /** Marge latérale de l'écran ; écart entre deux groupes ; marge intérieure d'une ligne. */
    val s4 = 16.dp

    /** Au-dessus d'un titre de groupe. */
    val s5 = 24.dp
    val s6 = 32.dp

    /** Du haut de l'écran au premier titre. */
    val s8 = 64.dp
}

/** Les tailles de la charte. */
object HorizonSize {
    /** Côté minimal de toute zone tactile. */
    val touchMin = 48.dp
    val icon = 22.dp

    /** Hauteur d'une ligne d'envoi ou de réglage. */
    val row = 64.dp

    /** Hauteur de la barre de navigation, zone des gestes non comprise. */
    val navHeight = 68.dp
}
