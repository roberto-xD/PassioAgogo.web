# Herramientas para la app de control

Archivos para **pegar en la app de control** (Kotlin/Android), no para compilar aquí.
Esta web no los usa: viven en el repositorio para que el código de las dos mitades se
revise junto, porque entre las dos se reparte algo que ninguna puede hacer sola.

| Archivo | Para qué |
|---|---|
| [`EnvioApi.kt`](EnvioApi.kt) | Generar guías y pedir recolecciones |
| [`../crypto/AdminKeyTool.kt`](../crypto/AdminKeyTool.kt) | Generar el par de claves y abrir direcciones |

## Cómo se reparte el trabajo

El token de Envia **no puede estar en la app**. Un APK se descompila, y ese token genera
guías reales y cobra. La clave privada del administrador **no puede estar en el
servidor**: es lo único que abre las direcciones de los clientes, y si viviera donde
viven los datos cifrados el cifrado no serviría de nada.

Así que cada mitad guarda lo suyo:

```
app de control                        Edge Functions
──────────────                        ──────────────
clave privada  →  abre el sobre
                  dirección en claro
                        │
                        └──── TLS ───→ token de Envia
                                       pide la guía
                                       anota el resultado
```

La dirección **pasa** por el servidor, no se queda: va a la paquetería, que tiene que
saber a dónde llevar el paquete, y no se escribe en ninguna tabla.

## Uso

```kotlin
val api = EnvioApi(supabase)

// 1. Abrir la dirección con la clave privada (AdminKeyTool).
val direccion = AdminKeyTool.descifrarDireccion(sobre, privada)

// 2. Pedir la guía. Sin carrier ni servicio usa lo que el cliente eligió al cotizar.
when (val r = api.generarGuia(solicitudId = solicitud.id, direccion = direccion)) {
    is ResultadoGuia.Ok -> {
        if (r.guia.yaTenia) avisar("Esta solicitud ya tenía guía: ${r.guia.numero}")
        else imprimir(r.guia.url)
        r.aviso?.let { avisar(it) }   // la guía existe, pero algo de después falló
    }
    is ResultadoGuia.Fallo -> avisar(r.mensaje)
}

// 3. Al final del día, una recolección por paquetería.
val r = api.solicitarRecoleccion(
    solicitudIds = pedidosDeEstafeta.map { it.id },
    fecha = "2026-11-03",
)
```

## Lo que conviene saber antes de usarlo

**Una solicitud con guía no genera otra.** Si se vuelve a pedir, devuelve la que hay con
`yaTenia = true` y no cobra. Dos clics seguidos dejarían la segunda etiqueta impresa,
pagada y pegada en ninguna caja.

**Una recolección es de una sola paquetería.** Envia agrupa por paquetería y dirección de
origen. Mezclarlas devuelve un error antes de llamar a Envia.

**No todas recogen.** Unas lo agendan solas al crear la guía, otras lo aceptan aparte y
otras solo reciben en sucursal. Cuando la paquetería dice que no recoge, ese motivo se
devuelve tal cual: es lo que decide si hay que ir a dejarlo.

**`aviso` no es un error.** Significa que la guía o la recolección existen —y están
pagadas— pero no se pudieron anotar en la solicitud. Hay que apuntar el número a mano.

**Mientras se prueba, en sandbox.** `ENVIA_BASE_URL` apunta por defecto a
`api-test.envia.com`. En producción, cada guía generada cuesta dinero de verdad.
