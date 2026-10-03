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
