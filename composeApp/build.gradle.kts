import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

val copyWasmResources = tasks.create("copyWasmResourcesWorkaround", Copy::class.java) {
    into("build/processedResources/wasmJs/main")
}

afterEvaluate {
    project.tasks.getByName("wasmJsProcessResources").finalizedBy(copyWasmResources)
    project.tasks.getByName("wasmJsDevelopmentExecutableCompileSync").dependsOn(copyWasmResources)
}

kotlin {
    js(IR) {
        moduleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs{
        moduleName = "composeApp"
        browser {
            commonWebpackConfig {
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

    sourceSets {
//        val jsWasmMain by creating {
//            dependencies {
//                implementation(compose.runtime)
//                implementation(compose.ui)
//                implementation(compose.foundation)
//                implementation(compose.material)
//                implementation(compose.components.resources)
//                implementation(compose.components.uiToolingPreview)
//            }
//        }
//        val jsMain by getting {
//            dependsOn(jsWasmMain)
//            dependencies {
//                // Ktor
//                implementation(libs.ktor.client.core)
//                implementation(libs.ktor.client.serialization)
//                implementation(libs.ktor.client.content.negotiation)
//                // Coroutines
//                implementation(libs.kotlinx.coroutines.core)
//                // Koin
//                implementation(libs.koin.core)
//            }
//        }
//        val wasmJsMain by getting {
//            dependsOn(jsWasmMain)
//        }

        wasmJsMain.dependencies {



        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            // Coroutines
            implementation(libs.kotlinx.coroutines.core)
            // Koin
            implementation(libs.koin.core)
            //implementation(libs.koin.compose)
            // Ktor
            //implementation(libs.ktor.client.core)
            //implementation(libs.ktor.client.serialization)
            //implementation(libs.ktor.client.content.negotiation)
            implementation ("com.squareup.picasso:picasso:2.71828")
        }
    }
}
