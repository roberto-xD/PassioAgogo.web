-- 42_pago_neto.sql
--
-- Lo que de verdad entra por cada venta, después de la comisión de Mercado Pago.
--
-- `pago_total` es lo que paga el cliente; no es lo que le queda a la tienda. Entre uno
-- y otro está la comisión, que cambia según el medio de pago y los meses sin
-- intereses. Sin esta columna, la única forma de saber cuánto dejó un pedido es abrir
-- el panel de Mercado Pago y cruzarlo a mano.
--
-- **La comisión es un dato de la tienda, no del cliente.** No se le concede ninguna de
-- estas columnas. Ver la nota sobre permisos al final: esa concesión por columnas solo
-- vale si no hay un `grant select` sobre la tabla entera, y eso hay que comprobarlo.
--
-- Idempotente: se puede correr dos veces.

begin;

alter table order_requests
  add column if not exists pago_neto       numeric(10,2),
  add column if not exists pago_liberacion timestamptz;

comment on column order_requests.pago_neto is
  'Lo que Mercado Pago acredita por este pago, ya descontada su comision: el campo '
  'transaction_details.net_received_amount de la API clasica de pagos. No es lo que '
  'hay en el banco: eso depende de la liberacion y del retiro.';
comment on column order_requests.pago_liberacion is
  'Cuando Mercado Pago libera el dinero (money_release_date). Hasta esa fecha el '
  'importe esta acreditado pero no disponible.';

-- La comisión se **calcula**, no se guarda: es lo que se cobró menos lo que llegó.
--
-- Generada y no una columna normal porque así no puede desviarse de las otras dos. La
-- alternativa era sumar el array `fee_details` que manda Mercado Pago, pero su
-- documentación no enumera los valores de `type` ni de `fee_payer` —hay comisiones que
-- paga el comprador y no la tienda—, así que sumarlo sería interpretar algo que no
-- está escrito. Restar sí: lo que no llegó, no llegó.
do $$
begin
  if not exists (
    select 1 from information_schema.columns
     where table_name = 'order_requests' and column_name = 'pago_comision'
  ) then
    alter table order_requests
      add column pago_comision numeric(10,2)
      generated always as (pago_total - pago_neto) stored;
  end if;
end $$;

comment on column order_requests.pago_comision is
  'Lo que se quedo Mercado Pago: pago_total - pago_neto. Calculada, para que no pueda '
  'contradecir a las otras dos.';

-- El historial guarda además el desglose crudo, tal como lo manda Mercado Pago.
--
-- En `jsonb` y sin interpretar a propósito: hoy no sabemos qué significa cada `type`
-- —su documentación no los enumera— y guardarlo entero permite entenderlo el dia que
-- haga falta, sin haber tenido que adivinarlo hoy.
alter table pagos
  add column if not exists neto      numeric(10,2),
  add column if not exists comisiones jsonb;

comment on column pagos.comisiones is
  'El array fee_details de Mercado Pago, sin interpretar: [{type, amount, fee_payer}].';

commit;

-- ---------------------------------------------------------------------------
-- COMPROBAR ANTES DE DARLO POR BUENO
-- ---------------------------------------------------------------------------
--
-- Estas columnas no se le conceden a `authenticated`, pero eso **solo sirve si no hay
-- una concesion sobre la tabla entera**. Con un `grant select on order_requests`, los
-- permisos por columna no pintan nada y cualquier cliente podria leer el margen de la
-- tienda. Es el mismo caso que `product_variants.costo`.
--
-- Esto debe devolver CERO filas:
--
-- select grantee, privilege_type
--   from information_schema.table_privileges
--  where table_name = 'order_requests'
--    and grantee = 'authenticated'
--    and privilege_type = 'SELECT';
--
-- Si devuelve algo, hay que quitarlo y conceder solo lo que el cliente necesita:
--
-- revoke select on order_requests from authenticated;
-- grant select (id, folio, estado, total_estimado, created_at, motivo_rechazo,
--               metodo_entrega, necesita_recoleccion,
--               envio_paqueteria, envio_servicio, envio_descripcion, envio_costo,
--               envio_entrega,
--               guia_numero, guia_rastreo_url, guia_paqueteria, guia_servicio,
--               pago_estado, pago_total, pago_orden_id, pago_referencia, pago_metodo,
--               pago_marca, pago_ultimos4, pago_actualizado_en,
--               dir_version, dir_contenido, dir_iv, dir_dk_usuario, dir_iv_usuario,
--               dir_salt_usuario, dir_kdf_iteraciones, dir_dk_admin, dir_admin_key_id)
--    on order_requests to authenticated;
--
-- (Sin pago_neto, pago_comision ni pago_liberacion, que son de la tienda. Y sin
-- guia_url, que es el PDF de la etiqueta.)

-- Para ver qué deja de verdad cada medio de pago:
--
-- select pago_metodo, pago_marca, count(*),
--        round(sum(pago_total), 2)    as cobrado,
--        round(sum(pago_neto), 2)     as recibido,
--        round(sum(pago_comision), 2) as comision,
--        round(100 * sum(pago_comision) / nullif(sum(pago_total), 0), 2) as pct
--   from order_requests
--  where pago_estado = 'aprobado' and pago_neto is not null
--  group by 1, 2 order by cobrado desc;
