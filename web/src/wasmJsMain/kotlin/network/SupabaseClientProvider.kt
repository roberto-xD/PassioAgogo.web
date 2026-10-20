package network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json

/**
 * El cliente de Supabase de la aplicación.
 *
 * - [Postgrest]: lectura/escritura de datos (catálogo).
 * - [Storage]: imágenes de producto.
 * - [Auth]: sesiones de usuario.
 * - [Realtime]: preparado para suscripciones en tiempo real a futuro.
 *
 * **Hay uno solo, compartido por todos los repositorios.** La sesión vive dentro del
 * cliente: con una instancia por repositorio, la que abre `AuthRepository` al iniciar
 * sesión no la verían los demás, y sus consultas saldrían con la clave anónima en lugar
 * del JWT del usuario. El RLS las rechazaría, y los datos propios —las solicitudes de
 * cada quien— no se podrían ni leer ni escribir.
 *
 * De paso evita abrir una conexión de Realtime por repositorio.
 */
private val cliente: SupabaseClient by lazy {
    createSupabaseClient(
        supabaseUrl = SupabaseConfig.URL,
        supabaseKey = SupabaseConfig.ANON_KEY,
    ) {
        // Tolerante a columnas que el DTO no mapea (p. ej. attributes, timestamps).
        defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })

        install(Postgrest)
        install(Storage)
        install(Auth)
        install(Realtime)
    }
}

fun createSupabase(): SupabaseClient = cliente
