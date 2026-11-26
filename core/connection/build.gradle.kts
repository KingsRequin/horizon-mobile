// Ce que la liaison avec le PC demande à Android : coffre de clés, service au premier plan, réseau.
// La logique elle-même (session, reprise) est dans core:link, testée sur la JVM.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "fr.kingsrequin.horizon.connection"
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:protocol"))
    api(project(":core:link"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.media3.session)
    // Les icônes du lecteur pour la notification, les mêmes que sur la télécommande.
    implementation(project(":core:design"))

    testImplementation(libs.junit)
}
