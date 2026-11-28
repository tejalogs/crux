import org.crux.buildlogic.flare
import java.util.Properties

plugins {
    id("org.crux.android-application")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.aboutlibraries)
    id("kotlin-parcelize")
}

flare {
    namespace = "org.crux"
    applicationId = "org.crux.social"
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        freeCompilerArgs.add("-Xconsistent-data-class-copy-visibility")
    }
}

android {
    val fdroid = rootProject.file("fdroid.properties")
    val fdroidProp = Properties()
    fdroidProp.load(fdroid.inputStream())

    defaultConfig {
        versionCode =
            System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: fdroidProp.getProperty("versionCode")
                ?.toIntOrNull() ?: 1
        versionName =
            System.getenv("BUILD_VERSION") ?: fdroidProp.getProperty("versionName") ?: "0.0.0"
    }

    val file = rootProject.file("signing.properties")
    val hasSigningProps = file.exists()

    signingConfigs {
        if (hasSigningProps) {
            create("crux") {
                val signingProp = Properties()
                signingProp.load(file.inputStream())
                storeFile = rootProject.file(signingProp.getProperty("storeFile"))
                storePassword = signingProp.getProperty("storePassword")
                keyAlias = signingProp.getProperty("keyAlias")
                keyPassword = signingProp.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasSigningProps) {
                signingConfig = signingConfigs.getByName("crux")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Keep release APKs lean for real Android devices; debug builds keep x86_64 for emulators.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            optimization {
                enable = true
            }
            // CI release builds stay unsigned so no build code ever sees the keystore; the
            // workflow signs the finished APK with apksigner in a separate step.
            signingConfig =
                when {
                    providers.environmentVariable("CRUX_UNSIGNED_RELEASE").orNull == "true" -> null
                    hasSigningProps -> signingConfigs.getByName("crux")
                    else -> signingConfigs.getByName("debug")
                }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "DebugProbesKt.bin",
                "org/fusesource/jansi/**",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
            )
        }
    }

    // Reproducible, F-Droid friendly: no signing-block dependency metadata.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.kotlinx)
    implementation(platform(libs.koin.bom))
    implementation(libs.bundles.koin)
    implementation(libs.koin.annotations)
    implementation(libs.bundles.coil3)
    implementation(libs.bundles.coil3.extensions)
    implementation(libs.bundles.ktor)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.molecule.runtime)
    implementation(libs.ksoup)
    implementation(libs.readability)
    implementation(libs.compose.placeholder)
    implementation(libs.bundles.accompanist)
    lintChecks(libs.compose.lint.checks)
    implementation(libs.composeIcons.fontAwesome)
    implementation(libs.datastore)
    implementation(libs.datastore.core.okio)
    implementation(libs.okio)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.swiper)
    implementation(libs.reorderable)
    implementation(libs.androidx.window)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.compose.webview)
    implementation(libs.androidx.splash)
    implementation(libs.materialKolor)
    implementation(libs.colorpicker.compose)
    implementation(libs.material.motion.compose)
    implementation(libs.nestedScrollView)
    implementation(libs.precompose.molecule)
    implementation(libs.webkit)
    implementation(libs.swiperefreshlayout)
    implementation(libs.bundles.navigation3)
    implementation(libs.richtext.ui.material3)
    implementation(libs.richtext.commonmark)
    implementation(libs.litertlm.android)

    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}

kotlin {
    sourceSets {
        all {
            languageSettings {
                optIn("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
            }
        }
    }
}
