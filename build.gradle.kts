plugins {
    // Se declaran aquí (apply false) para compartir versiones entre subproyectos.
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.compose.multiplatform).apply(false)
    alias(libs.plugins.kotlin.serialization).apply(false)
}

// Fija versiones seguras de dependencias JS transitorias del toolchain de Kotlin/JS.
// Estas dependencias son de build/dev (no viajan en el bundle de produccion), pero
// Dependabot las reporta. `ws` 8.5.0 es vulnerable a CVE-2024-37890 (DoS).
// IMPORTANTE: tras cambiar esto hay que regenerar el lockfile ejecutando en local:
//   ./gradlew kotlinUpgradeYarnLock
// y commitear el kotlin-js-store/yarn.lock resultante.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>()
        .resolution("ws", "8.18.2")
}

