// Liaison avec le PC : TLS épinglé, appairage, reprise, service au premier plan.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "fr.kingsrequin.horizon.connection"
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:protocol"))

    testImplementation(libs.junit)
}
