-- AMAN | أمان
-- AMAN-1: canonical backend/database repairs
-- This migration is a source artifact. It must be reviewed and applied only
-- in a separately authorized database phase; it has not been executed here.

begin;

-- ---------------------------------------------------------------------------
-- Canonical naming and audit/ledger vocabulary
-- ---------------------------------------------------------------------------

alter table public.audit_logs rename column actor_id to actor_user_id;
alter table public.audit_logs rename column before_data to "before";
alter table public.audit_logs rename column after_data to "after";
alter table public.audit_logs add column actor_role text;

update public.audit_logs a
set actor_role = coalesce((
  select string_agg(r.code, ',' order by r.code)
  from public.admin_user_roles ur
  join public.admin_roles r on r.id = ur.role_id and r.status = 'active'
  where ur.user_id = a.actor_user_id
), case when a.actor_user_id is null then 'system' else 'customer' end)
where a.actor_role is null;

alter table public.financial_ledger add column direction text not null default 'credit';
update public.financial_ledger
set entry_type = case entry_type
  when 'points_purchase' then 'points_purchase_income'
  when 'payment_task' then 'task_payment'
  else entry_type
end,
direction = case
  when entry_type in ('payment_task', 'task_payment', 'expense') then 'debit'
  else 'credit'
end;

do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'financial_ledger_entry_type_check') then
    alter table public.financial_ledger add constraint financial_ledger_entry_type_check
      check (entry_type in ('points_purchase_income','task_payment','expense','adjustment'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'financial_ledger_direction_check') then
    alter table public.financial_ledger add constraint financial_ledger_direction_check
      check (direction in ('credit','debit'));
  end if;
end $$;

alter table public.support_messages add column sender_type text not null default 'customer';
do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'support_messages_sender_type_check') then
    alter table public.support_messages add constraint support_messages_sender_type_check
      check (sender_type in ('customer','admin','system'));
  end if;
end $$;

create index if not exists idx_profiles_phone on public.profiles(phone);
create index if not exists idx_protection_extensions_protection on public.protection_extensions(protection_id);
create index if not exists idx_admin_notifications_target on public.admin_notifications(target_type, target_id);

-- ---------------------------------------------------------------------------
-- Canonical role: admin
-- ---------------------------------------------------------------------------

do $$
declare v_admin uuid; v_super uuid;
begin
  select id into v_admin from public.admin_roles where code = 'admin' for update;
  select id into v_super from public.admin_roles where code = 'super_admin' for update;
  if v_admin is null and v_super is not null then
    update public.admin_roles set code = 'admin', name = 'Administrator' where id = v_super;
    v_admin := v_super;
  elsif v_admin is null then
    insert into public.admin_roles(code, name) values ('admin', 'Administrator') returning id into v_admin;
  elsif v_super is not null and v_super <> v_admin then
    insert into public.admin_role_permissions(role_id, permission_id)
      select v_admin, permission_id from public.admin_role_permissions where role_id = v_super
      on conflict do nothing;
    update public.admin_user_roles set role_id = v_admin where role_id = v_super
      and not exists (select 1 from public.admin_user_roles x where x.user_id = public.admin_user_roles.user_id and x.role_id = v_admin);
    delete from public.admin_role_permissions where role_id = v_super;
    delete from public.admin_user_roles where role_id = v_super;
    delete from public.admin_roles where id = v_super;
  end if;
  insert into public.admin_role_permissions(role_id, permission_id)
    select v_admin, p.id from public.admin_permissions p on conflict do nothing;
end $$;

create or replace function public.is_admin(p_user_id uuid default auth.uid())
returns boolean language sql stable security definer set search_path = public, pg_temp as $$
  select exists (
    select 1
    from public.admin_user_roles ur
    join public.admin_roles r on r.id = ur.role_id and r.code = 'admin' and r.status = 'active'
    join public.admin_role_permissions rp on rp.role_id = r.id
    join public.admin_permissions p on p.id = rp.permission_id and p.code = 'admin_dashboard.read'
    where ur.user_id = p_user_id
  );
$$;

create or replace function public.write_admin_audit(
  p_entity_type text, p_entity_id uuid, p_before jsonb, p_after jsonb,
  p_metadata jsonb default '{}'::jsonb
) returns void language plpgsql security definer set search_path = public, pg_temp as $$
declare v_role text;
begin
  select coalesce(string_agg(r.code, ',' order by r.code), 'customer') into v_role
  from public.admin_user_roles ur
  join public.admin_roles r on r.id = ur.role_id and r.status = 'active'
  where ur.user_id = auth.uid();
  insert into public.audit_logs(actor_user_id, actor_role, action, entity_type, entity_id, "before", "after", metadata)
  values (auth.uid(), coalesce(v_role, case when auth.uid() is null then 'system' else 'customer' end),
          'rpc', p_entity_type, p_entity_id, p_before, p_after, coalesce(p_metadata, '{}'::jsonb));
end;
$$;

-- ---------------------------------------------------------------------------
-- Auth -> profile provisioning
-- ---------------------------------------------------------------------------

create or replace function public.provision_profile_from_auth()
returns trigger language plpgsql security definer set search_path = public, pg_temp as $$
begin
  insert into public.profiles(id, full_name, username, phone, email)
  values (
    new.id,
    nullif(trim(coalesce(new.raw_user_meta_data ->> 'full_name', '')), ''),
    nullif(trim(coalesce(new.raw_user_meta_data ->> 'username', '')), ''),
    nullif(trim(coalesce(new.phone, new.raw_user_meta_data ->> 'phone', '')), ''),
    new.email
  ) on conflict (id) do nothing;
  insert into public.point_balances(user_id, balance_points) values (new.id, 0) on conflict do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.provision_profile_from_auth();

-- ---------------------------------------------------------------------------
-- Customer number lifecycle: provider is authoritative from longest prefix.
-- ---------------------------------------------------------------------------

create or replace function public.add_customer_number(p_phone_e164 text)
returns public.customer_numbers language plpgsql security definer set search_path = public, pg_temp as $$
declare v_normalized text; v_provider uuid; v_phone public.phone_numbers; v_row public.customer_numbers;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  v_normalized := regexp_replace(coalesce(p_phone_e164, ''), '[^0-9+]', '', 'g');
  if v_normalized !~ '^\+[0-9]{7,15}$' then raise exception 'invalid_phone'; end if;
  v_provider := public.resolve_provider(v_normalized);
  if v_provider is null then raise exception 'unknown_phone_prefix'; end if;
  insert into public.phone_numbers(phone_e164, normalized_phone, provider_id)
    values (v_normalized, v_normalized, v_provider)
    on conflict (normalized_phone) do update set provider_id = excluded.provider_id, updated_at = now()
    returning * into v_phone;
  insert into public.customer_numbers(user_id, phone_number_id, status)
    values (auth.uid(), v_phone.id, 'active')
    on conflict (user_id, phone_number_id) do update set status = 'active'
    returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.update_customer_number(p_customer_number_id uuid, p_phone_e164 text)
returns public.customer_numbers language plpgsql security definer set search_path = public, pg_temp as $$
declare v_row public.customer_numbers; v_protection uuid; v_normalized text; v_provider uuid; v_phone public.phone_numbers;
begin
  select * into v_row from public.customer_numbers where id = p_customer_number_id and user_id = auth.uid() for update;
  if not found then raise exception 'customer_number_not_found'; end if;
  select id into v_protection from public.protections where phone_number_id = v_row.phone_number_id and status = 'active';
  if v_protection is not null then raise exception 'protected_number_cannot_change'; end if;
  v_normalized := regexp_replace(coalesce(p_phone_e164, ''), '[^0-9+]', '', 'g');
  if v_normalized !~ '^\+[0-9]{7,15}$' then raise exception 'invalid_phone'; end if;
  v_provider := public.resolve_provider(v_normalized);
  if v_provider is null then raise exception 'unknown_phone_prefix'; end if;
  insert into public.phone_numbers(phone_e164, normalized_phone, provider_id)
    values (v_normalized, v_normalized, v_provider)
    on conflict (normalized_phone) do update set provider_id = excluded.provider_id, updated_at = now()
    returning * into v_phone;
  update public.customer_numbers set phone_number_id = v_phone.id, status = 'active'
    where id = p_customer_number_id returning * into v_row;
  perform public.write_admin_audit('customer_number', v_row.id, null, to_jsonb(v_row), jsonb_build_object('operation','update_customer_number'));
  return v_row;
end;
$$;

create or replace function public.archive_customer_number(p_customer_number_id uuid)
returns public.customer_numbers language plpgsql security definer set search_path = public, pg_temp as $$
declare v_row public.customer_numbers; v_protection uuid;
begin
  select * into v_row from public.customer_numbers where id = p_customer_number_id and user_id = auth.uid() for update;
  if not found then raise exception 'customer_number_not_found'; end if;
  select id into v_protection from public.protections where phone_number_id = v_row.phone_number_id and status = 'active';
  if v_protection is not null then raise exception 'protected_number_cannot_archive'; end if;
  update public.customer_numbers set status = 'archived' where id = p_customer_number_id returning * into v_row;
  perform public.write_admin_audit('customer_number', v_row.id, null, to_jsonb(v_row), jsonb_build_object('operation','archive_customer_number'));
  return v_row;
end;
$$;

-- ---------------------------------------------------------------------------
-- Purchase, approval and subscriber provisioning
-- ---------------------------------------------------------------------------

create or replace function public.submit_points_purchase(
  p_package_id uuid, p_payment_method_id uuid, p_payment_reference text, p_idempotency_key text
) returns public.points_purchase_requests language plpgsql security definer set search_path = public, pg_temp as $$
declare v_pkg public.points_packages; v_method public.payment_methods; v_existing public.points_purchase_requests; v_row public.points_purchase_requests;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  if nullif(trim(p_idempotency_key), '') is null then raise exception 'idempotency_key_required'; end if;
  select * into v_existing from public.points_purchase_requests where idempotency_key = trim(p_idempotency_key) for update;
  if found then
    if v_existing.user_id <> auth.uid() then raise exception 'idempotency_key_conflict'; end if;
    if v_existing.package_id <> p_package_id or v_existing.payment_method_id <> p_payment_method_id
       or v_existing.payment_reference <> trim(p_payment_reference) then
      raise exception 'idempotency_payload_conflict';
    end if;
    return v_existing;
  end if;
  select * into v_pkg from public.points_packages where id = p_package_id and status = 'active';
  if not found then raise exception 'package_unavailable'; end if;
  select * into v_method from public.payment_methods where id = p_payment_method_id and status = 'active';
  if not found then raise exception 'payment_method_unavailable'; end if;
  if nullif(trim(p_payment_reference), '') is null then raise exception 'payment_reference_required'; end if;
  insert into public.points_purchase_requests(request_number,user_id,package_id,points_amount_snapshot,price_amount_snapshot,currency_snapshot,payment_method_id,payment_method_name_snapshot,payment_reference,idempotency_key)
  values ('REQ-'||to_char(clock_timestamp(),'YYYYMMDDHH24MISSMS')||'-'||substr(gen_random_uuid()::text,1,8),auth.uid(),v_pkg.id,v_pkg.points_amount,v_pkg.price_amount,v_pkg.currency,v_method.id,v_method.name,trim(p_payment_reference),trim(p_idempotency_key))
  returning * into v_row;
  return v_row;
end;
$$;

create or replace function public.approve_points_purchase(p_request_id uuid, p_idempotency_key text)
returns public.points_purchase_requests language plpgsql security definer set search_path = public, pg_temp as $$
declare v_req public.points_purchase_requests; v_balance bigint; v_sub public.subscribers;
begin
  perform public.require_admin_permission('admin_purchases.approve');
  if nullif(trim(p_idempotency_key), '') is null then raise exception 'idempotency_key_required'; end if;
  select * into v_req from public.points_purchase_requests where id = p_request_id for update;
  if not found then raise exception 'request_not_found'; end if;
  if v_req.status <> 'pending' then return v_req; end if;
  insert into public.subscribers(user_id, status, became_subscriber_at) values (v_req.user_id, 'active', now())
    on conflict (user_id) do update set status = 'active', became_subscriber_at = coalesce(public.subscribers.became_subscriber_at, excluded.became_subscriber_at), updated_at = now()
    returning * into v_sub;
  insert into public.point_balances(user_id, balance_points) values (v_req.user_id, 0) on conflict do nothing;
  select balance_points into v_balance from public.point_balances where user_id = v_req.user_id for update;
  v_balance := v_balance + v_req.points_amount_snapshot;
  update public.point_balances set balance_points = v_balance, updated_at = now() where user_id = v_req.user_id;
  insert into public.point_ledger(user_id,entry_type,amount,balance_after,reference_type,reference_id,description,created_by)
    values(v_req.user_id,'purchase_credit',v_req.points_amount_snapshot,v_balance,'points_purchase_request',v_req.id,'Approved points purchase',auth.uid());
  update public.points_purchase_requests set status='approved', subscriber_id=v_sub.id, reviewed_by=auth.uid(), reviewed_at=now(), updated_at=now() where id=v_req.id returning * into v_req;
  insert into public.financial_ledger(entry_type,amount,currency,direction,reference_type,reference_id,description,created_by)
    values('points_purchase_income',v_req.price_amount_snapshot,v_req.currency_snapshot,'credit','points_purchase_request',v_req.id,'Approved points purchase',auth.uid());
  insert into public.operations(user_id,operation_type,status,reference_type,reference_id,points_delta,money_amount,idempotency_key)
    values(v_req.user_id,'points_approval','succeeded','points_purchase_request',v_req.id,v_req.points_amount_snapshot,v_req.price_amount_snapshot,trim(p_idempotency_key))
    on conflict (idempotency_key) do nothing;
  insert into public.notifications(user_id,notification_type,title,content,reference_type,reference_id)
    values(v_req.user_id,'system','تم اعتماد شراء النقاط','تمت إضافة النقاط إلى رصيدك.','points_purchase_request',v_req.id);
  perform public.write_admin_audit('points_purchase_request', v_req.id, null, to_jsonb(v_req), jsonb_build_object('decision','approved'));
  return v_req;
end;
$$;

-- ---------------------------------------------------------------------------
-- Activation and extension: reserve idempotency before any mutation.
-- ---------------------------------------------------------------------------

create or replace function public.activate_protection(p_phone_number_id uuid, p_duration_days integer, p_operation_key text)
returns public.protections language plpgsql security definer set search_path = public, pg_temp as $$
declare v_sub public.subscribers; v_phone public.phone_numbers; v_tariff public.provider_tariffs; v_balance bigint; v_cost bigint; v_protection public.protections; v_provider uuid; v_existing public.operations; v_op uuid;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  if p_duration_days <= 0 then raise exception 'invalid_duration'; end if;
  if nullif(trim(p_operation_key), '') is null then raise exception 'idempotency_key_required'; end if;
  select * into v_existing from public.operations where idempotency_key = trim(p_operation_key) for update;
  if found then
    if v_existing.user_id <> auth.uid() or v_existing.operation_type <> 'protection_activation' then raise exception 'idempotency_key_conflict'; end if;
    if v_existing.reference_id is null then raise exception 'operation_in_progress'; end if;
    select * into v_protection from public.protections where id = v_existing.reference_id;
    if not found then raise exception 'idempotency_result_missing'; end if;
    return v_protection;
  end if;
  select * into v_sub from public.subscribers where user_id=auth.uid() and status='active' for update;
  if not found then raise exception 'active_subscriber_required'; end if;
  if not exists (select 1 from public.customer_numbers where user_id=auth.uid() and phone_number_id=p_phone_number_id and status='active') then raise exception 'customer_number_not_owned'; end if;
  select * into v_phone from public.phone_numbers where id=p_phone_number_id for update;
  if not found then raise exception 'phone_not_found'; end if;
  v_provider := public.resolve_provider(v_phone.normalized_phone);
  if v_provider is null then raise exception 'unknown_phone_prefix'; end if;
  update public.phone_numbers set provider_id=v_provider, updated_at=now() where id=v_phone.id returning * into v_phone;
  if exists(select 1 from public.protections where phone_number_id=p_phone_number_id and status='active') then raise exception 'phone_already_protected'; end if;
  select * into v_tariff from public.provider_tariffs where provider_id=v_provider and status='active' and effective_from <= now() and (effective_to is null or effective_to > now()) order by effective_from desc limit 1;
  if not found then raise exception 'tariff_not_found'; end if;
  v_cost := p_duration_days * v_tariff.points_per_day;
  insert into public.operations(user_id,operation_type,status,phone_number_id,idempotency_key,metadata)
    values(auth.uid(),'protection_activation','processing',p_phone_number_id,trim(p_operation_key),jsonb_build_object('duration_days',p_duration_days))
    on conflict (idempotency_key) do nothing returning id into v_op;
  if v_op is null then
    select * into v_existing from public.operations where idempotency_key=trim(p_operation_key) for update;
    if v_existing.user_id <> auth.uid() or v_existing.operation_type <> 'protection_activation' then raise exception 'idempotency_key_conflict'; end if;
    select * into v_protection from public.protections where id=v_existing.reference_id;
    if not found then raise exception 'operation_in_progress'; end if;
    return v_protection;
  end if;
  insert into public.point_balances(user_id,balance_points) values(auth.uid(),0) on conflict do nothing;
  select balance_points into v_balance from public.point_balances where user_id=auth.uid() for update;
  if v_balance < v_cost then raise exception 'insufficient_points'; end if;
  update public.point_balances set balance_points=balance_points-v_cost, updated_at=now() where user_id=auth.uid();
  insert into public.protections(phone_number_id,subscriber_id,user_id,expires_at,duration_days,provider_id,tariff_id,points_per_day_snapshot,total_points_snapshot,activated_by)
    values(p_phone_number_id,v_sub.id,auth.uid(),now()+make_interval(days=>p_duration_days),p_duration_days,v_provider,v_tariff.id,v_tariff.points_per_day,v_cost,auth.uid()) returning * into v_protection;
  insert into public.point_ledger(user_id,entry_type,amount,balance_after,reference_type,reference_id,description,created_by)
    values(auth.uid(),'activation_debit',-v_cost,v_balance-v_cost,'protection',v_protection.id,'Protection activation',auth.uid());
  update public.operations set status='succeeded', reference_type='protection', reference_id=v_protection.id, points_delta=-v_cost, updated_at=now() where id=v_op;
  perform public.rebuild_task_plan(v_protection.id, v_protection.started_at);
  insert into public.notifications(user_id,notification_type,title,content,reference_type,reference_id)
    values(auth.uid(),'system','تم تفعيل الحماية','تم تفعيل حماية الرقم بنجاح.','protection',v_protection.id);
  perform public.write_admin_audit('protection',v_protection.id,null,to_jsonb(v_protection),jsonb_build_object('operation','activation'));
  return v_protection;
end;
$$;

create or replace function public.extend_protection(p_protection_id uuid, p_days integer, p_operation_key text)
returns public.protections language plpgsql security definer set search_path = public, pg_temp as $$
declare v_p public.protections; v_tariff public.provider_tariffs; v_balance bigint; v_cost bigint; v_before timestamptz; v_after timestamptz; v_op uuid; v_existing public.operations; v_anchor timestamptz;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  if p_days <= 0 then raise exception 'invalid_extension_days'; end if;
  if nullif(trim(p_operation_key), '') is null then raise exception 'idempotency_key_required'; end if;
  select * into v_existing from public.operations where idempotency_key=trim(p_operation_key) for update;
  if found then
    if v_existing.user_id <> auth.uid() or v_existing.operation_type <> 'protection_extension' then raise exception 'idempotency_key_conflict'; end if;
    if v_existing.reference_id is null then raise exception 'operation_in_progress'; end if;
    select * into v_p from public.protections where id=v_existing.reference_id;
    if not found then raise exception 'idempotency_result_missing'; end if;
    return v_p;
  end if;
  select * into v_p from public.protections where id=p_protection_id and user_id=auth.uid() and status in ('active','expired') for update;
  if not found then raise exception 'protection_not_owned_or_inactive'; end if;
  select * into v_tariff from public.provider_tariffs where id=v_p.tariff_id and status='active';
  if not found then raise exception 'tariff_not_found'; end if;
  v_cost := p_days * v_p.points_per_day_snapshot;
  v_before := v_p.expires_at;
  insert into public.operations(user_id,operation_type,status,reference_type,reference_id,phone_number_id,idempotency_key,metadata)
    values(auth.uid(),'protection_extension','processing','protection',v_p.id,v_p.phone_number_id,trim(p_operation_key),jsonb_build_object('days',p_days))
    on conflict (idempotency_key) do nothing returning id into v_op;
  if v_op is null then raise exception 'idempotency_retry_unexpected'; end if;
  insert into public.point_balances(user_id,balance_points) values(auth.uid(),0) on conflict do nothing;
  select balance_points into v_balance from public.point_balances where user_id=auth.uid() for update;
  if coalesce(v_balance,0) < v_cost then raise exception 'insufficient_points'; end if;
  if v_p.status='expired' then
    v_after := now()+make_interval(days=>p_days);
    update public.protections set status='active', expires_at=v_after, duration_days=duration_days+p_days, total_points_snapshot=total_points_snapshot+v_cost, updated_at=now() where id=v_p.id returning * into v_p;
    v_anchor := now();
  else
    v_after := v_before+make_interval(days=>p_days);
    update public.protections set expires_at=v_after, duration_days=duration_days+p_days, total_points_snapshot=total_points_snapshot+v_cost, updated_at=now() where id=v_p.id returning * into v_p;
    v_anchor := null;
  end if;
  update public.point_balances set balance_points=balance_points-v_cost, updated_at=now() where user_id=auth.uid();
  insert into public.point_ledger(user_id,entry_type,amount,balance_after,reference_type,reference_id,description,created_by)
    values(auth.uid(),'extension_debit',-v_cost,v_balance-v_cost,'protection',v_p.id,'Protection extension or renewal',auth.uid());
  insert into public.protection_extensions(protection_id,subscriber_id,days_added,points_per_day_snapshot,points_cost,expires_at_before,expires_at_after,operation_id)
    values(v_p.id,v_p.subscriber_id,p_days,v_p.points_per_day_snapshot,v_cost,v_before,v_p.expires_at,v_op);
  update public.operations set status='succeeded', reference_id=v_p.id, points_delta=-v_cost, updated_at=now() where id=v_op;
  perform public.rebuild_task_plan(v_p.id, v_anchor);
  insert into public.notifications(user_id,notification_type,title,content,reference_type,reference_id)
    values(auth.uid(),'system',case when v_before <= now() then 'تم تجديد الحماية' else 'تم تمديد الحماية' end,'تم تحديث مدة الحماية بنجاح.','protection',v_p.id);
  perform public.write_admin_audit('protection',v_p.id,null,to_jsonb(v_p),jsonb_build_object('operation',case when v_before <= now() then 'renewal' else 'extension' end));
  return v_p;
end;
$$;

-- ---------------------------------------------------------------------------
-- Expiration and deterministic task planning.
-- ---------------------------------------------------------------------------

create or replace function public.expire_protections()
returns integer language plpgsql security definer set search_path = public, pg_temp as $$
declare v_count integer;
begin
  update public.protections set status='expired', updated_at=now()
    where status='active' and expires_at <= now();
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

create or replace function public.rebuild_task_plan(p_protection_id uuid, p_anchor_date timestamptz default null)
returns uuid language plpgsql security definer set search_path = public, pg_temp as $$
declare v_protection public.protections; v_settings public.task_settings; v_plan_id uuid; v_anchor timestamptz; v_horizon timestamptz; v_count integer := 0; v_due timestamptz;
begin
  select * into v_protection from public.protections where id=p_protection_id for update;
  if not found then raise exception 'protection_not_found'; end if;
  select * into v_settings from public.task_settings where provider_id=v_protection.provider_id;
  if not found then raise exception 'task_settings_not_found'; end if;
  v_anchor := coalesce(p_anchor_date, (select max(completed_at) from public.payment_tasks where protection_id=p_protection_id and status='completed'), v_protection.started_at);
  v_horizon := v_protection.expires_at;
  if v_settings.allow_post_expiry_creation and v_settings.post_expiry_creation_limit_days is not null then
    v_horizon := v_horizon + make_interval(days=>v_settings.post_expiry_creation_limit_days);
  end if;
  insert into public.protection_task_plans(protection_id,anchor_date,interval_days,planned_until,planned_task_count,version)
    values(v_protection.id,v_anchor,v_settings.interval_days,v_horizon,0,1)
    on conflict (protection_id) do update set anchor_date=excluded.anchor_date, interval_days=excluded.interval_days, planned_until=excluded.planned_until, version=public.protection_task_plans.version+1, last_rebuilt_at=now()
    returning id into v_plan_id;
  update public.payment_tasks set status='cancelled', cancelled_at=now(), cancellation_reason='plan_rebuilt', updated_at=now()
    where plan_id=v_plan_id and status='open' and due_at >= now();
  v_due := v_anchor;
  while v_due < now() loop
    v_due := v_due + make_interval(days=>v_settings.interval_days);
  end loop;
  while v_due < v_horizon loop
    if v_settings.auto_create then
      insert into public.payment_tasks(plan_id,protection_id,phone_number_id,subscriber_id,provider_id,due_at,amount_snapshot,currency_snapshot)
        values(v_plan_id,v_protection.id,v_protection.phone_number_id,v_protection.subscriber_id,v_protection.provider_id,v_due,v_settings.task_amount,v_settings.currency)
        on conflict (plan_id,due_at) do nothing;
      v_count := v_count + 1;
    end if;
    v_due := v_due + make_interval(days=>v_settings.interval_days);
  end loop;
  update public.protection_task_plans set planned_task_count=v_count, planned_until=v_horizon, last_rebuilt_at=now() where id=v_plan_id;
  return v_plan_id;
end;
$$;

-- Visibility is enforced by the authorized read policy, not by Android state.
drop policy if exists tasks_admin_only on public.payment_tasks;
create policy tasks_admin_only on public.payment_tasks for select using (
  public.admin_has_permission('admin_tasks.read')
  and exists (
    select 1 from public.task_settings s
    where s.provider_id = payment_tasks.provider_id
      and payment_tasks.due_at <= now() + make_interval(days => s.visibility_days_before)
  )
);

-- ---------------------------------------------------------------------------
-- Support: RPC-only customer writes, with explicit sender type.
-- ---------------------------------------------------------------------------

revoke insert, update, delete on public.support_threads, public.support_messages from authenticated;

create or replace function public.create_support_thread(p_subject text, p_body text)
returns public.support_threads language plpgsql security definer set search_path = public, pg_temp as $$
declare v_thread public.support_threads;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  if nullif(trim(p_subject),'') is null then raise exception 'support_subject_required'; end if;
  if nullif(trim(p_body),'') is null then raise exception 'support_body_required'; end if;
  insert into public.support_threads(user_id,subject) values(auth.uid(),trim(p_subject)) returning * into v_thread;
  insert into public.support_messages(thread_id,sender_type,sender_id,body) values(v_thread.id,'customer',auth.uid(),trim(p_body));
  return v_thread;
end;
$$;

create or replace function public.send_support_message(p_thread_id uuid, p_body text)
returns public.support_messages language plpgsql security definer set search_path = public, pg_temp as $$
declare v_thread public.support_threads; v_message public.support_messages;
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  if nullif(trim(p_body),'') is null then raise exception 'support_body_required'; end if;
  select * into v_thread from public.support_threads where id=p_thread_id and user_id=auth.uid() and status='open' for update;
  if not found then raise exception 'support_thread_not_owned_or_closed'; end if;
  insert into public.support_messages(thread_id,sender_type,sender_id,body) values(p_thread_id,'customer',auth.uid(),trim(p_body)) returning * into v_message;
  update public.support_threads set last_reply_at=now(), updated_at=now() where id=p_thread_id;
  return v_message;
end;
$$;

create or replace function public.admin_send_support_message(p_thread_id uuid, p_body text)
returns public.support_messages language plpgsql security definer set search_path = public, pg_temp as $$
declare v_thread public.support_threads; v_message public.support_messages;
begin
  perform public.require_admin_permission('admin_support.reply');
  if nullif(trim(p_body),'') is null then raise exception 'support_body_required'; end if;
  select * into v_thread from public.support_threads where id=p_thread_id and status='open' for update;
  if not found then raise exception 'support_thread_not_found_or_closed'; end if;
  insert into public.support_messages(thread_id,sender_type,sender_id,body) values(p_thread_id,'admin',auth.uid(),trim(p_body)) returning * into v_message;
  update public.support_threads set last_reply_at=now(), updated_at=now() where id=p_thread_id;
  return v_message;
end;
$$;

-- Add the permission used by the admin reply RPC.
insert into public.admin_permissions(code,name) values ('admin_support.reply','Reply to support threads') on conflict (code) do nothing;
insert into public.admin_role_permissions(role_id,permission_id)
select r.id,p.id from public.admin_roles r cross join public.admin_permissions p where r.code='admin' and p.code='admin_support.reply' on conflict do nothing;

-- ---------------------------------------------------------------------------
-- Grants: no broad table writes; mutation goes through checked RPCs.
-- ---------------------------------------------------------------------------

revoke all on function public.provision_profile_from_auth() from public, anon, authenticated;
revoke all on function public.write_admin_audit(text,uuid,jsonb,jsonb,jsonb) from public, anon, authenticated;
revoke all on function public.add_customer_number(text), public.update_customer_number(uuid,text), public.archive_customer_number(uuid) from public, anon;
revoke all on function public.create_support_thread(text,text), public.send_support_message(uuid,text), public.admin_send_support_message(uuid,text) from public, anon;
grant execute on function public.add_customer_number(text), public.update_customer_number(uuid,text), public.archive_customer_number(uuid) to authenticated;
grant execute on function public.create_support_thread(text,text), public.send_support_message(uuid,text) to authenticated;
grant execute on function public.admin_send_support_message(uuid,text) to authenticated;
grant execute on function public.submit_points_purchase(uuid,uuid,text,text), public.activate_protection(uuid,integer,text), public.extend_protection(uuid,integer,text) to authenticated;
revoke all on function public.expire_protections() from public, anon, authenticated;

-- Canonical financial writes are RPC-only.
revoke insert, update, delete on public.financial_ledger from authenticated;
revoke insert, update, delete on public.point_ledger from authenticated;
revoke insert, update, delete on public.protections, public.protection_extensions from authenticated;

commit;
