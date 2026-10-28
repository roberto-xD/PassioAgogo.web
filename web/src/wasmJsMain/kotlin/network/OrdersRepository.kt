package network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import models.CartItem
import models.MetodoEntrega
import models.NuevaSolicitudDto
import models.NuevoItemDto
import models.Solicitud
import models.SolicitudDto

/** Se envió, o hay un motivo que contarle a la persona. */
sealed interface EnvioResult {
    data class Enviada(val folio: Long) : EnvioResult
    data class Fallo(val mensaje: String) : EnvioResult
    /** No hay sesión: el carrito se conserva y se pide acceder. */
    data object SinSesion : EnvioResult
}

/**
 * Solicitudes de compra (23_order_requests.sql / 24_cliente_solicitudes.sql).
 *
 * Una solicitud **no es una venta**: no mueve inventario y los precios que lleva son los
 * que vio la persona. Un admin la convierte en pedido y la base recotiza entonces con la
 * promoción vigente.
 */
class OrdersRepository(
    supabaseProvider: () -> SupabaseClient = ::createSupabase,
) {
    private val supabase: SupabaseClient by lazy(supabaseProvider)

    /**
     * Crea la solicitud y sus artículos.
     *
     * Van en dos pasos porque los artículos necesitan el id de la solicitud. Si el
     * segundo falla se borra la cabecera: una solicitud sin artículos no le sirve a
     * nadie y ensucia la cola de administración.
     */
    suspend fun enviar(
        items: List<CartItem>,
        nombre: String,
        telefono: String,
        email: String,
        notas: String,
        entrega: MetodoEntrega = MetodoEntrega.Domicilio,
        direccion: SobreDireccion? = null,
    ): EnvioResult {
        if (!SupabaseConfig.isConfigured) {
            return EnvioResult.Fallo("El envío de solicitudes aún no está configurado.")
        }
        if (items.isEmpty()) return EnvioResult.Fallo("El carrito está vacío.")

        val userId = supabase.auth.currentUserOrNull()?.id ?: return EnvioResult.SinSesion

        // La solicitud, en dos versiones: con columna propia para el método de entrega y
        // sin ella. La segunda es para una base donde 32_metodo_entrega.sql todavía no se
        // ha corrido: en vez de dejar la elección sin registrar, la anota en las notas.
        fun cabecera(conColumna: Boolean) = NuevaSolicitudDto(
            clienteNombre = nombre.trim(),
            clienteTelefono = telefono.trim().ifBlank { null },
            clienteEmail = email.trim().ifBlank { null },
            notas = when {
                conColumna -> notas.trim().ifBlank { null }
                else -> listOf(entrega.etiqueta, notas.trim())
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
            },
            createdBy = userId,
            metodoEntrega = if (conColumna) entrega.clave else null,
            dirVersion = direccion?.version,
            dirContenido = direccion?.contenido,
            dirIv = direccion?.iv,
            dirDkUsuario = direccion?.dkUsuario,
            dirIvUsuario = direccion?.ivUsuario,
            dirSaltUsuario = direccion?.saltUsuario,
            dirKdfIteraciones = direccion?.kdfIteraciones,
            dirDkAdmin = direccion?.dkAdmin,
            dirAdminKeyId = direccion?.adminKeyId,
        )

        var solicitudId: String? = null
        return try {
            val creada = try {
                insertarCabecera(cabecera(conColumna = true))
            } catch (e: Exception) {
                // Un 400 por columna desconocida se rechaza antes de escribir nada, así
                // que reintentar no deja a medias ninguna solicitud.
                if (!faltaLaColumna(e.message.orEmpty())) throw e
                insertarCabecera(cabecera(conColumna = false))
            }

            val id = creada.id ?: return EnvioResult.Fallo(
                "No se pudo registrar la solicitud. Inténtalo de nuevo."
            )
            solicitudId = id

            supabase.from(SupabaseConfig.ORDER_REQUEST_ITEMS_TABLE).insert(
                items.map {
                    NuevoItemDto(
                        requestId = id,
                        variantId = it.variantId,
                        cantidad = it.cantidad,
                        precioEstimado = it.precio,
                    )
                }
            )

            EnvioResult.Enviada(creada.folio ?: 0L)
        } catch (e: Exception) {
            solicitudId?.let { huerfana ->
                runCatching {
                    supabase.from(SupabaseConfig.ORDER_REQUESTS_TABLE)
                        .delete { filter { eq("id", huerfana) } }
                }
            }
            EnvioResult.Fallo(mensajeDeError(e.message.orEmpty()))
        }
    }

    private suspend fun insertarCabecera(fila: NuevaSolicitudDto): SolicitudDto =
        supabase.from(SupabaseConfig.ORDER_REQUESTS_TABLE)
            .insert(fila) { select() }
            .decodeSingle()

    /** Las solicitudes de quien tiene la sesión abierta, de la más reciente a la más vieja. */
    suspend fun misSolicitudes(): List<Solicitud>? {
        if (!SupabaseConfig.isConfigured) return emptyList()
        supabase.auth.currentUserOrNull() ?: return null

        return runCatching {
            supabase.from(SupabaseConfig.ORDER_REQUESTS_TABLE)
                .select(
                    columns = Columns.raw(
                        "id, folio, estado, total_estimado, created_at, motivo_rechazo, " +
                            "order_request_items(cantidad, precio_estimado)"
                    )
                ) {
                    // El RLS ya limita a las propias; el orden es cosa de la consulta.
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<SolicitudDto>()
                .mapNotNull { dto ->
                    val id = dto.id ?: return@mapNotNull null
                    Solicitud(
                        id = id,
                        folio = dto.folio ?: 0L,
                        estado = dto.estado.orEmpty(),
                        total = dto.totalEstimado,
                        fecha = soloFecha(dto.createdAt.orEmpty()),
                        unidades = dto.items.sumOf { it.cantidad },
                        motivoRechazo = dto.motivoRechazo.orEmpty(),
                    )
                }
        }.getOrNull()
    }
}

/** "2026-10-20T18:04:11.123Z" → "2026-10-20". Para el historial basta el día. */
private fun soloFecha(iso: String): String = iso.substringBefore('T')

/** PostgREST se queja de una columna que no existe; distinto de que falte un permiso. */
private fun faltaLaColumna(raw: String): Boolean {
    val m = raw.lowercase()
    return "metodo_entrega" in m &&
        ("does not exist" in m || "no existe" in m || "pgrst204" in m || "schema cache" in m)
}

private fun mensajeDeError(raw: String): String {
    val m = raw.lowercase()
    return when {
        // El insert choca con la política de RLS: la cuenta no tiene permiso para pedir.
        "row-level security" in m || "violates row-level" in m ->
            "Tu cuenta no tiene permiso para enviar solicitudes. Escríbenos y lo revisamos."
        "foreign key" in m || "violates foreign key" in m ->
            "Alguno de los productos ya no está disponible. Quítalo e inténtalo de nuevo."
        "failed to fetch" in m || "network" in m ->
            "No se pudo conectar. Revisa tu conexión e inténtalo de nuevo."
        else -> "No se pudo enviar la solicitud. Inténtalo de nuevo."
    }
}
