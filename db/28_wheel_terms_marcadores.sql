-- ============================================================
-- 28 · Devuelve los marcadores a las cláusulas editadas a mano
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después de 27_wheel_term_vars · Idempotente
--
-- La migración del script 27 cambiaba `[FECHA DE INICIO]` por
-- `{{fecha_inicio}}` solo donde el corchete siguiera puesto. En
-- «Vigencia» y «Restricciones» ya se había escrito el texto a mano, así
-- que se quedaron sin marcador: la vista no tiene nada que sustituir y
-- editar la variable no cambia lo que se publica.
--
-- Esto vuelve a enlazarlas. Conserva la redacción que ya tenían; lo
-- único que cambia es el dato, que pasa a venir de la variable.
--
-- ⚠ ANTES DE CORRER: decide qué fecha vale. Hoy no coinciden.
--     wheel_term_vars.fecha_fin = '18 de Noviembre 2026'
--     el texto publicado        = '1 de Diciembre de 2026'
--   Después de este script manda SIEMPRE la variable, así que si la
--   buena es el 1 de diciembre hay que corregirla (abajo queda el
--   update listo).
-- ============================================================

begin;

-- ------------------------------------------------------------
-- Vigencia
-- ------------------------------------------------------------
update wheel_terms
   set cuerpo = 'La promocion estara vigente del {{fecha_inicio}} al {{fecha_fin}}, o ' ||
                'hasta agotar los premios disponibles, lo que ocurra primero. Fuera de ' ||
                'ese periodo la ruleta no estara disponible y no se emitiran codigos ' ||
                'nuevos.'
 where titulo = 'Vigencia'
   and cuerpo not like '%{{fecha_inicio}}%';

-- ------------------------------------------------------------
-- Restricciones
--
-- Se respeta «categorias», que es como lo reescribiste: lo que se
-- excluye son categorías enteras, no productos sueltos.
-- ------------------------------------------------------------
update wheel_terms
   set cuerpo = 'El descuento no es acumulable con otras promociones ni con precios ya ' ||
                'rebajados, no es canjeable por dinero en efectivo ni por otros ' ||
                'productos, y esta sujeto a disponibilidad de inventario. Quedan ' ||
                'excluidas las siguientes categorias: {{productos_excluidos}}. Los ' ||
                'gastos de envio no forman parte del descuento salvo que se indique lo ' ||
                'contrario.'
 where titulo = 'Restricciones'
   and cuerpo not like '%{{productos_excluidos}}%';

-- El valor de la variable tiene que leerse bien en su hueco. Decía
-- 'Categoría "Varios"', que en «las siguientes categorias: ...» quedaba
-- repetido y además no mencionaba Lencería, que sí estaba en el texto.
update wheel_term_vars
   set valor = 'Lencería y Varios'
 where clave = 'productos_excluidos'
   and valor = 'Categoría "Varios"';

commit;

-- ============================================================
-- Revisión
-- ============================================================

-- 1. Cómo queda el texto ya armado. Aquí se ve si las fechas cuadran.
select orden, titulo, cuerpo from v_wheel_terms where activo order by orden;

-- 2. Variables que no usa ninguna cláusula: o sobran, o falta ponerlas
--    en el texto. Cualquiera de las dos cosas conviene saberla.
select v.clave, v.etiqueta, v.valor
  from wheel_term_vars v
 where not exists (
   select 1 from wheel_terms t where t.cuerpo like '%{{' || v.clave || '}}%'
 )
 order by v.orden;

-- 3. Marcadores citados en el texto que no tienen variable definida.
--    La web los pinta como «—», asi que no se ven, pero el dato falta.
select t.titulo, m[1] as marcador_huerfano
  from wheel_terms t,
       lateral regexp_matches(t.cuerpo, '\{\{([a-z0-9_]+)\}\}', 'g') m
 where not exists (select 1 from wheel_term_vars v where v.clave = m[1]);

-- Si la fecha buena era el 1 de diciembre:
-- update wheel_term_vars set valor = '1 de Diciembre 2026' where clave = 'fecha_fin';
