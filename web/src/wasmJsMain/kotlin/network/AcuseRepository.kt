package network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import models.Acuse
import models.AcuseDto
import models.ArticuloAcuse
import models.MetodoEntrega
import models.SiteSettingDto

/** Lo que se encontró, o por qué no. */
sealed interface ResultadoAcuse {
    data class Encontrado(val acuse: Acuse) : ResultadoAcuse

    /**
     * No hay fila que enseñar.
     *
     * No se distingue si el pedido no existe o si es de otra persona, y es a propósito:
     * distinguirlo no le sirve a quien llegó bien, y sí a quien prueba identificadores
     * ajenos para averiguar cuáles existen.
     */
    data object NoEsta : ResultadoAcuse

    data object SinSesion : ResultadoAcuse
}

/**
 * La solicitud entera, para la pantalla de «gracias».
 *
 * **El id viene de la URL de regreso de Mercado Pago, y no se cree: se usa para buscar.**
 * El RLS limita `order_requests` a las propias, así que pedir una ajena no devuelve nada.
 * Esa es la razón por la que basta con el id: la autorización no la hace esta consulta,
 * la hace la base.
 *
 * Lo mismo con el estado del pago: aquí solo se lee. Quien lo escribe es la Edge Function
 * del webhook, con `service_role`, después de preguntarle a Mercado Pago.
 */
class AcuseRepository(
    supabaseProvider: () -> SupabaseClient = ::createSupabase,
) {
    private val supabase: SupabaseClient by lazy(supabaseProvider)

    suspend fun leer(solicitudId: String): ResultadoAcuse {
        if (!SupabaseConfig.isConfigured || solicitudId.isBlank()) {
            return ResultadoAcuse.NoEsta
        }
        // Sin sesión se distingue, y aquí sí conviene: a quien se le caducó la sesión
        // volviendo de pagar hay que decirle que vuelva a entrar, no que su pedido no
        // existe. *Cuál* pedido es suyo sigue decidiéndolo el RLS, no esto.
        supabase.auth.currentUserOrNull() ?: return ResultadoAcuse.SinSesion

        // Igual que el historial: si un script todavía no se ha corrido, PostgREST
        // rechaza la consulta entera con un 400. Se va quitando de atrás hacia adelante
        // en vez de dejar la pantalla en blanco — un acuse sin el nombre del artículo
        // sigue sirviendo; uno vacío, no.
        val acuse = leerFila(solicitudId, conGratis = true, conPago = true, conNombres = true)
            ?: leerFila(solicitudId, conGratis = false, conPago = true, conNombres = true)
            ?: leerFila(solicitudId, conGratis = false, conPago = false, conNombres = true)
            ?: leerFila(solicitudId, conGratis = false, conPago = false, conNombres = false)

        return acuse?.let(ResultadoAcuse::Encontrado) ?: ResultadoAcuse.NoEsta
    }

    private suspend fun leerFila(
        solicitudId: String,
        conGratis: Boolean,
        conPago: Boolean,
        conNombres: Boolean,
    ): Acuse? = runCatching {
        val columnas = buildString {
            append("id, folio, estado, total_estimado, created_at, metodo_entrega, ")
            append("envio_costo, envio_descripcion, envio_entrega, ")
            // 48_codigos.sql. Va en su propio escalón: metida con las del pago, una base
            // sin ese script se quedaría también sin el estado del cobro, que es lo
            // único que esta pantalla existe para contar.
            if (conGratis) append("envio_gratis, ")
            if (conPago) {
                append("pago_estado, pago_total, pago_metodo, pago_referencia, ")
                append("pago_ultimos4, pago_marca, ")
            }
            append("dir_version, dir_contenido, dir_iv, dir_dk_usuario, dir_iv_usuario, ")
            append("dir_salt_usuario, dir_kdf_iteraciones, dir_dk_admin, dir_admin_key_id, ")
            append(
                if (conNombres) {
                    "order_request_items(cantidad, precio_estimado, " +
                        "product_variants(titulo, sku, products(nombre)))"
                } else {
                    "order_request_items(cantidad, precio_estimado)"
                }
            )
        }

        supabase.from(SupabaseConfig.ORDER_REQUESTS_TABLE)
            .select(columns = Columns.raw(columnas)) {
                filter { eq("id", solicitudId) }
            }
            .decodeList<AcuseDto>()
            .firstOrNull()
            ?.let(::aAcuse)
    }.getOrNull()

    /**
     * Los ajustes del acuse: el empaque, el rótulo del estado de cuenta y los SKUs
     * sugeridos (40_acuse.sql).
     *
     * `site_settings` lo lee cualquiera, también sin sesión: son ajustes de
     * presentación, no datos de nadie.
     */
    suspend fun ajustes(): Map<String, String> = runCatching {
        supabase.from(SupabaseConfig.SITE_SETTINGS_TABLE)
            .select(columns = Columns.raw("clave, valor")) {
                filter {
                    isIn("clave", listOf("acuse_empaque", "acuse_recomendados", "pago_statement"))
                }
            }
            .decodeList<SiteSettingDto>()
            .associate { it.clave to it.valor }
    }.getOrElse { emptyMap() }

    private fun aAcuse(dto: AcuseDto): Acuse? {
        val id = dto.id ?: return null

        val articulos = dto.items.map { item ->
            // El nombre bueno es el del producto más el título de la variante
            // («Varita de cristal · Rosa»). Sin catálogo se cae al SKU, y sin SKU a algo
            // neutro: un renglón sin nombre se lee como un error de la página.
            val producto = item.variante?.products?.nombre.orEmpty()
            val variante = item.variante?.titulo.orEmpty()
            val nombre = listOf(producto, variante)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { item.variante?.sku.orEmpty() }
                .ifBlank { "Artículo" }

            ArticuloAcuse(
                nombre = nombre,
                cantidad = item.cantidad,
                precio = item.precioEstimado,
            )
        }

        // El sobre solo se arma si está completo. A medias no abre, y un intento de
        // descifrado que falla se enseña igual que una dirección que no existe.
        val sobre = if (!dto.dirContenido.isNullOrBlank() && !dto.dirDkUsuario.isNullOrBlank()) {
            SobreDireccion(
                version = dto.dirVersion ?: 1,
                contenido = dto.dirContenido,
                iv = dto.dirIv.orEmpty(),
                dkUsuario = dto.dirDkUsuario,
                ivUsuario = dto.dirIvUsuario.orEmpty(),
                saltUsuario = dto.dirSaltUsuario.orEmpty(),
                kdfIteraciones = dto.dirKdfIteraciones ?: 0,
                dkAdmin = dto.dirDkAdmin.orEmpty(),
                adminKeyId = dto.dirAdminKeyId.orEmpty(),
            )
        } else {
            null
        }

        return Acuse(
            id = id,
            folio = dto.folio ?: 0L,
            fecha = dto.createdAt.orEmpty().substringBefore('T'),
            estado = dto.estado.orEmpty(),
            entrega = if (dto.metodoEntrega == MetodoEntrega.Personal.clave) {
                MetodoEntrega.Personal
            } else {
                MetodoEntrega.Domicilio
            },
            totalArticulos = dto.totalEstimado,
            envioCosto = dto.envioCosto ?: 0.0,
            envioDescripcion = dto.envioDescripcion.orEmpty(),
            envioEntrega = dto.envioEntrega.orEmpty(),
            envioGratis = dto.envioGratis,
            pagoEstado = dto.pagoEstado.orEmpty(),
            pagoTotal = dto.pagoTotal ?: 0.0,
            pagoMetodo = dto.pagoMetodo.orEmpty(),
            pagoReferencia = dto.pagoReferencia.orEmpty(),
            pagoUltimos4 = dto.pagoUltimos4.orEmpty(),
            pagoMarca = dto.pagoMarca.orEmpty(),
            articulos = articulos,
            sobre = sobre,
        )
    }
}
