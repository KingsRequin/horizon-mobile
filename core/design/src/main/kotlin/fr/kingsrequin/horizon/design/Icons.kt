package fr.kingsrequin.horizon.design

import androidx.annotation.DrawableRes

/**
 * Les icônes de l'appli : Phosphor Regular, les mêmes pictogrammes que la barre d'Horizon pour
 * les mêmes choses ; variante pleine (`…Fill`) pour les commandes du lecteur, comme la charte.
 * Dessinées à [HorizonSize.icon], teintées par la couleur du texte. Fabriquées par
 * `tools/icones.ps1`.
 */
object HorizonIcons {
    @DrawableRes val DeviceMobile = R.drawable.ic_device_mobile
    @DrawableRes val Watch = R.drawable.ic_watch
    @DrawableRes val Headset = R.drawable.ic_headset
    @DrawableRes val Check = R.drawable.ic_check
    @DrawableRes val Timer = R.drawable.ic_timer
    @DrawableRes val GearSix = R.drawable.ic_gear_six
    @DrawableRes val Warning = R.drawable.ic_warning
    @DrawableRes val CaretDown = R.drawable.ic_caret_down
    @DrawableRes val Play = R.drawable.ic_play
    @DrawableRes val X = R.drawable.ic_x
    @DrawableRes val House = R.drawable.ic_house
    @DrawableRes val Desktop = R.drawable.ic_desktop
    @DrawableRes val Lock = R.drawable.ic_lock
    @DrawableRes val QrCode = R.drawable.ic_qr_code
    @DrawableRes val Copy = R.drawable.ic_copy
    @DrawableRes val PlayFill = R.drawable.ic_play_fill
    @DrawableRes val PauseFill = R.drawable.ic_pause_fill
    @DrawableRes val SkipBackFill = R.drawable.ic_skip_back_fill
    @DrawableRes val SkipForwardFill = R.drawable.ic_skip_forward_fill
    @DrawableRes val SpeakerHigh = R.drawable.ic_speaker_high
    @DrawableRes val SpeakerSlash = R.drawable.ic_speaker_slash
    @DrawableRes val List = R.drawable.ic_list
    @DrawableRes val Moon = R.drawable.ic_moon
    @DrawableRes val Lightning = R.drawable.ic_lightning
}
