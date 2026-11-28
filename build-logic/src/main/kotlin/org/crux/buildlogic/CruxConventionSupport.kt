package org.crux.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.KtlintPlugin

class CruxAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("com.android.application")
    }
}

class CruxRootConventionsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.subprojects.forEach { subproject ->
            subproject.pluginManager.apply(KtlintPlugin::class.java)
            subproject.extensions.configure<KtlintExtension> {
                version.set(ktlintCliVersion)
                filter {
                    exclude { element -> element.file.path.contains("build", ignoreCase = true) }
                }
            }
        }
    }
}

class CruxAndroidApplicationSpec internal constructor(
    private val project: Project,
) {
    var namespace: String? = null
    var applicationId: String? = null

    internal fun apply(configure: ApplicationExtension.() -> Unit = {}) {
        val androidNamespace = requireNotNull(namespace) {
            "crux { namespace = ... } is required for Android applications."
        }
        val androidApplicationId = applicationId ?: androidNamespace

        project.extensions.configure<ApplicationExtension> {
            this.namespace = androidNamespace
            compileSdk {
                version = release(project.intVersion("compileSdk")) {
                    minorApiLevel = 0
                }
            }
            defaultConfig {
                minSdk {
                    version = release(project.intVersion("minSdk"))
                }
                this.applicationId = androidApplicationId
                targetSdk {
                    version = release(project.intVersion("compileSdk"))
                }
                vectorDrawables {
                    useSupportLibrary = true
                }
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

                packaging {
                    resources {
                        excludes.add("/META-INF/{AL2.0,LGPL2.1}")
                        excludes.add("DebugProbesKt.bin")
                    }
                }
            }

            compileOptions {
                isCoreLibraryDesugaringEnabled = true
                sourceCompatibility = JavaVersion.toVersion(project.intVersion("java"))
                targetCompatibility = JavaVersion.toVersion(project.intVersion("java"))
            }
            buildFeatures {
                compose = true
                buildConfig = true
            }
            packaging {
                resources {
                    excludes += "/META-INF/{AL2.0,LGPL2.1}"
                }
            }
            lint {
                disable.add("MissingTranslation")
            }
            androidResources {
                generateLocaleConfig = true
            }
            configure()
        }
    }
}

private const val ktlintCliVersion = "1.8.0"

fun Project.crux(configure: CruxAndroidApplicationSpec.() -> Unit) {
    CruxAndroidApplicationSpec(this).apply(configure).apply()
}

fun Project.flare(configure: CruxAndroidApplicationSpec.() -> Unit) = crux(configure)

private fun Project.intVersion(name: String): Int =
    versionCatalog().findVersion(name).get().requiredVersion.toInt()

private fun Project.versionCatalog() = extensions.getByType<VersionCatalogsExtension>().named("libs")
