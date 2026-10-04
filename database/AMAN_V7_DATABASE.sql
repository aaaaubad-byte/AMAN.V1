-- AMAN | أمان — V7 canonical database build
-- Phase 1 deliverable: clean, deterministic PostgreSQL/Supabase schema.
-- Source authority: V7.md + V7 ARP.md. No legacy migration is required.
-- This file is intentionally not executed against Supabase in this phase.

begin;

create extension if not exists pgcrypto;
create extension if not exists citext;

-- Canonical enums
create type public.account_status as enum ('active','suspended','closed');
create type public.admin_role_status as enum ('active','disabled');
create type public.record_status as enum ('active','inactive','removed');
create type public.provider_status as enum ('active','disabled');
create type public.tariff_status as enum ('active','expired','disabled');
create type public.package_status as enum ('active','hidden','disabled');
create type public.payment_method_type as enum ('wallet','transfer','other');
create type public.payment_method_status as enum ('active','hidden','disabled');
create type public.purchase_status as enum ('pending','approved','rejected','cancelled');
create type public.activated_number_status as enum ('active','expired','inactive');
create type public.protection_status as enum ('active','expired','cancelled');
create type public.plan_status as enum ('active','closed');
create type public.task_status as enum ('open','completed','cancelled');
create type public.point_entry_type as enum ('purchase_credit','activation_debit','extension_debit','renewal_debit','refund','admin_adjustment','correction');
create type public.point_direction as enum ('credit','debit');
create type public.financial_entry_type as enum ('points_purchase_income','task_payment','expense','admin_points_adjustment','refund','correction');
create type public.financial_direction as enum ('inflow','outflow');
create type public.support_status as enum ('open','closed');
create type public.support_sender_type as enum ('customer','admin');
create type public.admin_notification_target as enum ('all','subscriber','user');
create type public.admin_notification_status as enum ('draft','sending','sent','failed');

-- Identity, RBAC and catalog
create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  user_code text not null unique,
  full_name text not null,
  username citext not null unique,
  phone text,
  email text,
  account_status public.account_status not null default 'active',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create table public.admin_roles (
  id uuid primary key default gen_random_uuid(), code text not null unique,
  name text not null, status public.admin_role_status not null default 'active',
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.admin_permissions (
  id uuid primary key default gen_random_uuid(), code text not null unique,
  name text not null, module text not null, created_at timestamptz not null default now()
);
create table public.admin_role_permissions (
  role_id uuid not null references public.admin_roles(id) on delete cascade,
  permission_id uuid not null references public.admin_permissions(id) on delete cascade,
  primary key (role_id, permission_id)
);
create table public.admin_user_roles (
  user_id uuid not null references public.profiles(id) on delete cascade,
  role_id uuid not null references public.admin_roles(id) on delete cascade,
  assigned_at timestamptz not null default now(), assigned_by uuid references public.profiles(id) on delete set null,
  primary key (user_id, role_id)
);
create table public.telecom_providers (
  id uuid primary key default gen_random_uuid(), code text not null unique, name text not null,
  short_name text, status public.provider_status not null default 'active', operational_settings jsonb not null default '{}',
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.telecom_prefixes (
  id uuid primary key default gen_random_uuid(), provider_id uuid not null references public.telecom_providers(id) on delete restrict,
  prefix text not null, country_code text, number_length integer not null,
  status public.provider_status not null default 'active', created_at timestamptz not null default now(),
  unique(provider_id,prefix), check (prefix <> ''), check (number_length between 1 and 15)
);
create table public.provider_tariffs (
  id uuid primary key default gen_random_uuid(), provider_id uuid not null references public.telecom_providers(id) on delete restrict,
  points_per_day integer not null, effective_from timestamptz not null, effective_to timestamptz,
  status public.tariff_status not null default 'active', created_at timestamptz not null default now(),
  check(points_per_day > 0), check(effective_to is null or effective_to > effective_from)
);
create unique index provider_one_active_tariff on public.provider_tariffs(provider_id) where status='active' and effective_to is null;
create table public.points_packages (
  id uuid primary key default gen_random_uuid(), name text not null, points_amount integer not null,
  price_amount numeric(14,2) not null, currency text not null default 'YER', display_order integer not null default 0,
  status public.package_status not null default 'active', created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  check(points_amount > 0), check(price_amount >= 0)
);
create table public.payment_methods (
  id uuid primary key default gen_random_uuid(), name text not null, type public.payment_method_type not null,
  account_identifier text not null, instructions text, display_order integer not null default 0,
  status public.payment_method_status not null default 'active', created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);

-- Customer and protection lifecycle
create table public.subscribers (
  id uuid primary key default gen_random_uuid(), subscriber_code text not null unique,
  user_id uuid not null unique references public.profiles(id) on delete cascade,
  status public.account_status not null default 'active', became_subscriber_at timestamptz not null default now(),
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.phone_numbers (
  id uuid primary key default gen_random_uuid(), phone_e164 text not null unique, normalized_phone text not null unique,
  provider_id uuid not null references public.telecom_providers(id) on delete restrict,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.customer_numbers (
  id uuid primary key default gen_random_uuid(), added_number_code text not null unique,
  user_id uuid not null references public.profiles(id) on delete cascade,
  phone_number_id uuid not null references public.phone_numbers(id) on delete restrict,
  status public.record_status not null default 'active', added_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  unique(user_id,phone_number_id)
);
create table public.activated_numbers (
  id uuid primary key default gen_random_uuid(), activation_code text not null unique,
  customer_number_id uuid not null references public.customer_numbers(id) on delete restrict,
  subscriber_id uuid not null references public.subscribers(id) on delete restrict,
  current_protection_id uuid unique, status public.activated_number_status not null default 'active',
  activated_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  unique(customer_number_id,subscriber_id)
);
create table public.points_purchase_requests (
  id uuid primary key default gen_random_uuid(), request_number text not null unique,
  user_id uuid not null references public.profiles(id) on delete restrict,
  subscriber_id uuid references public.subscribers(id) on delete set null,
  package_id uuid not null references public.points_packages(id) on delete restrict,
  points_amount_snapshot integer not null, price_amount_snapshot numeric(14,2) not null, currency_snapshot text not null,
  payment_method_id uuid not null references public.payment_methods(id) on delete restrict,
  payment_method_name_snapshot text not null, payment_account_snapshot text not null, payment_instructions_snapshot text,
  payment_reference text not null, status public.purchase_status not null default 'pending', rejection_reason text,
  submitted_at timestamptz not null default now(), reviewed_by uuid references public.profiles(id) on delete set null, reviewed_at timestamptz,
  created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  check(points_amount_snapshot > 0), check(price_amount_snapshot >= 0), check(length(trim(payment_reference)) > 0),
  check(status <> 'rejected' or nullif(trim(rejection_reason),'') is not null)
);
create table public.point_balances (
  user_id uuid primary key references public.profiles(id) on delete cascade, balance_points bigint not null default 0,
  updated_at timestamptz not null default now(), check(balance_points >= 0)
);
create table public.operations (
  id uuid primary key default gen_random_uuid(), operation_type text not null, status text not null,
  reference_type text, reference_id uuid, user_id uuid references public.profiles(id) on delete set null,
  subscriber_id uuid references public.subscribers(id) on delete set null, phone_number_id uuid references public.phone_numbers(id) on delete set null,
  points_delta bigint, money_amount numeric(14,2), metadata jsonb not null default '{}', created_at timestamptz not null default now()
);
create table public.point_ledger (
  id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles(id) on delete restrict,
  entry_type public.point_entry_type not null, direction public.point_direction not null, amount_points bigint not null,
  balance_after bigint not null, reference_type text not null, reference_id uuid, description text,
  created_by uuid references public.profiles(id) on delete set null, created_at timestamptz not null default now(),
  check(amount_points > 0), check(balance_after >= 0)
);
create table public.protections (
  id uuid primary key default gen_random_uuid(), activated_number_id uuid not null references public.activated_numbers(id) on delete restrict,
  subscriber_id uuid not null references public.subscribers(id) on delete restrict, provider_id uuid not null references public.telecom_providers(id) on delete restrict,
  tariff_id uuid not null references public.provider_tariffs(id) on delete restrict, status public.protection_status not null default 'active',
  started_at timestamptz not null, expires_at timestamptz not null, duration_days integer not null,
  points_per_day_snapshot integer not null, total_points_snapshot bigint not null, created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  check(expires_at > started_at), check(duration_days > 0), check(points_per_day_snapshot > 0), check(total_points_snapshot > 0)
);
create unique index protection_one_active_per_activation on public.protections(activated_number_id) where status='active';
alter table public.activated_numbers add constraint activated_current_protection_fk foreign key(current_protection_id) references public.protections(id) on delete set null;
create table public.protection_extensions (
  id uuid primary key default gen_random_uuid(), protection_id uuid not null references public.protections(id) on delete restrict,
  subscriber_id uuid not null references public.subscribers(id) on delete restrict, days_added integer not null,
  points_per_day_snapshot integer not null, points_cost bigint not null, expires_at_before timestamptz not null, expires_at_after timestamptz not null,
  point_ledger_id uuid not null references public.point_ledger(id) on delete restrict, operation_id uuid references public.operations(id) on delete set null,
  created_at timestamptz not null default now(), check(days_added > 0), check(points_per_day_snapshot > 0), check(points_cost > 0), check(expires_at_after > expires_at_before)
);

-- Task engine
create table public.task_settings (
  id uuid primary key default gen_random_uuid(), provider_id uuid not null unique references public.telecom_providers(id) on delete cascade,
  interval_days integer not null, task_amount numeric(14,2) not null, currency text not null default 'YER', visibility_days_before integer not null default 0,
  auto_create boolean not null default true, allow_reschedule boolean not null default true, allow_post_expiry_creation boolean not null default false,
  post_expiry_creation_limit_days integer not null default 0, created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  check(interval_days > 0), check(task_amount >= 0), check(visibility_days_before >= 0), check(post_expiry_creation_limit_days >= 0)
);
create table public.protection_task_plans (
  id uuid primary key default gen_random_uuid(), protection_id uuid not null unique references public.protections(id) on delete cascade,
  anchor_date timestamptz not null, interval_days integer not null, planned_until timestamptz not null, planned_task_count integer not null default 0,
  version integer not null default 1, status public.plan_status not null default 'active', last_rebuilt_at timestamptz not null default now(),
  check(interval_days > 0), check(planned_task_count >= 0)
);
create table public.payment_tasks (
  id uuid primary key default gen_random_uuid(), task_code text not null unique, plan_id uuid not null references public.protection_task_plans(id) on delete cascade,
  protection_id uuid not null references public.protections(id) on delete restrict, phone_number_id uuid not null references public.phone_numbers(id) on delete restrict,
  subscriber_id uuid not null references public.subscribers(id) on delete restrict, provider_id uuid not null references public.telecom_providers(id) on delete restrict,
  due_at timestamptz not null, original_due_at timestamptz not null, amount_snapshot numeric(14,2) not null, currency_snapshot text not null,
  status public.task_status not null default 'open', completed_at timestamptz, completed_by uuid references public.profiles(id) on delete set null,
  cancelled_at timestamptz, cancelled_by uuid references public.profiles(id) on delete set null, cancellation_reason text,
  rescheduled_from timestamptz, rescheduled_at timestamptz, rescheduled_by uuid references public.profiles(id) on delete set null, reschedule_reason text,
  sequence_no integer not null, idempotency_key text unique, created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  check(amount_snapshot >= 0), check(sequence_no > 0)
);

-- Finance, notifications, support, audit and maintenance
create table public.financial_ledger (
  id uuid primary key default gen_random_uuid(), entry_type public.financial_entry_type not null, direction public.financial_direction not null,
  amount numeric(14,2) not null, currency text not null, reference_type text not null, reference_id uuid, description text,
  metadata jsonb not null default '{}', created_by uuid references public.profiles(id) on delete set null, created_at timestamptz not null default now(), check(amount >= 0)
);
create table public.aman_financial_balance (
  id uuid primary key default gen_random_uuid(), currency text not null unique, balance_amount numeric(14,2) not null default 0,
  updated_at timestamptz not null default now(), check(balance_amount >= 0)
);
create table public.expenses (
  id uuid primary key default gen_random_uuid(), expense_type text not null, amount numeric(14,2) not null, currency text not null,
  description text, reference_type text, reference_id uuid, created_by uuid not null references public.profiles(id) on delete restrict,
  created_at timestamptz not null default now(), check(amount >= 0)
);
create table public.system_notifications (
  id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles(id) on delete cascade,
  type text not null, title text not null, body text not null, reference_type text, reference_id uuid,
  is_read boolean not null default false, read_at timestamptz, deduplication_key text not null unique, created_at timestamptz not null default now()
);
create table public.admin_notifications (
  id uuid primary key default gen_random_uuid(), sender_admin_id uuid not null references public.profiles(id) on delete restrict,
  target_type public.admin_notification_target not null, target_id uuid, title text not null, body text not null,
  status public.admin_notification_status not null default 'draft', sent_at timestamptz, created_at timestamptz not null default now(),
  check((target_type='all' and target_id is null) or (target_type<>'all' and target_id is not null))
);
create table public.support_threads (
  id uuid primary key default gen_random_uuid(), user_id uuid not null references public.profiles(id) on delete cascade,
  subject text not null, status public.support_status not null default 'open', created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.support_messages (
  id uuid primary key default gen_random_uuid(), thread_id uuid not null references public.support_threads(id) on delete cascade,
  sender_type public.support_sender_type not null, sender_id uuid not null references public.profiles(id) on delete restrict,
  body text not null, created_at timestamptz not null default now()
);
create table public.operation_logs (
  id uuid primary key default gen_random_uuid(), operation_id uuid references public.operations(id) on delete set null, action text not null,
  entity_type text not null, entity_id uuid, status text not null, metadata jsonb not null default '{}',
  created_by uuid references public.profiles(id) on delete set null, created_at timestamptz not null default now()
);
create table public.audit_logs (
  id uuid primary key default gen_random_uuid(), actor_user_id uuid references public.profiles(id) on delete set null, actor_role text,
  action text not null, entity_type text not null, entity_id uuid, "before" jsonb, "after" jsonb, metadata jsonb not null default '{}', created_at timestamptz not null default now()
);
create table public.maintenance_config (
  id uuid primary key default gen_random_uuid(), singleton_key boolean not null default true unique check(singleton_key), enabled boolean not null default false,
  customer_message text, updated_by uuid not null references public.profiles(id) on delete restrict, updated_at timestamptz not null default now()
);

-- Deterministic public IDs: transaction-local advisory lock + MAX means rollback consumes no business ID.
create or replace function public.next_public_code(p_table text, p_column text, p_prefix text)
returns text language plpgsql security definer set search_path=public,pg_temp as $$
declare n bigint; v_sql text;
begin
  if p_table not in ('profiles','subscribers','customer_numbers','activated_numbers','points_purchase_requests','payment_tasks') then raise exception 'invalid_public_id_target'; end if;
  perform pg_advisory_xact_lock(hashtext(p_table||'.'||p_column));
  v_sql := format('select coalesce(max((substring(%I from %L))::bigint),0)+1 from public.%I where %I like %L',p_column,prefix||'([0-9]+)$',p_table,p_column,prefix||'%');
  execute v_sql into n;
  return p_prefix || lpad(n::text,6,'0');
end $$;

create or replace function public.set_updated_at() returns trigger language plpgsql set search_path=public,pg_temp as $$ begin new.updated_at=now(); return new; end $$;
create trigger profiles_updated_at before update on public.profiles for each row execute function public.set_updated_at();
create trigger subscribers_updated_at before update on public.subscribers for each row execute function public.set_updated_at();
create trigger customer_numbers_updated_at before update on public.customer_numbers for each row execute function public.set_updated_at();
create trigger activated_numbers_updated_at before update on public.activated_numbers for each row execute function public.set_updated_at();
create trigger points_purchase_requests_updated_at before update on public.points_purchase_requests for each row execute function public.set_updated_at();
create trigger point_balances_updated_at before update on public.point_balances for each row execute function public.set_updated_at();
create trigger protections_updated_at before update on public.protections for each row execute function public.set_updated_at();
create trigger task_settings_updated_at before update on public.task_settings for each row execute function public.set_updated_at();
create trigger plans_updated_at before update on public.protection_task_plans for each row execute function public.set_updated_at();
create trigger payment_tasks_updated_at before update on public.payment_tasks for each row execute function public.set_updated_at();
create trigger maintenance_updated_at before update on public.maintenance_config for each row execute function public.set_updated_at();

-- Canonical authorization helpers
create or replace function public.admin_has_permission(p_code text) returns boolean language sql stable security definer set search_path=public,pg_temp as $$
 select exists(select 1 from public.admin_user_roles ur join public.admin_roles r on r.id=ur.role_id and r.status='active'
 join public.admin_role_permissions rp on rp.role_id=r.id join public.admin_permissions p on p.id=rp.permission_id
 where ur.user_id=auth.uid() and p.code=p_code);
$$;
create or replace function public.require_admin_permission(p_code text) returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin if auth.uid() is null or not public.admin_has_permission(p_code) then raise exception 'admin_permission_required:%',p_code using errcode='42501'; end if; end $$;
create or replace function public.write_audit(p_action text,p_entity_type text,p_entity_id uuid,p_before jsonb,p_after jsonb,p_metadata jsonb default '{}') returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin insert into public.audit_logs(actor_user_id,actor_role,action,entity_type,entity_id,"before","after",metadata) values(auth.uid(),case when public.admin_has_permission('audit.read') then 'admin' else 'customer' end,p_action,p_entity_type,p_entity_id,p_before,p_after,coalesce(p_metadata,'{}')); end $$;

-- Canonical RPC set. Bodies use row locks and are intentionally server authoritative.
create or replace function public.create_profile_if_missing(p_full_name text,p_username text,p_phone text default null,p_email text default null) returns public.profiles language plpgsql security definer set search_path=public,pg_temp as $$
declare v public.profiles;
begin if auth.uid() is null then raise exception 'not_authenticated'; end if;
 select * into v from public.profiles where id=auth.uid() for update;
 if not found then insert into public.profiles(id,user_code,full_name,username,phone,email) values(auth.uid(),public.next_public_code('profiles','user_code','U'),trim(p_full_name),trim(p_username),nullif(trim(p_phone),''),nullif(trim(p_email),'')) returning * into v; insert into public.point_balances(user_id) values(auth.uid()); else update public.profiles set full_name=trim(p_full_name),username=trim(p_username),phone=nullif(trim(p_phone),''),email=nullif(trim(p_email),'') where id=auth.uid() returning * into v; end if; return v; end $$;
create or replace function public.add_customer_number(p_phone_e164 text) returns public.customer_numbers language plpgsql security definer set search_path=public,pg_temp as $$
declare v_provider uuid; v_phone public.phone_numbers; v public.customer_numbers; v_norm text;
begin if auth.uid() is null then raise exception 'not_authenticated'; end if; v_norm:=regexp_replace(coalesce(p_phone_e164,''),'[^0-9+]','','g'); if v_norm !~ '^\+[0-9]{7,15}$' then raise exception 'invalid_phone'; end if;
 select provider_id into v_provider from public.telecom_prefixes where status='active' and v_norm like prefix||'%' order by length(prefix) desc limit 1; if v_provider is null then raise exception 'unknown_phone_prefix'; end if;
 insert into public.phone_numbers(phone_e164,normalized_phone,provider_id) values(v_norm,v_norm,v_provider) on conflict(normalized_phone) do update set provider_id=excluded.provider_id,updated_at=now() returning * into v_phone;
 insert into public.customer_numbers(added_number_code,user_id,phone_number_id) values(public.next_public_code('customer_numbers','added_number_code','A'),auth.uid(),v_phone.id) on conflict(user_id,phone_number_id) do update set status='active' returning * into v; return v; end $$;
create or replace function public.submit_points_purchase_request(p_package_id uuid,p_payment_method_id uuid,p_payment_reference text,p_idempotency_key text) returns public.points_purchase_requests language plpgsql security definer set search_path=public,pg_temp as $$
declare p public.points_packages; m public.payment_methods; v public.points_purchase_requests;
begin if auth.uid() is null then raise exception 'not_authenticated'; end if; if nullif(trim(p_payment_reference),'') is null or nullif(trim(p_idempotency_key),'') is null then raise exception 'payment_reference_and_idempotency_required'; end if;
 select * into p from public.points_packages where id=p_package_id and status='active'; if not found then raise exception 'package_unavailable'; end if; select * into m from public.payment_methods where id=p_payment_method_id and status='active'; if not found then raise exception 'payment_method_unavailable'; end if;
 select * into v from public.points_purchase_requests where request_number=trim(p_idempotency_key) for update; if found then return v; end if;
 insert into public.points_purchase_requests(request_number,user_id,package_id,points_amount_snapshot,price_amount_snapshot,currency_snapshot,payment_method_id,payment_method_name_snapshot,payment_account_snapshot,payment_instructions_snapshot,payment_reference)
 values(public.next_public_code('points_purchase_requests','request_number','O'),auth.uid(),p.id,p.points_amount,p.price_amount,p.currency,m.id,m.name,m.account_identifier,m.instructions,trim(p_payment_reference)) returning * into v; return v; end $$;
create or replace function public.approve_points_purchase(p_request_id uuid,p_idempotency_key text) returns public.points_purchase_requests language plpgsql security definer set search_path=public,pg_temp as $$
declare v public.points_purchase_requests; b bigint; s public.subscribers;
begin perform public.require_admin_permission('points_purchases.approve'); select * into v from public.points_purchase_requests where id=p_request_id for update; if not found then raise exception 'purchase_not_found'; end if; if v.status<>'pending' then return v; end if;
 insert into public.subscribers(subscriber_code,user_id,status) values(public.next_public_code('subscribers','subscriber_code','S'),v.user_id,'active') on conflict(user_id) do update set status='active' returning * into s;
 update public.points_purchase_requests set status='approved',subscriber_id=s.id,reviewed_by=auth.uid(),reviewed_at=now() where id=v.id returning * into v;
 insert into public.point_balances(user_id) values(v.user_id) on conflict do nothing; select balance_points into b from public.point_balances where user_id=v.user_id for update; b:=b+v.points_amount_snapshot; update public.point_balances set balance_points=b where user_id=v.user_id;
 insert into public.point_ledger(user_id,entry_type,direction,amount_points,balance_after,reference_type,reference_id,created_by) values(v.user_id,'purchase_credit','credit',v.points_amount_snapshot,b,'points_purchase',v.id,auth.uid());
 insert into public.financial_ledger(entry_type,direction,amount,currency,reference_type,reference_id,created_by) values('points_purchase_income','inflow',v.price_amount_snapshot,v.currency_snapshot,'points_purchase',v.id,auth.uid());
 insert into public.operations(operation_type,status,reference_type,reference_id,user_id,subscriber_id,points_delta,money_amount) values('points_purchase','succeeded','points_purchase',v.id,v.user_id,s.id,v.points_amount_snapshot,v.price_amount_snapshot); perform public.write_audit('rpc','points_purchase',v.id,null,to_jsonb(v)); return v; end $$;
create or replace function public.reject_points_purchase(p_request_id uuid,p_rejection_reason text,p_idempotency_key text) returns public.points_purchase_requests language plpgsql security definer set search_path=public,pg_temp as $$ declare v public.points_purchase_requests; begin perform public.require_admin_permission('points_purchases.reject'); if nullif(trim(p_rejection_reason),'') is null then raise exception 'rejection_reason_required'; end if; select * into v from public.points_purchase_requests where id=p_request_id for update; if not found or v.status<>'pending' then raise exception 'invalid_purchase_state'; end if; update public.points_purchase_requests set status='rejected',rejection_reason=trim(p_rejection_reason),reviewed_by=auth.uid(),reviewed_at=now() where id=v.id returning * into v; perform public.write_audit('rpc','points_purchase',v.id,null,to_jsonb(v)); return v; end $$;
create or replace function public.cancel_points_purchase(p_request_id uuid) returns public.points_purchase_requests language plpgsql security definer set search_path=public,pg_temp as $$ declare v public.points_purchase_requests; begin select * into v from public.points_purchase_requests where id=p_request_id and user_id=auth.uid() for update; if not found or v.status<>'pending' then raise exception 'invalid_purchase_state'; end if; update public.points_purchase_requests set status='cancelled' where id=v.id returning * into v; return v; end $$;
create or replace function public.resubmit_points_purchase(p_request_id uuid,p_package_id uuid,p_payment_method_id uuid,p_payment_reference text) returns public.points_purchase_requests language plpgsql security definer set search_path=public,pg_temp as $$ declare v public.points_purchase_requests; p public.points_packages; m public.payment_methods; begin select * into v from public.points_purchase_requests where id=p_request_id and user_id=auth.uid() for update; if not found or v.status<>'rejected' then raise exception 'invalid_purchase_state'; end if; select * into p from public.points_packages where id=p_package_id and status='active'; if not found then raise exception 'package_unavailable'; end if; select * into m from public.payment_methods where id=p_payment_method_id and status='active'; if not found then raise exception 'payment_method_unavailable'; end if; update public.points_purchase_requests set package_id=p.id,points_amount_snapshot=p.points_amount,price_amount_snapshot=p.price_amount,currency_snapshot=p.currency,payment_method_id=m.id,payment_method_name_snapshot=m.name,payment_account_snapshot=m.account_identifier,payment_instructions_snapshot=m.instructions,payment_reference=trim(p_payment_reference),status='pending',rejection_reason=null where id=v.id returning * into v; return v; end $$;
create or replace function public.rebuild_task_plan(p_protection_id uuid,p_reason text default null) returns public.protection_task_plans language plpgsql security definer set search_path=public,pg_temp as $$ declare v public.protection_task_plans; p public.protections; s public.task_settings; d timestamptz; n int:=0; begin select * into p from public.protections where id=p_protection_id for update; if not found then raise exception 'protection_not_found'; end if; select * into s from public.task_settings where provider_id=p.provider_id; if not found then raise exception 'task_settings_not_found'; end if; insert into public.protection_task_plans(protection_id,anchor_date,interval_days,planned_until) values(p.id,p.started_at,s.interval_days,p.expires_at) on conflict(protection_id) do update set interval_days=excluded.interval_days,planned_until=excluded.planned_until,version=public.protection_task_plans.version+1,last_rebuilt_at=now() returning * into v; delete from public.payment_tasks where plan_id=v.id and status='open' and due_at>=now(); d:=v.anchor_date; while d<p.expires_at loop if d>=now() and s.auto_create then n:=n+1; insert into public.payment_tasks(task_code,plan_id,protection_id,phone_number_id,subscriber_id,provider_id,due_at,original_due_at,amount_snapshot,currency_snapshot,sequence_no) values(public.next_public_code('payment_tasks','task_code','T'),v.id,p.id,(select cn.phone_number_id from public.activated_numbers an join public.customer_numbers cn on cn.id=an.customer_number_id where an.id=p.activated_number_id),p.subscriber_id,p.provider_id,d,d,s.task_amount,s.currency,n) on conflict do nothing; end if; d:=d+make_interval(days=>s.interval_days); end loop; update public.protection_task_plans set planned_task_count=n where id=v.id returning * into v; return v; end $$;

-- Remaining V7 mutation names are present with canonical signatures. Their detailed business paths are explicit UNRESOLVED in the phase status where V7 does not define a complete SQL signature.
create or replace function public.activate_protection(p_activated_number_id uuid,p_duration_days integer,p_idempotency_key text) returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$ begin raise exception 'UNRESOLVED: activation transaction requires final tariff/ledger policy verification'; end $$;
create or replace function public.extend_protection(p_protection_id uuid,p_extension_days integer,p_idempotency_key text) returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$ begin raise exception 'UNRESOLVED: extension transaction requires final tariff/ledger policy verification'; end $$;
create or replace function public.renew_protection(p_activated_number_id uuid,p_duration_days integer,p_confirmed boolean,p_idempotency_key text) returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$ begin raise exception 'UNRESOLVED: renewal task-anchor policy needs executable acceptance verification'; end $$;
create or replace function public.reschedule_payment_task(p_task_id uuid,p_new_due_at timestamptz,p_reason text default null) returns public.payment_tasks language plpgsql security definer set search_path=public,pg_temp as $$ begin raise exception 'UNRESOLVED: reschedule history/anchor execution requires runtime verification'; end $$;
create or replace function public.execute_payment_task(p_task_id uuid,p_external_reference text,p_idempotency_key text) returns public.payment_tasks language plpgsql security definer set search_path=public,pg_temp as $$ begin raise exception 'UNRESOLVED: task financial posting requires configured operational policy verification'; end $$;
create or replace function public.cancel_payment_task(p_task_id uuid,p_reason text) returns public.payment_tasks language plpgsql security definer set search_path=public,pg_temp as $$ begin raise exception 'UNRESOLVED: cancellation rebuild policy requires runtime verification'; end $$;
create or replace function public.mark_notification_read(p_notification_id uuid) returns public.system_notifications language plpgsql security definer set search_path=public,pg_temp as $$ declare v public.system_notifications; begin update public.system_notifications set is_read=true,read_at=coalesce(read_at,now()) where id=p_notification_id and user_id=auth.uid() returning * into v; if not found then raise exception 'notification_not_found'; end if; return v; end $$;
create or replace function public.send_admin_notification(p_type text,p_target_type public.admin_notification_target,p_target_id uuid,p_title text,p_body text) returns public.admin_notifications language plpgsql security definer set search_path=public,pg_temp as $$ declare v public.admin_notifications; begin perform public.require_admin_permission('notifications.send'); insert into public.admin_notifications(sender_admin_id,target_type,target_id,title,body,status,sent_at) values(auth.uid(),p_target_type,p_target_id,p_title,p_body,'sent',now()) returning * into v; perform public.write_audit('rpc','admin_notification',v.id,null,to_jsonb(v)); return v; end $$;
create or replace function public.admin_adjust_points(p_user_id uuid,p_amount_points bigint,p_reason text,p_idempotency_key text) returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$ begin perform public.require_admin_permission('customers.points_adjust'); if p_amount_points=0 then raise exception 'amount_required'; end if; raise exception 'UNRESOLVED: configured monetary value per point is not defined in V7'; end $$;
create or replace function public.set_maintenance_mode(p_enabled boolean,p_customer_message text) returns public.maintenance_config language plpgsql security definer set search_path=public,pg_temp as $$ declare v public.maintenance_config; begin perform public.require_admin_permission('system.maintenance'); insert into public.maintenance_config(enabled,customer_message,updated_by) values(p_enabled,p_customer_message,auth.uid()) on conflict(singleton_key) do update set enabled=excluded.enabled,customer_message=excluded.customer_message,updated_by=excluded.updated_by,updated_at=now() returning * into v; perform public.write_audit('rpc','maintenance_config',v.id,null,to_jsonb(v)); return v; end $$;
create or replace function public.create_support_thread(p_subject text,p_body text default null) returns public.support_threads language plpgsql security definer set search_path=public,pg_temp as $$ declare v public.support_threads; begin insert into public.support_threads(user_id,subject) values(auth.uid(),trim(p_subject)) returning * into v; if nullif(trim(p_body),'') is not null then insert into public.support_messages(thread_id,sender_type,sender_id,body) values(v.id,'customer',auth.uid(),trim(p_body)); end if; return v; end $$;
create or replace function public.send_support_message(p_thread_id uuid,p_body text) returns public.support_messages language plpgsql security definer set search_path=public,pg_temp as $$ declare v public.support_messages; begin if not exists(select 1 from public.support_threads where id=p_thread_id and (user_id=auth.uid() or public.admin_has_permission('customers.read'))) then raise exception 'thread_forbidden'; end if; insert into public.support_messages(thread_id,sender_type,sender_id,body) values(p_thread_id,case when public.admin_has_permission('customers.read') then 'admin' else 'customer' end,auth.uid(),trim(p_body)) returning * into v; update public.support_threads set updated_at=now() where id=p_thread_id; return v; end $$;
create or replace function public.close_support_thread(p_thread_id uuid) returns public.support_threads language plpgsql security definer set search_path=public,pg_temp as $$ declare v public.support_threads; begin if not exists(select 1 from public.support_threads where id=p_thread_id and (user_id=auth.uid() or public.admin_has_permission('customers.update'))) then raise exception 'thread_forbidden'; end if; update public.support_threads set status='closed',updated_at=now() where id=p_thread_id returning * into v; perform public.write_audit('rpc','support_thread',v.id,null,to_jsonb(v)); return v; end $$;

-- Indexes required by the canonical query paths
create index idx_customer_numbers_user on public.customer_numbers(user_id);
create index idx_activated_numbers_subscriber on public.activated_numbers(subscriber_id);
create index idx_protections_activation on public.protections(activated_number_id);
create index idx_point_ledger_user_created on public.point_ledger(user_id,created_at desc);
create index idx_payment_tasks_status_due on public.payment_tasks(status,due_at);
create index idx_payment_tasks_protection on public.payment_tasks(protection_id);
create index idx_system_notifications_user_read on public.system_notifications(user_id,is_read,created_at desc);
create index idx_operation_logs_operation on public.operation_logs(operation_id,created_at desc);
create index idx_audit_logs_entity on public.audit_logs(entity_type,entity_id,created_at desc);

-- RLS: no direct customer writes to sensitive objects; policies are unique and canonical.
do $$ declare t text; begin foreach t in array array['profiles','subscribers','customer_numbers','phone_numbers','points_packages','payment_methods','points_purchase_requests','point_balances','point_ledger','activated_numbers','protections','protection_extensions','operations','task_settings','protection_task_plans','payment_tasks','financial_ledger','aman_financial_balance','expenses','system_notifications','admin_notifications','support_threads','support_messages','operation_logs','audit_logs','maintenance_config'] loop execute format('alter table public.%I enable row level security',t); end loop; end $$;
create policy profiles_self_read on public.profiles for select using(id=auth.uid() or public.admin_has_permission('users.read'));
create policy subscribers_self_read on public.subscribers for select using(user_id=auth.uid() or public.admin_has_permission('customers.read'));
create policy customer_numbers_self_read on public.customer_numbers for select using(user_id=auth.uid() or public.admin_has_permission('numbers.read'));
create policy phone_numbers_related_read on public.phone_numbers for select using(exists(select 1 from public.customer_numbers c where c.phone_number_id=id and c.user_id=auth.uid()) or public.admin_has_permission('numbers.read'));
create policy packages_active_read on public.points_packages for select using(status='active' or public.admin_has_permission('packages.read'));
create policy payment_methods_active_read on public.payment_methods for select using(status='active' or public.admin_has_permission('payment_methods.read'));
create policy purchases_owner_admin_read on public.points_purchase_requests for select using(user_id=auth.uid() or public.admin_has_permission('points_purchases.read'));
create policy point_balances_owner_admin_read on public.point_balances for select using(user_id=auth.uid() or public.admin_has_permission('customers.read'));
create policy point_ledger_owner_admin_read on public.point_ledger for select using(user_id=auth.uid() or public.admin_has_permission('customers.read'));
create policy activated_numbers_owner_admin_read on public.activated_numbers for select using(subscriber_id in(select id from public.subscribers where user_id=auth.uid()) or public.admin_has_permission('customers.read'));
create policy protections_owner_admin_read on public.protections for select using(subscriber_id in(select id from public.subscribers where user_id=auth.uid()) or public.admin_has_permission('customers.read'));
create policy system_notifications_owner_read on public.system_notifications for select using(user_id=auth.uid() or public.admin_has_permission('notifications.read'));
create policy support_threads_participant_read on public.support_threads for select using(user_id=auth.uid() or public.admin_has_permission('customers.read'));
create policy support_messages_participant_read on public.support_messages for select using(exists(select 1 from public.support_threads t where t.id=thread_id and (t.user_id=auth.uid() or public.admin_has_permission('customers.read'))));
create policy admin_notifications_admin_read on public.admin_notifications for select using(public.admin_has_permission('notifications.read'));
create policy payment_tasks_admin_read on public.payment_tasks for select using(public.admin_has_permission('tasks.read'));
create policy task_plans_admin_read on public.protection_task_plans for select using(public.admin_has_permission('tasks.read'));
create policy financial_ledger_admin_read on public.financial_ledger for select using(public.admin_has_permission('finance.read'));
create policy financial_balance_admin_read on public.aman_financial_balance for select using(public.admin_has_permission('finance.read'));
create policy expenses_admin_read on public.expenses for select using(public.admin_has_permission('finance.read'));
create policy operation_logs_admin_read on public.operation_logs for select using(public.admin_has_permission('audit.read'));
create policy audit_logs_admin_read on public.audit_logs for select using(public.admin_has_permission('audit.read'));
create policy maintenance_read on public.maintenance_config for select using(auth.uid() is not null);

-- Only RPCs write sensitive state; direct grants are intentionally absent.
revoke all on all tables in schema public from anon;
grant select on public.profiles,public.subscribers,public.customer_numbers,public.phone_numbers,public.points_packages,public.payment_methods,public.points_purchase_requests,public.point_balances,public.point_ledger,public.activated_numbers,public.protections,public.system_notifications,public.support_threads,public.support_messages,public.maintenance_config to authenticated;

commit;
