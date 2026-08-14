#!/usr/bin/env python3
"""Regenera la fuente de emoji y la lista de soportados.

Uso:  pip install fonttools uharfbuzz  &&  python3 build.py
"""
import json
import os
import subprocess
import sys
import urllib.request

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.abspath(os.path.join(AQUI, "..", ".."))
FUENTE_ORIGEN = os.path.join(AQUI, "Noto-COLRv1.ttf")
URL_ORIGEN = "https://cdn.jsdelivr.net/gh/googlefonts/noto-emoji@main/fonts/Noto-COLRv1.ttf"
SALIDA_FUENTE = os.path.join(
    RAIZ, "web/src/commonMain/composeResources/font/emoji_passion.ttf"
)
SALIDA_KT = os.path.join(RAIZ, "web/src/wasmJsMain/kotlin/ui/theme/EmojiSupport.kt")


def descargar_origen():
    if not os.path.exists(FUENTE_ORIGEN):
        print("Descargando Noto Color Emoji (5 MB, no se versiona)...")
        urllib.request.urlretrieve(URL_ORIGEN, FUENTE_ORIGEN)


def recortar(secuencias):
    puntos = sorted({ord(c) for s in secuencias for c in s} | {0x200D, 0xFE0F})
    subprocess.run(
        [
            "pyftsubset", FUENTE_ORIGEN,
            "--unicodes=" + ",".join("U+%04X" % c for c in puntos),
            "--output-file=" + SALIDA_FUENTE,
            "--layout-features=*", "--no-hinting", "--notdef-outline",
        ],
        check=True,
    )


def verificar(secuencias):
    """Cada secuencia debe dar un unico glifo con capas de color.

    Sin esto un recorte mal hecho pasaria desapercibido hasta verlo en el navegador:
    las banderas se degradan a las letras del codigo de pais, que no es un fallo que
    salte a la vista en ningun log.
    """
    import uharfbuzz as hb
    from fontTools.ttLib import TTFont

    f = TTFont(SALIDA_FUENTE)
    color = {r.BaseGlyph for r in f["COLR"].table.BaseGlyphList.BaseGlyphPaintRecord}
    orden = f.getGlyphOrder()
    fuente = hb.Font(hb.Face(hb.Blob.from_file_path(SALIDA_FUENTE)))

    fallos = []
    for s in secuencias:
        variante = s if len(s) > 1 else s + "️"
        buf = hb.Buffer()
        buf.add_str(variante)
        buf.guess_segment_properties()
        hb.shape(fuente, buf)
        nombres = [orden[i.codepoint] for i in buf.glyph_infos]
        if len(nombres) != 1:
            fallos.append((s, "se parte en %d glifos" % len(nombres)))
        elif nombres[0] not in color:
            fallos.append((s, "sin capas de color"))
    return fallos


def escribir_kotlin(secuencias):
    def escapar(s):
        salida = ""
        for c in s:
            p = ord(c)
            if p > 0xFFFF:
                p -= 0x10000
                salida += "\\u%04X\\u%04X" % (0xD800 + (p >> 10), 0xDC00 + (p & 0x3FF))
            else:
                salida += "\\u%04X" % p
        return salida

    filas = "\n".join(
        '    "%s", // %s  %s'
        % (escapar(s), s, " ".join("U+%04X" % ord(c) for c in s))
        for s in secuencias
    )
    plantilla = open(os.path.join(AQUI, "EmojiSupport.kt.tmpl"), encoding="utf-8").read()
    open(SALIDA_KT, "w", encoding="utf-8").write(plantilla.replace("{{ENTRADAS}}", filas))


def main():
    secuencias = json.load(open(os.path.join(AQUI, "emojis.json"), encoding="utf-8"))
    descargar_origen()
    recortar(secuencias)
    fallos = verificar(secuencias)
    if fallos:
        for s, motivo in fallos:
            print("  FALLA %s -> %s" % (s, motivo), file=sys.stderr)
        sys.exit("La fuente de origen no cubre todo lo pedido; revisa los emojis de arriba.")
    escribir_kotlin(secuencias)
    kb = os.path.getsize(SALIDA_FUENTE) // 1024
    print("OK: %d emojis, %d KB" % (len(secuencias), kb))


if __name__ == "__main__":
    main()
