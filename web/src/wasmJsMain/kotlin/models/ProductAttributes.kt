package models

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import ui.pgmodels.PGAttributeChip

/**
 * Traduce los `attributes` (jsonb) de un producto a las chips de la ficha.
 *
 * La base guarda el dato en crudo —`{"material": "silicona"}`— y el rótulo bonito vive
 * aparte, en `attribute_presets`. Aquí se juntan los dos.
 *
 * El criterio de fondo es que **ningún atributo capturado desaparezca en silencio**: si
 * un par clave-valor no tiene preset, se muestra igual con el texto crudo presentable.
 * Una chip fea avisa de que falta darla de alta; una chip ausente no avisa de nada.
 */

/** Valores que significan "sí": la chip la nombra la clave, no el valor. */
private val AFIRMATIVOS = setOf("si", "sí", "s", "yes", "true", "1")

/** Valores que significan "no": no se pinta chip. Ver [buildAttributeChips]. */
private val NEGATIVOS = setOf("no", "false", "0")

private fun normalizar(texto: String): String = texto.trim().lowercase()

/**
 * Misma normalización que el índice único de la tabla: mayúsculas y espacios dan igual.
 * Separa con un carácter nulo, que ninguna clave ni valor puede contener: así dos pares
 * distintos nunca se confunden al concatenarlos.
 */
private fun clavePar(clave: String, valor: String): String =
    normalizar(clave) + "\u0000" + normalizar(valor)

/** Índice por par clave+valor para resolver cada atributo en una sola pasada. */
fun attributePresetIndex(presets: List<AttributePresetDto>): Map<String, AttributePresetDto> =
    presets.filter { it.clave.isNotBlank() && it.valor.isNotBlank() }
        .associateBy { clavePar(it.clave, it.valor) }

/**
 * Chips de un producto, ya ordenadas para pintarse.
 *
 * Reglas cuando el par no tiene preset:
 * - Valor afirmativo (`{"recargable": "si"}`) → la chip dice **Recargable**. El valor
 *   solo, "Si", no informaría de nada.
 * - Valor negativo (`{"impermeable": "no"}`) → **no se pinta**. Estas chips son un
 *   argumento de venta; enumerar lo que el producto no hace juega en contra, y un
 *   "Impermeable" a secas mentiría.
 * - Cualquier otro valor → la chip dice el valor: **Silicona**.
 *
 * @param presetsByPair índice de [attributePresetIndex].
 */
fun buildAttributeChips(
    attributes: JsonObject?,
    presetsByPair: Map<String, AttributePresetDto>,
): List<PGAttributeChip> {
    if (attributes.isNullOrEmpty()) return emptyList()

    // El orden del preset manda; lo que no tiene preset se va al final.
    val ordenadas = mutableListOf<Pair<Int, PGAttributeChip>>()

    attributes.forEach { (clave, elemento) ->
        valoresDe(elemento).forEach { valor ->
            val preset = presetsByPair[clavePar(clave, valor)]
            val chip = when {
                preset != null -> PGAttributeChip(
                    label = preset.identificador.trim(),
                    emoji = preset.emoji.orEmpty().trim(),
                )
                normalizar(valor) in NEGATIVOS -> return@forEach
                normalizar(valor) in AFIRMATIVOS -> PGAttributeChip(label = presentable(clave))
                else -> PGAttributeChip(label = presentable(valor))
            }
            if (chip.label.isNotBlank()) {
                ordenadas += (preset?.orden ?: Int.MAX_VALUE) to chip
            }
        }
    }

    return ordenadas
        .sortedBy { it.first }
        .map { it.second }
        // Dos claves distintas pueden llevar al mismo rótulo; una sola chip basta.
        .distinctBy { it.label.lowercase() }
}

/**
 * Valores de un atributo. Un arreglo —`{"colores": ["negro", "rosa"]}`— da una chip por
 * elemento. Los objetos anidados se ignoran: no hay forma sensata de rotularlos.
 */
private fun valoresDe(elemento: JsonElement): List<String> = when (elemento) {
    is JsonArray -> elemento.mapNotNull { hijo ->
        (hijo as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }
    is JsonPrimitive -> listOfNotNull(elemento.contentOrNull?.takeIf { it.isNotBlank() })
    else -> emptyList()
}

/** "tamano_chico" → "Tamano chico". Lo justo para que un texto crudo no chirríe. */
private fun presentable(crudo: String): String = crudo.trim()
    .replace('_', ' ')
    .replace('-', ' ')
    .replaceFirstChar { it.uppercase() }
