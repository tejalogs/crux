pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            content {
                includeGroup("moe.tlaster.ozone")
                includeGroup("moe.tlaster.ozone.generator")
            }
        }
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
        maven("https://central.sonatype.com/repository/maven-snapshots/")
        maven("https://jitpack.io")
    }
}

rootProject.name = "Crux"
include(":app")
include(":shared")
include(":feature:login-api")
include(":feature:agent")
include(":feature:login")
include(":feature:subscription")
include(":feature:tab")
include(":compose-ui")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
