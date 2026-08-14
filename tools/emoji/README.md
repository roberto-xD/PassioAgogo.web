# Fuente de emoji

`web/src/commonMain/composeResources/font/emoji_passion.ttf` es un **recorte** de
[Noto Color Emoji](https://github.com/googlefonts/noto-emoji) (COLRv1, licencia OFL)
que contiene solo los emojis de `emojis.json`.

## Por qué un recorte

Compose Web dibuja el texto con Skia sobre un canvas y no tiene acceso a las fuentes
del sistema: si el glifo no está en una fuente empaquetada, no se pinta. La fuente
completa pesa 5 MB; este recorte, con 63 emojis, unos 180 KB.

## Cómo añadir un emoji

1. Añádelo a `emojis.json`.
2. Corre `python3 build.py` (necesita `pip install fonttools uharfbuzz`).
3. Compila y sube. El script regenera la fuente **y** `EmojiSupport.kt`, que es lo
   que evita que un emoji no incluido se pinte como cuadro vacío.

El script verifica cada emoji con HarfBuzz antes de dar el recorte por bueno: comprueba
que la secuencia produzca **un solo glifo a color**, no sus piezas sueltas. Es la
comprobación que importa en banderas y secuencias con ZWJ (🏳‍🌈, 🇲🇽), donde un recorte
mal hecho deja las letras del código de país en lugar de la bandera.

## Mantener la lista al día

Los emojis los eliges tú en Supabase, así que la lista puede quedarse corta. Para ver
cuáles están en uso:

```sql
select distinct emoji from attribute_presets where emoji is not null
union
select distinct emoji from guides where emoji is not null;
```

Cualquiera que no esté en `emojis.json` simplemente no se dibuja —queda el rótulo—, así
que la web nunca se rompe por esto; solo se ve más sosa de lo previsto.
