package network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** La clave pública con la que se envuelve la copia de administración. */
@Serializable
data class ClaveAdminDto(
    val id: String = "",
    val publica: String = "",
)

/** La fila de `user_addresses` tal y como viaja. Todo salvo `etiqueta` va cifrado. */
@Serializable
data class DireccionFilaDto(
    val id: String? = null,
    val etiqueta: String? = null,
    val version: Int = CRYPTO_VERSION,
    val contenido: String = "",
    val iv: String = "",
    @SerialName("dk_usuario") val dkUsuario: String = "",
    @SerialName("iv_usuario") val ivUsuario: String = "",
    @SerialName("salt_usuario") val saltUsuario: String = "",
    @SerialName("kdf_iteraciones") val kdfIteraciones: Int = KDF_ITERACIONES,
    @SerialName("dk_admin") val dkAdmin: String = "",
    @SerialName("admin_key_id") val adminKeyId: String = "",
) {
    fun aSobre(): SobreDireccion = SobreDireccion(
        version = version,
        contenido = contenido,
        iv = iv,
        dkUsuario = dkUsuario,
        ivUsuario = ivUsuario,
        saltUsuario = saltUsuario,
        kdfIteraciones = kdfIteraciones,
        dkAdmin = dkAdmin,
        adminKeyId = adminKeyId,
    )
}

/**
 * La dirección cifrada del cliente (31_direcciones_cifradas).
 *
 * Este repositorio **nunca ve la dirección en claro**: mueve sobres cerrados. Abrirlos y
 * cerrarlos es trabajo de [AddressCrypto], que es el único que toca la clave.
 */
class AddressRepository(
    supabaseProvider: () -> SupabaseClient = ::createSupabase,
) {
    private val supabase: SupabaseClient by lazy(supabaseProvider)

    /** La clave activa de administración, o `null` si no hay ninguna dada de alta. */
    suspend fun claveAdmin(): ClaveAdminDto? {
        if (!SupabaseConfig.isConfigured) return null
        return runCatching {
            supabase.from(SupabaseConfig.CRYPTO_KEYS_TABLE)
                .select(columns = Columns.raw("id, publica")) {
                    filter { eq("activa", true) }
                }
                .decodeList<ClaveAdminDto>()
                .firstOrNull { it.id.isNotBlank() && it.publica.isNotBlank() }
        }.getOrNull()
    }

    /** El sobre guardado, o `null` si esta persona aún no tiene dirección. */
    suspend fun sobreGuardado(): DireccionFilaDto? {
        if (!SupabaseConfig.isConfigured) return null
        supabase.auth.currentUserOrNull() ?: return null
        return runCatching {
            supabase.from(SupabaseConfig.USER_ADDRESSES_TABLE)
                .select(
                    columns = Columns.raw(
                        "id, etiqueta, version, contenido, iv, dk_usuario, iv_usuario, " +
                            "salt_usuario, kdf_iteraciones, dk_admin, admin_key_id"
                    )
                )
                .decodeList<DireccionFilaDto>()
                .firstOrNull()
        }.getOrNull()
    }

    /**
     * Guarda el sobre. Una por persona, así que se reemplaza la que hubiera.
     *
     * @return `null` si fue bien, o un mensaje para la persona.
     */
    suspend fun guardar(sobre: SobreDireccion, etiqueta: String): String? {
        if (!SupabaseConfig.isConfigured) return "El guardado aún no está configurado."
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: return "Tu sesión expiró. Vuelve a entrar."

        val fila = buildJsonObject {
            put("user_id", userId)
            if (etiqueta.isBlank()) put("etiqueta", JsonNull) else put("etiqueta", etiqueta.trim())
            put("version", sobre.version)
            put("contenido", sobre.contenido)
            put("iv", sobre.iv)
            put("dk_usuario", sobre.dkUsuario)
            put("iv_usuario", sobre.ivUsuario)
            put("salt_usuario", sobre.saltUsuario)
            put("kdf_iteraciones", sobre.kdfIteraciones)
            put("dk_admin", sobre.dkAdmin)
            put("admin_key_id", sobre.adminKeyId)
        }

        return try {
            // `upsert` sobre el índice único de user_id: una dirección por persona.
            supabase.from(SupabaseConfig.USER_ADDRESSES_TABLE)
                .upsert(fila) { onConflict = "user_id" }
            null
        } catch (e: Exception) {
            mensajeDireccion(e.message.orEmpty())
        }
    }

    /** La borra. El sobre se va entero; no queda copia que alguien pueda abrir luego. */
    suspend fun borrar(): String? {
        if (!SupabaseConfig.isConfigured) return null
        val userId = supabase.auth.currentUserOrNull()?.id ?: return null
        return try {
            supabase.from(SupabaseConfig.USER_ADDRESSES_TABLE)
                .delete { filter { eq("user_id", userId) } }
            null
        } catch (e: Exception) {
            mensajeDireccion(e.message.orEmpty())
        }
    }

    /** Guarda la sal del cliente en su perfil. No es secreta. */
    suspend fun guardarSal(sal: String): Boolean {
        if (!SupabaseConfig.isConfigured) return false
        val userId = supabase.auth.currentUserOrNull()?.id ?: return false
        return runCatching {
            supabase.from("profiles").update(
                buildJsonObject { put("kdf_salt", sal) }
            ) {
                filter { eq("id", userId) }
            }
            true
        }.getOrDefault(false)
    }
}

private fun mensajeDireccion(raw: String): String {
    val m = raw.lowercase()
    return when {
        "row-level security" in m || "violates row-level" in m ->
            "No tienes permiso para guardar esta dirección."
        // Falta dar de alta la clave publica de administracion (script 31).
        "admin_key_id" in m || "foreign key" in m ->
            "Falta configurar la clave de seguridad de la tienda. Avísanos."
        "failed to fetch" in m || "network" in m ->
            "No se pudo conectar. Revisa tu conexión e inténtalo de nuevo."
        else -> "No se pudo guardar la dirección. Inténtalo de nuevo."
    }
}
