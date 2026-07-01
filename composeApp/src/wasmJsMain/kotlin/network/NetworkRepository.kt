package network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.Flow
import models.PGCatalog

class NetworkRepository(private val httpClient: HttpClient) {

    suspend fun getProductList(
        store: String? = null,
    ): Flow<NetWorkResult<PGCatalog?>> = toResultFlow {
        val response = httpClient.get(ApiConfig.CATALOG_URL) {
            headers {
                if (ApiConfig.API_KEY.isNotEmpty()) {
                    append("x-api-key", ApiConfig.API_KEY)
                }
                append(HttpHeaders.Accept, "application/json")
            }
            if (store != null) {
                parameter("store", store)
            }
        }
        NetWorkResult.Success(response.body<PGCatalog>())
    }
}
