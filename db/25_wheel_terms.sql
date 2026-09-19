-- ============================================================
-- 25 · Bases de la promoción de la ruleta
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después de 24_wheel · Idempotente
--
-- El texto vive en la base para poder corregirlo sin recompilar la
-- web: unas bases que no se pueden cambiar en el momento son unas
-- bases que se quedan desactualizadas.
--
-- Va en secciones y no en un solo campo largo para que cada cláusula
-- se edite por separado y la web pueda maquetarlas con su título.
--
-- ⚠ EL TEXTO SEMBRADO ES UN BORRADOR. Está redactado a partir de cómo
--   funciona la promoción de verdad, pero **debe revisarlo un
--   profesional legal antes de publicarlo**, y hay dos cosas que solo
--   tú puedes rellenar: las fechas de vigencia y los productos
--   excluidos. La consulta del final te las señala.
-- ============================================================

begin;

create table if not exists wheel_terms (
  id      uuid primary key default gen_random_uuid(),
  titulo  text not null check (length(trim(titulo)) between 1 and 80),
  cuerpo  text not null check (length(trim(cuerpo)) between 1 and 2000),
  orden   integer not null default 0,
  activo  boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_wheel_terms_orden
  on wheel_terms (orden)
  where activo;

alter table wheel_terms enable row level security;

grant select on wheel_terms to anon, authenticated;

-- Unas bases que no se pueden leer no sirven de nada: lectura abierta.
drop policy if exists wheel_terms_select_anon on wheel_terms;
create policy wheel_terms_select_anon on wheel_terms for select to anon
  using (activo);

drop policy if exists wheel_terms_select_auth on wheel_terms;
create policy wheel_terms_select_auth on wheel_terms for select to authenticated
  using (activo or is_staff());

drop policy if exists wheel_terms_write on wheel_terms;
create policy wheel_terms_write on wheel_terms for all to authenticated
  using (is_admin()) with check (is_admin());

drop trigger if exists trg_wheel_terms_updated_at on wheel_terms;
create trigger trg_wheel_terms_updated_at
  before update on wheel_terms
  for each row execute function fn_set_updated_at();

commit;

-- ============================================================
-- Borrador de las bases
-- ============================================================
insert into wheel_terms (titulo, cuerpo, orden)
select * from (values
  (
    'Organizador',
    'La promocion "Gira y gana" es organizada por Roberto Gutierrez C, con domicilio en ' ||
    'Velo de novia 16, Los Angeles Iztapalapa, Ciudad de Mexico, titular de la tienda ' ||
    'Passion a gogo. Para cualquier duda sobre esta promocion puedes escribir a ' ||
    'contacto@passionagogo.com.',
    1
  ),
  (
    'Vigencia',
    'La promocion estara vigente del [FECHA DE INICIO] al [FECHA DE FIN], o hasta agotar ' ||
    'los premios disponibles, lo que ocurra primero. Fuera de ese periodo la ruleta no ' ||
    'estara disponible y no se emitiran codigos nuevos.',
    2
  ),
  (
    'Quien puede participar',
    'Pueden participar personas mayores de 18 anos con residencia en Mexico. La tienda ' ||
    'comercializa productos para publico adulto, por lo que al participar declaras, bajo ' ||
    'tu responsabilidad, que cumples ese requisito de edad. No pueden participar el ' ||
    'personal de la tienda ni sus familiares directos.',
    3
  ),
  (
    'Como participar',
    'Entra a la seccion "Gira y gana" del sitio y pulsa el boton de girar. La ruleta se ' ||
    'detiene en un premio y, si ese premio otorga descuento, recibes un codigo unico que ' ||
    'queda registrado en nuestros sistemas. No hay que registrarse, ni comprar, ni ' ||
    'proporcionar datos personales para participar.',
    4
  ),
  (
    'Un giro por persona',
    'Se permite un giro por persona cada 24 horas. Para aplicar ese limite guardamos un ' ||
    'resumen criptografico de tu direccion IP, no la direccion en si. Si vuelves a entrar ' ||
    'dentro de ese periodo, el sitio te mostrara de nuevo el premio que ya obtuviste, que ' ||
    'sigue siendo valido.',
    5
  ),
  (
    'Premios y probabilidades',
    'Los premios son descuentos porcentuales aplicables sobre el precio de lista, y uno de ' ||
    'los sectores no otorga premio. Los premios vigentes son los que muestra la ruleta en ' ||
    'el momento de girar. Las probabilidades no son iguales para todos los premios: los ' ||
    'descuentos mayores tienen una probabilidad considerablemente menor. El resultado ' ||
    'lo determina un sorteo aleatorio en nuestros servidores y queda registrado antes de ' ||
    'mostrarse.',
    6
  ),
  (
    'Como se canjea',
    'Para canjear tu premio escribenos por WhatsApp con tu codigo antes de que venza. El ' ||
    'codigo tiene una vigencia de 30 dias naturales contados desde el giro, es de un solo ' ||
    'uso, personal e intransferible, y aplica a una sola compra. Verificaremos el codigo ' ||
    'contra nuestros registros antes de aplicar el descuento.',
    7
  ),
  (
    'Restricciones',
    'El descuento no es acumulable con otras promociones ni con precios ya rebajados, no ' ||
    'es canjeable por dinero en efectivo ni por otros productos, y esta sujeto a ' ||
    'disponibilidad de inventario. Quedan excluidos los siguientes productos: [PRODUCTOS ' ||
    'EXCLUIDOS, O "ninguno"]. Los gastos de envio no forman parte del descuento salvo que ' ||
    'se indique lo contrario.',
    8
  ),
  (
    'Datos personales',
    'Para participar no recabamos nombre, correo ni telefono. Solo guardamos el resumen ' ||
    'criptografico de la IP descrito arriba y el codigo emitido. Si decides canjear el ' ||
    'premio por WhatsApp, el tratamiento de esa conversacion se rige por nuestra Politica ' ||
    'de privacidad, disponible en este mismo sitio.',
    9
  ),
  (
    'Modificaciones y cancelacion',
    'Podemos modificar estas bases o suspender la promocion por causas justificadas, ' ||
    'publicando la version vigente en esta misma pagina. Los codigos ya emitidos y no ' ||
    'vencidos se respetaran en los terminos en que fueron obtenidos.',
    10
  ),
  (
    'Aceptacion',
    'La participacion en esta promocion implica la aceptacion de estas bases. Cualquier ' ||
    'situacion no prevista sera resuelta por el organizador conforme a la legislacion ' ||
    'aplicable en materia de proteccion al consumidor.',
    11
  )
) as nuevas(titulo, cuerpo, orden)
where not exists (
  select 1 from wheel_terms t where lower(trim(t.titulo)) = lower(trim(nuevas.titulo))
);

-- ============================================================
-- Qué falta por rellenar
--
-- Todo lo que aparezca aquí lleva un hueco entre corchetes que tienes
-- que sustituir antes de publicar la promoción.
-- ============================================================
select orden, titulo, cuerpo
from wheel_terms
where activo and cuerpo like '%[%]%'
order by orden;

-- Para editar una cláusula:
-- update wheel_terms
--    set cuerpo = 'La promocion estara vigente del 1 de octubre al 31 de octubre de 2026...'
--  where titulo = 'Vigencia';
