package fr.kingsrequin.horizon.design.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.kingsrequin.horizon.design.HorizonIcons
import fr.kingsrequin.horizon.design.HorizonShapes
import fr.kingsrequin.horizon.design.HorizonSize
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.R
import fr.kingsrequin.horizon.design.StatusError

/** Hauteur d'une ligne de niveau (`row-level`) et seuil « faible » d'Horizon. */
private val RowLevel = 76.dp
private const val LOW = 15

/**
 * Le niveau de batterie d'un appareil que le PC connaît (aujourd'hui son casque) : la ligne se
 * remplit à hauteur du pourcentage (`primary-container`, `status-error` quand c'est faible). En
 * charge, faible, ou mesure ancienne (`age` : « Il y a 3 h », contenu estompé) : une ligne sous
 * le nom. TalkBack lit un seul élément. Se pose dans un [Groupe].
 */
@Composable
fun LigneNiveau(@DrawableRes icon: Int, name: String, percent: Int, charging: Boolean, modifier: Modifier = Modifier, age: String? = null) {
    val colors = LocalHorizonColors.current
    val level = percent.coerceIn(0, 100)
    val low = level <= LOW && !charging
    val spoken = pluralStringResource(R.plurals.level_spoken, level, name, level)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = RowLevel)
            .clip(HorizonShapes.small)
            .background(colors.surfaceContainer)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Box(Modifier.matchParentSize()) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(level / 100f).background(if (low) StatusError.copy(alpha = 0.35f) else colors.primaryContainer))
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = RowLevel).alpha(if (age != null) 0.6f else 1f).padding(horizontal = HorizonSpace.s4, vertical = HorizonSpace.s3),
            horizontalArrangement = Arrangement.spacedBy(HorizonSpace.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(HorizonSize.icon))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HorizonSpace.half)) {
                Text(name, style = HorizonType.titleMedium, color = colors.onSurface, maxLines = 2)
                val (subIcon, sub) = when {
                    charging -> HorizonIcons.Lightning to stringResource(R.string.level_charging)
                    low -> HorizonIcons.Warning to stringResource(R.string.level_low)
                    age != null -> null to age
                    else -> null to null
                }
                if (sub != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(HorizonSpace.s1), verticalAlignment = Alignment.CenterVertically) {
                        if (subIcon != null) Icon(painterResource(subIcon), contentDescription = null, tint = if (low) StatusError else colors.onSurface, modifier = Modifier.size(16.dp))
                        Text(sub, style = HorizonType.bodySmall, color = colors.onSurface)
                    }
                }
            }
            Text(stringResource(R.string.volume_value, level), style = HorizonType.numeralLarge, color = if (low) StatusError else colors.onSurface)
        }
    }
}

@Preview(name = "Normal, en charge, faible, ancien", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun LigneNiveauApercu() = HorizonTheme {
    Groupe(Modifier.padding(HorizonSpace.s4)) {
        LigneNiveau(HorizonIcons.Headset, "Casque du PC", 92, charging = false)
        LigneNiveau(HorizonIcons.Headset, "Casque du PC", 40, charging = true)
        LigneNiveau(HorizonIcons.Headset, "Casque du PC", 12, charging = false)
        LigneNiveau(HorizonIcons.Headset, "Casque du PC", 70, charging = false, age = "Il y a 3 h")
    }
}

@Preview(name = "Police à 200 %, violet", backgroundColor = 0xFF0F0D14, showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun LigneNiveauGrande() = HorizonTheme(seed = 0xFFA06CD5.toInt()) {
    Groupe(Modifier.padding(HorizonSpace.s4)) { LigneNiveau(HorizonIcons.Headset, "Casque du PC", 92, charging = true) }
}
