-- 35_order_requests_envio.sql
--
-- La opción de envío que eligió el cliente al armar su solicitud.
--
-- Sin esto, la pantalla cotizaría, la persona elegiría y su elección se perdería al
-- enviar: administración tendría que volver a cotizar y adivinar con cuál se quedó. Una
-- lista de precios en la que elegir no cambia nada es peor que no enseñar precios.
--
-- Lo que se guarda es **lo que se le dijo al cliente**, no lo que costará la guía. El
-- precio real se conoce al generarla, y puede diferir: la cotización usa la caja mayor
-- de la tienda cuando el bulto no cabe, y la paquetería aplica peso volumétrico. La
-- diferencia la absorbe la tienda; esta columna sirve para saber cuánta fue.
--
-- Idempotente: se puede correr dos veces.

begin;

alter table order_requests
  add column if not exists envio_paqueteria text,
  add column if not exists envio_servicio   text,
  add column if not exists envio_descripcion text,
  add column if not exists envio_costo      numeric(10,2),
  add column if not exists envio_entrega    text;

comment on column order_requests.envio_paqueteria is
  'Paquetería elegida, con el nombre que usa Envia: fedex, dhl, estafeta…';
comment on column order_requests.envio_servicio is
  'Servicio elegido, con el nombre que usa Envia: ground, express, ground_od…';
comment on column order_requests.envio_descripcion is
  'Lo que se leyó en pantalla: «Estafeta Terrestre».';
comment on column order_requests.envio_costo is
  'Lo cotizado y mostrado al cliente, no lo que costó la guía.';
comment on column order_requests.envio_entrega is
  'La promesa de la paquetería con sus palabras: «2-4 días», «Día siguiente».';

-- Negativo no es un dato incompleto, es imposible.
do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'order_requests_envio_costo_check'
  ) then
    alter table order_requests add constraint order_requests_envio_costo_check
      check (envio_costo is null or envio_costo >= 0);
  end if;
end $$;

-- El cliente las escribe al crear su solicitud y las lee en su historial. Sin update:
-- una vez enviada, el envío se cambia hablando con la tienda, no editando la fila.
grant select (envio_paqueteria, envio_servicio, envio_descripcion, envio_costo, envio_entrega),
      insert (envio_paqueteria, envio_servicio, envio_descripcion, envio_costo, envio_entrega)
   on order_requests to authenticated;

commit;

-- Para ver qué eligen, que es lo que dice si vale la pena seguir ofreciendo todas:
--
-- select envio_paqueteria, envio_servicio, count(*), round(avg(envio_costo), 2) as costo_medio
--   from order_requests
--  where envio_paqueteria is not null
--  group by 1, 2
--  order by count(*) desc;
