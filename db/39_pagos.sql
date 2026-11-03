-- 39_pagos.sql
--
-- El cobro con Mercado Pago: en qué va el pago de cada solicitud, y el historial de
-- todo lo que Mercado Pago nos ha dicho sobre él.
--
-- Mismo reparto que las guías (36 y 38): unas columnas en `order_requests` para lo que
-- se enseña en pantalla, y una tabla aparte para la secuencia. Y por la misma razón: un
-- pago no es un dato, es una sucesión de avisos. Se crea la orden, el cliente paga, la
-- tarjeta se rechaza, lo intenta de nuevo, aprueba, y semanas después pide reembolso.
-- En una columna solo cabe el último estado, y la pregunta que de verdad se hace
-- —«¿por qué este pedido aparece pagado si el cliente dice que no le pasó?»— necesita
-- los avisos en orden.
--
-- **Ninguna de estas columnas la escribe el cliente.** Solo las lee. Quien las escribe
-- es la Edge Function `pago-webhook` con service_role, después de comprobar la firma
-- del aviso y de **volver a preguntarle a Mercado Pago** cuánto se pagó. Si el
-- navegador pudiera escribir `pago_estado`, «pagado» sería una palabra que cualquiera
-- se puede poner.
--
-- Idempotente: se puede correr dos veces.

begin;

-- ---------------------------------------------------------------------------
-- Lo que se enseña: el estado del pago de la solicitud
-- ---------------------------------------------------------------------------

alter table order_requests
  add column if not exists pago_estado        text,
  add column if not exists pago_total         numeric(10,2),
  add column if not exists pago_orden_id      text,
  add column if not exists pago_referencia    text,
  add column if not exists pago_metodo        text,
  add column if not exists pago_actualizado_en timestamptz;

comment on column order_requests.pago_estado is
  'pendiente | aprobado | rechazado | reembolsado | cancelado. Lo traduce la Edge '
  'Function desde el estado de Mercado Pago, que tiene más matices de los que sirve '
  'enseñar.';
comment on column order_requests.pago_total is
  'Lo que de verdad se cobró, según Mercado Pago. No lo que la pantalla calculó: para '
  'eso están total_estimado y envio_costo, y la diferencia entre ambos es lo que se '
  'quiere poder ver.';
comment on column order_requests.pago_orden_id is
  'El id de la orden en Mercado Pago (Orders API). Lo que se usa para volver a '
  'preguntar por ella.';
comment on column order_requests.pago_referencia is
  'El id del pago dentro de la orden. Es el número que el cliente ve en su '
  'comprobante y el que sirve para buscarlo en el panel de Mercado Pago.';
comment on column order_requests.pago_metodo is
  'Con qué pagó, en palabras: «Tarjeta de crédito», «OXXO», «Saldo en cuenta». Para '
  'el historial del cliente y para saber qué ofrecer.';
comment on column order_requests.pago_actualizado_en is
  'Cuándo llegó el último aviso. Un pago «pendiente» de hace tres semanas no está '
  'pendiente: está muerto, y sin esta fecha no se distingue de uno de hace un minuto.';

-- Un estado escrito a mano que no esté en la lista rompe la pantalla sin avisar.
do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'order_requests_pago_estado_check'
  ) then
    alter table order_requests add constraint order_requests_pago_estado_check
      check (pago_estado is null or pago_estado in
        ('pendiente', 'aprobado', 'rechazado', 'reembolsado', 'cancelado'));
  end if;
end $$;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'order_requests_pago_total_check'
  ) then
    alter table order_requests add constraint order_requests_pago_total_check
      check (pago_total is null or pago_total >= 0);
  end if;
end $$;

-- Una orden de Mercado Pago pertenece a una sola solicitud. Si dos filas apuntaran a
-- la misma, un aviso de pago acabaría marcando pagada la solicitud equivocada.
create unique index if not exists idx_order_requests_pago_orden
  on order_requests (pago_orden_id) where pago_orden_id is not null;

-- El cliente **lee** su estado de pago; no lo escribe. Sin `insert` y sin `update` a
-- propósito: estas columnas las pone el webhook con service_role. El método de pago y
-- la referencia se le dan porque son suyos —es su comprobante—; el id de la orden
-- también, porque es lo que tiene que decirnos si algo no cuadra.
grant select (pago_estado, pago_total, pago_orden_id, pago_referencia, pago_metodo,
              pago_actualizado_en)
   on order_requests to authenticated;

-- ---------------------------------------------------------------------------
-- El historial: cada aviso de Mercado Pago, en orden
-- ---------------------------------------------------------------------------

create table if not exists pagos (
  id         uuid primary key default gen_random_uuid(),
  request_id uuid not null references order_requests(id) on delete cascade,

  -- Qué pasó, con nuestras palabras. `creada` es cuando nosotros pedimos la orden;
  -- el resto llega por webhook.
  accion     text not null check (accion in
    ('creada', 'pendiente', 'aprobado', 'rechazado', 'reembolsado', 'cancelado')),

  orden_id   text,
  pago_id    text,
  monto      numeric(10,2),
  metodo     text,

  -- El estado tal cual lo dijo Mercado Pago, sin traducir: `accredited`,
  -- `cc_rejected_insufficient_amount`, `charged_back`… Nuestras cinco palabras sirven
  -- para la pantalla; para entender *por qué* se rechazó una tarjeta hace falta la
  -- suya, y si solo guardáramos la traducción habría que adivinarla.
  estado_crudo text,

  -- Quién pidió la orden. Nulo en los avisos del webhook, que no los pide nadie.
  -- `set null` al borrar la cuenta: borrar a una persona no borra el rastro de un cobro.
  usuario_id uuid references profiles(id) on delete set null,
  usuario    text,

  nota       text,
  created_at timestamptz not null default now()
);

create index if not exists idx_pagos_solicitud
  on pagos (request_id, created_at desc);

-- Mercado Pago reintenta los avisos y manda el mismo varias veces. Sin esto, un pago
-- aprobado aparecería tres veces en el historial y habría que mirar las horas para
-- saber que fue uno solo.
create unique index if not exists idx_pagos_aviso
  on pagos (request_id, accion, coalesce(pago_id, ''), coalesce(estado_crudo, ''));

alter table pagos enable row level security;

-- Solo administración. Al cliente no le dice nada que no esté ya en su historial, y sí
-- dice cómo responde Mercado Pago por dentro.
drop policy if exists pagos_select on pagos;
create policy pagos_select on pagos for select to authenticated
  using (is_admin());

-- Sin política de insert, update ni delete **a propósito**: solo entra por las Edge
-- Functions, que usan service_role y saltan el RLS.

commit;

-- Para ver si el cobro inmediato funciona o la gente lo abandona:
--
-- select pago_estado, count(*), round(sum(pago_total), 2) as cobrado
--   from order_requests
--  where pago_estado is not null
--  group by 1
--  order by count(*) desc;
--
-- Y las órdenes que se crearon pero nunca se pagaron, que son las que hay que perseguir
-- por WhatsApp:
--
-- select folio, pago_actualizado_en, total_estimado, envio_costo
--   from order_requests
--  where pago_estado = 'pendiente'
--    and pago_actualizado_en < now() - interval '2 days'
--  order by pago_actualizado_en;
