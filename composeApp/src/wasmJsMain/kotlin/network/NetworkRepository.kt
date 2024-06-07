package network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow

class NetworkRepository(private val httpClient: HttpClient) {

    fun getProductList(): Flow<NetWorkResult<String?>> {
        return toResultFlow {
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
                    }
            NetWorkResult.Success(response.bodyAsText())
        }
    }
}