package di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import network.NetworkRepository
import org.koin.dsl.module

val provideRepositoryModule = module {
    single<NetworkRepository> {
        NetworkRepository(
            HttpClient{
                install(ContentNegotiation){
                    json()
                }
                /*install(Logging){
                    logger = Logger.DEFAULT
                    level = LogLevel.ALL
                    logger = object : Logger{
                        override fun log(message: String) {
                            print("servicio: $message")
                        }
                    }
                }*/
            }
        )
    }
}