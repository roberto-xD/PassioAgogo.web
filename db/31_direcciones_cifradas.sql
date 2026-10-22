-- ============================================================
-- 31 · Dirección del cliente, cifrada extremo a extremo
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después del 30 · Idempotente
--
-- La base NO puede leer estas direcciones. Guarda tres bultos cifrados
-- y ninguna clave con la que abrirlos:
--
--   contenido   la dirección cifrada con una clave al azar (AES-GCM)
--   dk_usuario  esa clave, envuelta con la contraseña del cliente
--   dk_admin    esa misma clave, envuelta con la pública del admin
--
-- Quien se lleve un volcado de la tabla se lleva ruido. El cliente abre
-- su copia con su contraseña; administración abre la otra con la clave
-- privada, que no vive aquí ni debe vivir nunca aquí.
--
-- ⚠ CONSECUENCIA QUE NO SE PUEDE EVITAR: si el cliente restablece su
--   contraseña, su copia del sobre deja de abrir y tendrá que volver a
--   capturar la dirección. La de administración sigue abriendo, así que
--   el dato no se pierde del todo.
-- ============================================================

begin;

-- ------------------------------------------------------------
-- 1. Claves públicas de administración
--
-- Una clave pública no es secreta: su único trabajo es envolver. Se
-- guarda aquí para que la web sepa con cuál cifrar sin llevarla
-- incrustada en el código, y para poder rotarla sin recompilar.
--
-- La PRIVADA correspondiente NO va en esta tabla, ni en ninguna otra,
-- ni en el repositorio. Ver tools/crypto/README.md.
-- ------------------------------------------------------------
create table if not exists crypto_keys (
  id          text primary key check (id ~ '^[a-z0-9][a-z0-9-]{1,38}$'),
  -- Clave pública RSA-OAEP en formato SPKI, en base64.
  publica     text not null check (length(publica) between 100 and 2000),
  algoritmo   text not null default 'RSA-OAEP-256',
  activa      boolean not null default true,
  notas       text,
  created_at  timestamptz not null default now()
);

create unique index if not exists idx_crypto_keys_activa
  on crypto_keys (activa)
  where activa;

alter table crypto_keys enable row level security;

-- Cualquiera que vaya a cifrar necesita leerla, incluido quien aún no
-- ha iniciado sesión (se captura la dirección al registrarse).
grant select on crypto_keys to anon, authenticated;

drop policy if exists crypto_keys_select on crypto_keys;
create policy crypto_keys_select on crypto_keys for select to anon, authenticated
  using (activa);

drop policy if exists crypto_keys_write on crypto_keys;
create policy crypto_keys_write on crypto_keys for all to authenticated
  using (is_admin()) with check (is_admin());

-- ------------------------------------------------------------
-- 2. Sal del cliente
--
-- No es secreta. Su trabajo es que la misma contrasena en dos cuentas
-- distintas no derive la misma clave. Vive en el perfil para que la
-- clave derivada valga para todas las direcciones de esa persona.
-- ------------------------------------------------------------
alter table profiles add column if not exists kdf_salt text;

comment on column profiles.kdf_salt is
  'Sal publica para derivar la clave de cifrado del cliente (31). No es '
  'un secreto; sin la contrasena no sirve de nada.';

-- La web la necesita para derivar al acceder.
grant update (kdf_salt) on profiles to authenticated;

-- ------------------------------------------------------------
-- 3. Direcciones
-- ------------------------------------------------------------
create table if not exists user_addresses (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references profiles(id) on delete cascade,

  -- Lo único en claro: cómo la llama su dueño. No es un dato de
  -- localización, y permite elegir entre varias sin descifrarlas todas.
  etiqueta    text check (etiqueta is null or length(trim(etiqueta)) between 1 and 40),

  -- El sobre.
  version     integer not null default 1,
  contenido   text not null,
  iv          text not null,
  dk_usuario  text not null,
  iv_usuario  text not null,
  salt_usuario text not null,
  kdf_iteraciones integer not null default 210000 check (kdf_iteraciones >= 100000),
  dk_admin    text not null,
  admin_key_id text not null references crypto_keys(id),

  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

-- Por ahora una por persona. Quitar este índice es todo lo que hace
-- falta el día que se quieran varias.
create unique index if not exists idx_user_addresses_una_por_usuario
  on user_addresses (user_id);

alter table user_addresses enable row level security;

-- ------------------------------------------------------------
-- 4. Quién ve qué
--
-- Administración ve el sobre cerrado, no la dirección: lee la fila y
-- descifra en su propia app con la clave privada. La base nunca
-- participa en el descifrado.
-- ------------------------------------------------------------
drop policy if exists user_addresses_select on user_addresses;
create policy user_addresses_select on user_addresses for select to authenticated
  using (user_id = auth.uid() or is_admin());

-- Escribir solo su dueño: administración no tiene con qué envolver la
-- copia del cliente, así que una escritura suya dejaría una dirección
-- que el cliente ya no podría abrir.
drop policy if exists user_addresses_insert on user_addresses;
create policy user_addresses_insert on user_addresses for insert to authenticated
  with check (user_id = auth.uid());

drop policy if exists user_addresses_update on user_addresses;
create policy user_addresses_update on user_addresses for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists user_addresses_delete on user_addresses;
create policy user_addresses_delete on user_addresses for delete to authenticated
  using (user_id = auth.uid());

drop trigger if exists trg_user_addresses_updated_at on user_addresses;
create trigger trg_user_addresses_updated_at
  before update on user_addresses
  for each row execute function fn_set_updated_at();

-- ------------------------------------------------------------
-- 5. La dirección copiada en la solicitud
--
-- Se copia al enviar, con su propio sobre: si el cliente se muda, el
-- pedido viejo conserva adónde se envió de verdad.
-- ------------------------------------------------------------
alter table order_requests add column if not exists dir_version integer;
alter table order_requests add column if not exists dir_contenido text;
alter table order_requests add column if not exists dir_iv text;
alter table order_requests add column if not exists dir_dk_usuario text;
alter table order_requests add column if not exists dir_iv_usuario text;
alter table order_requests add column if not exists dir_salt_usuario text;
alter table order_requests add column if not exists dir_kdf_iteraciones integer;
alter table order_requests add column if not exists dir_dk_admin text;
alter table order_requests add column if not exists dir_admin_key_id text;

comment on column order_requests.dir_contenido is
  'Direccion de envio cifrada, copiada al enviar la solicitud. La base no '
  'puede leerla: se descifra en la app de administracion con la clave privada.';

commit;

-- ============================================================
-- 6. Alta de la clave pública de administración
--
-- Genera el par con tools/crypto/generar-clave-admin.mjs y pega aquí
-- SOLO la pública. Si pegas la privada, el cifrado deja de servir para
-- nada: estaría en la misma base que protege.
-- ============================================================
-- insert into crypto_keys (id, publica, notas) values (
--   'admin-2026',
--   'PEGA-AQUI-LA-PUBLICA-EN-BASE64',
--   'Generada el 2026-10-22. Privada en la boveda de Roberto.'
-- ) on conflict (id) do nothing;

-- ============================================================
-- Comprobación
-- ============================================================
-- Debe haber exactamente una clave activa:
-- select id, algoritmo, activa, created_at from crypto_keys;
--
-- Y esto debe devolver cero filas legibles para cualquiera que no sea
-- el dueño ni admin. Lo que devuelva, ademas, es ruido cifrado:
-- select user_id, left(contenido, 32) || '…' as muestra from user_addresses;
