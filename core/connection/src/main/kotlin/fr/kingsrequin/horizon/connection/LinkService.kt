package fr.kingsrequin.horizon.connection

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaStyleNotificationHelper
import fr.kingsrequin.horizon.design.R as DesignR
import fr.kingsrequin.horizon.link.LinkState
import fr.kingsrequin.horizon.link.MediaNow
import fr.kingsrequin.horizon.link.RemoteView
import fr.kingsrequin.horizon.protocol.ToPc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Garde l'appli en vie pour que la liaison tienne, appli fermée et écran éteint, et écoute la
 * batterie du téléphone. Il ne fait rien de lui-même : la liaison ([Link]) réagit au réseau et à
 * la batterie, jamais à une minuterie. Type `connectedDevice` : il relie le téléphone à un
 * appareil du réseau local. Pendant que le PC joue, sa notification devient celle d'un lecteur
 * (précédent, lecture, suivant), branchée sur une [PcMediaSession] : les commandes du PC passent
 * dans le volet, sur l'écran verrouillé et sur un casque.
 */
@OptIn(UnstableApi::class)
class LinkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Le système annonce chaque changement de la batterie ; seuls ceux du pourcentage ou de la charge partent. */
    private val battery = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = Link.sendBattery(Link.batteryOf(intent))
    }

    private lateinit var session: PcMediaSession

    /** Dernière pochette décodée, gardée avec les octets dont elle vient. */
    private var cover: Pair<ByteArray, Bitmap?>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Link.init(this)
        // Importance basse : ni son ni vibration, la notification reste repliée. (Les canaux
        // n'existent qu'à partir d'Android 8 : avant, cet appel ne fait rien.)
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW).setName(getString(R.string.link_channel)).setShowBadge(false).build(),
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        session = PcMediaSession(this, Link::command)
        ServiceCompat.startForeground(this, NOTIFICATION, notification(Link.manager.state.value), type)
        registerReceiver(battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        scope.launch {
            combine(Link.manager.state, Link.remote.view, Link.systemControls) { state, view, on -> Triple(state, view, on) }.collect { (state, view, on) ->
                val granted = (state as? LinkState.Connected)?.granted.orEmpty()
                val media = view.media.takeIf { on }
                val art = coverOf(view)
                session.update(media, sessionCommands(media, granted), view.art)
                val shown = if (session.active && media != null) mediaNotification(media, art) else notification(state)
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION, shown)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // « Arrêter » : la liaison se ferme et le service s'arrête, jusqu'à la prochaine ouverture de l'appli.
                Link.stop()
                return START_NOT_STICKY
            }
            ACTION_PLAY_PAUSE -> Link.command(ToPc.MediaCommand("play_pause"))
            ACTION_NEXT -> Link.command(ToPc.MediaCommand("next"))
            ACTION_PREVIOUS -> Link.command(ToPc.MediaCommand("previous"))
        }
        // En continu, Android relance le service s'il a dû le tuer.
        return if (Link.mode == LinkMode.Continuous) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(battery)
        scope.cancel()
        session.release()
        super.onDestroy()
    }

    /** La pochette de la lecture en cours, décodée une fois par image. */
    private fun coverOf(view: RemoteView): Bitmap? {
        val bytes = view.art ?: return null
        cover?.let { (from, bitmap) -> if (from === bytes) return bitmap }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size).also { cover = bytes to it }
    }

    /** La notification d'un lecteur : titre, artiste, pochette, précédent, lecture, suivant, et « Arrêter ». */
    private fun mediaNotification(media: MediaNow, art: Bitmap?): Notification {
        val immutable = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        fun command(code: Int, action: String) = PendingIntent.getService(this, code, Intent(this, LinkService::class.java).setAction(action), immutable)
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let { PendingIntent.getActivity(this, 0, it, immutable) }
        val playing = media.playing
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_horizon)
            .setContentTitle(media.title.ifBlank { media.appName })
            .setContentText(media.artist)
            .setSubText(media.appName)
            .setLargeIcon(art)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(DesignR.drawable.ic_skip_back_fill, getString(DesignR.string.media_previous), command(2, ACTION_PREVIOUS))
            .addAction(
                if (playing) DesignR.drawable.ic_pause_fill else DesignR.drawable.ic_play_fill,
                getString(if (playing) DesignR.string.media_pause else DesignR.string.media_play),
                command(3, ACTION_PLAY_PAUSE),
            )
            .addAction(DesignR.drawable.ic_skip_forward_fill, getString(DesignR.string.media_next), command(4, ACTION_NEXT))
            .addAction(0, getString(R.string.link_stop), command(1, ACTION_STOP))
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session.session).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun notification(state: LinkState): Notification {
        val text = when (state) {
            is LinkState.Connected -> getString(R.string.link_connected, state.pcName)
            is LinkState.Connecting -> getString(R.string.link_connecting, state.pcName)
            is LinkState.Offline -> getString(R.string.link_offline, state.pcName)
            is LinkState.Rejected -> getString(R.string.link_offline, state.pcName)
            LinkState.NotPaired, LinkState.Starting -> getString(R.string.link_channel)
        }
        val immutable = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let { PendingIntent.getActivity(this, 0, it, immutable) }
        val stop = PendingIntent.getService(this, 1, Intent(this, LinkService::class.java).setAction(ACTION_STOP), immutable)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_horizon)
            .setContentTitle(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.link_stop), stop)
            .build()
    }

    private companion object {
        const val CHANNEL = "liaison"
        const val NOTIFICATION = 1
        const val ACTION_STOP = "fr.kingsrequin.horizon.connection.STOP"
        const val ACTION_PLAY_PAUSE = "fr.kingsrequin.horizon.connection.PLAY_PAUSE"
        const val ACTION_NEXT = "fr.kingsrequin.horizon.connection.NEXT"
        const val ACTION_PREVIOUS = "fr.kingsrequin.horizon.connection.PREVIOUS"
    }
}
