pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
// START Non-FOSS component
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
// END Non-FOSS component
dependencyResolutionManagement {
    // repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack builds artifacts from any GitHub repo, so it may serve only the two libraries
        // that live there, and those two may come from nowhere else.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter {
                includeModule("com.github.Tlaster", "NestedScrollView")
                includeModule("com.github.tlaster", "swiper")
            }
        }
    }
}

rootProject.name = "Crux"
include(":app")
include(":shared")
include(":feature:agent")
include(":feature:subscription")
include(":feature:tab")
include(":compose-ui")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
