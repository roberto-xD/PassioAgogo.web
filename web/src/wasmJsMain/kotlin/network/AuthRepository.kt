package network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import models.ProfileDto

/**
 * Autenticación con Supabase Auth.
 *
 * La sesión la persiste el propio SDK en el almacenamiento del navegador y la restaura al
 * cargar la página, así que aquí no se guarda ningún token a mano.
 *
 * Al registrarse se envía el nombre en los metadatos del alta: el trigger
 * `handle_new_user` (script 02) lo usa para crear la fila de `profiles`; si no llega,
 * cae en la parte local del correo.
 */
class AuthRepository(
    supabaseProvider: () -> SupabaseClient = ::createSupabase,
) {
    private val supabase: SupabaseClient by lazy(supabaseProvider)

    /** `true` mientras haya sesión iniciada. */
    val isAuthenticated: Flow<Boolean>
        get() = if (!SupabaseConfig.isConfigured) flowOf(false)
        else supabase.auth.sessionStatus.map { it is SessionStatus.Authenticated }

    /** Correo del usuario actual, o cadena vacía si no hay sesión. */
    fun currentEmail(): String =
        if (!SupabaseConfig.isConfigured) ""
        else supabase.auth.currentUserOrNull()?.email.orEmpty()

    /** Devuelve un mensaje de error legible, o `null` si la operación fue correcta. */
    suspend fun signIn(email: String, password: String): String? = runAuth {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    /**
     * Alta de usuario. Si el proyecto exige confirmación por correo, el alta no abre
     * sesión: el usuario debe confirmar antes de poder entrar.
     */
    suspend fun signUp(email: String, password: String, nombre: String): String? = runAuth {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject { put("nombre", nombre) }
        }
    }

    suspend fun signOut(): String? = runAuth { supabase.auth.signOut() }

    /** Perfil del usuario autenticado; `null` si no hay sesión o no se pudo leer. */
    suspend fun loadProfile(): ProfileDto? {
        if (!SupabaseConfig.isConfigured) return null
        val userId = supabase.auth.currentUserOrNull()?.id ?: return null
        return runCatching {
            supabase.from("profiles")
                .select(columns = Columns.raw("id, nombre, rol, activo")) {
                    filter { eq("id", userId) }
                }
                .decodeSingleOrNull<ProfileDto>()
        }.getOrNull()
    }

    private suspend fun runAuth(block: suspend () -> Unit): String? =
        if (!SupabaseConfig.isConfigured) {
            "El acceso aún no está configurado."
        } else {
            try {
                block()
                null
            } catch (e: Exception) {
                friendlyAuthError(e.message.orEmpty())
            }
        }
}

/**
 * Traduce los mensajes de la API a algo entendible. Se comparan cadenas porque los
 * códigos concretos varían entre versiones del backend.
 */
private fun friendlyAuthError(raw: String): String {
    val message = raw.lowercase()
    return when {
        "invalid login" in message || "invalid credentials" in message ->
            "Correo o contraseña incorrectos."
        "email not confirmed" in message ->
            "Tu correo aún no está confirmado. Revisa la bandeja de entrada."
        "already registered" in message || "already been registered" in message ->
            "Ese correo ya tiene una cuenta. Inicia sesión."
        "password" in message && "least" in message ->
            "La contraseña es demasiado corta."
        "rate limit" in message || "too many" in message ->
            "Demasiados intentos. Espera un momento e inténtalo de nuevo."
        "failed to fetch" in message || "network" in message ->
            "No se pudo conectar. Revisa tu conexión."
        else -> "No se pudo completar la operación. Inténtalo de nuevo."
    }
}
