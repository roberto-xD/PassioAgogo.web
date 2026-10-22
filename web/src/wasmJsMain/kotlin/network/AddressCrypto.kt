package network

import kotlinx.coroutines.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.js.Promise

/** Versión del esquema de sobre. Sube si cambian los algoritmos. */
const val CRYPTO_VERSION = 1

/** Vueltas de PBKDF2 al derivar la clave del cliente. Se guarda junto a la sal. */
const val KDF_ITERACIONES = 210_000

/** Dónde se recuerda la clave derivada mientras la pestaña siga abierta. */
private const val CLAVE_SESION = "passio_kek_v1"

/** La dirección en claro. Solo existe así en memoria y en pantalla. */
@Serializable
data class Direccion(
    val calle: String = "",
    val interior: String = "",
    val colonia: String = "",
    val cp: String = "",
    val ciudad: String = "",
    val estado: String = "",
    val referencias: String = "",
) {
    val vacia: Boolean
        get() = listOf(calle, colonia, cp, ciudad, estado).all { it.isBlank() }

    /** Una línea para enseñarla de un vistazo. */
    fun resumen(): String = listOf(
        listOf(calle, interior).filter { it.isNotBlank() }.joinToString(" int. "),
        colonia,
        listOf(cp, ciudad).filter { it.isNotBlank() }.joinToString(" "),
        estado,
    ).filter { it.isNotBlank() }.joinToString(", ")
}

/** El sobre cerrado, tal y como viaja a la base. */
@Serializable
data class SobreDireccion(
    val version: Int = CRYPTO_VERSION,
    val contenido: String = "",
    val iv: String = "",
    @SerialName("dk_usuario") val dkUsuario: String = "",
    @SerialName("iv_usuario") val ivUsuario: String = "",
    @SerialName("salt_usuario") val saltUsuario: String = "",
    @SerialName("kdf_iteraciones") val kdfIteraciones: Int = KDF_ITERACIONES,
    @SerialName("dk_admin") val dkAdmin: String = "",
    @SerialName("admin_key_id") val adminKeyId: String = "",
)

/**
 * Cifrado extremo a extremo de la dirección.
 *
 * Por cada dirección se genera una clave al azar, se cifra el contenido con ella y
 * **esa clave se envuelve dos veces**: con la clave del cliente y con la pública de
 * administración. La base guarda los tres bultos y ninguna clave con la que abrirlos.
 *
 * **Lo que se recuerda no es la contraseña, es la clave derivada de ella.** La
 * contraseña solo existe en el instante del acceso; a partir de ahí se trabaja con la
 * clave, guardada en `sessionStorage`, que muere al cerrar la pestaña. Guardar la
 * contraseña dejaría a mano algo que además abre la cuenta entera, no solo la
 * dirección.
 *
 * La sal del cliente vive en su perfil y no es secreta: su trabajo es que la misma
 * contraseña en dos cuentas distintas no dé la misma clave.
 *
 * Todo el trabajo criptográfico ocurre dentro de una sola expresión `js()`. Es a
 * propósito: pasear `ArrayBuffer` y `CryptoKey` entre Kotlin/Wasm y JavaScript a cada
 * paso multiplicaría las ocasiones de equivocarse en un sitio donde el error no se ve
 * —el dato sale cifrado igual— y no se descubre hasta que alguien no puede abrir su
 * propia dirección.
 */
object AddressCrypto {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Sal nueva para un cliente que aún no tiene. No es secreta. */
    suspend fun nuevaSal(): String? = runCatching {
        salJs().await<JsString>().toString().takeIf { !it.startsWith("ERROR") }
    }.getOrNull()

    /**
     * Deriva la clave del cliente y la recuerda en esta pestaña.
     *
     * Se llama justo al acceder, que es el único momento en que la web ve la
     * contraseña. Devuelve `false` si no se pudo derivar.
     */
    suspend fun derivarYRecordar(password: String, sal: String): Boolean {
        if (password.isBlank() || sal.isBlank()) return false
        val kek = runCatching {
            derivarJs(password, sal, KDF_ITERACIONES).await<JsString>().toString()
        }.getOrNull() ?: return false
        if (kek.startsWith("ERROR")) return false
        guardarSesion(CLAVE_SESION, kek)
        return true
    }

    /** Se olvida al cerrar sesión: dejarla puesta sería dejar la puerta entornada. */
    fun olvidarClave() = borrarSesion(CLAVE_SESION)

    /** `true` si en esta pestaña se puede abrir o escribir una dirección. */
    fun hayClave(): Boolean = !leerSesion(CLAVE_SESION).isNullOrBlank()

    /**
     * Cierra el sobre.
     *
     * @param adminPublica clave pública de `crypto_keys`, en base64 (SPKI).
     * @return el sobre, o `null` si esta pestaña no tiene clave o algo falló.
     */
    suspend fun cifrar(
        direccion: Direccion,
        sal: String,
        adminKeyId: String,
        adminPublica: String,
    ): SobreDireccion? {
        val kek = leerSesion(CLAVE_SESION)?.takeIf { it.isNotBlank() } ?: return null
        val claro = json.encodeToString(Direccion.serializer(), direccion)

        val crudo = runCatching {
            cifrarJs(claro, kek, adminPublica).await<JsString>().toString()
        }.getOrNull() ?: return null
        if (crudo.startsWith("ERROR")) return null

        val partes = crudo.split('|')
        if (partes.size != 5) return null
        return SobreDireccion(
            version = CRYPTO_VERSION,
            contenido = partes[0],
            iv = partes[1],
            dkUsuario = partes[2],
            ivUsuario = partes[3],
            saltUsuario = sal,
            kdfIteraciones = KDF_ITERACIONES,
            dkAdmin = partes[4],
            adminKeyId = adminKeyId,
        )
    }

    /**
     * Abre el sobre con la clave de esta pestaña.
     *
     * Devuelve `null` cuando no se puede, sin decir por qué: puede ser que la pestaña
     * no tenga clave, o que el cliente restableciera su contraseña y su copia del sobre
     * ya no abra. Quien llama decide qué contarle a la persona.
     */
    suspend fun descifrar(sobre: SobreDireccion): Direccion? {
        val kek = leerSesion(CLAVE_SESION)?.takeIf { it.isNotBlank() } ?: return null

        val claro = runCatching {
            descifrarJs(sobre.contenido, sobre.iv, sobre.dkUsuario, sobre.ivUsuario, kek)
                .await<JsString>().toString()
        }.getOrNull() ?: return null
        if (claro.startsWith("ERROR")) return null

        return runCatching { json.decodeFromString(Direccion.serializer(), claro) }.getOrNull()
    }
}

// ------------------------------------------------------------
// WebCrypto. Cada una devuelve su resultado en base64, o "ERROR: …".
// ------------------------------------------------------------

private fun salJs(): Promise<JsString> = js(
    """(function () {
        return (async function () {
            try {
                var s = crypto.getRandomValues(new Uint8Array(16)), out = '';
                for (var i = 0; i < s.length; i++) out += String.fromCharCode(s[i]);
                return btoa(out);
            } catch (e) { return 'ERROR'; }
        })();
    })()"""
)

/** Contraseña + sal → clave AES-256 en bruto, base64. */
private fun derivarJs(password: String, sal: String, iteraciones: Int): Promise<JsString> = js(
    """(function (pwd, salt64, iters) {
        var enc = new TextEncoder();
        var unb64 = function (s) {
            var raw = atob(s), a = new Uint8Array(raw.length);
            for (var i = 0; i < raw.length; i++) a[i] = raw.charCodeAt(i);
            return a;
        };
        return (async function () {
            try {
                var base = await crypto.subtle.importKey(
                    'raw', enc.encode(pwd), 'PBKDF2', false, ['deriveKey']);
                var kek = await crypto.subtle.deriveKey(
                    { name: 'PBKDF2', salt: unb64(salt64), iterations: iters,
                      hash: 'SHA-256' },
                    base, { name: 'AES-GCM', length: 256 }, true, ['wrapKey', 'unwrapKey']);
                var raw = new Uint8Array(await crypto.subtle.exportKey('raw', kek)), s = '';
                for (var i = 0; i < raw.length; i++) s += String.fromCharCode(raw[i]);
                return btoa(s);
            } catch (e) {
                return 'ERROR: ' + (e && e.message ? e.message : e);
            }
        })();
    })(password, sal, iteraciones)"""
)

/** → "contenido|iv|dkUsuario|ivUsuario|dkAdmin" */
private fun cifrarJs(
    claro: String,
    kekB64: String,
    adminPublicaB64: String,
): Promise<JsString> = js(
    """(function (plain, kek64, adminPub) {
        var enc = new TextEncoder();
        var b64 = function (buf) {
            var b = new Uint8Array(buf), s = '';
            for (var i = 0; i < b.length; i++) s += String.fromCharCode(b[i]);
            return btoa(s);
        };
        var unb64 = function (s) {
            var raw = atob(s), a = new Uint8Array(raw.length);
            for (var i = 0; i < raw.length; i++) a[i] = raw.charCodeAt(i);
            return a;
        };
        return (async function () {
            try {
                var dk = await crypto.subtle.generateKey(
                    { name: 'AES-GCM', length: 256 }, true, ['encrypt', 'decrypt']);
                var iv = crypto.getRandomValues(new Uint8Array(12));
                var ct = await crypto.subtle.encrypt(
                    { name: 'AES-GCM', iv: iv }, dk, enc.encode(plain));

                var kek = await crypto.subtle.importKey(
                    'raw', unb64(kek64), { name: 'AES-GCM' }, false, ['wrapKey']);
                var ivU = crypto.getRandomValues(new Uint8Array(12));
                var dkU = await crypto.subtle.wrapKey(
                    'raw', dk, kek, { name: 'AES-GCM', iv: ivU });

                var pub = await crypto.subtle.importKey(
                    'spki', unb64(adminPub),
                    { name: 'RSA-OAEP', hash: 'SHA-256' }, false, ['wrapKey']);
                var dkA = await crypto.subtle.wrapKey('raw', dk, pub, { name: 'RSA-OAEP' });

                return [b64(ct), b64(iv), b64(dkU), b64(ivU), b64(dkA)].join('|');
            } catch (e) {
                return 'ERROR: ' + (e && e.message ? e.message : e);
            }
        })();
    })(claro, kekB64, adminPublicaB64)"""
)

private fun descifrarJs(
    contenido: String,
    iv: String,
    dkUsuario: String,
    ivUsuario: String,
    kekB64: String,
): Promise<JsString> = js(
    """(function (ct64, iv64, dkU64, ivU64, kek64) {
        var dec = new TextDecoder();
        var unb64 = function (s) {
            var raw = atob(s), a = new Uint8Array(raw.length);
            for (var i = 0; i < raw.length; i++) a[i] = raw.charCodeAt(i);
            return a;
        };
        return (async function () {
            try {
                var kek = await crypto.subtle.importKey(
                    'raw', unb64(kek64), { name: 'AES-GCM' }, false, ['unwrapKey']);
                var dk = await crypto.subtle.unwrapKey(
                    'raw', unb64(dkU64), kek, { name: 'AES-GCM', iv: unb64(ivU64) },
                    { name: 'AES-GCM', length: 256 }, false, ['decrypt']);
                return dec.decode(await crypto.subtle.decrypt(
                    { name: 'AES-GCM', iv: unb64(iv64) }, dk, unb64(ct64)));
            } catch (e) {
                return 'ERROR: ' + (e && e.message ? e.message : e);
            }
        })();
    })(contenido, iv, dkUsuario, ivUsuario, kekB64)"""
)

// ------------------------------------------------------------
// sessionStorage: vive mientras la pestaña, nunca llega al disco.
// ------------------------------------------------------------

private fun guardarSesion(clave: String, valor: String) {
    js(
        """(function (k, v) {
            try { window.sessionStorage.setItem(k, v); } catch (e) {}
        })(clave, valor)"""
    )
}

private fun leerSesion(clave: String): String? = js(
    """(function (k) {
        try { return window.sessionStorage.getItem(k); } catch (e) { return null; }
    })(clave)"""
)

private fun borrarSesion(clave: String) {
    js(
        """(function (k) {
            try { window.sessionStorage.removeItem(k); } catch (e) {}
        })(clave)"""
    )
}
