package network

/**
 * Configuración de acceso a Supabase.
 *
 * SEGURIDAD:
 * - La `ANON_KEY` es una clave **pública por diseño** (viaja al cliente web). No es un
 *   secreto: la protección de datos se hace con políticas RLS (Row Level Security) en la
 *   base de datos (09/10_rls.sql). NUNCA pongas aquí la `service_role` key.
 * - Idealmente inyecta URL/clave por configuración de build (dev vs prod) en lugar de
 *   dejarlas fijas en el código.
 *
 * Mientras estos valores sean los placeholders, [isConfigured] es false: el repositorio
 * devuelve un catálogo vacío y no se instancia el cliente de Supabase.
 */
object SupabaseConfig {
    // TODO: reemplazar por los valores reales del proyecto Supabase.
    const val URL: String = "https://YOUR-PROJECT-REF.supabase.co"
    const val ANON_KEY: String = "YOUR-SUPABASE-ANON-KEY"

    /** Tablas del catálogo (01_catalog.sql / 05_promotions.sql). */
    const val PRODUCTS_TABLE: String = "products"
    const val CATEGORIES_TABLE: String = "categories"
    const val PROMOTIONS_TABLE: String = "promotions"

    /** Bucket de Storage con las imágenes de producto. */
    const val IMAGES_BUCKET: String = "inventory"

    val isConfigured: Boolean
        get() = !URL.contains("YOUR-PROJECT-REF") && !ANON_KEY.startsWith("YOUR-")

    /**
     * Resuelve una entrada de `products.imagenes` a URL cargable:
     * si ya es una URL absoluta se usa tal cual; si es un path relativo se asume
     * que vive en el bucket público [IMAGES_BUCKET] de Storage.
     */
    fun publicImageUrl(pathOrUrl: String): String =
        if (pathOrUrl.startsWith("http")) pathOrUrl
        else "$URL/storage/v1/object/public/$IMAGES_BUCKET/${pathOrUrl.removePrefix("/")}"
}
