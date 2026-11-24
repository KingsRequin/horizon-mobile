package fr.kingsrequin.horizon.link

import fr.kingsrequin.horizon.protocol.DeviceBattery
import fr.kingsrequin.horizon.protocol.ToPc

/**
 * Décide quand la batterie du téléphone part vers le PC : seulement quand le pourcentage ou la
 * charge a changé depuis le dernier message rendu. Aucun envoi périodique.
 */
class BatteryReporter(private val deviceName: () -> String) {
    private var last: Pair<Int, Boolean>? = null

    /** Heure du dernier message rendu par [reading]. */
    @get:Synchronized
    var lastSentMs: Long? = null
        private set

    /** Le message à envoyer pour cette mesure, ou `null` si le PC la connaît déjà. */
    @Synchronized
    fun reading(percent: Int, charging: Boolean, nowMs: Long): ToPc.PhoneBattery? {
        val value = percent.coerceIn(0, 100) to charging
        if (value == last) return null
        last = value
        lastSentMs = nowMs
        return ToPc.PhoneBattery(listOf(DeviceBattery(PHONE, PHONE, deviceName(), value.first, charging, nowMs)))
    }

    /** Nouvelle session, ou envoi raté : le PC ne sait rien, la mesure suivante part. */
    @Synchronized
    fun reset() {
        last = null
    }

    private companion object {
        /** Identifiant et genre de la batterie du téléphone lui-même. */
        const val PHONE = "phone"
    }
}
