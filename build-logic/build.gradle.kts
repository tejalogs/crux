plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    compileOnly("com.android.tools.build:gradle:9.4.1")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:14.2.0")
    testImplementation(kotlin("test-junit"))
}

gradlePlugin {
    plugins {
        create("cruxMultiplatformLibrary") {
            id = "org.crux.multiplatform-library"
            implementationClass = "org.crux.buildlogic.CruxMultiplatformLibraryPlugin"
        }
        create("cruxAndroidApplication") {
            id = "org.crux.android-application"
            implementationClass = "org.crux.buildlogic.CruxAndroidApplicationPlugin"
        }
        create("cruxRootConventions") {
            id = "org.crux.root-conventions"
            implementationClass = "org.crux.buildlogic.CruxRootConventionsPlugin"
        }
    }
}
