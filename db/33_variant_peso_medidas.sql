-- 33_variant_peso_medidas.sql
--
-- Peso y medidas de cada variante, que es lo que Envia pide para cotizar: sin esto no
-- hay cotización posible, ni una.
--
-- Van en `product_variants` y no en `products` porque el carrito guarda variantes: una
-- presentación de 3 piezas no pesa lo que una suelta, y es la variante la que se envía.
--
-- Las unidades van en el nombre de la columna a propósito. Envia quiere KG y CM; aquí se
-- guardan **gramos** y **centímetros** enteros, que es como se leen de una báscula y de
-- una cinta, y la conversión se hace en un solo sitio al armar la petición. Un decimal
-- mal entendido entre gramos y kilos son mil pesos de diferencia en una guía.
--
-- Todo arranca con 200 g y 10x10x10 cm, que es el grueso del catálogo. Lo que no encaje
-- —torsos y copas, sobre todo— se corrige a mano con las consultas del final.
--
-- Idempotente: se puede correr dos veces.

begin;

alter table product_variants
  add column if not exists peso_gr  integer not null default 200,
  add column if not exists largo_cm integer not null default 10,
  add column if not exists ancho_cm integer not null default 10,
  add column if not exists alto_cm  integer not null default 10;

-- Cero o negativo no es un dato incompleto, es un dato imposible: la paquetería
-- rechazaría la guía y lo haría al generarla, no al capturarla.
do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'product_variants_medidas_check'
  ) then
    alter table product_variants
      add constraint product_variants_medidas_check
      check (peso_gr > 0 and largo_cm > 0 and ancho_cm > 0 and alto_cm > 0);
  end if;
end $$;

comment on column product_variants.peso_gr  is 'Peso de la variante empacada, en gramos.';
comment on column product_variants.largo_cm is 'Lado mayor de la variante empacada, en cm.';
comment on column product_variants.ancho_cm is 'Lado medio de la variante empacada, en cm.';
comment on column product_variants.alto_cm  is 'Lado menor de la variante empacada, en cm.';

commit;

-- ============================================================
-- Quién lee estas columnas
-- ============================================================
--
-- El peso **no es un secreto**: no se esconde nada diciendo que un lubricante pesa 200 g.
-- Lo que importa es otra cosa: que el peso con el que se cotiza **no venga del
-- navegador**. La función de cotización recibe identificadores de variante y cantidades,
-- y busca los pesos ella misma con service_role. Si los mandara el cliente, bastaría con
-- editar la petición para declarar un gramo y pagar el envío más barato. Es el mismo
-- criterio que la ruleta: lo que decide un cobro no se decide en el navegador.
--
-- Por eso aquí no hay ningún `revoke`. Un revoke por columna **no haría nada** si `anon`
-- tiene el select a nivel de tabla, que es como está hoy `product_variants` —`costo` se
-- mantiene fuera del tráfico pidiendo columnas explícitas, no quitando permisos—. Dejar
-- un revoke que no revoca sería peor que no ponerlo: parecería una defensa.
--
-- Para ver qué permisos hay de verdad sobre la tabla:
--
-- select grantee, privilege_type, column_name
--   from information_schema.column_privileges
--  where table_name = 'product_variants'
--    and grantee in ('anon', 'authenticated')
--    and column_name in ('costo', 'peso_gr', 'largo_cm', 'ancho_cm', 'alto_cm')
--  order by grantee, column_name;

-- ============================================================
-- Lo que queda por capturar a mano
-- ============================================================
--
-- 1. Las variantes de «Torsos y copas», que son las que no encajan en el default.
--    Salen con su peso y medidas actuales para irlas corrigiendo:
--
-- select c.nombre as categoria, p.nombre as producto,
--        coalesce(nullif(v.titulo, ''), v.sku) as variante,
--        v.peso_gr, v.largo_cm, v.ancho_cm, v.alto_cm
--   from product_variants v
--   join products p   on p.id = v.product_id
--   join categories c on c.id = p.category_id
--  where translate(lower(c.nombre), 'áéíóú', 'aeiou') like '%torso%'
--     or translate(lower(c.nombre), 'áéíóú', 'aeiou') like '%copa%'
--  order by p.nombre, variante;
--
-- 2. Para corregir una de golpe:
--
-- update product_variants
--    set peso_gr = 2500, largo_cm = 35, ancho_cm = 25, alto_cm = 20
--  where sku = 'EL-SKU-QUE-SEA';
--
-- 3. Para ver qué sigue con el valor de arranque, que es lo mismo que decir
--    «qué falta por pesar»:
--
-- select c.nombre as categoria, count(*) as variantes_sin_pesar
--   from product_variants v
--   join products p   on p.id = v.product_id
--   join categories c on c.id = p.category_id
--  where v.peso_gr = 200 and v.largo_cm = 10 and v.ancho_cm = 10 and v.alto_cm = 10
--  group by c.nombre
--  order by variantes_sin_pesar desc;
