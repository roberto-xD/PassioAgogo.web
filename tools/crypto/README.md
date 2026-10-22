# Direcciones cifradas — qué es cada pieza

La dirección de un cliente se guarda en `user_addresses` **cifrada de punta a
punta**. La base de datos no puede leerla: guarda tres bultos y ninguna clave.

```
contenido     la dirección (JSON) cifrada con una clave al azar  ──┐
dk_usuario    esa clave, envuelta con la contraseña del cliente    │ la misma
dk_admin      esa clave, envuelta con la pública de administración ┘ clave
```

Quien se lleve un volcado de la tabla se lleva ruido. Para abrirlo hace falta
la contraseña de ese cliente, o la clave privada de administración.

## Lo que hay que hacer una vez

1. **Generar el par.** Hay dos caminos; el resultado es idéntico.

   **Desde la app de control (recomendado).** `AdminKeyTool.kt` de esta misma
   carpeta se pega en el proyecto de Android. `generarPar(contrasena, id)`
   devuelve la pública en base64 y el contenido de `admin-privada.json` ya
   cifrado. Así la clave privada no sale nunca del dispositivo donde va a
   vivir.

   **Desde una terminal**, si lo prefieres:

   ```sh
   node tools/crypto/generar-clave-admin.mjs
   ```

   Necesita Node 18 o superior: usa `node:readline/promises`, que no existe
   antes. Si te dio un error al arrancar, casi seguro es eso.

2. **La pública va a la base**, en el `insert into crypto_keys` del script
   `db/31_direcciones_cifradas.sql`.

3. **El archivo va a tu gestor de contraseñas.** No al repositorio, no a la
   base, no por mensajería. Está cifrado con tu contraseña maestra, pero un
   archivo cifrado que anda rodando es un archivo que alguien acabará
   intentando abrir.

4. **Comprueba que tu app de control lo abre** antes de que haya direcciones
   reales guardadas. Si descubres que no puede después, las direcciones ya
   capturadas no se recuperan.

## Lo que tiene que hacer la app de control

Descifrar pasa entero en tu app; la base no participa. `AdminKeyTool.kt` ya lo
implementa; lo de abajo es lo que hace por dentro, por si tienes que portarlo.

### ⚠ La trampa de OAEP

El navegador usa RSA-OAEP con **SHA-256 tanto para el resumen como para MGF1**.
En Java, el nombre `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` suena a eso mismo
pero usa **SHA-1** para MGF1, y el descifrado falla con `BadPaddingException`
sin ninguna pista de por qué.

Comprobado contra WebCrypto, no supuesto:

```
INGENUA:   RSA/ECB/OAEPWithSHA-256AndMGF1Padding  → BadPaddingException
EXPLICITA: RSA/ECB/OAEPPadding + OAEPParameterSpec → abre
```

Hay que pasar los parámetros a mano:

```kotlin
Cipher.getInstance("RSA/ECB/OAEPPadding").init(
    Cipher.DECRYPT_MODE, privada,
    OAEPParameterSpec("SHA-256", "MGF1",
        MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT),
)
```

El resto de parámetros que tienen que casar: **AES-GCM con etiqueta de 128
bits** (`GCMParameterSpec(128, iv)`, que es lo que usa WebCrypto por omisión) y
**PBKDF2WithHmacSHA256** con las iteraciones y la sal que vienen en el archivo.

### Los pasos

1. Pedir la contraseña maestra al entrar a la sección de direcciones.
2. Derivar la clave con **PBKDF2-SHA256**, con el `salt` y las `iteraciones`
   que vienen en `admin-privada.json`.
3. Descifrar `privadaCifrada` con **AES-GCM** y el `iv` del archivo. Sale la
   clave privada en formato PKCS#8.
4. Por cada dirección que quieras ver:
   - desenvolver `dk_admin` con la privada (**RSA-OAEP**, SHA-256) → clave de datos;
   - descifrar `contenido` con esa clave (**AES-GCM**, usando `iv`) → el JSON.

El JSON tiene esta forma:

```json
{
  "calle": "Velo de novia 16",
  "interior": "",
  "colonia": "Los Ángeles",
  "cp": "09830",
  "ciudad": "Ciudad de México",
  "estado": "CDMX",
  "referencias": "Portón negro, entre dos farmacias"
}
```

Guarda la contraseña maestra en memoria mientras dure la sesión de tu app, y
suéltala al cerrar. No la escribas en disco.

### Sobre el Keystore de Android

Tentador, pero cuidado: una clave generada como **no exportable** en el Android
Keystore no se puede respaldar. Si se pierde o se formatea el teléfono, se
pierden **todas** las direcciones guardadas, y no hay forma de recuperarlas.

Por eso `AdminKeyTool` genera una clave normal y la protege con tu contraseña
maestra: así puedes tener una copia. Si además quieres la comodidad del
Keystore, guarda ahí una copia **y** conserva el archivo cifrado como respaldo.

## Lo que este esquema NO resuelve

- **Si un cliente restablece su contraseña, su copia del sobre deja de abrir.**
  La tuya sigue abriendo, así que el dato no se pierde, pero él tendrá que
  volver a capturar su dirección. Es inevitable: su clave *es* su contraseña.
- **No protege de un atacante dentro de tu navegador.** Mientras la pestaña
  está abierta y el cliente ha entrado, la clave derivada vive en
  `sessionStorage`. Un XSS en el sitio podría leerla. El cifrado protege lo
  guardado, no una sesión ya abierta.
- **No protege de ti con la clave privada en la mano.** Eso es el requisito, no
  un descuido: administración tiene que poder enviar el paquete.

## Rotar la clave de administración

1. Generar un par nuevo con otro `id`.
2. Darlo de alta en `crypto_keys` y poner `activa = false` en el viejo.
3. Las direcciones ya guardadas **siguen envueltas con la vieja**: no se pueden
   re-envolver sin descifrarlas. Conserva la privada antigua mientras quede
   alguna fila con ese `admin_key_id`:

   ```sql
   select admin_key_id, count(*) from user_addresses group by admin_key_id;
   ```
