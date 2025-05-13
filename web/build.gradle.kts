plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "agogo.js"
            }
        }
        binaries.executable()
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val jsMain by getting {
            dependencies {
//                implementation(libs.compose.web.core)
//                implementation(libs.compose.html.core)
//                implementation(project(":shared"))
//                implementation(libs.compose.ui)
//                implementation(libs.compose.ui.tooling.preview)
//                implementation(libs.compose.material3)
//                implementation(libs.androidx.runtime.android)
//                implementation(libs.androidx.animation.core.android)
//                implementation(libs.androidx.ui.android)
//
//                // Dependencias específicas para JS (Compose Multiplatform)
                implementation(libs.jetbrains.compose.ui)
                implementation(libs.jetbrains.compose.material3)
//
//                // Coroutines
//                implementation(libs.kotlinx.coroutines.core)
//                // Koin
//                implementation(libs.koin.core)
//                implementation(libs.koin.compose)
//                // Ktor
//                implementation(libs.ktor.client.core)
//                implementation(libs.ktor.client.serialization)
//                implementation(libs.ktor.client.content.negotiation)
//                implementation(libs.ktor.client.logging)
//                implementation(libs.ktor.client.encoding)
//
                implementation(libs.coil.compose.core)
                implementation(libs.coil.compose)
                implementation(libs.coil.mp)
                implementation(libs.coil.network.ktor)

            }
        }
    }
}




