package fr.kingsrequin.horizon.ui

import android.content.res.Resources
import fr.kingsrequin.horizon.R

/** Une durée écoulée, à l'échelle où on la dit : « à l'instant », « il y a 2 min », « il y a 3 h », « il y a 2 j ». */
sealed interface Ago {
    data object Now : Ago

    data class Minutes(val count: Int) : Ago

    data class Hours(val count: Int) : Ago

    data class Days(val count: Int) : Ago
}

/** La durée `deltaMs`, arrondie vers le bas à son unité ; moins d'une minute (ou une heure à venir) : « à l'instant ». */
fun agoOf(deltaMs: Long): Ago {
    val minutes = deltaMs / 60_000
    return when {
        minutes < 1 -> Ago.Now
        minutes < 60 -> Ago.Minutes(minutes.toInt())
        minutes < 24 * 60 -> Ago.Hours((minutes / 60).toInt())
        else -> Ago.Days((minutes / (24 * 60)).toInt())
    }
}

/** La durée en clair, dans la langue de l'appli. */
fun Ago.text(res: Resources): String = when (this) {
    Ago.Now -> res.getString(R.string.ago_now)
    is Ago.Minutes -> res.getString(R.string.ago_minutes, count)
    is Ago.Hours -> res.getString(R.string.ago_hours, count)
    is Ago.Days -> res.getString(R.string.ago_days, count)
}
