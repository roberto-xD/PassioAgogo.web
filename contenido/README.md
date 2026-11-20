# `contenido/` — los textos públicos, en un solo sitio

Aquí vive el texto de las páginas informativas y legales. **Esta carpeta es la fuente
única**: de aquí saldrán las dos salidas que hoy tendrían que escribirse dos veces.

```
contenido/*.md  ──┬──►  páginas HTML estáticas  (lo que leen Google y las IAs)
                  └──►  textos para las pantallas Compose  (lo que lee el cliente en la app)
```

Mientras el generador no exista, estos archivos son **el borrador a revisar**. Nada de lo
que hay aquí está publicado todavía.

## Por qué

El sitio se dibuja entero en un `<canvas>`: el HTML que entrega el servidor son 3 601
bytes con un «Cargando…». Ni Google ni los rastreadores de las IAs de búsqueda pueden
leer una sola palabra de las políticas, el catálogo o las guías. De ahí que una búsqueda
encuentre las redes sociales y no pueda resumir nada del sitio.

Y al ir a publicarlas apareció lo segundo: **los textos que hay escritos son de antes del
MVP**. Decían, entre otras cosas, que el sitio «no procesa compras, pagos ni envíos en
línea», que se compra «por WhatsApp, sin registro ni formularios» y que no hacen falta
cuentas. Publicar eso en HTML habría dado a las IAs una fuente citable y equivocada.

## Formato

Markdown con un encabezado de metadatos. El encabezado define la URL y las etiquetas de
la página:

```yaml
---
slug: terminos              # → https://passionagogo.com/terminos/
titulo: Términos y condiciones
title_tag: Términos y condiciones · Passion à gogo     # <title> de la página
descripcion: ...            # <meta name="description">, 150-160 caracteres
pantalla: TermsScreen       # pantalla Compose que recibe este texto
actualizado: 2026-11-18
---
```

Convenciones del cuerpo:

- `##` para las secciones; es lo que hoy es `SectionTitle(...)`. En el FAQ, cada `##` es
  una pregunta, y eso permite generar después el marcado `FAQPage` de la página HTML.
- `###` para las tarjetas de «Qué ofrecemos» en `nosotros.md`: el título es el `###` y el
  cuerpo, el párrafo que le sigue. Es lo que hoy es la lista `OFERTA`.
- Un párrafo por bloque, separado por línea en blanco; es `Paragraph(...)`.
- Las listas con `-` y las numeradas se convierten en párrafos con `•` o con su número,
  como hoy.
- Nada de HTML crudo ni de tablas: tiene que poder dibujarse en Compose, donde no hay ni
  uno ni otro.

## Lo que falta decidir antes de publicar

Los borradores llevaban marcas `⟦DECIDIR: …⟧` allí donde hacía falta una decisión del
titular o un dato que no está en el código, en lugar de inventarlo.

**No queda ninguna.** Todas se resolvieron el 20 de noviembre de 2026:

- Preparación del pedido en 1-2 días hábiles, igual para la CDMX que para el resto del
  país.
- Entrega fallida: se trata como cancelación, con reembolso del producto menos el envío.
- Entrega personal en Perisur, San Lázaro y Roma Sur, de lunes a viernes de 14:00 a
  19:00 h.
- Envío gratis por código promocional o por compra mínima de $1,500 MXN.
- Cambio de talla en lencería: 15 días naturales.
- Garantía de 90 días naturales en dispositivos; evaluación de la evidencia en 48 h
  hábiles; reembolso reflejado entre 3 y 10 días hábiles según el banco.
- Atención todos los días de 9:00 a 21:00 h.
- Facturación: todavía no se emite CFDI, y el texto lo dice con esas palabras.
- Titular: Roberto A. Gutiérrez C., con domicilio en Velo de Novia 16, Los Ángeles
  Iztapalapa, Ciudad de México, que es a la vez el domicilio fiscal. Publicarlo es lo que
  exigen la LFPC y la LFPDPPP; si en algún momento se quiere dejar de exponer un
  domicilio particular, la salida es un domicilio fiscal distinto (oficina virtual o
  buzón), no omitirlo del aviso.

## Pendiente en código

- **`DevolucionesScreen`** no existe. La página `devoluciones-y-garantias.md` tiene URL
  propia en la web, pero en la app necesita su pantalla y su entrada de navegación.
- **Reembolso de pagos en efectivo.** El webhook ya reconoce las fichas de OXXO
  (`ticket`), pero `reembolsar` llama a la API de reembolsos de Mercado Pago, que para un
  pago en efectivo no puede devolver el dinero a su origen. El texto dice «lo acordaremos
  contigo»: ese acuerdo hoy es manual.
- **Cupón en lugar de reembolso.** La política lo ofrece y la tabla `codigos` lo permite,
  pero no hay nada que lo emita: lo crea un administrador a mano.
- **Revocación posterior a la entrega.** El botón «Cancelar esta solicitud» solo cubre
  antes de la entrega. El derecho del artículo 56 se ejerce escribiendo, y se procesa a
  mano.

## Revisión legal

Estos textos describen con precisión **lo que el sistema hace de verdad** —eso está
leído del código—. Lo que no se puede garantizar es que la redacción cumpla la LFPDPPP y
la LFPC como las aplica un abogado mexicano. Tres puntos para un profesional:

1. **Datos sensibles.** La LFPDPPP considera sensible la preferencia sexual. El contenido
   de un pedido en esta tienda puede revelarla, y eso sube el estándar: consentimiento
   expreso, y en algunos casos por escrito. El aviso lo trata en su sección 5.
2. **Artículo 56 de la LFPC frente a la exclusión por higiene.** El texto aportado por el
   titular descartaba las devoluciones por arrepentimiento sin más. Como el artículo 56
   concede cinco días hábiles para revocar sin responsabilidad, la política publicada
   reconcilia las dos cosas: el derecho se respeta mientras el producto siga sellado, y la
   exclusión por higiene empieza cuando el sello se rompe. Es una postura defendible, pero
   es la que más fricción genera y conviene tenerla respaldada.
3. **Garantía de 90 días (artículo 77).** La cifra la fijó el titular. Conviene confirmar
   en el DOF el plazo vigente, porque hubo una reforma en 2026 que lo modificó.
