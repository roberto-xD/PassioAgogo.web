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

Los borradores llevan marcas `⟦DECIDIR: …⟧` allí donde hace falta una decisión tuya o un
dato que no está en el código. Ninguna está inventada. Son:

| Marca | Dónde | Qué hace falta |
|---|---|---|
| Plazo de entrega | términos, FAQ | Días hábiles reales por zona (CDMX vs. resto) |
| Umbral de envío gratis | términos, FAQ | Si hay un monto mínimo fijo además de los códigos |
| Quién paga el reenvío | términos | Si el paquete vuelve por domicilio incorrecto |
| Devoluciones | términos, FAQ | Postura final sobre producto abierto vs. sellado |
| Facturación | términos, FAQ | Si se emite CFDI y cómo se solicita |
| Entrega personal | términos, FAQ | Zona y condiciones de `metodo_entrega = 'personal'` |
| Domicilio publicado | términos, privacidad | Confirmar si se publica el domicilio particular o se usa otro |
| Razón social | ambos | «Roberto Gutierrez C» como persona física, y RFC si se factura |

## Revisión legal

Estos textos describen con precisión **lo que el sistema hace de verdad** —eso lo puedo
garantizar, está leído del código—. Lo que no puedo garantizar es que la redacción
cumpla la LFPDPPP y la LFPC como las aplica un abogado mexicano. Dos puntos concretos
que conviene que revise un profesional:

1. **Datos sensibles.** La LFPDPPP considera sensible la preferencia sexual. El contenido
   de un pedido en esta tienda puede revelarla, y eso sube el estándar: consentimiento
   expreso, y en algunos casos por escrito. El aviso lo trata en su sección 5.
2. **Artículo 56 de la LFPC.** El contrato se perfecciona a los cinco días hábiles desde
   la entrega, y durante ese plazo el consumidor puede revocar su consentimiento sin
   responsabilidad. El flujo de cancelación que tenemos construido solo cubre **antes**
   de la entrega; este derecho existe **después**. Hay además una reforma de 2026 que,
   según la prensa, sube la garantía de 60 a 90 días: hay que confirmar el texto vigente
   en el DOF.
