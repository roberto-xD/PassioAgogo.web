-- ============================================================
-- 16 · Galería: call to action (enlace + rótulo)
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después del script de galería (13 en este repo) · Idempotente
--
-- El número de archivo sigue la numeración del proyecto en Supabase,
-- donde 12 y 13 ya están ocupados por otros scripts.
--
-- enlace: URL absoluta (https://…) o ruta interna del sitio (/tienda),
--   para enlazar tanto a destinos externos como a la propia web.
--   NULL = el elemento no muestra botón.
-- enlace_texto: rótulo del botón ("Ver más", "Comprar ahora"…).
--   NULL con enlace presente = la web decide el texto por defecto.
--
-- Nota para la web: las rutas internas se resuelven contra el hash
-- routing de la app (`/catalogo` → `#/catalogo`), así que deben
-- coincidir con las rutas de `ui/navigation/Screen`.
-- ============================================================

begin;

alter table gallery_items
  add column if not exists enlace text
  check (enlace is null or enlace ~ '^(https?://|/)');

alter table gallery_items
  add column if not exists enlace_texto text
  check (enlace_texto is null or length(trim(enlace_texto)) between 1 and 40);

-- Un rótulo sin destino no tiene sentido: se permite enlace sin texto,
-- pero no texto sin enlace.
alter table gallery_items
  drop constraint if exists gallery_items_cta_coherente;
alter table gallery_items
  add constraint gallery_items_cta_coherente
  check (enlace_texto is null or enlace is not null);

commit;
