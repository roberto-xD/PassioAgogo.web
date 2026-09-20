-- ============================================================
-- 26 · Secciones propias de categoría
-- Passion à gogo · Supabase (PostgreSQL)
-- Idempotente
--
-- Una categoría raíz marcada con `seccion` sale del catálogo principal
-- —ni sus chips ni sus productos aparecen ahí, tampoco al buscar— y pasa
-- a tener su propia pantalla, donde se ve ella sola con sus
-- subcategorías.
--
-- Es una marca en la propia tabla y no una lista en el código para poder
-- separar mañana otro rubro sin recompilar la web: basta un UPDATE.
--
-- El valor de `seccion` es el que viaja en la URL (#/seccion/lenceria) y
-- el nombre de la categoría es el que se lee en la barra de navegación.
-- ============================================================

begin;

alter table categories
  add column if not exists seccion text;

comment on column categories.seccion is
  'Slug de su pantalla propia (#/seccion/<slug>). Con valor, la categoria '
  'sale del catalogo principal. Solo tiene sentido en categorias raiz.';

-- Formato del slug: lo que se puede escribir en una URL sin escapar nada.
alter table categories drop constraint if exists chk_categories_seccion_formato;
alter table categories add constraint chk_categories_seccion_formato
  check (seccion is null or seccion ~ '^[a-z0-9]+(-[a-z0-9]+)*$');

-- Una subcategoría con seccion propia se quedaría huérfana: su madre la
-- seguiría listando y a la vez tendría pantalla aparte. Solo raíces.
alter table categories drop constraint if exists chk_categories_seccion_raiz;
alter table categories add constraint chk_categories_seccion_raiz
  check (seccion is null or parent_id is null);

-- Dos categorías con el mismo slug se taparían la una a la otra en la URL.
create unique index if not exists idx_categories_seccion
  on categories (seccion)
  where seccion is not null;

commit;

-- ============================================================
-- Lencería
--
-- La categoría padre y un primer juego de subcategorías. Renombra o
-- borra las que no uses: no hay nada en el código que dependa de estos
-- nombres, la web dibuja las que encuentre.
-- ============================================================
insert into categories (nombre, descripcion, seccion)
select 'Lencería', 'Lencería y ropa interior', 'lenceria'
where not exists (
  select 1 from categories where lower(trim(nombre)) = 'lencería'
);

insert into categories (nombre, parent_id)
select nueva.nombre, madre.id
from (values
  ('Conjuntos'),
  ('Babydolls y camisones'),
  ('Bodies'),
  ('Corsés y bustiers'),
  ('Medias y ligueros'),
  ('Tangas y panties'),
  ('Disfraces'),
  ('Tallas extra')
) as nueva(nombre)
cross join (select id from categories where seccion = 'lenceria') as madre
where not exists (
  select 1 from categories c
  where c.parent_id = madre.id
    and lower(trim(c.nombre)) = lower(trim(nueva.nombre))
);

-- ============================================================
-- Cómo queda
-- ============================================================
select
  coalesce(madre.nombre, c.nombre) as categoria,
  case when c.parent_id is null then '(raíz)' else c.nombre end as subcategoria,
  coalesce(madre.seccion, c.seccion, '(catálogo principal)') as donde_se_ve
from categories c
left join categories madre on madre.id = c.parent_id
where c.seccion is not null or madre.seccion is not null
order by categoria, c.parent_id nulls first, subcategoria;

-- Para separar otro rubro mas adelante:
-- update categories set seccion = 'juguetes' where nombre = 'Juguetes';
--
-- Para devolver una categoria al catalogo principal:
-- update categories set seccion = null where nombre = 'Lencería';
