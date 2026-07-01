package network

/**
 * Configuración de acceso a la API del catálogo.
 *
 * SEGURIDAD:
 * - La clave anterior estuvo hardcodeada y quedó expuesta en el historial de git.
 *   DEBE rotarse en AWS API Gateway antes de salir a producción.
 * - En una app web cualquier valor aquí viaja al cliente y es visible. Lo ideal es
 *   exponer el catálogo mediante un endpoint público o un backend/proxy propio en
 *   lugar de embeber una API key. Mientras tanto, provéela en tiempo de build (no
 *   la escribas en el código fuente).
 *
 * Mientras [API_KEY] esté vacía, las peticiones se envían sin cabecera x-api-key y
 * el catálogo mostrará un estado vacío en lugar de fallar.
 */
object ApiConfig {
    const val CATALOG_URL: String =
        "https://bjlgneijbl.execute-api.us-east-2.amazonaws.com/dev/v1/catalog"

    // TODO: rotar la clave y proveerla vía configuración de build, no en el código.
    const val API_KEY: String = ""

    val hasApiKey: Boolean get() = API_KEY.isNotEmpty()
}
