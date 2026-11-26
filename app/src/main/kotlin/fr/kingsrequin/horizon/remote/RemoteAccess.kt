package fr.kingsrequin.horizon.remote

/** Ce que l'appli peut montrer de la télécommande pour ce PC et ce téléphone. */
enum class Access {
    /** Le PC ne sait pas la faire (Horizon 0.8) : pas d'onglet. */
    Hidden,

    /** Le PC la sait faire mais ce téléphone n'en a pas le droit : l'onglet dit où l'accorder. */
    ToGrant,

    Granted,
}

/** L'accès à la télécommande d'après ce que sait le PC (`features`) et ce qu'il permet (`granted`). */
fun remoteAccess(features: Set<String>, granted: Set<String>): Access = when {
    MEDIA !in features -> Access.Hidden
    MEDIA !in granted -> Access.ToGrant
    else -> Access.Granted
}

private const val MEDIA = "media"
