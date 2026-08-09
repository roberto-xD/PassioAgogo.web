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
  descripcion text check (descripcion is null or length(descripcion) <= 300),
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
-- insert into gallery_items (titulo, descripcion, imagen, categoria, orden) values
--   ('Expo 2026', 'Nuestro stand en la expo de este año.', 'galeria/expo-2026.jpg', 'evento', 1),
--   ('2x1 en toda la tienda', 'Promoción vigente durante todo el mes.', 'galeria/promo-2x1.jpg', 'promocion', 2),
--   ('Novedades del mes', 'Los productos que acaban de llegar.', 'galeria/novedades.jpg', 'producto', 3);
-- commit;
