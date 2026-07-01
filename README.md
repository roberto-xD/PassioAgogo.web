# Passio Agogo

Proyecto Kotlin Multiplatform (Compose Multiplatform). El objetivo principal es la
página web (`:web`), con módulos adicionales para Android e iOS.

## Módulos

| Módulo        | En el build¹ | Estado                                                        |
|---------------|:------------:|---------------------------------------------------------------|
| `web`         | ✅           | **Target de producción.** Catálogo en Compose (JS/IR): modelos, red (Ktor), ViewModel y UI con estados. |
| `androidApp`  | ✅           | App Android (actualmente plantilla, sin funcionalidad real).  |
| `shared`      | ✅           | Módulo compartido KMP (vacío por ahora).                      |
| `composeApp`  | ❌           | Implementación WASM previa. **No incluido** en el build; su lógica se portó a `web`. Candidato a eliminarse. |

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
(`App`). El HTML/CSS estáticos están en `web/src/commonMain/resources/`.

## Catálogo

El módulo `web` implementa el flujo completo del catálogo:

- `models/` — DTOs serializables (`PGCatalog`, `PGCatalogItem`).
- `network/` — cliente Ktor, `CatalogRepository` y `ApiConfig`.
- `viewmodel/CatalogViewModel` — expone `CatalogUiState` (carga / error / productos).
- `ui/CatalogScreen` + `ui/ProductCard` — UI con grid adaptable y estados vacíos.

Mientras `ApiConfig.API_KEY` esté vacía, el repositorio devuelve un catálogo vacío y la
UI muestra "Catálogo próximamente" en lugar de fallar. Al proveer la clave, la pantalla
carga los productos reales.

Pendientes conocidos:

- **Imágenes**: `ProductCard` usa un placeholder. Falta integrar carga remota (Coil)
  una vez validada en el target JS.
- **`composeApp`**: su lógica ya se portó aquí; puede archivarse/eliminarse.

## ⚠️ Seguridad

El repositorio contuvo una **API key de AWS API Gateway hardcodeada** en el código de
red. Se removió del código fuente, pero **sigue en el historial de git y debe rotarse**
antes de exponer nada a producción. Ver
[`web/.../network/ApiConfig.kt`](web/src/commonMain/kotlin/network/ApiConfig.kt).

En una app web cualquier secreto embebido es visible para el cliente; lo correcto es
exponer el catálogo mediante un endpoint público o un backend/proxy propio, o inyectar
la clave en tiempo de build.
