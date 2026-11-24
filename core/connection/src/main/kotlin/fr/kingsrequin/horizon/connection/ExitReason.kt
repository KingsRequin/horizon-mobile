package fr.kingsrequin.horizon.connection

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/**
 * Pourquoi l'appli s'est arrêtée la dernière fois, d'après le système (Android 11 et plus ;
 * `null` avant, ou si rien n'est noté). Pour le rapport de diagnostic : c'est ce qui dit si une
 * surcouche du constructeur tue le service.
 */
fun lastExitReason(context: Context): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val exits = context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(null, 0, 1)
    val exit = exits.firstOrNull() ?: return null
    val reason = when (exit.reason) {
        ApplicationExitInfo.REASON_ANR -> "anr"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash_native"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency_died"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive_resource_usage"
        ApplicationExitInfo.REASON_EXIT_SELF -> "exit_self"
        ApplicationExitInfo.REASON_FREEZER -> "freezer"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization_failure"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
        ApplicationExitInfo.REASON_OTHER -> "other"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission_change"
        ApplicationExitInfo.REASON_SIGNALED -> "signaled"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user_requested"
        ApplicationExitInfo.REASON_USER_STOPPED -> "user_stopped"
        else -> "reason_${exit.reason}"
    }
    // La description du système dit souvent qui a tué le processus (« kill background », nom du nettoyeur).
    return listOfNotNull(reason, exit.description?.takeIf { it.isNotBlank() }).joinToString(" : ")
}
