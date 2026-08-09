package models

import kotlinx.serialization.Serializable
import network.MediaConfig
import network.SupabaseConfig

/** Elemento de la galería (tabla `gallery_items`, script 13). */
@Serializable
data class GalleryItemDto(
    val id: String? = null,
    val titulo: String? = null,
    val descripcion: String? = null,
    val imagen: String? = null,
    val categoria: String? = null,
    val orden: Int = 0,
)

/** Datos ya resueltos para pintar una diapositiva del carrusel. */
data class GallerySlide(
    val titulo: String,
    val descripcion: String,
    val categoria: String,
    val imageUrl: String,
)

fun GalleryItemDto.toSlide(): GallerySlide? {
    val path = imagen?.takeIf { it.isNotBlank() } ?: return null
    return GallerySlide(
        titulo = titulo.orEmpty(),
        descripcion = descripcion.orEmpty(),
        categoria = categoria.orEmpty(),
        imageUrl = SupabaseConfig.publicStorageUrl(path, MediaConfig.MEDIA_BUCKET),
    )
}
