-- ============================================================
-- 18 · Mueve el texto de `descripcion` a `resumen` en products
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después de 17_products_resumen_sobre_pedido · Re-ejecutable
--
-- Contexto: los productos se cargaron cuando la tarjeta del catálogo
-- todavía mostraba `descripcion`. Desde el script 17 la tarjeta lee
-- `resumen`, así que ese texto hay que trasladarlo y dejar
-- `descripcion` vacía para reescribirla más adelante con la ficha
-- larga.
--
-- La operación BORRA texto, por eso el paso 1 lo respalda antes.
-- No vacíes `products_descripcion_respaldo` hasta haber revisado el
-- catálogo en la web.
--
-- Dos límites que el script respeta en silencio y luego te reporta:
--   · `resumen` admite como máximo 160 caracteres (check del 17).
--     Las descripciones más largas NO se tocan: recortarlas sin
--     avisar perdería texto. El listado final las marca.
--   · Si ya escribiste un `resumen` a mano, manda el tuyo: esa fila
--     se deja intacta.
-- ============================================================

begin;

-- ------------------------------------------------------------
-- 1. Copia de seguridad
-- ------------------------------------------------------------
create table if not exists products_descripcion_respaldo (
  product_id    uuid primary key references products(id) on delete cascade,
  nombre        text,
  descripcion   text,
  respaldado_en timestamptz not null default now()
);

-- Sin políticas: la tabla queda fuera del alcance de anon y
-- authenticated, solo la ven postgres y service_role.
alter table products_descripcion_respaldo enable row level security;

-- `do nothing` para que una segunda ejecución nunca pise el respaldo
-- original con un valor ya modificado.
insert into products_descripcion_respaldo (product_id, nombre, descripcion)
select id, nombre, descripcion
  from products
 where coalesce(trim(descripcion), '') <> ''
on conflict (product_id) do nothing;

-- ------------------------------------------------------------
-- 2. El traslado
-- ------------------------------------------------------------
update products
   set resumen     = trim(descripcion),
       descripcion = null
 where coalesce(trim(descripcion), '') <> ''
   and length(trim(descripcion)) <= 160
   and coalesce(trim(resumen), '') = '';

commit;

-- ------------------------------------------------------------
-- 3. Revisión: todo lo que salga como PENDIENTE necesita tu mano
-- ------------------------------------------------------------
select
  p.nombre,
  p.resumen,
  length(coalesce(trim(p.descripcion), '')) as largo_descripcion_restante,
  case
    when coalesce(trim(p.descripcion), '') = '' then 'listo'
    when length(trim(p.descripcion)) > 160
      then 'PENDIENTE · descripcion de ' || length(trim(p.descripcion)) ||
           ' caracteres: no cabe en resumen'
    else 'PENDIENTE · ya tenia resumen propio: descripcion intacta'
  end as estado
from products p
order by estado desc, p.nombre;

-- ============================================================
-- OPCIONAL · Qué hacer con las descripciones demasiado largas
-- ============================================================
-- Lo recomendable es reescribirlas a mano: un resumen de tarjeta se
-- redacta distinto que un texto largo. Míralas primero:
--
-- select nombre, length(trim(descripcion)) as largo, descripcion
--   from products
--  where length(trim(descripcion)) > 160
--  order by largo desc;
--
-- Si prefieres recortarlas automáticamente, esto corta por la última
-- palabra completa antes del carácter 157 y cierra con puntos
-- suspensivos. El texto íntegro sigue en el respaldo.
--
-- begin;
-- update products
--    set resumen     = regexp_replace(left(trim(descripcion), 157), '\s+\S*$', '') || '…',
--        descripcion = null
--  where length(trim(descripcion)) > 160
--    and coalesce(trim(resumen), '') = '';
-- commit;

-- ============================================================
-- DESHACER · Devuelve el texto a `descripcion`
-- ============================================================
-- Restaura el original y vacía `resumen` solo si sigue siendo la
-- copia que hizo este script: los resúmenes que hayas escrito o
-- editado tú se conservan.
--
-- begin;
-- update products p
--    set descripcion = r.descripcion,
--        resumen     = case
--                        when p.resumen = trim(r.descripcion) then null
--                        else p.resumen
--                      end
--   from products_descripcion_respaldo r
--  where r.product_id = p.id;
-- commit;

-- ============================================================
-- LIMPIEZA · Cuando el catálogo ya se vea bien en la web
-- ============================================================
-- drop table products_descripcion_respaldo;
