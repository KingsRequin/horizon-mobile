pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "horizon-mobile"

// core:protocol : Kotlin pur (messages, lecture du QR). core:link : Kotlin pur aussi (session TLS
// épinglée, reprise de la liaison), testé sur la JVM. core:design : la charte. core:connection : ce
// que la liaison demande à Android (coffre de clés, service, réseau). app : les écrans.
include(":app", ":core:protocol", ":core:link", ":core:design", ":core:connection")
