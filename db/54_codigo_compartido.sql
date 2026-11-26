-- ============================================================
-- 54 · Códigos compartidos: un código, una solicitud **por persona**
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después del 53 · Idempotente
--
-- El índice del script 43 era único **global**:
--
--   create unique index idx_order_requests_codigo
--     on order_requests (codigo_descuento) where codigo_descuento is not null;
--
-- Nació cuando los códigos eran solo de la ruleta: personales, uno por
-- persona. Con eso, «un código, una solicitud» y «un código, una
-- persona» eran la misma frase, y el índice resolvía la carrera de
-- abrir dos carritos a la vez con el mismo premio.
--
-- El script 48 generalizó `codigos` con `usos_max`, es decir, códigos de
-- varios usos, pero este índice no se revisó. Tal y como está, un código
-- de campaña repartido en redes **solo puede estar en una solicitud
-- pendiente en toda la tienda a la vez**: el segundo cliente que lo use
-- al mismo tiempo se lleva un 409 y no puede pedir.
--
-- La corrección es acotar la unicidad a la persona. Entonces:
--
--   · Un código de campaña lo puede tener a la vez una solicitud de cada
--     cliente. Las campañas funcionan.
--   · Una misma persona sigue sin poder tener dos solicitudes pendientes
--     con el mismo código. Que es exactamente la carrera que el 43 vino
--     a cerrar, porque los códigos de ruleta son personales.
--
-- Lo que de verdad limita cuántas veces se canjea un código **no es este
-- índice**: es `consumir_codigo` (48), que hace
-- `update codigos set usos = usos + 1 where usos < usos_max` en una sola
-- sentencia. El candado de fila serializa las llamadas simultáneas, así
-- que `usos_max` no se puede rebasar por mucha concurrencia que haya.
-- Este índice es la segunda línea, no la primera.
-- ============================================================

begin;

drop index if exists idx_order_requests_codigo;

-- `created_by` es quien pidió. No se filtra por cancelada: al cancelar,
-- `fn_cancelar_solicitud` (51) deja `codigo_descuento` en null, y el
-- reembolso hace lo mismo, así que una solicitud cerrada ya no ocupa el
-- código y no estorba a la siguiente.
create unique index if not exists idx_order_requests_codigo_persona
  on order_requests (codigo_descuento, created_by)
  where codigo_descuento is not null;

comment on index idx_order_requests_codigo_persona is
  'Un codigo, una solicitud por persona. Deja que un codigo de campana '
  'lo usen varios clientes a la vez, y sigue impidiendo que la misma '
  'persona abra dos carritos con el mismo codigo. El tope real de '
  'canjes lo pone usos_max via consumir_codigo (48).';

commit;

-- ============================================================
-- Qué repasar
-- ============================================================

-- 1. Antes de correrlo: ¿hay códigos de varios usos que estén chocando?
--    Si esta consulta devuelve filas, son clientes que ya se toparon con
--    el 409 por este motivo.
-- select c.codigo, c.usos, c.usos_max, count(o.id) as solicitudes
--   from codigos c
--   left join order_requests o on o.codigo_descuento = c.codigo
--  where c.usos_max > 1
--  group by c.codigo, c.usos, c.usos_max
--  order by solicitudes desc;

-- 2. Después: que nadie tenga dos solicitudes vivas con el mismo código.
--    Debe devolver cero filas; si devuelve alguna, el índice no se creó.
select codigo_descuento, created_by, count(*) as cuantas
  from order_requests
 where codigo_descuento is not null
 group by codigo_descuento, created_by
having count(*) > 1;

-- ============================================================
-- Repartir un código de campaña
-- ============================================================
--
-- insert into codigos (codigo, tipo, descuento_pct, usos_max, expira_at, nota)
-- values ('NOV15', 'descuento', 15, 200,
--         timestamptz '2026-11-30 23:59:59-06:00', 'Campana de noviembre');
--
-- `usos_max` es el tope de canjes **pagados**, no de carritos abiertos:
-- se gasta cuando el pago se aprueba, no al aplicarlo. Quien abandone el
-- pago no consume nada.
