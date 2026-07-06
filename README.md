# Passio Agogo

Aplicación **web** hecha con Compose Multiplatform (target wasmJs). Es un proyecto
de un único módulo, `:web`.

## Módulo

| Módulo | Estado                                                                                   |
|--------|------------------------------------------------------------------------------------------|
| `web`  | **Target de producción.** Catálogo en Compose (wasmJs): modelos, red (Ktor), ViewModel y UI con estados e imágenes (Coil). |

## Requisitos

- JDK 17+
- El wrapper de Gradle descarga la versión correcta (Gradle 8.11.1).
- Las versiones de Kotlin, Compose, Ktor, etc. se gestionan de forma centralizada
  en [`gradle/libs.versions.toml`](gradle/libs.versions.toml). No dupliques versiones
  en `gradle.properties`.

## Build y ejecución (web)

El módulo `web` usa el target **wasmJs** (Compose para Web con Skia/skiko), que es
el camino soportado y estable para Compose UI en el navegador. Requiere un navegador
moderno con WebAssembly GC (Chrome/Edge 119+, Firefox 120+, Safari 18+).

```bash
# Servidor de desarrollo con recarga
./gradlew :web:wasmJsBrowserDevelopmentRun

# Artefacto de producción (queda en web/build/dist/wasmJs/productionExecutable)
./gradlew :web:wasmJsBrowserDistribution
```

> Nota de hosting: sirve los `.wasm` con el MIME `application/wasm`.

El punto de entrada es `web/src/wasmJsMain/kotlin/com/smartbe/web/Main.kt`
(`App`). El HTML/CSS estáticos están en `web/src/wasmJsMain/resources/`.

## Catálogo

El módulo `web` implementa el flujo completo del catálogo:

- `models/` — DTOs (`PGCatalog`, `PGCatalogItem` serializable con `@SerialName`).
- `network/` — `SupabaseConfig`, `SupabaseClientProvider` y `CatalogRepository`.
- `viewmodel/CatalogViewModel` — expone `CatalogUiState` (carga / error / productos).
- `ui/CatalogScreen` + `ui/ProductCard` — UI con grid adaptable, estados vacíos e
  imágenes remotas vía Coil (con placeholder cuando no hay URL).

## Navegación

Landing multipágina con navegación propia (sin Navigation Compose, para evitar
dependencias con versiones acopladas a Compose 1.7.3):

- `ui/navigation/Screen` — enum de pantallas (Inicio, Catálogo, Nosotros, Términos,
  Privacidad, Ayuda) con su ruta.
- `ui/navigation/rememberScreenState` — estado de la pantalla **sincronizado con el hash
  de la URL** (`#/inicio`, `#/catalogo`, …): enlaces compartibles y botón atrás/adelante
  del navegador funcionando.
- `ui/components/NavBar` (barra superior) y `ui/components/Footer` (enlaces legales).
- `ui/screens/` — pantallas de contenido; los textos de Términos y Privacidad son
  **placeholder** y deben reemplazarse por el contenido legal real.

Migrar a `org.jetbrains.androidx.navigation:navigation-compose` más adelante es directo
si se necesitan rutas anidadas o argumentos complejos.

## Backend: Supabase

Se usa el SDK oficial [supabase-kt](https://github.com/supabase-community/supabase-kt).
El cliente (`SupabaseClientProvider`) instala **Postgrest** (datos), **Storage**
(imágenes), **Auth** y **Realtime** — estos dos últimos quedan listos para sesiones de
usuario y suscripciones en tiempo real a futuro.

Puesta en marcha:

1. Crea el proyecto en Supabase y pon `URL` y `ANON_KEY` reales en
   [`network/SupabaseConfig.kt`](web/src/wasmJsMain/kotlin/network/SupabaseConfig.kt)
   (idealmente inyectados por build, no fijos en el código).
2. Crea la tabla del catálogo y **activa RLS** con una política de lectura pública:
   ```sql
   alter table products enable row level security;
   create policy "lectura publica" on products for select to anon using (true);
   ```
3. Ajusta los nombres de columnas a los `@SerialName` de `PGCatalogItem` (o al revés).
4. Sube las imágenes a un bucket **público** de Storage y guarda su URL pública en la
   columna `image` (Coil las carga directamente).

Mientras `SupabaseConfig` tenga los valores placeholder, `isConfigured` es `false`: el
repositorio devuelve un catálogo vacío (la UI muestra "Catálogo próximamente") y el
cliente de Supabase **no se instancia**.

## ⚠️ Seguridad

- La **`ANON_KEY` de Supabase es pública por diseño** (va en el cliente web); la
  protección de datos se hace con **RLS** en la base de datos. **Nunca** uses la
  `service_role` key en el cliente.
- Nota histórica: el repo contuvo una **API key de AWS hardcodeada** (ya eliminada del
  código). Sigue en el historial de git, así que **debe rotarse** en AWS.
