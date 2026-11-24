package fr.kingsrequin.horizon.pairing

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kingsrequin.horizon.R
import fr.kingsrequin.horizon.connection.LinkMode
import fr.kingsrequin.horizon.design.HorizonIcons
import fr.kingsrequin.horizon.design.HorizonShapes
import fr.kingsrequin.horizon.design.HorizonSize
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.StatusWait
import fr.kingsrequin.horizon.design.components.Bouton
import fr.kingsrequin.horizon.design.components.Groupe

/** Le parcours d'appairage, relié au téléphone : permissions, caméra, réglages du système. */
@Composable
fun PairingFlow(viewModel: PairingViewModel, onDone: () -> Unit) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    // La caméra se demande à l'entrée du viseur, pas avant.
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), viewModel::onCameraPermission)
    // La notification du service : accordée ou non, la liaison marche.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.choose(LinkMode.Continuous) }
    LaunchedEffect(step) {
        when (step) {
            PairingStep.Connecting -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            PairingStep.Done -> onDone()
            else -> Unit
        }
    }
    PairingScreen(
        step = step,
        onScan = { camera.launch(Manifest.permission.CAMERA) },
        onRetry = viewModel::retry,
        onOpenSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) },
        onChoose = { mode ->
            if (mode == LinkMode.Continuous && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.choose(mode)
            }
        },
        scanner = { QrScanner(viewModel::onQr, it) },
    )
}

/** Les écrans du parcours, sans rien du téléphone : `scanner` pose le viseur (ou son image, dans un aperçu). */
@Composable
fun PairingScreen(
    step: PairingStep,
    onScan: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onChoose: (LinkMode) -> Unit,
    scanner: @Composable (Modifier) -> Unit,
) {
    val colors = LocalHorizonColors.current
    if (step is PairingStep.Scanning) return Viewfinder(step.hint, scanner)
    Column(
        Modifier.fillMaxSize().background(colors.surface).systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = HorizonSpace.s4),
        verticalArrangement = Arrangement.spacedBy(HorizonSpace.s4),
    ) {
        Spacer(Modifier.height(HorizonSpace.s8 - HorizonSpace.s4))
        when (step) {
            PairingStep.Intro, PairingStep.Done -> {
                Title(stringResource(R.string.pairing_title))
                Text(stringResource(R.string.pairing_intro), style = HorizonType.bodyLarge, color = colors.onSurface, modifier = Modifier.padding(horizontal = HorizonSpace.s2))
                Text(stringResource(R.string.pairing_intro_network), style = HorizonType.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = HorizonSpace.s2))
                Spacer(Modifier.height(HorizonSpace.s2))
                Bouton(stringResource(R.string.pairing_scan), onScan)
            }
            PairingStep.CameraDenied -> {
                Title(stringResource(R.string.pairing_title))
                Problem(stringResource(R.string.pairing_camera_denied))
                Bouton(stringResource(R.string.pairing_open_settings), onOpenSettings)
                Bouton(stringResource(R.string.pairing_retry), onScan, primary = false)
            }
            PairingStep.Connecting -> {
                Title(stringResource(R.string.pairing_title))
                Box(Modifier.fillMaxWidth().padding(vertical = HorizonSpace.s6), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary, trackColor = colors.surfaceContainerHighest)
                }
                Text(
                    stringResource(R.string.pairing_connecting),
                    style = HorizonType.bodyLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            is PairingStep.Failed -> {
                Title(stringResource(R.string.pairing_title))
                Problem(stringResource(step.message))
                Bouton(stringResource(R.string.pairing_retry), onRetry)
            }
            is PairingStep.ChooseMode -> {
                Title(stringResource(R.string.pairing_done_title, step.pcName))
                Text(stringResource(R.string.pairing_mode_question), style = HorizonType.bodyLarge, color = colors.onSurface, modifier = Modifier.padding(horizontal = HorizonSpace.s2))
                Groupe {
                    Choice(stringResource(R.string.pairing_mode_continuous), stringResource(R.string.pairing_mode_continuous_detail)) { onChoose(LinkMode.Continuous) }
                    Choice(stringResource(R.string.pairing_mode_when_open), stringResource(R.string.pairing_mode_when_open_detail)) { onChoose(LinkMode.WhenOpen) }
                }
            }
            is PairingStep.Scanning -> Unit
        }
        Spacer(Modifier.height(HorizonSpace.s5))
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = HorizonType.titleLarge, color = LocalHorizonColors.current.onSurface, modifier = Modifier.padding(horizontal = HorizonSpace.s2).semantics { heading() })
}

/** Ce qui ne va pas : une icône d'alerte et le texte, annoncé par le lecteur d'écran. */
@Composable
private fun Problem(text: String) {
    val colors = LocalHorizonColors.current
    Column(
        Modifier.fillMaxWidth().clip(HorizonShapes.extraLarge).background(colors.surfaceContainer).padding(HorizonSpace.s4).semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(HorizonSpace.s3),
    ) {
        Icon(painterResource(HorizonIcons.Warning), contentDescription = null, Modifier.size(HorizonSize.icon), tint = StatusWait)
        Text(text, style = HorizonType.bodyLarge, color = colors.onSurface)
    }
}

/** Un choix du mode de liaison : son nom et ce qu'il implique. */
@Composable
private fun Choice(label: String, detail: String, onClick: () -> Unit) {
    val colors = LocalHorizonColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(HorizonShapes.small)
            .background(colors.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = HorizonSize.row)
            .padding(horizontal = HorizonSpace.s4, vertical = HorizonSpace.s4),
        verticalArrangement = Arrangement.spacedBy(HorizonSpace.s1),
    ) {
        Text(label, style = HorizonType.labelLarge, color = colors.onSurface)
        Text(detail, style = HorizonType.bodyMedium, color = colors.onSurfaceVariant)
    }
}

/** Le viseur plein écran, sa consigne en haut et, en bas, pourquoi le dernier code lu ne convient pas. */
@Composable
private fun Viewfinder(hint: Int?, scanner: @Composable (Modifier) -> Unit) {
    val colors = LocalHorizonColors.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        scanner(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(HorizonSpace.s4), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.pairing_scan_title),
                style = HorizonType.labelLarge,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.clip(HorizonShapes.large).background(colors.surface.copy(alpha = 0.86f)).padding(horizontal = HorizonSpace.s4, vertical = HorizonSpace.s3),
            )
            // Toujours présent, pour que le lecteur d'écran annonce le texte quand il change.
            Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                if (hint != null) {
                    Text(
                        stringResource(hint),
                        style = HorizonType.bodyLarge,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clip(HorizonShapes.large).background(colors.surface.copy(alpha = 0.86f)).padding(horizontal = HorizonSpace.s4, vertical = HorizonSpace.s3),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepPreview(step: PairingStep) = HorizonTheme {
    PairingScreen(step, {}, {}, {}, {}) { Box(it.background(Color(0xFF2A2A2A))) }
}

@Preview(name = "1. Explication", widthDp = 360, heightDp = 640)
@Composable
private fun PairingIntro() = StepPreview(PairingStep.Intro)

@Preview(name = "Caméra refusée", widthDp = 360, heightDp = 640)
@Composable
private fun PairingCameraDenied() = StepPreview(PairingStep.CameraDenied)

@Preview(name = "2. Viseur, mauvais code", widthDp = 360, heightDp = 640)
@Composable
private fun PairingScanning() = StepPreview(PairingStep.Scanning(R.string.pairing_not_horizon))

@Preview(name = "Connexion", widthDp = 360, heightDp = 640)
@Composable
private fun PairingConnecting() = StepPreview(PairingStep.Connecting)

@Preview(name = "Échec", widthDp = 360, heightDp = 640)
@Composable
private fun PairingFailed() = StepPreview(PairingStep.Failed(R.string.pairing_unreachable))

@Preview(name = "3. Choix du mode", widthDp = 360, heightDp = 640)
@Composable
private fun PairingChooseMode() = StepPreview(PairingStep.ChooseMode("Bureau"))

@Preview(name = "Choix du mode, police à 200 %", widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun PairingChooseModeLarge() = StepPreview(PairingStep.ChooseMode("Bureau"))
