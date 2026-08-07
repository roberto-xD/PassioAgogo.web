-- ============================================================
-- 12 · Mensajes del formulario de contacto (web pública)
-- Passion Agogo · Supabase (PostgreSQL)
-- Ejecutar después de 09/10 (usa is_staff()) · Idempotente
--
-- Los mensajes NO se insertan desde el navegador: entran por la
-- Edge Function `contact`, que verifica el captcha de Turnstile y
-- aplica límite de envíos antes de escribir con service_role.
-- Por eso esta tabla no tiene política de INSERT: un cliente con
-- la anon key no puede saltarse esa verificación.
-- ============================================================

begin;

create table if not exists contact_messages (
  id         uuid primary key default gen_random_uuid(),
  nombre     text not null check (length(trim(nombre)) between 1 and 80),
  email      text not null check (length(email) between 5 and 120),
  mensaje    text not null check (length(trim(mensaje)) between 10 and 2000),
  -- Hash con sal del IP de origen: permite limitar envíos sin guardar el IP.
  ip_hash    text,
  atendido   boolean not null default false,
  created_at timestamptz not null default now()
);

create index if not exists idx_contact_messages_created
  on contact_messages(created_at desc);

-- Consulta del límite de envíos por origen.
create index if not exists idx_contact_messages_rate
  on contact_messages(ip_hash, created_at);

-- Bandeja de pendientes.
create index if not exists idx_contact_messages_pendientes
  on contact_messages(created_at desc)
  where not atendido;

alter table contact_messages enable row level security;

-- Solo el staff lee los mensajes y los marca como atendidos.
drop policy if exists contact_messages_select on contact_messages;
create policy contact_messages_select on contact_messages for select to authenticated
  using (is_staff());

drop policy if exists contact_messages_update on contact_messages;
create policy contact_messages_update on contact_messages for update to authenticated
  using (is_staff())
  with check (is_staff());

commit;
