package fr.kingsrequin.horizon.pairing

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

private val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.CHARACTER_SET to "UTF-8")

/**
 * Le texte du QR code d'une image en niveaux de gris (`width × height` octets, ligne par ligne :
 * le plan Y d'une image de caméra), ou `null` s'il n'y en a pas de lisible. L'image inversée est
 * essayée aussi : un écran en mode sombre peut montrer le code clair sur sombre.
 */
fun decodeQr(luminance: ByteArray, width: Int, height: Int): String? {
    if (width <= 0 || height <= 0 || luminance.size < width * height) return null
    val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
    return read(source) ?: read(source.invert())
}

private fun read(source: LuminanceSource): String? = try {
    QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
} catch (_: ReaderException) {
    null
}
