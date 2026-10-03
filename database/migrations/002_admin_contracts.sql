-- AMAN | أمان
-- Phase 2 support: permission-scoped Admin data and atomic operations.
-- Additive migration. Review and apply only in the separately authorized database phase.
-- This file has not been executed against any database.

begin;

-- Fine-grained permissions; the seeded super_admin role receives these all.
insert into public.admin_permissions(code, name) values
  ('admin_dashboard.read', 'Read Admin dashboard'),
  ('admin_users.read', 'Read user profiles'),
  ('admin_users.update', 'Update user profiles and account status'),
  ('admin_subscribers.read', 'Read subscribers'),
  ('admin_subscribers.update', 'Update subscriber status'),
  ('admin_numbers.read', 'Read customer numbers'),
  ('admin_numbers.update', 'Update or archive customer-number relations'),
  ('admin_protections.read', 'Read protections'),
  ('admin_points.read', 'Read point balances and ledger'),
  ('admin_purchases.read', 'Read purchase requests'),
  ('admin_purchases.approve', 'Approve purchase requests'),
  ('admin_purchases.reject', 'Reject purchase requests'),
  ('admin_tasks.read', 'Read operational tasks and plans'),
  ('admin_tasks.execute', 'Record an externally completed task payment'),
  ('admin_tasks.reschedule', 'Reschedule an open operational task'),
  ('admin_tasks.cancel', 'Cancel an open operational task'),
  ('admin_tasks.settings', 'Read and update task settings'),
  ('admin_providers.read', 'Read providers, prefixes and tariffs'),
  ('admin_providers.manage', 'Manage providers, prefixes and tariffs'),
  ('admin_packages.read', 'Read point packages'),
  ('admin_packages.manage', 'Manage point packages'),
  ('admin_payment_methods.read', 'Read payment methods'),
  ('admin_payment_methods.manage', 'Manage payment methods'),
  ('admin_notifications.read', 'Read administrative notification history'),
  ('admin_notifications.send', 'Send administrative notifications'),
  ('admin_operations.read', 'Read operational activity'),
  ('admin_reports.read', 'Read reports and financial ledger'),
  ('admin_reports.export', 'Export authorized reports'),
  ('admin_audit.read', 'Read audit history'),
  ('admin_account.read', 'Read own Admin account context')
on conflict (code) do update set name = excluded.name;

insert into public.admin_role_permissions(role_id, permission_id)
select r.id, p.id
from public.admin_roles r cross join public.admin_permissions p
where r.code = 'super_admin'
on conflict do nothing;

create or replace function public.admin_has_permission(p_permission_code text)
returns boolean
language sql stable security definer
set search_path = public, pg_temp
as $$
  select auth.uid() is not null and exists (
    select 1
    from public.admin_user_roles ur
    join public.admin_roles r on r.id = ur.role_id and r.status = 'active'
    join public.admin_role_permissions rp on rp.role_id = r.id
    join public.admin_permissions p on p.id = rp.permission_id
    where ur.user_id = auth.uid() and p.code = p_permission_code
  );
$$;
revoke all on function public.admin_has_permission(text) from public, anon;
grant execute on function public.admin_has_permission(text) to authenticated;

create or replace function public.require_admin_permission(p_permission_code text)
returns void language plpgsql security definer
set search_path = public, pg_temp
as $$
begin
  if auth.uid() is null or not public.admin_has_permission(p_permission_code) then
    raise exception 'admin_permission_required:%', p_permission_code using errcode = '42501';
  end if;
end;
$$;
revoke all on function public.require_admin_permission(text) from public, anon, authenticated;

create or replace function public.write_admin_audit(
  p_entity_type text, p_entity_id uuid, p_before jsonb, p_after jsonb, p_metadata jsonb default '{}'::jsonb
) returns void language plpgsql security definer
set search_path = public, pg_temp
as $$
begin
  insert into public.audit_logs(actor_id, action, entity_type, entity_id, before_data, after_data, metadata)
  values (auth.uid(), 'rpc', p_entity_type, p_entity_id, p_before, p_after,
          coalesce(p_metadata, '{}'::jsonb) || jsonb_build_object(
            'actor_roles', coalesce((select jsonb_agg(r.code order by r.code)
              from public.admin_user_roles ur join public.admin_roles r on r.id = ur.role_id
              where ur.user_id = auth.uid() and r.status = 'active'), '[]'::jsonb)));
end;
$$;
revoke all on function public.write_admin_audit(text, uuid, jsonb, jsonb, jsonb) from public, anon, authenticated;

-- Permission-filtered reads. Customer self-access remains intact; Admin access is no longer
-- granted to every active role merely because it passes is_admin().
drop policy if exists profiles_self_select on public.profiles;
create policy profiles_self_select on public.profiles for select
  using (id = auth.uid() or public.admin_has_permission('admin_users.read'));
drop policy if exists profiles_self_update on public.profiles;
create policy profiles_self_update on public.profiles for update
  using (id = auth.uid()) with check (id = auth.uid());
revoke update on public.profiles from authenticated;
grant update (full_name, username, phone) on public.profiles to authenticated;

drop policy if exists subscribers_self_select on public.subscribers;
create policy subscribers_self_select on public.subscribers for select
  using (user_id = auth.uid() or public.admin_has_permission('admin_subscribers.read'));
drop policy if exists numbers_self_select on public.customer_numbers;
create policy numbers_self_select on public.customer_numbers for select
  using (user_id = auth.uid() or public.admin_has_permission('admin_numbers.read'));
drop policy if exists phone_self_select on public.phone_numbers;
create policy phone_self_select on public.phone_numbers for select
  using (exists (select 1 from public.customer_numbers c where c.phone_number_id = id and c.user_id = auth.uid())
         or public.admin_has_permission('admin_numbers.read'));
drop policy if exists packages_read_active on public.points_packages;
create policy packages_read_active on public.points_packages for select
  using (status = 'active' or public.admin_has_permission('admin_packages.read'));
drop policy if exists methods_read_active on public.payment_methods;
create policy methods_read_active on public.payment_methods for select
  using (status = 'active' or public.admin_has_permission('admin_payment_methods.read'));
drop policy if exists balances_self_select on public.point_balances;
create policy balances_self_select on public.point_balances for select
  using (user_id = auth.uid() or public.admin_has_permission('admin_points.read'));
drop policy if exists purchases_self_select on public.points_purchase_requests;
create policy purchases_self_select on public.points_purchase_requests for select
  using (user_id = auth.uid() or public.admin_has_permission('admin_purchases.read'));
drop policy if exists ledger_self_select on public.point_ledger;
create policy ledger_self_select on public.point_ledger for select
  using (user_id = auth.uid() or public.admin_has_permission('admin_points.read'));
drop policy if exists protections_self_select on public.protections;
create policy protections_self_select on public.protections for select
  using (user_id = auth.uid() or public.admin_has_permission('admin_protections.read'));
drop policy if exists extensions_self_select on public.protection_extensions;
create policy extensions_self_select on public.protection_extensions for select
  using (subscriber_id in (select id from public.subscribers where user_id = auth.uid())
         or public.admin_has_permission('admin_protections.read'));
drop policy if exists plans_self_select on public.protection_task_plans;
create policy plans_self_select on public.protection_task_plans for select
  using (protection_id in (select id from public.protections where user_id = auth.uid())
         or public.admin_has_permission('admin_tasks.read')
         or public.admin_has_permission('admin_protections.read'));
drop policy if exists tasks_admin_only on public.payment_tasks;
create policy tasks_admin_only on public.payment_tasks for select
  using (public.admin_has_permission('admin_tasks.read'));
drop policy if exists operations_self_select on public.operations;
create policy operations_self_select on public.operations for select
  using (user_id = auth.uid() or public.admin_has_permission('admin_operations.read')
         or public.admin_has_permission('admin_reports.read'));
drop policy if exists notifications_self_select on public.notifications;
create policy notifications_self_select on public.notifications for select
  using (user_id = auth.uid() or public.admin_has_permission('admin_notifications.read'));
drop policy if exists notifications_self_update on public.notifications;
create policy notifications_self_update on public.notifications for update
  using (user_id = auth.uid()) with check (user_id = auth.uid());
revoke update on public.notifications from authenticated;
grant update (read_at) on public.notifications to authenticated;
drop policy if exists audit_admin_only on public.audit_logs;
create policy audit_admin_only on public.audit_logs for select
  using (public.admin_has_permission('admin_audit.read'));

-- Admin-only operational/support tables are explicitly protected before granting reads.
alter table public.telecom_providers enable row level security;
alter table public.telecom_prefixes enable row level security;
alter table public.provider_tariffs enable row level security;
alter table public.task_settings enable row level security;
alter table public.financial_ledger enable row level security;
create policy telecom_providers_read on public.telecom_providers for select
  using (status = 'active' or public.admin_has_permission('admin_providers.read'));
create policy telecom_prefixes_read on public.telecom_prefixes for select
  using ((status = 'active' and exists (select 1 from public.telecom_providers p where p.id = provider_id and p.status = 'active'))
         or public.admin_has_permission('admin_providers.read'));
create policy provider_tariffs_read on public.provider_tariffs for select
  using ((status = 'active' and effective_from <= now() and (effective_to is null or effective_to > now()))
         or public.admin_has_permission('admin_providers.read'));
create policy task_settings_admin_read on public.task_settings for select
  using (public.admin_has_permission('admin_tasks.settings'));
create policy financial_ledger_admin_read on public.financial_ledger for select
  using (public.admin_has_permission('admin_reports.read'));

grant select on public.telecom_providers, public.telecom_prefixes, public.provider_tariffs,
  public.task_settings, public.payment_tasks, public.financial_ledger to authenticated;

-- Direct table writes cannot impersonate an Admin. Sensitive changes use audited RPCs below.
create table if not exists public.admin_notifications (
  id uuid primary key default gen_random_uuid(),
  sender_admin_id uuid not null references public.profiles(id) on delete restrict,
  target_type text not null check (target_type in ('all','subscriber','user')),
  target_id uuid,
  title text not null check (length(trim(title)) between 1 and 160),
  body text not null check (length(trim(body)) between 1 and 4000),
  status text not null default 'sent' check (status in ('sent','failed')),
  recipient_count integer not null default 0 check (recipient_count >= 0),
  sent_at timestamptz,
  created_at timestamptz not null default now(),
  check ((target_type = 'all' and target_id is null) or (target_type <> 'all' and target_id is not null))
);
create index if not exists idx_admin_notifications_created on public.admin_notifications(created_at desc);
alter table public.admin_notifications enable row level security;
create policy admin_notifications_read on public.admin_notifications for select
  using (public.admin_has_permission('admin_notifications.read'));
grant select on public.admin_notifications to authenticated;

-- The existing schema already records task history. These links make rescheduling explicit,
-- and a mandatory external reference distinguishes recording payment from executing it.
alter table public.payment_tasks add column if not exists rescheduled_to uuid;
alter table public.payment_tasks add column if not exists external_payment_reference text;
do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'payment_tasks_rescheduled_to_fkey') then
    alter table public.payment_tasks add constraint payment_tasks_rescheduled_to_fkey
      foreign key (rescheduled_to) references public.payment_tasks(id) on delete set null;
  end if;
end $$;

-- Preserve an existing task-plan anchor when rebuilding future tasks after an operational event.
create or replace function public.rebuild_task_plan(p_protection_id uuid, p_anchor_date timestamptz default null)
returns uuid language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_protection public.protections;
  v_settings public.task_settings;
  v_plan_id uuid;
  v_anchor timestamptz;
  v_count integer := 0;
  v_due timestamptz;
begin
  select * into v_protection from public.protections where id = p_protection_id for update;
  if not found then raise exception 'protection_not_found'; end if;
  select * into v_settings from public.task_settings where provider_id = v_protection.provider_id;
  if not found then raise exception 'task_settings_not_found'; end if;
  select id, anchor_date into v_plan_id, v_anchor from public.protection_task_plans where protection_id = p_protection_id for update;
  v_anchor := coalesce(p_anchor_date, v_anchor, v_protection.started_at);
  insert into public.protection_task_plans(protection_id, anchor_date, interval_days, planned_until, planned_task_count, version)
  values (v_protection.id, v_anchor, v_settings.interval_days, v_protection.expires_at, 0, 1)
  on conflict (protection_id) do update set anchor_date = excluded.anchor_date,
    interval_days = excluded.interval_days, planned_until = excluded.planned_until,
    version = public.protection_task_plans.version + 1, last_rebuilt_at = now()
  returning id into v_plan_id;
  delete from public.payment_tasks where plan_id = v_plan_id and status = 'open';
  v_due := v_anchor;
  while v_due < v_protection.expires_at loop
    if v_settings.auto_create and v_due >= now() then
      insert into public.payment_tasks(plan_id, protection_id, phone_number_id, subscriber_id, provider_id,
        due_at, amount_snapshot, currency_snapshot)
      values (v_plan_id, v_protection.id, v_protection.phone_number_id, v_protection.subscriber_id,
        v_protection.provider_id, v_due, v_settings.task_amount, v_settings.currency)
      on conflict (plan_id, due_at) do nothing;
      v_count := v_count + 1;
    end if;
    v_due := v_due + make_interval(days => v_settings.interval_days);
  end loop;
  update public.protection_task_plans set planned_task_count = v_count, planned_until = v_protection.expires_at,
    last_rebuilt_at = now() where id = v_plan_id;
  return v_plan_id;
end;
$$;

create or replace function public.approve_points_purchase(p_request_id uuid, p_idempotency_key text)
returns public.points_purchase_requests language plpgsql security definer set search_path = public, pg_temp as $$
declare v_req public.points_purchase_requests; v_balance bigint;
begin
  perform public.require_admin_permission('admin_purchases.approve');
  if nullif(trim(p_idempotency_key), '') is null then raise exception 'idempotency_key_required'; end if;
  select * into v_req from public.points_purchase_requests where id = p_request_id for update;
  if not found then raise exception 'request_not_found'; end if;
  if v_req.status <> 'pending' then return v_req; end if;
  insert into public.point_balances(user_id, balance_points) values (v_req.user_id, 0) on conflict do nothing;
  select balance_points into v_balance from public.point_balances where user_id = v_req.user_id for update;
  v_balance := v_balance + v_req.points_amount_snapshot;
  update public.point_balances set balance_points = v_balance, updated_at = now() where user_id = v_req.user_id;
  insert into public.point_ledger(user_id, entry_type, amount, balance_after, reference_type, reference_id, description, created_by)
    values (v_req.user_id, 'purchase_credit', v_req.points_amount_snapshot, v_balance, 'points_purchase_request', v_req.id, 'Approved points purchase', auth.uid());
  update public.points_purchase_requests set status = 'approved', reviewed_by = auth.uid(), reviewed_at = now(), updated_at = now()
    where id = v_req.id returning * into v_req;
  insert into public.financial_ledger(entry_type, amount, currency, reference_type, reference_id, description, created_by)
    values ('points_purchase', v_req.price_amount_snapshot, v_req.currency_snapshot, 'points_purchase_request', v_req.id, 'Approved points purchase', auth.uid());
  insert into public.operations(user_id, operation_type, status, reference_type, reference_id, points_delta, money_amount, idempotency_key)
    values (v_req.user_id, 'points_approval', 'succeeded', 'points_purchase_request', v_req.id,
      v_req.points_amount_snapshot, v_req.price_amount_snapshot, p_idempotency_key);
  insert into public.notifications(user_id, notification_type, title, content, reference_type, reference_id)
    values (v_req.user_id, 'system', 'تم اعتماد شراء النقاط', 'تمت إضافة النقاط إلى رصيدك.', 'points_purchase_request', v_req.id);
  perform public.write_admin_audit('points_purchase_request', v_req.id, null, to_jsonb(v_req), jsonb_build_object('decision','approved'));
  return v_req;
end;
$$;

create or replace function public.reject_points_purchase(p_request_id uuid, p_reason text, p_idempotency_key text)
returns public.points_purchase_requests language plpgsql security definer set search_path = public, pg_temp as $$
declare v_req public.points_purchase_requests;
begin
  perform public.require_admin_permission('admin_purchases.reject');
  if nullif(trim(p_reason), '') is null then raise exception 'rejection_reason_required'; end if;
  if nullif(trim(p_idempotency_key), '') is null then raise exception 'idempotency_key_required'; end if;
  select * into v_req from public.points_purchase_requests where id = p_request_id for update;
  if not found then raise exception 'request_not_found'; end if;
  if v_req.status <> 'pending' then raise exception 'purchase_not_pending'; end if;
  update public.points_purchase_requests set status = 'rejected', rejection_reason = trim(p_reason),
    reviewed_by = auth.uid(), reviewed_at = now(), updated_at = now()
    where id = p_request_id returning * into v_req;
  insert into public.operations(user_id, operation_type, status, reference_type, reference_id, points_delta,
    money_amount, metadata, idempotency_key)
    values (v_req.user_id, 'points_rejection', 'succeeded', 'points_purchase_request', v_req.id, 0,
      0, jsonb_build_object('reason', trim(p_reason)), p_idempotency_key);
  insert into public.notifications(user_id, notification_type, title, content, reference_type, reference_id)
    values (v_req.user_id, 'system', 'تم رفض طلب شراء النقاط', trim(p_reason), 'points_purchase_request', v_req.id);
  perform public.write_admin_audit('points_purchase_request', v_req.id, null, to_jsonb(v_req), jsonb_build_object('decision','rejected'));
  return v_req;
end;
$$;

create or replace function public.admin_update_profile(
  p_user_id uuid, p_full_name text, p_username text, p_phone text, p_status public.account_status
) returns public.profiles language plpgsql security definer set search_path = public, pg_temp as $$
declare v_before public.profiles; v_after public.profiles;
begin
  perform public.require_admin_permission('admin_users.update');
  select * into v_before from public.profiles where id = p_user_id for update;
  if not found then raise exception 'profile_not_found'; end if;
  update public.profiles set full_name = nullif(trim(p_full_name), ''), username = nullif(trim(p_username), ''),
    phone = nullif(trim(p_phone), ''), account_status = p_status, updated_at = now()
    where id = p_user_id returning * into v_after;
  perform public.write_admin_audit('profile', p_user_id, to_jsonb(v_before), to_jsonb(v_after), jsonb_build_object('operation','admin_update_profile'));
  return v_after;
end;
$$;

create or replace function public.admin_set_subscriber_status(p_subscriber_id uuid, p_status public.record_status)
returns public.subscribers language plpgsql security definer set search_path = public, pg_temp as $$
declare v_before public.subscribers; v_after public.subscribers;
begin
  perform public.require_admin_permission('admin_subscribers.update');
  select * into v_before from public.subscribers where id = p_subscriber_id for update;
  if not found then raise exception 'subscriber_not_found'; end if;
  update public.subscribers set status = p_status, updated_at = now() where id = p_subscriber_id returning * into v_after;
  perform public.write_admin_audit('subscriber', p_subscriber_id, to_jsonb(v_before), to_jsonb(v_after));
  return v_after;
end;
$$;

create or replace function public.admin_set_customer_number_status(p_customer_number_id uuid, p_status public.record_status)
returns public.customer_numbers language plpgsql security definer set search_path = public, pg_temp as $$
declare v_before public.customer_numbers; v_after public.customer_numbers;
begin
  perform public.require_admin_permission('admin_numbers.update');
  select * into v_before from public.customer_numbers where id = p_customer_number_id for update;
  if not found then raise exception 'customer_number_not_found'; end if;
  update public.customer_numbers set status = p_status where id = p_customer_number_id returning * into v_after;
  perform public.write_admin_audit('customer_number', p_customer_number_id, to_jsonb(v_before), to_jsonb(v_after));
  return v_after;
end;
$$;

create or replace function public.admin_save_provider(
  p_id uuid, p_code text, p_name text, p_short_name text, p_status public.record_status, p_operational_settings jsonb
) returns public.telecom_providers language plpgsql security definer set search_path = public, pg_temp as $$
declare v_before public.telecom_providers; v_after public.telecom_providers;
begin
  perform public.require_admin_permission('admin_providers.manage');
  if nullif(trim(p_code), '') is null or nullif(trim(p_name), '') is null then raise exception 'provider_code_and_name_required'; end if;
  if p_operational_settings is not null and jsonb_typeof(p_operational_settings) <> 'object' then raise exception 'settings_must_be_object'; end if;
  if p_id is null then
    insert into public.telecom_providers(code, name, short_name, status, operational_settings)
      values (trim(p_code), trim(p_name), nullif(trim(p_short_name), ''), p_status, coalesce(p_operational_settings, '{}'::jsonb)) returning * into v_after;
  else
    select * into v_before from public.telecom_providers where id = p_id for update;
    if not found then raise exception 'provider_not_found'; end if;
    update public.telecom_providers set code = trim(p_code), name = trim(p_name), short_name = nullif(trim(p_short_name), ''),
      status = p_status, operational_settings = coalesce(p_operational_settings, '{}'::jsonb), updated_at = now()
      where id = p_id returning * into v_after;
  end if;
  perform public.write_admin_audit('telecom_provider', v_after.id, to_jsonb(v_before), to_jsonb(v_after));
  return v_after;
end;
$$;

create or replace function public.admin_save_points_package(
  p_id uuid, p_name text, p_points_amount integer, p_price_amount numeric, p_currency text, p_display_order integer, p_status public.record_status
) returns public.points_packages language plpgsql security definer set search_path = public, pg_temp as $$
declare v_before public.points_packages; v_after public.points_packages;
begin
  perform public.require_admin_permission('admin_packages.manage');
  if nullif(trim(p_name), '') is null or p_points_amount <= 0 or p_price_amount < 0 or nullif(trim(p_currency), '') is null then
    raise exception 'invalid_points_package';
  end if;
  if p_id is null then
    insert into public.points_packages(name, points_amount, price_amount, currency, display_order, status)
      values (trim(p_name), p_points_amount, p_price_amount, upper(trim(p_currency)), p_display_order, p_status) returning * into v_after;
  else
    select * into v_before from public.points_packages where id = p_id for update;
    if not found then raise exception 'package_not_found'; end if;
    update public.points_packages set name = trim(p_name), points_amount = p_points_amount, price_amount = p_price_amount,
      currency = upper(trim(p_currency)), display_order = p_display_order, status = p_status, updated_at = now()
      where id = p_id returning * into v_after;
  end if;
  perform public.write_admin_audit('points_package', v_after.id, to_jsonb(v_before), to_jsonb(v_after));
  return v_after;
end;
$$;

create or replace function public.admin_save_payment_method(
  p_id uuid, p_name text, p_payment_data jsonb, p_instructions text, p_status public.record_status
) returns public.payment_methods language plpgsql security definer set search_path = public, pg_temp as $$
declare v_before public.payment_methods; v_after public.payment_methods;
begin
  perform public.require_admin_permission('admin_payment_methods.manage');
  if nullif(trim(p_name), '') is null or (p_payment_data is not null and jsonb_typeof(p_payment_data) <> 'object') then
    raise exception 'invalid_payment_method';
  end if;
  if p_id is null then
    insert into public.payment_methods(name, payment_data, instructions, status)
      values (trim(p_name), coalesce(p_payment_data, '{}'::jsonb), nullif(trim(p_instructions), ''), p_status) returning * into v_after;
  else
    select * into v_before from public.payment_methods where id = p_id for update;
    if not found then raise exception 'payment_method_not_found'; end if;
    update public.payment_methods set name = trim(p_name), payment_data = coalesce(p_payment_data, '{}'::jsonb),
      instructions = nullif(trim(p_instructions), ''), status = p_status, updated_at = now()
      where id = p_id returning * into v_after;
  end if;
  perform public.write_admin_audit('payment_method', v_after.id, to_jsonb(v_before), to_jsonb(v_after));
  return v_after;
end;
$$;

create or replace function public.admin_save_task_settings(
  p_provider_id uuid, p_interval_days integer, p_task_amount numeric, p_currency text,
  p_visibility_days_before integer, p_auto_create boolean, p_allow_reschedule boolean,
  p_allow_post_expiry_creation boolean, p_post_expiry_creation_limit_days integer
) returns public.task_settings language plpgsql security definer set search_path = public, pg_temp as $$
declare v_before public.task_settings; v_after public.task_settings; v_protection_id uuid;
begin
  perform public.require_admin_permission('admin_tasks.settings');
  if p_interval_days <= 0 or p_task_amount < 0 or p_visibility_days_before < 0
    or (p_post_expiry_creation_limit_days is not null and p_post_expiry_creation_limit_days < 0)
    or nullif(trim(p_currency), '') is null then raise exception 'invalid_task_settings'; end if;
  select * into v_before from public.task_settings where provider_id = p_provider_id for update;
  insert into public.task_settings(provider_id, interval_days, task_amount, currency, visibility_days_before,
    auto_create, allow_reschedule, allow_post_expiry_creation, post_expiry_creation_limit_days, updated_at)
    values (p_provider_id, p_interval_days, p_task_amount, upper(trim(p_currency)), p_visibility_days_before,
      p_auto_create, p_allow_reschedule, p_allow_post_expiry_creation, p_post_expiry_creation_limit_days, now())
    on conflict (provider_id) do update set interval_days = excluded.interval_days, task_amount = excluded.task_amount,
      currency = excluded.currency, visibility_days_before = excluded.visibility_days_before,
      auto_create = excluded.auto_create, allow_reschedule = excluded.allow_reschedule,
      allow_post_expiry_creation = excluded.allow_post_expiry_creation,
      post_expiry_creation_limit_days = excluded.post_expiry_creation_limit_days, updated_at = now()
    returning * into v_after;
  for v_protection_id in select id from public.protections where provider_id = p_provider_id and status = 'active' loop
    perform public.rebuild_task_plan(v_protection_id, null);
  end loop;
  perform public.write_admin_audit('task_settings', v_after.id, to_jsonb(v_before), to_jsonb(v_after), jsonb_build_object('future_plans_rebuilt', true));
  return v_after;
end;
$$;

create or replace function public.cancel_payment_task(p_task_id uuid, p_reason text)
returns public.payment_tasks language plpgsql security definer set search_path = public, pg_temp as $$
declare v_before public.payment_tasks; v_after public.payment_tasks;
begin
  perform public.require_admin_permission('admin_tasks.cancel');
  if nullif(trim(p_reason), '') is null then raise exception 'cancellation_reason_required'; end if;
  select * into v_before from public.payment_tasks where id = p_task_id for update;
  if not found then raise exception 'task_not_found'; end if;
  if v_before.status <> 'open' then raise exception 'task_not_open'; end if;
  update public.payment_tasks set status = 'cancelled', cancelled_at = now(), cancellation_reason = trim(p_reason), updated_at = now()
    where id = p_task_id returning * into v_after;
  perform public.write_admin_audit('payment_task', p_task_id, to_jsonb(v_before), to_jsonb(v_after), jsonb_build_object('operation','cancel'));
  return v_after;
end;
$$;

create or replace function public.reschedule_payment_task(p_task_id uuid, p_new_due_at timestamptz, p_idempotency_key text)
returns public.payment_tasks language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_before public.payment_tasks; v_after public.payment_tasks; v_settings public.task_settings;
  v_protection public.protections; v_due timestamptz; v_plan_interval integer; v_task_count integer;
begin
  perform public.require_admin_permission('admin_tasks.reschedule');
  if p_new_due_at is null or p_new_due_at <= now() then raise exception 'new_due_date_must_be_future'; end if;
  if nullif(trim(p_idempotency_key), '') is null then raise exception 'idempotency_key_required'; end if;
  select * into v_before from public.payment_tasks where id = p_task_id for update;
  if not found then raise exception 'task_not_found'; end if;
  if v_before.status <> 'open' then
    if v_before.rescheduled_to is not null then
      select * into v_after from public.payment_tasks where id = v_before.rescheduled_to;
      return v_after;
    end if;
    raise exception 'task_not_open';
  end if;
  select * into v_protection from public.protections where id = v_before.protection_id for update;
  select * into v_settings from public.task_settings where provider_id = v_before.provider_id;
  if not found or not v_settings.allow_reschedule then raise exception 'reschedule_not_allowed'; end if;
  if p_new_due_at >= v_protection.expires_at then raise exception 'new_due_date_after_protection_expiry'; end if;
  v_plan_interval := v_settings.interval_days;
  update public.payment_tasks set status = 'cancelled', cancelled_at = now(), cancellation_reason = 'rescheduled', updated_at = now()
    where id = p_task_id;
  delete from public.payment_tasks where plan_id = v_before.plan_id and status = 'open'
    and due_at >= p_new_due_at and id <> p_task_id;
  insert into public.payment_tasks(plan_id, protection_id, phone_number_id, subscriber_id, provider_id, due_at,
    amount_snapshot, currency_snapshot, rescheduled_from, rescheduled_at)
    values (v_before.plan_id, v_before.protection_id, v_before.phone_number_id, v_before.subscriber_id,
      v_before.provider_id, p_new_due_at, v_settings.task_amount, v_settings.currency, v_before.id, now())
    returning * into v_after;
  update public.payment_tasks set rescheduled_to = v_after.id where id = p_task_id;
  update public.protection_task_plans set anchor_date = p_new_due_at, interval_days = v_plan_interval,
    version = version + 1, last_rebuilt_at = now() where id = v_after.plan_id;
  v_due := p_new_due_at + make_interval(days => v_plan_interval);
  while v_due < v_protection.expires_at loop
    insert into public.payment_tasks(plan_id, protection_id, phone_number_id, subscriber_id, provider_id, due_at,
      amount_snapshot, currency_snapshot)
      values (v_after.plan_id, v_protection.id, v_protection.phone_number_id, v_protection.subscriber_id,
        v_protection.provider_id, v_due, v_settings.task_amount, v_settings.currency)
      on conflict (plan_id, due_at) do nothing;
    v_due := v_due + make_interval(days => v_plan_interval);
  end loop;
  select count(*) into v_task_count from public.payment_tasks where plan_id = v_after.plan_id and status = 'open';
  update public.protection_task_plans set planned_task_count = v_task_count, planned_until = v_protection.expires_at,
    last_rebuilt_at = now() where id = v_after.plan_id;
  insert into public.operations(user_id, operation_type, status, reference_type, reference_id, phone_number_id, metadata, idempotency_key)
    values (null, 'payment_task', 'succeeded', 'payment_task', v_after.id, v_after.phone_number_id,
      jsonb_build_object('operation','reschedule','previous_task_id',p_task_id,'new_due_at',p_new_due_at), p_idempotency_key);
  perform public.write_admin_audit('payment_task', p_task_id, to_jsonb(v_before), to_jsonb(v_after), jsonb_build_object('operation','reschedule'));
  return v_after;
end;
$$;

-- Replace the unsafe two-argument RPC with a three-argument function requiring external evidence.
revoke execute on function public.execute_payment_task(uuid, text) from public, anon, authenticated;
create or replace function public.execute_payment_task(p_task_id uuid, p_execution_key text, p_external_reference text)
returns public.payment_tasks language plpgsql security definer set search_path = public, pg_temp as $$
declare v_before public.payment_tasks; v_after public.payment_tasks;
begin
  perform public.require_admin_permission('admin_tasks.execute');
  if nullif(trim(p_external_reference), '') is null then raise exception 'external_payment_reference_required'; end if;
  if nullif(trim(p_execution_key), '') is null then raise exception 'execution_key_required'; end if;
  select * into v_before from public.payment_tasks where id = p_task_id for update;
  if not found then raise exception 'task_not_found'; end if;
  if v_before.status = 'completed' then return v_before; end if;
  if v_before.status <> 'open' then raise exception 'task_not_open'; end if;
  update public.payment_tasks set status = 'completed', completed_at = now(), completed_by = auth.uid(),
    execution_key = p_execution_key, external_payment_reference = trim(p_external_reference), updated_at = now()
    where id = p_task_id returning * into v_after;
  insert into public.financial_ledger(entry_type, amount, currency, reference_type, reference_id, description, created_by)
    values ('payment_task', v_after.amount_snapshot, v_after.currency_snapshot, 'payment_task', v_after.id,
      'Externally completed payment task: ' || trim(p_external_reference), auth.uid());
  perform public.write_admin_audit('payment_task', p_task_id, to_jsonb(v_before), to_jsonb(v_after));
  perform public.rebuild_task_plan(v_after.protection_id, now());
  return v_after;
end;
$$;

create or replace function public.send_admin_notification(p_target_type text, p_target_id uuid, p_title text, p_body text)
returns public.admin_notifications language plpgsql security definer set search_path = public, pg_temp as $$
declare v_row public.admin_notifications; v_count integer;
begin
  perform public.require_admin_permission('admin_notifications.send');
  if p_target_type not in ('all','subscriber','user') then raise exception 'invalid_notification_target'; end if;
  if nullif(trim(p_title), '') is null or length(trim(p_title)) > 160 then raise exception 'invalid_notification_title'; end if;
  if nullif(trim(p_body), '') is null or length(trim(p_body)) > 4000 then raise exception 'invalid_notification_body'; end if;
  if p_target_type = 'all' and p_target_id is not null then raise exception 'all_target_must_not_have_id'; end if;
  if p_target_type <> 'all' and p_target_id is null then raise exception 'target_id_required'; end if;
  insert into public.admin_notifications(sender_admin_id, target_type, target_id, title, body, status, sent_at)
    values (auth.uid(), p_target_type, p_target_id, trim(p_title), trim(p_body), 'sent', now()) returning * into v_row;
  if p_target_type = 'user' then
    insert into public.notifications(user_id, notification_type, title, content, reference_type, reference_id)
      select p.id, 'admin_alert', v_row.title, v_row.body, 'admin_notification', v_row.id
      from public.profiles p where p.id = p_target_id and p.account_status = 'active';
  elsif p_target_type = 'subscriber' then
    insert into public.notifications(user_id, notification_type, title, content, reference_type, reference_id)
      select s.user_id, 'admin_alert', v_row.title, v_row.body, 'admin_notification', v_row.id
      from public.subscribers s join public.profiles p on p.id = s.user_id
      where s.id = p_target_id and s.status = 'active' and p.account_status = 'active';
  else
    insert into public.notifications(user_id, notification_type, title, content, reference_type, reference_id)
      select p.id, 'admin_alert', v_row.title, v_row.body, 'admin_notification', v_row.id
      from public.profiles p where p.account_status = 'active';
  end if;
  get diagnostics v_count = row_count;
  if v_count = 0 then raise exception 'notification_target_has_no_active_recipients'; end if;
  update public.admin_notifications set recipient_count = v_count where id = v_row.id returning * into v_row;
  perform public.write_admin_audit('admin_notification', v_row.id, null, to_jsonb(v_row));
  return v_row;
end;
$$;

create or replace function public.admin_account_info()
returns jsonb language plpgsql stable security definer set search_path = public, pg_temp as $$
declare v_result jsonb;
begin
  perform public.require_admin_permission('admin_account.read');
  select jsonb_build_object(
    'profile', to_jsonb(p),
    'roles', coalesce((select jsonb_agg(jsonb_build_object('code', r.code, 'name', r.name) order by r.name)
      from public.admin_user_roles ur join public.admin_roles r on r.id = ur.role_id
      where ur.user_id = auth.uid() and r.status = 'active'), '[]'::jsonb),
    'permissions', coalesce((select jsonb_agg(distinct perm.code order by perm.code)
      from public.admin_user_roles ur join public.admin_roles r on r.id = ur.role_id and r.status = 'active'
      join public.admin_role_permissions rp on rp.role_id = r.id join public.admin_permissions perm on perm.id = rp.permission_id
      where ur.user_id = auth.uid()), '[]'::jsonb),
    'system_version', '1.0.0'
  ) into v_result from public.profiles p where p.id = auth.uid();
  if v_result is null then raise exception 'admin_profile_not_found'; end if;
  return v_result;
end;
$$;

-- Expose only the RPCs intended for authenticated callers. All functions re-check permission.
revoke all on function public.approve_points_purchase(uuid, text) from public, anon;
revoke all on function public.reject_points_purchase(uuid, text, text) from public, anon;
revoke all on function public.admin_update_profile(uuid, text, text, text, public.account_status) from public, anon;
revoke all on function public.admin_set_subscriber_status(uuid, public.record_status) from public, anon;
revoke all on function public.admin_set_customer_number_status(uuid, public.record_status) from public, anon;
revoke all on function public.admin_save_provider(uuid, text, text, text, public.record_status, jsonb) from public, anon;
revoke all on function public.admin_save_points_package(uuid, text, integer, numeric, text, integer, public.record_status) from public, anon;
revoke all on function public.admin_save_payment_method(uuid, text, jsonb, text, public.record_status) from public, anon;
revoke all on function public.admin_save_task_settings(uuid, integer, numeric, text, integer, boolean, boolean, boolean, integer) from public, anon;
revoke all on function public.cancel_payment_task(uuid, text) from public, anon;
revoke all on function public.reschedule_payment_task(uuid, timestamptz, text) from public, anon;
revoke all on function public.execute_payment_task(uuid, text, text) from public, anon;
revoke all on function public.send_admin_notification(text, uuid, text, text) from public, anon;
revoke all on function public.admin_account_info() from public, anon;
revoke all on function public.rebuild_task_plan(uuid, timestamptz) from public, anon, authenticated;
grant execute on function public.approve_points_purchase(uuid, text),
  public.reject_points_purchase(uuid, text, text),
  public.admin_update_profile(uuid, text, text, text, public.account_status),
  public.admin_set_subscriber_status(uuid, public.record_status),
  public.admin_set_customer_number_status(uuid, public.record_status),
  public.admin_save_provider(uuid, text, text, text, public.record_status, jsonb),
  public.admin_save_points_package(uuid, text, integer, numeric, text, integer, public.record_status),
  public.admin_save_payment_method(uuid, text, jsonb, text, public.record_status),
  public.admin_save_task_settings(uuid, integer, numeric, text, integer, boolean, boolean, boolean, integer),
  public.cancel_payment_task(uuid, text), public.reschedule_payment_task(uuid, timestamptz, text),
  public.execute_payment_task(uuid, text, text), public.send_admin_notification(text, uuid, text, text),
  public.admin_account_info() to authenticated;

commit;
