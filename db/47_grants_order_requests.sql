-- 47_grants_order_requests.sql
--
-- Lo que el cliente puede leer de `order_requests`, columna por columna.
--
-- Al quitar el `grant select` sobre la tabla entera —que era lo que dejaba a cualquiera
-- con sesión leer el margen de la tienda— se cayeron también las columnas de siempre:
-- `id`, `folio`, `estado`… nunca se habían concedido por columna porque las cubría
-- aquel permiso. El historial empezó a devolver 403.
--
-- Esto repone lo que falta. Los scripts anteriores ya concedieron lo suyo (32, 35, 36,
-- 37, 39, 40, 43, 45) y esas concesiones siguen vivas; aquí se repiten todas de todas
-- formas, para que este archivo sea la lista completa y no haya que reconstruirla
-- leyendo ocho scripts el día que algo falle.
--
-- **Lo que NO se concede, y por qué:**
--
--   pago_neto, pago_comision, pago_liberacion   el margen de la tienda
--   guia_url                                    el PDF de la etiqueta, para imprimir
--   guia_costo, guia_envia_id                   lo que costó la guía de verdad
--   recoleccion_id, recoleccion_fecha           logística interna
--
-- Idempotente: se puede correr dos veces.

begin;

-- Por si acaso: que no vuelva a haber permiso sobre la tabla entera. Mientras exista,
-- las concesiones por columna de abajo no sirven de nada.
revoke select on order_requests from authenticated;

grant select (
  -- Identidad y estado.
  --
  -- `folio` **ya no**: es consecutivo, y enseñárselo al cliente cuenta cuántos
  -- pedidos lleva la tienda y a qué ritmo entran. Lo sustituye `codigo_pedido`
  -- (52_codigo_pedido.sql), que va en la lista condicional de abajo porque llega
  -- en un script posterior a este.
  id, estado, total_estimado, created_at, motivo_rechazo,
  -- Lo que escribió al pedir. Es suyo: puede leerlo.
  cliente_nombre, cliente_telefono, cliente_email, notas, created_by,
  -- Entrega
  metodo_entrega, necesita_recoleccion,
  -- Envío: lo que se le prometió
  envio_paqueteria, envio_servicio, envio_descripcion, envio_costo, envio_entrega,
  -- Guía: el número y el rastreo, para seguir su paquete. El PDF no.
  guia_numero, guia_rastreo_url, guia_paqueteria, guia_servicio, guia_generada_en,
  -- Pago: lo que pagó y con qué. Lo que la tienda recibió, no.
  pago_estado, pago_total, pago_orden_id, pago_referencia, pago_metodo,
  pago_marca, pago_ultimos4, pago_actualizado_en,
  -- Descuento y regalo
  codigo_descuento, descuento_pct, descuento_monto, regalo,
  -- El sobre cifrado de la dirección. Lo lee para descifrarlo en su navegador con su
  -- propia clave; sin esto, el acuse no puede enseñarle a dónde se envía.
  dir_version, dir_contenido, dir_iv, dir_dk_usuario, dir_iv_usuario,
  dir_salt_usuario, dir_kdf_iteraciones, dir_dk_admin, dir_admin_key_id
) on order_requests to authenticated;

-- Las columnas que conceden los scripts **posteriores** a este, una por una y solo si
-- existen.
--
-- Están aquí por el `revoke` del principio: se lleva por delante lo que concedieron
-- 48 y 49, así que volver a correr 47 después de ellos les quitaría el permiso por
-- detrás y el historial volvería al 403. Y están condicionadas porque 47 también tiene
-- que poder correrse **antes**: en la lista de arriba, una columna que todavía no
-- existe haría fallar el script entero, que es justo el agujero que vino a tapar.
--
-- Al añadir una columna nueva que el cliente deba leer: a esta lista, no a la de
-- arriba, si llega en un script con número mayor que 47.
do $$
declare
  v_col text;
begin
  foreach v_col in array array[
    'envio_gratis',   -- 48_codigos.sql: el envio lo cubrio un codigo
    'order_id',       -- 49_envios_admin_rpc.sql: el pedido en que se convirtio
    'codigo_pedido'   -- 52_codigo_pedido.sql: lo que ve el cliente en vez del folio
  ] loop
    if exists (
      select 1 from information_schema.columns
       where table_schema = 'public'
         and table_name = 'order_requests'
         and column_name = v_col
    ) then
      execute format(
        'grant select (%I) on order_requests to authenticated', v_col
      );
    end if;
  end loop;
end $$;

commit;

-- ---------------------------------------------------------------------------
-- Comprobar
-- ---------------------------------------------------------------------------
--
-- Cero filas: no hay permiso sobre la tabla entera.
--
-- select grantee, privilege_type
--   from information_schema.table_privileges
--  where table_name = 'order_requests'
--    and grantee = 'authenticated' and privilege_type = 'SELECT';
--
-- Y esto NO debe listar pago_neto, pago_comision, pago_liberacion ni guia_url:
--
-- select column_name
--   from information_schema.column_privileges
--  where table_name = 'order_requests'
--    and grantee = 'authenticated' and privilege_type = 'SELECT'
--  order by column_name;
--
-- La prueba de verdad, desde la web con sesión iniciada: el historial carga, el acuse
-- enseña la dirección, y pedir el margen a mano devuelve 403.
