-- ============================================================
-- 23 · Eventos: galería de imágenes y rótulo del enlace
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después de 21_events_widget · Idempotente
--
-- La ficha de evento muestra un carrusel, así que un evento necesita
-- varias imágenes y no una sola. `imagenes` sustituye a `imagen`: la
-- primera del arreglo hace de portada en la tarjeta del listado, igual
-- que en los productos.
--
-- enlace_texto: rótulo del botón, como en gallery_items (script 16).
-- NULL con enlace presente = la web pone un texto por defecto.
-- ============================================================

begin;

alter table events
  add column if not exists imagenes text[] not null default '{}';

alter table events
  add column if not exists enlace_texto text
  check (enlace_texto is null or length(trim(enlace_texto)) between 1 and 40);

-- Un rótulo sin destino no lleva a ninguna parte.
alter table events drop constraint if exists events_enlace_texto_sin_enlace;
alter table events add constraint events_enlace_texto_sin_enlace
  check (enlace_texto is null or enlace is not null);

-- ------------------------------------------------------------
-- Migración de la columna antigua
--
-- Se conserva `imagen` en lugar de borrarla: si algo saliera mal en la
-- web, el dato original sigue ahí. Cuando compruebes que el listado y
-- la ficha se ven bien, puedes eliminarla con el comando del final.
-- ------------------------------------------------------------
update events
   set imagenes = array[imagen]
 where imagen is not null
   and length(trim(imagen)) > 0
   and cardinality(imagenes) = 0;

commit;

-- ============================================================
-- Cómo cargar varias imágenes
-- ============================================================
-- update events
--    set imagenes = array['eventos/ahcon-1.jpg', 'eventos/ahcon-2.jpg']
--  where titulo = 'Ah-Con!';
--
-- Rótulo propio del botón:
-- update events
--    set enlace = 'https://…', enlace_texto = 'Comprar boletos'
--  where titulo = 'Ah-Con!';

-- ============================================================
-- Revisión
-- ============================================================
select titulo,
       cardinality(imagenes) as num_imagenes,
       coalesce(enlace_texto, case when enlace is null then '— sin enlace —'
                                   else '— texto por defecto —' end) as boton,
       fecha_inicio,
       vigente_hasta < now() as ya_paso,
       activo
from events
order by vigente_hasta desc;

-- ============================================================
-- LIMPIEZA · cuando hayas comprobado que todo se ve bien
-- ============================================================
-- alter table events drop column imagen;
