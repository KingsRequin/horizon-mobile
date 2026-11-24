package fr.kingsrequin.horizon.settings

import fr.kingsrequin.horizon.connection.LinkMode
import fr.kingsrequin.horizon.link.LinkState

/** Ce que le rapport dit du téléphone. `gms` : les services Google sont présents. */
data class DeviceInfo(val manufacturer: String, val model: String, val api: Int, val gms: Boolean)

private val ipv4 = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b""")
private val localName = Regex("""\S+\.local\b""")

/**
 * Le rapport de diagnostic, à coller dans un signalement : ce qui aide à comprendre une liaison
 * qui ne tient pas, et rien qui identifie la personne. Le journal de la liaison n'en reçoit pas
 * (ni nom du PC, ni adresse, ni jeton : voir `LinkLog`) ; par précaution, ce qui ressemblerait
 * quand même à une adresse ou à un nom en `.local` est remplacé ici.
 */
fun diagnostic(info: DeviceInfo, appVersion: String, mode: LinkMode, state: LinkState, exitReason: String?, log: List<String>): String {
    val addresses = LinkedHashMap<String, String>()
    fun anonymous(line: String): String =
        ipv4.replace(localName.replace(line, "nom du PC.local")) { match -> addresses.getOrPut(match.value) { "adresse ${addresses.size + 1}" } }
    val link = when (state) {
        is LinkState.Connected -> "Connected (${state.features.sorted().joinToString(", ")})"
        is LinkState.Offline -> "Offline (${state.reason})"
        is LinkState.Connecting -> "Connecting"
        is LinkState.Rejected -> "Rejected"
        LinkState.NotPaired -> "NotPaired"
        LinkState.Starting -> "Starting"
    }
    return buildString {
        appendLine("Horizon Mobile $appVersion")
        appendLine("${info.manufacturer} ${info.model}, Android API ${info.api}, services Google : ${if (info.gms) "oui" else "non"}")
        appendLine("mode : $mode")
        appendLine("liaison : $link")
        appendLine("dernier arrêt : ${exitReason ?: "inconnu"}")
        appendLine("journal (UTC) :")
        if (log.isEmpty()) appendLine("  (vide)")
        log.forEach { appendLine("  ${anonymous(it)}") }
    }.trimEnd()
}

/** Ce que l'accueil conseille pour que la liaison tienne en arrière-plan. */
enum class Advice {
    /** Proposer l'exemption d'optimisation de batterie. */
    AllowBackground,

    /** Le téléphone a arrêté l'appli : ouvrir ses réglages. */
    WasKilled,
}

/** Raisons d'arrêt ([fr.kingsrequin.horizon.connection.lastExitReason]) qui disent que le système a tué l'appli. */
private val killed = listOf("signaled", "low_memory", "freezer", "excessive_resource_usage")

/**
 * Le conseil à montrer, s'il y en a un. Rien tant que la liaison n'a jamais marché (ce n'est pas
 * le moment), ni en mode « à l'ouverture » (rien ne tourne en arrière-plan).
 */
fun backgroundAdvice(mode: LinkMode, connectedOnce: Boolean, ignoringOptimizations: Boolean, exitReason: String?): Advice? = when {
    mode != LinkMode.Continuous || !connectedOnce -> null
    exitReason != null && killed.any { exitReason.startsWith(it) } -> Advice.WasKilled
    !ignoringOptimizations -> Advice.AllowBackground
    else -> null
}
