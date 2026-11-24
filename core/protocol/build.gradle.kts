// Kotlin pur, sans Android : messages du protocole et lecture du QR, testés sur la JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(libs.junit)
}
