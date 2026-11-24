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

    @Serializable
    @SerialName("hello.ok")
    data class HelloOk(val pcId: String, val pcName: String, val protocol: Int, val features: List<String>, val accent: String) : FromPc

    /** `reason` : `unknown_device`, `bad_token`, `protocol`. */
    @Serializable
    @SerialName("hello.rejected")
    data class HelloRejected(val reason: String) : FromPc

    /** La couleur d'accent de la barre a changé (`#rrggbb`). */
    @Serializable
    @SerialName("accent")
    data class Accent(val color: String) : FromPc

    /** Un message d'un type que cette version ne connaît pas : ignoré, la session continue. */
    @Serializable
    @SerialName("unknown")
    data object Unknown : FromPc
}
