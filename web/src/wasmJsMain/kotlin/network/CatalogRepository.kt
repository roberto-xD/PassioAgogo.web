package network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.Flow
import models.PGCatalog

class CatalogRepository(
    httpClientProvider: () -> HttpClient = ::createHttpClient,
) {
    // Se construye solo la primera vez que se necesita (es decir, cuando hay API key
    // y se hace una llamada real). Con la key vacía el motor de Ktor nunca se instancia.
    private val httpClient: HttpClient by lazy(httpClientProvider)

    fun getCatalog(store: String? = null): Flow<NetworkResult<PGCatalog>> = toResultFlow {
        // Sin API key no tiene sentido llamar al servicio (respondería 403); se
        // devuelve un catálogo vacío para que la UI muestre un estado limpio.
        if (!ApiConfig.hasApiKey) {
            return@toResultFlow NetworkResult.Success(PGCatalog(items = emptyList()))
        }

        val response = httpClient.get(ApiConfig.CATALOG_URL) {
            headers {
                append("x-api-key", ApiConfig.API_KEY)
                append(HttpHeaders.Accept, "application/json")
            }
            if (store != null) parameter("store", store)
        }
        NetworkResult.Success(response.body<PGCatalog>())
    }
}
