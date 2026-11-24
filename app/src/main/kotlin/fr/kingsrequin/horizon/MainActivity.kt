package fr.kingsrequin.horizon

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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
