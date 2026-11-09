-- 50_pagos_admin_rpc.sql
--
-- El dinero real por venta, para la app de control.
--
-- Hermana de `fn_envios_admin` (49) y por el mismo motivo: el 47 cerró `pago_neto`,
-- `pago_comision`, `pago_liberacion` y `guia_costo` a `authenticated`, y como un
-- `grant` es por rol y no por persona, al cerrárselas al cliente se las cerramos
-- también al admin. Misma salida: `security definer`, `search_path` fijo y la
-- comprobación de rol en una línea que se lee.
--
-- Van en dos funciones y no en una porque son dos preguntas. La de envíos la abre
-- quien va a imprimir etiquetas; esta la abre quien va a cuadrar el mes. Juntarlas
-- habría obligado a cada pantalla a cargar lo que no mira, y habría puesto el margen
-- de la tienda en la consulta que más veces al día se hace.
--
-- **Lo que esto NO es.** `neto_tras_guia` no es la ganancia: descuenta la comisión de
-- Mercado Pago y lo que costó la guía, nada más. El costo de los productos está en el
-- catálogo y no entra aquí. Llamarle «ganancia» a esta cifra sería tomar una decisión
-- de negocio con un número que no la sostiene.
--
-- Requiere 48_codigos.sql: lee `envio_gratis`.
--
-- Idempotente: se puede correr dos veces.

begin;

-- `create or replace` no puede cambiar el tipo de retorno; con el drop delante, este
-- archivo se edita y se vuelve a correr sin pensarlo. Mismo criterio que en el 49.
drop function if exists fn_pagos_admin(uuid, date, date, boolean, integer);

create function fn_pagos_admin(
  p_id             uuid    default null,
  -- Rango por la fecha del **cobro**, no la de la solicitud: esta función es de
  -- dinero, y el dinero de noviembre es el que entró en noviembre, aunque el carrito
  -- se armara en octubre. Una solicitud sin pago no tiene fecha de cobro y por tanto
  -- no aparece en ningún rango, que es justo lo correcto: no hay ingreso que contar.
  p_desde          date    default null,
  p_hasta          date    default null,
  p_solo_aprobadas boolean default false,
  p_limite         integer default 200
)
returns table (
  id                 uuid,
  folio              bigint,
  cliente_nombre     text,
  estado             text,
  created_at         timestamptz,
  -- Lo que se le enseñó
  total_estimado     numeric,
  envio_costo        numeric,
  envio_gratis       boolean,
  codigo_descuento   text,
  descuento_pct      integer,
  descuento_monto    numeric,
  regalo             text,
  -- Lo que se cobró
  pago_estado        text,
  pago_total         numeric,
  pago_metodo        text,
  pago_marca         text,
  pago_ultimos4      text,
  pago_referencia    text,
  pago_orden_id      text,
  pago_actualizado_en timestamptz,
  -- Lo que llegó de verdad
  pago_neto          numeric,
  pago_comision      numeric,
  pago_liberacion    timestamptz,
  -- Lo que costó mandarlo
  guia_costo         numeric,
  -- Neto menos guía. Ver la advertencia de arriba: no es la ganancia.
  neto_tras_guia     numeric
)
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not is_admin() then
    raise exception 'Solo administracion puede consultar los pagos'
      using errcode = '42501';
  end if;

  return query
  select
    o.id,
    o.folio,
    o.cliente_nombre,
    o.estado::text,
    o.created_at,
    o.total_estimado,
    o.envio_costo,
    o.envio_gratis,
    o.codigo_descuento,
    o.descuento_pct,
    o.descuento_monto,
    o.regalo,
    o.pago_estado,
    o.pago_total,
    o.pago_metodo,
    o.pago_marca,
    o.pago_ultimos4,
    o.pago_referencia,
    o.pago_orden_id,
    o.pago_actualizado_en,
    o.pago_neto,
    o.pago_comision,
    o.pago_liberacion,
    o.guia_costo,
    -- Nulo mientras no se sepa el neto, y no cero: un cero aquí se sumaría en los
    -- totales del mes y diría que esa venta no dejó nada, cuando lo que pasa es que
    -- Mercado Pago todavía no ha contado cuánto.
    case
      when o.pago_neto is null then null
      else o.pago_neto - coalesce(o.guia_costo, 0)
    end
  from order_requests o
  where (p_id is null or o.id = p_id)
    and (not p_solo_aprobadas or o.pago_estado = 'aprobado')
    -- En hora de Ciudad de México, no en UTC. Con UTC, todo lo cobrado después de las
    -- seis de la tarde del 30 caería en el mes siguiente, y el corte del mes dejaría
    -- de cuadrar con el de la tienda. Esto impide usar un índice sobre la columna; a
    -- este volumen no se nota, y si algún día se notara, la solución es un índice
    -- sobre la expresión, no volver a UTC.
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

-- Igual que en el 49: quitárselo a `public` se lo quita a `service_role`, que lo
-- hereda. Ninguna Edge Function la llama; si alguna llegara a hacerlo, hay que
-- devolvérselo explícitamente.
revoke all on function fn_pagos_admin(uuid, date, date, boolean, integer) from public;
grant execute on function fn_pagos_admin(uuid, date, date, boolean, integer) to authenticated;

commit;

-- ---------------------------------------------------------------------------
-- Comprobar
-- ---------------------------------------------------------------------------
--
-- Con sesión de admin devuelve filas; con sesión de cliente lanza 42501, que
-- PostgREST traduce a 403.
--
-- select * from fn_pagos_admin(p_limite := 20);
-- select * from fn_pagos_admin(p_solo_aprobadas := true,
--                              p_desde := date '2026-11-01',
--                              p_hasta := date '2026-11-30');
--
-- Lo cobrado y lo que quedó, por mes. Se suma sobre la función, no sobre la tabla:
-- así el corte sale de los mismos números que la pantalla.
--
-- select to_char(pago_actualizado_en at time zone 'America/Mexico_City', 'YYYY-MM') as mes,
--        count(*)                  as ventas,
--        sum(pago_total)           as cobrado,
--        sum(pago_comision)        as comisiones,
--        sum(guia_costo)           as guias,
--        sum(neto_tras_guia)       as neto_tras_guia
--   from fn_pagos_admin(p_solo_aprobadas := true, p_limite := 100000)
--  group by 1 order by 1 desc;
--
-- Lo que Mercado Pago todavía no libera:
--
-- select folio, pago_total, pago_neto,
--        pago_liberacion at time zone 'America/Mexico_City' as libera_cdmx
--   from fn_pagos_admin(p_solo_aprobadas := true)
--  where pago_liberacion > now()
--  order by pago_liberacion;
--
-- Si sale «structure of query does not match function result type», es que algún tipo
-- declarado no coincide. Los de verdad:
--
-- select column_name, data_type
--   from information_schema.columns
--  where table_name = 'order_requests'
--    and column_name in ('folio', 'total_estimado', 'descuento_pct', 'pago_liberacion')
--  order by column_name;
