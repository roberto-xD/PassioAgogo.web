-- 32_metodo_entrega.sql
--
-- Cómo quiere recibir el pedido quien lo solicita: envío a domicilio o recogerlo en
-- persona. Hasta ahora la web no lo registraba y administración tenía que preguntarlo
-- por WhatsApp en cada solicitud.
--
-- Va en `order_requests` y no en `profiles` porque es una decisión de cada pedido: la
-- misma persona puede querer que uno le llegue a casa y recoger el siguiente.
--
-- Idempotente: se puede correr dos veces.

begin;

alter table order_requests
  add column if not exists metodo_entrega text not null default 'domicilio';

-- Dos valores y nada más. Sin esto, un cliente modificado podría escribir cualquier
-- cosa en la columna y la app de control tendría que defenderse de texto libre.
do $$
begin
  if not exists (
    select 1 from pg_constraint
     where conname = 'order_requests_metodo_entrega_check'
  ) then
    alter table order_requests
      add constraint order_requests_metodo_entrega_check
      check (metodo_entrega in ('domicilio', 'personal'));
  end if;
end $$;

comment on column order_requests.metodo_entrega is
  'domicilio = envío a la dirección cifrada del cliente; personal = entrega en mano en CDMX.';

-- El cliente la escribe al crear su solicitud y la lee en su historial. No se le da
-- update: una vez enviada, el método se cambia hablando con la tienda, no editando la
-- fila. Administración ya tiene update sobre la tabla completa.
grant select (metodo_entrega), insert (metodo_entrega) on order_requests to authenticated;

commit;

-- Para revisar lo que ya había: todo lo anterior a este script queda como 'domicilio',
-- que es lo que la web venía ofreciendo de hecho.
--
--   select metodo_entrega, count(*) from order_requests group by 1;
