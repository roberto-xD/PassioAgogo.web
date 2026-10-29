-- 36_order_requests_guia.sql
--
-- La guía de envío: lo que devuelve la paquetería cuando el pedido ya es un paquete.
--
-- Se separa de las columnas `envio_*` (35) a propósito. Aquellas guardan **lo que se le
-- prometió al cliente** al cotizar; estas, **lo que pasó de verdad**. Casi siempre
-- coinciden, pero no siempre: el bulto puede no caber en la caja que se cotizó, la
-- paquetería aplica peso volumétrico, o administración acaba enviando con otra. Mezclar
-- ambas cosas en una columna haría imposible saber cuánto se desvió, que es justo el
-- número que dice si los topes de empaque están bien puestos.
--
-- Quién escribe esto: la Edge Function `generar-guia`, con service_role, después de
-- comprobar que quien la llama es administración. La web nunca escribe aquí.
--
-- Idempotente: se puede correr dos veces.

begin;

alter table order_requests
  -- Lo que se imprime y se pega en la caja.
  add column if not exists guia_numero      text,
  add column if not exists guia_url         text,
  add column if not exists guia_rastreo_url text,
  -- Con cuál se envió de verdad.
  add column if not exists guia_paqueteria  text,
  add column if not exists guia_servicio    text,
  -- Lo que costó de verdad.
  add column if not exists guia_costo       numeric(10,2),
  -- El identificador de Envia. Sin él no se puede cancelar la guía ni pedir que pasen
  -- a recogerla: las dos cosas se piden por ese número, no por el de rastreo.
  add column if not exists guia_envia_id    text,
  add column if not exists guia_generada_en timestamptz,
  -- Recolección a domicilio (`/ship/pickup/`), cuando se pide.
  add column if not exists recoleccion_id   text,
  add column if not exists recoleccion_fecha date;

comment on column order_requests.guia_numero is
  'Número de rastreo de la paquetería.';
comment on column order_requests.guia_url is
  'PDF de la etiqueta. Para imprimir, no para enseñarle al cliente.';
comment on column order_requests.guia_envia_id is
  'shipmentId de Envia. Hace falta para cancelar y para pedir recolección.';
comment on column order_requests.guia_costo is
  'Lo que costó la guía de verdad, frente a envio_costo que es lo cotizado.';

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'order_requests_guia_costo_check'
  ) then
    alter table order_requests add constraint order_requests_guia_costo_check
      check (guia_costo is null or guia_costo >= 0);
  end if;
end $$;

-- Una guía por solicitud. Sin esto, dos clics seguidos en «generar» cobran dos veces y
-- la segunda etiqueta queda huérfana: impresa, pagada y pegada en ninguna caja.
create unique index if not exists order_requests_guia_unica
  on order_requests (guia_envia_id) where guia_envia_id is not null;

-- El cliente ve **su rastreo**, no su etiqueta. El número y el enlace le sirven para
-- seguir el paquete; el PDF es para imprimir en la tienda y no le aporta nada.
-- El RLS ya limita cada quien a sus propias solicitudes.
grant select (guia_numero, guia_rastreo_url, guia_paqueteria, guia_servicio)
   on order_requests to authenticated;

commit;

-- ============================================================
-- Para revisar
-- ============================================================
--
-- 1. Cuánto se desvía lo cotizado de lo que costó. Si la diferencia es sistemática,
--    los topes de empaque (100x50x50, 40 kg) están mal puestos:
--
-- select envio_paqueteria, count(*),
--        round(avg(envio_costo), 2) as cotizado,
--        round(avg(guia_costo), 2)  as real,
--        round(avg(guia_costo - envio_costo), 2) as diferencia
--   from order_requests
--  where guia_costo is not null and envio_costo is not null
--  group by 1 order by 1;
--
-- 2. Pedidos con guía pero sin recolección pedida:
--
-- select folio, guia_paqueteria, guia_numero, guia_generada_en
--   from order_requests
--  where guia_envia_id is not null and recoleccion_id is null
--  order by guia_generada_en;
