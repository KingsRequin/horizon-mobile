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
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import fr.kingsrequin.horizon.link.LinkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Garde l'appli en vie pour que la liaison tienne, appli fermée et écran éteint, et écoute la
 * batterie du téléphone. Il ne fait rien de lui-même : la liaison ([Link]) réagit au réseau et à
 * la batterie, jamais à une minuterie. Type `connectedDevice` : il relie le téléphone à un
 * appareil du réseau local.
 */
class LinkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Le système annonce chaque changement de la batterie ; seuls ceux du pourcentage ou de la charge partent. */
    private val battery = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = Link.sendBattery(Link.batteryOf(intent))
    }

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
        ServiceCompat.startForeground(this, NOTIFICATION, notification(Link.manager.state.value), type)
        registerReceiver(battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        scope.launch { Link.manager.state.collect { getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(it)) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // « Arrêter » : jusqu'à la prochaine ouverture de l'appli.
            stopSelf()
            return START_NOT_STICKY
        }
        // En continu, Android relance le service s'il a dû le tuer.
        return if (Link.mode == LinkMode.Continuous) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(battery)
        scope.cancel()
        super.onDestroy()
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
    }
}
