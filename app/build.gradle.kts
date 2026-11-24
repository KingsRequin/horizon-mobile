import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Signature de la version publiée par variables d'environnement ; absentes : APK non signé.
fun env(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
val keystorePath = env("HM_KEYSTORE_PATH")

// Version dans version.properties, pour qu'un script de publication puisse l'incrémenter.
val versionProps = Properties().apply { rootProject.file("version.properties").inputStream().use(::load) }

android {
    namespace = "fr.kingsrequin.horizon"

    defaultConfig {
        applicationId = "fr.kingsrequin.horizon"
        targetSdk = 36
        versionCode = versionProps.getProperty("versionCode").toInt()
        versionName = versionProps.getProperty("versionName")
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = env("HM_KEYSTORE_PASSWORD")
                keyAlias = env("HM_KEY_ALIAS")
                keyPassword = env("HM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Variante d'essai : autre paquet, installable à côté de la version publiée.
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures { compose = true }

    // La liste des langues de l'appli (réglage « langue par appli » du système, Android 13 et plus).
    androidResources { generateLocaleConfig = true }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:protocol"))
    implementation(project(":core:design"))
    implementation(project(":core:connection"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // Lecture du QR d'appairage : CameraX pour l'image, ZXing (Java pur) pour le code.
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
