package network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import models.PGCatalog

class NetworkRepository(private val httpClient: HttpClient) {

    suspend fun getProductList(
        store: String? = null,
    ): Flow<NetWorkResult<PGCatalog?>> {
        val response =
            httpClient
                .get("https://bjlgneijbl.execute-api.us-east-2.amazonaws.com/dev/v1/catalog"){
                    url{
                        contentType(ContentType.Application.FormUrlEncoded)
                    }
                    headers {
                        append("x-api-key", "TgE8gu5BZR7RoKAhAhi7p3Op2NL7tHf95JXjRxUB")
                        append(HttpHeaders.Accept, "text/html")
                        append(HttpHeaders.UserAgent, "ktor client")
                        append(HttpHeaders.Host,"bjlgneijbl.execute-api.us-east-2.amazonaws.com")
                    }
                    parameter(
                        key = "store",
                        value = store
                    )
                }
        print("status: ${response.status} ")
        when (response.status){
            HttpStatusCode.BadRequest ->{
                print("bad request")
            }
            HttpStatusCode.OK -> {
                print("success request")
            }
        }

        return toResultFlow {
            val cuac = response.body<PGCatalog>()
            NetWorkResult.Success(cuac)
        }
    }
}