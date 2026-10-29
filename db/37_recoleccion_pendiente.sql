-- 37_recoleccion_pendiente.sql
--
-- Marca qué pedidos hay que recoger en la tienda y cuáles se llevan a sucursal.
--
-- La decisión se toma al generar la guía, que es cuando se sabe con qué paquetería va y
-- si esa recoge. Pero la recolección **no se pide ahí**: Envia agrupa varias guías en
-- una sola si son de la misma paquetería y salen de la misma dirección, y ese agrupado
-- es justo lo que hace que valga la pena. Pedir una por pedido sería un camión por
-- paquete.
--
-- Así que se marca ahora y se pide después, en lote. La vista del final es la lista de
-- lo que queda pendiente, agrupada por paquetería.
--
-- Idempotente: se puede correr dos veces.

begin;

alter table order_requests
  -- Nulo = todavía no se ha decidido, que es distinto de «no hace falta». Un pedido sin
  -- guía no ha llegado a esa pregunta.
  add column if not exists necesita_recoleccion boolean;

comment on column order_requests.necesita_recoleccion is
  'true = pasan a recogerlo; false = se lleva a sucursal; null = aún sin decidir.';

-- Marcarlo lo hace la Edge Function con service_role; el cliente solo lee.
grant select (necesita_recoleccion) on order_requests to authenticated;

-- ============================================================
-- Lo que falta por recoger
-- ============================================================
--
-- Con guía, marcado para recolección y sin recolección pedida todavía. La app de
-- control lee esto, agrupa por paquetería y pide una recolección por cada grupo.
--
-- `security_invoker` para que el RLS siga aplicando a quien consulta: sin esto la vista
-- correría con los permisos de quien la creó y cualquiera vería los pedidos de todos.
create or replace view v_recolecciones_pendientes
with (security_invoker = on) as
select
  o.id,
  o.folio,
  o.guia_paqueteria,
  o.guia_numero,
  o.guia_generada_en,
  o.cliente_nombre
from order_requests o
where o.guia_numero is not null
  and o.necesita_recoleccion is true
  and o.recoleccion_id is null
order by o.guia_paqueteria, o.guia_generada_en;

comment on view v_recolecciones_pendientes is
  'Guías marcadas para recolección que todavía no se han pedido, por paquetería.';

grant select on v_recolecciones_pendientes to authenticated;

commit;

-- Cuántas esperan por paquetería, que es la pregunta que se hace al final del día:
--
-- select guia_paqueteria, count(*)
--   from v_recolecciones_pendientes
--  group by 1 order by 2 desc;
