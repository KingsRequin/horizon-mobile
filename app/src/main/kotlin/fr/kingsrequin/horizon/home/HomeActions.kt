package fr.kingsrequin.horizon.home

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.kingsrequin.horizon.R
import fr.kingsrequin.horizon.connection.Link
import fr.kingsrequin.horizon.design.HorizonIcons
import fr.kingsrequin.horizon.design.HorizonSpace
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.HorizonType
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.components.ActionRapide
import fr.kingsrequin.horizon.design.components.Groupe
import fr.kingsrequin.horizon.design.components.GroupeActions
import fr.kingsrequin.horizon.design.components.LigneNiveau
import fr.kingsrequin.horizon.design.components.LigneReglage
import fr.kingsrequin.horizon.design.formatCountdown
import fr.kingsrequin.horizon.link.ActionsNow
import fr.kingsrequin.horizon.link.TimerNow
import fr.kingsrequin.horizon.link.remainingAt
import fr.kingsrequin.horizon.protocol.HeadsetInfo
import fr.kingsrequin.horizon.protocol.ToPc
import fr.kingsrequin.horizon.remote.Access
import kotlinx.coroutines.delay

/** Ce que les actions rapides demandent au PC. */
class HomeCommands(
    val lock: () -> Unit,
    val dnd: (Boolean) -> Unit,
    val timerStart: (Int) -> Unit,
    val timerPause: (Boolean) -> Unit,
    val timerStop: () -> Unit,
)

/**
 * Les actions rapides de l'accueil, branchées sur la liaison : regarde les actions du PC tant que
 * l'accueil est à l'écran, fait défiler le minuteur, dit « PC verrouillé » après l'appui.
 */
@Composable
fun HomeActionsRoute(access: Access) {
    if (access == Access.Hidden) return
    val context = LocalContext.current
    val locked = stringResource(R.string.actions_locked)
    LifecycleResumeEffect(Unit) {
        Link.watch("actions", true)
        onPauseOrDispose { Link.watch("actions", false) }
    }
    val view by Link.remote.view.collectAsStateWithLifecycle()
    val running = view.actions?.timer?.paused == false
    val now by produceState(System.currentTimeMillis(), running) {
        value = System.currentTimeMillis()
        while (running) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val commands = HomeCommands(
        lock = {
            Link.command(ToPc.ActionLock)
            Toast.makeText(context, locked, Toast.LENGTH_SHORT).show()
        },
        dnd = { Link.command(ToPc.ActionDnd(it)) },
        timerStart = { Link.command(ToPc.TimerStart(it)) },
        timerPause = { Link.command(ToPc.TimerPause(it)) },
        timerStop = { Link.command(ToPc.TimerStop) },
    )
    HomeActions(view.actions, access, now, commands)
}

/**
 * Verrouiller, ne pas déranger et minuteur, puis la batterie du casque du PC s'il en a un.
 * `actions` nul : rien encore reçu du PC (rien n'est montré). À accorder : où l'accorder.
 */
@Composable
fun HomeActions(actions: ActionsNow?, access: Access, nowMs: Long, commands: HomeCommands) {
    val colors = LocalHorizonColors.current
    if (access == Access.ToGrant) {
        Text(stringResource(R.string.actions_to_grant), style = HorizonType.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = HorizonSpace.s2))
        return
    }
    if (actions == null) return
    var timerSheet by rememberSaveable { mutableStateOf(false) }
    val timer = actions.timer
    Column(verticalArrangement = Arrangement.spacedBy(HorizonSpace.s4)) {
        GroupeActions(
            listOf(
                ActionRapide(HorizonIcons.Lock, stringResource(R.string.actions_lock), null, commands.lock),
                ActionRapide(HorizonIcons.Moon, stringResource(R.string.actions_dnd), actions.dnd) { commands.dnd(!actions.dnd) },
                ActionRapide(HorizonIcons.Timer, timer?.let { formatCountdown(it.remainingAt(nowMs)) } ?: stringResource(R.string.actions_timer), timer != null) { timerSheet = true },
            ),
        )
        actions.headset?.let { headset ->
            Groupe { LigneNiveau(HorizonIcons.Headset, stringResource(R.string.home_pc_headset), headset.level, headset.charging) }
        }
    }
    if (timerSheet) {
        TimerSheet(timer, actions.presets, commands) { timerSheet = false }
    }
}

/** Le minuteur en feuille du bas : en cours, pause ou reprise et arrêt ; sinon les durées d'Horizon. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerSheet(timer: TimerNow?, presets: List<Int>, commands: HomeCommands, onDismiss: () -> Unit) {
    val colors = LocalHorizonColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surfaceContainerHigh) {
        Column(Modifier.padding(HorizonSpace.s4).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(HorizonSpace.s2)) {
            Text(stringResource(R.string.actions_timer), style = HorizonType.titleMedium, color = colors.onSurface, modifier = Modifier.padding(start = HorizonSpace.s2))
            Groupe {
                if (timer != null) {
                    LigneReglage(stringResource(if (timer.paused) R.string.timer_resume else R.string.timer_pause)) {
                        commands.timerPause(!timer.paused)
                        onDismiss()
                    }
                    LigneReglage(stringResource(R.string.timer_stop)) {
                        commands.timerStop()
                        onDismiss()
                    }
                } else {
                    presets.forEach { minutes ->
                        LigneReglage(stringResource(R.string.timer_minutes, minutes)) {
                            commands.timerStart(minutes)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

private val previewCommands = HomeCommands({}, {}, {}, {}, {})

@Preview(name = "Au repos, casque", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun HomeActionsIdle() = HorizonTheme {
    Column(Modifier.padding(HorizonSpace.s4)) {
        HomeActions(ActionsNow(false, null, listOf(5, 25), HeadsetInfo("Casque", 92, false)), Access.Granted, 0, previewCommands)
    }
}

@Preview(name = "NPD et minuteur en cours", backgroundColor = 0xFF130D0A, showBackground = true, widthDp = 360)
@Composable
private fun HomeActionsBusy() = HorizonTheme {
    Column(Modifier.padding(HorizonSpace.s4)) {
        HomeActions(ActionsNow(true, TimerNow(760_000, 1_500_000, false, 0), listOf(5, 25), null), Access.Granted, 0, previewCommands)
    }
}
