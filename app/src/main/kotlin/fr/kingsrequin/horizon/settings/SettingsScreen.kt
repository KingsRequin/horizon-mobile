package fr.kingsrequin.horizon.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import fr.kingsrequin.horizon.R
import fr.kingsrequin.horizon.connection.LinkMode
import fr.kingsrequin.horizon.design.HorizonShapes
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.components.Bouton
import fr.kingsrequin.horizon.design.components.Groupe
import fr.kingsrequin.horizon.design.components.LigneReglage

/** Ce que les réglages savent faire ; l'appli branche chaque action sur le téléphone. */
class SettingsActions(
    val setMode: (LinkMode) -> Unit = {},
    val forget: () -> Unit = {},
    /** Réglage de la langue par appli du système ; `null` avant Android 13, où il n'existe pas. */
    val language: (() -> Unit)? = null,
    val copyReport: () -> Unit = {},
    val licences: () -> Unit = {},
    val source: () -> Unit = {},
    val download: (Update) -> Unit = {},
    /** Commandes du PC dans le volet et sur l'écran verrouillé ; `null` sans télécommande. */
    val systemControls: ((Boolean) -> Unit)? = null,
)

/** Les réglages : mode de liaison, PC appairé, langue, rapport de diagnostic, à propos. */
@Composable
fun SettingsScreen(mode: LinkMode, pcName: String, version: String, update: Update?, actions: SettingsActions, modifier: Modifier = Modifier, systemControls: Boolean = true) {
    val colors = LocalHorizonColors.current
    // Oublier le PC se confirme dans l'écran, sans boîte de dialogue.
    var forgetting by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().background(colors.surface).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = HorizonSpace.s4),
        verticalArrangement = Arrangement.spacedBy(HorizonSpace.s2),
    ) {
        Spacer(Modifier.height(HorizonSpace.s8 - HorizonSpace.s2))
        Text(stringResource(R.string.tab_settings), style = HorizonType.titleLarge, color = colors.onSurface, modifier = Modifier.padding(horizontal = HorizonSpace.s2).semantics { heading() })
        if (update != null) {
            Spacer(Modifier.height(HorizonSpace.s2))
            Bouton(stringResource(R.string.update_available, update.version), { actions.download(update) })
        }
        Section(stringResource(R.string.settings_link))
        Groupe {
            LigneReglage(stringResource(R.string.pairing_mode_continuous), detail = stringResource(R.string.pairing_mode_continuous_detail), selected = mode == LinkMode.Continuous) { actions.setMode(LinkMode.Continuous) }
            LigneReglage(stringResource(R.string.pairing_mode_when_open), detail = stringResource(R.string.pairing_mode_when_open_detail), selected = mode == LinkMode.WhenOpen) { actions.setMode(LinkMode.WhenOpen) }
        }
        actions.systemControls?.let { set ->
            Section(stringResource(R.string.settings_remote))
            Groupe {
                LigneReglage(stringResource(R.string.settings_system_controls), detail = stringResource(R.string.settings_system_controls_detail), selected = systemControls) { set(!systemControls) }
            }
        }
        Section(stringResource(R.string.settings_pc))
        if (forgetting) {
            Column(
                Modifier.fillMaxWidth().clip(HorizonShapes.extraLarge).background(colors.surfaceContainer).padding(HorizonSpace.s4).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(HorizonSpace.s3),
            ) {
                Text(stringResource(R.string.settings_forget_confirm, pcName), style = HorizonType.bodyLarge, color = colors.onSurface)
                Bouton(stringResource(R.string.settings_forget_yes), {
                    forgetting = false
                    actions.forget()
                })
                Bouton(stringResource(R.string.pairing_cancel), { forgetting = false }, primary = false)
            }
        } else {
            Groupe {
                LigneReglage(pcName)
                LigneReglage(stringResource(R.string.settings_forget)) { forgetting = true }
            }
        }
        Section(stringResource(R.string.settings_app))
        Groupe {
            actions.language?.let { LigneReglage(stringResource(R.string.settings_language), onClick = it) }
            LigneReglage(stringResource(R.string.settings_report), detail = stringResource(R.string.settings_report_detail), onClick = actions.copyReport)
            LigneReglage(stringResource(R.string.settings_licences), onClick = actions.licences)
            LigneReglage(stringResource(R.string.settings_source), onClick = actions.source)
            LigneReglage(stringResource(R.string.settings_version, version))
        }
        Spacer(Modifier.height(HorizonSpace.s5))
    }
}

/** Titre d'un groupe de lignes. */
@Composable
private fun Section(title: String) {
    Text(
        title,
        style = HorizonType.bodySmall,
        fontWeight = FontWeight(650),
        color = LocalHorizonColors.current.onSurfaceVariant,
        modifier = Modifier.padding(start = HorizonSpace.s2, top = HorizonSpace.s5 - HorizonSpace.s2).semantics { heading() },
    )
}

@Preview(name = "Réglages", widthDp = 360, heightDp = 900)
@Composable
private fun SettingsPreview() = HorizonTheme {
    SettingsScreen(LinkMode.Continuous, "Bureau", "0.1.0", Update("0.2.0", "https://exemple.fr"), SettingsActions(language = {}, systemControls = {}))
}

@Preview(name = "Réglages, police à 200 %", widthDp = 360, heightDp = 1400, fontScale = 2f)
@Composable
private fun SettingsLarge() = HorizonTheme { SettingsScreen(LinkMode.WhenOpen, "Bureau", "0.1.0", null, SettingsActions()) }
