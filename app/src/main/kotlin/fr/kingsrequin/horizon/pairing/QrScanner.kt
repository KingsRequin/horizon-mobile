package fr.kingsrequin.horizon.pairing

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

private const val TAG = "Viseur"

/**
 * Le viseur : l'image de la caméra arrière, et `onQr` (sur le fil principal) pour chaque image
 * où un QR code se lit. Une image analysée à la fois, les autres sont sautées. La caméra n'est
 * tenue que tant que le viseur est à l'écran.
 */
@Composable
fun QrScanner(onQr: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onQr)
    val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(owner) {
        val main = ContextCompat.getMainExecutor(context)
        val analyser = Executors.newSingleThreadExecutor()
        val cameras = ProcessCameraProvider.getInstance(context)
        var disposed = false
        cameras.addListener({
            if (disposed) return@addListener
            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(analyser) { image ->
                val text = image.use { decodeQr(luminance(it), it.width, it.height) }
                if (text != null) main.execute { callback(text) }
            }
            try {
                val provider = cameras.get()
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                // Pas de caméra arrière, ou caméra prise par une autre appli : le viseur reste noir.
                Log.w(TAG, "caméra indisponible", e)
            }
        }, main)
        onDispose {
            disposed = true
            if (cameras.isDone) runCatching { cameras.get().unbindAll() }
            analyser.shutdown()
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
}

/** Le plan Y de l'image (niveaux de gris), sans le remplissage de fin de ligne. */
private fun luminance(image: ImageProxy): ByteArray {
    val plane = image.planes[0]
    val (width, height, stride) = Triple(image.width, image.height, plane.rowStride)
    val buffer = plane.buffer
    val out = ByteArray(width * height)
    if (stride == width) {
        buffer.get(out, 0, minOf(out.size, buffer.remaining()))
    } else {
        for (row in 0 until height) {
            buffer.position(row * stride)
            buffer.get(out, row * width, minOf(width, buffer.remaining()))
        }
    }
    return out
}
