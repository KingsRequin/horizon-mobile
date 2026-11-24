package fr.kingsrequin.horizon.link

/**
 * Les derniers événements de la liaison, pour le rapport de diagnostic. En mémoire seulement.
 * Ne reçoit jamais de jeton ni d'empreinte.
 */
class LinkLog(private val capacity: Int = 20) {
    private val entries = ArrayDeque<String>()

    /** Ajoute `event`, daté de `nowMs` (heure UTC du jour). */
    @Synchronized
    fun add(nowMs: Long, event: String) {
        val seconds = nowMs / 1000 % 86_400
        entries.addLast("%02d:%02d:%02d %s".format(seconds / 3600, seconds / 60 % 60, seconds % 60, event))
        while (entries.size > capacity) entries.removeFirst()
    }

    /** Les lignes gardées, de la plus ancienne à la plus récente. */
    @Synchronized
    fun lines(): List<String> = entries.toList()
}
