-- 46_vigencia_10dias.sql
--
-- Diez días de vigencia para los códigos que se giren a partir de ahora.
--
-- Esto **no toca los ya emitidos**: aquéllos quedaron con fecha fija al 13 de
-- noviembre de 2026 (44_vigencia_13nov.sql), que es lo que se publicó en los términos.
-- La fecha de caducidad se graba en el giro, así que cambiar esto solo afecta a lo que
-- venga después.
--
-- Se le pone a todos los premios, incluido el de regalo y el de consolación. Al de
-- consolación no le sirve de nada —no hay nada que canjear— pero tampoco estorba, y
-- dejar un premio sin plazo «porque da igual» es como se acaban colando los que sí
-- importan.
--
-- Idempotente: se puede correr dos veces.

begin;

update wheel_prizes set vigencia_dias = 10;

commit;

-- Comprobar:
--
-- select rotulo, tipo, descuento_pct, vigencia_dias, peso, activo
--   from wheel_prizes order by orden;
--
-- Y, tras el primer giro, que la fecha se esté grabando:
--
-- select codigo, rotulo, created_at,
--        expira_at at time zone 'America/Mexico_City' as caduca_cdmx
--   from wheel_spins order by created_at desc limit 5;
--
-- Si `expira_at` sale nulo en un giro NUEVO, es que la función `spin` no se ha
-- redesplegado: es ella quien lo graba.
