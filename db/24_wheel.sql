-- ============================================================
-- 24 · Ruleta de descuentos
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después de 21_events_widget (usa site_settings) · Idempotente
--
-- Dos tablas:
--
--   wheel_prizes · los sectores de la ruleta y su probabilidad.
--   wheel_spins  · cada giro con su código, para poder verificarlo
--                  cuando alguien lo reclame.
--
-- **El premio no lo decide el navegador.** Lo elige la Edge Function
-- `spin`, que escribe con service_role. `wheel_spins` no tiene política
-- de INSERT, igual que `contact_messages`: sin ese camino, cualquiera
-- con la anon key podría fabricarse un código.
--
-- Del lado público solo se leen las columnas que hacen falta para
-- dibujar la ruleta. `peso` queda fuera a propósito: publicar las
-- probabilidades del sorteo no aporta nada y se lee mal.
-- ============================================================

begin;

-- ------------------------------------------------------------
-- 1. Premios
-- ------------------------------------------------------------
create table if not exists wheel_prizes (
  id       uuid primary key default gen_random_uuid(),
  -- Lo que se pinta en el sector. Corto: tiene que caber girado.
  rotulo   text not null check (length(trim(rotulo)) between 1 and 12),
  -- Frase que acompaña al resultado.
  detalle  text check (detalle is null or length(trim(detalle)) between 1 and 200),
  -- Probabilidad relativa. El reparto es peso / suma de pesos, así que
  -- no hace falta que sumen 100 ni nada en concreto. Peso 0 = no sale
  -- nunca, pero se sigue dibujando.
  peso     integer not null default 1 check (peso >= 0),
  -- Si false, es un sector de consolación: no hay nada que reclamar y
  -- la web no ofrece el botón de WhatsApp.
  otorga_premio boolean not null default true,
  orden    integer not null default 0,
  activo   boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_wheel_prizes_orden
  on wheel_prizes (orden, created_at)
  where activo;

alter table wheel_prizes enable row level security;

-- Privilegios por columna: el público dibuja la ruleta sin ver los pesos.
revoke all on wheel_prizes from anon;
grant select (id, rotulo, detalle, orden) on wheel_prizes to anon;
grant select on wheel_prizes to authenticated;

drop policy if exists wheel_prizes_select_anon on wheel_prizes;
create policy wheel_prizes_select_anon on wheel_prizes for select to anon
  using (activo);

drop policy if exists wheel_prizes_select_auth on wheel_prizes;
create policy wheel_prizes_select_auth on wheel_prizes for select to authenticated
  using (activo or is_staff());

drop policy if exists wheel_prizes_write on wheel_prizes;
create policy wheel_prizes_write on wheel_prizes for all to authenticated
  using (is_admin()) with check (is_admin());

drop trigger if exists trg_wheel_prizes_updated_at on wheel_prizes;
create trigger trg_wheel_prizes_updated_at
  before update on wheel_prizes
  for each row execute function fn_set_updated_at();

-- ------------------------------------------------------------
-- 2. Giros
-- ------------------------------------------------------------
create table if not exists wheel_spins (
  id        uuid primary key default gen_random_uuid(),
  -- Lo que la persona enseña para reclamar. Único: es la prueba.
  codigo    text not null unique check (length(trim(codigo)) between 4 and 24),
  prize_id  uuid references wheel_prizes(id) on delete set null,
  -- Copia del rótulo: si mañana borras el premio, el código sigue
  -- diciendo qué se gana.
  rotulo    text not null,
  otorga_premio boolean not null default true,
  -- Hash con sal del IP: permite limitar giros sin guardar el IP.
  ip_hash   text not null,
  canjeado  boolean not null default false,
  canjeado_at timestamptz,
  created_at timestamptz not null default now()
);

-- Consulta del límite por origen y de la verificación por código.
create index if not exists idx_wheel_spins_origen
  on wheel_spins (ip_hash, created_at desc);

create index if not exists idx_wheel_spins_pendientes
  on wheel_spins (created_at desc)
  where not canjeado;

alter table wheel_spins enable row level security;

-- Sin acceso público: los códigos son de quien los ganó. Ni siquiera
-- lectura, o cualquiera podría listar los ajenos.
revoke all on wheel_spins from anon;
grant select, update on wheel_spins to authenticated;

drop policy if exists wheel_spins_select on wheel_spins;
create policy wheel_spins_select on wheel_spins for select to authenticated
  using (is_staff());

-- Para marcar un código como canjeado al atenderlo.
drop policy if exists wheel_spins_update on wheel_spins;
create policy wheel_spins_update on wheel_spins for update to authenticated
  using (is_staff()) with check (is_staff());

-- ------------------------------------------------------------
-- 3. Interruptor de la sección
-- ------------------------------------------------------------
insert into site_settings (clave, valor, descripcion) values
  ('ruleta_visible', 'true',
   'Muestra u oculta la seccion de la ruleta de descuentos. true/false.')
on conflict (clave) do nothing;

commit;

-- ============================================================
-- Premios iniciales
--
-- Los pesos son relativos y suman 1000, así que se leen como tantos
-- por mil: 0% sale 300 de cada 1000 giros y FREE 1 de cada 1000.
-- **Ajústalos a lo que estés dispuesto a regalar** antes de publicar.
-- ============================================================
insert into wheel_prizes (rotulo, detalle, peso, otorga_premio, orden)
select * from (values
  ('0%',    'Esta vez no hubo suerte. Vuelve a intentarlo pronto.',            300, false, 1),
  ('10%',   'Diez por ciento de descuento en tu proxima compra.',              400, true,  2),
  ('25%',   'Veinticinco por ciento de descuento en tu proxima compra.',       200, true,  3),
  ('50%',   'Mitad de precio en un articulo de tu eleccion.',                   80, true,  4),
  ('75%',   'Setenta y cinco por ciento de descuento. De los buenos.',          19, true,  5),
  ('FREE!', 'Un articulo de cortesia. Escribenos para elegirlo.',                1, true,  6)
) as nuevos(rotulo, detalle, peso, otorga_premio, orden)
where not exists (
  select 1 from wheel_prizes p where lower(trim(p.rotulo)) = lower(trim(nuevos.rotulo))
);

-- ============================================================
-- Consultas útiles
-- ============================================================
-- Qué probabilidad tiene cada premio ahora mismo:
-- select rotulo, peso,
--        round(100.0 * peso / nullif(sum(peso) over (), 0), 2) as porcentaje
--   from wheel_prizes where activo order by orden;
--
-- Verificar un código que alguien reclama por WhatsApp:
-- select codigo, rotulo, canjeado, created_at
--   from wheel_spins where codigo = 'PAG-XXXXXX';
--
-- Marcarlo como entregado:
-- update wheel_spins set canjeado = true, canjeado_at = now()
--  where codigo = 'PAG-XXXXXX';
--
-- Apagar la seccion:
-- update site_settings set valor = 'false' where clave = 'ruleta_visible';

-- ============================================================
-- Revisión
-- ============================================================
select rotulo, peso,
       round(100.0 * peso / nullif(sum(peso) over (), 0), 2) as probabilidad_pct,
       otorga_premio, activo, orden
from wheel_prizes
order by orden;
