-- AMAN | أمان
-- AMAN-2 support: audited Admin RPCs for telecom prefixes and provider tariffs.
-- Additive source migration. It has NOT been executed on any database.
-- Existing Admin permissions, RLS read policies, and provider tariff snapshots are preserved.

begin;

create or replace function public.admin_save_telecom_prefix(
  p_id uuid,
  p_provider_id uuid,
  p_prefix text,
  p_country_code text,
  p_number_length integer,
  p_status public.record_status
) returns public.telecom_prefixes
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_before public.telecom_prefixes;
  v_after public.telecom_prefixes;
  v_prefix text;
begin
  perform public.require_admin_permission('admin_providers.manage');
  v_prefix := regexp_replace(coalesce(p_prefix, ''), '[[:space:]]+', '', 'g');
  if p_provider_id is null then raise exception 'provider_required'; end if;
  if v_prefix !~ '^[+]?[0-9]{2,15}$' then raise exception 'invalid_telecom_prefix'; end if;
  if p_number_length is not null and (p_number_length < 1 or p_number_length > 15) then
    raise exception 'invalid_number_length';
  end if;
  if not exists (select 1 from public.telecom_providers where id = p_provider_id) then
    raise exception 'provider_not_found';
  end if;

  if p_id is null then
    insert into public.telecom_prefixes(provider_id, prefix, country_code, number_length, status)
    values (p_provider_id, v_prefix, nullif(trim(p_country_code), ''), p_number_length, p_status)
    returning * into v_after;
  else
    select * into v_before from public.telecom_prefixes where id = p_id for update;
    if not found then raise exception 'telecom_prefix_not_found'; end if;
    update public.telecom_prefixes
       set provider_id = p_provider_id,
           prefix = v_prefix,
           country_code = nullif(trim(p_country_code), ''),
           number_length = p_number_length,
           status = p_status
     where id = p_id returning * into v_after;
  end if;

  perform public.write_admin_audit(
    'telecom_prefix', v_after.id, to_jsonb(v_before), to_jsonb(v_after),
    jsonb_build_object('operation', case when p_id is null then 'create' else 'update' end)
  );
  return v_after;
end;
$$;

create or replace function public.admin_save_provider_tariff(
  p_id uuid,
  p_provider_id uuid,
  p_points_per_day integer,
  p_effective_from timestamptz,
  p_effective_to timestamptz,
  p_status public.record_status
) returns public.provider_tariffs
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_before public.provider_tariffs;
  v_after public.provider_tariffs;
begin
  perform public.require_admin_permission('admin_providers.manage');
  if p_provider_id is null then raise exception 'provider_required'; end if;
  if p_points_per_day is null or p_points_per_day <= 0 then raise exception 'invalid_points_per_day'; end if;
  if p_effective_from is null then raise exception 'effective_from_required'; end if;
  if p_effective_to is not null and p_effective_to <= p_effective_from then
    raise exception 'effective_to_must_follow_effective_from';
  end if;
  if not exists (select 1 from public.telecom_providers where id = p_provider_id) then
    raise exception 'provider_not_found';
  end if;

  if p_id is null then
    insert into public.provider_tariffs(provider_id, points_per_day, effective_from, effective_to, status)
    values (p_provider_id, p_points_per_day, p_effective_from, p_effective_to, p_status)
    returning * into v_after;
  else
    select * into v_before from public.provider_tariffs where id = p_id for update;
    if not found then raise exception 'provider_tariff_not_found'; end if;
    update public.provider_tariffs
       set provider_id = p_provider_id,
           points_per_day = p_points_per_day,
           effective_from = p_effective_from,
           effective_to = p_effective_to,
           status = p_status
     where id = p_id returning * into v_after;
  end if;

  -- Existing protection rows keep points_per_day_snapshot and tariff_id as historical evidence.
  perform public.write_admin_audit(
    'provider_tariff', v_after.id, to_jsonb(v_before), to_jsonb(v_after),
    jsonb_build_object('operation', case when p_id is null then 'create' else 'update' end)
  );
  return v_after;
end;
$$;

revoke all on function public.admin_save_telecom_prefix(uuid, uuid, text, text, integer, public.record_status) from public, anon;
revoke all on function public.admin_save_provider_tariff(uuid, uuid, integer, timestamptz, timestamptz, public.record_status) from public, anon;
grant execute on function public.admin_save_telecom_prefix(uuid, uuid, text, text, integer, public.record_status) to authenticated;
grant execute on function public.admin_save_provider_tariff(uuid, uuid, integer, timestamptz, timestamptz, public.record_status) to authenticated;

commit;
