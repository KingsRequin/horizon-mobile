package fr.kingsrequin.horizon.connection

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.util.AtomicFile
import fr.kingsrequin.horizon.link.Pairing
import fr.kingsrequin.horizon.link.PairingStore
import fr.kingsrequin.horizon.link.pairingFromText
import fr.kingsrequin.horizon.link.toText
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * L'appairage sur le disque, chiffré en AES-256-GCM par une clé du coffre d'Android : elle n'en
 * sort jamais, et un fichier copié ailleurs (autre téléphone) est illisible. Dans ce cas, comme
 * pour un fichier abîmé, il n'y a simplement plus d'appairage : l'appli le redemande.
 *
 * Un simple fichier, remplacé d'un coup à chaque écriture, dans le dossier que les sauvegardes
 * ignorent. (DataStore aurait apporté une bibliothèque native pour un seul petit fichier.)
 */
class KeystorePairingStore(context: Context) : PairingStore {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "appairage"))
    private val oneAtATime = Mutex()

    override suspend fun load(): Pairing? = oneAtATime.withLock {
        withContext(Dispatchers.IO) {
            try {
                pairingFromText(decrypt(file.readFully()))
            } catch (_: IOException) {
                null
            } catch (_: GeneralSecurityException) {
                null
            }
        }
    }

    override suspend fun save(p: Pairing): Unit = oneAtATime.withLock {
        withContext(Dispatchers.IO) {
            val blob = encrypt(p.toText())
            val out = file.startWrite()
            try {
                out.write(blob)
                file.finishWrite(out)
            } catch (e: IOException) {
                file.failWrite(out)
                throw e
            }
        }
    }

    override suspend fun clear(): Unit = oneAtATime.withLock { withContext(Dispatchers.IO) { file.delete() } }

    /** Vecteur d'initialisation (12 octets, tiré par le coffre), puis le texte chiffré et son sceau. */
    private fun encrypt(text: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(text.toByteArray())
    }

    private fun decrypt(blob: ByteArray): String {
        if (blob.size <= IV_BYTES) throw GeneralSecurityException("appairage tronqué")
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES)) }
        return String(cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES))
    }

    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keystore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "horizon-appairage"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
