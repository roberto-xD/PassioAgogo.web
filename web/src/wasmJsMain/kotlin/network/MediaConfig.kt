package network

/**
 * Archivos multimedia propios (videos, imágenes de portada) alojados en Supabase Storage.
 *
 * Los valores son **paths relativos al bucket** [SupabaseConfig.IMAGES_BUCKET]; también
 * se acepta una URL absoluta si el archivo vive en otro sitio (CDN, otro bucket…).
 */
object MediaConfig {
    /** Video de presentación de la sección "Video". */
    const val PRESENTACION_PATH: String = "video/presentacion.mp4"

    /** Imagen mostrada antes de reproducir. Vacío = sin portada. */
    const val PRESENTACION_POSTER_PATH: String = ""

    val presentacionUrl: String
        get() = SupabaseConfig.publicStorageUrl(PRESENTACION_PATH)

    val presentacionPosterUrl: String?
        get() = PRESENTACION_POSTER_PATH
            .takeIf { it.isNotBlank() }
            ?.let(SupabaseConfig::publicStorageUrl)

    /** Sin proyecto configurado no se puede construir una URL válida de Storage. */
    val isConfigured: Boolean
        get() = SupabaseConfig.isConfigured && PRESENTACION_PATH.isNotBlank()
}
