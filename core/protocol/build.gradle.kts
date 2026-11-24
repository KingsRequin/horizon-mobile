// Kotlin pur, sans Android : messages du protocole et lecture du QR, testés sur la JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
