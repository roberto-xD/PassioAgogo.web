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

El punto de entrada es `web/src/wasmJsMain/kotlin/com/smartbe/web/Main.kt`
(`App`). El HTML/CSS estáticos están en `web/src/wasmJsMain/resources/`.

## Despliegue

Sube **todo** el contenido de `web/build/dist/wasmJs/productionExecutable/` a la raíz
pública del sitio (en cPanel, normalmente `public_html/`). No hace falta configurar
reescrituras: la app usa hash routing (`#/catalogo`), así que el servidor solo sirve
`index.html`.

⚠️ **El servidor debe entregar los `.wasm` con el MIME `application/wasm`.** Si no, el
navegador rechaza el módulo y la página se queda en "Cargando…" con este error en
consola:

```
wasm streaming compile failed: TypeError: Failed to execute 'compile' on
'WebAssembly': Incorrect response MIME type. Expected 'application/wasm'.
```

Para Apache/cPanel esto ya está resuelto: el repo incluye
[`.htaccess`](web/src/wasmJsMain/resources/.htaccess) en `resources/`, que el build copia
a la carpeta de distribución (MIME correctos, compresión y caché). **Verifica que el
archivo llegue al servidor**: algunos clientes FTP y gestores de archivos ocultan los
archivos que empiezan con punto — activa "mostrar archivos ocultos" o súbelo a mano.

En otros hosts, configura el MIME equivalente:

| Host | Dónde |
|---|---|
| Nginx | `types { application/wasm wasm; }` o añadirlo en `mime.types` |
| Netlify | `_headers` → `/*.wasm` con `Content-Type: application/wasm` |
| Vercel | `vercel.json` → `headers` |
| IIS | `web.config` → `<staticContent><mimeMap fileExtension=".wasm" mimeType="application/wasm" /></staticContent>` |

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

La base de datos está definida por los scripts SQL `00`–`11` (catálogo, inventario,
ventas, compras, RLS y vistas), ya ejecutados en el proyecto Supabase. El catálogo
consulta `products` con embedding de PostgREST (`categories(...)` +
`product_variants(...)`); los DTOs viven en `models/PGCatalog.kt` y usan los nombres
reales de columnas (`nombre`, `precio_venta`, `imagenes`, …).

El RLS (script 10) permite al rol `anon` leer catálogo y promociones **sin login y solo
registros activos**, por lo que la web pública no necesita autenticación para mostrar
productos.

Puesta en marcha:

1. Pon `URL` y `ANON_KEY` reales en
   [`network/SupabaseConfig.kt`](web/src/wasmJsMain/kotlin/network/SupabaseConfig.kt)
   (idealmente inyectados por build, no fijos en el código).
2. Sube las imágenes de producto al bucket **público** `inventory` de Storage.
   `products.imagenes` acepta URLs absolutas o paths relativos al bucket
   (`SupabaseConfig.publicImageUrl` resuelve ambos). Datos de prueba:
   [`db/seed_dev.sql`](db/seed_dev.sql).

Mientras `SupabaseConfig` tenga los valores placeholder, `isConfigured` es `false`: el
repositorio devuelve un catálogo vacío (la UI muestra "Catálogo próximamente") y el
cliente de Supabase **no se instancia**.

Las **promociones** vigentes (script 05) se aplican al precio mostrado: el cliente
consulta promos activas dentro de su vigencia con sus targets y resuelve por
especificidad variante > producto > categoría (incluyendo subcategorías, como
`fn_promotion_variants`). Tipos soportados: `porcentaje`, `monto_fijo` y
`precio_especial`. Con oferta, la tarjeta muestra el precio final y el original tachado.

El catálogo incluye **filtro por categoría** (chips: "Todas" + categorías raíz con
productos; seleccionar una incluye sus subcategorías) y **búsqueda por texto**
(insensible a mayúsculas/acentos, sobre nombre, descripción, marca, categoría y SKU;
combinable con el filtro). Ambos operan en cliente sobre los datos ya cargados —
instantáneos — y recalculan promociones.

## Video (widget reutilizable)

Compose para web dibuja todo en un `<canvas>`, así que un `<video>` no puede vivir dentro
del árbol de composición. La solución es superponerlo:

- [`ui/components/HtmlElementView`](web/src/wasmJsMain/kotlin/ui/components/HtmlElementView.kt)
  — puente genérico Compose↔DOM: crea el elemento, lo posiciona sobre el canvas copiando
  el rectángulo que Compose midió (convirtiendo píxeles físicos a CSS) y lo elimina al
  salir. Sirve para cualquier elemento HTML: video, iframe, mapa…
- [`ui/components/VideoPlayer`](web/src/wasmJsMain/kotlin/ui/components/VideoPlayer.kt) —
  el widget: recibe la URL y los parámetros (`autoPlay`, `muted`, `loop`, `controls`,
  `posterUrl`) y toma el tamaño del `Modifier`. Al ser un `<video>` nativo, trae gratis
  los controles, el streaming por rangos y la pantalla completa.

Colócalo donde quieras:

```kotlin
VideoPlayer(
    url = MediaConfig.presentacionUrl,
    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
)
```

Hoy se usa en la sección **Video** (`#/video`); reutilizarlo en el hero de Inicio o en una
ficha de producto es solo colocar el composable.

Como el elemento vive fuera del canvas, los contenedores de Compose no lo recortan: la app
publica el área de contenido en `LocalHtmlOverlayClip` y el puente recorta contra ella, de
modo que al hacer scroll el video no se dibuja sobre la barra ni el pie.

Puesta en marcha: sube el archivo al bucket `inventory` de Storage y ajusta la ruta en
[`network/MediaConfig.kt`](web/src/wasmJsMain/kotlin/network/MediaConfig.kt)
(`video/presentacion.mp4` por defecto; también acepta una URL absoluta). Formato
recomendado: **MP4 (H.264 + AAC)**, el de mayor compatibilidad entre navegadores.

> El puente está inspirado en el `HtmlView` de
> [KMP-ShaPlayer](https://github.com/shadmanadman/KMP-ShaPlayer) (Apache 2.0), que valida
> el enfoque; la implementación es propia y solo para web.

## Contacto (formulario + anti-bots)

La pantalla **Contacto** (`#/contacto`) envía el mensaje a la Edge Function
[`supabase/functions/contact`](supabase/functions/contact/index.ts), que **valida, verifica
el captcha y limita envíos** antes de escribir con `service_role`. La tabla
[`contact_messages`](db/12_contact_messages.sql) **no tiene política de INSERT**: la
función es el único camino de entrada, así que nadie puede saltarse la verificación con
la anon key. El staff lee los mensajes y los marca como atendidos.

Anti-bots: **Cloudflare Turnstile en modo invisible** (gratis y sin tope de
verificaciones). Como la app se dibuja en un `<canvas>`, el widget no puede vivir dentro
de Compose: se monta en el DOM (`#turnstile-host`) mediante el helper `window.paTurnstile`
de [`index.html`](web/src/wasmJsMain/resources/index.html), y
[`network/Turnstile.kt`](web/src/wasmJsMain/kotlin/network/Turnstile.kt) dispara el reto y
espera el token. Normalmente el usuario no ve nada; si Cloudflare pide interacción, el
widget aparece centrado sobre el canvas.

> No se usa *honeypot*: es una técnica contra bots que rellenan formularios del DOM, y
> aquí no existe tal formulario (todo se dibuja en el canvas). La defensa efectiva es la
> del servidor: token de Turnstile + límite por origen.

Puesta en marcha:

1. Ejecuta [`db/12_contact_messages.sql`](db/12_contact_messages.sql) en el SQL Editor.
2. En [Cloudflare → Turnstile](https://dash.cloudflare.com/?to=/:account/turnstile) crea un
   widget (modo **Invisible**) con el dominio del sitio. Pon la **site key** en
   `TurnstileConfig.SITE_KEY` y guarda la **secret key** para el paso 4.
3. Despliega la función **sin verificación de JWT** — es un endpoint público que se
   llama sin sesión iniciada:
   ```bash
   supabase functions deploy contact --no-verify-jwt
   ```
   ([`supabase/config.toml`](supabase/config.toml) ya lo declara para despliegues desde
   el CLI; en el dashboard, desactiva *Verify JWT* en los ajustes de la función.)

   > Con la verificación activada, el navegador falla el preflight con
   > *"Response to preflight request doesn't pass access control check: It does not have
   > HTTP ok status"*: el `OPTIONS` de CORS no lleva el header `Authorization`, así que
   > el gateway lo rechaza antes de ejecutar la función. Desactivarla no expone datos:
   > la función valida los campos, verifica el captcha y limita los envíos por origen.
4. Configura los secrets de la función: `TURNSTILE_SECRET`, `CONTACT_IP_SALT` (cadena
   aleatoria), `CONTACT_ALLOWED_ORIGINS` (tu dominio) y opcionalmente
   `CONTACT_MAX_PER_HOUR` (default 5).

Sin `SITE_KEY` configurada el formulario envía sin token, y sin `TURNSTILE_SECRET` la
función omite la verificación: así funciona en local sin cuenta de Cloudflare. **Ambos
deben estar configurados en producción.**

El IP no se guarda: se almacena su **hash con sal** (`ip_hash`) solo para poder limitar
los envíos por origen.

## ⚠️ Seguridad

- La **`ANON_KEY` de Supabase es pública por diseño** (va en el cliente web); la
  protección de datos se hace con **RLS** en la base de datos. **Nunca** uses la
  `service_role` key en el cliente.
- **`product_variants.costo` es legible por `anon`**: RLS filtra filas, no columnas, y
  la política pública de variantes expone también el costo de compra (margen). La app
  pide columnas explícitas y nunca lo solicita, pero cualquier cliente HTTP podría.
  Recomendado ejecutar en Supabase:
  ```sql
  revoke select on product_variants from anon;
  grant select (id, product_id, sku, attributes, precio_venta, activo, created_at, updated_at)
    on product_variants to anon;
  ```
  (Tras esto, consultas `select=*` de `anon` sobre variantes fallarán; la app ya usa
  columnas explícitas.)
- Nota histórica: el repo contuvo una **API key de AWS hardcodeada** (ya eliminada del
  código). Sigue en el historial de git, así que **debe rotarse** en AWS.
