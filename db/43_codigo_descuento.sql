-- 43_codigo_descuento.sql
--
-- Que los códigos de la ruleta se puedan canjear al pagar.
--
-- Hasta ahora un código era una promesa que se cumplía a mano por WhatsApp. Para que
-- el cobro lo aplique solo hacen falta tres cosas que no existían:
--
--  1. Que el premio diga **cuánto** descuenta, en un número y no en una etiqueta.
--  2. Que el código pueda **caducar**.
--  3. Un sitio donde contar los **intentos fallidos**, porque un validador de códigos
--     sin freno es una invitación a probarlos todos.
--
-- Decidido: el descuento va **solo sobre los artículos**, nunca sobre el envío —ese lo
-- paga la tienda entero y no tiene margen que recortar—; la vigencia se configura por
-- premio; y el código es **al portador y de un solo uso**, como la ruleta, que es
-- anónima y no sabe quién giró.
--
-- Idempotente: se puede correr dos veces.

begin;

-- ---------------------------------------------------------------------------
-- 1. El premio dice cuánto descuenta
-- ---------------------------------------------------------------------------
--
-- `rotulo` es lo que se pinta en el sector: texto para el ojo. Sacar de ahí el importe
-- funcionaría mientras todos digan «25%» y se rompería el día que uno diga «2x1» o
-- «Envío gratis», sacándole un 2 o un 0. Lo que decide un cobro no se deduce de una
-- etiqueta.

alter table wheel_prizes
  add column if not exists descuento_pct integer,
  add column if not exists vigencia_dias integer;

comment on column wheel_prizes.descuento_pct is
  'Porcentaje de descuento sobre los articulos, sin el envio. Nulo = el premio no es '
  'un descuento (consolacion, o algo que se entrega a mano).';
comment on column wheel_prizes.vigencia_dias is
  'Dias que vale el codigo desde que se gira. Nulo = no caduca.';

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'wheel_prizes_descuento_pct_check'
  ) then
    alter table wheel_prizes add constraint wheel_prizes_descuento_pct_check
      check (descuento_pct is null or descuento_pct between 1 and 100);
  end if;
  if not exists (
    select 1 from pg_constraint where conname = 'wheel_prizes_vigencia_dias_check'
  ) then
    alter table wheel_prizes add constraint wheel_prizes_vigencia_dias_check
      check (vigencia_dias is null or vigencia_dias between 1 and 3650);
  end if;
end $$;

-- Se rellena **una vez** lo que ya existe, leyendo los rótulos que son exactamente un
-- porcentaje. Esto es una migración, no una regla: a partir de aquí manda la columna,
-- y un premio nuevo hay que configurarlo. Solo toca los que están sin rellenar.
update wheel_prizes
   set descuento_pct = nullif(regexp_replace(rotulo, '[^0-9]', '', 'g'), '')::integer
 where descuento_pct is null
   and otorga_premio
   and rotulo ~ '^[0-9]{1,3}\s*%$';

-- ---------------------------------------------------------------------------
-- 2. El código caduca
-- ---------------------------------------------------------------------------
--
-- La fecha se graba **en el giro**, no se deduce del premio al canjear. Si mañana
-- acortas la vigencia de un premio, los códigos ya emitidos conservan la que se les
-- prometió: no se le puede cambiar el plazo a alguien a toro pasado.
--
-- Los códigos de antes se quedan en nulo, que es «no caduca». Se emitieron sin plazo y
-- ponérselo ahora seria quitarles algo que ya se les habia dado.

alter table wheel_spins
  add column if not exists expira_at timestamptz;

comment on column wheel_spins.expira_at is
  'Cuando deja de valer. Se graba al girar, desde wheel_prizes.vigencia_dias. Nulo = '
  'no caduca, que es el caso de los emitidos antes de que existiera esta columna.';

create index if not exists idx_wheel_spins_codigo_vivo
  on wheel_spins (codigo) where not canjeado;

-- ---------------------------------------------------------------------------
-- 3. El código en la solicitud
-- ---------------------------------------------------------------------------

alter table order_requests
  add column if not exists codigo_descuento text,
  add column if not exists descuento_pct    integer,
  add column if not exists descuento_monto  numeric(10,2);

comment on column order_requests.codigo_descuento is
  'El codigo de la ruleta aplicado. Lo valida y lo aplica crear-pago; del navegador '
  'solo llega el codigo, nunca el importe.';
comment on column order_requests.descuento_pct is
  'El porcentaje que se aplico, copiado al crear el cobro. Se guarda aunque el premio '
  'cambie despues: lo que se le descontó a esta persona no lo reescribe nadie.';
comment on column order_requests.descuento_monto is
  'Los pesos descontados, sobre los articulos. El envio nunca se descuenta.';

-- **Un código, una solicitud, para siempre.** Esto es lo que resuelve la carrera de
-- abrir dos carritos con el mismo código: lo decide la base, no una comprobación
-- nuestra que dos peticiones simultáneas pueden pasar las dos.
--
-- Y no deja a nadie sin premio: si abandona el pago, el código sigue en esa solicitud
-- y esa solicitud se puede pagar después.
create unique index if not exists idx_order_requests_codigo
  on order_requests (codigo_descuento) where codigo_descuento is not null;

-- El cliente lee lo que se le aplicó; escribirlo no, que para eso está crear-pago.
grant select (codigo_descuento, descuento_pct, descuento_monto)
   on order_requests to authenticated;

-- ---------------------------------------------------------------------------
-- 4. Los intentos de código
-- ---------------------------------------------------------------------------
--
-- Un validador de códigos sin freno se prueba entero. Son 31 caracteres en 6
-- posiciones —casi 887 millones de combinaciones—, pero los que **valen** son solo los
-- emitidos: con unos miles vivos, un script da con uno en una tarde. Y un 75 % vale
-- dinero de verdad.
--
-- Tabla aparte de `cotizacion_intentos` aunque la forma sea la misma: son dos cosas
-- distintas con dos topes distintos, y una tabla que se llama «cotizaciones» llena de
-- intentos de código miente a quien la abra dentro de un año.

create table if not exists codigo_intentos (
  id         bigserial primary key,
  usuario_id uuid references profiles(id) on delete cascade,
  ip_hash    text,
  -- Solo si acertó. Para distinguir a quien se equivoca al teclear de quien prueba a
  -- ciegas: el segundo falla siempre.
  acerto     boolean not null default false,
  created_at timestamptz not null default now()
);

create index if not exists idx_codigo_intentos_usuario
  on codigo_intentos (usuario_id, created_at desc);
create index if not exists idx_codigo_intentos_ip
  on codigo_intentos (ip_hash, created_at desc);

alter table codigo_intentos enable row level security;
-- Sin politicas: solo entra por la Edge Function, con service_role.

commit;

-- ---------------------------------------------------------------------------
-- Configurar los premios
-- ---------------------------------------------------------------------------
--
-- Comprobar qué quedó tras la migración, y rellenar lo que falte a mano:
--
-- select rotulo, otorga_premio, descuento_pct, vigencia_dias, peso, activo
--   from wheel_prizes order by orden;
--
-- update wheel_prizes set vigencia_dias = 30 where descuento_pct is not null;
-- update wheel_prizes set vigencia_dias = 7  where rotulo = '75%';
--
-- Y para ver qué se ha canjeado de verdad:
--
-- select s.rotulo, count(*) filter (where s.canjeado) as canjeados, count(*) as emitidos,
--        round(sum(o.descuento_monto), 2) as descontado
--   from wheel_spins s
--   left join order_requests o on o.codigo_descuento = s.codigo
--  group by 1 order by 2 desc;
