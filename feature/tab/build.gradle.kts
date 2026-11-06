import org.crux.buildlogic.CruxPlatform
import org.crux.buildlogic.flare

plugins {
    id("org.crux.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    flare {
        namespace = "org.crux.feature.tab"
        platforms(
            CruxPlatform.ANDROID,
            CruxPlatform.JVM,
            CruxPlatform.IOS,
            CruxPlatform.WEB,
            CruxPlatform.MACOS,
        )
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.shared)
                implementation(projects.feature.subscription)
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.runtime)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.kotlinx.immutable)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
