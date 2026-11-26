package fr.kingsrequin.horizon.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.kingsrequin.horizon.design.HorizonIcons
import fr.kingsrequin.horizon.design.HorizonShapes
import fr.kingsrequin.horizon.design.HorizonSize
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors

/** Hauteur des boutons de la carte (`control`). */
private val Control = 48.dp

/**
 * La lecture en cours sur le PC, en une carte de l'accueil : pochette, titre, artiste, lecture ou
 * pause (le seul `primary` de l'accueil) et morceau suivant. Appuyer ailleurs ouvre la
 * télécommande. `title` nul : rien ne joue, la carte reste (« Rien en lecture », pochette de
 * repli, boutons estompés) pour que la mise en page ne saute pas.
 */
@Composable
fun CarteLecture(
    image: ImageBitmap?,
    title: String?,
    artist: String?,
    playing: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHorizonColors.current
    val something = title != null
    // Police agrandie : les boutons passent sous le titre, qui garde la largeur de la carte.
    val big = LocalDensity.current.fontScale > 1.3f
    val texts = @Composable { m: Modifier ->
        Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title ?: "Rien en lecture", style = HorizonType.titleMedium, color = colors.onSurface, maxLines = if (big || !something) 2 else 1, overflow = TextOverflow.Ellipsis)
            if (something && !artist.isNullOrEmpty()) {
                Text(artist, style = HorizonType.bodyMedium, color = colors.onSurfaceVariant, maxLines = if (big) 2 else 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    val buttons = @Composable {
        Commande(
            if (playing) HorizonIcons.PauseFill else HorizonIcons.PlayFill,
            if (playing) "Pause" else "Lecture",
            something,
            colors.primary,
            colors.onPrimary,
            HorizonShapes.largeIncreased,
            Modifier.size(60.dp, Control),
            onClick = onPlayPause,
        )
        Commande(
            HorizonIcons.SkipForwardFill,
            "Morceau suivant",
            something,
            colors.surfaceContainerHighest,
            colors.onSurface,
            HorizonShapes.largeIncreased,
            Modifier.size(HorizonSize.touchMin, Control),
            onClick = onNext,
        )
    }
    val card = modifier
        .fillMaxWidth()
        .clip(HorizonShapes.extraLarge)
        .background(colors.surfaceContainerHigh)
        .clickable(role = Role.Button, onClickLabel = "Ouvrir la télécommande", onClick = onOpen)
        .padding(14.dp)
    val gap = Arrangement.spacedBy(HorizonSpace.s3)
    if (big) {
        Column(card, verticalArrangement = gap) {
            Row(horizontalArrangement = gap, verticalAlignment = Alignment.CenterVertically) {
                Pochette(image.takeIf { something }, 64.dp)
                texts(Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HorizonSpace.s3, Alignment.End)) { buttons() }
        }
    } else {
        Row(card, horizontalArrangement = gap, verticalAlignment = Alignment.CenterVertically) {
            Pochette(image.takeIf { something }, 64.dp)
            texts(Modifier.weight(1f))
            buttons()
        }
    }
}

@Preview(name = "En lecture, en pause, rien", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun CarteLectureApercu() = HorizonTheme {
    Column(Modifier.padding(HorizonSpace.s4), verticalArrangement = Arrangement.spacedBy(HorizonSpace.s4)) {
        CarteLecture(null, "FROGZ", "SPLATA", playing = true, onPlayPause = {}, onNext = {}, onOpen = {})
        CarteLecture(null, "Un titre bien trop long pour tenir sur une seule ligne", "SPLATA", playing = false, onPlayPause = {}, onNext = {}, onOpen = {})
        CarteLecture(null, null, null, playing = false, onPlayPause = {}, onNext = {}, onOpen = {})
    }
}

@Preview(name = "Police à 200 %", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun CarteLectureGrande() = HorizonTheme {
    CarteLecture(null, "FROGZ", "SPLATA", playing = true, onPlayPause = {}, onNext = {}, onOpen = {}, modifier = Modifier.padding(HorizonSpace.s4))
}

@Preview(name = "Violet", backgroundColor = 0xFF0F0D14, showBackground = true, widthDp = 360)
@Composable
private fun CarteLectureViolet() = HorizonTheme(seed = 0xFFA06CD5.toInt()) {
    CarteLecture(null, "FROGZ", "SPLATA", playing = true, onPlayPause = {}, onNext = {}, onOpen = {}, modifier = Modifier.padding(HorizonSpace.s4))
}
