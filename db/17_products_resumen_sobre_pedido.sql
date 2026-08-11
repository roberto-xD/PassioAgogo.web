-- ============================================================
-- 17 · Productos: resumen para la tarjeta y bandera "sobre pedido"
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después de 10_rls_adjustments · Idempotente
--
-- resumen: texto corto que la tarjeta del catálogo muestra en lugar
--   de `descripcion`. `descripcion` NO desaparece: se conserva para
--   la ficha ampliada del producto y sigue alimentando la búsqueda.
--
-- sobre_pedido: el artículo puede conseguirse por encargo aunque no
--   esté disponible en tienda. Cobra sentido sobre todo cuando el
--   producto está inactivo: distingue "descatalogado" de "no lo
--   tenemos, pero te lo conseguimos".
-- ============================================================

begin;

alter table products
  add column if not exists resumen text
  check (resumen is null or length(trim(resumen)) between 1 and 160);

alter table products
  add column if not exists sobre_pedido boolean not null default false;

-- ------------------------------------------------------------
-- RLS: sin esto la bandera sería invisible en la web.
--
-- Las políticas anteriores solo dejaban leer productos activos, así
-- que un producto inactivo marcado como "sobre pedido" nunca llegaría
-- al navegador y su badge no se mostraría jamás. Se amplían para
-- incluirlos, manteniendo ocultos los inactivos que tampoco se
-- consiguen por encargo.
-- ------------------------------------------------------------

drop policy if exists products_select_anon on products;
create policy products_select_anon on products for select to anon
  using (activo or sobre_pedido);

drop policy if exists products_select on products;
create policy products_select on products for select to authenticated
  using (is_staff() or activo or sobre_pedido);

-- Índice para la consulta del catálogo público.
create index if not exists idx_products_visibles
  on products(nombre)
  where activo or sobre_pedido;

commit;

-- ============================================================
-- Ejemplos (opcional):
-- ============================================================
-- update products
--    set resumen = 'Brocha vibradora grande recargable, silicona suave.'
--  where nombre = 'Brocha vibradora grande';
--
-- -- Producto agotado que sí se consigue por encargo:
-- update products set activo = false, sobre_pedido = true where nombre = '…';
