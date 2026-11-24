package fr.kingsrequin.horizon.link

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import okio.ByteString.Companion.toByteString

/** Le serveur joint n'a pas la clé du PC appairé : ce n'est pas lui (ou quelqu'un se met entre les deux). */
class WrongPc : Exception("la clé du serveur n'est pas celle du PC appairé")

/**
 * Empreinte de la clé publique d'un certificat : SHA-256 du SPKI (DER), en base64url sans
 * remplissage (43 caractères). C'est ce que porte le QR d'Horizon. Elle ne dépend que de la
 * clé : le PC peut renouveler son certificat sans qu'il faille réappairer.
 */
fun spkiFingerprint(cert: X509Certificate): String = cert.publicKey.encoded.toByteString().sha256().base64Url().trimEnd('=')

/**
 * Ne fait confiance qu'au certificat dont la clé a l'empreinte attendue. Le PC présente un
 * certificat auto-signé : ni autorité ni nom d'hôte à vérifier, l'empreinte lue dans le QR est
 * la seule preuve, et elle suffit.
 *
 * Lint déconseille d'écrire son propre gestionnaire de confiance parce qu'on y désactive
 * souvent toute vérification ; celui-ci fait l'inverse, il n'accepte qu'une clé (PinningTest).
 */
@Suppress("CustomX509TrustManager")
internal class PinnedTrustManager(private val fingerprint: String) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        // Le premier certificat est celui dont le serveur prouve détenir la clé.
        val presented = chain.firstOrNull()?.let(::spkiFingerprint)
        if (presented == null || !MessageDigest.isEqual(presented.toByteArray(), fingerprint.toByteArray())) {
            throw CertificateException(WrongPc())
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String): Unit =
        throw CertificateException("ce gestionnaire ne sert qu'à vérifier le PC")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
