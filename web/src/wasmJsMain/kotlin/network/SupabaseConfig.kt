package network

/**
 * Configuración de acceso a Supabase.
 *
 * SEGURIDAD:
 * - La `ANON_KEY` es una clave **pública por diseño** (viaja al cliente web). No es un
 *   secreto: la protección de datos se hace con políticas RLS (Row Level Security) en la
 *   base de datos. NUNCA pongas aquí la `service_role` key (salta el RLS).
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

    /** Nombre de la tabla del catálogo (ajústalo a tu esquema cuando esté listo). */
    const val PRODUCTS_TABLE: String = "products"

    /** Bucket de Storage con las imágenes de producto. */
    const val IMAGES_BUCKET: String = "product-images"

    val isConfigured: Boolean
        get() = !URL.contains("YOUR-PROJECT-REF") && !ANON_KEY.startsWith("YOUR-")
}
