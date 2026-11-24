package fr.kingsrequin.horizon.pairing

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.kingsrequin.horizon.R
import fr.kingsrequin.horizon.connection.LinkMode
import fr.kingsrequin.horizon.link.PairResult
import fr.kingsrequin.horizon.protocol.PairingLink
import fr.kingsrequin.horizon.protocol.QrResult
import fr.kingsrequin.horizon.protocol.parsePairingLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Où en est le parcours d'appairage. Les messages sont des textes de l'appli (`R.string`). */
sealed interface PairingStep {
    /** Où trouver le code dans Horizon. */
    data object Intro : PairingStep

    data object CameraDenied : PairingStep

    /** Le viseur ; `hint` : pourquoi le dernier code lu ne convient pas. */
    data class Scanning(@StringRes val hint: Int?) : PairingStep

    data object Connecting : PairingStep

    data class Failed(@StringRes val message: Int) : PairingStep

    /** Appairé : reste à choisir quand la liaison est tenue. */
    data class ChooseMode(val pcName: String) : PairingStep

    data object Done : PairingStep
}

/**
 * Le parcours d'appairage : explication, viseur, connexion, choix du mode. `pair` appaire avec
 * le PC d'un lien lu ; `setMode` enregistre le mode choisi. `scope` n'est donné que par les
 * tests : l'appli emploie celui du modèle de vue.
 */
class PairingViewModel(
    private val pair: suspend (PairingLink) -> PairResult,
    private val setMode: (LinkMode) -> Unit,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = scope ?: viewModelScope
    private val current = MutableStateFlow<PairingStep>(PairingStep.Intro)
    val step: StateFlow<PairingStep> = current.asStateFlow()
    private var cameraGranted = false

    /** Revient au début du parcours. */
    fun start() {
        current.value = PairingStep.Intro
    }

    /** Réponse à la demande de la caméra, faite à l'entrée du viseur. */
    fun onCameraPermission(granted: Boolean) {
        cameraGranted = granted
        current.value = if (granted) PairingStep.Scanning(null) else PairingStep.CameraDenied
    }

    /** Un QR code vient d'être lu par le viseur (appelé à chaque image tant qu'il est devant l'objectif). */
    fun onQr(text: String) {
        if (current.value !is PairingStep.Scanning) return
        when (val read = parsePairingLink(text)) {
            is QrResult.Ok -> connect(read.link)
            QrResult.NotHorizon -> current.value = PairingStep.Scanning(R.string.pairing_not_horizon)
            QrResult.Malformed -> current.value = PairingStep.Scanning(R.string.pairing_malformed)
            is QrResult.Unsupported -> current.value = PairingStep.Failed(R.string.pairing_version)
        }
    }

    /** Un lien `horizon://pair` ouvert depuis ailleurs (navigateur, autre lecteur de QR). */
    fun onLink(text: String) {
        if (current.value is PairingStep.Connecting) return
        when (val read = parsePairingLink(text)) {
            is QrResult.Ok -> connect(read.link)
            QrResult.Malformed -> current.value = PairingStep.Failed(R.string.pairing_malformed)
            is QrResult.Unsupported -> current.value = PairingStep.Failed(R.string.pairing_version)
            QrResult.NotHorizon -> Unit
        }
    }

    private fun connect(link: PairingLink) {
        current.value = PairingStep.Connecting
        scope.launch {
            current.value = when (val result = pair(link)) {
                is PairResult.Paired -> PairingStep.ChooseMode(result.pcName)
                is PairResult.Refused -> PairingStep.Failed(
                    when (result.reason) {
                        "expired" -> R.string.pairing_expired
                        "used" -> R.string.pairing_used
                        else -> R.string.pairing_refused
                    },
                )
                PairResult.Unreachable -> PairingStep.Failed(R.string.pairing_unreachable)
                PairResult.WrongPc -> PairingStep.Failed(R.string.pairing_wrong_pc)
            }
        }
    }

    fun choose(mode: LinkMode) {
        if (current.value !is PairingStep.ChooseMode) return
        setMode(mode)
        current.value = PairingStep.Done
    }

    /** Après un échec : le viseur si la caméra est accordée, sinon le début. */
    fun retry() {
        current.value = if (cameraGranted) PairingStep.Scanning(null) else PairingStep.Intro
    }
}
