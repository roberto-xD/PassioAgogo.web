-- 40_acuse.sql
--
-- Lo que le falta a la pantalla de «gracias»: los últimos dígitos de la tarjeta, el
-- texto del estado de cuenta y los productos que se sugieren al final.
--
-- Idempotente: se puede correr dos veces.

begin;

-- ---------------------------------------------------------------------------
-- 1. Los últimos cuatro dígitos de la tarjeta
-- ---------------------------------------------------------------------------
--
-- Para que el acuse diga «Visa ···· 4821» en vez de «Tarjeta». Sirve para reconocer
-- *con cuál* se pagó cuando se tienen varias, que es justo la duda al revisar un cobro.
--
-- Cuatro dígitos no identifican una tarjeta ni permiten usarla: es el mismo dato que
-- imprime cualquier recibo, y por eso se puede guardar. El número completo **no pasa
-- por aquí en ningún momento**: lo captura Mercado Pago en su página y nunca toca
-- nuestro servidor ni nuestro navegador. Esa es la razón de usar Checkout Pro.

alter table order_requests
  add column if not exists pago_ultimos4 text;

comment on column order_requests.pago_ultimos4 is
  'Ultimos cuatro digitos de la tarjeta, tal como los da Mercado Pago. Nunca el '
  'numero completo: ese no pasa por esta aplicacion.';

-- Cuatro dígitos, o nada. Un campo de texto libre aquí acabaría recibiendo cualquier
-- cosa el día que alguien escriba a mano en la tabla.
do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'order_requests_pago_ultimos4_check'
  ) then
    alter table order_requests add constraint order_requests_pago_ultimos4_check
      check (pago_ultimos4 is null or pago_ultimos4 ~ '^[0-9]{4}$');
  end if;
end $$;

-- Lo lee el cliente; lo escribe el webhook con service_role, como el resto de `pago_*`.
grant select (pago_ultimos4) on order_requests to authenticated;

-- ---------------------------------------------------------------------------
-- 2. Ajustes del acuse
-- ---------------------------------------------------------------------------

insert into site_settings (clave, valor, descripcion) values
  -- **Esta fila es la unica fuente.** La lee la Edge Function `crear-pago` para
  -- mandarselo a Mercado Pago, y la lee la web para decirle a la persona que lo vera
  -- asi en su estado de cuenta. Si cada una lo sacara de su lado, acabarian diciendo
  -- cosas distintas y le estariamos prometiendo algo falso sobre su tarjeta.
  --
  -- Mercado Pago lo recorta a 13 caracteres.
  ('pago_statement', 'PAG',
   'Lo que se lee en el estado de cuenta de la tarjeta. Maximo 13 caracteres. '
   'Lo usan la Edge Function crear-pago y la pantalla de gracias: cambiarlo aqui '
   'los cambia a los dos.'),

  -- SKUs separados por coma, en el orden en que se quieren enseñar. Vacio = no se
  -- enseña la sección. Se eligen a mano a proposito: son pocos y cambian poco, y un
  -- motor de recomendaciones para esto seria construir una maquina para mover una
  -- silla.
  ('acuse_recomendados', '',
   'SKUs separados por coma que se sugieren al final del acuse de compra, en ese '
   'orden. Vacio oculta la seccion. Ejemplo: SKU-001,SKU-014,SKU-022'),

  -- El texto del empaque discreto. En la base y no en el codigo porque es una promesa
  -- al cliente: si algun dia cambia como se empaca, se cambia aqui y no hay que
  -- desplegar la web para dejar de prometer algo que ya no se cumple.
  ('acuse_empaque', 'Tu paquete llega en una caja neutra, sin logotipos ni '
   'descripcion del contenido. La guia declara «Articulos de higiene personal».',
   'Promesa de empaque discreto que se enseña en el acuse. Vacio la oculta.')
on conflict (clave) do nothing;

commit;

-- Para poner los recomendados, con SKUs que existan de verdad:
--
-- select v.sku, p.nombre, v.precio_venta
--   from product_variants v join products p on p.id = v.product_id
--  where v.activo and p.activo
--  order by p.nombre;
--
-- update site_settings set valor = 'SKU-001,SKU-014,SKU-022'
--  where clave = 'acuse_recomendados';
