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
 * Crea el cliente de Supabase con los módulos instalados.
 *
 * - [Postgrest]: lectura/escritura de datos (catálogo).
 * - [Storage]: imágenes de producto.
 * - [Auth]: preparado para sesiones de usuario (login) a futuro.
 * - [Realtime]: preparado para suscripciones en tiempo real a futuro.
 */
fun createSupabase(): SupabaseClient = createSupabaseClient(
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
