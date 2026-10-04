-- AMAN | أمان
-- Phase 1: production database foundation
-- PostgreSQL / Supabase migration
-- This migration is intentionally self-contained and can be replayed on a clean project.

begin;

create extension if not exists pgcrypto;
create extension if not exists citext;

create type public.account_status as enum ('active','suspended','closed');
create type public.record_status as enum ('active','inactive','archived');
create type public.purchase_status as enum ('pending','approved','rejected');
create type public.protection_status as enum ('active','expired','cancelled');
create type public.operation_status as enum ('pending','processing','succeeded','failed','cancelled');
create type public.operation_type as enum ('points_purchase','points_approval','points_rejection','number_added','protection_activation','protection_extension','payment_task');
create type public.task_status as enum ('open','completed','cancelled');
create type public.notification_type as enum ('system','admin_alert');
create type public.message_status as enum ('open','closed');
create type public.audit_action as enum ('insert','update','delete','rpc');

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text,
  username citext unique,
  phone text,
  email text,
  account_status public.account_status not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.admin_roles (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null,
  status public.record_status not null default 'active',
  created_at timestamptz not null default now()
);

create table public.admin_permissions (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null,
  created_at timestamptz not null default now()
);

create table public.admin_role_permissions (
  role_id uuid not null references public.admin_roles(id) on delete cascade,
  permission_id uuid not null references public.admin_permissions(id) on delete cascade,
  primary key (role_id, permission_id)
);

create table public.admin_user_roles (
  user_id uuid not null references public.profiles(id) on delete cascade,
  role_id uuid not null references public.admin_roles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, role_id)
);

create table public.subscribers (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references public.profiles(id) on delete cascade,
  status public.record_status not null default 'active',
  became_subscriber_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.telecom_providers (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null,
  short_name text,
  status public.record_status not null default 'active',
  operational_settings jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.telecom_prefixes (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.telecom_providers(id) on delete restrict,
  prefix text not null,
  country_code text,
  number_length integer,
  status public.record_status not null default 'active',
  created_at timestamptz not null default now(),
  unique (provider_id, prefix),
  check (prefix <> ''),
  check (number_length is null or number_length > 0)
);

create table public.provider_tariffs (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references public.telecom_providers(id) on delete restrict,
  points_per_day integer not null check (points_per_day > 0),
  effective_from timestamptz not null default now(),
  effective_to timestamptz,
  status public.record_status not null default 'active',
  created_at timestamptz not null default now(),
  check (effective_to is null or effective_to > effective_from)
);

create table public.phone_numbers (
  id uuid primary key default gen_random_uuid(),
  phone_e164 text not null,
  normalized_phone text not null unique,
  provider_id uuid references public.telecom_providers(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.customer_numbers (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  subscriber_id uuid references public.subscribers(id) on delete set null,
  phone_number_id uuid not null references public.phone_numbers(id) on delete cascade,
  added_at timestamptz not null default now(),
  status public.record_status not null default 'active',
  unique (user_id, phone_number_id)
);

create table public.points_packages (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  points_amount integer not null check (points_amount > 0),
  price_amount numeric(18,2) not null check (price_amount >= 0),
  currency text not null default 'USD',
  display_order integer not null default 0,
  status public.record_status not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.payment_methods (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  payment_data jsonb not null default '{}'::jsonb,
  instructions text,
  status public.record_status not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.point_balances (
  user_id uuid primary key references public.profiles(id) on delete cascade,
  balance_points bigint not null default 0 check (balance_points >= 0),
  updated_at timestamptz not null default now()
);

create table public.points_purchase_requests (
  id uuid primary key default gen_random_uuid(),
  request_number text not null unique,
  user_id uuid not null references public.profiles(id) on delete restrict,
  subscriber_id uuid references public.subscribers(id) on delete set null,
  package_id uuid not null references public.points_packages(id) on delete restrict,
  points_amount_snapshot integer not null check (points_amount_snapshot > 0),
  price_amount_snapshot numeric(18,2) not null check (price_amount_snapshot >= 0),
  currency_snapshot text not null,
  payment_method_id uuid not null references public.payment_methods(id) on delete restrict,
  payment_method_name_snapshot text not null,
  payment_reference text not null,
  submitted_at timestamptz not null default now(),
  status public.purchase_status not null default 'pending',
  rejection_reason text,
  reviewed_by uuid references public.profiles(id) on delete set null,
  reviewed_at timestamptz,
  idempotency_key text not null unique,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (length(trim(payment_reference)) > 0),
  check ((status = 'rejected' and rejection_reason is not null) or status <> 'rejected')
);

create table public.point_ledger (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete restrict,
  entry_type text not null check (entry_type in ('purchase_credit','activation_debit','extension_debit','adjustment')),
  amount bigint not null check (amount <> 0),
  balance_after bigint not null check (balance_after >= 0),
  reference_type text,
  reference_id uuid,
  description text,
  created_at timestamptz not null default now(),
  created_by uuid references public.profiles(id) on delete set null
);

create table public.protections (
  id uuid primary key default gen_random_uuid(),
  phone_number_id uuid not null references public.phone_numbers(id) on delete restrict,
  subscriber_id uuid not null references public.subscribers(id) on delete restrict,
  user_id uuid not null references public.profiles(id) on delete restrict,
  status public.protection_status not null default 'active',
  started_at timestamptz not null default now(),
  expires_at timestamptz not null,
  duration_days integer not null check (duration_days > 0),
  provider_id uuid not null references public.telecom_providers(id) on delete restrict,
  tariff_id uuid not null references public.provider_tariffs(id) on delete restrict,
  points_per_day_snapshot integer not null check (points_per_day_snapshot > 0),
  total_points_snapshot bigint not null check (total_points_snapshot > 0),
  activated_by uuid not null references public.profiles(id) on delete restrict,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (expires_at > started_at)
);

create unique index protections_one_active_phone on public.protections(phone_number_id) where status = 'active';

create table public.protection_extensions (
  id uuid primary key default gen_random_uuid(),
  protection_id uuid not null references public.protections(id) on delete restrict,
  subscriber_id uuid not null references public.subscribers(id) on delete restrict,
  days_added integer not null check (days_added > 0),
  points_per_day_snapshot integer not null check (points_per_day_snapshot > 0),
  points_cost bigint not null check (points_cost > 0),
  expires_at_before timestamptz not null,
  expires_at_after timestamptz not null,
  point_ledger_id uuid references public.point_ledger(id) on delete restrict,
  operation_id uuid,
  created_at timestamptz not null default now(),
  check (expires_at_after > expires_at_before)
);

create table public.task_settings (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null unique references public.telecom_providers(id) on delete cascade,
  interval_days integer not null check (interval_days > 0),
  task_amount numeric(18,2) not null check (task_amount >= 0),
  currency text not null default 'USD',
  visibility_days_before integer not null default 0 check (visibility_days_before >= 0),
  auto_create boolean not null default true,
  allow_reschedule boolean not null default true,
  allow_post_expiry_creation boolean not null default false,
  post_expiry_creation_limit_days integer check (post_expiry_creation_limit_days is null or post_expiry_creation_limit_days >= 0),
  updated_at timestamptz not null default now()
);

create table public.protection_task_plans (
  id uuid primary key default gen_random_uuid(),
  protection_id uuid not null unique references public.protections(id) on delete cascade,
  anchor_date timestamptz not null,
  interval_days integer not null check (interval_days > 0),
  planned_until timestamptz not null,
  planned_task_count integer not null default 0 check (planned_task_count >= 0),
  version integer not null default 1 check (version > 0),
  status public.record_status not null default 'active',
  last_rebuilt_at timestamptz not null default now()
);

create table public.payment_tasks (
  id uuid primary key default gen_random_uuid(),
  plan_id uuid not null references public.protection_task_plans(id) on delete cascade,
  protection_id uuid not null references public.protections(id) on delete restrict,
  phone_number_id uuid not null references public.phone_numbers(id) on delete restrict,
  subscriber_id uuid not null references public.subscribers(id) on delete restrict,
  provider_id uuid not null references public.telecom_providers(id) on delete restrict,
  due_at timestamptz not null,
  amount_snapshot numeric(18,2) not null check (amount_snapshot >= 0),
  currency_snapshot text not null,
  status public.task_status not null default 'open',
  completed_at timestamptz,
  completed_by uuid references public.profiles(id) on delete set null,
  cancelled_at timestamptz,
  cancellation_reason text,
  rescheduled_from uuid references public.payment_tasks(id) on delete set null,
  rescheduled_at timestamptz,
  execution_key text unique,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (plan_id, due_at)
);

create table public.financial_ledger (
  id uuid primary key default gen_random_uuid(),
  entry_type text not null,
  amount numeric(18,2) not null,
  currency text not null,
  reference_type text,
  reference_id uuid,
  description text,
  created_at timestamptz not null default now(),
  created_by uuid references public.profiles(id) on delete set null
);

create table public.operations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references public.profiles(id) on delete set null,
  operation_type public.operation_type not null,
  status public.operation_status not null default 'pending',
  reference_type text,
  reference_id uuid,
  points_delta bigint,
  money_amount numeric(18,2),
  phone_number_id uuid references public.phone_numbers(id) on delete set null,
  metadata jsonb not null default '{}'::jsonb,
  idempotency_key text unique,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.protection_extensions add constraint protection_extensions_operation_fk foreign key (operation_id) references public.operations(id) on delete restrict;

create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  notification_type public.notification_type not null default 'system',
  title text not null,
  content text not null,
  read_at timestamptz,
  reference_type text,
  reference_id uuid,
  created_at timestamptz not null default now()
);

create table public.support_threads (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  subject text not null,
  status public.message_status not null default 'open',
  last_reply_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.support_messages (
  id uuid primary key default gen_random_uuid(),
  thread_id uuid not null references public.support_threads(id) on delete cascade,
  sender_id uuid not null references public.profiles(id) on delete restrict,
  body text not null,
  created_at timestamptz not null default now()
);

create table public.audit_logs (
  id uuid primary key default gen_random_uuid(),
  actor_id uuid references public.profiles(id) on delete set null,
  action public.audit_action not null,
  entity_type text not null,
  entity_id uuid,
  before_data jsonb,
  after_data jsonb,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index idx_customer_numbers_user on public.customer_numbers(user_id);
create index idx_customer_numbers_phone on public.customer_numbers(phone_number_id);
create index idx_prefixes_prefix on public.telecom_prefixes(prefix);
create index idx_protections_subscriber_status on public.protections(subscriber_id, status);
create index idx_purchases_user_status on public.points_purchase_requests(user_id, status);
create index idx_purchases_status_submitted on public.points_purchase_requests(status, submitted_at);
create index idx_point_ledger_user_created on public.point_ledger(user_id, created_at desc);
create index idx_operations_user_created on public.operations(user_id, created_at desc);
create index idx_tasks_status_due on public.payment_tasks(status, due_at);
create index idx_tasks_protection on public.payment_tasks(protection_id);
create index idx_notifications_user_created on public.notifications(user_id, created_at desc);
create index idx_audit_entity on public.audit_logs(entity_type, entity_id);

create or replace function public.set_updated_at() returns trigger language plpgsql security invoker as $$
begin new.updated_at = now(); return new; end; $$;

do $$ declare t text; begin
  foreach t in array array['profiles','subscribers','telecom_providers','phone_numbers','points_packages','payment_methods','points_purchase_requests','protections','operations','support_threads','payment_tasks'] loop
    execute format('create trigger %I before update on public.%I for each row execute function public.set_updated_at()', 'trg_'||t||'_updated_at', t);
  end loop;
end $$;

create or replace function public.is_admin(p_user_id uuid default auth.uid()) returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.admin_user_roles ur join public.admin_roles r on r.id = ur.role_id where ur.user_id = p_user_id and r.status = 'active');
$$;

create or replace function public.resolve_provider(p_normalized_phone text) returns uuid language sql stable security invoker as $$
  select p.provider_id from public.telecom_prefixes p where p.status = 'active' and p_normalized_phone like p.prefix || '%' order by length(p.prefix) desc limit 1;
$$;

create or replace function public.rebuild_task_plan(p_protection_id uuid, p_anchor_date timestamptz default null) returns uuid language plpgsql security definer set search_path = public as $$
declare v_protection public.protections; v_settings public.task_settings; v_plan_id uuid; v_anchor timestamptz; v_count integer; v_due timestamptz; begin
  select * into v_protection from public.protections where id = p_protection_id for update;
  if not found then raise exception 'protection_not_found'; end if;
  select * into v_settings from public.task_settings where provider_id = v_protection.provider_id;
  if not found then raise exception 'task_settings_not_found'; end if;
  v_anchor := coalesce(p_anchor_date, v_protection.started_at);
  insert into public.protection_task_plans(protection_id, anchor_date, interval_days, planned_until, planned_task_count, version)
  values (v_protection.id, v_anchor, v_settings.interval_days, v_protection.expires_at, 0, 1)
  on conflict (protection_id) do update set anchor_date = excluded.anchor_date, interval_days = excluded.interval_days, planned_until = excluded.planned_until, version = public.protection_task_plans.version + 1, last_rebuilt_at = now()
  returning id into v_plan_id;
  delete from public.payment_tasks where plan_id = v_plan_id and status = 'open';
  v_due := v_anchor; v_count := 0;
  while v_due < v_protection.expires_at loop
    if v_due >= now() then
      insert into public.payment_tasks(plan_id, protection_id, phone_number_id, subscriber_id, provider_id, due_at, amount_snapshot, currency_snapshot)
      values (v_plan_id, v_protection.id, v_protection.phone_number_id, v_protection.subscriber_id, v_protection.provider_id, v_due, v_settings.task_amount, v_settings.currency) on conflict do nothing;
      v_count := v_count + 1;
    end if;
    v_due := v_due + make_interval(days => v_settings.interval_days);
  end loop;
  update public.protection_task_plans set planned_task_count = v_count, planned_until = v_protection.expires_at, last_rebuilt_at = now() where id = v_plan_id;
  return v_plan_id;
end; $$;

create or replace function public.submit_points_purchase(p_package_id uuid, p_payment_method_id uuid, p_payment_reference text, p_idempotency_key text) returns public.points_purchase_requests language plpgsql security invoker set search_path = public as $$
declare v_pkg public.points_packages; v_method public.payment_methods; v_row public.points_purchase_requests; begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  select * into v_pkg from public.points_packages where id = p_package_id and status = 'active';
  if not found then raise exception 'package_unavailable'; end if;
  select * into v_method from public.payment_methods where id = p_payment_method_id and status = 'active';
  if not found then raise exception 'payment_method_unavailable'; end if;
  insert into public.points_purchase_requests(request_number,user_id,package_id,points_amount_snapshot,price_amount_snapshot,currency_snapshot,payment_method_id,payment_method_name_snapshot,payment_reference,idempotency_key)
  values ('REQ-'||to_char(clock_timestamp(),'YYYYMMDDHH24MISSMS')||'-'||substr(gen_random_uuid()::text,1,8),auth.uid(),v_pkg.id,v_pkg.points_amount,v_pkg.price_amount,v_pkg.currency,v_method.id,v_method.name,trim(p_payment_reference),p_idempotency_key)
  on conflict (idempotency_key) do update set updated_at = public.points_purchase_requests.updated_at returning * into v_row;
  return v_row;
end; $$;

create or replace function public.approve_points_purchase(p_request_id uuid, p_idempotency_key text) returns public.points_purchase_requests language plpgsql security definer set search_path = public as $$
declare v_req public.points_purchase_requests; v_balance bigint; begin
  if not public.is_admin() then raise exception 'admin_permission_required'; end if;
  select * into v_req from public.points_purchase_requests where id = p_request_id for update;
  if not found then raise exception 'request_not_found'; end if;
  if v_req.status <> 'pending' then return v_req; end if;
  insert into public.point_balances(user_id,balance_points) values(v_req.user_id,0) on conflict do nothing;
  select balance_points into v_balance from public.point_balances where user_id = v_req.user_id for update;
  v_balance := v_balance + v_req.points_amount_snapshot;
  update public.point_balances set balance_points = v_balance, updated_at = now() where user_id = v_req.user_id;
  insert into public.point_ledger(user_id,entry_type,amount,balance_after,reference_type,reference_id,description,created_by) values(v_req.user_id,'purchase_credit',v_req.points_amount_snapshot,v_balance,'points_purchase_request',v_req.id,'Approved points purchase',auth.uid());
  update public.points_purchase_requests set status='approved', reviewed_by=auth.uid(), reviewed_at=now(), updated_at=now() where id=v_req.id returning * into v_req;
  insert into public.financial_ledger(entry_type,amount,currency,reference_type,reference_id,description,created_by) values('points_purchase',v_req.price_amount_snapshot,v_req.currency_snapshot,'points_purchase_request',v_req.id,'Approved points purchase',auth.uid());
  insert into public.operations(user_id,operation_type,status,reference_type,reference_id,points_delta,money_amount,idempotency_key) values(v_req.user_id,'points_approval','succeeded','points_purchase_request',v_req.id,v_req.points_amount_snapshot,v_req.price_amount_snapshot,p_idempotency_key) on conflict (idempotency_key) do nothing;
  insert into public.notifications(user_id,notification_type,title,content,reference_type,reference_id) values(v_req.user_id,'system','تم اعتماد شراء النقاط','تمت إضافة النقاط إلى رصيدك.','points_purchase_request',v_req.id);
  insert into public.audit_logs(actor_id,action,entity_type,entity_id,after_data) values(auth.uid(),'rpc','points_purchase_request',v_req.id,to_jsonb(v_req));
  return v_req;
end; $$;

create or replace function public.activate_protection(p_phone_number_id uuid, p_duration_days integer, p_operation_key text) returns public.protections language plpgsql security definer set search_path = public as $$
declare v_sub public.subscribers; v_phone public.phone_numbers; v_tariff public.provider_tariffs; v_balance bigint; v_cost bigint; v_protection public.protections; v_op uuid; begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  select * into v_sub from public.subscribers where user_id=auth.uid() and status='active'; if not found then raise exception 'active_subscriber_required'; end if;
  select * into v_phone from public.phone_numbers where id=p_phone_number_id for update; if not found then raise exception 'phone_not_found'; end if;
  if exists(select 1 from public.protections where phone_number_id=p_phone_number_id and status='active') then raise exception 'phone_already_protected'; end if;
  select * into v_tariff from public.provider_tariffs where provider_id=v_phone.provider_id and status='active' and effective_from <= now() and (effective_to is null or effective_to > now()) order by effective_from desc limit 1; if not found then raise exception 'tariff_not_found'; end if;
  if p_duration_days <= 0 then raise exception 'invalid_duration'; end if;
  v_cost := p_duration_days * v_tariff.points_per_day;
  insert into public.point_balances(user_id,balance_points) values(auth.uid(),0) on conflict do nothing;
  select balance_points into v_balance from public.point_balances where user_id=auth.uid() for update;
  if v_balance < v_cost then raise exception 'insufficient_points'; end if;
  update public.point_balances set balance_points=balance_points-v_cost, updated_at=now() where user_id=auth.uid();
  insert into public.protections(phone_number_id,subscriber_id,user_id,expires_at,duration_days,provider_id,tariff_id,points_per_day_snapshot,total_points_snapshot,activated_by) values(p_phone_number_id,v_sub.id,auth.uid(),now()+make_interval(days=>p_duration_days),p_duration_days,v_phone.provider_id,v_tariff.id,v_tariff.points_per_day,v_cost,auth.uid()) returning * into v_protection;
  insert into public.point_ledger(user_id,entry_type,amount,balance_after,reference_type,reference_id,description,created_by) values(auth.uid(),'activation_debit',-v_cost,v_balance-v_cost,'protection',v_protection.id,'Protection activation',auth.uid());
  insert into public.operations(user_id,operation_type,status,reference_type,reference_id,points_delta,phone_number_id,idempotency_key) values(auth.uid(),'protection_activation','succeeded','protection',v_protection.id,-v_cost,p_phone_number_id,p_operation_key) on conflict (idempotency_key) do nothing returning id into v_op;
  perform public.rebuild_task_plan(v_protection.id, v_protection.started_at);
  insert into public.audit_logs(actor_id,action,entity_type,entity_id,after_data) values(auth.uid(),'rpc','protection',v_protection.id,to_jsonb(v_protection));
  return v_protection;
end; $$;

create or replace function public.extend_protection(p_protection_id uuid, p_days integer, p_operation_key text) returns public.protections language plpgsql security definer set search_path = public as $$
declare v_p public.protections; v_tariff public.provider_tariffs; v_balance bigint; v_cost bigint; v_before timestamptz; v_op uuid; begin
  select * into v_p from public.protections where id=p_protection_id and user_id=auth.uid() and status='active' for update; if not found then raise exception 'protection_not_owned_or_inactive'; end if;
  if p_days <= 0 then raise exception 'invalid_extension_days'; end if;
  select * into v_tariff from public.provider_tariffs where id=v_p.tariff_id and status='active'; if not found then raise exception 'tariff_not_found'; end if;
  v_cost := p_days * v_p.points_per_day_snapshot; v_before := v_p.expires_at;
  select balance_points into v_balance from public.point_balances where user_id=auth.uid() for update;
  if coalesce(v_balance,0) < v_cost then raise exception 'insufficient_points'; end if;
  update public.point_balances set balance_points=balance_points-v_cost, updated_at=now() where user_id=auth.uid();
  update public.protections set expires_at=expires_at+make_interval(days=>p_days), duration_days=duration_days+p_days, total_points_snapshot=total_points_snapshot+v_cost, updated_at=now() where id=v_p.id returning * into v_p;
  insert into public.operations(user_id,operation_type,status,reference_type,reference_id,points_delta,phone_number_id,idempotency_key) values(auth.uid(),'protection_extension','succeeded','protection',v_p.id,-v_cost,v_p.phone_number_id,p_operation_key) on conflict (idempotency_key) do nothing returning id into v_op;
  insert into public.point_ledger(user_id,entry_type,amount,balance_after,reference_type,reference_id,description,created_by) values(auth.uid(),'extension_debit',-v_cost,v_balance-v_cost,'protection',v_p.id,'Protection extension',auth.uid());
  insert into public.protection_extensions(protection_id,subscriber_id,days_added,points_per_day_snapshot,points_cost,expires_at_before,expires_at_after,operation_id) values(v_p.id,v_p.subscriber_id,p_days,v_p.points_per_day_snapshot,v_cost,v_before,v_p.expires_at,v_op);
  perform public.rebuild_task_plan(v_p.id, null);
  insert into public.audit_logs(actor_id,action,entity_type,entity_id,after_data) values(auth.uid(),'rpc','protection',v_p.id,to_jsonb(v_p));
  return v_p;
end; $$;

create or replace function public.execute_payment_task(p_task_id uuid, p_execution_key text) returns public.payment_tasks language plpgsql security definer set search_path = public as $$
declare v_task public.payment_tasks; begin
  if not public.is_admin() then raise exception 'admin_permission_required'; end if;
  select * into v_task from public.payment_tasks where id=p_task_id for update;
  if not found then raise exception 'task_not_found'; end if;
  if v_task.status <> 'open' then return v_task; end if;
  update public.payment_tasks set status='completed', completed_at=now(), completed_by=auth.uid(), execution_key=p_execution_key, updated_at=now() where id=p_task_id returning * into v_task;
  insert into public.financial_ledger(entry_type,amount,currency,reference_type,reference_id,description,created_by) values('payment_task',v_task.amount_snapshot,v_task.currency_snapshot,'payment_task',v_task.id,'Completed payment task',auth.uid());
  insert into public.audit_logs(actor_id,action,entity_type,entity_id,after_data) values(auth.uid(),'rpc','payment_task',v_task.id,to_jsonb(v_task));
  perform public.rebuild_task_plan(v_task.protection_id, now());
  return v_task;
end; $$;

alter table public.profiles enable row level security;
alter table public.subscribers enable row level security;
alter table public.customer_numbers enable row level security;
alter table public.phone_numbers enable row level security;
alter table public.points_packages enable row level security;
alter table public.payment_methods enable row level security;
alter table public.point_balances enable row level security;
alter table public.points_purchase_requests enable row level security;
alter table public.point_ledger enable row level security;
alter table public.protections enable row level security;
alter table public.protection_extensions enable row level security;
alter table public.protection_task_plans enable row level security;
alter table public.payment_tasks enable row level security;
alter table public.operations enable row level security;
alter table public.notifications enable row level security;
alter table public.support_threads enable row level security;
alter table public.support_messages enable row level security;
alter table public.audit_logs enable row level security;

create policy profiles_self_select on public.profiles for select using (id=auth.uid() or public.is_admin());
create policy profiles_self_update on public.profiles for update using (id=auth.uid() or public.is_admin()) with check (id=auth.uid() or public.is_admin());
create policy subscribers_self_select on public.subscribers for select using (user_id=auth.uid() or public.is_admin());
create policy numbers_self_select on public.customer_numbers for select using (user_id=auth.uid() or public.is_admin());
create policy phone_self_select on public.phone_numbers for select using (exists(select 1 from public.customer_numbers c where c.phone_number_id=id and c.user_id=auth.uid()) or public.is_admin());
create policy packages_read_active on public.points_packages for select using (status='active' or public.is_admin());
create policy methods_read_active on public.payment_methods for select using (status='active' or public.is_admin());
create policy balances_self_select on public.point_balances for select using (user_id=auth.uid() or public.is_admin());
create policy purchases_self_select on public.points_purchase_requests for select using (user_id=auth.uid() or public.is_admin());
create policy ledger_self_select on public.point_ledger for select using (user_id=auth.uid() or public.is_admin());
create policy protections_self_select on public.protections for select using (user_id=auth.uid() or public.is_admin());
create policy extensions_self_select on public.protection_extensions for select using (subscriber_id in (select id from public.subscribers where user_id=auth.uid()) or public.is_admin());
create policy plans_self_select on public.protection_task_plans for select using (protection_id in (select id from public.protections where user_id=auth.uid()) or public.is_admin());
create policy tasks_admin_only on public.payment_tasks for select using (public.is_admin());
create policy operations_self_select on public.operations for select using (user_id=auth.uid() or public.is_admin());
create policy notifications_self_select on public.notifications for select using (user_id=auth.uid() or public.is_admin());
create policy notifications_self_update on public.notifications for update using (user_id=auth.uid() or public.is_admin()) with check (user_id=auth.uid() or public.is_admin());
create policy threads_self_all on public.support_threads for all using (user_id=auth.uid() or public.is_admin()) with check (user_id=auth.uid() or public.is_admin());
create policy messages_thread_access on public.support_messages for all using (exists(select 1 from public.support_threads t where t.id=thread_id and (t.user_id=auth.uid() or public.is_admin()))) with check (sender_id=auth.uid() or public.is_admin());
create policy audit_admin_only on public.audit_logs for select using (public.is_admin());

revoke all on all tables in schema public from anon;
revoke all on all tables in schema public from authenticated;
grant select on public.points_packages, public.payment_methods, public.telecom_providers, public.telecom_prefixes to authenticated;
grant select, update on public.profiles, public.notifications to authenticated;
grant select on public.subscribers, public.customer_numbers, public.phone_numbers, public.point_balances, public.points_purchase_requests, public.point_ledger, public.protections, public.protection_extensions, public.protection_task_plans, public.operations, public.support_threads, public.support_messages to authenticated;
grant execute on function public.submit_points_purchase(uuid,uuid,text,text) to authenticated;
grant execute on function public.activate_protection(uuid,integer,text) to authenticated;
grant execute on function public.extend_protection(uuid,integer,text) to authenticated;
grant execute on function public.approve_points_purchase(uuid,text) to authenticated;
grant execute on function public.execute_payment_task(uuid,text) to authenticated;

do $$ begin
  insert into public.admin_roles(code,name) values ('super_admin','Super Administrator') on conflict (code) do nothing;
end $$;

commit;
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
