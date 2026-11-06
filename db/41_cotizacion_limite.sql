-- 41_cotizacion_limite.sql
--
-- Un registro mínimo de cotizaciones de envío, solo para poder limitarlas.
--
-- `cotizar-envio` se abre en abanico: una llamada a Envia **por paquetería**. Sin
-- freno, un bucle contra esa función gasta la cuota de la tienda y puede hacer que
-- Envia nos limite justo cuando alguien está comprando de verdad. No roba dinero;
-- estorba la venta, que a efectos prácticos es parecido.
--
-- `contact` y `spin` cuentan sobre sus propias tablas —los mensajes y los giros—, pero
-- una cotización no se guarda en ningún sitio, así que hace falta esta.
--
-- **Lo que NO se guarda: el código postal.** Para contar llamadas no hace falta saber a
-- dónde se cotizaba, y la función tiene por norma no escribir en ninguna parte el
-- destino. Guardarlo aquí abriría por la puerta de atrás un rastro de a dónde mira cada
-- quien, que es justo lo que el cifrado de direcciones evita por delante.
--
-- Idempotente: se puede correr dos veces.

begin;

create table if not exists cotizacion_intentos (
  id         bigserial primary key,
  -- Quién. Desde que la función exige sesión siempre hay uno; se deja anulable por si
  -- algún día vuelve a admitir llamadas sin cuenta.
  usuario_id uuid references profiles(id) on delete cascade,
  -- El IP **nunca en claro**, solo su hash con sal, igual que en `contact_messages`.
  -- Sirve para frenar a quien abre diez cuentas desde la misma conexión.
  ip_hash    text,
  created_at timestamptz not null default now()
);

create index if not exists idx_cotizacion_intentos_usuario
  on cotizacion_intentos (usuario_id, created_at desc);
create index if not exists idx_cotizacion_intentos_ip
  on cotizacion_intentos (ip_hash, created_at desc);

alter table cotizacion_intentos enable row level security;

-- Sin ninguna política **a propósito**: solo entra y se lee por la Edge Function, que
-- usa service_role y salta el RLS. Un contador que el interesado puede borrar no
-- cuenta nada.

commit;

-- La función borra lo viejo en cada llamada, así que la tabla no crece. Para
-- comprobarlo:
--
-- select count(*), min(created_at), max(created_at) from cotizacion_intentos;
--
-- Y para ver si alguien se está pasando:
--
-- select usuario_id, count(*)
--   from cotizacion_intentos
--  where created_at > now() - interval '1 hour'
--  group by 1 order by 2 desc limit 10;
