package fr.kingsrequin.horizon.link

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.util.Base64
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PinningTest {
    private val pc = HeldCertificate.Builder().commonName("Horizon").build()

    @Test
    fun theFingerprintIsTheSha256OfThePublicKeyInBase64Url() {
        // Même calcul que `Identity::fingerprint` d'Horizon : SHA-256 du SPKI (DER), base64url sans remplissage.
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(pc.certificate.publicKey.encoded))
        assertEquals(expected, spkiFingerprint(pc.certificate))
        assertEquals(43, spkiFingerprint(pc.certificate).length)
    }

    @Test
    fun aRenewedCertificateWithTheSameKeyKeepsItsFingerprint() {
        val renewed = HeldCertificate.Builder().commonName("Horizon, renouvelé").keyPair(pc.keyPair).build()
        assertEquals(spkiFingerprint(pc.certificate), spkiFingerprint(renewed.certificate))
    }

    @Test
    fun onlyTheExpectedKeyIsTrusted() {
        val trust = PinnedTrustManager(spkiFingerprint(pc.certificate))
        trust.checkServerTrusted(arrayOf(pc.certificate), "ECDHE_ECDSA")
        val other = HeldCertificate.Builder().commonName("Horizon").build()
        val refused = assertThrows(CertificateException::class.java) { trust.checkServerTrusted(arrayOf(other.certificate), "ECDHE_ECDSA") }
        assertTrue(refused.cause is WrongPc)
        // Seul le certificat présenté en premier compte : la bonne clé plus loin dans la chaîne ne suffit pas.
        assertThrows(CertificateException::class.java) { trust.checkServerTrusted(arrayOf(other.certificate, pc.certificate), "ECDHE_ECDSA") }
        assertThrows(CertificateException::class.java) { trust.checkServerTrusted(arrayOf(), "ECDHE_ECDSA") }
        assertThrows(CertificateException::class.java) { trust.checkClientTrusted(arrayOf(pc.certificate), "ECDHE_ECDSA") }
        assertEquals(0, trust.acceptedIssuers.size)
    }
}
