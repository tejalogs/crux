import org.crux.buildlogic.CruxPlatform
import org.crux.buildlogic.flare

plugins {
    id("org.crux.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    flare {
        namespace = "org.crux.feature.login"
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
                api(projects.feature.loginApi)
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.runtime)
                api(dependencies.platform(libs.koin.bom))
                api(libs.koin.core)
                implementation(libs.bundles.kotlinx)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.molecule.runtime)
            }
        }
    }
}
