package ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import models.CaminoCancelacion
import models.EstadoPago
import models.Solicitud
import models.etiquetaDePago
import models.formatPrice
import network.CancelacionEstado
import network.WhatsAppConfig
import ui.components.BottomSheet
import ui.components.ButtonVariant
import ui.components.Paragraph
import ui.components.PassionButton
import ui.components.copyToClipboard
import ui.components.openInNewTab
import ui.theme.PassionTheme
import viewmodel.AvisoCancelacion
import viewmodel.CartUiState

/**
 * Las solicitudes que ha enviado quien tiene la sesión abierta.
 *
 * El RLS ya limita la consulta a las propias, así que aquí no hace falta filtrar por
 * nada: lo que llega es lo que esa persona puede ver.
 *
 * **No es el carrito.** Aquí está lo ya enviado; lo que se está armando vive en
 * `#/carrito`. Los dos nombres se parecían demasiado y daba la impresión de que el
 * carrito se vaciaba al entrar aquí, así que esta pantalla pasó a llamarse «Historial
 * de solicitudes» y avisa cuando hay algo pendiente de enviar.
 */
@Composable
fun AccountHistoryContent(
    state: CartUiState,
    onGoToCatalog: () -> Unit,
    onGoToCart: () -> Unit = {},
    onPagar: (String) -> Unit = {},
    onCancelar: (String, String) -> Unit = { _, _ -> },
    onCerrarAviso: () -> Unit = {},
) {
    Spacer(Modifier.height(PassionTheme.spacing.s2))
    val solicitudes = state.solicitudes
    when {
        state.cargandoSolicitudes && solicitudes == null ->
            Paragraph("Cargando tus solicitudes…")

        solicitudes == null -> Paragraph(
            "No pudimos cargar tus solicitudes. Vuelve a intentarlo más tarde."
        )

        solicitudes.isEmpty() -> {
            Paragraph(
                "Aquí aparecen las solicitudes que ya enviaste. Todavía no has " +
                        "enviado ninguna."
            )
            Spacer(Modifier.height(PassionTheme.spacing.s3))
            // Lo que la persona tiene a medio armar no está aquí sino en el
            // carrito. Sin este aviso, ver la lista vacía con artículos ya
            // elegidos parece que se perdieron.
            if (state.cart.items.isNotEmpty()) {
                Paragraph(
                    "Tienes ${state.unidades} artículo" +
                            (if (state.unidades == 1) "" else "s") +
                            " en tu solicitud sin enviar."
                )
                Spacer(Modifier.height(PassionTheme.spacing.s3))
                Button(onClick = onGoToCart, shape = MaterialTheme.shapes.extraLarge) {
                    Text("Ver mi solicitud", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(PassionTheme.spacing.s2))
            }
            Button(onClick = onGoToCatalog, shape = MaterialTheme.shapes.extraLarge) {
                Text("Ver el catálogo", style = MaterialTheme.typography.labelLarge)
            }
        }

        else -> {
            // El fallo del cobro se enseña arriba y una sola vez: es de la pantalla, no
            // de una tarjeta, y repetirlo en cada una haría pensar que falló en todas.
            state.errorPago?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = PassionTheme.spacing.s2),
                )
            }
            solicitudes.forEach {
                Tarjeta(
                    solicitud = it,
                    pagando = state.pagando == it.id,
                    onPagar = { onPagar(it.id) },
                    cancelando = state.cancelando == it.id,
                    // El aviso es de una tarjeta, no de la pantalla: sin comprobar el
                    // id, cancelar la de arriba contestaría también en la de abajo.
                    aviso = state.avisoCancelacion?.takeIf { a -> a.solicitudId == it.id },
                    onCancelar = { motivo -> onCancelar(it.id, motivo) },
                    onCerrarAviso = onCerrarAviso,
                )
            }
        }
    }
}

@Composable
private fun Tarjeta(
    solicitud: Solicitud,
    pagando: Boolean = false,
    onPagar: () -> Unit = {},
    cancelando: Boolean = false,
    aviso: AvisoCancelacion? = null,
    onCancelar: (String) -> Unit = {},
    onCerrarAviso: () -> Unit = {},
) {
    val forma = MaterialTheme.shapes.medium

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PassionTheme.spacing.s4)
            .clip(forma)
            .background(PassionTheme.semantics.overlayWeak)
            .padding(PassionTheme.spacing.s4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Folio #${solicitud.folio}",
                color = PassionTheme.semantics.onBackgroundStrong,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Distintivo(solicitud)
        }

        Text(
            text = "${solicitud.fecha} · ${solicitud.unidades} artículo" +
                if (solicitud.unidades == 1) "" else "s",
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = PassionTheme.spacing.s1),
        )

        Text(
            text = "Total estimado: ${formatPrice(solicitud.total)}",
            color = PassionTheme.semantics.onBackgroundStrong,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = PassionTheme.spacing.s2),
        )
        // El total ya lleva el envío dentro, así que se dice cuánto es: una cifra que
        // no cuadra con la suma de los artículos invita a pensar que está mal.
        if (solicitud.envioGratis) {
            Text(
                text = "Envío gratis",
                color = PassionTheme.semantics.onBackgroundSubtle,
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (solicitud.tieneEnvio) {
            Text(
                text = "Incluye envío" +
                    solicitud.envioDescripcion.takeIf { it.isNotBlank() }
                        ?.let { " · $it" }.orEmpty() +
                    " · ${formatPrice(solicitud.envioCosto)}",
                color = PassionTheme.semantics.onBackgroundSubtle,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Pago(solicitud, pagando, onPagar)

        Guia(solicitud)

        // El motivo solo existe cuando no se pudo atender, y es justo lo que la persona
        // necesita saber para decidir si vuelve a intentarlo.
        if (solicitud.motivoRechazo.isNotBlank()) {
            Text(
                text = solicitud.motivoRechazo,
                color = PassionTheme.semantics.onBackgroundMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = PassionTheme.spacing.s2),
            )
        }

        // La canceló la tienda, no ella. Sin esta línea, su pedido aparece cancelado
        // sin que nadie le haya dicho nada, que es la forma más rápida de que escriba
        // enfadada y con razón.
        if (solicitud.cancelada && solicitud.canceladaPor == "tienda") {
            Text(
                text = "Cancelamos este pedido desde la tienda" +
                    if (solicitud.pago == EstadoPago.Reembolsado) {
                        " y te devolvimos el pago."
                    } else {
                        "."
                    },
                color = PassionTheme.semantics.onBackgroundMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = PassionTheme.spacing.s2),
            )
            EnlaceWhatsApp(
                texto = "Preguntar por WhatsApp",
                enlace = WhatsAppConfig.enlaceParaPedido(solicitud.folio),
            )
        }

        // Pedida y con el dinero todavía cobrado: lo único que importa aquí es qué
        // pasa ahora, y que no es una espera indefinida.
        if (solicitud.cancelacionPedida) {
            Text(
                text = "Registramos tu cancelación. Te escribimos por WhatsApp para " +
                    "devolverte el dinero.",
                color = PassionTheme.semantics.onBackgroundMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = PassionTheme.spacing.s2),
            )
            EnlaceWhatsApp(
                texto = "Escribir por WhatsApp",
                enlace = WhatsAppConfig.enlaceParaDevolucion(solicitud.folio),
            )
        }

        Cancelacion(
            solicitud = solicitud,
            cancelando = cancelando,
            aviso = aviso,
            onCancelar = onCancelar,
            onCerrarAviso = onCerrarAviso,
        )
    }
}

/**
 * «Cancelar esta solicitud», y la hoja que explica qué significa eso aquí.
 *
 * El enlace va al final de la tarjeta y en tono apagado a propósito: es una salida, no
 * una invitación. Lo que no hace es esconderse —quien quiere cancelar y no encuentra
 * cómo acaba escribiendo igual, pero enfadado.
 */
@Composable
private fun Cancelacion(
    solicitud: Solicitud,
    cancelando: Boolean,
    aviso: AvisoCancelacion?,
    onCancelar: (String) -> Unit,
    onCerrarAviso: () -> Unit,
) {
    if (!solicitud.puedeCancelar) return

    var abierto by remember(solicitud.id) { mutableStateOf(false) }

    Text(
        text = "Cancelar esta solicitud",
        color = PassionTheme.semantics.onBackgroundSubtle,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .padding(top = PassionTheme.spacing.s3)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable { abierto = true }
            .padding(vertical = PassionTheme.spacing.s1),
    )

    if (!abierto) return

    val cerrar = {
        abierto = false
        onCerrarAviso()
    }

    BottomSheet(
        title = when (solicitud.caminoDeCancelacion) {
            CaminoCancelacion.ConGuia -> "Tu pedido ya salió"
            CaminoCancelacion.Pagada -> "Cancelar y recuperar tu dinero"
            CaminoCancelacion.SinPagar -> "Cancelar esta solicitud"
        },
        onDismiss = cerrar,
        // Son tres párrafos como mucho: a pantalla completa se leería como una hoja a
        // medio cargar.
        altoFijo = false,
    ) {
        // Ya contestó la base. Lo que dijo manda sobre lo que esta tarjeta creía: entre
        // abrir la hoja y confirmar pueden haber pasado cosas —un webhook que aprueba
        // el pago tarda segundos—, y el texto de abajo se escribió con lo de antes.
        if (aviso != null) {
            Paragraph(aviso.mensaje)
            if (aviso.estado == CancelacionEstado.Solicitada ||
                aviso.estado == CancelacionEstado.ConGuia
            ) {
                EnlaceWhatsApp(
                    texto = "Escribir por WhatsApp",
                    enlace = if (aviso.estado == CancelacionEstado.Solicitada) {
                        WhatsAppConfig.enlaceParaDevolucion(solicitud.folio)
                    } else {
                        WhatsAppConfig.enlaceParaCancelacion(solicitud.folio)
                    },
                )
            }
            Spacer(Modifier.height(PassionTheme.spacing.s3))
            PassionButton(texto = "Entendido", onClick = cerrar)
            return@BottomSheet
        }

        when (solicitud.caminoDeCancelacion) {
            // Nada cobrado: no hay nada que deshacer y no hace falta dramatizarlo.
            // Que se vaya sabiendo que puede volver vale más que retenerlo hoy.
            CaminoCancelacion.SinPagar -> {
                Paragraph(
                    "Todavía no has pagado, así que no hay nada que devolver. " +
                        "Cancelamos esta solicitud y tus artículos siguen en el " +
                        "catálogo para cuando te animes."
                )
                // Una ficha de OXXO sigue siendo cobrable aunque la solicitud se
                // cancele: la caja no sabe nada de esto. Más vale decirlo aquí que
                // devolver el dinero después.
                if (solicitud.pago == EstadoPago.Pendiente) {
                    Paragraph(
                        "Si tienes una ficha de pago pendiente, no la pagues: " +
                            "después de cancelar tendríamos que devolverte el dinero."
                    )
                }
                Spacer(Modifier.height(PassionTheme.spacing.s3))
                BotonesDeCancelacion(cancelando, onCancelar = { onCancelar("") }, cerrar)
            }

            // Pagada y sin guía: lo que se pide aquí es parar el pedido. El dinero lo
            // devuelve una persona, y eso se dice tal cual en vez de prometer un
            // automatismo que no existe.
            CaminoCancelacion.Pagada -> {
                Paragraph(
                    "Ya recibimos tu pago, así que dejamos de preparar tu pedido y " +
                        "te escribimos por WhatsApp para devolverte el dinero. La " +
                        "devolución la hacemos a mano, por el mismo medio con el " +
                        "que pagaste."
                )
                Spacer(Modifier.height(PassionTheme.spacing.s3))
                BotonesDeCancelacion(cancelando, onCancelar = { onCancelar("") }, cerrar)
            }

            // Con guía no se cancela desde aquí, y el botón no aparece: un «Sí,
            // cancelar» que no cancela es peor que no ofrecerlo.
            CaminoCancelacion.ConGuia -> {
                Paragraph(
                    "Tu pedido ya tiene guía y puede estar en camino, así que desde " +
                        "aquí ya no se puede cancelar. Escríbenos por WhatsApp con tu " +
                        "folio y lo vemos contigo."
                )
                EnlaceWhatsApp(
                    texto = "Escribir por WhatsApp",
                    enlace = WhatsAppConfig.enlaceParaCancelacion(solicitud.folio),
                )
                Spacer(Modifier.height(PassionTheme.spacing.s3))
                PassionButton(
                    texto = "Entendido",
                    onClick = cerrar,
                    variante = ButtonVariant.Secundario,
                )
            }
        }
    }
}

@Composable
private fun BotonesDeCancelacion(
    cancelando: Boolean,
    onCancelar: () -> Unit,
    onCerrar: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(PassionTheme.spacing.s3)) {
        // «Mejor no» primero y en primario: de las dos salidas, la que deshace es la
        // que se pulsa por error.
        PassionButton(texto = "Mejor no", onClick = onCerrar, enabled = !cancelando)
        PassionButton(
            texto = if (cancelando) "Cancelando…" else "Sí, cancelar",
            onClick = onCancelar,
            variante = ButtonVariant.Secundario,
            enabled = !cancelando,
        )
    }
}

/**
 * El enlace a la conversación, con el mensaje ya redactado.
 *
 * Recibe el enlace hecho y no el folio: el mensaje no es el mismo cuando alguien va a
 * pedir una cancelación que cuando pregunta por una devolución que ya pidió. Quien
 * abre WhatsApp y encuentra escrito lo que no quería decir, lo borra y escribe «hola»,
 * y ahí se pierde el folio.
 */
@Composable
private fun EnlaceWhatsApp(texto: String, enlace: String) {
    if (!WhatsAppConfig.isConfigured) return
    Text(
        text = texto,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .padding(top = PassionTheme.spacing.s2)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable { openInNewTab(enlace) }
            .padding(vertical = PassionTheme.spacing.s1),
    )
}

/**
 * En qué va el pago, y el botón para hacerlo.
 *
 * Se enseña siempre que haya algo que decir, incluso «sin pagar»: el hueco en blanco
 * deja a la persona sin saber si la tienda espera su dinero o su paciencia.
 *
 * El importe que se enseña cuando ya está pagado es **el que cobró Mercado Pago**, no el
 * total calculado. Si no coinciden, eso es justo lo que hay que poder ver.
 */
@Composable
private fun Pago(solicitud: Solicitud, pagando: Boolean, onPagar: () -> Unit) {
    val pago = solicitud.pago

    // En una cancelada, «El pago no se completó» o «Sin pagar» son reproches por algo
    // que ya no va a pasar: nadie va a pagar un pedido que canceló. Lo que sí se sigue
    // diciendo es dónde está el dinero cuando lo hay —cobrado o devuelto—, que es
    // justo lo que esa persona viene a mirar.
    val hayDinero = pago == EstadoPago.Aprobado || pago == EstadoPago.Reembolsado
    if (solicitud.cerrada && !hayDinero) return

    Text(
        text = when (pago) {
            EstadoPago.Aprobado ->
                "Pagado · ${formatPrice(solicitud.pagoTotal.takeIf { it > 0.0 } ?: solicitud.total)}" +
                    etiquetaDePago(
                        marca = solicitud.pagoMarca,
                        metodo = solicitud.pagoMetodo,
                        ultimos4 = solicitud.pagoUltimos4,
                    ).takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            // «En proceso» y no «pendiente»: en Mercado Pago esto es también el estado
            // de un pago en efectivo con ficha sin pagar, y el de una tarjeta en
            // revisión. Lo que las une es que todavía no se sabe.
            EstadoPago.Pendiente -> "Pago en proceso"
            EstadoPago.Rechazado -> "El pago no se completó"
            EstadoPago.Reembolsado -> "Pago reembolsado"
            EstadoPago.Cancelado -> "Pago cancelado"
            EstadoPago.Ninguno -> "Sin pagar"
        },
        color = if (pago == EstadoPago.Aprobado) {
            PassionTheme.semantics.onBackgroundStrong
        } else {
            PassionTheme.semantics.onBackgroundMuted
        },
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (pago == EstadoPago.Aprobado) FontWeight.SemiBold else null,
        modifier = Modifier.padding(top = PassionTheme.spacing.s2),
    )

    // La referencia es su comprobante: es el número que sirve para reclamar en Mercado
    // Pago si algo no cuadra, y ahí no vale el folio nuestro.
    if (pago == EstadoPago.Aprobado && solicitud.pagoReferencia.isNotBlank()) {
        var copiado by remember(solicitud.id) { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ref. ${solicitud.pagoReferencia}",
                color = PassionTheme.semantics.onBackgroundSubtle,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .weight(1f)
                    .clickable { copiado = copyToClipboard(solicitud.pagoReferencia) }
                    .padding(vertical = PassionTheme.spacing.s1),
            )
            Text(
                text = if (copiado) "copiado" else "toca para copiar",
                color = PassionTheme.semantics.onBackgroundSubtle,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = PassionTheme.spacing.s2),
            )
        }
    }

    if (!solicitud.puedePagar) return

    Spacer(Modifier.height(PassionTheme.spacing.s2))
    Button(
        onClick = onPagar,
        enabled = !pagando,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text(
            text = when {
                pagando -> "Abriendo…"
                // Reintentar no es lo mismo que pagar: quien ya vio un rechazo necesita
                // saber que esto es otro intento, no un segundo cargo.
                pago == EstadoPago.Rechazado || pago == EstadoPago.Cancelado ->
                    "Intentar el pago otra vez"
                pago == EstadoPago.Pendiente -> "Continuar el pago"
                else -> "Pagar ${formatPrice(solicitud.total)}"
            },
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * El número de guía, cuando el pedido ya es un paquete.
 *
 * Es el dato que la gente viene a buscar aquí, así que se enseña entero y se copia de un
 * toque: un número de rastreo se usa para pegarlo en otro sitio, y leerlo a mano de una
 * pantalla para teclearlo en otra es donde se cuelan los dígitos cambiados.
 *
 * Sin guía no se inventa nada ni se deja el hueco en blanco: «Pendiente de envío» dice
 * dónde está el pedido, que es la pregunta de fondo.
 */
@Composable
private fun Guia(solicitud: Solicitud) {
    if (!solicitud.tieneGuia) {
        // Una cancelada no está pendiente de envío: no está pendiente de nada. Dejar
        // ese renglón hace dudar de si la cancelación se registró.
        if (solicitud.cerrada) return
        Text(
            text = "Pendiente de envío",
            color = PassionTheme.semantics.onBackgroundMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = PassionTheme.spacing.s2),
        )
        return
    }

    var copiado by remember(solicitud.id) { mutableStateOf(false) }

    Text(
        text = solicitud.guiaPaqueteria.ifBlank { "Guía" }.replaceFirstChar { it.uppercase() },
        color = PassionTheme.semantics.onBackgroundSubtle,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = PassionTheme.spacing.s3),
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = solicitud.guiaNumero,
            color = PassionTheme.semantics.onBackgroundStrong,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable { copiado = copyToClipboard(solicitud.guiaNumero) }
                .padding(vertical = PassionTheme.spacing.s1),
        )
        Text(
            // El aviso sustituye a la invitación, no se añade: dos textos a la vez
            // obligan a leer cuál es cuál.
            text = if (copiado) "copiado" else "toca para copiar",
            color = PassionTheme.semantics.onBackgroundSubtle,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = PassionTheme.spacing.s2),
        )
    }

    if (solicitud.guiaRastreoUrl.isNotBlank()) {
        Text(
            text = "Rastrear envío",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .padding(top = PassionTheme.spacing.s1)
                .clip(MaterialTheme.shapes.extraSmall)
                .clickable { openInNewTab(solicitud.guiaRastreoUrl) }
                .padding(vertical = PassionTheme.spacing.s1),
        )
    }
}

/**
 * El distintivo de la esquina, solo cuando hay algo fuera de lo normal que decir.
 *
 * Antes enseñaba `estado` siempre: «En revisión» y «Atendida». Dejó de ser verdad en
 * cuanto el pedido pasó a tener pasos propios —pagado, con guía, en camino—, porque
 * `estado` no es el avance del pedido sino la cola de administración: una solicitud
 * pagada y con guía seguía diciendo «En revisión», que es exactamente lo contrario de
 * lo que la persona estaba viendo debajo.
 *
 * Lo que de verdad pasa se cuenta ahí abajo, renglón a renglón, y no necesita rótulo.
 * Aquí quedan las dos cosas que **no** son avance sino excepción: lo cancelado y lo
 * que no se pudo atender. Sin esto, una solicitud cancelada se ve igual que una viva.
 */
@Composable
private fun Distintivo(solicitud: Solicitud) {
    val rotulo = when {
        solicitud.cancelada -> "Cancelada"
        solicitud.cancelacionPedida -> "Cancelación pedida"
        solicitud.estado == "rechazada" -> "No pudimos atenderla"
        else -> return
    }
    val error = solicitud.estado == "rechazada"

    Text(
        text = rotulo,
        color = if (error) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            PassionTheme.semantics.onBackgroundMuted
        },
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                if (error) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    PassionTheme.semantics.overlayWeak
                }
            )
            .padding(
                horizontal = PassionTheme.spacing.s3,
                vertical = PassionTheme.spacing.s1,
            ),
    )
}
