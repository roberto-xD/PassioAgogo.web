#!/usr/bin/env node
/**
 * Genera el par de claves de administración para las direcciones cifradas.
 *
 * CÓRRELO EN TU EQUIPO. No lo ejecutes en un servidor compartido, ni en un
 * contenedor de CI, ni dejes que lo ejecute nadie por ti: la clave privada que
 * sale de aquí es la que abre todas las direcciones de tus clientes.
 *
 *   node generar-clave-admin.mjs
 *
 * Produce dos cosas:
 *
 *   1. La clave PÚBLICA en base64, para pegar en la tabla `crypto_keys`.
 *      No es secreta. Su único trabajo es envolver.
 *
 *   2. Un archivo `admin-privada.json` con la clave PRIVADA cifrada con la
 *      contraseña maestra que escribas. Ese archivo es lo que importa tu app
 *      de control. Sin la contraseña no sirve de nada, ni para ti.
 *
 * Qué hacer con cada cosa:
 *
 *   · La pública → al script 31, en el insert de `crypto_keys`.
 *   · El archivo → a tu gestor de contraseñas o a una copia cifrada que
 *     controles. NO al repositorio. NO a la base de datos. NO por WhatsApp.
 *   · La contraseña maestra → en tu cabeza y en tu gestor de contraseñas.
 *     Si la pierdes, pierdes el acceso a todas las direcciones guardadas.
 *
 * Por qué no la genero yo: tendría que pasar por mi entorno y por el registro
 * de esta conversación. Una clave privada que ha estado en manos ajenas deja
 * de ser privada.
 */

import { webcrypto as crypto } from 'node:crypto';
import { writeFileSync, existsSync } from 'node:fs';
import { createInterface } from 'node:readline/promises';
import { stdin, stdout } from 'node:process';

const SALIDA = 'admin-privada.json';
const ITERACIONES = 310000;
const MIN_CONTRASENA = 12;

const b64 = buf => Buffer.from(buf).toString('base64');

if (existsSync(SALIDA)) {
  console.error(`\n  Ya existe ${SALIDA} en esta carpeta.`);
  console.error('  Muévelo o bórralo antes de generar otro par, para no');
  console.error('  pisar una clave que quizá siga en uso.\n');
  process.exit(1);
}

const rl = createInterface({ input: stdin, output: stdout });
console.log('\n  Contraseña maestra para proteger la clave privada.');
console.log(`  Mínimo ${MIN_CONTRASENA} caracteres. Si la pierdes, pierdes las direcciones.\n`);
const pass = await rl.question('  Contraseña: ');
const pass2 = await rl.question('  Repítela:   ');
rl.close();

if (pass !== pass2) {
  console.error('\n  No coinciden. No se generó nada.\n');
  process.exit(1);
}
if (pass.length < MIN_CONTRASENA) {
  console.error(`\n  Demasiado corta: mínimo ${MIN_CONTRASENA} caracteres. No se generó nada.\n`);
  process.exit(1);
}

// --- el par ---
const par = await crypto.subtle.generateKey(
  { name: 'RSA-OAEP', modulusLength: 3072,
    publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
  true, ['wrapKey', 'unwrapKey']);

const publicaSpki = await crypto.subtle.exportKey('spki', par.publicKey);
const privadaPkcs8 = await crypto.subtle.exportKey('pkcs8', par.privateKey);

// --- la privada, cifrada con la contraseña maestra ---
const salt = crypto.getRandomValues(new Uint8Array(16));
const iv = crypto.getRandomValues(new Uint8Array(12));
const base = await crypto.subtle.importKey(
  'raw', new TextEncoder().encode(pass), 'PBKDF2', false, ['deriveKey']);
const kek = await crypto.subtle.deriveKey(
  { name: 'PBKDF2', salt, iterations: ITERACIONES, hash: 'SHA-256' },
  base, { name: 'AES-GCM', length: 256 }, false, ['encrypt']);
const privadaCifrada = await crypto.subtle.encrypt(
  { name: 'AES-GCM', iv }, kek, privadaPkcs8);

const keyId = `admin-${new Date().toISOString().slice(0, 10)}`;

writeFileSync(SALIDA, JSON.stringify({
  id: keyId,
  algoritmo: 'RSA-OAEP-256',
  kdf: { nombre: 'PBKDF2-SHA256', iteraciones: ITERACIONES, salt: b64(salt) },
  iv: b64(iv),
  privadaCifrada: b64(privadaCifrada),
  generada: new Date().toISOString(),
}, null, 2) + '\n', { mode: 0o600 });

console.log(`\n  Listo.\n`);
console.log(`  1. Pega esto en el insert de crypto_keys del script 31:\n`);
console.log(`     id      = '${keyId}'`);
console.log(`     publica = '${b64(publicaSpki)}'\n`);
console.log(`  2. Guarda ${SALIDA} donde guardas tus secretos.`);
console.log(`     Permisos puestos a 600. NO lo subas al repositorio.\n`);
console.log(`  3. Comprueba que tu app de control puede abrirlo con la`);
console.log(`     contraseña ANTES de que haya direcciones guardadas.\n`);
