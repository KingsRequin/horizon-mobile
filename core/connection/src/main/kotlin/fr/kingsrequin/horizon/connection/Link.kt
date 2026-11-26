package fr.kingsrequin.horizon.connection

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import fr.kingsrequin.horizon.link.BatteryReporter
import fr.kingsrequin.horizon.link.ConnectionManager
import fr.kingsrequin.horizon.link.LinkLog
import fr.kingsrequin.horizon.link.LinkState
import fr.kingsrequin.horizon.link.OkHttpTransport
import fr.kingsrequin.horizon.link.Remote
import fr.kingsrequin.horizon.protocol.ToPc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * La liaison avec le PC, une pour tout le processus : écrans, service et récepteur de démarrage
 * passent par ici. [init] d'abord, de n'importe où ; le reste suit.
 */
object Link {
    private const val TAG = "Liaison"
    private const val PREFS = "liaison"
    private const val KEY_MODE = "mode"
    private const val KEY_SYSTEM_CONTROLS = "commandes-systeme"

    /** Pas d'une touche de volume sur le volume du PC. */
    private const val VOLUME_KEY_STEP = 0.02f
    private const val MEDIA = "media"
    private const val VOLUME = "volume"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** L'application, pas un écran : elle vit autant que le processus. */
    private lateinit var app: Application
    private lateinit var lan: LanNetwork

    lateinit var manager: ConnectionManager
        private set
    lateinit var reporter: BatteryReporter
        private set

    /** Ce que le PC dit de sa lecture et de son volume ; remis à zéro à chaque session finie. */
    val remote = Remote(System::currentTimeMillis)

    /** Écrans qui regardent le volume du PC (la télécommande) : il n'est envoyé que pendant ce temps. */
    private var volumeWatchers = 0

    private val systemControlsState = MutableStateFlow(true)

    /** Commandes du PC dans le volet et sur l'écran verrouillé (réglage, activé par défaut). */
    val systemControls: StateFlow<Boolean> = systemControlsState.asStateFlow()

    /** Vrai pendant que l'écran Télécommande est au premier plan : les touches de volume règlent le PC. */
    @Volatile
    var volumeKeysOnPc = false

    /** Derniers événements de la liaison, pour le rapport de diagnostic. */
    val log = LinkLog()

    @Volatile
    private var appVisible = false

    /** « Arrêter » de la notification : jusqu'à la prochaine ouverture de l'appli. */
    @Volatile
    private var stoppedByUser = false

    /** Mode de liaison choisi ; le changer démarre ou arrête le service. */
    var mode: LinkMode
        get() = linkModeOf(app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, null))
        set(value) {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_MODE, value.name) }
            apply()
        }

    @Synchronized
    fun init(context: Context) {
        if (::manager.isInitialized) return
        app = context.applicationContext as Application
        systemControlsState.value = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SYSTEM_CONTROLS, true)
        reporter = BatteryReporter(::deviceName)
        // Les connexions sortent par le réseau local, même si les données mobiles sont le réseau par défaut.
        val transport = OkHttpTransport(bind = { fallback -> lan.socketFactory() ?: fallback })
        manager = ConnectionManager(KeystorePairingStore(app), transport, System::currentTimeMillis, scope, ::deviceName, appVersion(), log)
        // Session neuve : le PC ne sait rien, la batterie du moment part.
        manager.onConnected = {
            reporter.reset()
            sendBattery(currentBattery())
        }
        // L'état du média est suivi tant que la liaison est ouverte (carte de l'accueil, commandes
        // du système) : le PC n'envoie que ses changements, et une pochette par morceau.
        manager.onPcMessage = remote::apply
        manager.watch(setOf(MEDIA))
        scope.launch { manager.state.map { it is LinkState.Connected }.distinctUntilChanged().collect { if (!it) remote.reset() } }
        lan = LanNetwork(app, manager::onNetwork)
        // Le service suit l'appairage : il démarre avec lui et s'arrête quand le PC est oublié.
        scope.launch { manager.state.map(::paired).distinctUntilChanged().collect { apply() } }
    }

    /** Un écran commence (`true`) ou cesse de regarder le volume du PC. */
    @Synchronized
    fun watchVolume(on: Boolean) {
        volumeWatchers = (volumeWatchers + if (on) 1 else -1).coerceAtLeast(0)
        manager.watch(if (volumeWatchers > 0) setOf(MEDIA, VOLUME) else setOf(MEDIA))
    }

    /** Une commande de la télécommande ; sans session, elle provoque un essai et se perd. */
    fun command(msg: ToPc) {
        manager.send(msg)
    }

    /** Règle le volume du PC : affiché tout de suite, envoyé aussitôt. */
    fun setPcVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        remote.localVolume(v)
        command(ToPc.VolumeSet(v))
    }

    /**
     * Une touche de volume du téléphone (`up` : monter) ; vrai si elle a réglé le PC. Seulement
     * sur l'écran Télécommande et quand le volume du PC est connu : sinon le téléphone garde la
     * touche pour son propre volume.
     */
    fun volumeKey(up: Boolean): Boolean {
        if (!volumeKeysOnPc) return false
        val current = remote.view.value.volume ?: return false
        setPcVolume(current + if (up) VOLUME_KEY_STEP else -VOLUME_KEY_STEP)
        return true
    }

    /** Active ou coupe les commandes du PC dans le volet et sur l'écran verrouillé. */
    fun setSystemControls(on: Boolean) {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_SYSTEM_CONTROLS, on) }
        systemControlsState.value = on
    }

    /** L'appli vient à l'écran, ou le quitte. */
    fun setAppVisible(visible: Boolean) {
        appVisible = visible
        if (visible) stoppedByUser = false
        apply()
        if (visible) manager.onAppOpened()
    }

    /** Arrête la liaison et son service jusqu'à la prochaine ouverture de l'appli. */
    fun stop() {
        stoppedByUser = true
        apply()
    }

    /** `block` est appelé une fois l'appairage relu (au démarrage du téléphone, par exemple). */
    fun whenReady(block: () -> Unit) {
        scope.launch {
            manager.state.filter { it !is LinkState.Starting }.first()
            block()
        }
    }

    /** Démarre ou arrête le service selon le mode, l'appairage et la présence de l'appli à l'écran. */
    fun apply() {
        val state = manager.state.value
        if (state is LinkState.Starting) return
        val service = Intent(app, LinkService::class.java)
        val run = !stoppedByUser && shouldRun(mode, paired(state), appVisible)
        // Sans PC appairé il n'y a rien à suspendre, et l'appairage doit pouvoir se faire.
        manager.setActive(run || !paired(state))
        if (run) {
            try {
                ContextCompat.startForegroundService(app, service)
            } catch (e: IllegalStateException) {
                // Android refuse un service au premier plan lancé depuis l'arrière-plan hors des cas
                // prévus : la liaison reprendra à la prochaine ouverture de l'appli.
                Log.w(TAG, "service non démarré", e)
                log.add(System.currentTimeMillis(), "service non démarré : ${e.javaClass.simpleName}")
            }
        } else {
            app.stopService(service)
        }
    }

    /** La mesure du système (pourcentage, branché), ou `null` si elle manque. */
    internal fun batteryOf(intent: Intent?): Pair<Int, Boolean>? = intent?.let {
        batteryOf(it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), it.getIntExtra(BatteryManager.EXTRA_SCALE, -1), it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0))
    }

    /** Envoie la mesure au PC si elle a changé depuis le dernier envoi. */
    internal fun sendBattery(battery: Pair<Int, Boolean>?) {
        val (percent, charging) = battery ?: return
        reporter.reading(percent, charging, System.currentTimeMillis())?.let(manager::send)
    }

    /** La dernière annonce de batterie, que le système garde : la lire ne demande aucun récepteur. */
    private fun currentBattery(): Pair<Int, Boolean>? = batteryOf(app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))

    private fun paired(state: LinkState): Boolean = state !is LinkState.Starting && state !is LinkState.NotPaired

    /**
     * Le nom que l'utilisateur a donné à son téléphone, sinon son modèle. La clé est écrite en
     * toutes lettres : sa constante (`Settings.Global.DEVICE_NAME`) n'existe qu'à partir
     * d'Android 7.1, et avant la lecture rend simplement « rien ».
     */
    private fun deviceName(): String =
        Settings.Global.getString(app.contentResolver, "device_name")?.takeIf { it.isNotBlank() } ?: Build.MODEL.orEmpty().ifBlank { "Android" }

    private fun appVersion(): String = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
}
