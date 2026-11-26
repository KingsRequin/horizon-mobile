package fr.kingsrequin.horizon.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Version du protocole parlée par cette appli (`docs/PHONE.md` du dépôt d'Horizon). */
const val PROTOCOL = 1

/** Une batterie envoyée au PC : celle du téléphone, plus tard celles de ses appareils. */
@Serializable
data class DeviceBattery(
    val id: String,
    /** `phone`, `watch`, `earbuds`… : le PC en tire l'icône. */
    val kind: String,
    val name: String,
    val percent: Int,
    val charging: Boolean,
    /** Heure de la mesure, en millisecondes depuis 1970. */
    val atMs: Long,
)

/** La lecture en cours sur le PC (`media.state`). */
@Serializable
data class MediaInfo(
    val appId: String,
    val appName: String,
    val title: String,
    val artist: String,
    val playing: Boolean,
    /** Position au moment de l'envoi. */
    val positionMs: Long,
    /** 0 : durée inconnue (direct). */
    val durationMs: Long,
    /** Clé de la pochette (`media.art` porte la même) ; absente : pas de pochette. */
    val artKey: String? = null,
)

/** Une lecture que le PC sait piloter (`media.select`). */
@Serializable
data class SessionInfo(val appId: String, val name: String)

/** Une sortie audio du PC ; `kind` : `headset` ou `speaker` (l'icône). */
@Serializable
data class OutputInfo(val id: String, val name: String, val kind: String)

/** Une appli du mixeur du PC ; `key` (clé d'exe) est ce que les commandes reprennent. */
@Serializable
data class AppInfo(val key: String, val name: String, val volume: Float, val muted: Boolean, val active: Boolean)

/** Minuteur du PC : `remainingMs` au moment de l'envoi, à décompter depuis la réception. */
@Serializable
data class TimerInfo(val remainingMs: Long, val totalMs: Long, val paused: Boolean)

/** Casque du PC. */
@Serializable
data class HeadsetInfo(val name: String, val level: Int, val charging: Boolean)

/** Ce que le téléphone envoie. Le nom de série est le champ `type` du message. */
@Serializable
sealed interface ToPc {
    @Serializable
    @SerialName("pair.request")
    data class PairRequest(val token: String, val deviceId: String, val deviceName: String, val appVersion: String) : ToPc

    @Serializable
    @SerialName("hello")
    data class Hello(val deviceId: String, val deviceToken: String, val protocol: Int, val appVersion: String) : ToPc

    @Serializable
    @SerialName("phone.battery")
    data class PhoneBattery(val devices: List<DeviceBattery>) : ToPc

    /** Ce que l'appli regarde (`media`, `volume`) ; vide : plus rien. Remplace l'abonnement précédent. */
    @Serializable
    @SerialName("watch")
    data class Watch(val topics: List<String>) : ToPc

    /** `action` : `play_pause`, `next`, `previous`. */
    @Serializable
    @SerialName("media.command")
    data class MediaCommand(val action: String) : ToPc

    @Serializable
    @SerialName("media.seek")
    data class MediaSeek(val positionMs: Long) : ToPc

    @Serializable
    @SerialName("media.select")
    data class MediaSelect(val appId: String) : ToPc

    /** Volume général, de 0 à 1. */
    @Serializable
    @SerialName("volume.set")
    data class VolumeSet(val volume: Float) : ToPc

    @Serializable
    @SerialName("volume.mute")
    data class VolumeMute(val muted: Boolean) : ToPc

    @Serializable
    @SerialName("mixer.app_volume")
    data class MixerAppVolume(val key: String, val volume: Float) : ToPc

    @Serializable
    @SerialName("mixer.app_mute")
    data class MixerAppMute(val key: String, val muted: Boolean) : ToPc

    /** Sortie audio par défaut du PC. */
    @Serializable
    @SerialName("mixer.output")
    data class MixerOutput(val id: String) : ToPc

    /** Verrouiller le PC. */
    @Serializable
    @SerialName("action.lock")
    data object ActionLock : ToPc

    @Serializable
    @SerialName("action.dnd")
    data class ActionDnd(val on: Boolean) : ToPc

    @Serializable
    @SerialName("timer.start")
    data class TimerStart(val minutes: Int) : ToPc

    @Serializable
    @SerialName("timer.pause")
    data class TimerPause(val paused: Boolean) : ToPc

    @Serializable
    @SerialName("timer.stop")
    data object TimerStop : ToPc

    /** Déplacer le pointeur, en pixels. */
    @Serializable
    @SerialName("input.move")
    data class InputMove(val dx: Int, val dy: Int) : ToPc

    /** `button` : `left`, `right`, `middle` ; `action` : `click`, `down`, `up`. */
    @Serializable
    @SerialName("input.button")
    data class InputButton(val button: String, val action: String) : ToPc

    /** Défiler, en unités de molette (120 = un cran ; positif : vers le haut, vers la droite). */
    @Serializable
    @SerialName("input.scroll")
    data class InputScroll(val dx: Int, val dy: Int) : ToPc

    /** Zoomer (Ctrl + molette), mêmes unités. */
    @Serializable
    @SerialName("input.zoom")
    data class InputZoom(val delta: Int) : ToPc

    /** Taper ce texte (256 caractères au plus). */
    @Serializable
    @SerialName("input.text")
    data class InputText(val text: String) : ToPc

    /** Appuyer sur une touche nommée (`escape`, `f5`, `c`…) avec ses modificateurs (`ctrl`, `alt`, `shift`, `win`). */
    @Serializable
    @SerialName("input.key")
    data class InputKey(val key: String, val mods: List<String> = emptyList()) : ToPc
}

/** Ce que le PC envoie. */
@Serializable
sealed interface FromPc {
    @Serializable
    @SerialName("pair.accepted")
    data class PairAccepted(val deviceToken: String, val pcId: String, val pcName: String) : FromPc

    /** `reason` : `expired`, `used`, `unknown`. */
    @Serializable
    @SerialName("pair.rejected")
    data class PairRejected(val reason: String) : FromPc

    /** `granted` : ce que ce téléphone a le droit de faire ; absent d'un PC 0.8 (tout ce qu'il sait faire). */
    @Serializable
    @SerialName("hello.ok")
    data class HelloOk(val pcId: String, val pcName: String, val protocol: Int, val features: List<String>, val accent: String, val granted: List<String>? = null) : FromPc

    /** `reason` : `unknown_device`, `bad_token`, `protocol`. */
    @Serializable
    @SerialName("hello.rejected")
    data class HelloRejected(val reason: String) : FromPc

    /** La couleur d'accent de la barre a changé (`#rrggbb`). */
    @Serializable
    @SerialName("accent")
    data class Accent(val color: String) : FromPc

    /** Les droits de ce téléphone ont changé (page Téléphone d'Horizon). */
    @Serializable
    @SerialName("rights")
    data class Rights(val granted: List<String>) : FromPc

    /** La lecture a changé ; `media` absent : rien ne joue. */
    @Serializable
    @SerialName("media.state")
    data class MediaState(val media: MediaInfo?, val sessions: List<SessionInfo>) : FromPc

    /** Pochette de la lecture `artKey`, en JPEG encodé en base64. */
    @Serializable
    @SerialName("media.art")
    data class MediaArt(val artKey: String, val jpeg: String) : FromPc

    /** Volume général du PC, de 0 à 1. */
    @Serializable
    @SerialName("volume.state")
    data class VolumeState(val volume: Float, val muted: Boolean) : FromPc

    /** Sorties, sortie par défaut et volume de chaque appli (celles qui émettent d'abord). */
    @Serializable
    @SerialName("mixer.state")
    data class MixerState(val outputs: List<OutputInfo>, val defaultOutput: String?, val apps: List<AppInfo>) : FromPc

    /** Ne pas déranger, minuteur (`null` : arrêté), durées proposées, casque du PC. */
    @Serializable
    @SerialName("actions.state")
    data class ActionsState(val dnd: Boolean, val timer: TimerInfo?, val timerPresets: List<Int>, val headset: HeadsetInfo?) : FromPc

    /** Un message d'un type que cette version ne connaît pas : ignoré, la session continue. */
    @Serializable
    @SerialName("unknown")
    data object Unknown : FromPc
}
