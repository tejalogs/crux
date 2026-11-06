import org.crux.buildlogic.CruxPlatform
import org.crux.buildlogic.flare

plugins {
    id("org.crux.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
}

kotlin {
    flare {
        namespace = "org.crux.feature.login.api"
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
                implementation(libs.bundles.kotlinx)
                implementation(libs.bundles.ktor)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.annotations)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
