package fr.kingsrequin.horizon.connection

/** Quand la liaison avec le PC est tenue. */
enum class LinkMode {
    /** En continu : un service garde la liaison, appli fermée et écran éteint. */
    Continuous,

    /** Seulement quand l'appli est à l'écran. */
    WhenOpen,
}

/** Le service de liaison doit-il tourner ? Jamais sans PC appairé. */
fun shouldRun(mode: LinkMode, paired: Boolean, appVisible: Boolean): Boolean = paired && (mode == LinkMode.Continuous || appVisible)

/** Le mode enregistré ; « en continu » tant que rien n'a été choisi, ou si la valeur est inconnue. */
fun linkModeOf(stored: String?): LinkMode = LinkMode.entries.firstOrNull { it.name == stored } ?: LinkMode.Continuous

/**
 * Pourcentage et charge tirés d'une annonce `ACTION_BATTERY_CHANGED` ; `null` si la mesure
 * manque. « En charge » : le téléphone est branché (secteur, USB, sans fil, station).
 */
fun batteryOf(level: Int, scale: Int, plugged: Int): Pair<Int, Boolean>? {
    if (level < 0 || scale <= 0) return null
    return (level * 100 / scale).coerceIn(0, 100) to (plugged != 0)
}
