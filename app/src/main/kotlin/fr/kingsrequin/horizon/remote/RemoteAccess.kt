package fr.kingsrequin.horizon.remote

/** Ce que l'appli peut montrer d'une fonction du PC pour ce téléphone. */
enum class Access {
    /** Le PC ne sait pas la faire (Horizon trop ancien) : rien à l'écran. */
    Hidden,

    /** Le PC la sait faire mais ce téléphone n'en a pas le droit : l'écran dit où l'accorder. */
    ToGrant,

    Granted,
}

/** L'accès à la télécommande d'après ce que sait le PC (`features`) et ce qu'il permet (`granted`). */
fun remoteAccess(features: Set<String>, granted: Set<String>): Access = access(MEDIA in features, MEDIA in granted)

/** L'accès au mixeur par appli, à la sortie audio et aux actions rapides (droit « Son et actions »). */
fun soundAccess(features: Set<String>, granted: Set<String>): Access = access(MIXER in features || ACTIONS in features, SOUND in granted)

/** L'accès au clavier et à la souris du PC (droit « Clavier et souris », jamais accordé d'office). */
fun inputAccess(features: Set<String>, granted: Set<String>): Access = access(INPUT in features, INPUT in granted)

/** La sortie qui suit `current` dans `outputs` (en boucle) ; `null` s'il n'y en a pas d'autre. */
fun nextOutput(outputs: List<String>, current: String?): String? {
    if (outputs.size < 2) return null
    val at = outputs.indexOf(current)
    return outputs[(at + 1) % outputs.size]
}

private fun access(known: Boolean, allowed: Boolean): Access = when {
    !known -> Access.Hidden
    !allowed -> Access.ToGrant
    else -> Access.Granted
}

private const val MEDIA = "media"
private const val MIXER = "mixer"
private const val ACTIONS = "actions"
private const val SOUND = "sound"
private const val INPUT = "input"
