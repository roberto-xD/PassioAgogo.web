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
    const val URL: String = "https://vkykibdblwnmwansjcua.supabase.co"
    const val ANON_KEY: String = "sb_publishable_oJgt6IBSg_ot55uU0HV9oQ_2IwtZZ9V"

    /** Tablas del catálogo (01_catalog.sql / 05_promotions.sql). */
    const val PRODUCTS_TABLE: String = "products"
    const val CATEGORIES_TABLE: String = "categories"
    const val PROMOTIONS_TABLE: String = "promotions"

    /** Diccionario de atributos para las chips de la ficha (19_attribute_presets.sql). */
    const val ATTRIBUTE_PRESETS_TABLE: String = "attribute_presets"

    /** Galería de la portada (13_gallery.sql). */
    const val GALLERY_TABLE: String = "gallery_items"

    /** Eventos y ajustes del sitio (21_events_widget.sql). */
    const val EVENTS_TABLE: String = "events"
    const val SITE_SETTINGS_TABLE: String = "site_settings"

    /** Interruptor del widget flotante de eventos, en `site_settings`. */
    const val SETTING_EVENTS_WIDGET: String = "widget_eventos_visible"

    /** Bucket de Storage con las imágenes de producto. */
    const val IMAGES_BUCKET: String = "inventory"

    val isConfigured: Boolean
        get() = !URL.contains("YOUR-PROJECT-REF") && !ANON_KEY.startsWith("YOUR-")

    /**
     * Resuelve una entrada de `products.imagenes` a URL cargable:
     * si ya es una URL absoluta se usa tal cual; si es un path relativo se asume
     * que vive en el bucket público [IMAGES_BUCKET] de Storage.
     */
    fun publicImageUrl(pathOrUrl: String): String = publicStorageUrl(pathOrUrl)

    /**
     * URL pública de un archivo de Storage. Acepta una URL absoluta (se devuelve tal
     * cual) o un path relativo al bucket indicado.
     */
    fun publicStorageUrl(pathOrUrl: String, bucket: String = IMAGES_BUCKET): String =
        if (pathOrUrl.startsWith("http")) pathOrUrl
        else "$URL/storage/v1/object/public/$bucket/${pathOrUrl.removePrefix("/")}"
}
