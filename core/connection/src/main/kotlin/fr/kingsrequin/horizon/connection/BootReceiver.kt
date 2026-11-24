package fr.kingsrequin.horizon.connection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Au démarrage du téléphone, ou après une mise à jour de l'appli : la liaison reprend sans que
 * l'appli soit ouverte, si un PC est appairé et que le mode est « en continu » ([Link.apply]).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Link.init(context)
        // Le service se lance pendant que l'annonce est encore en cours de traitement : c'est ce
        // qui autorise son démarrage depuis l'arrière-plan.
        val pending = goAsync()
        Link.whenReady {
            try {
                Link.apply()
            } finally {
                pending.finish()
            }
        }
    }
}
