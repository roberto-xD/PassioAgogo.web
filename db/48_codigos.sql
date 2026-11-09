-- 48_codigos.sql
--
-- Una tabla para todo lo que se canjea, venga de la ruleta o de la mano.
--
-- Hasta ahora un código **era** un giro: vivía en `wheel_spins`, con su `ip_hash` y su
-- premio, y se gastaba una vez. Eso no da para lo que hace falta:
--
--  - Un código repartido en Instagram existe sin que nadie haya girado nada.
--  - Un código de campaña se usa muchas veces, no una.
--  - «Envío gratis a partir de $X» necesita un mínimo de compra, que no es un concepto
--    de `wheel_prizes`: ahí los premios son sectores de una rueda.
--
-- Meterlos a mano en `wheel_spins` habría funcionado un tiempo, a costa de llenarla de
-- filas que mienten sobre lo que pasó.
--
-- Reparto: **`codigos` es lo que se canjea**; `wheel_spins` se queda con lo que siempre
-- fue, el registro de quién giró y cuándo. Se enlazan por el código, que es único en
-- las dos.
--
-- Hay que **volver a desplegar `spin`** después de correr esto: desde ahora emite la
-- fila de `codigos` además de anotar el giro, y lee `tipo`, `descuento_pct` y
-- `minimo_compra` de `wheel_prizes`. La versión anterior seguiría escribiendo solo en
-- `wheel_spins`, y esos códigos no se podrían canjear.
--
-- Idempotente: se puede correr dos veces.

begin;

-- ---------------------------------------------------------------------------
-- 1. Los códigos
-- ---------------------------------------------------------------------------

create table if not exists codigos (
  id       uuid primary key default gen_random_uuid(),
  -- Siempre en mayúsculas: se dictan por teléfono y se teclean a mano, y «pag-x4k2»
  -- y «PAG-X4K2» tienen que ser el mismo.
  codigo   text not null unique
           check (codigo = upper(codigo) and length(trim(codigo)) between 4 and 24),

  tipo     text not null check (tipo in ('descuento', 'regalo', 'envio_gratis')),

  -- Solo con `descuento`.
  descuento_pct integer check (descuento_pct is null or descuento_pct between 1 and 100),
  -- Solo con `regalo`: qué se mete en la caja. Es lo que leerá quien lo prepare.
  regalo   text,
  -- El «a partir de $X», sobre el importe de los artículos (nunca sobre el envío).
  -- Nulo = sin mínimo.
  minimo_compra numeric(10,2) check (minimo_compra is null or minimo_compra >= 0),

  -- 1 para un código personal; más para uno de campaña que se comparte.
  usos_max integer not null default 1 check (usos_max >= 1),
  usos     integer not null default 0 check (usos >= 0),

  expira_at timestamptz,
  -- Apagarlo sin borrarlo: un código repartido que hay que cortar a media campaña.
  activo   boolean not null default true,

  origen   text not null default 'manual' check (origen in ('ruleta', 'manual')),
  -- De qué sector salió, cuando viene de la ruleta. `set null` porque borrar un premio
  -- no debe borrar los códigos que ya repartió.
  prize_id uuid references wheel_prizes(id) on delete set null,
  -- Para acordarse dentro de seis meses de qué era «NOV15».
  nota     text,

  created_at timestamptz not null default now()
);

comment on table codigos is
  'Todo lo que se puede canjear al pagar. La ruleta crea uno por giro; los de campaña '
  'se insertan a mano. wheel_spins guarda el giro, no el canje.';

-- Coherencia: un descuento sin porcentaje no descuenta, y un porcentaje en un premio
-- que no es descuento son dos cosas a la vez.
do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'codigos_tipo_coherente') then
    alter table codigos add constraint codigos_tipo_coherente check (
      (tipo = 'descuento'    and descuento_pct is not null and regalo is null) or
      (tipo = 'regalo'       and descuento_pct is null     and regalo is not null) or
      (tipo = 'envio_gratis' and descuento_pct is null     and regalo is null)
    );
  end if;
end $$;

create index if not exists idx_codigos_vivos
  on codigos (codigo) where activo and usos < usos_max;

alter table codigos enable row level security;

-- Sin sesión no se toca esta tabla. Hoy ya no se podría —RLS está puesto y no hay
-- política para `anon`—, pero el permiso seguiría ahí esperando a que alguien añadiera
-- una política «solo para leer una cosilla». Quitarlo cierra las dos puertas.
revoke all on codigos from anon;

-- Sin política de lectura para el cliente **a propósito**: si pudiera listarlos, se
-- llevaría todos los códigos de campaña de una consulta. Se consultan de uno en uno
-- por la Edge Function, que además lleva el freno de intentos.
drop policy if exists codigos_admin on codigos;
create policy codigos_admin on codigos for all to authenticated
  using (is_admin()) with check (is_admin());

-- ---------------------------------------------------------------------------
-- 2. El envío gratis también en la ruleta
-- ---------------------------------------------------------------------------

alter table wheel_prizes
  add column if not exists minimo_compra numeric(10,2)
    check (minimo_compra is null or minimo_compra >= 0);

comment on column wheel_prizes.minimo_compra is
  'Compra minima en articulos para que el premio aplique. Pensado para envio_gratis.';

do $$
begin
  alter table wheel_prizes drop constraint if exists wheel_prizes_tipo_check;
  alter table wheel_prizes add constraint wheel_prizes_tipo_check
    check (tipo in ('descuento', 'regalo', 'envio_gratis', 'ninguno'));
end $$;

comment on column wheel_prizes.tipo is
  'descuento (un porcentaje sobre los articulos), regalo (un articulo que se mete en '
  'la caja), envio_gratis (la tienda absorbe el envio, con minimo_compra si lo tiene) '
  'o ninguno (consolacion). El rotulo es lo que se pinta; esto es lo que se aplica.';

-- El check de coherencia de 45 daba por hecho que todo lo que no fuera descuento no
-- llevaba porcentaje; sigue valiendo para envio_gratis, así que no se toca.

-- ---------------------------------------------------------------------------
-- 3. Lo ya emitido pasa a la tabla nueva
-- ---------------------------------------------------------------------------
--
-- Un código por giro, conservando lo que se prometió: el tipo del premio, su caducidad
-- y si ya se había canjeado. `on conflict do nothing` para poder correrlo dos veces.

insert into codigos (
  codigo, tipo, descuento_pct, regalo, minimo_compra,
  usos_max, usos, expira_at, origen, prize_id, nota
)
select
  s.codigo,
  coalesce(p.tipo, 'descuento'),
  p.descuento_pct,
  case when p.tipo = 'regalo' then coalesce(p.detalle, s.rotulo) end,
  p.minimo_compra,
  1,
  case when s.canjeado then 1 else 0 end,
  s.expira_at,
  'ruleta',
  s.prize_id,
  'Migrado desde wheel_spins'
  from wheel_spins s
  join wheel_prizes p on p.id = s.prize_id
 where p.tipo in ('descuento', 'regalo', 'envio_gratis')
on conflict (codigo) do nothing;

-- ---------------------------------------------------------------------------
-- 4. El envío gratis en la solicitud
-- ---------------------------------------------------------------------------

alter table order_requests
  add column if not exists envio_gratis boolean not null default false;

comment on column order_requests.envio_gratis is
  'El envio fue promocional: el cliente no eligio paqueteria y la tienda absorbe el '
  'costo. Lo que costo de verdad queda en guia_costo al generar la guia.';

grant select (envio_gratis) on order_requests to authenticated;

-- ---------------------------------------------------------------------------
-- 5. Consumir un uso
-- ---------------------------------------------------------------------------
--
-- En una función y no en un `update` desde la Edge Function porque la condición
-- `usos < usos_max` tiene que evaluarse **dentro** de la misma sentencia que
-- incrementa. Leer el contador, decidir y escribir por separado deja una rendija: dos
-- pagos aprobados en el mismo instante leerían el mismo valor y pasarían los dos.
--
-- `security definer` para que la pueda llamar el webhook con su rol sin abrirle la
-- tabla entera, y `search_path` fijo para que nadie la redirija a otra `codigos`.

create or replace function consumir_codigo(p_codigo text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_consumido boolean := false;
begin
  update codigos
     set usos = usos + 1
   where codigo = upper(trim(p_codigo))
     and usos < usos_max
  returning true into v_consumido;

  -- La ruleta sigue mirando su propia columna; mientras existan las dos, cuentan lo
  -- mismo. Sin el `and not canjeado`, un aviso repetido reescribiría la fecha del
  -- canje original.
  if v_consumido then
    update wheel_spins
       set canjeado = true, canjeado_at = now()
     where codigo = upper(trim(p_codigo))
       and not canjeado;
  end if;

  return coalesce(v_consumido, false);
end;
$$;

-- Solo la Edge Function. Quitar el permiso de `public` también se lo quita a
-- `service_role`, que lo hereda, así que hay que devolvérselo explícitamente: sin esto
-- el webhook llamaría y recibiría «permission denied» sin que nada más fallara.
revoke all on function consumir_codigo(text) from public, anon, authenticated;
grant execute on function consumir_codigo(text) to service_role;

commit;

-- ---------------------------------------------------------------------------
-- Repartir códigos a mano
-- ---------------------------------------------------------------------------
--
-- Uno de campaña, compartido, con tope de usos y fecha:
--
-- insert into codigos (codigo, tipo, descuento_pct, usos_max, expira_at, nota)
-- values ('NOV15', 'descuento', 15, 200,
--         timestamptz '2026-11-30 23:59:59-06:00', 'Campana de noviembre en Instagram');
--
-- Envío gratis a partir de $800:
--
-- insert into codigos (codigo, tipo, minimo_compra, usos_max, expira_at, nota)
-- values ('ENVIOGRATIS', 'envio_gratis', 800, 500,
--         timestamptz '2026-12-31 23:59:59-06:00', 'Promo de fin de ano');
--
-- Un lote de códigos individuales de un uso, para entregar de uno en uno:
--
-- insert into codigos (codigo, tipo, descuento_pct, usos_max, expira_at, nota)
-- select 'VIP-' || upper(substr(md5(random()::text), 1, 6)), 'descuento', 20, 1,
--        now() + interval '30 days', 'Lote VIP noviembre'
--   from generate_series(1, 50)
-- on conflict (codigo) do nothing;
--
-- Y en la ruleta: convertir un sector en «envío gratis a partir de $800». El código lo
-- emite `spin` al girar, con la vigencia del premio; esto solo dice qué es el sector.
--
-- update wheel_prizes
--    set tipo = 'envio_gratis', descuento_pct = null, minimo_compra = 800
--  where rotulo = 'ENVIO GRATIS';
--
-- Cortar una campaña a media marcha, sin borrarla:
--
-- update codigos set activo = false where codigo = 'NOV15';
--
-- Cómo va cada uno:
--
-- select codigo, tipo, descuento_pct, minimo_compra, usos, usos_max, activo,
--        expira_at at time zone 'America/Mexico_City' as caduca_cdmx, nota
--   from codigos where origen = 'manual' order by created_at desc;
