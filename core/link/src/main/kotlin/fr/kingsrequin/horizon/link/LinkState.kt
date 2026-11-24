package fr.kingsrequin.horizon.link

/** L'orange d'Horizon (ARGB) : la couleur tant que le PC n'en a pas envoyé de lisible. */
const val DEFAULT_ACCENT: Int = 0xFFF97316.toInt()

/** Pourquoi le téléphone n'est pas relié au PC. */
enum class OfflineReason {
    /** Pas de réseau local (Wi-Fi coupé, données mobiles seules). */
    NoNetwork,

    /** Le PC ne répond à aucune de ses adresses (éteint, autre réseau, Horizon fermé). */
    Unreachable,

    /** Une machine répond à l'adresse du PC, mais ce n'est pas lui. */
    WrongPc,

    /** Le PC et l'appli ne parlent pas la même version : l'un des deux est à mettre à jour. */
    Incompatible,

    /** La liaison est suspendue : arrêtée depuis la notification, ou appli fermée en mode « à l'ouverture ». */
    Paused,
}

/** Où en est la liaison avec le PC. */
sealed interface LinkState {
    /** L'appairage n'a pas encore été relu : ne rien afficher. */
    data object Starting : LinkState

    data object NotPaired : LinkState

    /** Une série d'essais est en cours : « Reconnexion… ». */
    data class Connecting(val pcName: String) : LinkState

    /** `seed` : couleur d'accent de la barre du PC (ARGB) ; `features` : ce que ce PC sait recevoir. */
    data class Connected(val pcName: String, val seed: Int, val features: Set<String>) : LinkState

    /** Plus d'essai en cours ; le prochain viendra d'un événement (réseau, appli ouverte, batterie à envoyer). */
    data class Offline(val pcName: String, val reason: OfflineReason, val sinceMs: Long) : LinkState

    /** Le PC ne reconnaît plus ce téléphone (retiré côté Horizon) : seul un nouvel appairage relance. */
    data class Rejected(val pcName: String) : LinkState
}

/** Résultat d'un appairage. */
sealed interface PairResult {
    data class Paired(val pcName: String) : PairResult

    /** Le PC a répondu non : `expired`, `used`, `unknown`. */
    data class Refused(val reason: String) : PairResult

    data object Unreachable : PairResult

    data object WrongPc : PairResult

    /** Le téléphone n'a pas pu enregistrer l'appairage (coffre de clés indisponible). */
    data object Error : PairResult
}

/** Attentes entre deux essais d'une série, en secondes. Après la dernière, on attend un événement. */
val BACKOFF_S = listOf(2, 5, 15, 30, 60)
