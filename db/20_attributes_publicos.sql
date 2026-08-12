-- ============================================================
-- 20 · Atributos visibles en la web pública
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después de 19_attribute_presets · Idempotente
--
-- La ficha de producto muestra los `attributes` del producto como
-- chips informativas ("🧴 Silicona", "🔋 Recargable"). Para eso hacen
-- falta dos cosas que el script 19 no contempla, porque se pensó como
-- herramienta de captura del staff:
--
--   1. El rol `anon` debe poder leer los presets activos. Sin esto la
--      web recibe cero filas y las chips se quedarían sin rótulo ni
--      emoji: el jsonb solo guarda {"material": "silicona"}, mientras
--      que "Silicona" y "🧴" viven únicamente en `attribute_presets`.
--
--   2. `products.attributes` debe existir. Si no existe, la consulta
--      del catálogo falla entera con un 400 de PostgREST y la web se
--      queda sin productos, no solo sin chips.
--
-- Lo que se expone es el vocabulario del catálogo (materiales,
-- colores, tallas). Es la misma información que ya se ve en cada
-- ficha, así que no añade superficie sensible. Los presets inactivos
-- siguen ocultos.
-- ============================================================

begin;

-- ------------------------------------------------------------
-- 1. Columna de atributos en productos
-- ------------------------------------------------------------
-- `if not exists`: si el script 01 ya la creó, esto no la toca.
alter table products
  add column if not exists attributes jsonb not null default '{}'::jsonb;

-- ------------------------------------------------------------
-- 2. Lectura pública de los presets activos
-- ------------------------------------------------------------
-- Supabase concede select por defecto a los roles del API, pero se
-- deja explícito para no depender de ese default.
grant select on attribute_presets to anon, authenticated;

drop policy if exists attribute_presets_select_anon on attribute_presets;
create policy attribute_presets_select_anon on attribute_presets for select to anon
  using (activo);

-- Las políticas permisivas se suman, así que esta convive con la del
-- script 19 (solo staff). Se añade para que una persona con sesión
-- iniciada vea exactamente el mismo catálogo que un visitante: sin
-- ella, iniciar sesión sin ser staff dejaría las chips sin rótulo.
drop policy if exists attribute_presets_select_publico on attribute_presets;
create policy attribute_presets_select_publico on attribute_presets for select to authenticated
  using (activo or is_staff());

commit;

-- ============================================================
-- Revisión: qué atributos tienen tus productos y si cada par
-- clave/valor tiene una chip con rótulo y emoji.
--
-- Lo que salga como 'sin preset' se seguirá mostrando en la ficha,
-- pero con el texto crudo del jsonb y sin icono.
-- ============================================================
select
  a.clave,
  a.valor,
  count(*) as productos,
  coalesce(p.identificador, '— sin preset —') as chip,
  coalesce(p.emoji, '') as emoji
from products
cross join lateral (
  select key as clave, jsonb_array_elements_text(
           case when jsonb_typeof(value) = 'array' then value else jsonb_build_array(value) end
         ) as valor
  from jsonb_each(products.attributes)
) a
left join attribute_presets p
       on lower(trim(p.clave)) = lower(trim(a.clave))
      and lower(trim(p.valor)) = lower(trim(a.valor))
      and p.activo
group by a.clave, a.valor, p.identificador, p.emoji
order by (p.identificador is null) desc, a.clave, a.valor;
