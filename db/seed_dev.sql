-- ============================================================
-- Seed de DESARROLLO · Productos dummy para probar la conexión
-- web ↔ Supabase (catálogo + imágenes del bucket `inventory`).
--
-- ⚠ Solo para dev. Idempotente: seguro re-ejecutar.
-- Ejecutar en el SQL Editor de Supabase (corre como postgres,
-- por lo que RLS no aplica a los inserts).
--
-- ANTES DE CORRER: reemplaza los valores de `imagenes` por los
-- nombres reales de tus archivos en el bucket `inventory`
-- (p. ej. array['playera-roja.png']) o por URLs absolutas.
-- ============================================================

begin;

-- ---------- Categoría de prueba ----------
insert into categories (id, nombre, descripcion)
values ('a0000000-0000-4000-8000-000000000001', 'Demo', 'Categoría de prueba (seed dev)')
on conflict (id) do nothing;

-- ---------- Productos de prueba ----------
insert into products (id, nombre, descripcion, category_id, marca, imagenes)
values
  ('b0000000-0000-4000-8000-000000000001',
   'Producto demo 1', 'Primer producto de prueba del catálogo',
   'a0000000-0000-4000-8000-000000000001', 'Passio',
   array['REEMPLAZA-imagen-1.jpg']),
  ('b0000000-0000-4000-8000-000000000002',
   'Producto demo 2', 'Segundo producto de prueba, con dos variantes',
   'a0000000-0000-4000-8000-000000000001', 'Agogo',
   array['REEMPLAZA-imagen-2.jpg']),
  ('b0000000-0000-4000-8000-000000000003',
   'Producto demo 3', 'Producto sin imagen para probar el placeholder',
   'a0000000-0000-4000-8000-000000000001', null,
   '{}')
on conflict (id) do nothing;

-- ---------- Variantes (precio obligatorio) ----------
insert into product_variants (product_id, sku, precio_venta, costo)
values
  ('b0000000-0000-4000-8000-000000000001', 'DEMO-001',   199.00,  90.00),
  ('b0000000-0000-4000-8000-000000000002', 'DEMO-002-A', 349.50, 150.00),
  ('b0000000-0000-4000-8000-000000000002', 'DEMO-002-B', 299.00, 150.00),
  ('b0000000-0000-4000-8000-000000000003', 'DEMO-003',    99.90,  40.00)
on conflict (sku) do nothing;

commit;

-- ============================================================
-- (Opcional) Promoción de prueba: 20% a toda la categoría Demo,
-- vigente 30 días. Descomentar para probar precios con oferta.
-- ⚠ Requiere al menos un perfil (usuario registrado en Auth):
--   la FK promotions.created_by referencia profiles(id).
-- ============================================================
-- begin;
-- insert into promotions (id, nombre, tipo, valor, fecha_inicio, fecha_fin, created_by)
-- values ('c0000000-0000-4000-8000-000000000001', 'Promo demo 20%', 'porcentaje', 20,
--         now() - interval '1 hour', now() + interval '30 days',
--         (select id from profiles limit 1))
-- on conflict (id) do nothing;
--
-- insert into promotion_targets (promotion_id, category_id)
-- select 'c0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001'
-- where not exists (
--   select 1 from promotion_targets
--   where promotion_id = 'c0000000-0000-4000-8000-000000000001'
--     and category_id  = 'a0000000-0000-4000-8000-000000000001'
-- );
-- commit;

-- ============================================================
-- LIMPIEZA (descomentar y correr cuando ya no se necesiten):
-- ============================================================
-- begin;
-- delete from promotion_targets where promotion_id = 'c0000000-0000-4000-8000-000000000001';
-- delete from promotions where id = 'c0000000-0000-4000-8000-000000000001';
-- delete from product_variants where sku like 'DEMO-%';
-- delete from products  where id::text like 'b0000000-%';
-- delete from categories where id = 'a0000000-0000-4000-8000-000000000001';
-- commit;
