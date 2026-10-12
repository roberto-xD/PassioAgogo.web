-- ============================================================
-- 27 · Variables de las bases de la ruleta
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después de 25_wheel_terms · Idempotente
--
-- Los huecos del borrador ([FECHA DE INICIO], [PRODUCTOS EXCLUIDOS])
-- obligaban a reeditar el texto completo para cambiar un dato. Peor
-- aún: un mismo dato repetido en dos cláusulas podía quedar
-- descuadrado al actualizar solo una.
--
-- Ahora el cuerpo lleva marcadores {{clave}} y los valores viven en
-- `wheel_term_vars`. La web no lee `wheel_terms` directamente, sino la
-- vista `v_wheel_terms`, que ya devuelve el texto sustituido.
--
-- Cambiar la vigencia pasa de "reescribir un párrafo" a "editar un
-- campo de fecha".
-- ============================================================

begin;

-- ------------------------------------------------------------
-- 1. Variables
-- ------------------------------------------------------------
create table if not exists wheel_term_vars (
  clave       text primary key check (clave ~ '^[a-z0-9_]{2,40}$'),
  -- Nombre legible para la pantalla de administración.
  etiqueta    text not null check (length(trim(etiqueta)) between 1 and 60),
  valor       text not null default '',
  -- Pista para quien lo edita: qué se espera en este campo.
  descripcion text,
  orden       integer not null default 0,
  updated_at  timestamptz not null default now()
);

alter table wheel_term_vars enable row level security;

-- La vista se evalúa con los permisos de quien consulta, así que el
-- público necesita poder leer las variables para ver el texto armado.
-- No hay nada sensible: son fechas y nombres que se publican igual.
grant select on wheel_term_vars to anon, authenticated;

drop policy if exists wheel_term_vars_select_anon on wheel_term_vars;
create policy wheel_term_vars_select_anon on wheel_term_vars for select to anon
  using (true);

drop policy if exists wheel_term_vars_select_auth on wheel_term_vars;
create policy wheel_term_vars_select_auth on wheel_term_vars for select to authenticated
  using (true);

drop policy if exists wheel_term_vars_write on wheel_term_vars;
create policy wheel_term_vars_write on wheel_term_vars for all to authenticated
  using (is_admin()) with check (is_admin());

drop trigger if exists trg_wheel_term_vars_updated_at on wheel_term_vars;
create trigger trg_wheel_term_vars_updated_at
  before update on wheel_term_vars
  for each row execute function fn_set_updated_at();

-- ------------------------------------------------------------
-- 2. Sustitución
--
-- STABLE y no IMMUTABLE: lee de una tabla, así que su resultado
-- cambia entre transacciones. Declararla IMMUTABLE permitiría al
-- planificador cachear valores viejos.
-- ------------------------------------------------------------
create or replace function fn_wheel_render(p_texto text)
returns text
language plpgsql
stable
set search_path = public
as $$
declare
  v record;
  r text := p_texto;
begin
  if r is null then return null; end if;
  for v in select clave, valor from wheel_term_vars loop
    r := replace(r, '{{' || v.clave || '}}', coalesce(nullif(trim(v.valor), ''), '—'));
  end loop;
  return r;
end;
$$;

-- ------------------------------------------------------------
-- 3. Vista pública: es lo que debe leer la web
-- ------------------------------------------------------------
create or replace view v_wheel_terms
with (security_invoker = true) as
select
  t.id,
  t.titulo,
  fn_wheel_render(t.cuerpo) as cuerpo,
  t.orden,
  t.activo
from wheel_terms t
order by t.orden;

grant select on v_wheel_terms to anon, authenticated;

commit;

-- ============================================================
-- 4. Variables iniciales
--
-- `valor` vacío = pendiente de rellenar. La pantalla de la app lo
-- señala y la vista lo muestra como «—» en lugar de dejar el
-- marcador crudo a la vista del público.
-- ============================================================
begin;

insert into wheel_term_vars (clave, etiqueta, valor, descripcion, orden) values
  ('organizador_nombre', 'Nombre del organizador',
   'Roberto Gutierrez C',
   'Persona o razón social que organiza la promoción.', 1),
  ('organizador_domicilio', 'Domicilio del organizador',
   'Velo de novia 16, Los Angeles Iztapalapa, Ciudad de Mexico',
   'Domicilio fiscal o comercial que se publica en las bases.', 2),
  ('organizador_tienda', 'Nombre comercial',
   'Passion a gogo',
   'Como se conoce la tienda de cara al público.', 3),
  ('contacto_email', 'Correo de contacto',
   'contacto@passionagogo.com',
   'Correo para dudas sobre la promoción.', 4),
  ('fecha_inicio', 'Inicio de la vigencia',
   '',
   'Fecha en que arranca la promoción. Ej: 1 de octubre de 2026', 5),
  ('fecha_fin', 'Fin de la vigencia',
   '',
   'Fecha en que termina. Ej: 31 de octubre de 2026', 6),
  ('edad_minima', 'Edad mínima',
   '18',
   'Solo el número. Se usa en la cláusula de participación.', 7),
  ('pais', 'País de residencia',
   'Mexico',
   'Dónde deben residir quienes participan.', 8),
  ('limite_horas', 'Horas entre giros',
   '24',
   'Cada cuántas horas puede volver a girar una misma persona.', 9),
  ('vigencia_codigo_dias', 'Vigencia del código (días)',
   '30',
   'Días naturales que dura un código antes de vencer.', 10),
  ('productos_excluidos', 'Productos excluidos',
   '',
   'Qué queda fuera del descuento. Escribe "ninguno" si no hay exclusiones.', 11)
on conflict (clave) do nothing;

commit;

-- ============================================================
-- 5. Migrar el texto existente a marcadores
--
-- Sustituye los huecos entre corchetes del borrador por {{claves}}.
-- Solo toca las cláusulas que todavía los tengan, así que es seguro
-- re-ejecutarlo aunque ya hayas editado el texto a mano.
-- ============================================================
begin;

update wheel_terms set cuerpo = replace(cuerpo,
  '[FECHA DE INICIO]', '{{fecha_inicio}}') where cuerpo like '%[FECHA DE INICIO]%';

update wheel_terms set cuerpo = replace(cuerpo,
  '[FECHA DE FIN]', '{{fecha_fin}}') where cuerpo like '%[FECHA DE FIN]%';

update wheel_terms set cuerpo = replace(cuerpo,
  '[PRODUCTOS EXCLUIDOS, O "ninguno"]', '{{productos_excluidos}}')
  where cuerpo like '%[PRODUCTOS EXCLUIDOS%';

-- Datos del organizador que estaban escritos a pelo en la cláusula 1
update wheel_terms
   set cuerpo = 'La promocion "Gira y gana" es organizada por {{organizador_nombre}}, con ' ||
                'domicilio en {{organizador_domicilio}}, titular de la tienda ' ||
                '{{organizador_tienda}}. Para cualquier duda sobre esta promocion puedes ' ||
                'escribir a {{contacto_email}}.'
 where titulo = 'Organizador'
   and cuerpo not like '%{{%';

update wheel_terms
   set cuerpo = 'Pueden participar personas mayores de {{edad_minima}} anos con residencia ' ||
                'en {{pais}}. La tienda comercializa productos para publico adulto, por lo ' ||
                'que al participar declaras, bajo tu responsabilidad, que cumples ese ' ||
                'requisito de edad. No pueden participar el personal de la tienda ni sus ' ||
                'familiares directos.'
 where titulo = 'Quien puede participar'
   and cuerpo not like '%{{%';

update wheel_terms
   set cuerpo = 'Se permite un giro por persona cada {{limite_horas}} horas. Para aplicar ' ||
                'ese limite guardamos un resumen criptografico de tu direccion IP, no la ' ||
                'direccion en si. Si vuelves a entrar dentro de ese periodo, el sitio te ' ||
                'mostrara de nuevo el premio que ya obtuviste, que sigue siendo valido.'
 where titulo = 'Un giro por persona'
   and cuerpo not like '%{{%';

update wheel_terms
   set cuerpo = 'Para canjear tu premio escribenos por WhatsApp con tu codigo antes de que ' ||
                'venza. El codigo tiene una vigencia de {{vigencia_codigo_dias}} dias ' ||
                'naturales contados desde el giro, es de un solo uso, personal e ' ||
                'intransferible, y aplica a una sola compra. Verificaremos el codigo contra ' ||
                'nuestros registros antes de aplicar el descuento.'
 where titulo = 'Como se canjea'
   and cuerpo not like '%{{%';

commit;

-- ============================================================
-- Revisión
-- ============================================================
-- Variables sin rellenar (bloquean la publicación):
-- select clave, etiqueta, descripcion from wheel_term_vars
--  where trim(valor) = '' order by orden;
--
-- Marcadores usados en el texto que NO tienen variable definida:
-- select t.titulo, m[1] as marcador_huerfano
--   from wheel_terms t,
--        lateral regexp_matches(t.cuerpo, '\{\{([a-z0-9_]+)\}\}', 'g') m
--  where not exists (select 1 from wheel_term_vars v where v.clave = m[1]);
--
-- Cómo queda el texto ya armado:
-- select titulo, cuerpo from v_wheel_terms where activo order by orden;
