import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    val kvisionVersion: String by System.getProperties()
    id("io.kvision") version kvisionVersion
}

version = "1.0.0-SNAPSHOT"
group = "pg.agogo.landing"

repositories {
    mavenCentral()
    mavenLocal()
}

val kotlinVersion: String by System.getProperties()
val kvisionVersion: String by System.getProperties()

kotlin {
    js(IR){
        moduleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(project.projectDir.path)
                    }
                }
            }
        }
        binaries.executable()
    }
//    @OptIn(ExperimentalWasmDsl::class)
//    wasmJs {
//        moduleName = "composeApp"
//        browser {
//            commonWebpackConfig {
//                outputFileName = "composeApp.js"
//                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
//                    static = (static ?: mutableListOf()).apply {
//                        // Serve sources to debug inside browser
//                        add(project.projectDir.path)
//                    }
//                }
//            }
//        }
//        binaries.executable()
//    }

    sourceSets {
        commonMain.dependencies {
            implementation("io.kvision:kvision:$kvisionVersion")
            implementation("io.kvision:kvision-rest:$kvisionVersion")
            implementation("io.kvision:kvision-state-flow:$kvisionVersion")
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            // Ktor
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.client.content.negotiation)
            // Coroutines
            implementation(libs.kotlinx.coroutines.core)
            // Koin
            implementation(libs.koin.core)

        }
    }
}
