-- 44_vigencia_13nov.sql
--
-- Pone fecha de caducidad a los códigos **ya emitidos**: el 13 de noviembre de 2026,
-- que es la que se anunció en los términos y condiciones.
--
-- Va con fecha fija y no con los `vigencia_dias` del premio porque son dos cosas
-- distintas. `vigencia_dias` cuenta desde el giro y sirve de aquí en adelante: cada
-- código nuevo nace con su propio plazo. Lo que ya se emitió se rige por lo que se
-- publicó, y lo publicado es un día del calendario, igual para todos.
--
-- La hora es **el final de ese día en hora de Ciudad de México** (UTC-6, sin horario de
-- verano desde 2022). Sin la zona, Postgres la interpretaría en la del servidor —UTC—
-- y los códigos morirían a las seis de la tarde del día 13, seis horas antes de lo
-- prometido. Es la clase de detalle que nadie nota hasta que alguien reclama.
--
-- Idempotente: solo toca los que no tienen fecha, así que correrlo dos veces no mueve
-- los que ya la tengan.

begin;

update wheel_spins
   set expira_at = timestamptz '2026-11-13 23:59:59.999999-06:00'
 where expira_at is null;

commit;

-- Comprobar qué quedó:
--
-- select count(*) filter (where expira_at is null)                  as sin_fecha,
--        count(*) filter (where expira_at is not null and not canjeado) as vivos,
--        count(*) filter (where canjeado)                           as canjeados,
--        min(expira_at) as caduca
--   from wheel_spins;
--
-- Y en hora local, para leerlo sin hacer cuentas:
--
-- select codigo, rotulo, canjeado,
--        expira_at at time zone 'America/Mexico_City' as caduca_cdmx
--   from wheel_spins
--  where not canjeado
--  order by created_at desc limit 20;

-- ---------------------------------------------------------------------------
-- PENDIENTE: la vigencia de los códigos NUEVOS
-- ---------------------------------------------------------------------------
--
-- Este script no la configura. Mientras `wheel_prizes.vigencia_dias` siga en nulo,
-- cada código que se gire a partir de ahora nacerá **sin caducidad**, y dentro de un
-- año seguirá valiendo. Para ponérsela:
--
-- update wheel_prizes set vigencia_dias = 30 where descuento_pct is not null;
-- update wheel_prizes set vigencia_dias = 7  where rotulo = '50%';

-- ---------------------------------------------------------------------------
-- PENDIENTE: el premio «FREE!»
-- ---------------------------------------------------------------------------
--
-- Se quedó con `descuento_pct` en nulo porque su rótulo no es un porcentaje y la
-- migración solo rellenó los que lo eran. **No se adivinó a propósito**: un premio que
-- se llama «FREE!» puede ser el 100 % de descuento o puede ser un artículo de regalo
-- que se entrega a mano, y entre las dos hay dinero de por medio.
--
-- Tal como está, quien lo gane verá «Ese premio no es un descuento. Escríbenos por
-- WhatsApp», que es correcto si se entrega a mano. Si es 100 % de descuento:
--
-- update wheel_prizes set descuento_pct = 100 where rotulo = 'FREE!';
--
-- Ojo si lo haces: con el 100 % el total se queda en el envío, y si el pedido es para
-- recoger en persona se queda en cero. `crear-pago` rechaza por debajo de `MP_MINIMO`
-- y manda a resolverlo por WhatsApp, que para un premio de peso 1 es razonable.
