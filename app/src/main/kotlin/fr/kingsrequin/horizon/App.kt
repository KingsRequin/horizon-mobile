package fr.kingsrequin.horizon

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.kingsrequin.horizon.connection.Link
import fr.kingsrequin.horizon.connection.LinkMode
import fr.kingsrequin.horizon.connection.lastExitReason
import fr.kingsrequin.horizon.design.DEFAULT_SEED
import fr.kingsrequin.horizon.design.HorizonIcons
import fr.kingsrequin.horizon.design.HorizonTheme
import fr.kingsrequin.horizon.design.LocalHorizonColors
import fr.kingsrequin.horizon.design.components.BarreNavigation
import fr.kingsrequin.horizon.design.components.Onglet
import fr.kingsrequin.horizon.home.HomeScreen
import fr.kingsrequin.horizon.link.LinkState
import fr.kingsrequin.horizon.pairing.PairingFlow
import fr.kingsrequin.horizon.pairing.PairingStep
import fr.kingsrequin.horizon.pairing.PairingViewModel
import fr.kingsrequin.horizon.settings.Advice
import fr.kingsrequin.horizon.settings.DeviceInfo
import fr.kingsrequin.horizon.settings.SettingsActions
import fr.kingsrequin.horizon.settings.SettingsScreen
import fr.kingsrequin.horizon.settings.Update
import fr.kingsrequin.horizon.settings.backgroundAdvice
import fr.kingsrequin.horizon.settings.checkForUpdate
import fr.kingsrequin.horizon.settings.diagnostic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val REPOSITORY = "https://github.com/KingsRequin/horizon-mobile"
private const val PREFS = "appli"
private const val KEY_CONNECTED_ONCE = "deja-connecte"

/**
 * Ce que l'appli montre : rien tant que l'appairage n'est pas relu, le parcours d'appairage sans
 * PC (ou pendant qu'il est en cours, ou redemandé), sinon l'accueil et les réglages. La couleur
 * est celle que le PC a envoyée en dernier.
 */
@Composable
fun App(pairingLink: MutableStateFlow<String?>) {
    val state by Link.manager.state.collectAsStateWithLifecycle()
    val pairing: PairingViewModel = viewModel(factory = viewModelFactory { initializer { PairingViewModel(Link.manager::pair, { Link.mode = it }) } })
    val step by pairing.step.collectAsStateWithLifecycle()
    val link by pairingLink.collectAsStateWithLifecycle()
    // Hors ligne, l'appli garde la dernière couleur reçue.
    var seed by rememberSaveable { mutableIntStateOf(DEFAULT_SEED) }
    (state as? LinkState.Connected)?.let { seed = it.seed }
    var pairAgain by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(link) {
        link?.let {
            pairing.onLink(it)
            pairingLink.value = null
        }
    }

    // Un nouvel appairage demandé depuis l'accueil s'abandonne par le retour.
    BackHandler(enabled = pairAgain && step == PairingStep.Intro) { pairAgain = false }

    HorizonTheme(seed) {
        val inPairing = state is LinkState.NotPaired || pairAgain || (step != PairingStep.Intro && step != PairingStep.Done)
        when {
            state is LinkState.Starting -> Box(Modifier.fillMaxSize().background(LocalHorizonColors.current.surface))
            inPairing -> PairingFlow(pairing) {
                pairAgain = false
                pairing.start()
            }
            else -> Paired(state, onPairAgain = { pairAgain = true })
        }
    }
}

/**
 * L'appli une fois un PC appairé : accueil et réglages, sous la barre de navigation.
 *
 * `BatteryLife` : lint rappelle que le Play Store réserve la demande d'exemption d'optimisation
 * de batterie à quelques usages. L'appli n'y est pas distribuée, et la demande n'est faite que
 * sur un appui, pour tenir la liaison avec le PC écran éteint.
 */
@SuppressLint("BatteryLife")
@Composable
private fun Paired(state: LinkState, onPairAgain: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(Link.mode) }
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    val exitReason = remember { lastExitReason(context) }
    // L'heure avance seule tant que l'appli est à l'écran : « il y a 2 min » se met à jour.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val update by produceState<Update?>(null) { value = checkForUpdate(context, version, System.currentTimeMillis()) }
    if (state is LinkState.Connected && !prefs.getBoolean(KEY_CONNECTED_ONCE, false)) prefs.edit { putBoolean(KEY_CONNECTED_ONCE, true) }
    // Relu à chaque retour à l'appli (la demande d'exemption et les réglages du système sont
    // d'autres écrans : la carte doit tomber dès qu'on en revient), et quand l'heure passe.
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed++ }
    val advice = remember(now, mode, state, resumed) {
        val exempt = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        backgroundAdvice(mode, prefs.getBoolean(KEY_CONNECTED_ONCE, false), exempt, exitReason)
    }
    val pcName = when (state) {
        is LinkState.Connected -> state.pcName
        is LinkState.Connecting -> state.pcName
        is LinkState.Offline -> state.pcName
        is LinkState.Rejected -> state.pcName
        LinkState.NotPaired, LinkState.Starting -> ""
    }
    val copied = stringResource(R.string.settings_report_copied)
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    Column(Modifier.fillMaxSize().background(LocalHorizonColors.current.surface)) {
        val content = Modifier.weight(1f)
        if (tab == 0) {
            HomeScreen(
                state = state,
                lastSentMs = Link.reporter.lastSentMs,
                nowMs = now,
                advice = advice,
                onPairAgain = onPairAgain,
                onAdvice = {
                    // L'exemption se demande par la boîte du système ; sinon, la fiche de l'appli.
                    val exemption = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", context.packageName, null))
                    if (it != Advice.AllowBackground || !open(context, exemption)) open(context, appDetails)
                },
                onStatus = { tab = 1 },
                modifier = content,
            )
        } else {
            SettingsScreen(
                mode = mode,
                pcName = pcName,
                version = version,
                update = update,
                actions = SettingsActions(
                    setMode = {
                        Link.mode = it
                        mode = it
                    },
                    forget = { scope.launch { Link.manager.forget() } },
                    language = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        { open(context, Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))) }
                    } else {
                        null
                    },
                    copyReport = {
                        val report = diagnostic(deviceInfo(context), version, mode, state, exitReason, Link.log.lines())
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Horizon Mobile", report))
                        // À partir d'Android 13, le système confirme lui-même la copie.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    },
                    licences = { open(context, Intent(Intent.ACTION_VIEW, "$REPOSITORY/blob/main/LICENCES-TIERCES.md".toUri())) },
                    source = { open(context, Intent(Intent.ACTION_VIEW, REPOSITORY.toUri())) },
                    download = { open(context, Intent(Intent.ACTION_VIEW, it.url.toUri())) },
                ),
                modifier = content,
            )
        }
        BarreNavigation(
            tabs = listOf(Onglet(HorizonIcons.House, stringResource(R.string.tab_home)), Onglet(HorizonIcons.GearSix, stringResource(R.string.tab_settings))),
            current = tab,
            onSelect = { tab = it },
        )
    }
}

/** Ouvre un écran d'une autre appli ou du système ; faux s'il n'existe pas sur ce téléphone. */
private fun open(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}

private fun deviceInfo(context: Context): DeviceInfo {
    val gms = try {
        context.packageManager.getPackageInfo("com.google.android.gms", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
    return DeviceInfo(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty(), Build.VERSION.SDK_INT, gms)
}
