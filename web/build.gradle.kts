import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // Compose para Web se ejecuta sobre el target wasmJs (Skia/skiko). El target
    // js(IR) no carga el runtime de skiko de forma fiable, por eso se usa wasmJs.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "agogo"
        browser {
            commonWebpackConfig {
                outputFileName = "agogo.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)

                // Backend remoto: SDK oficial de Supabase (datos, auth, realtime, storage).
                implementation(project.dependencies.platform(libs.supabase.bom))
                implementation(libs.supabase.postgrest)
                implementation(libs.supabase.auth)
                implementation(libs.supabase.realtime)
                implementation(libs.supabase.storage)

                // Motor Ktor requerido por Supabase y por Coil en el target JS/Wasm.
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.js)

                // Carga de imágenes remotas (Coil sobre Ktor).
                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor)
            }
        }
    }
}
