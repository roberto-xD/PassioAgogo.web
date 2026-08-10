package models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import network.MediaConfig
import network.SupabaseConfig

/** Rótulo del botón cuando la fila tiene enlace pero no texto (script 16). */
private const val CTA_LABEL_POR_DEFECTO = "Ver más"

/** Elemento de la galería (tabla `gallery_items`, scripts 13 y 16). */
@Serializable
data class GalleryItemDto(
    val id: String? = null,
    val titulo: String? = null,
    val descripcion: String? = null,
    val detalles: String? = null,
    val imagen: String? = null,
    val categoria: String? = null,
    val enlace: String? = null,
    @SerialName("enlace_texto") val enlaceTexto: String? = null,
    val orden: Int = 0,
)

/** Datos ya resueltos para pintar una diapositiva del carrusel. */
data class GallerySlide(
    val titulo: String,
    val descripcion: String,
    val detalles: String,
    val categoria: String,
    val imageUrl: String,
    /** Destino del botón de acción. Vacío = el elemento no muestra botón. */
    val ctaUrl: String,
    /** Rótulo ya resuelto: el de la fila o el predeterminado. */
    val ctaLabel: String,
)

fun GalleryItemDto.toSlide(): GallerySlide? {
    val path = imagen?.takeIf { it.isNotBlank() } ?: return null
    return GallerySlide(
        titulo = titulo.orEmpty(),
        descripcion = descripcion.orEmpty(),
        detalles = detalles.orEmpty(),
        categoria = categoria.orEmpty(),
        imageUrl = SupabaseConfig.publicStorageUrl(path, MediaConfig.MEDIA_BUCKET),
        ctaUrl = enlace?.trim().orEmpty(),
        ctaLabel = enlaceTexto?.trim()?.takeIf { it.isNotBlank() } ?: CTA_LABEL_POR_DEFECTO,
    )
}
