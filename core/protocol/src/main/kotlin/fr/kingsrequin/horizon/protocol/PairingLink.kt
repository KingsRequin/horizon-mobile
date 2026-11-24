package fr.kingsrequin.horizon.protocol

import java.net.URLDecoder

/** Ce que porte le QR code d'appairage affiché par Horizon. */
data class PairingLink(
    /** Adresses IPv4 du PC, à essayer dans l'ordre. */
    val hosts: List<String>,
    /** Nom du PC ; sert aussi de nom d'hôte (`<nom>.local`). */
    val name: String,
    val port: Int,
    /** Empreinte de la clé publique du PC (SHA-256 du SPKI, base64url). */
    val fingerprint: String,
    /** Jeton d'appairage, à usage unique. */
    val token: String,
)

/** Résultat de la lecture d'un QR code. */
sealed interface QrResult {
    data class Ok(val link: PairingLink) : QrResult

    /** Un QR code qui n'a rien à voir avec Horizon (lien web, Wi-Fi, texte). */
    data object NotHorizon : QrResult

    /** Un lien d'Horizon d'une version que cette appli ne connaît pas : il faut la mettre à jour. */
    data class Unsupported(val version: String) : QrResult

    /** Un lien d'Horizon incomplet ou abîmé. */
    data object Malformed : QrResult
}

private const val PREFIX = "horizon://"
private const val LINK_VERSION = "1"

/** Lit `horizon://pair?v=1&h=<ipv4,…>&n=<nom>&p=<port>&fp=<empreinte>&t=<jeton>`. */
fun parsePairingLink(text: String): QrResult {
    val link = text.trim()
    if (!link.startsWith(PREFIX, ignoreCase = true)) return QrResult.NotHorizon
    val rest = link.substring(PREFIX.length)
    if (rest.substringBefore('?') != "pair" || '?' !in rest) return QrResult.Malformed
    val params = HashMap<String, String>()
    for (pair in rest.substringAfter('?').split('&')) {
        if (pair.isEmpty()) continue
        params[pair.substringBefore('=')] = decode(pair.substringAfter('=', "")) ?: return QrResult.Malformed
    }
    val version = params["v"] ?: return QrResult.Malformed
    if (version != LINK_VERSION) return QrResult.Unsupported(version)
    val hosts = params["h"].orEmpty().split(',').filter { it.isNotEmpty() }
    val port = params["p"]?.toIntOrNull()
    val name = params["n"]
    val fingerprint = params["fp"].orEmpty()
    val token = params["t"].orEmpty()
    if (hosts.isEmpty() || hosts.size > MAX_HOSTS || !hosts.all(::isLocalIpv4) || port == null || port !in 1..65535) return QrResult.Malformed
    if (name == null || !isPlainName(name) || fingerprint.isEmpty() || token.isEmpty()) return QrResult.Malformed
    return QrResult.Ok(PairingLink(hosts, name, port, fingerprint, token))
}

/** Valeur encodée en pourcent ; `null` si elle est abîmée. Un « + » reste un plus. */
private fun decode(value: String): String? = try {
    // Le nom du jeu de caractères, pas l'objet : cette forme-là n'existe qu'à partir d'Android 13.
    URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
} catch (_: IllegalArgumentException) {
    null
}

/** Adresses qu'un lien peut porter, et longueur du nom du PC : au-delà, ce n'est pas un lien d'Horizon. */
private const val MAX_HOSTS = 8
private const val MAX_NAME = 64

/**
 * Une adresse IPv4 d'un réseau local, écrite en chiffres : jamais un nom à résoudre, jamais une
 * adresse d'Internet ni le téléphone lui-même. Ce sont les seules qu'Horizon annonce (10/8,
 * 172.16/12, 192.168/16, 169.254/16) ; un lien qui en porte une autre est refusé, d'où qu'il vienne.
 */
private fun isLocalIpv4(host: String): Boolean {
    val parts = host.split('.')
    if (parts.size != 4 || !parts.all { part -> part.length in 1..3 && part.all(Char::isDigit) && part.toInt() <= 255 }) return false
    val (a, b) = parts[0].toInt() to parts[1].toInt()
    return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
}

/** Un nom de PC montrable tel quel : court, sans caractère de contrôle ni de mise en forme invisible. */
private fun isPlainName(name: String): Boolean =
    name.isNotEmpty() && name.length <= MAX_NAME && name.none { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }

/** `#rrggbb` → couleur ARGB opaque ; `null` si ce n'en est pas une. */
fun parseColor(hex: String): Int? {
    if (hex.length != 7 || hex[0] != '#') return null
    val rgb = hex.substring(1).toIntOrNull(16) ?: return null
    return if (hex.substring(1).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) 0xFF000000.toInt() or rgb else null
}
