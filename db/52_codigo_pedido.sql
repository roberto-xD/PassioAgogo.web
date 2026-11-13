-- 52_codigo_pedido.sql
--
-- Un código corto para que el cliente siga su pedido, en vez del folio.
--
-- El folio es consecutivo, así que enseñarlo cuenta cosas que no son asunto de quien
-- compra: cuántos pedidos lleva la tienda, y a qué ritmo entran. Dos clientes que
-- comparen sus folios saben cuánto se vendió entre uno y otro.
--
-- **El folio no se va.** Sigue siendo el número con el que trabaja administración y
-- con el que está escrito todo el historial. Lo que cambia es qué ve el cliente.
--
-- Y no basta con dejar de pintarlo: hoy el cliente **puede leer `folio`** desde la API
-- con su sesión. Si solo se cambiara la pantalla, la numeración seguiría a un clic de
-- distancia en las herramientas del navegador. Por eso aquí se le retira ese permiso
-- de lectura y se le da el del código nuevo.
--
-- **El código no es un secreto ni una llave.** No sirve para buscar un pedido ajeno:
-- lo que decide qué solicitudes se ven sigue siendo el RLS, que las limita a las
-- propias. Son cuatro caracteres; si algún día se usara para consultar sin sesión,
-- se recorren enteros en una tarde. No lo conviertas en eso sin alargarlo.
--
-- Idempotente: se puede correr dos veces.

begin;

-- ---------------------------------------------------------------------------
-- 1 · La columna
-- ---------------------------------------------------------------------------

alter table order_requests
  add column if not exists codigo_pedido text;

comment on column order_requests.codigo_pedido is
  'Codigo corto que ve el cliente para seguir su pedido. El folio sigue siendo el '
  'numero interno; este existe para no ensenar la numeracion consecutiva.';

-- ---------------------------------------------------------------------------
-- 2 · De qué letras se hace
-- ---------------------------------------------------------------------------
--
-- Se dicta por teléfono y se teclea a mano, así que el alfabeto se elige para eso:
--
--   · Sin vocales. Cuatro letras con vocales acaban formando palabras, y una palabra
--     se recuerda mal y se teclea «corregida». En una tienda como esta, además,
--     tarde o temprano sale una que no quieres leerle a un cliente.
--   · Sin caracteres que se confunden al leerlos: fuera O y 0, I y 1 y L, S y 5,
--     B y 8, Z y 2.
--
-- Quedan 18 letras y 5 dígitos: 23^4 = 279 841 combinaciones. De sobra, y con sitio
-- para que el sorteo falle unas cuantas veces sin notarse.

create or replace function fn_alfabeto_pedido()
returns text
language sql
immutable
as $$ select 'CDFGHJKMNPQRTVWXYZ34679' $$;

-- ---------------------------------------------------------------------------
-- 3 · Quién lo pone
-- ---------------------------------------------------------------------------
--
-- Un trigger y no un `default`: hay que poder mirar si el código ya existe y volver a
-- tirar, y un `default` no tiene dónde hacerlo.
--
-- `random()` basta porque esto no es un secreto (ver la cabecera). Lo que garantiza
-- que no se repita es el índice único de abajo; el bucle solo evita llegar hasta él.
--
-- Queda una rendija: dos inserciones a la vez pueden sortear el mismo código y pasar
-- las dos comprobaciones antes de escribir. Entonces el índice rechaza una, y es una
-- solicitud perdida. Con 279 841 combinaciones y los pedidos que entran al día, eso
-- no va a pasar; se deja dicho porque el día que la tienda crezca diez veces, esta es
-- la línea que hay que volver a leer.

create or replace function fn_codigo_pedido()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_alfabeto text := fn_alfabeto_pedido();
  v_largo    int  := length(v_alfabeto);
  v_codigo   text;
  v_intento  int  := 0;
begin
  -- Puesto a mano: se respeta. Sirve para migrar o para corregir uno a dedo.
  if new.codigo_pedido is not null and btrim(new.codigo_pedido) <> '' then
    new.codigo_pedido := upper(btrim(new.codigo_pedido));
    return new;
  end if;

  loop
    v_codigo := '';
    for _ in 1..4 loop
      v_codigo := v_codigo ||
        substr(v_alfabeto, 1 + floor(random() * v_largo)::int, 1);
    end loop;

    exit when not exists (
      select 1 from order_requests o where o.codigo_pedido = v_codigo
    );

    v_intento := v_intento + 1;
    -- Cincuenta fallos seguidos no es mala suerte: es que el espacio se llenó.
    -- Mejor gritar que entregar pedidos sin código.
    if v_intento >= 50 then
      raise exception 'No se pudo generar un codigo de pedido libre tras % intentos',
        v_intento;
    end if;
  end loop;

  new.codigo_pedido := v_codigo;
  return new;
end;
$$;

drop trigger if exists trg_codigo_pedido on order_requests;
create trigger trg_codigo_pedido
  before insert on order_requests
  for each row execute function fn_codigo_pedido();

-- ---------------------------------------------------------------------------
-- 4 · Lo que ya existe
-- ---------------------------------------------------------------------------
--
-- Fila por fila y no de una: el trigger es `before insert`, así que a lo ya guardado
-- no lo toca. Se hace aquí, una vez.

do $$
declare
  v_fila     record;
  v_alfabeto text := fn_alfabeto_pedido();
  v_largo    int  := length(v_alfabeto);
  v_codigo   text;
  v_intento  int;
begin
  for v_fila in select id from order_requests where codigo_pedido is null loop
    v_intento := 0;
    loop
      v_codigo := '';
      for _ in 1..4 loop
        v_codigo := v_codigo ||
          substr(v_alfabeto, 1 + floor(random() * v_largo)::int, 1);
      end loop;
      exit when not exists (
        select 1 from order_requests o where o.codigo_pedido = v_codigo
      );
      v_intento := v_intento + 1;
      if v_intento >= 50 then
        raise exception 'No se pudo generar un codigo libre para la solicitud %',
          v_fila.id;
      end if;
    end loop;
    update order_requests set codigo_pedido = v_codigo where id = v_fila.id;
  end loop;
end $$;

-- Ahora que todas lo tienen, se exige. Y el índice único es lo que de verdad impide
-- que dos pedidos compartan código.
alter table order_requests alter column codigo_pedido set not null;

create unique index if not exists idx_order_requests_codigo_pedido
  on order_requests (codigo_pedido);

-- ---------------------------------------------------------------------------
-- 5 · Quién lee qué
-- ---------------------------------------------------------------------------
--
-- El cliente gana el código y **pierde el folio**. Esto es el cambio de verdad: sin
-- el revoke, la numeración seguiría siendo legible desde la API aunque la pantalla
-- enseñara otra cosa, y todo esto habría sido pintura.
--
-- Administración no se entera: entra por `fn_envios_admin` y `fn_pagos_admin`, que
-- son `security definer` y leen la tabla entera.

grant select (codigo_pedido) on order_requests to authenticated;
revoke select (folio) on order_requests from authenticated;

-- ---------------------------------------------------------------------------
-- 6 · Que administración lo pueda buscar
-- ---------------------------------------------------------------------------
--
-- Sin esto el cambio deja a la tienda peor que antes: el cliente escribe «mi pedido
-- K7MD» por WhatsApp y del otro lado no hay forma de saber cuál es. Las dos
-- funciones devuelven ahora el código, y la de envíos acepta buscar por él.

-- Las dos firmas: la de antes, de tres argumentos, y la de cuatro que crea este
-- script. Sin la segunda, correrlo dos veces falla con «already exists with same
-- argument types», porque `drop` por firma no alcanza a la que acaba de nacer.
drop function if exists fn_envios_admin(uuid, boolean, integer);
drop function if exists fn_envios_admin(uuid, boolean, integer, text);

create function fn_envios_admin(
  p_id              uuid    default null,
  p_solo_sin_guia   boolean default false,
  p_limite          integer default 100,
  -- Lo que dicta el cliente. Sin distinguir mayúsculas ni espacios de más: se copia
  -- de WhatsApp, y ahí llega como llegue.
  p_codigo          text    default null
)
returns table (
  id                   uuid,
  folio                bigint,
  codigo_pedido        text,
  cliente_nombre       text,
  cliente_telefono     text,
  estado               text,
  created_by           uuid,
  creador_rol          text,
  metodo_entrega       text,
  envio_paqueteria     text,
  envio_servicio       text,
  guia_numero          text,
  guia_url             text,
  guia_rastreo_url     text,
  guia_paqueteria      text,
  guia_servicio        text,
  guia_costo           numeric,
  guia_generada_en     timestamptz,
  necesita_recoleccion boolean,
  recoleccion_id       text,
  recoleccion_fecha    date,
  cancelacion_solicitada_at timestamptz,
  cancelada_at         timestamptz,
  cancelada_por        text,
  cancelacion_motivo   text,
  created_at           timestamptz
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_codigo text := nullif(upper(btrim(coalesce(p_codigo, ''))), '');
begin
  if not is_admin() then
    raise exception 'Solo administracion puede consultar los envios'
      using errcode = '42501';
  end if;

  return query
  select
    o.id, o.folio, o.codigo_pedido,
    o.cliente_nombre, o.cliente_telefono,
    o.estado::text, o.created_by, p.rol::text,
    o.metodo_entrega, o.envio_paqueteria, o.envio_servicio,
    o.guia_numero, o.guia_url, o.guia_rastreo_url,
    o.guia_paqueteria, o.guia_servicio, o.guia_costo, o.guia_generada_en,
    o.necesita_recoleccion, o.recoleccion_id, o.recoleccion_fecha,
    o.cancelacion_solicitada_at, o.cancelada_at, o.cancelada_por,
    o.cancelacion_motivo, o.created_at
  from order_requests o
  left join profiles p on p.id = o.created_by
  where (p_id is null or o.id = p_id)
    and (v_codigo is null or o.codigo_pedido = v_codigo)
    and (
      not p_solo_sin_guia
      or (
        o.metodo_entrega = 'domicilio'
        and o.estado = 'enviada'
        and o.guia_numero is null
        and p.rol = 'cliente'
        and o.cancelada_at is null
        and o.cancelacion_solicitada_at is null
      )
    )
  order by
    case when p_solo_sin_guia then o.created_at end asc,
    o.guia_generada_en desc nulls last
  limit greatest(p_limite, 1);
end;
$$;

revoke all on function fn_envios_admin(uuid, boolean, integer, text) from public;
grant execute on function fn_envios_admin(uuid, boolean, integer, text) to authenticated;

drop function if exists fn_pagos_admin(uuid, date, date, boolean, integer);
drop function if exists fn_pagos_admin(uuid, date, date, boolean, integer, text);

create function fn_pagos_admin(
  p_id             uuid    default null,
  p_desde          date    default null,
  p_hasta          date    default null,
  p_solo_aprobadas boolean default false,
  p_limite         integer default 200,
  p_codigo         text    default null
)
returns table (
  id                 uuid,
  folio              bigint,
  codigo_pedido      text,
  cliente_nombre     text,
  estado             text,
  created_at         timestamptz,
  total_estimado     numeric,
  envio_costo        numeric,
  envio_gratis       boolean,
  codigo_descuento   text,
  descuento_pct      integer,
  descuento_monto    numeric,
  regalo             text,
  pago_estado        text,
  pago_total         numeric,
  pago_metodo        text,
  pago_marca         text,
  pago_ultimos4      text,
  pago_referencia    text,
  pago_orden_id      text,
  pago_actualizado_en timestamptz,
  pago_neto          numeric,
  pago_comision      numeric,
  pago_liberacion    timestamptz,
  guia_costo         numeric,
  neto_tras_guia     numeric
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_codigo text := nullif(upper(btrim(coalesce(p_codigo, ''))), '');
begin
  if not is_admin() then
    raise exception 'Solo administracion puede consultar los pagos'
      using errcode = '42501';
  end if;

  return query
  select
    o.id, o.folio, o.codigo_pedido, o.cliente_nombre, o.estado::text, o.created_at,
    o.total_estimado, o.envio_costo, o.envio_gratis,
    o.codigo_descuento, o.descuento_pct, o.descuento_monto, o.regalo,
    o.pago_estado, o.pago_total, o.pago_metodo, o.pago_marca, o.pago_ultimos4,
    o.pago_referencia, o.pago_orden_id, o.pago_actualizado_en,
    o.pago_neto, o.pago_comision, o.pago_liberacion, o.guia_costo,
    case
      when o.pago_neto is null then null
      else o.pago_neto - coalesce(o.guia_costo, 0)
    end
  from order_requests o
  where (p_id is null or o.id = p_id)
    and (v_codigo is null or o.codigo_pedido = v_codigo)
    and (not p_solo_aprobadas or o.pago_estado = 'aprobado')
    and (
      p_desde is null
      or (o.pago_actualizado_en at time zone 'America/Mexico_City')::date >= p_desde
    )
    and (
      p_hasta is null
      or (o.pago_actualizado_en at time zone 'America/Mexico_City')::date <= p_hasta
    )
  order by o.pago_actualizado_en desc nulls last, o.created_at desc
  limit greatest(p_limite, 1);
end;
$$;

revoke all on function fn_pagos_admin(uuid, date, date, boolean, integer, text) from public;
grant execute on function fn_pagos_admin(uuid, date, date, boolean, integer, text)
  to authenticated;

commit;

-- ---------------------------------------------------------------------------
-- Comprobar
-- ---------------------------------------------------------------------------
--
-- Que todas tengan código y que ninguno se repita:
--
-- select count(*) as pedidos,
--        count(distinct codigo_pedido) as codigos,
--        count(*) filter (where codigo_pedido is null) as sin_codigo
--   from order_requests;
--
-- Buscar el que te dictó un cliente:
--
-- select * from fn_envios_admin(p_codigo := 'k7md');
-- select * from fn_pagos_admin(p_codigo := 'K7MD');
--
-- Que el cliente ya no pueda leer el folio: esto NO debe listar `folio`.
--
-- select column_name from information_schema.column_privileges
--  where table_name = 'order_requests' and grantee = 'authenticated'
--    and privilege_type = 'SELECT'
--  order by column_name;
--
-- Cuánto queda del espacio de códigos. Por encima del 5 % conviene ir pensando en
-- cinco caracteres, porque los reintentos del sorteo empiezan a notarse:
--
-- select count(*) as usados,
--        round(100.0 * count(*) / power(23, 4), 2) as pct_del_espacio
--   from order_requests;
