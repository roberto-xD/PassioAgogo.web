# Passio Agogo

Proyecto Kotlin Multiplatform (Compose Multiplatform). El objetivo principal es la
página web (`:web`), con módulos adicionales para Android e iOS.

## Módulos

| Módulo        | En el build¹ | Estado                                                        |
|---------------|:------------:|---------------------------------------------------------------|
| `web`         | ✅           | **Target de producción.** Landing page en Compose (JS/IR).    |
| `androidApp`  | ✅           | App Android (actualmente plantilla, sin funcionalidad real).  |
| `shared`      | ✅           | Módulo compartido KMP (vacío por ahora).                      |
| `composeApp`  | ❌           | Implementación WASM del catálogo. **No incluido** en el build y aún incompleto (ver más abajo). |

¹ Según `settings.gradle.kts`.

## Requisitos

- JDK 17+
- El wrapper de Gradle descarga la versión correcta (Gradle 8.11.1).
- Las versiones de Kotlin, Compose, Ktor, etc. se gestionan de forma centralizada
  en [`gradle/libs.versions.toml`](gradle/libs.versions.toml). No dupliques versiones
  en `gradle.properties`.

## Build y ejecución (web)

```bash
# Servidor de desarrollo con recarga
./gradlew :web:jsBrowserDevelopmentRun

# Artefacto de producción (queda en web/build/dist/js/productionExecutable)
./gradlew :web:jsBrowserDistribution
```

El punto de entrada es `web/src/commonMain/kotlin/com/smartbe/web/Main.kt`
(`LandingPage`). El HTML/CSS estáticos están en `web/src/commonMain/resources/`.

## Estado del catálogo (pendiente)

La funcionalidad de catálogo (consumo de la API, tarjetas, navegación) vive en el
módulo `composeApp`, que hoy **no forma parte del build** y está incompleto:

- No está incluido en `settings.gradle.kts`.
- Su `build.gradle.kts` referencia plugins que no están declarados en el catálogo.
- `wasmJsMain/App.kt` está vacío mientras `main.kt` invoca `App()`.

Para habilitarlo hay que decidir si se recupera como módulo WASM o se porta su lógica
al módulo `web`, y luego cablear la UI al `HomeViewModel`.

## ⚠️ Seguridad

El repositorio contuvo una **API key de AWS API Gateway hardcodeada** en
`NetworkRepository`. Se removió del código fuente, pero **sigue en el historial de git
y debe rotarse** antes de exponer nada a producción. Ver
[`composeApp/.../network/ApiConfig.kt`](composeApp/src/wasmJsMain/kotlin/network/ApiConfig.kt).

En una app web/WASM cualquier secreto embebido es visible para el cliente; lo correcto
es exponer el catálogo mediante un endpoint público o un backend/proxy propio.
