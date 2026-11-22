#!/usr/bin/env python3
"""Genera, desde `contenido/*.md`, las dos salidas del texto público del sitio.

    contenido/*.md  ──┬──►  sitio/            páginas HTML que leen Google y las IAs
                      └──►  Textos.kt         texto para las pantallas Compose

Uso:  python3 tools/contenido/build.py     (sin dependencias externas)

Existe porque la app se dibuja entera en un <canvas>: el HTML que entrega el
servidor no contiene una sola palabra, así que ningún rastreador puede leer las
políticas ni el resto del contenido. Las páginas de `sitio/` son el gemelo
legible, y este script evita que el texto haya que escribirlo dos veces.
"""
import html
import os
import re
import sys
from datetime import date

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.abspath(os.path.join(AQUI, "..", ".."))
ORIGEN = os.path.join(RAIZ, "contenido")
SALIDA_SITIO = os.path.join(RAIZ, "sitio")
SALIDA_KT = os.path.join(
    RAIZ, "web/src/wasmJsMain/kotlin/ui/content/Textos.kt"
)

# ---------------------------------------------------------------------------
# Configuración del sitio
# ---------------------------------------------------------------------------

# El dominio canónico. Las dos variantes sirven el sitio hoy, lo que parte las
# señales en dos; el .htaccess redirige la otra aquí. Para cambiar de opinión
# basta editar esta línea y la regla del .htaccess.
DOMINIO = "https://www.passionagogo.com"

MARCA = "Passion à gogo"
CORREO = "contacto@passionagogo.com"
WHATSAPP = "+525513878451"
# Los perfiles. Van al `sameAs` del JSON-LD y al pie de cada página: son lo que
# le dice a un buscador que esta web y esas cuentas son la misma marca, en vez
# de tres cosas sueltas que casualmente se llaman igual.
#
# Ojo con el nombre: las cuentas son «passioagogo» y el dominio
# «passionagogo.com», con una n de diferencia. No es una errata de aquí.
INSTAGRAM = "https://www.instagram.com/passioagogo"
FACEBOOK = "https://www.facebook.com/passioagogo"

# Orden del menú de las páginas estáticas.
ORDEN = [
    "nosotros",
    "preguntas-frecuentes",
    "devoluciones-y-garantias",
    "terminos",
    "privacidad",
]

# Prioridad en el sitemap. La portada va aparte, en 1.0.
PRIORIDAD = {
    "nosotros": "0.8",
    "preguntas-frecuentes": "0.8",
    "devoluciones-y-garantias": "0.7",
    "terminos": "0.4",
    "privacidad": "0.4",
}


# ---------------------------------------------------------------------------
# Lectura del Markdown
# ---------------------------------------------------------------------------

class Bloque:
    """Un trozo de documento, con el destino al que puede ir.

    `destino` vale "ambos", "web" o "app". Lo pone una directiva `{solo-web}`
    o `{solo-app}` en la línea anterior, y existe por un caso concreto: la
    lista de contacto de «Nosotros» se imprime en la página, pero en la app la
    dibuja `ContactRow`, que además copia el correo al portapapeles.
    """

    def __init__(self, tipo, texto, extra=None, destino="ambos"):
        self.tipo = tipo      # titulo | parrafo | vineta | numero | tarjeta
        self.texto = texto
        self.extra = extra    # cuerpo de la tarjeta
        self.destino = destino


def leer(ruta):
    with open(ruta, encoding="utf-8") as f:
        crudo = f.read()

    if not crudo.startswith("---\n"):
        raise SystemExit(f"{ruta}: falta el encabezado de metadatos")
    _, cabecera, cuerpo = crudo.split("---\n", 2)

    meta = {}
    for linea in cabecera.strip().splitlines():
        clave, _, valor = linea.partition(":")
        meta[clave.strip()] = valor.strip()

    for obligatoria in ("slug", "titulo", "title_tag", "descripcion", "actualizado"):
        if obligatoria not in meta:
            raise SystemExit(f"{ruta}: falta «{obligatoria}» en el encabezado")

    return meta, bloques_de(cuerpo, ruta)


def bloques_de(cuerpo, ruta):
    """Parte el cuerpo en bloques.

    Markdown de verdad no hace falta: aquí solo se usan encabezados, párrafos
    y listas, porque todo tiene que poder dibujarse también en Compose, donde
    no hay tablas ni HTML.
    """
    bloques = []
    destino = "ambos"
    parrafo = []
    # Una línea suelta continúa el bloque anterior solo si viene pegada a él.
    # Sin esto, el párrafo que sigue a una lista —separado por una línea en
    # blanco— se metía dentro de la última viñeta y desaparecía como párrafo.
    tras_blanco = True

    def cerrar_parrafo():
        nonlocal parrafo, destino
        if parrafo:
            bloques.append(Bloque("parrafo", " ".join(parrafo), destino=destino))
            parrafo = []
            destino = "ambos"

    for linea in cuerpo.splitlines():
        pelada = linea.strip()

        if not pelada:
            cerrar_parrafo()
            tras_blanco = True
            continue

        venia_de_blanco, tras_blanco = tras_blanco, False

        if pelada in ("{solo-web}", "{solo-app}"):
            cerrar_parrafo()
            destino = pelada[6:-1]
            continue

        if pelada.startswith("### "):
            cerrar_parrafo()
            # El cuerpo de la tarjeta es el párrafo siguiente: se rellena abajo.
            bloques.append(Bloque("tarjeta", pelada[4:], extra="", destino=destino))
            destino = "ambos"
            continue

        if pelada.startswith("## "):
            cerrar_parrafo()
            bloques.append(Bloque("titulo", pelada[3:], destino=destino))
            destino = "ambos"
            continue

        if pelada.startswith("#"):
            raise SystemExit(f"{ruta}: el título del documento va en el encabezado, no en el cuerpo")

        if pelada.startswith("- "):
            cerrar_parrafo()
            bloques.append(Bloque("vineta", pelada[2:], destino=destino))
            destino = "ambos"
            continue

        numerada = re.match(r"^(\d+)\.\s+(.*)$", pelada)
        if numerada:
            cerrar_parrafo()
            bloques.append(
                Bloque("numero", numerada.group(2), extra=numerada.group(1), destino=destino)
            )
            destino = "ambos"
            continue

        # Continuación: una viñeta o una tarjeta partida en varias líneas
        # pertenece al bloque anterior, no abre uno nuevo.
        if not parrafo and not venia_de_blanco and bloques and bloques[-1].tipo in (
            "vineta", "numero"
        ):
            bloques[-1].texto += " " + pelada
            continue
        if not parrafo and bloques and bloques[-1].tipo == "tarjeta" and not bloques[-1].extra:
            # El cuerpo de la tarjeta sí llega tras una línea en blanco: es el
            # párrafo que sigue al `###`, y solo el primero.
            bloques[-1].extra = pelada
            continue
        if not parrafo and not venia_de_blanco and bloques and bloques[-1].tipo == "tarjeta":
            bloques[-1].extra = (bloques[-1].extra + " " + pelada).strip()
            continue

        parrafo.append(pelada)

    cerrar_parrafo()
    return bloques


# ---------------------------------------------------------------------------
# Salida HTML
# ---------------------------------------------------------------------------

def inline_html(texto):
    """`**fuerte**` → <strong>, y el resto escapado."""
    partes = re.split(r"\*\*(.+?)\*\*", texto)
    salida = []
    for i, parte in enumerate(partes):
        if i % 2:
            salida.append(f"<strong>{html.escape(parte)}</strong>")
        else:
            salida.append(enlazar(html.escape(parte)))
    return "".join(salida)


def enlazar(escapado):
    """Convierte el correo en un enlace. El teléfono no: va en el pie."""
    return escapado.replace(
        CORREO, f'<a href="mailto:{CORREO}">{CORREO}</a>'
    )


def cuerpo_html(bloques):
    salida = []
    lista = None          # "ul" | "ol" | None
    tarjetas_abiertas = False

    def cerrar():
        nonlocal lista, tarjetas_abiertas
        if lista:
            salida.append(f"</{lista}>")
            lista = None
        if tarjetas_abiertas:
            salida.append("</div>")
            tarjetas_abiertas = False

    for b in bloques:
        if b.destino == "app":
            continue

        if b.tipo == "titulo":
            cerrar()
            salida.append(f"<h2>{inline_html(b.texto)}</h2>")
        elif b.tipo == "parrafo":
            cerrar()
            salida.append(f"<p>{inline_html(b.texto)}</p>")
        elif b.tipo == "tarjeta":
            if lista:
                cerrar()
            if not tarjetas_abiertas:
                salida.append('<div class="tarjetas">')
                tarjetas_abiertas = True
            salida.append(
                "<section class=\"tarjeta\">"
                f"<h3>{inline_html(b.texto)}</h3>"
                f"<p>{inline_html(b.extra)}</p>"
                "</section>"
            )
        elif b.tipo in ("vineta", "numero"):
            quiere = "ul" if b.tipo == "vineta" else "ol"
            if tarjetas_abiertas:
                cerrar()
            if lista != quiere:
                if lista:
                    salida.append(f"</{lista}>")
                salida.append(f"<{quiere}>")
                lista = quiere
            salida.append(f"<li>{inline_html(b.texto)}</li>")

    cerrar()
    return "\n".join(salida)


def redes_html():
    """Los perfiles, enlazados de verdad en el pie.

    El `sameAs` del JSON-LD por sí solo es una afirmación; un enlace que se
    puede seguir es lo que de verdad une la web con las cuentas.
    """
    enlaces = []
    if INSTAGRAM:
        enlaces.append(f'<a href="{INSTAGRAM}" rel="me">Instagram</a>')
    if FACEBOOK:
        enlaces.append(f'<a href="{FACEBOOK}" rel="me">Facebook</a>')
    if not enlaces:
        return ""
    return " Síguenos en " + " y ".join(enlaces) + "."


def jsonld_organizacion():
    same_as = [u for u in (INSTAGRAM, FACEBOOK) if u]
    campos = [
        '"@context": "https://schema.org"',
        '"@type": "OnlineStore"',
        f'"name": "{MARCA}"',
        f'"url": "{DOMINIO}/"',
        f'"logo": "{DOMINIO}/favicon.svg"',
        f'"email": "{CORREO}"',
        f'"telephone": "{WHATSAPP}"',
        '"currenciesAccepted": "MXN"',
        '"areaServed": {"@type": "Country", "name": "México"}',
        '"address": {"@type": "PostalAddress", "addressLocality": "Ciudad de México", '
        '"addressCountry": "MX"}',
    ]
    if same_as:
        campos.append('"sameAs": [' + ", ".join(f'"{u}"' for u in same_as) + "]")
    return "{\n    " + ",\n    ".join(campos) + "\n  }"


def jsonld_faq(bloques):
    """`FAQPage` a partir de los `##` del FAQ y el texto que sigue a cada uno."""
    pares = []
    pregunta = None
    respuesta = []
    for b in bloques:
        if b.destino == "app":
            continue
        if b.tipo == "titulo":
            if pregunta and respuesta:
                pares.append((pregunta, " ".join(respuesta)))
            pregunta, respuesta = b.texto, []
        elif pregunta and b.tipo in ("parrafo", "vineta", "numero"):
            respuesta.append(re.sub(r"\*\*(.+?)\*\*", r"\1", b.texto))
    if pregunta and respuesta:
        pares.append((pregunta, " ".join(respuesta)))

    entradas = []
    for p, r in pares:
        if "⟦" in p or "⟦" in r:
            continue  # nada a medio decidir en los datos estructurados
        entradas.append(
            '{"@type": "Question", "name": %s, "acceptedAnswer": '
            '{"@type": "Answer", "text": %s}}' % (json_txt(p), json_txt(r))
        )
    if not entradas:
        return None
    return (
        '{\n    "@context": "https://schema.org",\n    "@type": "FAQPage",'
        '\n    "mainEntity": [\n      ' + ",\n      ".join(entradas) + "\n    ]\n  }"
    )


def json_txt(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


PLANTILLA = """<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title_tag}</title>
<meta name="description" content="{descripcion}">
<link rel="canonical" href="{url}">
<meta name="theme-color" content="#FBF6FA">
<meta name="rating" content="adult">
<link rel="icon" type="image/svg+xml" href="/favicon.svg">
<meta property="og:type" content="website">
<meta property="og:site_name" content="{marca}">
<meta property="og:locale" content="es_MX">
<meta property="og:title" content="{title_tag}">
<meta property="og:description" content="{descripcion}">
<meta property="og:url" content="{url}">
<meta property="og:image" content="{dominio}/favicon.svg">
<meta name="twitter:card" content="summary">
<meta name="twitter:title" content="{title_tag}">
<meta name="twitter:description" content="{descripcion}">
<script type="application/ld+json">
{jsonld}
</script>
<style>
:root {{
  --fondo: #FBF6FA; --papel: #FFFDFE; --tinta: #2B1F29; --suave: #5B4A58;
  --morado: #8E22BB; --rosa: #E8317E; --borde: #F4E3F8;
}}
* {{ box-sizing: border-box; }}
body {{
  margin: 0; background: var(--fondo); color: var(--tinta);
  font: 17px/1.65 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}}
a {{ color: var(--morado); }}
header.sitio {{
  background: var(--papel); border-bottom: 1px solid var(--borde);
  padding: 14px 20px; display: flex; flex-wrap: wrap; gap: 12px;
  align-items: center; justify-content: space-between;
}}
header.sitio .marca {{
  font-size: 19px; font-weight: 700; color: var(--morado); text-decoration: none;
}}
header.sitio nav {{ display: flex; flex-wrap: wrap; gap: 14px; font-size: 15px; }}
main {{ max-width: 760px; margin: 0 auto; padding: 32px 20px 56px; }}
h1 {{ font-size: 30px; line-height: 1.2; margin: 0 0 6px; }}
h2 {{ font-size: 21px; margin: 34px 0 10px; }}
h3 {{ font-size: 17px; margin: 0 0 6px; }}
.fecha {{ color: var(--suave); font-size: 14px; margin: 0 0 28px; }}
ul, ol {{ padding-left: 22px; }}
li {{ margin: 6px 0; }}
.tarjetas {{
  display: grid; gap: 14px; margin: 18px 0;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
}}
.tarjeta {{
  background: var(--papel); border: 1px solid var(--borde);
  border-radius: 14px; padding: 16px;
}}
.tarjeta p {{ margin: 0; color: var(--suave); font-size: 15px; }}
.cta {{
  display: inline-block; margin: 28px 0 0; padding: 13px 22px;
  background: var(--morado); color: #fff; text-decoration: none;
  border-radius: 999px; font-weight: 600;
}}
footer.sitio {{
  border-top: 1px solid var(--borde); background: var(--papel);
  padding: 26px 20px; font-size: 14px; color: var(--suave);
}}
footer.sitio nav {{ display: flex; flex-wrap: wrap; gap: 14px; margin-bottom: 12px; }}
@media (max-width: 480px) {{
  main {{ padding: 24px 16px 44px; }}
  h1 {{ font-size: 26px; }}
}}
</style>
</head>
<body>
<header class="sitio">
  <a class="marca" href="/">{marca}</a>
  <nav>{menu}</nav>
</header>
<main>
<h1>{titulo}</h1>
<p class="fecha">Última actualización: {fecha_larga}</p>
{cuerpo}
<a class="cta" href="/#/{ruta_app}">Ver en la tienda</a>
</main>
<footer class="sitio">
  <nav>{menu}</nav>
  <p>{marca} · Tienda de bienestar sexual para personas mayores de 18 años ·
  Envíos discretos a todo México desde la Ciudad de México.</p>
  <p>Escríbenos a <a href="mailto:{correo}">{correo}</a> o por
  <a href="https://wa.me/{whatsapp_url}">WhatsApp</a>.{redes}</p>
</footer>
</body>
</html>
"""

MESES = [
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
]


def fecha_larga(iso):
    a, m, d = (int(x) for x in iso.split("-"))
    return f"{d} de {MESES[m - 1]} de {a}"


def menu_html(docs, actual):
    piezas = []
    for slug in ORDEN:
        doc = docs.get(slug)
        if not doc:
            continue
        titulo = html.escape(doc[0].get("menu") or doc[0]["titulo"])
        if slug == actual:
            piezas.append(f"<span>{titulo}</span>")
        else:
            piezas.append(f'<a href="/{slug}/">{titulo}</a>')
    return "\n    ".join(piezas)


def escribir_paginas(docs):
    for slug, (meta, bloques) in docs.items():
        jsonld = jsonld_faq(bloques) if slug == "preguntas-frecuentes" else None
        if jsonld is None:
            jsonld = jsonld_organizacion()

        pagina = PLANTILLA.format(
            title_tag=html.escape(meta["title_tag"]),
            descripcion=html.escape(meta["descripcion"]),
            titulo=html.escape(meta["titulo"]),
            url=f"{DOMINIO}/{slug}/",
            dominio=DOMINIO,
            marca=html.escape(MARCA),
            correo=CORREO,
            whatsapp_url=WHATSAPP.lstrip("+"),
            redes=redes_html(),
            menu=menu_html(docs, slug),
            cuerpo=cuerpo_html(bloques),
            ruta_app=meta.get("ruta_app", ""),
            fecha_larga=fecha_larga(meta["actualizado"]),
            jsonld=jsonld,
        )
        destino = os.path.join(SALIDA_SITIO, slug)
        os.makedirs(destino, exist_ok=True)
        with open(os.path.join(destino, "index.html"), "w", encoding="utf-8") as f:
            f.write(pagina)
        print(f"  sitio/{slug}/index.html   {len(pagina.encode()):>6} bytes")


def escribir_robots():
    cuerpo = (
        "# Passion à gogo\n"
        "User-agent: *\n"
        "Allow: /\n"
        "\n"
        f"Sitemap: {DOMINIO}/sitemap.xml\n"
    )
    with open(os.path.join(SALIDA_SITIO, "robots.txt"), "w", encoding="utf-8") as f:
        f.write(cuerpo)
    print("  sitio/robots.txt")


def escribir_sitemap(docs):
    hoy = date.today().isoformat()
    urls = [("", "1.0", hoy)]
    for slug in ORDEN:
        if slug in docs:
            urls.append((f"{slug}/", PRIORIDAD.get(slug, "0.5"), docs[slug][0]["actualizado"]))

    lineas = ['<?xml version="1.0" encoding="UTF-8"?>',
              '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">']
    for ruta, prioridad, mod in urls:
        lineas += [
            "  <url>",
            f"    <loc>{DOMINIO}/{ruta}</loc>",
            f"    <lastmod>{mod}</lastmod>",
            f"    <priority>{prioridad}</priority>",
            "  </url>",
        ]
    lineas.append("</urlset>")
    with open(os.path.join(SALIDA_SITIO, "sitemap.xml"), "w", encoding="utf-8") as f:
        f.write("\n".join(lineas) + "\n")
    print(f"  sitio/sitemap.xml        {len(urls)} URLs")


# ---------------------------------------------------------------------------
# Salida Kotlin
# ---------------------------------------------------------------------------

def kt(s):
    return s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")


def sin_negrita(s):
    return re.sub(r"\*\*(.+?)\*\*", r"\1", s)


def kotlin_bloques(bloques):
    lineas = []
    for b in bloques:
        if b.destino == "web":
            continue
        texto = kt(sin_negrita(b.texto))
        if b.tipo == "titulo":
            lineas.append(f'            Bloque.Titulo("{texto}"),')
        elif b.tipo == "parrafo":
            lineas.append(f'            Bloque.Parrafo("{texto}"),')
        elif b.tipo == "vineta":
            lineas.append(f'            Bloque.Vineta("{texto}"),')
        elif b.tipo == "numero":
            lineas.append(f'            Bloque.Numero({b.extra}, "{texto}"),')
        elif b.tipo == "tarjeta":
            lineas.append(
                f'            Bloque.Tarjeta("{texto}", "{kt(sin_negrita(b.extra))}"),'
            )
    return "\n".join(lineas)


CABECERA_KT = '''// GENERADO por tools/contenido/build.py — no editar a mano.
//
// El texto vive en `contenido/*.md`, que es también de donde salen las páginas
// de `sitio/`. Para cambiar una palabra: edita el .md y vuelve a correr el
// script. Editar aquí se pierde en la siguiente generación.

package ui.content

/** Los documentos de texto del sitio, tal y como los dibuja [DocumentoScreen]. */
object Textos {
'''


def escribir_kotlin(docs):
    partes = [CABECERA_KT]
    for slug in ORDEN:
        if slug not in docs:
            continue
        meta, bloques = docs[slug]
        nombre = re.sub(r"-(\w)", lambda m: m.group(1).upper(), slug)
        partes.append(
            f'''
    val {nombre} = Documento(
        titulo = "{kt(meta["titulo"])}",
        actualizado = "{fecha_larga(meta["actualizado"])}",
        bloques = listOf(
{kotlin_bloques(bloques)}
        ),
    )
'''
        )
    partes.append("}\n")

    os.makedirs(os.path.dirname(SALIDA_KT), exist_ok=True)
    with open(SALIDA_KT, "w", encoding="utf-8") as f:
        f.write("".join(partes))
    print(f"  {os.path.relpath(SALIDA_KT, RAIZ)}")


# ---------------------------------------------------------------------------

def main():
    if not os.path.isdir(ORIGEN):
        raise SystemExit("no encuentro contenido/")

    docs = {}
    for nombre in sorted(os.listdir(ORIGEN)):
        if not nombre.endswith(".md") or nombre == "README.md":
            continue
        meta, bloques = leer(os.path.join(ORIGEN, nombre))
        docs[meta["slug"]] = (meta, bloques)

    faltan = [s for s in ORDEN if s not in docs]
    if faltan:
        raise SystemExit(f"ORDEN menciona páginas que no existen: {', '.join(faltan)}")
    sobran = [s for s in docs if s not in ORDEN]
    if sobran:
        raise SystemExit(f"hay documentos fuera de ORDEN: {', '.join(sobran)}")

    pendientes = sum(
        1
        for _, bloques in docs.values()
        for b in bloques
        if "⟦" in b.texto or (b.extra and "⟦" in str(b.extra))
    )

    os.makedirs(SALIDA_SITIO, exist_ok=True)
    print("Generando:")
    escribir_paginas(docs)
    escribir_robots()
    escribir_sitemap(docs)
    escribir_kotlin(docs)

    if pendientes:
        print(f"\n⚠  {pendientes} bloque(s) con marcas ⟦…⟧ sin resolver.", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
