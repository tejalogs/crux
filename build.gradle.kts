import org.koin.compiler.plugin.KoinGradleExtension

plugins {
    id("org.crux.root-conventions")
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ben.manes.versions)
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.koin.compiler) apply false
}

subprojects {
    pluginManager.withPlugin("io.insert-koin.compiler.plugin") {
        extensions.configure<KoinGradleExtension> {
            logSeverity.set("info")
            versionCheckSeverity.set("info")
        }
    }
}
