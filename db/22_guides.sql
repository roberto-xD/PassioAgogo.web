-- ============================================================
-- 22 · Guías de uso y cuidados
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después de 09/10 (usa is_staff) · Idempotente
--
-- Contenido editorial de la sección «Uso y cuidados»: una guía por
-- tipo de producto.
--
-- El texto se guarda en cuatro columnas y no en un solo campo libre
-- porque la sección promete exactamente eso —uso, limpieza y cuidado—
-- y así la web puede maquetar cada bloque con su rótulo sin tener que
-- interpretar el contenido. `advertencias` es opcional y se pinta
-- destacada.
--
-- category_id enlaza la guía con una categoría del catálogo. Hoy solo
-- sirve para ordenar el trabajo editorial; más adelante permite
-- mostrar «cómo se cuida» dentro de la ficha del producto.
-- ============================================================

begin;

create table if not exists guides (
  id           uuid primary key default gen_random_uuid(),
  titulo       text not null check (length(trim(titulo)) between 1 and 120),
  -- Una línea: es lo que se lee con la guía plegada.
  resumen      text check (resumen is null or length(trim(resumen)) between 1 and 200),
  emoji        text check (emoji is null or length(emoji) <= 8),
  uso          text,
  limpieza     text,
  cuidados     text,
  -- Opcional. Se muestra resaltada, para lo que no conviene pasar por alto.
  advertencias text,
  category_id  uuid references categories(id) on delete set null,
  orden        integer not null default 0,
  activo       boolean not null default true,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);

-- Una guía sin nada que contar no debería publicarse.
alter table guides drop constraint if exists guides_con_contenido;
alter table guides add constraint guides_con_contenido check (
  coalesce(trim(uso), '') <> ''
  or coalesce(trim(limpieza), '') <> ''
  or coalesce(trim(cuidados), '') <> ''
);

create index if not exists idx_guides_orden
  on guides (orden, titulo)
  where activo;

alter table guides enable row level security;

grant select on guides to anon, authenticated;

drop policy if exists guides_select_anon on guides;
create policy guides_select_anon on guides for select to anon
  using (activo);

-- El staff ve también los borradores, para prepararlos antes de publicar.
drop policy if exists guides_select_auth on guides;
create policy guides_select_auth on guides for select to authenticated
  using (activo or is_staff());

drop policy if exists guides_write on guides;
create policy guides_write on guides for all to authenticated
  using (is_staff()) with check (is_staff());

drop trigger if exists trg_guides_updated_at on guides;
create trigger trg_guides_updated_at
  before update on guides
  for each row execute function fn_set_updated_at();

commit;

-- ============================================================
-- Contenido inicial
--
-- Son recomendaciones generales de higiene y conservación, no
-- consejo médico. **Revisa y ajusta la redacción a tu catálogo y a
-- las instrucciones de cada fabricante antes de publicarlo**: cuando
-- el fabricante diga otra cosa, manda el fabricante.
--
-- `on conflict do nothing` no aplica aquí (no hay clave única por
-- título), así que el insert se salta las guías que ya existan.
-- ============================================================

insert into guides (titulo, emoji, resumen, uso, limpieza, cuidados, advertencias, orden)
select * from (values
  (
    'Juguetes de silicona', '🧴',
    'Suaves y porosos solo si son de mala calidad: la silicona médica es de las opciones más seguras.',
    'Usa siempre lubricante de base agua. Empieza despacio y con lubricante de sobra: la silicona agarra más que el vidrio o el metal.',
    'Lava antes y después de cada uso con agua tibia y jabón neutro sin perfume. Sécalo al aire o con una toalla limpia que no suelte pelusa.',
    'Guárdalo seco, en su bolsa o por separado: la silicona puede reaccionar al contacto prolongado con otros juguetes de silicona.',
    'No uses lubricante de silicona: degrada el material y lo deja pegajoso de forma permanente.',
    10
  ),
  (
    'Juguetes de vidrio y cristal', '🔷',
    'No porosos, fáciles de higienizar y compatibles con cualquier lubricante.',
    'Compatible con lubricante de agua, de silicona y con aceites. Puedes templarlo con agua tibia antes de usarlo.',
    'Agua tibia y jabón neutro. Al no ser poroso, se limpia por completo con facilidad.',
    'Guárdalo en funda acolchada y revísalo a contraluz antes de cada uso.',
    'Deséchalo ante cualquier fisura, astilla o golpe, por pequeño que parezca. No lo enfríes ni lo calientes de golpe.',
    20
  ),
  (
    'Juguetes de metal', '⚙️',
    'Acero inoxidable: peso, firmeza y una limpieza muy sencilla.',
    'Compatible con todos los lubricantes. Conduce la temperatura, así que puedes templarlo o enfriarlo con agua antes de usarlo.',
    'Agua tibia y jabón neutro; sécalo bien para evitar marcas de agua.',
    'Guárdalo seco y separado de superficies que puedan rayarlo.',
    'Comprueba que no tenga rebabas ni recubrimientos descascarillados antes de usarlo.',
    30
  ),
  (
    'Juguetes con motor o recargables', '🔋',
    'Electrónica dentro: la limpieza cambia y el agua tiene límites.',
    'Cárgalo por completo antes del primer uso. Comprueba si es sumergible o solo resistente a salpicaduras: no es lo mismo.',
    'Limpia la superficie con un paño húmedo y jabón neutro, evitando el puerto de carga. Sumérgelo solo si el fabricante lo indica.',
    'Guárdalo con algo de carga, no descargado del todo, y lejos de fuentes de calor.',
    'Nunca lo hiervas ni lo metas al lavavajillas, y no lo cargues mientras esté húmedo.',
    40
  ),
  (
    'Lubricantes', '💧',
    'El de base agua es el comodín: compatible con todo y fácil de retirar.',
    'Base agua: compatible con preservativos y con todos los materiales; se reactiva con un poco de agua. Base silicona: dura más y sirve en agua, pero no con juguetes de silicona. Con aceite: no lo uses con preservativos de látex.',
    'Retíralo con agua tibia. Los de base silicona necesitan jabón.',
    'Ciérralo bien, guárdalo lejos del sol y respeta la fecha de caducidad del envase.',
    'Suspende su uso ante picor, ardor o irritación. Si tienes la piel sensible, prueba antes en el antebrazo.',
    50
  ),
  (
    'Lencería y textiles', '👗',
    'Prendas delicadas: encaje, tul y elásticos que no perdonan la lavadora.',
    'Comprueba la talla y prueba la prenda con calma antes de la ocasión: los elásticos ceden con el uso, no antes.',
    'Lavado a mano, en agua fría y con jabón neutro. Nada de lejía ni suavizante.',
    'Sécala a la sombra y en horizontal; no la retuerzas ni la cuelgues mojada. Guárdala extendida o doblada sin apretar.',
    null,
    60
  )
) as nuevas(titulo, emoji, resumen, uso, limpieza, cuidados, advertencias, orden)
where not exists (
  select 1 from guides g where lower(trim(g.titulo)) = lower(trim(nuevas.titulo))
);

-- ============================================================
-- Enlazar una guía con una categoría del catálogo (opcional)
-- ============================================================
-- update guides set category_id = (select id from categories where nombre = 'Lubricantes')
--  where titulo = 'Lubricantes';

-- ============================================================
-- Revisión
-- ============================================================
select titulo, emoji, activo, orden,
       (advertencias is not null) as tiene_advertencias
from guides
order by orden, titulo;
