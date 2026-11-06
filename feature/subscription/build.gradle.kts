import org.crux.buildlogic.CruxPlatform
import org.crux.buildlogic.flare

plugins {
    id("org.crux.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    flare {
        namespace = "org.crux.feature.subscription"
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
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.runtime)
                implementation(libs.bundles.kotlinx)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.annotations)
                implementation(libs.bundles.ktor)
                implementation(libs.ktor.client.resources)
                implementation(libs.ksoup)
                implementation(libs.readability)
                implementation(libs.xmlUtil)
                implementation(libs.molecule.runtime)
                implementation(libs.paging.common)
                implementation(libs.paging.compose)
                implementation(libs.kotlinx.immutable)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.paging.testing)
                implementation(libs.ktor.client.mock)
            }
        }
        val androidJvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }
        val appleMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}
