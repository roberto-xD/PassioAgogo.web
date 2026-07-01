package network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Cliente HTTP compartido, configurado para JSON tolerante a campos desconocidos. */
fun createHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) {
        json(
            json = Json {
                ignoreUnknownKeys = true
                isLenient = true
            },
            contentType = ContentType.Any,
        )
    }
}
