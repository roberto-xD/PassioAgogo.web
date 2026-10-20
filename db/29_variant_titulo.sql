-- ============================================================
-- 29 · Título de la variante
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después del 28 · Idempotente
--
-- Hasta ahora la web componía el rótulo de cada presentación con los
-- `attributes` de la variante, y cuando dos quedaban iguales recurría al
-- SKU. Eso dejaba a la vista códigos de inventario y rótulos
-- inconsistentes: «Menta», «25 mL · 7508304289358», «Fresa · 25 mL».
--
-- Con un título propio el rótulo deja de deducirse y pasa a escribirse.
--
-- Este script lo siembra con «Nombre del producto · atributos» para que
-- ninguna variante nazca sin rótulo; la idea es corregirlos a mano
-- después. La consulta del final lista los que conviene repasar.
-- ============================================================

begin;

alter table product_variants
  add column if not exists titulo text;

comment on column product_variants.titulo is
  'Rotulo de la presentacion que ve el cliente. Vacio = la web lo deduce '
  'de los attributes. No usar codigos de inventario: esto se publica.';

alter table product_variants drop constraint if exists chk_product_variants_titulo;
alter table product_variants add constraint chk_product_variants_titulo
  check (titulo is null or length(trim(titulo)) between 1 and 120);

-- La web lo necesita sin sesión. El resto de columnas sigue como estaba:
-- este grant suma, no reemplaza, así que `costo` continúa oculto a anon.
grant select (titulo) on product_variants to anon, authenticated;

commit;

-- ============================================================
-- Siembra
--
-- Solo toca las que no tengan título, así que es seguro volver a
-- ejecutarlo cuando ya hayas corregido algunos a mano.
-- ============================================================
begin;

-- 1. Las que tienen atributos: nombre + atributos legibles.
--    El rótulo sale de `attribute_presets`, igual que las chips, así que
--    «menta» se escribe «Menta» y «25» se escribe «25 mL». El orden lo
--    manda `attribute_presets.orden`; a igualdad, la clave alfabética.
with atributos as (
  select v.id,
         string_agg(
           coalesce(nullif(trim(ap.identificador), ''), initcap(a.value)),
           ' · ' order by coalesce(ap.orden, 999), a.key
         ) as texto
    from product_variants v
    cross join lateral jsonb_each_text(coalesce(v.attributes, '{}'::jsonb)) a
    left join attribute_presets ap
           on lower(trim(ap.clave)) = lower(trim(a.key))
          and lower(trim(ap.valor)) = lower(trim(a.value))
          and ap.activo
   group by v.id
)
update product_variants v
   set titulo = left(trim(p.nombre) || ' · ' || a.texto, 120)
  from products p, atributos a
 where p.id = v.product_id
   and a.id = v.id
   and a.texto is not null
   and (v.titulo is null or trim(v.titulo) = '');

-- 2. Las que no tienen atributos: solo el nombre del producto.
update product_variants v
   set titulo = left(trim(p.nombre), 120)
  from products p
 where p.id = v.product_id
   and (v.titulo is null or trim(v.titulo) = '');

commit;

-- ============================================================
-- Qué repasar
-- ============================================================

-- 1. Productos con varias presentaciones y TÍTULOS REPETIDOS. Son los
--    urgentes: en la web salen como opciones indistinguibles.
select p.nombre as producto, v.titulo, count(*) as cuantas,
       string_agg(v.sku, ', ') as skus
  from product_variants v
  join products p on p.id = v.product_id
 group by p.nombre, v.titulo
having count(*) > 1
 order by p.nombre;

-- 2. Todas las presentaciones de los productos que tienen más de una,
--    para repasar la redacción de un vistazo.
-- select p.nombre as producto, v.titulo, v.precio_venta
--   from product_variants v
--   join products p on p.id = v.product_id
--  where v.activo
--    and (select count(*) from product_variants x
--          where x.product_id = v.product_id and x.activo) > 1
--  order by p.nombre, v.precio_venta;

-- Para corregir uno:
-- update product_variants set titulo = 'Menta 25 mL' where sku = '7508304289365';
