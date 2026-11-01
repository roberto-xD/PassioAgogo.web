-- 38_guia_eventos.sql
--
-- Quién generó y quién canceló cada guía, y cuándo.
--
-- Va en tabla y no en columnas de `order_requests` porque es un **historial**: una
-- solicitud puede generar guía, cancelarla y volver a generar otra, y en una columna
-- solo cabría la última. La pregunta que esto responde —«¿quién pidió esta guía que
-- cobraron y por qué ya no está?»— necesita la secuencia entera, no el final.
--
-- Guarda copia del número, la paquetería y el costo. Parece repetido mientras la guía
-- existe, pero al cancelarla esas columnas de `order_requests` se vacían: sin la copia,
-- el registro diría que alguien canceló algo sin decir qué.
--
-- Escribe la Edge Function con service_role. Nadie más: un historial que el interesado
-- puede editar no es un historial.
--
-- Idempotente: se puede correr dos veces.

begin;

create table if not exists guia_eventos (
  id         uuid primary key default gen_random_uuid(),
  request_id uuid not null references order_requests(id) on delete cascade,

  accion     text not null check (accion in ('generada', 'cancelada')),

  -- Quién. Se conserva la fila aunque la cuenta se borre —`set null`— porque borrar a
  -- una persona no debe borrar el rastro de un cobro.
  usuario_id uuid references profiles(id) on delete set null,
  -- El correo tal cual estaba ese día: si la cuenta desaparece, el registro todavía
  -- dice de quién fue la acción.
  usuario    text,

  -- Copia de lo que había, para que el registro se entienda solo.
  guia_numero     text,
  guia_paqueteria text,
  guia_costo      numeric(10,2),
  -- Solo en las cancelaciones: si la paquetería devolvió el importe.
  devuelto   boolean,
  -- Lo que dijo quien lo hizo, cuando lo dijo. Por ejemplo, por qué cambió de
  -- paquetería respecto a la que eligió el cliente.
  nota       text,

  created_at timestamptz not null default now()
);

create index if not exists idx_guia_eventos_solicitud
  on guia_eventos (request_id, created_at desc);

alter table guia_eventos enable row level security;

-- Solo administración lee. Al cliente no le dice nada útil que no esté ya en su
-- historial, y sí dice cómo se mueve la tienda por dentro.
drop policy if exists guia_eventos_select on guia_eventos;
create policy guia_eventos_select on guia_eventos for select to authenticated
  using (is_admin());

-- Sin política de insert, update ni delete **a propósito**: solo entra por la Edge
-- Function, que usa service_role y salta el RLS. Un historial que se puede reescribir
-- desde el cliente no sirve para lo único que sirve un historial.

commit;

-- ============================================================
-- Para revisar
-- ============================================================
--
-- 1. Lo que ha pasado con una solicitud, en orden:
--
-- select e.created_at, e.accion, e.usuario, e.guia_paqueteria,
--        e.guia_numero, e.guia_costo, e.devuelto, e.nota
--   from guia_eventos e
--   join order_requests o on o.id = e.request_id
--  where o.folio = 2050
--  order by e.created_at;
--
-- 2. Guías canceladas sin que llegara la devolución, que es dinero pendiente:
--
-- select e.created_at, e.usuario, e.guia_paqueteria, e.guia_numero, e.guia_costo
--   from guia_eventos e
--  where e.accion = 'cancelada' and e.devuelto is not true
--  order by e.created_at desc;
--
-- 3. Cuánto se ha cancelado por mes, que dice si algo del proceso falla seguido:
--
-- select date_trunc('month', created_at) as mes,
--        count(*) filter (where accion = 'generada')  as generadas,
--        count(*) filter (where accion = 'cancelada') as canceladas
--   from guia_eventos
--  group by 1 order by 1 desc;
