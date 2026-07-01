package network

/**
 * Configuración de acceso a la API del catálogo.
 *
 * SEGURIDAD:
 * - La clave anterior estuvo hardcodeada en el repositorio y quedó expuesta en el
 *   historial de git. DEBE rotarse en AWS API Gateway antes de salir a producción.
 * - En una app web/WASM cualquier valor aquí viaja al cliente y es visible. Lo ideal
 *   es exponer el catálogo mediante un endpoint público o un backend/proxy propio en
 *   lugar de embeber una API key. Mientras tanto, inyéctala en tiempo de build en vez
 *   de escribirla en el código fuente.
 */
object ApiConfig {
    const val CATALOG_URL: String =
        "https://bjlgneijbl.execute-api.us-east-2.amazonaws.com/dev/v1/catalog"

    // TODO: rotar la clave y proveerla vía configuración de build, no en el código.
    const val API_KEY: String = ""
}
