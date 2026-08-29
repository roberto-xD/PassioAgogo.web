package network

/**
 * Convierte un título en un fragmento de URL legible: "Ah-Con! 2026" → "ah-con-2026".
 *
 * Se usa para que la ficha de un evento tenga una dirección que se entienda al leerla y
 * al compartirla por mensaje, en lugar de un identificador ilegible.
 *
 * Los acentos se quitan descomponiendo el texto en Unicode y retirando las marcas
 * diacríticas: no hay `Normalizer` en Kotlin/Wasm, así que lo hace el propio navegador.
 */
fun slugify(texto: String): String {
    val base = stripDiacritics(texto.trim().lowercase())
    val limpio = base.map { c ->
        when {
            c in 'a'..'z' || c in '0'..'9' -> c
            else -> '-'
        }
    }.joinToString("")
    // Varios separadores seguidos se colapsan y no se dejan en los extremos.
    return limpio.split("-").filter { it.isNotEmpty() }.joinToString("-")
}

private fun stripDiacritics(value: String): String =
    js("value.normalize('NFD').replace(/[\\u0300-\\u036f]/g, '')")
