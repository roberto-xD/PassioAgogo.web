# Generador del texto público

`build.py` lee los Markdown de `contenido/` y escribe las dos salidas donde ese texto
tiene que aparecer:

```
contenido/*.md  ──┬──►  sitio/                               páginas HTML
                  └──►  web/.../ui/content/Textos.kt         pantallas Compose
```

```sh
python3 tools/contenido/build.py      # sin dependencias; solo biblioteca estándar
```

## Por qué existe

La app se dibuja entera en un `<canvas>` de Skia. El HTML que entrega el servidor son
unos 4 KB con un «Cargando…», y después de ejecutar el JavaScript el DOM sigue sin un
solo carácter de texto: lo comprobamos contra producción, 0 caracteres, 0 encabezados,
0 enlaces.

Eso deja el sitio invisible para dos públicos distintos:

- **Googlebot** ejecuta el JavaScript, pero lo que obtiene son píxeles en un canvas, no
  texto.
- **Los rastreadores de las IAs de búsqueda** (GPTBot, OAI-SearchBot, ClaudeBot,
  PerplexityBot) ni siquiera ejecutan JavaScript: leen el HTML crudo y se van.

Por eso una búsqueda de la marca encontraba las redes sociales —HTML normal en sitios
ajenos— y no podía resumir ni las políticas ni nada del sitio. Las páginas de `sitio/`
son el gemelo legible, y este script evita que el texto haya que escribirlo dos veces y
se desincronice.

## Qué genera

| Salida | Qué es |
|---|---|
| `sitio/<slug>/index.html` | Una página por documento, con `<title>`, descripción, canonical, Open Graph y JSON-LD |
| `sitio/robots.txt` | Permite todo y apunta al sitemap |
| `sitio/sitemap.xml` | La portada y las cinco páginas, con su fecha de modificación |
| `web/src/wasmJsMain/kotlin/ui/content/Textos.kt` | Los mismos documentos como datos para Compose |

El JSON-LD es `OnlineStore` en todas las páginas menos en el FAQ, que emite `FAQPage`
con una entrada por cada `##`. Un bloque con una marca `⟦…⟧` sin resolver se queda fuera
de los datos estructurados: no tiene sentido publicar algo a medio decidir como un dato
que los buscadores van a citar.

## El formato del Markdown

Las convenciones están en `contenido/README.md`. Dos detalles que viven aquí:

- **`{solo-web}` y `{solo-app}`** antes de un bloque lo mandan a una sola salida. Hoy lo
  usa la lista de contacto de «Nosotros»: en la página es texto y en la app son botones
  —el correo se copia, el teléfono abre WhatsApp—, así que la pone `AboutScreen`.
- **Nada de `/*` dentro de un KDoc** si tocas el Kotlin generado a mano. Kotlin anida los
  comentarios de bloque, así que un `/*` suelto dentro de un `/** … */` se come el resto
  del archivo. Ya pasó una vez con un `contenido/*.md` escrito en una KDoc.

## El dominio

`DOMINIO`, arriba del script, es el dominio canónico y se escribe en el canonical, el
sitemap y el `robots.txt`. Las dos variantes del dominio servían el sitio con un 200, lo
que repartía las señales entre dos sitios idénticos; ahora el `.htaccess` redirige la
variante sin `www`. **Para cambiar de opinión hay que tocar los dos sitios**: esta
constante y la regla del `.htaccess`.

## Desplegar

`sitio/` se versiona ya generado, así que subirlo no necesita Python ni Gradle. En el
hosting, su contenido va en la raíz pública —`public_html/`—, junto al bundle:

```
public_html/
├── index.html, agogo.js, *.wasm, .htaccess   ← del build de Gradle
├── robots.txt
├── sitemap.xml
├── nosotros/index.html
├── preguntas-frecuentes/index.html
├── devoluciones-y-garantias/index.html
├── terminos/index.html
└── privacidad/index.html
```

No hay conflicto entre ambos: la app usa rutas con `#`, así que `/terminos/` es un
archivo de verdad y `/#/terminos` sigue siendo la pantalla de la app.

Después del primer despliegue, en Google Search Console: verificar la propiedad, enviar
`https://www.passionagogo.com/sitemap.xml` y revisar en una semana qué quedó indexado.
