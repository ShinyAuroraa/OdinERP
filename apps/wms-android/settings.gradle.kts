pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "de.mannodermaus.android-junit5") {
                useModule("de.mannodermaus.gradle.plugins:android-junit5:${requested.version}")
            }
        }
    }
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WmsAndroid"
include(":app")
