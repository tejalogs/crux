import org.crux.buildlogic.CruxPlatform
import org.crux.buildlogic.flare

plugins {
    id("org.crux.multiplatform-library")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.room)
}

kotlin {
    flare {
        namespace = "org.crux.feature.agent"
        platforms(
            CruxPlatform.ANDROID,
            CruxPlatform.JVM,
            CruxPlatform.IOS,
            CruxPlatform.WEB,
        )
        ksp(
            libs.room.compiler,
        )
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.shared)
                implementation(projects.feature.subscription)
                implementation(dependencies.platform(libs.compose.bom))
                implementation(libs.compose.runtime)
                implementation(libs.koog.agents)
                implementation(libs.koog.agents.features.memory)
                implementation(libs.koog.http.client.ktor)
                implementation(libs.koog.prompt.executor.ollama.client)
                implementation(libs.bundles.kotlinx)
                implementation(libs.room.runtime)
                implementation(libs.sqlite)
                implementation(dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.annotations)
            }
        }
        val nonWebMain by getting {
            dependencies {
                implementation(libs.sqlite.bundled)
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

room3 {
    schemaDirectory("$projectDir/schemas")
}
