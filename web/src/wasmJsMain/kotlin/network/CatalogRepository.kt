package network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import models.AttributePresetDto
import models.CatalogBundle
import models.CategoryRefDto
import models.ProductDto
import models.PromotionDto

/** Columnas de la variante que existen desde siempre. */
private val VARIANTES_SIEMPRE = listOf("id", "sku", "precio_venta", "activo", "attributes")

/**
 * Columnas de la variante que llegaron con un script posterior, **de la más antigua a
 * la más reciente**.
 *
 * El orden importa: al degradar se sueltan por el final, así que una base a medio
 * actualizar conserva lo que sí tiene. Al añadir una columna nueva, va al final.
 */
private val VARIANTES_OPCIONALES = listOf(
    "titulo",  // 29_variant_titulo
    "imagen",  // 53_variant_imagen
)

class CatalogRepository(
    supabaseProvider: () -> SupabaseClient = ::createSupabase,
) {
    // Se construye solo cuando hay configuración real y se hace una consulta. Con los
    // placeholders el cliente de Supabase nunca se instancia.
    private val supabase: SupabaseClient by lazy(supabaseProvider)

    /**
     * Catálogo completo: productos activos (con categoría y variantes), promociones
     * vigentes (con targets) y la jerarquía de categorías para resolver promos por
     * categoría/subcategoría.
     *
     * Se piden columnas explícitas — nunca `*` — para no exponer campos sensibles
     * como `product_variants.costo` en el tráfico del cliente. El RLS (10_rls.sql)
     * ya limita a `anon` a registros activos.
     */
    fun getCatalog(): Flow<NetworkResult<CatalogBundle>> = toResultFlow {
        if (!SupabaseConfig.isConfigured) {
            return@toResultFlow NetworkResult.Success(CatalogBundle())
        }

        // Las columnas de [VARIANTES_OPCIONALES] llegaron después: si su script no se
        // ha corrido, PostgREST rechaza la consulta entera con un 400 y el catálogo se
        // quedaría en blanco. Se reintenta soltando la más reciente, y luego la
        // siguiente, hasta quedarse con las de siempre. Lo único que se pierde es la
        // función que trajo cada una.
        val products = (VARIANTES_OPCIONALES.size downTo 0)
            .firstNotNullOfOrNull { cuantas -> leerProductos(VARIANTES_OPCIONALES.take(cuantas)) }
            ?: emptyList()

        // Promociones vigentes hoy (el RLS ya limita a activas para anon).
        val now = nowIso()
        val promotions = supabase.from(SupabaseConfig.PROMOTIONS_TABLE)
            .select(
                columns = Columns.raw(
                    "id, tipo, valor, " +
                        "promotion_targets(category_id, product_id, variant_id)"
                )
            ) {
                filter {
                    eq("activo", true)
                    lte("fecha_inicio", now)
                    gte("fecha_fin", now)
                }
            }
            .decodeList<PromotionDto>()

        // Categorías: jerarquía para expandir promos y nombres para el filtro de la UI.
        //
        // `seccion` se pide aparte porque es columna nueva (26_category_sections): si el
        // script todavía no se ha corrido, PostgREST rechaza la consulta entera con un
        // 400 y el catálogo se quedaría en blanco. Se reintenta sin ella y lo único que
        // se pierde es la separación por secciones.
        val categoryRefs = runCatching {
            supabase.from(SupabaseConfig.CATEGORIES_TABLE)
                .select(columns = Columns.raw("id, nombre, parent_id, seccion"))
                .decodeList<CategoryRefDto>()
        }.getOrElse {
            supabase.from(SupabaseConfig.CATEGORIES_TABLE)
                .select(columns = Columns.raw("id, nombre, parent_id"))
                .decodeList<CategoryRefDto>()
        }

        // Diccionario de los `attributes`: convierte {"material":"silicona"} en la chip
        // "🧴 Silicona". El RLS (script 20) deja leer los activos sin sesión.
        val attributePresets = supabase.from(SupabaseConfig.ATTRIBUTE_PRESETS_TABLE)
            .select(columns = Columns.raw("clave, valor, identificador, emoji, orden")) {
                filter { eq("activo", true) }
                order("orden", Order.ASCENDING)
            }
            .decodeList<AttributePresetDto>()

        NetworkResult.Success(
            CatalogBundle(products, promotions, categoryRefs, attributePresets)
        )
    }

    /** El catálogo, o `null` si la consulta no pasó. */
    private suspend fun leerProductos(opcionales: List<String>): List<ProductDto>? = runCatching {
        val variantes = (VARIANTES_SIEMPRE + opcionales).joinToString(", ")
            .let { "product_variants($it)" }
        supabase.from(SupabaseConfig.PRODUCTS_TABLE)
            .select(
                columns = Columns.raw(
                    "id, nombre, resumen, descripcion, marca, imagenes, attributes, " +
                        "category_id, activo, sobre_pedido, " +
                        "categories(nombre), " + variantes
                )
            ) {
                // Sin filtro de `activo` aquí: la visibilidad se decide en el ViewModel
                // (activo o sobre pedido), para que el resultado sea el mismo tanto para
                // un visitante anónimo como para un miembro del staff con sesión, cuyas
                // políticas RLS son distintas.
                order("nombre", Order.ASCENDING)
            }
            .decodeList<ProductDto>()
    }.getOrNull()
}
