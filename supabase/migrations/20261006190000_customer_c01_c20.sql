-- AMAN customer C01-C20 alignment, applied AFTER AMAN_V11_DATABASE_FINAL.sql.
-- This file is a migration artifact only; it has NOT been run against Supabase.
begin;

-- Protection pricing is configured by management per whole tariff unit.
-- DAILY/WEEKLY/MONTHLY/YEARLY are modes, while unit length and point price
-- are explicit snapshots. Existing historical monetary `rate` is preserved.
alter table public.protection_tariff
  add column if not exists duration_unit_days integer;
alter table public.protection_tariff
  add column if not exists points_per_unit bigint;
update public.protection_tariff
set duration_unit_days = case tariff_mode
  when 'DAILY' then 1 when 'WEEKLY' then 7 when 'MONTHLY' then 30 when 'YEARLY' then 365
end
where duration_unit_days is null;
alter table public.protection_tariff alter column duration_unit_days set not null;
do $$ begin
  if not exists (select 1 from pg_constraint where conname='protection_tariff_unit_days_ck') then
    alter table public.protection_tariff add constraint protection_tariff_unit_days_ck
      check (duration_unit_days between 1 and 36500);
  end if;
  if not exists (select 1 from pg_constraint where conname='protection_tariff_points_per_unit_ck') then
    alter table public.protection_tariff add constraint protection_tariff_points_per_unit_ck
      check (points_per_unit is null or points_per_unit > 0);
  end if;
end $$;

alter table public.protection_period add column if not exists units_snapshot integer;
alter table public.protection_period add column if not exists duration_unit_days_snapshot integer;
alter table public.protection_period add column if not exists points_per_unit_snapshot bigint;
alter table public.protection_extension add column if not exists tariff_id uuid references public.protection_tariff(id) on delete restrict;
alter table public.protection_extension add column if not exists units_added integer;
alter table public.protection_extension add column if not exists duration_unit_days_snapshot integer;
alter table public.protection_extension add column if not exists points_per_unit_snapshot bigint;
do $$ begin
  if not exists (select 1 from pg_constraint where conname='protection_period_units_snapshot_ck') then
    alter table public.protection_period add constraint protection_period_units_snapshot_ck
      check (units_snapshot is null or units_snapshot > 0);
  end if;
  if not exists (select 1 from pg_constraint where conname='protection_extension_units_added_ck') then
    alter table public.protection_extension add constraint protection_extension_units_added_ck
      check (units_added is null or units_added > 0);
  end if;
end $$;
create index if not exists idx_protection_tariff_company_active
  on public.protection_tariff(telecom_company_id,status,effective_from,effective_to);

-- Private helper; never returns a customer id supplied by the client.
create or replace function public.current_customer_profile_id()
returns uuid
language sql
stable
security definer
set search_path=public
as $$
  select cp.id
  from public.customer_profile cp
  join public.auth_account aa on aa.id=cp.auth_account_id
  where aa.auth_user_id=auth.uid() and cp.account_status='ACTIVE'
  limit 1
$$;
revoke all on function public.current_customer_profile_id() from public, anon;
grant execute on function public.current_customer_profile_id() to authenticated;

-- One authenticated read model for the customer app. All operational arrays
-- are filtered by the verified auth.uid() profile; no customer_id is accepted.
create or replace function public.get_customer_screen_data(
  p_screen_id text,
  p_query text default null
)
returns jsonb
language plpgsql
stable
security definer
set search_path=public
as $$
declare
  v_customer_id uuid;
  v_profile jsonb;
  v_balance jsonb;
  v_numbers jsonb;
  v_candidates jsonb;
  v_protections jsonb;
  v_tariffs jsonb;
  v_prefixes jsonb;
  v_packages jsonb;
  v_methods jsonb;
  v_purchases jsonb;
  v_ledger jsonb;
  v_operations jsonb;
  v_notifications jsonb;
  v_threads jsonb;
  v_messages jsonb;
  v_admin_messages jsonb;
  v_content jsonb;
  v_maintenance jsonb;
begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if;
  if p_screen_id not in ('C04','C05','C06','C07','C08','C09','C10','C11','C12','C13','C14','C15','C16','C17','C18','C19') then
    raise exception 'INVALID_CUSTOMER_SCREEN';
  end if;
  v_customer_id := public.current_customer_profile_id();
  if v_customer_id is null then raise exception 'CUSTOMER_NOT_FOUND'; end if;

  select coalesce(jsonb_agg(to_jsonb(cp) - 'auth_account_id'), '[]'::jsonb) into v_profile
  from public.customer_profile cp where cp.id=v_customer_id;
  select coalesce(jsonb_agg(jsonb_build_object('balance',pb.balance,'updated_at',pb.updated_at)), '[]'::jsonb) into v_balance
  from public.points_balance pb where pb.customer_id=v_customer_id;

  select coalesce(jsonb_agg(x.row_data order by x.added_at desc), '[]'::jsonb) into v_numbers
  from (
    select cn.added_at,
      jsonb_build_object(
        'id',cn.id,'customer_id',cn.customer_id,'phone_number_id',pn.id,
        'telecom_company_id',pn.telecom_company_id,'public_added_number_code',cn.public_added_number_code,
        'status',cn.status::text,'added_at',cn.added_at,'updated_at',cn.updated_at,
        'display_phone',pn.display_phone,'normalized_phone',pn.normalized_phone,
        'company_name',tc.name,
        'active_protection',exists(select 1 from public.number_protection_identity ni
          join public.protection_period pp on pp.protection_identity_id=ni.id
          where ni.customer_number_id=cn.id and pp.status='ACTIVE' and pp.end_at>now())
      ) as row_data
    from public.customer_number cn
    join public.phone_number pn on pn.id=cn.phone_number_id
    join public.telecom_company tc on tc.id=pn.telecom_company_id
    where cn.customer_id=v_customer_id
    order by cn.added_at desc limit 500
  ) x;

  select coalesce(jsonb_agg(x.row_data order by x.added_at desc), '[]'::jsonb) into v_candidates
  from (
    select cn.added_at,
      jsonb_build_object('id',cn.id,'customer_number_id',cn.id,'customer_id',cn.customer_id,
        'telecom_company_id',pn.telecom_company_id,'public_added_number_code',cn.public_added_number_code,
        'display_phone',pn.display_phone,'normalized_phone',pn.normalized_phone,'company_name',tc.name,'status',cn.status::text) as row_data
    from public.customer_number cn
    join public.phone_number pn on pn.id=cn.phone_number_id
    join public.telecom_company tc on tc.id=pn.telecom_company_id
    where cn.customer_id=v_customer_id and cn.status='ACTIVE'
      and not exists(select 1 from public.number_protection_identity ni
        join public.protection_period pp on pp.protection_identity_id=ni.id
        where ni.customer_number_id=cn.id and pp.status='ACTIVE' and pp.end_at>now())
    order by cn.added_at desc limit 500
  ) x;

  select coalesce(jsonb_agg(x.row_data order by x.start_at desc), '[]'::jsonb) into v_protections
  from (
    select pp.start_at,
      jsonb_build_object('id',pp.id,'protection_identity_id',ni.id,'customer_number_id',cn.id,
        'telecom_company_id',pn.telecom_company_id,'public_activation_code',ni.public_activation_code,
        'display_phone',pn.display_phone,'normalized_phone',pn.normalized_phone,'company_name',tc.name,
        'status',case when pp.end_at<=now() then 'EXPIRED' else pp.status::text end,
        'effective_status',case when pp.end_at<=now() then 'EXPIRED' else pp.status::text end,
        'start_at',pp.start_at,'end_at',pp.end_at,'duration_days',pp.duration_days,
        'tariff_id',pp.tariff_id,'tariff_mode_snapshot',pp.tariff_mode_snapshot::text,
        'tariff_value_snapshot',pp.tariff_value_snapshot,'currency_snapshot',pp.currency_snapshot,
        'points_cost_snapshot',pp.points_cost_snapshot,'units_snapshot',pp.units_snapshot,
        'duration_unit_days_snapshot',pp.duration_unit_days_snapshot,
        'points_per_unit_snapshot',pp.points_per_unit_snapshot)
      as row_data
    from public.protection_period pp
    join public.number_protection_identity ni on ni.id=pp.protection_identity_id
    join public.customer_number cn on cn.id=ni.customer_number_id
    join public.phone_number pn on pn.id=cn.phone_number_id
    join public.telecom_company tc on tc.id=pn.telecom_company_id
    where pp.customer_id=v_customer_id
    order by pp.start_at desc limit 500
  ) x;

  select coalesce(jsonb_agg(jsonb_build_object('id',t.id,'telecom_company_id',t.telecom_company_id,
    'tariff_mode',t.tariff_mode::text,'duration_unit_days',t.duration_unit_days,
    'points_per_unit',t.points_per_unit,'rate',t.rate,'currency',t.currency,
    'effective_from',t.effective_from,'effective_to',t.effective_to,'status',t.status::text,
    'company_name',tc.name) order by tc.name,t.duration_unit_days), '[]'::jsonb) into v_tariffs
  from public.protection_tariff t
  join public.telecom_company tc on tc.id=t.telecom_company_id
  where t.status='ACTIVE' and t.effective_from<=now() and (t.effective_to is null or t.effective_to>now())
    and t.telecom_company_id in (select pn.telecom_company_id from public.customer_number cn
      join public.phone_number pn on pn.id=cn.phone_number_id where cn.customer_id=v_customer_id);

  select coalesce(jsonb_agg(jsonb_build_object('id',tp.id,'telecom_company_id',tp.telecom_company_id,
    'prefix',tp.prefix,'status',tp.status::text,'telecom_company',jsonb_build_object('name',tc.name))), '[]'::jsonb) into v_prefixes
  from public.telecom_prefix tp join public.telecom_company tc on tc.id=tp.telecom_company_id
  where tp.status='ACTIVE' and tc.status='ACTIVE';

  select coalesce(jsonb_agg(jsonb_build_object('id',p.id,'code',p.code,'name',p.name,
    'points',p.points,'price',p.price,'currency',p.currency,'display_order',p.display_order)
    order by p.display_order,p.name), '[]'::jsonb) into v_packages
  from public.points_package p where p.is_active=true and p.is_visible=true;
  select coalesce(jsonb_agg(jsonb_build_object('id',m.id,'code',m.code,'name',m.name,'type',m.type,
    'receiving_account',m.receiving_account,'transfer_instructions',m.transfer_instructions)
    order by m.display_order,m.name), '[]'::jsonb) into v_methods
  from public.payment_method m where m.is_active=true and m.is_visible=true;

  select coalesce(jsonb_agg(jsonb_build_object('id',p.id,'public_purchase_code',p.public_purchase_code,
    'package_name_snapshot',p.package_name_snapshot,'points_snapshot',p.points_snapshot,
    'price_snapshot',p.price_snapshot,'currency_snapshot',p.currency_snapshot,
    'payment_method_name_snapshot',p.payment_method_name_snapshot,'transfer_reference',p.transfer_reference,
    'status',p.status::text,'rejection_reason',p.rejection_reason,'submitted_at',p.submitted_at)
    order by p.submitted_at desc), '[]'::jsonb) into v_purchases
  from (select * from public.points_purchase where customer_id=v_customer_id order by submitted_at desc limit 500) p;

  select coalesce(jsonb_agg(jsonb_build_object('id',l.id,'direction',l.direction::text,'amount',l.amount,
    'balance_before',l.balance_before,'balance_after',l.balance_after,'entry_type',l.entry_type::text,
    'source_type',l.source_type,'source_id',l.source_id,'description',l.description,'created_at',l.created_at)
    order by l.created_at desc), '[]'::jsonb) into v_ledger
  from (select * from public.points_ledger where customer_id=v_customer_id order by created_at desc limit 1000) l;

  select coalesce(jsonb_agg(jsonb_build_object('id',o.id,'operation_type',o.operation_type,
    'entity_type',o.entity_type,'entity_id',o.entity_id,'status',o.status::text,
    'result_reference',o.result_reference,'created_at',o.created_at,'completed_at',o.completed_at)
    order by o.created_at desc), '[]'::jsonb) into v_operations
  from (select * from public.operation where actor_type='CUSTOMER' and actor_id=v_customer_id order by created_at desc limit 100) o;

  select coalesce(jsonb_agg(jsonb_build_object('id',n.id,'notification_type',n.notification_type,
    'title',n.title,'body',n.body,'reference_type',n.reference_type,'reference_id',n.reference_id,
    'is_read',n.is_read,'created_at',n.created_at,'read_at',n.read_at) order by n.created_at desc), '[]'::jsonb) into v_notifications
  from (select * from public.customer_notification where customer_id=v_customer_id order by created_at desc limit 500) n;

  select coalesce(jsonb_agg(jsonb_build_object('id',c.id,'subject',c.subject,'status',c.status::text,
    'created_at',c.created_at,'updated_at',c.updated_at,'closed_at',c.closed_at) order by c.updated_at desc), '[]'::jsonb) into v_threads
  from (select * from public.support_conversation where customer_id=v_customer_id order by updated_at desc limit 200) c;
  select coalesce(jsonb_agg(jsonb_build_object('id',m.id,'conversation_id',m.conversation_id,
    'sender_type',m.sender_type::text,'body',m.body,'sent_at',m.sent_at,'read_at',m.read_at)
    order by m.sent_at), '[]'::jsonb) into v_messages
  from public.support_message m join public.support_conversation c on c.id=m.conversation_id
  where c.customer_id=v_customer_id;

  select coalesce(jsonb_agg(jsonb_build_object('id',m.id,'thread_id',m.thread_id,
    'title','رسالة من إدارة أمان','body',m.body,'sent_at',m.sent_at,'read_at',m.read_at,
    'is_read',m.read_at is not null) order by m.sent_at desc), '[]'::jsonb) into v_admin_messages
  from public.admin_message m join public.admin_message_thread t on t.id=m.thread_id
  where t.customer_id=v_customer_id;

  select coalesce(jsonb_agg(jsonb_build_object('id',c.id,'content_key',c.content_key,
    'version',c.version,'title',c.title,'body',c.body,'published_at',c.published_at)
    order by c.content_key), '[]'::jsonb) into v_content
  from (select distinct on (content_key) * from public.app_content
    where status='ACTIVE' and content_key in ('ABOUT','TERMS','PRIVACY')
    order by content_key,version desc) c;
  select coalesce(jsonb_agg(jsonb_build_object('id',m.id,'enabled',m.enabled,'customer_message',m.customer_message,
    'updated_at',m.updated_at) order by m.updated_at desc), '[]'::jsonb) into v_maintenance
  from (select * from public.maintenance_config order by updated_at desc limit 1) m;

  return jsonb_build_object(
    'profile',v_profile,'balance',v_balance,'numbers',v_numbers,'candidates',v_candidates,
    'protections',v_protections,'tariffs',v_tariffs,'prefixes',v_prefixes,
    'packages',v_packages,'methods',v_methods,'purchases',v_purchases,'ledger',v_ledger,
    'operations',v_operations,'notifications',v_notifications,'threads',v_threads,
    'messages',v_messages,'admin_messages',v_admin_messages,'content',v_content,'maintenance',v_maintenance
  );
end;
$$;

-- Public, read-only legal/application content for sign-up and recovery screens.
create or replace function public.get_public_content(p_content_key text default 'ALL')
returns jsonb
language sql
stable
security definer
set search_path=public
as $$
  select jsonb_build_object('content',coalesce(jsonb_agg(jsonb_build_object(
    'id',c.id,'content_key',c.content_key,'version',c.version,'title',c.title,'body',c.body,'published_at',c.published_at
  ) order by c.content_key),'[]'::jsonb))
  from (select distinct on (content_key) * from public.app_content
    where status='ACTIVE'
      and (upper(coalesce(p_content_key,'ALL'))='ALL' or content_key=upper(p_content_key))
      and content_key in ('ABOUT','TERMS','PRIVACY')
    order by content_key,version desc) c
$$;

-- Update the customer's editable profile field only (email/auth identity remain Auth-owned).
create or replace function public.update_customer_profile(p_name text,p_idempotency_key text)
returns jsonb language plpgsql security definer set search_path=public as $$
declare v_customer_id uuid; v_profile public.customer_profile; v_op uuid; v_before jsonb;
begin
  v_customer_id:=public.current_customer_profile_id();
  if v_customer_id is null then raise exception 'CUSTOMER_NOT_FOUND'; end if;
  if nullif(trim(p_name),'') is null or length(trim(p_name))>120 then raise exception 'INVALID_NAME'; end if;
  if nullif(trim(p_idempotency_key),'') is null then raise exception 'IDEMPOTENCY_KEY_REQUIRED'; end if;
  select o.id into v_op from public.operation_idempotency i join public.operation o on o.id=i.operation_id
    where i.actor_id=v_customer_id and i.operation_type='UPDATE_CUSTOMER_PROFILE' and i.idempotency_key=p_idempotency_key;
  if v_op is not null then return jsonb_build_object('operation_id',v_op,'idempotent',true); end if;
  select * into v_profile from public.customer_profile where id=v_customer_id for update;
  v_before:=jsonb_build_object('name',v_profile.name);
  update public.customer_profile set name=trim(p_name),updated_at=now() where id=v_customer_id returning * into v_profile;
  insert into public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,idempotency_key,result_reference,completed_at)
    values(public.generate_public_code('OP'),'UPDATE_CUSTOMER_PROFILE','CUSTOMER',v_customer_id,'customer_profile',v_customer_id,'COMPLETED',p_idempotency_key,v_customer_id::text,now()) returning id into v_op;
  insert into public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id) values(v_customer_id,'UPDATE_CUSTOMER_PROFILE',p_idempotency_key,v_op);
  insert into public.audit_log(actor_type,actor_id,operation_id,entity_type,entity_id,action,before_state,after_state)
    values('CUSTOMER',v_customer_id,v_op,'customer_profile',v_customer_id,'UPDATE_NAME',v_before,jsonb_build_object('name',v_profile.name));
  return jsonb_build_object('customer_id',v_customer_id,'name',v_profile.name,'operation_id',v_op);
end $$;

create or replace function public.update_customer_number(p_customer_number_id uuid,p_phone text,p_idempotency_key text)
returns jsonb language plpgsql security definer set search_path=public as $$
declare v_customer_id uuid; v_number public.customer_number; v_phone text; v_company_id uuid; v_phone_id uuid; v_op uuid; v_before jsonb;
begin
  v_customer_id:=public.current_customer_profile_id();
  if v_customer_id is null then raise exception 'CUSTOMER_NOT_FOUND'; end if;
  if nullif(trim(p_idempotency_key),'') is null then raise exception 'IDEMPOTENCY_KEY_REQUIRED'; end if;
  select o.id into v_op from public.operation_idempotency i join public.operation o on o.id=i.operation_id
    where i.actor_id=v_customer_id and i.operation_type='UPDATE_CUSTOMER_NUMBER' and i.idempotency_key=p_idempotency_key;
  if v_op is not null then return jsonb_build_object('operation_id',v_op,'idempotent',true); end if;
  select * into v_number from public.customer_number where id=p_customer_number_id and customer_id=v_customer_id for update;
  if v_number.id is null then raise exception 'CUSTOMER_NUMBER_NOT_FOUND'; end if;
  if exists(select 1 from public.number_protection_identity where customer_number_id=v_number.id) then raise exception 'NUMBER_HAS_PROTECTION_HISTORY'; end if;
  v_phone:=public.normalize_phone(p_phone);
  if length(v_phone)<7 then raise exception 'INVALID_PHONE'; end if;
  select telecom_company_id into v_company_id from public.telecom_prefix
    where status='ACTIVE' and v_phone like prefix||'%' order by length(prefix) desc limit 1;
  if v_company_id is null then raise exception 'TELECOM_COMPANY_NOT_DETECTED'; end if;
  if exists(select 1 from public.customer_number other join public.phone_number pn on pn.id=other.phone_number_id
      where other.customer_id=v_customer_id and other.id<>v_number.id and pn.normalized_phone=v_phone) then
    raise exception 'NUMBER_ALREADY_LINKED';
  end if;
  select to_jsonb(pn) into v_before from public.phone_number pn where pn.id=v_number.phone_number_id;
  insert into public.phone_number(normalized_phone,display_phone,telecom_company_id)
    values(v_phone,trim(p_phone),v_company_id)
    on conflict(normalized_phone) do update set display_phone=excluded.display_phone,telecom_company_id=excluded.telecom_company_id,updated_at=now()
    returning id into v_phone_id;
  update public.customer_number set phone_number_id=v_phone_id,updated_at=now() where id=v_number.id;
  insert into public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,idempotency_key,result_reference,completed_at)
    values(public.generate_public_code('OP'),'UPDATE_CUSTOMER_NUMBER','CUSTOMER',v_customer_id,'customer_number',v_number.id,'COMPLETED',p_idempotency_key,v_number.id::text,now()) returning id into v_op;
  insert into public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id) values(v_customer_id,'UPDATE_CUSTOMER_NUMBER',p_idempotency_key,v_op);
  insert into public.audit_log(actor_type,actor_id,operation_id,entity_type,entity_id,action,before_state,after_state)
    values('CUSTOMER',v_customer_id,v_op,'customer_number',v_number.id,'UPDATE_PHONE',v_before,jsonb_build_object('normalized_phone',v_phone,'telecom_company_id',v_company_id));
  return jsonb_build_object('customer_number_id',v_number.id,'phone_number_id',v_phone_id,'operation_id',v_op);
end $$;

create or replace function public.delete_customer_number(p_customer_number_id uuid,p_idempotency_key text)
returns jsonb language plpgsql security definer set search_path=public as $$
declare v_customer_id uuid; v_number public.customer_number; v_op uuid; v_before jsonb;
begin
  v_customer_id:=public.current_customer_profile_id();
  if v_customer_id is null then raise exception 'CUSTOMER_NOT_FOUND'; end if;
  if nullif(trim(p_idempotency_key),'') is null then raise exception 'IDEMPOTENCY_KEY_REQUIRED'; end if;
  select o.id into v_op from public.operation_idempotency i join public.operation o on o.id=i.operation_id
    where i.actor_id=v_customer_id and i.operation_type='ARCHIVE_CUSTOMER_NUMBER' and i.idempotency_key=p_idempotency_key;
  if v_op is not null then return jsonb_build_object('operation_id',v_op,'idempotent',true); end if;
  select * into v_number from public.customer_number where id=p_customer_number_id and customer_id=v_customer_id for update;
  if v_number.id is null then raise exception 'CUSTOMER_NUMBER_NOT_FOUND'; end if;
  if exists(select 1 from public.number_protection_identity where customer_number_id=v_number.id) then raise exception 'NUMBER_HAS_PROTECTION_HISTORY'; end if;
  v_before:=jsonb_build_object('status',v_number.status::text);
  update public.customer_number set status='ARCHIVED',updated_at=now() where id=v_number.id;
  insert into public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,idempotency_key,result_reference,completed_at)
    values(public.generate_public_code('OP'),'ARCHIVE_CUSTOMER_NUMBER','CUSTOMER',v_customer_id,'customer_number',v_number.id,'COMPLETED',p_idempotency_key,v_number.id::text,now()) returning id into v_op;
  insert into public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id) values(v_customer_id,'ARCHIVE_CUSTOMER_NUMBER',p_idempotency_key,v_op);
  insert into public.audit_log(actor_type,actor_id,operation_id,entity_type,entity_id,action,before_state,after_state)
    values('CUSTOMER',v_customer_id,v_op,'customer_number',v_number.id,'ARCHIVE',v_before,jsonb_build_object('status','ARCHIVED'));
  return jsonb_build_object('customer_number_id',v_number.id,'status','ARCHIVED','operation_id',v_op);
end $$;

create or replace function public.create_support_conversation(p_subject text,p_body text,p_idempotency_key text)
returns jsonb language plpgsql security definer set search_path=public as $$
declare v_customer_id uuid; v_conversation uuid; v_message uuid; v_op uuid;
begin
  v_customer_id:=public.current_customer_profile_id();
  if v_customer_id is null then raise exception 'CUSTOMER_NOT_FOUND'; end if;
  if nullif(trim(p_subject),'') is null or length(trim(p_subject))>160 then raise exception 'INVALID_SUBJECT'; end if;
  if nullif(trim(p_body),'') is null or length(trim(p_body))>10000 then raise exception 'INVALID_MESSAGE'; end if;
  if nullif(trim(p_idempotency_key),'') is null then raise exception 'IDEMPOTENCY_KEY_REQUIRED'; end if;
  select o.id into v_op from public.operation_idempotency i join public.operation o on o.id=i.operation_id
    where i.actor_id=v_customer_id and i.operation_type='CREATE_SUPPORT_CONVERSATION' and i.idempotency_key=p_idempotency_key;
  if v_op is not null then return jsonb_build_object('operation_id',v_op,'conversation_id',(select entity_id from public.operation where id=v_op),'idempotent',true); end if;
  insert into public.support_conversation(customer_id,subject) values(v_customer_id,trim(p_subject)) returning id into v_conversation;
  insert into public.support_message(conversation_id,sender_type,sender_id,body)
    values(v_conversation,'CUSTOMER',v_customer_id,trim(p_body)) returning id into v_message;
  insert into public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,idempotency_key,result_reference,completed_at)
    values(public.generate_public_code('OP'),'CREATE_SUPPORT_CONVERSATION','CUSTOMER',v_customer_id,'support_conversation',v_conversation,'COMPLETED',p_idempotency_key,v_message::text,now()) returning id into v_op;
  insert into public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id) values(v_customer_id,'CREATE_SUPPORT_CONVERSATION',p_idempotency_key,v_op);
  return jsonb_build_object('conversation_id',v_conversation,'message_id',v_message,'operation_id',v_op);
end $$;

create or replace function public.send_support_message(p_conversation_id uuid,p_body text,p_idempotency_key text)
returns jsonb language plpgsql security definer set search_path=public as $$
declare v_customer_id uuid; v_message uuid; v_op uuid;
begin
  v_customer_id:=public.current_customer_profile_id();
  if v_customer_id is null then raise exception 'CUSTOMER_NOT_FOUND'; end if;
  if nullif(trim(p_body),'') is null or length(trim(p_body))>10000 then raise exception 'INVALID_MESSAGE'; end if;
  if nullif(trim(p_idempotency_key),'') is null then raise exception 'IDEMPOTENCY_KEY_REQUIRED'; end if;
  select o.id into v_op from public.operation_idempotency i join public.operation o on o.id=i.operation_id
    where i.actor_id=v_customer_id and i.operation_type='SEND_SUPPORT_MESSAGE' and i.idempotency_key=p_idempotency_key;
  if v_op is not null then return jsonb_build_object('operation_id',v_op,'idempotent',true); end if;
  perform 1 from public.support_conversation where id=p_conversation_id and customer_id=v_customer_id and status='OPEN' for update;
  if not found then raise exception 'SUPPORT_CONVERSATION_NOT_OPEN_OR_OWNED'; end if;
  insert into public.support_message(conversation_id,sender_type,sender_id,body)
    values(p_conversation_id,'CUSTOMER',v_customer_id,trim(p_body)) returning id into v_message;
  update public.support_conversation set updated_at=now() where id=p_conversation_id;
  insert into public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,idempotency_key,result_reference,completed_at)
    values(public.generate_public_code('OP'),'SEND_SUPPORT_MESSAGE','CUSTOMER',v_customer_id,'support_conversation',p_conversation_id,'COMPLETED',p_idempotency_key,v_message::text,now()) returning id into v_op;
  insert into public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id) values(v_customer_id,'SEND_SUPPORT_MESSAGE',p_idempotency_key,v_op);
  return jsonb_build_object('message_id',v_message,'operation_id',v_op);
end $$;

create or replace function public.close_support_conversation(p_conversation_id uuid)
returns jsonb language plpgsql security definer set search_path=public as $$
declare v_customer_id uuid;
begin
  v_customer_id:=public.current_customer_profile_id();
  if v_customer_id is null then raise exception 'CUSTOMER_NOT_FOUND'; end if;
  update public.support_conversation set status='CLOSED',closed_at=coalesce(closed_at,now()),closed_by=v_customer_id,updated_at=now()
    where id=p_conversation_id and customer_id=v_customer_id and status='OPEN';
  if not found then raise exception 'SUPPORT_CONVERSATION_NOT_OPEN_OR_OWNED'; end if;
  return jsonb_build_object('conversation_id',p_conversation_id,'status','CLOSED');
end $$;

create or replace function public.mark_admin_message_read(p_message_id uuid)
returns jsonb language plpgsql security definer set search_path=public as $$
declare v_customer_id uuid;
begin
  v_customer_id:=public.current_customer_profile_id();
  if v_customer_id is null then raise exception 'CUSTOMER_NOT_FOUND'; end if;
  update public.admin_message m set read_at=coalesce(m.read_at,now())
    from public.admin_message_thread t
    where m.id=p_message_id and t.id=m.thread_id and t.customer_id=v_customer_id;
  if not found then raise exception 'ADMIN_MESSAGE_NOT_FOUND'; end if;
  return jsonb_build_object('message_id',p_message_id,'is_read',true);
end $$;

-- Protection transactions use only admin-configured whole units. The client
-- sends a tariff id and unit count; the server derives days and point cost.
create or replace function public.activate_protection(
  p_customer_number_id uuid,p_tariff_id uuid,p_units integer,p_idempotency_key text
)
returns jsonb language plpgsql security definer set search_path=public as $$
declare
  v_customer_id uuid; v_number public.customer_number; v_company_id uuid; v_tariff public.protection_tariff;
  v_identity_id uuid; v_period_id uuid; v_balance public.points_balance; v_days bigint; v_cost bigint;
  v_start timestamptz:=now(); v_end timestamptz; v_op uuid; v_event uuid;
begin
  v_customer_id:=public.current_customer_profile_id();
  if v_customer_id is null then raise exception 'CUSTOMER_NOT_FOUND'; end if;
  if p_units is null or p_units<1 or p_units>120 then raise exception 'INVALID_TARIFF_UNITS'; end if;
  if nullif(trim(p_idempotency_key),'') is null then raise exception 'IDEMPOTENCY_KEY_REQUIRED'; end if;
  if not exists(select 1 from public.subscriber_identity where customer_profile_id=v_customer_id) then raise exception 'SUBSCRIPTION_REQUIRED'; end if;
  select o.id into v_op from public.operation_idempotency i join public.operation o on o.id=i.operation_id
    where i.actor_id=v_customer_id and i.operation_type='ACTIVATE_PROTECTION' and i.idempotency_key=p_idempotency_key;
  if v_op is not null then return jsonb_build_object('operation_id',v_op,'idempotent',true); end if;
  select * into v_number from public.customer_number where id=p_customer_number_id and customer_id=v_customer_id and status='ACTIVE' for update;
  if v_number.id is null then raise exception 'CUSTOMER_NUMBER_NOT_FOUND_OR_INACTIVE'; end if;
  select pn.telecom_company_id into v_company_id from public.phone_number pn where pn.id=v_number.phone_number_id and pn.status='ACTIVE';
  if v_company_id is null then raise exception 'PHONE_NUMBER_INACTIVE'; end if;
  select * into v_tariff from public.protection_tariff where id=p_tariff_id and telecom_company_id=v_company_id
    and status='ACTIVE' and effective_from<=now() and (effective_to is null or effective_to>now()) for share;
  if v_tariff.id is null then raise exception 'TARIFF_NOT_AVAILABLE'; end if;
  if v_tariff.points_per_unit is null or v_tariff.points_per_unit<=0 then raise exception 'TARIFF_POINTS_NOT_CONFIGURED'; end if;
  v_days:=p_units::bigint*v_tariff.duration_unit_days::bigint;
  v_cost:=p_units::bigint*v_tariff.points_per_unit;
  if v_days<=0 or v_days>3650000 or v_cost<=0 then raise exception 'INVALID_TARIFF_TOTAL'; end if;
  if exists(select 1 from public.number_protection_identity ni join public.protection_period pp on pp.protection_identity_id=ni.id
      where ni.customer_number_id=v_number.id and pp.status='ACTIVE' and pp.end_at>now()) then raise exception 'PROTECTION_ALREADY_ACTIVE'; end if;
  update public.protection_period pp set status='EXPIRED',updated_at=now()
    from public.number_protection_identity ni
    where ni.customer_number_id=v_number.id and pp.protection_identity_id=ni.id and pp.status='ACTIVE' and pp.end_at<=now();
  insert into public.number_protection_identity(customer_number_id,public_activation_code)
    values(v_number.id,public.generate_public_code('A'))
    on conflict(customer_number_id) do update set updated_at=now()
    returning id into v_identity_id;
  insert into public.points_balance(customer_id,balance) values(v_customer_id,0) on conflict(customer_id) do nothing;
  select * into v_balance from public.points_balance where customer_id=v_customer_id for update;
  if v_balance.balance<v_cost then raise exception 'INSUFFICIENT_POINTS'; end if;
  v_end:=v_start+make_interval(days=>v_days::integer);
  update public.points_balance set balance=balance-v_cost,updated_at=now() where id=v_balance.id;
  insert into public.points_ledger(customer_id,direction,amount,balance_before,balance_after,entry_type,source_type,description,created_by)
    values(v_customer_id,'DEBIT',v_cost,v_balance.balance,v_balance.balance-v_cost,'ACTIVATION_DEBIT','protection_period','تفعيل حماية لمدة '||v_days||' يومًا',v_customer_id);
  insert into public.protection_period(protection_identity_id,customer_id,start_at,end_at,duration_days,tariff_id,
    tariff_mode_snapshot,tariff_value_snapshot,currency_snapshot,points_cost_snapshot,status,
    units_snapshot,duration_unit_days_snapshot,points_per_unit_snapshot)
    values(v_identity_id,v_customer_id,v_start,v_end,v_days::integer,v_tariff.id,v_tariff.tariff_mode,
      v_tariff.rate,v_tariff.currency,v_cost,'ACTIVE',p_units,v_tariff.duration_unit_days,v_tariff.points_per_unit)
    returning id into v_period_id;
  insert into public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,idempotency_key,result_reference,completed_at)
    values(public.generate_public_code('OP'),'ACTIVATE_PROTECTION','CUSTOMER',v_customer_id,'protection_period',v_period_id,'COMPLETED',p_idempotency_key,v_period_id::text,now()) returning id into v_op;
  insert into public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id)
    values(v_customer_id,'ACTIVATE_PROTECTION',p_idempotency_key,v_op);
  insert into public.audit_log(actor_type,actor_id,operation_id,entity_type,entity_id,action,after_state)
    values('CUSTOMER',v_customer_id,v_op,'protection_period',v_period_id,'ACTIVATE',jsonb_build_object(
      'customer_number_id',v_number.id,'tariff_id',v_tariff.id,'units',p_units,'duration_days',v_days,'points_cost',v_cost,'balance_after',v_balance.balance-v_cost));
  insert into public.notification_event(event_type,source_type,source_id) values('PROTECTION_ACTIVATED','protection_period',v_period_id) returning id into v_event;
  insert into public.customer_notification(customer_id,event_id,notification_type,title,body,reference_type,reference_id)
    values(v_customer_id,v_event,'PROTECTION','تم تفعيل الحماية','تم تفعيل الحماية للرقم المرتبط بحسابك.','protection_period',v_period_id);
  return jsonb_build_object('protection_period_id',v_period_id,'status','ACTIVE','start_at',v_start,'end_at',v_end,
    'units',p_units,'duration_unit_days',v_tariff.duration_unit_days,'duration_days',v_days,
    'points_per_unit',v_tariff.points_per_unit,'points_cost',v_cost,'balance_after',v_balance.balance-v_cost,'operation_id',v_op);
end $$;

create or replace function public.extend_protection(
  p_protection_period_id uuid,p_tariff_id uuid,p_units integer,p_idempotency_key text
)
returns jsonb language plpgsql security definer set search_path=public as $$
declare
  v_customer_id uuid; v_period public.protection_period; v_number public.customer_number; v_company_id uuid;
  v_tariff public.protection_tariff; v_balance public.points_balance; v_days bigint; v_cost bigint;
  v_old_end timestamptz; v_new_end timestamptz; v_extension uuid; v_op uuid; v_event uuid;
begin
  v_customer_id:=public.current_customer_profile_id();
  if v_customer_id is null then raise exception 'CUSTOMER_NOT_FOUND'; end if;
  if p_units is null or p_units<1 or p_units>120 then raise exception 'INVALID_TARIFF_UNITS'; end if;
  if nullif(trim(p_idempotency_key),'') is null then raise exception 'IDEMPOTENCY_KEY_REQUIRED'; end if;
  if not exists(select 1 from public.subscriber_identity where customer_profile_id=v_customer_id) then raise exception 'SUBSCRIPTION_REQUIRED'; end if;
  select o.id into v_op from public.operation_idempotency i join public.operation o on o.id=i.operation_id
    where i.actor_id=v_customer_id and i.operation_type='EXTEND_PROTECTION' and i.idempotency_key=p_idempotency_key;
  if v_op is not null then return jsonb_build_object('operation_id',v_op,'idempotent',true); end if;
  select * into v_period from public.protection_period where id=p_protection_period_id and customer_id=v_customer_id for update;
  if v_period.id is null then raise exception 'PROTECTION_NOT_FOUND'; end if;
  if v_period.status<>'ACTIVE' or v_period.end_at<=now() then raise exception 'PROTECTION_NOT_ACTIVE'; end if;
  select cn.* into v_number from public.customer_number cn join public.number_protection_identity ni on ni.customer_number_id=cn.id
    where ni.id=v_period.protection_identity_id and cn.customer_id=v_customer_id and cn.status='ACTIVE' for update of cn;
  if v_number.id is null then raise exception 'CUSTOMER_NUMBER_NOT_FOUND_OR_INACTIVE'; end if;
  select pn.telecom_company_id into v_company_id from public.phone_number pn where pn.id=v_number.phone_number_id;
  select * into v_tariff from public.protection_tariff where id=p_tariff_id and telecom_company_id=v_company_id
    and status='ACTIVE' and effective_from<=now() and (effective_to is null or effective_to>now()) for share;
  if v_tariff.id is null then raise exception 'TARIFF_NOT_AVAILABLE'; end if;
  if v_tariff.points_per_unit is null or v_tariff.points_per_unit<=0 then raise exception 'TARIFF_POINTS_NOT_CONFIGURED'; end if;
  v_days:=p_units::bigint*v_tariff.duration_unit_days::bigint; v_cost:=p_units::bigint*v_tariff.points_per_unit;
  if v_days<=0 or v_days>3650000 or v_cost<=0 then raise exception 'INVALID_TARIFF_TOTAL'; end if;
  insert into public.points_balance(customer_id,balance) values(v_customer_id,0) on conflict(customer_id) do nothing;
  select * into v_balance from public.points_balance where customer_id=v_customer_id for update;
  if v_balance.balance<v_cost then raise exception 'INSUFFICIENT_POINTS'; end if;
  v_old_end:=v_period.end_at; v_new_end:=v_old_end+make_interval(days=>v_days::integer);
  update public.points_balance set balance=balance-v_cost,updated_at=now() where id=v_balance.id;
  update public.protection_period set end_at=v_new_end,duration_days=duration_days+v_days::integer,updated_at=now() where id=v_period.id;
  insert into public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,idempotency_key,result_reference,completed_at)
    values(public.generate_public_code('OP'),'EXTEND_PROTECTION','CUSTOMER',v_customer_id,'protection_period',v_period.id,'COMPLETED',p_idempotency_key,v_period.id::text,now()) returning id into v_op;
  insert into public.protection_extension(protection_period_id,days_added,tariff_mode_snapshot,tariff_value_snapshot,
    currency_snapshot,points_cost,balance_before,balance_after,operation_id,tariff_id,units_added,duration_unit_days_snapshot,points_per_unit_snapshot)
    values(v_period.id,v_days::integer,v_tariff.tariff_mode,v_tariff.rate,v_tariff.currency,v_cost,
      v_balance.balance,v_balance.balance-v_cost,v_op,v_tariff.id,p_units,v_tariff.duration_unit_days,v_tariff.points_per_unit)
    returning id into v_extension;
  insert into public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id)
    values(v_customer_id,'EXTEND_PROTECTION',p_idempotency_key,v_op);
  insert into public.points_ledger(customer_id,direction,amount,balance_before,balance_after,entry_type,source_type,source_id,description,created_by)
    values(v_customer_id,'DEBIT',v_cost,v_balance.balance,v_balance.balance-v_cost,'EXTENSION_DEBIT','protection_extension',v_extension,
      'تمديد الحماية '||p_units||' وحدة / '||v_days||' يومًا',v_customer_id);
  insert into public.audit_log(actor_type,actor_id,operation_id,entity_type,entity_id,action,before_state,after_state)
    values('CUSTOMER',v_customer_id,v_op,'protection_period',v_period.id,'EXTEND',jsonb_build_object('end_at',v_old_end),
      jsonb_build_object('end_at',v_new_end,'units',p_units,'duration_days',v_days,'points_cost',v_cost,'balance_after',v_balance.balance-v_cost));
  insert into public.notification_event(event_type,source_type,source_id) values('PROTECTION_EXTENDED','protection_period',v_period.id) returning id into v_event;
  insert into public.customer_notification(customer_id,event_id,notification_type,title,body,reference_type,reference_id)
    values(v_customer_id,v_event,'PROTECTION','تم تمديد الحماية','تم تمديد فترة الحماية للرقم المرتبط بحسابك.','protection_period',v_period.id);
  return jsonb_build_object('protection_period_id',v_period.id,'extension_id',v_extension,'end_at',v_new_end,
    'units',p_units,'duration_unit_days',v_tariff.duration_unit_days,'duration_days',v_days,
    'points_per_unit',v_tariff.points_per_unit,'points_cost',v_cost,'balance_after',v_balance.balance-v_cost,'operation_id',v_op);
end $$;

create or replace function public.renew_protection(
  p_protection_period_id uuid,p_tariff_id uuid,p_units integer,p_idempotency_key text
)
returns jsonb language plpgsql security definer set search_path=public as $$
declare
  v_customer_id uuid; v_old public.protection_period; v_number public.customer_number; v_company_id uuid;
  v_tariff public.protection_tariff; v_balance public.points_balance; v_days bigint; v_cost bigint;
  v_identity_id uuid; v_new_period uuid; v_start timestamptz:=now(); v_end timestamptz; v_op uuid; v_event uuid;
begin
  v_customer_id:=public.current_customer_profile_id();
  if v_customer_id is null then raise exception 'CUSTOMER_NOT_FOUND'; end if;
  if p_units is null or p_units<1 or p_units>120 then raise exception 'INVALID_TARIFF_UNITS'; end if;
  if nullif(trim(p_idempotency_key),'') is null then raise exception 'IDEMPOTENCY_KEY_REQUIRED'; end if;
  if not exists(select 1 from public.subscriber_identity where customer_profile_id=v_customer_id) then raise exception 'SUBSCRIPTION_REQUIRED'; end if;
  select o.id into v_op from public.operation_idempotency i join public.operation o on o.id=i.operation_id
    where i.actor_id=v_customer_id and i.operation_type='RENEW_PROTECTION' and i.idempotency_key=p_idempotency_key;
  if v_op is not null then return jsonb_build_object('operation_id',v_op,'idempotent',true); end if;
  select * into v_old from public.protection_period where id=p_protection_period_id and customer_id=v_customer_id for update;
  if v_old.id is null then raise exception 'PROTECTION_NOT_FOUND'; end if;
  if v_old.end_at>now() and v_old.status='ACTIVE' then raise exception 'PROTECTION_NOT_EXPIRED'; end if;
  v_identity_id:=v_old.protection_identity_id;
  select cn.* into v_number from public.customer_number cn join public.number_protection_identity ni on ni.customer_number_id=cn.id
    where ni.id=v_identity_id and cn.customer_id=v_customer_id and cn.status='ACTIVE' for update of cn;
  if v_number.id is null then raise exception 'CUSTOMER_NUMBER_NOT_FOUND_OR_INACTIVE'; end if;
  if exists(select 1 from public.protection_period pp where pp.protection_identity_id=v_identity_id and pp.id<>v_old.id and pp.status='ACTIVE' and pp.end_at>now()) then
    raise exception 'PROTECTION_ALREADY_ACTIVE';
  end if;
  select pn.telecom_company_id into v_company_id from public.phone_number pn where pn.id=v_number.phone_number_id;
  select * into v_tariff from public.protection_tariff where id=p_tariff_id and telecom_company_id=v_company_id
    and status='ACTIVE' and effective_from<=now() and (effective_to is null or effective_to>now()) for share;
  if v_tariff.id is null then raise exception 'TARIFF_NOT_AVAILABLE'; end if;
  if v_tariff.points_per_unit is null or v_tariff.points_per_unit<=0 then raise exception 'TARIFF_POINTS_NOT_CONFIGURED'; end if;
  v_days:=p_units::bigint*v_tariff.duration_unit_days::bigint; v_cost:=p_units::bigint*v_tariff.points_per_unit;
  if v_days<=0 or v_days>3650000 or v_cost<=0 then raise exception 'INVALID_TARIFF_TOTAL'; end if;
  insert into public.points_balance(customer_id,balance) values(v_customer_id,0) on conflict(customer_id) do nothing;
  select * into v_balance from public.points_balance where customer_id=v_customer_id for update;
  if v_balance.balance<v_cost then raise exception 'INSUFFICIENT_POINTS'; end if;
  update public.protection_period set status='EXPIRED',updated_at=now() where id=v_old.id and status='ACTIVE';
  v_end:=v_start+make_interval(days=>v_days::integer);
  update public.points_balance set balance=balance-v_cost,updated_at=now() where id=v_balance.id;
  insert into public.protection_period(protection_identity_id,customer_id,start_at,end_at,duration_days,tariff_id,
    tariff_mode_snapshot,tariff_value_snapshot,currency_snapshot,points_cost_snapshot,status,
    units_snapshot,duration_unit_days_snapshot,points_per_unit_snapshot)
    values(v_identity_id,v_customer_id,v_start,v_end,v_days::integer,v_tariff.id,v_tariff.tariff_mode,
      v_tariff.rate,v_tariff.currency,v_cost,'ACTIVE',p_units,v_tariff.duration_unit_days,v_tariff.points_per_unit)
    returning id into v_new_period;
  insert into public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,idempotency_key,result_reference,completed_at)
    values(public.generate_public_code('OP'),'RENEW_PROTECTION','CUSTOMER',v_customer_id,'protection_period',v_new_period,'COMPLETED',p_idempotency_key,v_new_period::text,now()) returning id into v_op;
  insert into public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id)
    values(v_customer_id,'RENEW_PROTECTION',p_idempotency_key,v_op);
  insert into public.points_ledger(customer_id,direction,amount,balance_before,balance_after,entry_type,source_type,source_id,description,created_by)
    values(v_customer_id,'DEBIT',v_cost,v_balance.balance,v_balance.balance-v_cost,'RENEWAL_DEBIT','protection_period',v_new_period,
      'تجديد الحماية '||p_units||' وحدة / '||v_days||' يومًا',v_customer_id);
  insert into public.audit_log(actor_type,actor_id,operation_id,entity_type,entity_id,action,before_state,after_state)
    values('CUSTOMER',v_customer_id,v_op,'protection_period',v_new_period,'RENEW',jsonb_build_object('previous_period_id',v_old.id,'previous_end_at',v_old.end_at),
      jsonb_build_object('units',p_units,'duration_days',v_days,'points_cost',v_cost,'balance_after',v_balance.balance-v_cost));
  insert into public.notification_event(event_type,source_type,source_id) values('PROTECTION_RENEWED','protection_period',v_new_period) returning id into v_event;
  insert into public.customer_notification(customer_id,event_id,notification_type,title,body,reference_type,reference_id)
    values(v_customer_id,v_event,'PROTECTION','تم تجديد الحماية','تم إنشاء فترة حماية جديدة للرقم المرتبط بحسابك.','protection_period',v_new_period);
  return jsonb_build_object('protection_period_id',v_new_period,'previous_period_id',v_old.id,'status','ACTIVE',
    'start_at',v_start,'end_at',v_end,'units',p_units,'duration_unit_days',v_tariff.duration_unit_days,
    'duration_days',v_days,'points_per_unit',v_tariff.points_per_unit,'points_cost',v_cost,
    'balance_after',v_balance.balance-v_cost,'operation_id',v_op);
end $$;

-- Task-plan rebuild semantics remain deliberately untouched: V11 has no
-- customer-safe task-plan RPC and the master reference marks DB-GAP-016 open.

comment on column public.protection_tariff.duration_unit_days is
  'Admin-configured days in one purchasable unit; customer selects whole units, never raw days.';
comment on column public.protection_tariff.points_per_unit is
  'Admin-configured integer points charged for one duration unit. NULL means tariff is not purchasable.';
comment on column public.protection_period.units_snapshot is 'Whole tariff units purchased for the original protection period.';
comment on column public.protection_period.points_per_unit_snapshot is 'Immutable points price per unit at original activation/renewal.';

-- A05 tariff administration contract. This is intentionally part of the same
-- migration as the customer RPCs so the admin form cannot create a tariff
-- that lacks the unit days / point price consumed by the customer app.
create or replace function public.admin_save_provider_tariff(
  p_id uuid,
  p_provider_id uuid,
  p_tariff_mode public.tariff_mode,
  p_duration_unit_days integer,
  p_points_per_unit bigint,
  p_rate numeric,
  p_currency text,
  p_effective_from timestamptz,
  p_effective_to timestamptz,
  p_status text
)
returns jsonb
language plpgsql
security definer
set search_path=public
as $$
declare
  v_admin_id uuid;
  v_tariff public.protection_tariff;
  v_before jsonb;
  v_operation_id uuid;
  v_status public.generic_status;
begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED'; end if;
  if not public.has_admin_permission('providers.write') then raise exception 'FORBIDDEN'; end if;
  if p_duration_unit_days is null or p_duration_unit_days not between 1 and 36500 then raise exception 'INVALID_DURATION_UNIT_DAYS'; end if;
  if p_points_per_unit is null or p_points_per_unit<=0 or p_points_per_unit>76861433640456465 then raise exception 'INVALID_POINTS_PER_UNIT'; end if;
  if p_rate is null or p_rate<0 then raise exception 'INVALID_TARIFF_RATE'; end if;
  if nullif(trim(p_currency),'') is null then raise exception 'CURRENCY_REQUIRED'; end if;
  if p_effective_from is null or (p_effective_to is not null and p_effective_to<=p_effective_from) then raise exception 'INVALID_EFFECTIVE_RANGE'; end if;
  v_status:=case lower(coalesce(p_status,'')) when 'active' then 'ACTIVE'::public.generic_status when 'inactive' then 'INACTIVE'::public.generic_status else null end;
  if v_status is null then raise exception 'INVALID_TARIFF_STATUS'; end if;
  if not exists(select 1 from public.telecom_company where id=p_provider_id) then raise exception 'PROVIDER_NOT_FOUND'; end if;
  v_admin_id:=public.current_admin_id();
  if v_admin_id is null then raise exception 'ADMIN_NOT_FOUND'; end if;

  if p_id is null then
    insert into public.protection_tariff(
      telecom_company_id,tariff_mode,rate,currency,effective_from,effective_to,status,
      duration_unit_days,points_per_unit
    ) values (
      p_provider_id,p_tariff_mode,p_rate,trim(p_currency),p_effective_from,p_effective_to,v_status,
      p_duration_unit_days,p_points_per_unit
    ) returning * into v_tariff;
  else
    select * into v_tariff from public.protection_tariff
      where id=p_id and telecom_company_id=p_provider_id for update;
    if v_tariff.id is null then raise exception 'TARIFF_NOT_FOUND'; end if;
    v_before:=to_jsonb(v_tariff);
    update public.protection_tariff set
      tariff_mode=p_tariff_mode,rate=p_rate,currency=trim(p_currency),
      effective_from=p_effective_from,effective_to=p_effective_to,status=v_status,
      duration_unit_days=p_duration_unit_days,points_per_unit=p_points_per_unit,updated_at=now()
      where id=v_tariff.id returning * into v_tariff;
  end if;

  insert into public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,result_reference,completed_at)
    values(public.generate_public_code('OP'),'SAVE_PROTECTION_TARIFF','ADMIN',v_admin_id,'protection_tariff',v_tariff.id,'COMPLETED',v_tariff.id::text,now())
    returning id into v_operation_id;
  insert into public.audit_log(actor_type,actor_id,operation_id,entity_type,entity_id,action,permission_code,before_state,after_state)
    values('ADMIN',v_admin_id,v_operation_id,'protection_tariff',v_tariff.id,
      case when p_id is null then 'CREATE_TARIFF' else 'UPDATE_TARIFF' end,
      'providers.write',v_before,to_jsonb(v_tariff));
  return jsonb_build_object('tariff_id',v_tariff.id,'tariff_mode',v_tariff.tariff_mode,
    'duration_unit_days',v_tariff.duration_unit_days,'points_per_unit',v_tariff.points_per_unit,
    'rate',v_tariff.rate,'currency',v_tariff.currency,'status',v_tariff.status,'operation_id',v_operation_id);
end;
$$;

-- The admin app calls this RPC using the argument key p_permission_code.
create or replace function public.admin_has_permission(p_permission_code text)
returns boolean
language sql
stable
security definer
set search_path=public
as $$
  select public.has_admin_permission(p_permission_code);
$$;

create policy telecom_company_admin_read on public.telecom_company
  for select using (public.has_admin_permission('providers.read'));
create policy telecom_prefix_admin_read on public.telecom_prefix
  for select using (public.has_admin_permission('providers.read'));
create policy protection_tariff_admin_read on public.protection_tariff
  for select using (public.has_admin_permission('providers.read'));

revoke all on function public.current_customer_profile_id() from public,anon,authenticated;
revoke all on function public.get_customer_screen_data(text,text) from public,anon;
revoke all on function public.get_public_content(text) from public;
revoke all on function public.update_customer_profile(text,text) from public,anon;
revoke all on function public.update_customer_number(uuid,text,text) from public,anon;
revoke all on function public.delete_customer_number(uuid,text) from public,anon;
revoke all on function public.create_support_conversation(text,text,text) from public,anon;
revoke all on function public.send_support_message(uuid,text,text) from public,anon;
revoke all on function public.close_support_conversation(uuid) from public,anon;
revoke all on function public.mark_admin_message_read(uuid) from public,anon;
revoke all on function public.activate_protection(uuid,uuid,integer,text) from public,anon;
revoke all on function public.extend_protection(uuid,uuid,integer,text) from public,anon;
revoke all on function public.renew_protection(uuid,uuid,integer,text) from public,anon;
revoke all on function public.admin_save_provider_tariff(uuid,uuid,public.tariff_mode,integer,bigint,numeric,text,timestamptz,timestamptz,text) from public,anon;
revoke all on function public.admin_has_permission(text) from public,anon;

grant execute on function public.get_customer_screen_data(text,text) to authenticated;
grant execute on function public.get_public_content(text) to anon,authenticated;
grant execute on function public.update_customer_profile(text,text) to authenticated;
grant execute on function public.update_customer_number(uuid,text,text) to authenticated;
grant execute on function public.delete_customer_number(uuid,text) to authenticated;
grant execute on function public.create_support_conversation(text,text,text) to authenticated;
grant execute on function public.send_support_message(uuid,text,text) to authenticated;
grant execute on function public.close_support_conversation(uuid) to authenticated;
grant execute on function public.mark_admin_message_read(uuid) to authenticated;
grant execute on function public.activate_protection(uuid,uuid,integer,text) to authenticated;
grant execute on function public.extend_protection(uuid,uuid,integer,text) to authenticated;
grant execute on function public.renew_protection(uuid,uuid,integer,text) to authenticated;
grant execute on function public.admin_save_provider_tariff(uuid,uuid,public.tariff_mode,integer,bigint,numeric,text,timestamptz,timestamptz,text) to authenticated;
grant execute on function public.admin_has_permission(text) to authenticated;

commit;
