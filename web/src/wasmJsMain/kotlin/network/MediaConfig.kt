package network

/**
 * Archivos multimedia propios (videos, imágenes de portada) alojados en Supabase Storage.
 *
 * Los valores son **paths relativos a [MEDIA_BUCKET]**; también se acepta una URL absoluta
 * si el archivo vive en otro sitio (CDN, otro bucket…).
 *
 * La URL pública resultante tiene esta forma —útil para comprobarla a mano en el
 * navegador— donde `<bucket>` y `<path>` son los valores de abajo:
 *
 *     https://<proyecto>.supabase.co/storage/v1/object/public/<bucket>/<path>
 */
object MediaConfig {
    /**
     * Bucket que contiene los archivos multimedia. Por defecto es el mismo de las imágenes
     * de producto; cámbialo si subiste los videos a un bucket propio (p. ej. "video").
     * El bucket debe ser **público**.
     */
    const val MEDIA_BUCKET: String = SupabaseConfig.IMAGES_BUCKET

    /** Video de presentación de la sección "Video", relativo a [MEDIA_BUCKET]. */
    const val PRESENTACION_PATH: String = "video/presentacion.mp4"

    /** Imagen mostrada antes de reproducir. Vacío = sin portada. */
    const val PRESENTACION_POSTER_PATH: String = ""

    val presentacionUrl: String
        get() = SupabaseConfig.publicStorageUrl(PRESENTACION_PATH, MEDIA_BUCKET)

    val presentacionPosterUrl: String?
        get() = PRESENTACION_POSTER_PATH
            .takeIf { it.isNotBlank() }
            ?.let { SupabaseConfig.publicStorageUrl(it, MEDIA_BUCKET) }

    /** Sin proyecto configurado no se puede construir una URL válida de Storage. */
    val isConfigured: Boolean
        get() = SupabaseConfig.isConfigured && PRESENTACION_PATH.isNotBlank()

    // ------------------------------------------------------------------
    // Podcast de la marca (Spotify)
    // ------------------------------------------------------------------

    /**
     * ID del show en Spotify: el código del enlace de *Compartir → Copiar enlace*
     * (`https://open.spotify.com/show/<id>`), sin parámetros.
     */
    const val PODCAST_SHOW_ID: String = "5as5H9vDI4JQwrwPvzddqw"

    val isPodcastConfigured: Boolean
        get() = !PODCAST_SHOW_ID.startsWith("YOUR-") && PODCAST_SHOW_ID.isNotBlank()

    /** Enlace al show en Spotify, para abrirlo fuera del reproductor incrustado. */
    val podcastShowUrl: String
        get() = "https://open.spotify.com/show/$PODCAST_SHOW_ID"
}
