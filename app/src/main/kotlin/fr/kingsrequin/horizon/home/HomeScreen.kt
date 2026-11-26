package fr.kingsrequin.horizon.home

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import fr.kingsrequin.horizon.R
import fr.kingsrequin.horizon.design.HorizonIcons
import fr.kingsrequin.horizon.design.HorizonShapes
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.components.Bouton
import fr.kingsrequin.horizon.design.components.CarteLecture
import fr.kingsrequin.horizon.design.components.EtatLiaison
import fr.kingsrequin.horizon.design.components.Groupe
import fr.kingsrequin.horizon.design.components.LigneEnvoi
import fr.kingsrequin.horizon.design.components.LinkTone
import fr.kingsrequin.horizon.design.components.SendState
import fr.kingsrequin.horizon.link.LinkState
import fr.kingsrequin.horizon.link.OfflineReason
import fr.kingsrequin.horizon.settings.Advice
import fr.kingsrequin.horizon.ui.agoOf
import fr.kingsrequin.horizon.ui.text

/**
 * L'accueil : le PC et l'état de la liaison, la lecture en cours sur le PC (`media`, la carte de
 * lecture, si ce téléphone a la télécommande), les actions rapides et le casque du PC
 * (`actions`, avec le droit « Son et actions »), ce que le téléphone lui envoie (jamais le
 * pourcentage : la barre d'Horizon est là pour ça), et au besoin une carte qui dit quoi faire.
 * `lastSentMs` : heure du dernier envoi de la batterie ; `nowMs` : l'heure, qui avance seule.
 */
@Composable
fun HomeScreen(
    state: LinkState,
    lastSentMs: Long?,
    nowMs: Long,
    advice: Advice?,
    onPairAgain: () -> Unit,
    onAdvice: (Advice) -> Unit,
    onStatus: () -> Unit,
    modifier: Modifier = Modifier,
    media: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val colors = LocalHorizonColors.current
    val res = LocalResources.current
    val (name, tone) = when (state) {
        is LinkState.Connected -> state.pcName to LinkTone.Connected
        is LinkState.Connecting -> state.pcName to LinkTone.Waiting
        is LinkState.Offline -> state.pcName to LinkTone.Offline
        is LinkState.Rejected -> state.pcName to LinkTone.Offline
        LinkState.NotPaired, LinkState.Starting -> return
    }
    val status = when (state) {
        is LinkState.Connected -> stringResource(R.string.status_connected)
        is LinkState.Connecting -> stringResource(R.string.status_connecting)
        is LinkState.Rejected -> stringResource(R.string.status_rejected)
        is LinkState.Offline -> when (state.reason) {
            OfflineReason.NoNetwork -> stringResource(R.string.status_no_wifi)
            OfflineReason.WrongPc -> stringResource(R.string.status_wrong_pc)
            OfflineReason.Incompatible -> stringResource(R.string.status_incompatible)
            OfflineReason.Paused -> stringResource(R.string.status_paused)
            OfflineReason.Unreachable -> stringResource(R.string.status_offline_seen, agoOf(nowMs - state.sinceMs).text(res).replaceFirstChar(Char::lowercase))
        }
    }
    Column(
        modifier.fillMaxSize().background(colors.surface).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = HorizonSpace.s4),
        verticalArrangement = Arrangement.spacedBy(HorizonSpace.s4),
    ) {
        Spacer(Modifier.height(HorizonSpace.s8 - HorizonSpace.s4))
        EtatLiaison(name, status, tone, onStatus)
        when {
            state is LinkState.Rejected -> Card(stringResource(R.string.home_rejected), null, stringResource(R.string.home_pair_again), onPairAgain)
            state is LinkState.Offline && state.reason == OfflineReason.Incompatible -> Card(stringResource(R.string.pairing_version), null, null) {}
            state is LinkState.Offline && state.reason == OfflineReason.WrongPc -> Card(stringResource(R.string.pairing_wrong_pc), null, stringResource(R.string.home_pair_again), onPairAgain)
            advice == Advice.AllowBackground -> Card(stringResource(R.string.advice_background), stringResource(R.string.advice_background_detail), stringResource(R.string.advice_allow)) { onAdvice(advice) }
            advice == Advice.WasKilled -> Card(stringResource(R.string.advice_killed), stringResource(R.string.advice_killed_detail), stringResource(R.string.pairing_open_settings)) { onAdvice(advice) }
        }
        media?.invoke()
        actions?.invoke()
        Column(verticalArrangement = Arrangement.spacedBy(HorizonSpace.s2)) {
            Text(
                stringResource(R.string.home_sent),
                style = HorizonType.bodySmall,
                fontWeight = FontWeight(650),
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = HorizonSpace.s2, top = HorizonSpace.s2).semantics { heading() },
            )
            Groupe {
                val sent = lastSentMs.takeIf { state is LinkState.Connected }
                LigneEnvoi(
                    icon = HorizonIcons.DeviceMobile,
                    label = stringResource(R.string.home_battery),
                    detail = if (sent != null) agoOf(nowMs - sent).text(res) else stringResource(R.string.home_waiting),
                    state = if (sent != null) SendState.Sent else SendState.Waiting,
                    onClick = onStatus,
                )
            }
        }
        Spacer(Modifier.height(HorizonSpace.s5))
    }
}

/** Une carte qui dit ce qui ne va pas et, s'il y a quelque chose à faire, le bouton pour le faire. */
@Composable
private fun Card(title: String, detail: String?, action: String?, onAction: () -> Unit) {
    val colors = LocalHorizonColors.current
    Column(
        Modifier.fillMaxWidth().clip(HorizonShapes.extraLarge).background(colors.surfaceContainer).padding(HorizonSpace.s4),
        verticalArrangement = Arrangement.spacedBy(HorizonSpace.s3),
    ) {
        Text(title, style = HorizonType.titleMedium, color = colors.onSurface)
        if (detail != null) Text(detail, style = HorizonType.bodyMedium, color = colors.onSurfaceVariant)
        if (action != null) Bouton(action, onAction, primary = false)
    }
}

@Composable
private fun HomePreview(state: LinkState, seed: Int = 0xFFF97316.toInt(), advice: Advice? = null, playing: String? = null) = HorizonTheme(seed) {
    val card: (@Composable () -> Unit)? = if (state is LinkState.Connected && "media" in state.granted) {
        { CarteLecture(null, playing, "SPLATA".takeIf { playing != null }, playing = true, onPlayPause = {}, onNext = {}, onOpen = {}) }
    } else {
        null
    }
    HomeScreen(state, lastSentMs = 0, nowMs = 125_000, advice = advice, onPairAgain = {}, onAdvice = {}, onStatus = {}, media = card)
}

@Preview(name = "Connecté, en lecture", widthDp = 360, heightDp = 560)
@Composable
private fun HomePlaying() = HomePreview(LinkState.Connected("Bureau", 0, setOf("battery", "media"), setOf("battery", "media")), playing = "FROGZ")

@Preview(name = "Connecté, rien en lecture, cyan", widthDp = 360, heightDp = 560)
@Composable
private fun HomeNothing() = HomePreview(LinkState.Connected("Bureau", 0, setOf("battery", "media"), setOf("battery", "media")), seed = 0xFF22B8CF.toInt())

@Preview(name = "Connecté, orange", widthDp = 360, heightDp = 560)
@Composable
private fun HomeConnected() = HomePreview(LinkState.Connected("Bureau", 0, setOf("battery"), setOf("battery")))

@Preview(name = "Connecté, violet, conseil", widthDp = 360, heightDp = 560)
@Composable
private fun HomeViolet() = HomePreview(LinkState.Connected("Bureau", 0, setOf("battery"), setOf("battery")), seed = 0xFFA06CD5.toInt(), advice = Advice.AllowBackground)

@Preview(name = "Reconnexion, cyan", widthDp = 360, heightDp = 560)
@Composable
private fun HomeCyan() = HomePreview(LinkState.Connecting("Bureau"), seed = 0xFF22B8CF.toInt())

@Preview(name = "Hors ligne, vert", widthDp = 360, heightDp = 560)
@Composable
private fun HomeGreen() = HomePreview(LinkState.Offline("Bureau", OfflineReason.Unreachable, -600_000), seed = 0xFF4CC26A.toInt())

@Preview(name = "PC qui ne reconnaît plus le téléphone", widthDp = 360, heightDp = 560)
@Composable
private fun HomeRejected() = HomePreview(LinkState.Rejected("Bureau"))

@Preview(name = "Police à 200 %", widthDp = 360, heightDp = 760, fontScale = 2f)
@Composable
private fun HomeLarge() = HomePreview(LinkState.Offline("Bureau", OfflineReason.Unreachable, -600_000), advice = Advice.WasKilled)
