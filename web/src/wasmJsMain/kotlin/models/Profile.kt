package models

import kotlinx.serialization.Serializable

/**
 * Perfil del usuario autenticado (tabla `profiles`, script 02).
 *
 * Lo crea automáticamente el trigger `handle_new_user` al registrarse, tomando el nombre
 * de los metadatos del alta. El RLS solo permite a cada usuario leer su propio perfil
 * (o al staff, cualquiera).
 */
@Serializable
data class ProfileDto(
    val id: String? = null,
    val nombre: String? = null,
    val rol: String? = null, // admin | vendedor | cliente
    val activo: Boolean = true,
)
