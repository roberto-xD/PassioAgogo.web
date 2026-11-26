-- ============================================================
-- 55 · Una solicitud cerrada no se queda con el código
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después del 54 · Idempotente
--
-- Síntoma: un cliente con un código de la ruleta no podía volver a
-- pedir. Su código seguía pegado a una solicitud que la tienda había
-- **rechazado** desde la app de control, y la web le devolvía un 409 al
-- intentar usarlo en un carrito nuevo.
--
-- Sin salida por ninguna parte: la pantalla del cliente no ofrece
-- cancelar lo que la tienda no pudo atender —y hace bien, cancelar algo
-- ya rechazado no significa nada—, así que no había gesto que soltara
-- el código.
--
-- La causa de fondo no es el rechazo: es dónde vivía la regla. «Una
-- solicitud cerrada no retiene el código» estaba escrita **en cada
-- quien la cierra**, y ya eran tres: `fn_cancelar_solicitud` (51) y la
-- Edge Function del reembolso lo hacen; el rechazo desde la app de
-- control, que llegó por otro camino, no se enteró. El cuarto que
-- aparezca tampoco se enteraría.
--
-- Así que la regla baja a la base, donde no se puede olvidar. Da igual
-- quién cierre la solicitud ni desde qué aplicación.
--
-- **Lo que no toca: `codigos.usos`.** El uso se gasta al aprobarse el
-- pago, no al aplicar el código, así que una solicitud sin pagar nunca
-- llegó a consumirlo y no hay nada que devolver. Quien sí lo devuelve,
-- cuando hubo cobro, es `liberar_codigo` (51) desde el reembolso.
-- ============================================================

begin;

create or replace function fn_soltar_codigo_al_cerrar()
returns trigger
language plpgsql
as $$
begin
  if new.codigo_descuento is null then
    return new;
  end if;

  -- Cerrada = cancelada del todo, o rechazada por la tienda. La
  -- cancelación solo *solicitada* no cuenta: ahí el dinero sigue
  -- cobrado y la solicitud todavía puede acabar en reembolso.
  if new.cancelada_at is not null or new.estado = 'rechazada' then
    -- Con el pago aprobado no se suelta: el código es parte del
    -- expediente de un cobro que hay que poder reconstruir, y si hay
    -- que devolver el dinero, de eso se encarga el reembolso, que
    -- además devuelve el uso. Rechazar algo ya cobrado sin reembolsar
    -- es un error de proceso, y borrar el rastro lo escondería.
    if coalesce(new.pago_estado, '') <> 'aprobado' then
      new.codigo_descuento := null;
    end if;
  end if;

  return new;
end;
$$;

comment on function fn_soltar_codigo_al_cerrar() is
  'Al cerrarse una solicitud sin cobro aprobado, suelta su '
  'codigo_descuento para que el cliente lo pueda volver a usar. La '
  'regla vive aqui y no en cada quien cierra: cancelar, rechazar y '
  'reembolsar llegan por caminos distintos y uno se olvido.';

drop trigger if exists trg_soltar_codigo_al_cerrar on order_requests;
create trigger trg_soltar_codigo_al_cerrar
  before update on order_requests
  for each row execute function fn_soltar_codigo_al_cerrar();

commit;

-- ---------------------------------------------------------------------------
-- Las que ya estaban atrapadas
-- ---------------------------------------------------------------------------
--
-- Las solicitudes rechazadas o canceladas antes de este script siguen
-- reteniendo su código. Esto las suelta con el mismo criterio.

begin;

update order_requests
   set codigo_descuento = null
 where codigo_descuento is not null
   and (cancelada_at is not null or estado = 'rechazada')
   and coalesce(pago_estado, '') <> 'aprobado';

commit;

-- ============================================================
-- Qué repasar
-- ============================================================

-- 1. Debe devolver cero filas: ninguna solicitud cerrada y sin cobro
--    puede seguir ocupando un código.
select codigo_pedido, estado, pago_estado, codigo_descuento
  from order_requests
 where codigo_descuento is not null
   and (cancelada_at is not null or estado = 'rechazada')
   and coalesce(pago_estado, '') <> 'aprobado';

-- 2. Las que sí conservan el código a propósito: cerradas pero con el
--    cobro aprobado. Son expediente, no un error.
-- select codigo_pedido, estado, pago_estado, codigo_descuento, reembolso_at
--   from order_requests
--  where codigo_descuento is not null
--    and (cancelada_at is not null or estado = 'rechazada')
--  order by created_at desc;
