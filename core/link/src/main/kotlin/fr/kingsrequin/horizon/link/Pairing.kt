package fr.kingsrequin.horizon.link

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Ce que le téléphone garde d'un PC appairé. */
@Serializable
data class Pairing(
    val pcId: String,
    val pcName: String,
    /** Adresses IPv4 du QR, dans l'ordre. */
    val hosts: List<String>,
    /** Nom d'hôte du PC, à essayer en `<nom>.local` quand aucune adresse ne répond. */
    val hostName: String,
    val port: Int,
    /** Empreinte de la clé publique du PC : c'est elle qui fait foi, pas l'adresse. */
    val fingerprint: String,
    /** Identifiant de ce téléphone pour ce PC. */
    val deviceId: String,
    /** Jeton permanent donné par le PC à l'appairage. Ne quitte jamais le téléphone en clair. */
    val deviceToken: String,
    /** Dernière adresse qui a répondu : essayée en premier. */
    val lastHost: String?,
)

private val text = Json { ignoreUnknownKeys = true }

/** L'appairage en texte, pour l'enregistrer. Il contient le jeton : à chiffrer avant de l'écrire. */
fun Pairing.toText(): String = text.encodeToString(Pairing.serializer(), this)

/** L'appairage lu d'un texte de [toText] ; `null` si le texte est illisible. */
fun pairingFromText(value: String): Pairing? = try {
    text.decodeFromString(Pairing.serializer(), value)
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

/** Où l'appairage est gardé. Un seul PC à la fois. */
interface PairingStore {
    /** L'appairage enregistré, ou `null` s'il n'y en a pas (ou s'il n'est plus lisible). */
    suspend fun load(): Pairing?

    suspend fun save(p: Pairing)

    suspend fun clear()
}
