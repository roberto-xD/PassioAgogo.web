-- 34_locations_direccion.sql
--
-- Desglosa la dirección de `locations`, que hoy es un texto libre («velo de novia»), en
-- los campos que pide Envia como origen del envío.
--
-- Una guía no se arma con una cadena: la paquetería quiere calle, número, colonia,
-- ciudad, estado y CP por separado, y un teléfono de contacto para la recolección. Lo
-- que hoy cabe en `direccion` no alcanza ni para cotizar.
--
-- `direccion` **se conserva**. Es lo que la app de control enseña hoy y lo que alguien
-- escribió a mano; borrarla para poner campos vacíos sería cambiar un dato por nada.
--
-- Un apunte sobre los nombres: Envia llama `district` a la colonia y `postalCode` al CP,
-- y espera el estado como **código de dos letras**, no como nombre. Aquí se guardan los
-- dos: `estado` para quien lee la tabla y `estado_codigo` para la petición. El código de
-- cada estado sale de `GET https://queries.envia.com/state?country_code=MX`.
--
-- Idempotente: se puede correr dos veces.

begin;

alter table locations
  add column if not exists calle         text,
  add column if not exists numero        text,
  add column if not exists interior      text,
  add column if not exists colonia       text,
  add column if not exists ciudad        text,
  add column if not exists estado        text,
  add column if not exists estado_codigo text,
  add column if not exists cp            text,
  add column if not exists pais          text not null default 'MX',
  -- Envia exige teléfono en el origen, y para la recolección es por donde llama el
  -- repartidor. Sin esto la guía se rechaza.
  add column if not exists telefono      text,
  add column if not exists contacto      text,
  add column if not exists referencias   text,
  -- Cuál de las ubicaciones es el origen de los envíos. Es una pregunta distinta de
  -- `activo` y de `tipo`: puede haber dos bodegas activas y solo de una salen los
  -- paquetes.
  add column if not exists es_origen     boolean not null default false;

comment on column locations.colonia       is 'Lo que Envia llama district; solo se usa en México.';
comment on column locations.estado        is 'Nombre legible del estado, para quien lee la tabla.';
comment on column locations.estado_codigo is 'Código de 2 letras que viaja a Envia (CDMX = CX).';
comment on column locations.cp            is 'Lo que Envia llama postalCode.';
comment on column locations.es_origen     is 'La ubicación desde la que salen los envíos. Solo una.';

-- Formatos que la paquetería rechazaría de todas formas, pero aquí se ven al capturar y
-- no al generar la guía.
do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'locations_cp_check'
  ) then
    alter table locations add constraint locations_cp_check
      check (cp is null or cp ~ '^[0-9]{5}$');
  end if;

  if not exists (
    select 1 from pg_constraint where conname = 'locations_estado_codigo_check'
  ) then
    alter table locations add constraint locations_estado_codigo_check
      check (estado_codigo is null or estado_codigo ~ '^[A-Z]{2}$');
  end if;
end $$;

-- Un solo origen. Índice parcial: restringe los `true` sin estorbar a los `false`.
create unique index if not exists locations_un_solo_origen
  on locations (es_origen) where es_origen;

-- La ubicación activa que ya existe pasa a ser el origen, con lo que sí sabemos de
-- cierto: el CP es siempre 04700, y de ahí salen ciudad y estado —confirmados contra
-- `GET https://geocodes.envia.com/zipcode/MX/04700`, que devuelve Coyoacán, Ciudad de
-- México, código CX—. La calle se arrastra del texto libre como punto de partida.
--
-- Número, colonia, teléfono y contacto se quedan vacíos **a propósito**: inventarlos
-- sería peor que dejarlos en blanco, porque una dirección a medias que parece completa
-- se descubre el día que no llega el repartidor.
update locations
   set es_origen     = true,
       calle         = coalesce(nullif(trim(calle), ''), nullif(trim(direccion), '')),
       cp            = coalesce(nullif(trim(cp), ''), '04700'),
       ciudad        = coalesce(nullif(trim(ciudad), ''), 'Ciudad de México'),
       estado        = coalesce(nullif(trim(estado), ''), 'Ciudad de México'),
       estado_codigo = coalesce(nullif(trim(estado_codigo), ''), 'CX'),
       pais          = coalesce(nullif(trim(pais), ''), 'MX')
 where id = (
   select id from locations
    where activo
    order by (tipo = 'bodega') desc, created_at
    limit 1
 )
 and not exists (select 1 from locations where es_origen);

commit;

-- ============================================================
-- Lo que falta capturar
-- ============================================================
--
-- 1. Qué le falta al origen para poder generar una guía:
--
-- select nombre,
--        case when coalesce(trim(calle), '')    = '' then 'calle '    end ||
--        case when coalesce(trim(numero), '')   = '' then 'numero '   end ||
--        case when coalesce(trim(colonia), '')  = '' then 'colonia '  end ||
--        case when coalesce(trim(cp), '')       = '' then 'cp '       end ||
--        case when coalesce(trim(ciudad), '')   = '' then 'ciudad '   end ||
--        case when coalesce(trim(estado_codigo), '') = '' then 'estado_codigo ' end ||
--        case when coalesce(trim(telefono), '') = '' then 'telefono ' end ||
--        case when coalesce(trim(contacto), '') = '' then 'contacto ' end
--          as falta
--   from locations
--  where es_origen;
--
-- 2. Para completarlo. Las colonias válidas del 04700 las da la propia Envia:
--    curl https://geocodes.envia.com/zipcode/MX/04700
--    (devuelve Pedregal de Carrasco y sus secciones, entre otras)
--
-- update locations
--    set numero   = '16',
--        colonia  = 'Pedregal de Carrasco',
--        telefono = '5513878451',
--        contacto = 'Nombre de quien entrega el paquete'
--  where es_origen;
--
-- 3. Si algún día hay otra bodega y cambia el origen, primero se apaga el anterior: el
--    índice único no deja dos a la vez.
--
-- update locations set es_origen = false where es_origen;
-- update locations set es_origen = true  where id = '...';
