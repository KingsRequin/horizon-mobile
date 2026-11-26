package fr.kingsrequin.horizon

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableStateFlow

/** Le seul écran de l'appli : tout le reste est dessiné dedans ([App]). */
class MainActivity : ComponentActivity() {
    /** Un lien `horizon://pair` par lequel l'appli vient d'être ouverte, jusqu'à ce qu'il soit traité. */
    private val pairingLink = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Toujours sombre, dessinée sous les barres du système.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        fr.kingsrequin.horizon.connection.Link.init(this)
        if (savedInstanceState == null) read(intent)
        setContent { App(pairingLink) }
    }

    /** Sur l'écran Télécommande, les touches de volume règlent le PC (sinon, celui du téléphone). */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val up = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> true
            KeyEvent.KEYCODE_VOLUME_DOWN -> false
            else -> return super.onKeyDown(keyCode, event)
        }
        return fr.kingsrequin.horizon.connection.Link.volumeKey(up) || super.onKeyDown(keyCode, event)
    }

    /** Le relâchement d'une touche de volume prise pour le PC ne doit pas régler le téléphone. */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val volume = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        val link = fr.kingsrequin.horizon.connection.Link
        return volume && link.volumeKeysOnPc && link.remote.view.value.volume != null || super.onKeyUp(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    override fun onStart() {
        super.onStart()
        fr.kingsrequin.horizon.connection.Link.setAppVisible(true)
    }

    override fun onStop() {
        fr.kingsrequin.horizon.connection.Link.setAppVisible(false)
        super.onStop()
    }

    private fun read(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) pairingLink.value = intent.dataString
    }
}
