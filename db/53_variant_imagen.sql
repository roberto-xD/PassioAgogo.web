-- ============================================================
-- 53 · Foto de la variante
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después del 52 · Idempotente
--
-- En la ficha de producto, al elegir una presentación el carrusel se
-- queda en la foto de esa presentación y deja de pasar solo. Para eso
-- hace falta saber qué foto le corresponde a cada variante, y hasta
-- ahora no había forma: `products.imagenes` es una lista suelta y la
-- variante no apuntaba a ninguna.
--
-- **Se guarda la URL, no la posición.** Un número sería más corto de
-- escribir, pero el día que alguien reordene las fotos del producto
-- todas las variantes pasarían a señalar la foto equivocada sin que
-- nada fallara ni lo avisara. Con la URL, reordenar no rompe nada.
--
-- La URL debe ser una de las de `products.imagenes` del mismo producto.
-- Si no lo es, la web no se cae: ignora el salto y el carrusel se
-- comporta como antes. La consulta del final lista esos casos.
--
-- Es opcional por variante: sin foto asignada, elegir esa presentación
-- no mueve el carrusel. Un producto cuyas presentaciones se distinguen
-- solo por el tamaño no necesita esto.
-- ============================================================

begin;

alter table product_variants
  add column if not exists imagen text;

comment on column product_variants.imagen is
  'URL de la foto de esta presentacion. Debe ser una de products.imagenes '
  'del mismo producto. Vacio = elegir esta presentacion no mueve el '
  'carrusel. Se guarda la URL y no la posicion para que reordenar las '
  'fotos del producto no reasigne las variantes en silencio.';

alter table product_variants drop constraint if exists chk_product_variants_imagen;
alter table product_variants add constraint chk_product_variants_imagen
  check (imagen is null or length(trim(imagen)) between 1 and 1000);

-- La web la necesita sin sesión. Este grant suma, no reemplaza, así que
-- `costo` sigue oculto a anon igual que hasta ahora.
grant select (imagen) on product_variants to anon, authenticated;

commit;

-- ============================================================
-- Qué repasar
-- ============================================================

-- 1. Variantes que apuntan a una foto que NO está en las del producto.
--    La web las ignora, así que no rompen nada, pero tampoco hacen lo
--    que quien las escribió esperaba.
select p.nombre as producto, v.titulo, v.imagen
  from product_variants v
  join products p on p.id = v.product_id
 where v.imagen is not null
   and trim(v.imagen) <> ''
   and not (v.imagen = any (coalesce(p.imagenes, array[]::text[])))
 order by p.nombre;

-- 2. Productos con varias presentaciones y varias fotos donde todavía
--    no se ha asignado ninguna: son los que ganarían con esto.
-- select p.nombre as producto,
--        count(*) as presentaciones,
--        coalesce(array_length(p.imagenes, 1), 0) as fotos
--   from product_variants v
--   join products p on p.id = v.product_id
--  where v.activo
--  group by p.id, p.nombre, p.imagenes
-- having count(*) > 1
--    and coalesce(array_length(p.imagenes, 1), 0) > 1
--    and count(v.imagen) = 0
--  order by p.nombre;

-- Para asignar una:
-- update product_variants
--    set imagen = 'https://.../menta.jpg'
--  where sku = '7508304289365';
