# Passion à gogo

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
- `ui/screens/` — pantallas de contenido.

### Textos legales

Términos y Privacidad están redactados a partir del funcionamiento real del sitio: la
privacidad describe exactamente qué se recoge en el formulario de contacto (nombre, correo,
mensaje y un hash con sal del IP, nunca el IP en claro) y los terceros implicados
(Supabase, Cloudflare Turnstile y el contenido incrustado de Spotify).

Antes de publicarlos:

1. Sustituye los marcadores `[RAZÓN SOCIAL]`, `[DOMICILIO]`, `[CORREO DE CONTACTO]` y
   `[CIUDAD, ESTADO/PAÍS]`.
2. **Que los revise un profesional legal**: el texto es una base sólida, no asesoría
   jurídica.
3. Actualiza la fecha de "Última actualización" al publicar.

Los términos incluyen la **restricción de acceso a mayores de 18 años** (sección 3), por
tratarse de una tienda para adultos. Esa cláusula *declara* el requisito pero **no lo
verifica**: si tu normativa exige comprobación efectiva de la edad, hace falta además una
pantalla de verificación al entrar (pendiente, decidido posponerla).

Si más adelante se añaden analítica, cuentas de usuario o nuevos servicios de terceros, hay
que actualizar la política de privacidad.

### Icono del sitio

El favicon se declara en [`index.html`](web/src/wasmJsMain/resources/index.html) y apunta a
`favicon.svg` (marca de texto con el degradado de la marca). Reemplázalo por el logotipo
real cuando esté disponible; para máxima compatibilidad con Safari antiguo y accesos
directos móviles, añade además un PNG de 180×180 y decláralo junto al SVG.

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

## Widgets multimedia (video y Spotify)

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

Puesta en marcha: sube el archivo a un bucket **público** de Storage y ajusta
[`network/MediaConfig.kt`](web/src/wasmJsMain/kotlin/network/MediaConfig.kt) —
`MEDIA_BUCKET` (por defecto el mismo de las imágenes, `inventory`) y `PRESENTACION_PATH`
(`video/presentacion.mp4`); también acepta una URL absoluta. Formato recomendado:
**MP4 (H.264 + AAC)**, el de mayor compatibilidad entre navegadores.

Si el video no carga, comprueba la URL a mano en el navegador: es
`https://<proyecto>.supabase.co/storage/v1/object/public/<MEDIA_BUCKET>/<PRESENTACION_PATH>`.
Un 400/404 significa que el bucket o la ruta no coinciden —ojo con confundir *una carpeta
llamada `video` dentro del bucket* con *un bucket llamado `video`*—; si en cambio el
archivo se descarga pero no se reproduce, suele ser el códec (H.265/HEVC no es compatible
con la mayoría de navegadores).

> El puente está inspirado en el `HtmlView` de
> [KMP-ShaPlayer](https://github.com/shadmanadman/KMP-ShaPlayer) (Apache 2.0), que valida
> el enfoque; la implementación es propia y solo para web.

### Carrusel de la galería

[`ui/components/ImageCarousel`](web/src/wasmJsMain/kotlin/ui/components/ImageCarousel.kt) es
la tira de imágenes de la portada (eventos, promociones y novedades):

- **Automático**: avanza a velocidad constante, fotograma a fotograma.
- **Manual**: se arrastra con el ratón o el dedo, y las flechas laterales avanzan una
  tarjeta. El avance automático se detiene mientras el usuario arrastra.
- **Al pasar el cursor**: se detiene, la tarjeta crece un 12 % y se dibuja **por encima**
  de las demás (`zIndex`), revelando la descripción completa. El aumento usa
  `graphicsLayer`, que no afecta a la medición, así que la tarjeta crece sobre sus vecinas
  en lugar de empujarlas; por eso la fila reserva algo más de alto del que ocupa la tarjeta.
- La lista es **circular**: se recorren `Int.MAX_VALUE` posiciones tomando el elemento por
  módulo, arrancando por la mitad para poder arrastrar también hacia atrás.

Contenido: tabla [`gallery_items`](db/13_gallery.sql). Sube las imágenes al bucket público
y guarda su ruta relativa (o una URL absoluta) en la columna `imagen`; `orden` decide la
posición y `activo` permite prepararlas antes de publicarlas. El RLS deja **leer los
elementos activos sin sesión** y reserva la escritura a la administración.

El carrusel vive hoy en Inicio, bajo el hero, pero recibe la lista por parámetro: moverlo a
otra pantalla es colocar el composable.

### Spotify

[`ui/components/SpotifyEmbed`](web/src/wasmJsMain/kotlin/ui/components/SpotifyEmbed.kt) es
el segundo widget sobre el mismo puente — el reproductor oficial de Spotify es un
`<iframe>`, así que no hizo falta nada nuevo:

```kotlin
SpotifyEmbed(
    content = SpotifyContent.Show,   // track | album | playlist | artist | show | episode
    id = MediaConfig.PODCAST_SHOW_ID,
    modifier = Modifier.fillMaxWidth().height(352.dp),  // 152.dp = versión compacta
)
```

Se usa en la sección **Podcast** (`#/podcast`). Configura el show en
[`MediaConfig.PODCAST_SHOW_ID`](web/src/wasmJsMain/kotlin/network/MediaConfig.kt) con el
código del enlace de *Compartir → Copiar enlace* de Spotify.

⚠️ **Reproducción limitada por Spotify**: los visitantes anónimos o con cuenta gratuita
solo escuchan **avances de ~30 segundos**. La reproducción completa exige Premium con
sesión iniciada y, aun así, Spotify la restringe en iframes de dominios externos. Por eso
la pantalla acompaña el reproductor con un botón **Abrir en Spotify**, donde el usuario sí
puede escuchar completo y seguir el podcast de forma fiable.

Al ser un embed de terceros con cookies de Spotify, conviene declararlo en la política de
privacidad.

> **Detalle de implementación**: el navegador fija los permisos que delega a un `<iframe>`
> **cuando este navega**, leyendo el atributo `allow` que exista en ese instante. Por eso
> los atributos se ponen al crear el elemento y `src` se asigna al final. Si se asignara
> `src` primero, la consola mostraría
> `Permissions policy violation: encrypted-media is not allowed in this document` y el
> reproductor quedaría bloqueado. El mismo orden aplica al `<video>`: sin `muted` presente
> antes de la fuente, el navegador bloquea el `autoPlay`.

## Tema (colores, tipografía y espaciado)

Todo el estilo vive en [`ui/theme/`](web/src/wasmJsMain/kotlin/ui/theme): las pantallas no
declaran ni un solo color literal.

- `Color.kt` — rampas de marca (morado, rosa, neutros cálidos), alias semánticos y los dos
  esquemas de Material 3. El oscuro se deriva de las mismas rampas.
  `PassionSemantics` cubre lo que Material no modela: degradado del fondo, superficie de
  las barras, acento de oferta, placeholder de imagen…
- `Type.kt` — escala tipográfica completa con **Poppins**, y `scriptAccent` con
  **Alex Brush**, reservado al logotipo (se usa en el hero de Inicio).
- `Dimens.kt` — radios, `Shapes` de Material y escala de espaciado de 4dp.
- `Theme.kt` — `PassionAGogoTheme` y la configuración.

### Cambiar de tema

Un único punto de decisión, en
[`ThemeConfig`](web/src/wasmJsMain/kotlin/ui/theme/Theme.kt):

```kotlin
object ThemeConfig {
    val MODE: ThemeMode = ThemeMode.Light   // Light | Dark | System
}
```

`System` sigue la preferencia del sistema operativo del visitante. Si cambias el valor por
defecto a `Dark`, actualiza también el fondo de `html, body` en
[`styles.css`](web/src/wasmJsMain/resources/styles.css) y el `theme-color` de
[`index.html`](web/src/wasmJsMain/resources/index.html): son la pantalla de carga previa a
que Compose monte, y si no coinciden se ve un destello al arrancar.

### Uso desde las pantallas

```kotlin
MaterialTheme.colorScheme.primary      // roles de Material (botones, campos, tarjetas)
MaterialTheme.typography.bodySmall     // escala tipográfica
PassionTheme.semantics.offer           // tokens propios
PassionTheme.spacing.s4                // espaciado
MaterialTheme.shapes.extraLarge        // radios (pill)
```

Los componentes de Material toman sus colores del esquema, así que ya no hace falta
pasarles `colors = ...` salvo excepciones justificadas (por ejemplo el verde corporativo de
Spotify, que es de un tercero y no cambia con el tema).

### Fuentes

Poppins y Alex Brush se cargan con Compose Resources desde
`web/src/commonMain/composeResources/font/`. Solo se incluyen los pesos que usa la escala
(regular, medium, semibold, bold) para no inflar el bundle; ambas son **SIL Open Font
License** y sus licencias se distribuyen en `composeResources/files/`.

## Acceso de usuarios (Supabase Auth)

Registro e inicio de sesión con correo y contraseña sobre la tabla `profiles` (script 02):

- [`network/AuthRepository`](web/src/wasmJsMain/kotlin/network/AuthRepository.kt) —
  `signIn`, `signUp`, `signOut`, estado de sesión y lectura del perfil. Al registrarse
  envía el nombre en los metadatos del alta, que es de donde el trigger `handle_new_user`
  lo toma para crear la fila de `profiles`.
- [`viewmodel/AuthViewModel`](web/src/wasmJsMain/kotlin/viewmodel/AuthViewModel.kt) —
  formulario, validación y errores traducidos a mensajes entendibles.
- `ui/screens/LoginScreen` (`#/acceder`, alterna entre entrar y crear cuenta) y
  `ui/screens/AccountScreen` (`#/cuenta`, datos del perfil y cierre de sesión). La barra
  superior muestra **Acceder** o **Mi cuenta** según haya sesión.

La sesión la **persiste el propio SDK** en el almacenamiento del navegador y la restaura al
recargar la página: no se guarda ningún token a mano.

Notas de configuración en Supabase → Authentication:

- Si **Confirm email** está activo (valor por defecto), el alta no abre sesión hasta que el
  usuario confirme el correo; la pantalla lo indica tras registrarse.
- Todo usuario nuevo se crea con rol `cliente`. Los roles `admin` y `vendedor` se asignan
  desde la base de datos: el trigger `fn_profiles_guard` (script 09) impide que alguien se
  autopromueva.
- El catálogo sigue siendo público: el acceso no es necesario para navegar, y hoy la web no
  expone funciones exclusivas de staff.

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

- **Inyección SQL: cubierta por arquitectura, no por filtrado.** Los datos viajan como
  JSON a PostgREST, que ejecuta **consultas parametrizadas**: los valores nunca se
  concatenan en la instrucción SQL. El formulario inserta con
  `.insert({ nombre, email, mensaje, ip_hash })` y filtra con `.eq(...)` / `.gte(...)`,
  siempre por parámetros; en el catálogo, además, **ningún dato del usuario llega a la
  base** (búsqueda y filtro operan en cliente sobre la lista ya cargada).
  Para que siga siendo cierto: **nunca construyas filtros concatenando entrada del
  usuario** (p. ej. `or("nombre.eq." + entrada)`) ni SQL dinámico en funciones `rpc`.
- Riesgos vecinos a tener en cuenta **al construir el panel de administración** que lea
  `contact_messages`: el contenido lo escribe un desconocido, así que (a) escápalo al
  mostrarlo si el panel es HTML —en Compose, `Text` no interpreta marcado, así que no
  aplica—, y (b) si exportas a CSV/Excel, antepón un apóstrofo a los valores que empiecen
  por `=`, `+`, `-` o `@` para evitar que se ejecuten como fórmulas.
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
