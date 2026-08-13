-- ============================================================
-- 21 · Eventos y el widget flotante que los anuncia
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después de 09/10 (usa is_staff) · Idempotente
--
-- Dos piezas:
--
--   site_settings · interruptores del sitio, en pares clave-valor.
--     Nace con uno solo, `widget_eventos_visible`, para encender y
--     apagar el widget sin tener que desactivar los eventos.
--
--   events · los eventos en sí. Tabla propia y no una etiqueta de
--     `gallery_items` porque un evento tiene fecha, y sin ella no se
--     puede ordenar por proximidad ni dejar de anunciar lo ya pasado.
-- ============================================================

begin;

-- ------------------------------------------------------------
-- 1. Interruptores del sitio
-- ------------------------------------------------------------
create table if not exists site_settings (
  clave       text primary key check (length(trim(clave)) between 1 and 60),
  valor       text not null,
  descripcion text,
  updated_at  timestamptz not null default now()
);

alter table site_settings enable row level security;

-- Son ajustes de presentación, no datos de nadie: la web los lee sin sesión.
grant select on site_settings to anon, authenticated;

drop policy if exists site_settings_select on site_settings;
create policy site_settings_select on site_settings for select to anon
  using (true);

drop policy if exists site_settings_select_auth on site_settings;
create policy site_settings_select_auth on site_settings for select to authenticated
  using (true);

drop policy if exists site_settings_write on site_settings;
create policy site_settings_write on site_settings for all to authenticated
  using (is_admin()) with check (is_admin());

drop trigger if exists trg_site_settings_updated_at on site_settings;
create trigger trg_site_settings_updated_at
  before update on site_settings
  for each row execute function fn_set_updated_at();

-- `do nothing`: re-ejecutar el script no vuelve a encender un widget
-- que hayas apagado a mano.
insert into site_settings (clave, valor, descripcion) values
  ('widget_eventos_visible', 'true',
   'Muestra u oculta el widget flotante de proximos eventos. true/false.')
on conflict (clave) do nothing;

-- ------------------------------------------------------------
-- 2. Eventos
-- ------------------------------------------------------------
create table if not exists events (
  id            uuid primary key default gen_random_uuid(),
  titulo        text not null check (length(trim(titulo)) between 1 and 120),
  -- Línea corta: es lo que cabe en el widget flotante.
  resumen       text check (resumen is null or length(trim(resumen)) between 1 and 160),
  -- Texto largo para la pantalla de Eventos.
  detalles      text,
  lugar         text check (lugar is null or length(trim(lugar)) <= 120),
  -- Ruta relativa al bucket público o URL absoluta, como en gallery_items.
  imagen        text,
  fecha_inicio  timestamptz not null,
  -- NULL = evento de un solo momento, sin duración.
  fecha_fin     timestamptz check (fecha_fin is null or fecha_fin >= fecha_inicio),
  -- Destino externo opcional (venta de boletos, formulario de registro…).
  enlace        text check (enlace is null or enlace ~ '^(https?://|/)'),
  orden         integer not null default 0,
  activo        boolean not null default true,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),

  -- Hasta cuándo sigue siendo "próximo". Columna generada para que la web
  -- pueda filtrar con una sola comparación: PostgREST no sabe expresar un
  -- coalesce entre dos columnas dentro de un filtro.
  vigente_hasta timestamptz generated always as (coalesce(fecha_fin, fecha_inicio)) stored
);

create index if not exists idx_events_proximos
  on events (vigente_hasta, fecha_inicio, orden)
  where activo;

alter table events enable row level security;

grant select on events to anon, authenticated;

-- El público ve los activos; el staff, todos (para preparar los que aún no
-- se anuncian). Igual que en products: las políticas permisivas se suman.
drop policy if exists events_select_anon on events;
create policy events_select_anon on events for select to anon
  using (activo);

drop policy if exists events_select_auth on events;
create policy events_select_auth on events for select to authenticated
  using (activo or is_staff());

drop policy if exists events_write on events;
create policy events_write on events for all to authenticated
  using (is_staff()) with check (is_staff());

drop trigger if exists trg_events_updated_at on events;
create trigger trg_events_updated_at
  before update on events
  for each row execute function fn_set_updated_at();

commit;

-- ============================================================
-- Apagar o encender el widget
-- ============================================================
-- update site_settings set valor = 'false' where clave = 'widget_eventos_visible';
-- update site_settings set valor = 'true'  where clave = 'widget_eventos_visible';

-- ============================================================
-- Semilla de ejemplo (borra o adapta)
-- ============================================================
-- insert into events (titulo, resumen, detalles, lugar, fecha_inicio, fecha_fin, orden) values
--   ('Noche de talleres',
--    'Taller de introduccion, cupo limitado.',
--    'Sesion guiada de hora y media. Incluye material. Reserva por WhatsApp.',
--    'Sucursal Iztapalapa',
--    now() + interval '10 days',
--    now() + interval '10 days' + interval '2 hours',
--    1),
--   ('Expo Passion a gogo',
--    'Tres dias de novedades y descuentos.',
--    'Presentacion de las marcas de la temporada.',
--    'Centro de convenciones',
--    now() + interval '30 days',
--    now() + interval '33 days',
--    2);

-- ============================================================
-- Revisión: qué anunciaría el widget ahora mismo
-- ============================================================
select
  (select valor from site_settings where clave = 'widget_eventos_visible') as widget_encendido,
  e.titulo,
  e.fecha_inicio,
  e.vigente_hasta,
  e.activo
from events e
where e.activo and e.vigente_hasta >= now()
order by e.fecha_inicio, e.orden;
