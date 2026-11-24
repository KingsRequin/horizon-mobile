package fr.kingsrequin.horizon.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import fr.kingsrequin.horizon.design.HorizonShapes
import fr.kingsrequin.horizon.design.HorizonSpace

/**
 * Des lignes soudées en un bloc : `shape-extra-large` aux extrémités, `shape-small` aux coins
 * qui se font face, écart `space-1`. Les lignes posées dedans ([LigneEnvoi]) ont elles-mêmes
 * des coins `shape-small` : le groupe arrondit le dehors. Une ligne seule se pose aussi dans un
 * groupe, pour avoir les coins d'une carte.
 */
@Composable
fun Groupe(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(HorizonShapes.extraLarge), verticalArrangement = Arrangement.spacedBy(HorizonSpace.s1), content = content)
}
