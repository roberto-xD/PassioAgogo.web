-- 49_envios_admin_rpc.sql
--
-- Lo que la app de control necesita leer y el 47 le quitó.
--
-- El 47 cerró `order_requests` columna por columna para que nadie con sesión pudiera
-- leerse el margen de la tienda. Pero un `grant` es **por rol**, no por persona: el
-- admin entra por el mismo `authenticated` que cualquier cliente, así que al cerrarle
-- la puerta al segundo se la cerramos también al primero. Con permisos por columna no
-- hay forma de decir «estos ven más»; es una limitación del mecanismo, no un descuido.
--
-- La salida es una función `security definer`: corre con los permisos de su dueño —y
-- por eso ve las columnas restringidas— y comprueba el rol ella misma, en una línea
-- que se lee.
--
-- 1. FUERA LA VISTA v_envios_admin
--
--    Supabase la marca como CRITICAL y tiene razón: una vista sin `security_invoker`
--    corre con los permisos de su dueño igual que esta función, pero su única defensa
--    es un `where is_admin()` enterrado en el cuerpo. El linter no puede comprobarlo, y
--    quien edite la vista mañana tampoco tiene por qué verlo.
--
--    La diferencia de verdad no es el linter: es que la vista, si la defensa falla,
--    devuelve **cero filas en silencio**, y una lista vacía se parece muchísimo a «hoy
--    no hay envíos». La función lanza 42501, que PostgREST traduce a 403 y se
--    diagnostica en diez segundos.
--
--    *** ESTO ROMPE LO QUE TODAVÍA CONSULTE LA VISTA. *** Corre este script el mismo
--    día que publiques la versión de la app de control que llama a `fn_envios_admin`,
--    no antes: mientras tanto la vista sigue funcionando, porque es de su dueño y los
--    permisos por columna del 47 no la tocan.
--
-- 2. order_id PARA EL HISTORIAL
--
--    Enlaza la solicitud con el pedido en que se convirtió. Es dato suyo y no expone
--    nada que no deba ver. Va también a la lista condicional del 47: si no, volver a
--    correr aquel se lo llevaría por delante.
--
-- Idempotente: se puede correr dos veces.

begin;

-- ---------------------------------------------------------------------------
-- 1 · Fuera la vista
-- ---------------------------------------------------------------------------
drop view if exists v_envios_admin;

-- ---------------------------------------------------------------------------
-- 2 · order_id para el historial
-- ---------------------------------------------------------------------------
grant select (order_id) on order_requests to authenticated;

-- ---------------------------------------------------------------------------
-- 3 · La función de administración
--
-- `security definer` para poder leer las columnas que el 47 quitó a
-- `authenticated`: guia_url, guia_costo y las de recolección.
--
-- `search_path` fijo: sin esto, alguien con permiso de crear objetos podría anteponer
-- un esquema suyo y secuestrar lo que resuelven los nombres de dentro —incluido
-- `is_admin()`, que es toda la defensa.
--
-- La comprobación de rol **lanza** en vez de devolver vacío. Ver arriba.
-- ---------------------------------------------------------------------------

-- `create or replace` no puede cambiar el tipo de retorno de una función que ya
-- existe: el día que haya que añadir una columna a la lista de abajo, un replace a
-- secas falla con «cannot change return type». Con el drop delante, este archivo se
-- puede editar y volver a correr sin pensarlo.
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
    o.created_at
  from order_requests o
  -- `left join` y no `join`: con el interno, una solicitud cuyo perfil se haya
  -- borrado desaparece de la lista sin decir nada, y lo que desaparece de una
  -- pantalla de logística es un paquete que no se envía. El filtro de abajo sigue
  -- dejándola fuera de «por despachar», que es donde sí importa el rol.
  left join profiles p on p.id = o.created_by
  where (p_id is null or o.id = p_id)
    and (
      not p_solo_sin_guia
      or (
        -- Lo que queda por despachar: de domicilio, aún abierta, sin guía, y
        -- levantada por el propio cliente, que son las únicas con dirección cifrada
        -- del destinatario.
        o.metodo_entrega = 'domicilio'
        and o.estado = 'enviada'
        and o.guia_numero is null
        and p.rol = 'cliente'
      )
    )
  order by
    case when p_solo_sin_guia then o.created_at end asc,
    o.guia_generada_en desc nulls last
  limit greatest(p_limite, 1);
end;
$$;

-- Quitarle el permiso a `public` se lo quita también a `service_role`, que lo hereda.
-- Aquí da igual —las Edge Functions leen `order_requests` directamente y con
-- `service_role` se saltan RLS y permisos por columna—, pero conviene saberlo antes de
-- llamarla desde una: habría que devolvérselo explícitamente.
revoke all on function fn_envios_admin(uuid, boolean, integer) from public;
grant execute on function fn_envios_admin(uuid, boolean, integer) to authenticated;

commit;

-- ---------------------------------------------------------------------------
-- Comprobar
-- ---------------------------------------------------------------------------
--
-- Con sesión de admin devuelve filas; con sesión de cliente lanza 42501, que PostgREST
-- traduce a 403. Eso es lo que se busca: un error claro.
--
-- select * from fn_envios_admin(p_solo_sin_guia := true);
-- select * from fn_envios_admin();
--
-- Que ya no quede la vista:
-- select relname from pg_class where relname = 'v_envios_admin';
--
-- Que order_id esté concedido:
-- select column_name from information_schema.column_privileges
--  where table_name = 'order_requests' and grantee = 'authenticated'
--    and column_name = 'order_id';
--
-- Si al llamarla sale «structure of query does not match function result type», es que
-- algún tipo declarado arriba no coincide con el de la columna. Los de verdad:
--
-- select column_name, data_type
--   from information_schema.columns
--  where table_name = 'order_requests'
--    and column_name in ('folio', 'guia_costo', 'recoleccion_id', 'recoleccion_fecha')
--  order by column_name;
