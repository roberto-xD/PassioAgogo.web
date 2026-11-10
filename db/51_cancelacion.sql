-- 51_cancelacion.sql
--
-- Cancelar una solicitud, desde el lado del cliente.
--
-- Tres escenarios, que son tres respuestas distintas a la misma pregunta:
--
--   sin pagar          se cancela y ya está. No hay dinero de por medio.
--   pagada, sin guía   se **pide** la cancelación. El dinero sigue cobrado, así que
--                      lo que cambia es que sale de la cola de envíos y la tienda
--                      devuelve a mano con `reembolsar`.
--   con guía           no se cancela aquí. El paquete ya existe y puede estar en
--                      manos de la paquetería; eso se habla por WhatsApp.
--
-- **Por qué «solicitada» y no «cancelada» cuando ya pagó.** Una tarjeta que dice
-- «Cancelada» con el cargo todavía en la tarjeta es lo más alarmante que esta pantalla
-- puede enseñar. El estado no se adelanta al dinero: pasa a cancelada cuando el
-- reembolso sale, y lo pone `reembolsar`.
--
-- **Por qué una función y no un `update` con RLS.** La decisión de cuál de los tres
-- escenarios aplica depende de columnas que el cliente no escribe —`pago_estado`,
-- `guia_numero`— y tiene que tomarse en la misma sentencia que escribe, o dos pestañas
-- abiertas la toman dos veces. Además evita abrirle `update` sobre `order_requests`,
-- que hoy no tiene.
--
-- **`estado` no se toca.** La columna vive en un script anterior a este repo y no sé si
-- es texto con check o un enum; meterle un valor nuevo a ciegas rompe la tabla o falla
-- a medias. Lo que marca la cancelación son columnas propias, y las consultas de
-- administración filtran por ellas.
--
-- Requiere 49_envios_admin_rpc.sql: vuelve a crear `fn_envios_admin`.
--
-- Idempotente: se puede correr dos veces.

begin;

-- ---------------------------------------------------------------------------
-- 1 · Las columnas
-- ---------------------------------------------------------------------------

alter table order_requests
  -- El cliente la pidió y el dinero sigue cobrado. Sale de la cola de envíos desde
  -- este momento: lo que no se va a enviar no puede seguir en la lista de lo que se
  -- va a enviar, o alguien le genera una guía mientras tanto.
  add column if not exists cancelacion_solicitada_at timestamptz,
  -- Cancelada de verdad: sin dinero de por medio, o con el reembolso ya hecho.
  add column if not exists cancelada_at timestamptz,
  add column if not exists cancelada_por text,
  add column if not exists cancelacion_motivo text,
  -- El reembolso, cuando lo hay (lo escribe `reembolsar`).
  add column if not exists reembolso_id text,
  add column if not exists reembolso_monto numeric(10,2),
  add column if not exists reembolso_at timestamptz;

comment on column order_requests.cancelacion_solicitada_at is
  'El cliente pidio cancelar una solicitud ya pagada. El dinero sigue cobrado: la '
  'devolucion la hace la tienda con la funcion reembolsar.';
comment on column order_requests.cancelada_at is
  'Cancelada de verdad: sin pago de por medio, o con el reembolso ya hecho.';
comment on column order_requests.cancelada_por is
  'cliente o tienda. Quien la canceló, no quien la pidió.';
comment on column order_requests.reembolso_id is
  'El id del reembolso en Mercado Pago. Con el se busca en su panel.';

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'order_requests_cancelada_por_check'
  ) then
    alter table order_requests add constraint order_requests_cancelada_por_check
      check (cancelada_por is null or cancelada_por in ('cliente', 'tienda'));
  end if;
end $$;

-- Es suyo: puede leer que la canceló y cuándo. El motivo también, que lo escribió él.
grant select (cancelacion_solicitada_at, cancelada_at, cancelada_por,
              cancelacion_motivo, reembolso_monto, reembolso_at)
   on order_requests to authenticated;

-- `reembolso_id` **no** se concede: es el identificador interno del movimiento en
-- Mercado Pago y no le sirve para nada que no le diga ya su propia referencia de pago.

-- Lo que queda por devolver: pagada, cancelación pedida y sin reembolso todavía.
create index if not exists idx_order_requests_por_reembolsar
  on order_requests (cancelacion_solicitada_at)
  where cancelacion_solicitada_at is not null and reembolso_at is null;

-- ---------------------------------------------------------------------------
-- 2 · Cancelar, desde el cliente
-- ---------------------------------------------------------------------------
--
-- Devuelve **qué pasó**, no un booleano: la pantalla tiene que decir tres cosas
-- distintas y un `false` no distingue «ya está cancelada» de «ya tiene guía».
--
-- `security definer` para poder leer `pago_estado` y `guia_numero`, que el cliente lee
-- pero no escribe, y para escribir columnas sobre las que no tiene `update`.
-- `search_path` fijo: sin esto alguien con permiso de crear objetos podría anteponer
-- un esquema suyo y cambiar a qué tabla se refiere `order_requests`.

drop function if exists fn_cancelar_solicitud(uuid, text);

create function fn_cancelar_solicitud(p_id uuid, p_motivo text default null)
returns table (resultado text, mensaje text)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_fila    order_requests%rowtype;
  v_usuario uuid := auth.uid();
begin
  if v_usuario is null then
    raise exception 'Necesitas una sesion' using errcode = '42501';
  end if;

  -- `for update`: entre leer y decidir no puede colarse un webhook que apruebe el
  -- pago, ni una segunda pestaña cancelando lo mismo.
  select * into v_fila
    from order_requests
   where id = p_id and created_by = v_usuario
   for update;

  -- Una solicitud ajena y una que no existe contestan igual. Distinguirlas diría, a
  -- quien pruebe identificadores, cuáles existen.
  if not found then
    return query select 'no_encontrada'::text,
      'No encontramos esa solicitud.'::text;
    return;
  end if;

  if v_fila.cancelada_at is not null or v_fila.cancelacion_solicitada_at is not null then
    return query select 'ya_cancelada'::text,
      'Esa solicitud ya estaba cancelada.'::text;
    return;
  end if;

  -- Con guía no se cancela desde aquí. El paquete ya existe y puede estar en manos de
  -- la paquetería; deshacerlo son dos cancelaciones —la guía y el cobro— y la segunda
  -- depende de que la primera salga. Eso no se decide en un diálogo.
  if v_fila.guia_numero is not null then
    return query select 'con_guia'::text,
      'Esa solicitud ya tiene guía de envío.'::text;
    return;
  end if;

  if coalesce(v_fila.pago_estado, '') = 'aprobado' then
    -- Pagada: se pide la cancelación. El estado no se adelanta al dinero.
    update order_requests
       set cancelacion_solicitada_at = now(),
           cancelada_por = 'cliente',
           cancelacion_motivo = nullif(btrim(coalesce(p_motivo, '')), '')
     where id = p_id;

    return query select 'solicitada'::text,
      'Registramos tu cancelación. Te escribimos por WhatsApp para la devolución.'::text;
    return;
  end if;

  -- Sin pagar: se cancela y ya está. El código, si lo llevaba, se suelta: nunca se
  -- gastó —eso lo hace el webhook al aprobarse el pago— y el índice único de
  -- `codigo_descuento` no lo dejaría usar en otro carrito mientras siguiera aquí.
  update order_requests
     set cancelada_at = now(),
         cancelada_por = 'cliente',
         cancelacion_motivo = nullif(btrim(coalesce(p_motivo, '')), ''),
         codigo_descuento = null
   where id = p_id;

  return query select 'cancelada'::text,
    'Cancelamos tu solicitud. Aquí te esperamos cuando quieras.'::text;
end;
$$;

revoke all on function fn_cancelar_solicitud(uuid, text) from public;
grant execute on function fn_cancelar_solicitud(uuid, text) to authenticated;

-- ---------------------------------------------------------------------------
-- 3 · Soltar el código al reembolsar
-- ---------------------------------------------------------------------------
--
-- El inverso de `consumir_codigo` (48). Hace falta porque el webhook gasta el uso al
-- aprobarse el pago: si después se devuelve el dinero, esa persona se quedó sin compra
-- **y** sin premio.
--
-- `greatest(usos - 1, 0)` y no `usos - 1` a secas: el check de la tabla exige
-- `usos >= 0`, y una llamada de más por un reintento no debe tumbar el reembolso.

create or replace function liberar_codigo(p_codigo text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_liberado boolean := false;
begin
  update codigos
     set usos = greatest(usos - 1, 0)
   where codigo = upper(btrim(p_codigo))
  returning true into v_liberado;

  if v_liberado then
    update wheel_spins
       set canjeado = false, canjeado_at = null
     where codigo = upper(btrim(p_codigo));
  end if;

  return coalesce(v_liberado, false);
end;
$$;

-- Solo la Edge Function del reembolso. Igual que con `consumir_codigo`: quitárselo a
-- `public` se lo quita a `service_role`, que lo hereda, así que hay que devolvérselo.
revoke all on function liberar_codigo(text) from public, anon, authenticated;
grant execute on function liberar_codigo(text) to service_role;

-- ---------------------------------------------------------------------------
-- 4 · Lo cancelado sale de la cola de envíos
-- ---------------------------------------------------------------------------
--
-- Esto es lo que impide el accidente de verdad: que alguien genere la guía de un
-- pedido que el cliente acaba de cancelar. Se vuelve a crear `fn_envios_admin` entera
-- —no se puede parchear el cuerpo de una función— con dos condiciones más en el filtro
-- de «por despachar». Buscar una solicitud por su id la sigue devolviendo cancelada y
-- todo: administración tiene que poder verla.

drop function if exists fn_envios_admin(uuid, boolean, integer);

create function fn_envios_admin(
  p_id              uuid    default null,
  p_solo_sin_guia   boolean default false,
  p_limite          integer default 100
)
returns table (
  id                   uuid,
  folio                bigint,
  cliente_nombre       text,
  cliente_telefono     text,
  estado               text,
  created_by           uuid,
  creador_rol          text,
  metodo_entrega       text,
  envio_paqueteria     text,
  envio_servicio       text,
  guia_numero          text,
  guia_url             text,
  guia_rastreo_url     text,
  guia_paqueteria      text,
  guia_servicio        text,
  guia_costo           numeric,
  guia_generada_en     timestamptz,
  necesita_recoleccion boolean,
  recoleccion_id       text,
  recoleccion_fecha    date,
  cancelacion_solicitada_at timestamptz,
  cancelada_at         timestamptz,
  cancelada_por        text,
  cancelacion_motivo   text,
  created_at           timestamptz
)
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not is_admin() then
    raise exception 'Solo administracion puede consultar los envios'
      using errcode = '42501';
  end if;

  return query
  select
    o.id,
    o.folio,
    o.cliente_nombre,
    o.cliente_telefono,
    o.estado::text,
    o.created_by,
    p.rol::text,
    o.metodo_entrega,
    o.envio_paqueteria,
    o.envio_servicio,
    o.guia_numero,
    o.guia_url,
    o.guia_rastreo_url,
    o.guia_paqueteria,
    o.guia_servicio,
    o.guia_costo,
    o.guia_generada_en,
    o.necesita_recoleccion,
    o.recoleccion_id,
    o.recoleccion_fecha,
    o.cancelacion_solicitada_at,
    o.cancelada_at,
    o.cancelada_por,
    o.cancelacion_motivo,
    o.created_at
  from order_requests o
  -- `left join` y no interno: con el interno, una solicitud cuyo perfil se haya
  -- borrado desaparece de la lista sin decir nada, y lo que desaparece de una
  -- pantalla de logística es un paquete que no se envía.
  left join profiles p on p.id = o.created_by
  where (p_id is null or o.id = p_id)
    and (
      not p_solo_sin_guia
      or (
        o.metodo_entrega = 'domicilio'
        and o.estado = 'enviada'
        and o.guia_numero is null
        and p.rol = 'cliente'
        -- Lo cancelado no se despacha, ni lo que está pedido para cancelar.
        and o.cancelada_at is null
        and o.cancelacion_solicitada_at is null
      )
    )
  order by
    case when p_solo_sin_guia then o.created_at end asc,
    o.guia_generada_en desc nulls last
  limit greatest(p_limite, 1);
end;
$$;

revoke all on function fn_envios_admin(uuid, boolean, integer) from public;
grant execute on function fn_envios_admin(uuid, boolean, integer) to authenticated;

commit;

-- ---------------------------------------------------------------------------
-- Comprobar
-- ---------------------------------------------------------------------------
--
-- Lo que queda por devolver. Esta es la pantalla que la app de control necesita:
--
-- select folio, cliente_nombre, cliente_telefono, pago_total, pago_referencia,
--        cancelacion_solicitada_at at time zone 'America/Mexico_City' as pedida_cdmx,
--        cancelacion_motivo
--   from order_requests
--  where cancelacion_solicitada_at is not null
--    and reembolso_at is null
--  order by cancelacion_solicitada_at;
--
-- El caso raro que hay que vigilar: una ficha de OXXO pagada **después** de cancelar.
-- La solicitud está cancelada y el dinero llegó igual, así que toca devolverlo.
--
-- select folio, pago_estado, pago_total, pago_referencia,
--        cancelada_at at time zone 'America/Mexico_City' as cancelada_cdmx
--   from order_requests
--  where cancelada_at is not null
--    and pago_estado = 'aprobado'
--    and reembolso_at is null;
--
-- Que la cancelación de un cliente saca la solicitud de la cola de envíos:
--
-- select folio from fn_envios_admin(p_solo_sin_guia := true);
