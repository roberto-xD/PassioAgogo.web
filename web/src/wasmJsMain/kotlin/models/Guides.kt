package models

import kotlinx.serialization.Serializable

/** Fila de `guides` (22_guides.sql). */
@Serializable
data class GuideDto(
    val id: String? = null,
    val titulo: String? = null,
    val resumen: String? = null,
    val emoji: String? = null,
    val uso: String? = null,
    val limpieza: String? = null,
    val cuidados: String? = null,
    val advertencias: String? = null,
)

/** Un bloque de la guía con su rótulo, ya listo para pintarse. */
data class GuideBlock(
    val label: String,
    val text: String,
)

/**
 * Guía lista para la pantalla.
 *
 * Los bloques vacíos no llegan hasta aquí: [blocks] solo contiene los que tienen texto,
 * así la UI no necesita comprobar nada antes de recorrerlos.
 */
data class GuideItem(
    val id: String,
    val titulo: String,
    val resumen: String,
    val emoji: String,
    val blocks: List<GuideBlock>,
    val advertencias: String,
)

fun GuideDto.toItem(): GuideItem? {
    val id = id ?: return null
    val titulo = titulo?.takeIf { it.isNotBlank() } ?: return null

    val blocks = listOfNotNull(
        uso?.takeIf { it.isNotBlank() }?.let { GuideBlock("Uso", it.trim()) },
        limpieza?.takeIf { it.isNotBlank() }?.let { GuideBlock("Limpieza", it.trim()) },
        cuidados?.takeIf { it.isNotBlank() }?.let { GuideBlock("Cuidados", it.trim()) },
    )
    // Sin nada que contar la guía no se muestra, aunque la fila exista.
    if (blocks.isEmpty()) return null

    return GuideItem(
        id = id,
        titulo = titulo.trim(),
        resumen = resumen.orEmpty().trim(),
        emoji = emoji.orEmpty().trim(),
        blocks = blocks,
        advertencias = advertencias.orEmpty().trim(),
    )
}
