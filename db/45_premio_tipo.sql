-- 45_premio_tipo.sql
--
-- Hay dos clases de premio, no una.
--
-- `descuento_pct` daba por supuesto que todo premio era un porcentaje. No lo es:
-- «FREE!» —que mañana puede llamarse «GRATIS» o «REGALO!»— es un artículo
-- promocional que se mete en la caja junto a lo comprado. No descuenta un peso y aun
-- así hay que aplicarlo, porque alguien tiene que acordarse de meterlo.
--
-- Que el mismo premio pueda llamarse de tres formas distintas es, precisamente, el
-- motivo de que el rótulo no decida nada: es lo que se pinta en el sector y cambia
-- cuando apetece. Quien decide es esta columna.
--
-- Idempotente: se puede correr dos veces.

begin;

alter table wheel_prizes
  add column if not exists tipo text not null default 'ninguno';

comment on column wheel_prizes.tipo is
  'descuento (un porcentaje sobre los articulos), regalo (un articulo que se mete en '
  'la caja) o ninguno (consolacion). El rotulo es lo que se pinta; esto es lo que se '
  'aplica.';

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'wheel_prizes_tipo_check'
  ) then
    alter table wheel_prizes add constraint wheel_prizes_tipo_check
      check (tipo in ('descuento', 'regalo', 'ninguno'));
  end if;
end $$;

-- Se deduce de lo que ya hay, **una vez**. A partir de aquí se configura a mano: un
-- premio nuevo nace en 'ninguno' y hay que decirle qué es, que es más seguro que
-- adivinarlo y acertar casi siempre.
update wheel_prizes
   set tipo = case
     when descuento_pct is not null then 'descuento'
     when otorga_premio             then 'regalo'
     else 'ninguno'
   end
 where tipo = 'ninguno';

-- Un descuento sin porcentaje no descuenta nada, y un regalo con porcentaje son dos
-- premios a la vez. Ninguna de las dos cosas debería poder guardarse.
do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'wheel_prizes_tipo_coherente'
  ) then
    alter table wheel_prizes add constraint wheel_prizes_tipo_coherente
      check (
        (tipo = 'descuento' and descuento_pct is not null) or
        (tipo <> 'descuento' and descuento_pct is null)
      );
  end if;
end $$;

-- ---------------------------------------------------------------------------
-- El regalo, en la solicitud
-- ---------------------------------------------------------------------------
--
-- Se copia el texto, no una referencia al premio. Quien prepara la caja necesita leer
-- qué meter sin cruzar tres tablas, y si mañana cambias el premio, lo que se le
-- prometió a esta persona no lo reescribe nadie. Mismo criterio que `descuento_pct`.

alter table order_requests
  add column if not exists regalo text;

comment on column order_requests.regalo is
  'Articulo promocional que gano con su codigo y va en la caja. Copiado al cobrar, '
  'para que no cambie si despues se edita el premio.';

grant select (regalo) on order_requests to authenticated;

commit;

-- Comprobar cómo quedó cada premio:
--
-- select rotulo, tipo, descuento_pct, vigencia_dias, peso, activo
--   from wheel_prizes order by orden;
--
-- «FREE!» debería haber quedado en 'regalo'. Conviene además que su `detalle` diga
-- QUÉ se regala, porque es lo que se va a leer al preparar el pedido:
--
-- update wheel_prizes
--    set detalle = 'Un articulo promocional de regalo con tu compra.'
--  where rotulo = 'FREE!';
--
-- Y los pedidos que llevan regalo pendiente de meter:
--
-- select folio, regalo, pago_estado, created_at
--   from order_requests
--  where regalo is not null and pago_estado = 'aprobado'
--  order by created_at desc;
