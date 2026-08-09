-- ============================================================
-- 13 · Galería: eventos, promociones y productos destacados
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después de 09/10 (usa is_admin() / is_staff()) · Idempotente
--
-- Alimenta el carrusel de la portada. Las imágenes se suben al
-- bucket público de Storage y aquí se guarda su ruta relativa
-- (o una URL absoluta si viven en otro sitio).
-- ============================================================

begin;

create table if not exists gallery_items (
  id          uuid primary key default gen_random_uuid(),
  titulo      text not null check (length(trim(titulo)) between 1 and 120),
  -- Resumen corto: se muestra sobre la imagen al pasar el cursor.
  descripcion text check (descripcion is null or length(descripcion) <= 300),
  -- Texto largo: se muestra en el diálogo al abrir la imagen.
  detalles    text,
  -- Ruta relativa al bucket público (p. ej. 'galeria/expo-2026.jpg') o URL absoluta.
  imagen      text not null,
  -- Etiqueta libre para agrupar: evento, promocion, producto…
  categoria   text,
  -- Menor primero; empates se resuelven por fecha de alta.
  orden       integer not null default 0,
  activo      boolean not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

-- Para instalaciones donde la tabla ya existía sin esta columna: re-ejecutar este
-- script la añade sin tocar los datos.
alter table gallery_items add column if not exists detalles text;

create index if not exists idx_gallery_orden
  on gallery_items(orden, created_at desc)
  where activo;

alter table gallery_items enable row level security;

-- Lectura pública (sin sesión) de los elementos activos: es contenido de portada.
drop policy if exists gallery_select_anon on gallery_items;
create policy gallery_select_anon on gallery_items for select to anon
  using (activo);

-- El staff ve también los inactivos, para poder prepararlos antes de publicarlos.
drop policy if exists gallery_select on gallery_items;
create policy gallery_select on gallery_items for select to authenticated
  using (is_staff() or activo);

-- Solo la administración gestiona la galería.
drop policy if exists gallery_write on gallery_items;
create policy gallery_write on gallery_items for all to authenticated
  using (is_admin())
  with check (is_admin());

drop trigger if exists trg_gallery_updated_at on gallery_items;
create trigger trg_gallery_updated_at
  before update on gallery_items
  for each row execute function fn_set_updated_at();

commit;

-- ============================================================
-- Datos de ejemplo (opcional, para probar el carrusel).
-- Reemplaza las rutas por las de tus imágenes en el bucket.
-- ============================================================
-- begin;
-- insert into gallery_items (titulo, descripcion, detalles, imagen, categoria, orden) values
--   ('Expo 2026', 'Nuestro stand en la expo de este año.',
--    'Estuvimos tres días presentando las novedades de la temporada. Agradecemos a todas
--     las personas que se acercaron a saludarnos y a probar los productos.',
--    'galeria/expo-2026.jpg', 'evento', 1),
--   ('2x1 en toda la tienda', 'Promoción vigente durante todo el mes.',
--    'Llévate dos artículos y paga uno en toda la tienda. Aplica sobre el de menor
--     precio, no es acumulable con otras promociones y es válida hasta agotar existencias.',
--    'galeria/promo-2x1.jpg', 'promocion', 2),
--   ('Novedades del mes', 'Los productos que acaban de llegar.',
--    'Selección de las incorporaciones más recientes al catálogo. Pregunta en tienda por
--     disponibilidad de tallas y colores.',
--    'galeria/novedades.jpg', 'producto', 3);
-- commit;
