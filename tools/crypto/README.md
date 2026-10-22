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

1. **Generar el par**, en tu equipo:

   ```sh
   node tools/crypto/generar-clave-admin.mjs
   ```

   Te pide una contraseña maestra y escribe `admin-privada.json` en la carpeta
   desde donde lo corras.

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

Descifrar pasa entero en tu app; la base no participa.

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
