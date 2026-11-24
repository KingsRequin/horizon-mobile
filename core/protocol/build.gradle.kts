// Kotlin pur, sans Android : messages du protocole et lecture du QR, testés sur la JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.animalsniffer)
}

kotlin { jvmToolchain(17) }

// Le code de l'appli seulement : les tests tournent sur la JVM du poste.
animalsniffer { sourceSets = listOf(project.sourceSets.main.get()) }

dependencies {
    signature(variantOf(libs.android.api24.signatures) { artifactType("signature") })
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
