-- ============================================================
-- 30 · Teléfono en el perfil y edición de los datos propios
-- Passion à gogo · Supabase (PostgreSQL)
-- Ejecutar después del 29 · Idempotente
--
-- La web deja editar el nombre y guardar un teléfono de contacto desde
-- «Mi cuenta», para no tener que volver a escribirlos en cada
-- solicitud.
-- ============================================================

begin;

alter table profiles
  add column if not exists telefono text;

comment on column profiles.telefono is
  'Telefono de contacto que la persona guarda desde la web. Se usa para '
  'rellenar la solicitud; no es un dato de autenticacion.';

alter table profiles drop constraint if exists chk_profiles_telefono;
alter table profiles add constraint chk_profiles_telefono
  check (telefono is null or length(trim(telefono)) between 7 and 20);

-- ------------------------------------------------------------
-- Cada quien edita su propia fila
-- ------------------------------------------------------------
drop policy if exists profiles_update_self on profiles;
create policy profiles_update_self on profiles for update to authenticated
  using (id = auth.uid())
  with check (id = auth.uid());

-- ------------------------------------------------------------
-- ⚠ Lo que esa política NO puede hacer
--
-- RLS decide qué FILAS se tocan, no qué COLUMNAS. Sin nada más, quien
-- puede editar su fila podría ponerse `rol = 'admin'` con una petición
-- a mano: la política lo dejaría pasar porque la fila sigue siendo la
-- suya.
--
-- Se cierra con un trigger y no revocando el permiso de columna porque
-- revocarlo le quitaría también a administración la posibilidad de
-- cambiar el rol de alguien desde tu app, que es `authenticated` igual
-- que cualquier cliente.
-- ------------------------------------------------------------
create or replace function fn_profiles_protege_rol()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if (new.rol is distinct from old.rol
      or new.activo is distinct from old.activo)
     and not is_admin() then
    raise exception 'Solo administracion puede cambiar el rol o el estado de una cuenta';
  end if;
  -- El id tampoco se mueve: cambiarlo desligaría la fila de su usuario.
  if new.id is distinct from old.id then
    raise exception 'El identificador del perfil no se puede cambiar';
  end if;
  return new;
end;
$$;

drop trigger if exists trg_profiles_protege_rol on profiles;
create trigger trg_profiles_protege_rol
  before update on profiles
  for each row execute function fn_profiles_protege_rol();

commit;

-- ============================================================
-- Comprobación
-- ============================================================
-- Debe aparecer profiles_update_self entre las políticas:
-- select policyname, cmd from pg_policies
--  where tablename = 'profiles' order by policyname;
--
-- Y esto debe fallar si lo ejecuta alguien que no sea admin:
-- update profiles set rol = 'admin' where id = auth.uid();
