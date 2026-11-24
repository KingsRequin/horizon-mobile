// Liaison avec le PC, en Kotlin pur : session TLS épinglée sur la clé du PC, appairage, reprise.
// Sans Android, donc testée sur la JVM contre un faux PC.
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
    api(project(":core:protocol"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
