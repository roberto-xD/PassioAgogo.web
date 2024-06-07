package data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

suspend fun client(): String{
    val client = HttpClient()
    val response: HttpResponse = client.get("https://bjlgneijbl.execute-api.us-east-2.amazonaws.com/dev/v1/catalog/"){
        url{
            parameters.append("x-api-key","TgE8gu5BZR7RoKAhAhi7p3Op2NL7tHf95JXjRxUB")
            contentType(ContentType.Application.FormUrlEncoded)
        }
    }

    return response.bodyAsText()
}