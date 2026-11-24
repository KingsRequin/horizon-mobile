package fr.kingsrequin.horizon.pairing

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrDecoderTest {
    /** Image en niveaux de gris d'un QR code, comme celle que rend la caméra : (luminance, largeur, hauteur). */
    private fun renderQr(text: String, modulePx: Int): Triple<ByteArray, Int, Int> {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0)
        val (w, h) = matrix.width * modulePx to matrix.height * modulePx
        val luminance = ByteArray(w * h) { i -> if (matrix.get(i % w / modulePx, i / w / modulePx)) 20 else 235.toByte() }
        return Triple(luminance, w, h)
    }

    private fun invert(luminance: ByteArray) = ByteArray(luminance.size) { (255 - (luminance[it].toInt() and 0xFF)).toByte() }

    @Test
    fun aGeneratedQrIsReadBack() {
        val text = "horizon://pair?v=1&h=192.168.1.49&n=BUREAU&p=28680&fp=${"A".repeat(43)}&t=${"b".repeat(22)}"
        val (lum, w, h) = renderQr(text, modulePx = 6)
        assertEquals(text, decodeQr(lum, w, h))
        assertEquals("clair sur sombre", text, decodeQr(invert(lum), w, h))
        assertNull(decodeQr(ByteArray(w * h) { 127 }, w, h))
    }

    @Test
    fun aFrameThatDoesNotMatchItsSizeIsIgnored() {
        assertNull(decodeQr(ByteArray(10), 100, 100))
        assertNull(decodeQr(ByteArray(0), 0, 0))
    }
}
