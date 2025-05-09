plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // ... otras dependencias
    }
}

allprojects {
    repositories {
        google()
        mavenCentral() // Agrega esta línea
        // Si usas un repositorio específico de JetBrains, podrías agregar algo como:
        // maven("https://maven.pkg.jetbrains.space/public/p/kotlin/p/runtime")
    }
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "app.js"
            }
        }
        binaries.executable()
    }
    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(libs.compose.ui)
                implementation(libs.compose.ui.tooling.preview)
                implementation(libs.compose.material3)
                implementation(libs.androidx.runtime.android)

                // Dependencias específicas para JS (Compose Multiplatform)
                implementation(libs.jetbrains.compose.ui)
                implementation(libs.jetbrains.compose.material3)

                // Coroutines
                implementation(libs.kotlinx.coroutines.core)
                // Koin
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                // Ktor
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.serialization)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.client.encoding)

                implementation(libs.coil.compose.core)
                implementation(libs.coil.compose)
                implementation(libs.coil.mp)
                implementation(libs.coil.network.ktor)

            }
        }
    }
}
