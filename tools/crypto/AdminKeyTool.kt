package tools.crypto

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * Claves de administración para las direcciones cifradas.
 *
 * **Este archivo no pertenece a la web: es para pegarlo en la app de control.** Vive
 * aquí para que las dos mitades del cifrado se lean juntas y no se separen con el
 * tiempo.
 *
 * Requiere Android API 26 o superior (PBKDF2WithHmacSHA256 y java.util.Base64).
 *
 * ---
 *
 * ⚠ LA TRAMPA QUE HAY QUE CONOCER
 *
 * El navegador usa RSA-OAEP con SHA-256 **tanto para el resumen como para MGF1**. En
 * Java, el nombre `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` suena a eso mismo pero usa
 * SHA-1 para MGF1, y el descifrado revienta con `BadPaddingException` sin pista alguna
 * de por qué.
 *
 * Comprobado contra WebCrypto, no supuesto:
 *
 *     INGENUA:   RSA/ECB/OAEPWithSHA-256AndMGF1Padding  → BadPaddingException
 *     EXPLICITA: RSA/ECB/OAEPPadding + OAEPParameterSpec → abre
 *
 * Por eso [OAEP] se construye a mano. No lo cambies por el nombre corto.
 */
object AdminKeyTool {

    private const val RSA_BITS = 3072
    private const val PBKDF2_ITERACIONES = 310_000
    private const val GCM_TAG_BITS = 128
    private const val MIN_CONTRASENA = 12

    /** Los parámetros que sí casan con WebCrypto. Ver la nota de arriba. */
    private val OAEP = OAEPParameterSpec(
        "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT,
    )

    private val B64: Base64.Encoder = Base64.getEncoder()
    private val UNB64: Base64.Decoder = Base64.getDecoder()

    /**
     * @param publicaB64 lo que se pega en `crypto_keys.publica` del script 31.
     * @param archivoPrivada contenido de `admin-privada.json`, ya cifrado con la
     *   contraseña maestra. Guárdalo donde guardas tus secretos.
     */
    data class ParGenerado(
        val id: String,
        val publicaB64: String,
        val archivoPrivada: String,
    )

    /**
     * Genera el par. La privada sale ya cifrada con [contrasenaMaestra]: nunca se
     * devuelve en claro, para que no acabe por accidente en un log o en una variable
     * que alguien serialice.
     *
     * Si pierdes la contraseña, pierdes el acceso a todas las direcciones guardadas.
     */
    fun generarPar(contrasenaMaestra: String, id: String): ParGenerado {
        require(contrasenaMaestra.length >= MIN_CONTRASENA) {
            "La contraseña maestra necesita al menos $MIN_CONTRASENA caracteres"
        }
        require(id.matches(Regex("^[a-z0-9][a-z0-9-]{1,38}$"))) {
            "El identificador solo admite minúsculas, dígitos y guiones"
        }

        val par = KeyPairGenerator.getInstance("RSA")
            .apply { initialize(RSA_BITS) }
            .generateKeyPair()

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }

        val cifrada = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(
                Cipher.ENCRYPT_MODE,
                derivar(contrasenaMaestra, salt, PBKDF2_ITERACIONES),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            doFinal(par.private.encoded)   // PKCS#8
        }

        val archivo = """
            {
              "id": "$id",
              "algoritmo": "RSA-OAEP-256",
              "kdf": {
                "nombre": "PBKDF2-SHA256",
                "iteraciones": $PBKDF2_ITERACIONES,
                "salt": "${B64.encodeToString(salt)}"
              },
              "iv": "${B64.encodeToString(iv)}",
              "privadaCifrada": "${B64.encodeToString(cifrada)}"
            }
        """.trimIndent()

        return ParGenerado(
            id = id,
            publicaB64 = B64.encodeToString(par.public.encoded),  // SPKI
            archivoPrivada = archivo,
        )
    }

    /**
     * Abre el archivo de la privada.
     *
     * Lanza si la contraseña no es la correcta. **No digas cuál de las dos cosas falló**
     * al enseñarlo en pantalla: distinguir «contraseña mala» de «archivo corrupto» le
     * dice a quien prueba contraseñas que va por buen camino.
     *
     * Mantén la [PrivateKey] en memoria mientras dure la sesión de la app y suéltala al
     * salir. No la escribas en disco ni la metas en el Keystore sin pensarlo: una clave
     * no exportable del Keystore no se puede respaldar, y si se pierde el teléfono se
     * pierden todas las direcciones.
     */
    fun abrirPrivada(archivoJson: String, contrasenaMaestra: String): PrivateKey {
        val salt = UNB64.decode(campo(archivoJson, "salt"))
        val iv = UNB64.decode(campo(archivoJson, "iv"))
        val cifrada = UNB64.decode(campo(archivoJson, "privadaCifrada"))
        val iteraciones = campo(archivoJson, "iteraciones").toInt()

        val pkcs8 = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(
                Cipher.DECRYPT_MODE,
                derivar(contrasenaMaestra, salt, iteraciones),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            doFinal(cifrada)
        }
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
    }

    /**
     * Abre una dirección: desenvuelve la clave de datos con la privada y descifra el
     * contenido con ella.
     *
     * Los tres argumentos salen de la fila de `user_addresses`, o de las columnas
     * `dir_*` de `order_requests` si es la dirección copiada en una solicitud.
     *
     * @return el JSON en claro: `{"calle":…,"interior":…,"colonia":…,"cp":…,
     *   "ciudad":…,"estado":…,"referencias":…}`
     */
    fun descifrarDireccion(
        contenidoB64: String,
        ivB64: String,
        dkAdminB64: String,
        privada: PrivateKey,
    ): String {
        val dkRaw = Cipher.getInstance("RSA/ECB/OAEPPadding").run {
            init(Cipher.DECRYPT_MODE, privada, OAEP)
            doFinal(UNB64.decode(dkAdminB64))
        }
        val claro = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(dkRaw, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, UNB64.decode(ivB64)),
            )
            doFinal(UNB64.decode(contenidoB64))
        }
        return String(claro, Charsets.UTF_8)
    }

    private fun derivar(pass: String, salt: ByteArray, iteraciones: Int): SecretKey {
        val spec = PBEKeySpec(pass.toCharArray(), salt, iteraciones, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    /**
     * Lee un campo del JSON sin arrastrar una librería.
     *
     * Vale porque el archivo lo escribe [generarPar] y es plano. Si algún día lo
     * sustituyes por algo con estructura, usa un parser de verdad.
     */
    private fun campo(json: String, nombre: String): String {
        val marca = "\"$nombre\""
        val inicio = json.indexOf(marca)
        require(inicio >= 0) { "Falta el campo $nombre" }
        var i = json.indexOf(':', inicio) + 1
        while (i < json.length && json[i].isWhitespace()) i++
        return if (json[i] == '"') {
            json.substring(i + 1, json.indexOf('"', i + 1))
        } else {
            val fin = json.indexOfFirst(i) { it == ',' || it == '}' || it.isWhitespace() }
            json.substring(i, fin).trim()
        }
    }

    private inline fun String.indexOfFirst(desde: Int, pred: (Char) -> Boolean): Int {
        for (i in desde until length) if (pred(this[i])) return i
        return length
    }
}
