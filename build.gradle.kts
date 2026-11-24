import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Android 7.0 et plus : l'appli doit tourner sur le plus d'appareils possible.
val sdkMin = 24
// Compose 1.13 demande la plateforme 37.1 au moins. Ces noms ne doivent pas être ceux d'une
// propriété de l'extension Android (`compileSdkMinor`…) : dans ses blocs, c'est elle qui serait lue.
val sdkMajor = 37
val sdkMinor = 2

/**
 * Lint en erreurs dans tous les modules. Les contrôles qui dépendent du jour ou du réseau
 * (nouvelles versions publiées, dernier `targetSdk`) sont coupés : ils changeraient le résultat
 * d'une machine à l'autre.
 */
fun Lint.strict() {
    abortOnError = true
    warningsAsErrors = true
    disable += setOf("NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion", "OldTargetApi")
}

// Réglages communs à tous les modules Android, posés une seule fois.
subprojects {
    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            compileSdk { version = release(sdkMajor) { minorApiLevel = sdkMinor } }
            defaultConfig { minSdk = sdkMin }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            lint { strict() }
        }
    }
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            compileSdk { version = release(sdkMajor) { minorApiLevel = sdkMinor } }
            defaultConfig { minSdk = sdkMin }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            lint { strict() }
        }
    }
}
