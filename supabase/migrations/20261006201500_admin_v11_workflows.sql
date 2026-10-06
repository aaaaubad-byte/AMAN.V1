-- AMAN Admin A01–A20 implementation support for the V11 database.
-- Apply after AMAN_V11_DATABASE_FINAL.sql and the customer C01–C20 migration.
-- All writes are SECURITY DEFINER RPCs; Android receives no direct write policy.
begin;

-- Export is a separate optional capability; existing V11 roles are not widened.
insert into public.permissions(code,name,domain,action)
values('reports.export','تصدير التقارير','reports','export')
on conflict(code) do nothing;
insert into public.role_permissions(role_id,permission_id)
select r.id,p.id from public.roles r cross join public.permissions p
where r.code='SUPER_ADMIN' and p.code='reports.export'
on conflict do nothing;

-- -----------------------------------------------------------------------------
-- Permission-gated administration reads. Existing customer self-read policies
-- remain intact; these policies only add authorized admin access.
-- -----------------------------------------------------------------------------
drop policy if exists admin_customer_read on public.customer_profile;
create policy admin_customer_read on public.customer_profile
for select using (public.has_admin_permission('customers.read') or public.has_admin_permission('users.read'));

drop policy if exists admin_number_read on public.customer_number;
create policy admin_number_read on public.customer_number
for select using (public.has_admin_permission('numbers.read'));
drop policy if exists admin_phone_read on public.phone_number;
create policy admin_phone_read on public.phone_number
for select using (public.has_admin_permission('numbers.read'));
drop policy if exists admin_points_balance_read on public.points_balance;
create policy admin_points_balance_read on public.points_balance
for select using (public.has_admin_permission('customers.read') or public.has_admin_permission('users.read') or public.has_admin_permission('finance.read'));
drop policy if exists admin_points_ledger_read on public.points_ledger;
create policy admin_points_ledger_read on public.points_ledger
for select using (public.has_admin_permission('customers.read') or public.has_admin_permission('users.read') or public.has_admin_permission('finance.read'));
drop policy if exists admin_protection_read on public.protection_period;
create policy admin_protection_read on public.protection_period
for select using (public.has_admin_permission('numbers.read'));

create policy admin_subscriber_identity_read on public.subscriber_identity
for select using (public.has_admin_permission('customers.read'));
create policy admin_protection_identity_read on public.number_protection_identity
for select using (public.has_admin_permission('numbers.read'));
create policy admin_protection_extension_read on public.protection_extension
for select using (public.has_admin_permission('numbers.read'));
drop policy if exists admin_purchase_read on public.points_purchase;
create policy admin_purchase_read on public.points_purchase
for select using (public.has_admin_permission('points_purchases.read'));
drop policy if exists admin_task_plan_read on public.task_plan;
create policy admin_task_plan_read on public.task_plan
for select using (public.has_admin_permission('tasks.read'));
drop policy if exists admin_task_history_read on public.task_schedule_history;
create policy admin_task_history_read on public.task_schedule_history
for select using (public.has_admin_permission('tasks.read'));
drop policy if exists admin_task_execution_read on public.task_execution;
create policy admin_task_execution_read on public.task_execution
for select using (public.has_admin_permission('tasks.read'));
create policy admin_task_configuration_read on public.task_configuration
for select using (public.has_admin_permission('task_settings.read') or public.has_admin_permission('providers.read'));
create policy admin_package_read on public.points_package
for select using (public.has_admin_permission('packages.read'));
create policy admin_payment_method_read on public.payment_method
for select using (public.has_admin_permission('payment_methods.read'));
create policy admin_financial_account_read on public.financial_account
for select using (public.has_admin_permission('finance.read'));
create policy admin_expense_type_read on public.expense_type
for select using (public.has_admin_permission('finance.read') or public.has_admin_permission('finance.write'));
drop policy if exists admin_expense_read on public.expense;
create policy admin_expense_read on public.expense
for select using (public.has_admin_permission('finance.read'));
create policy admin_financial_correction_read on public.financial_correction
for select using (public.has_admin_permission('finance.read'));
create policy admin_support_conversation_read on public.support_conversation
for select using (public.has_admin_permission('support.write') or public.has_admin_permission('customers.read'));
create policy admin_support_message_read on public.support_message
for select using (public.has_admin_permission('support.write') or public.has_admin_permission('customers.read'));
create policy admin_message_thread_read on public.admin_message_thread
for select using (public.has_admin_permission('support.write'));
create policy admin_message_read on public.admin_message
for select using (public.has_admin_permission('support.write'));
create policy admin_customer_notification_read on public.customer_notification
for select using (public.has_admin_permission('notifications.read'));
create policy admin_notification_campaign_read on public.admin_notification_campaign
for select using (public.has_admin_permission('notifications.read'));
create policy admin_notification_recipient_read on public.admin_notification_recipient
for select using (public.has_admin_permission('notifications.read'));
create policy admin_notification_event_read on public.notification_event
for select using (public.has_admin_permission('notifications.read'));
create policy admin_operation_read on public.operation
for select using (public.has_admin_permission('audit.read'));

-- A13 report readers can read the narrowly enumerated report source tables.
create policy admin_reports_profile_read on public.customer_profile for select using (public.has_admin_permission('reports.read'));
create policy admin_reports_customer_number_read on public.customer_number for select using (public.has_admin_permission('reports.read'));
create policy admin_reports_phone_read on public.phone_number for select using (public.has_admin_permission('reports.read'));
create policy admin_reports_points_read on public.points_ledger for select using (public.has_admin_permission('reports.read'));
create policy admin_reports_protection_read on public.protection_period for select using (public.has_admin_permission('reports.read'));
create policy admin_reports_protection_identity_read on public.number_protection_identity for select using (public.has_admin_permission('reports.read'));
create policy admin_reports_protection_extension_read on public.protection_extension for select using (public.has_admin_permission('reports.read'));
create policy admin_reports_purchase_read on public.points_purchase for select using (public.has_admin_permission('reports.read'));
create policy admin_reports_task_read on public.periodic_task for select using (public.has_admin_permission('reports.read'));
create policy admin_reports_task_plan_read on public.task_plan for select using (public.has_admin_permission('reports.read'));
create policy admin_reports_task_execution_read on public.task_execution for select using (public.has_admin_permission('reports.read'));
create policy admin_reports_company_read on public.telecom_company for select using (public.has_admin_permission('reports.read'));
create policy admin_reports_tariff_read on public.protection_tariff for select using (public.has_admin_permission('reports.read'));
create policy admin_reports_finance_read on public.financial_ledger for select using (public.has_admin_permission('reports.read'));
create policy admin_reports_operation_read on public.operation for select using (public.has_admin_permission('reports.read'));

-- SELECT grants are not sufficient by themselves; every table remains RLS gated.
grant select on public.subscriber_identity, public.number_protection_identity, public.protection_extension,
  public.task_plan, public.task_schedule_history, public.task_execution, public.financial_account,
  public.expense_type, public.financial_correction, public.admin_message_thread, public.admin_message,
  public.notification_event, public.admin_notification_campaign, public.admin_notification_recipient,
  public.operation, public.audit_log, public.financial_ledger, public.expense to authenticated;

-- -----------------------------------------------------------------------------
-- Common transactional operation + audit writer (not callable by app clients).
-- Duplicate idempotency keys fail the transaction, so a retried write cannot
-- silently apply a second time.
-- -----------------------------------------------------------------------------
create or replace function public.admin_record_change(
  p_operation_type text,
  p_entity_type text,
  p_entity_id uuid,
  p_permission text,
  p_before jsonb,
  p_after jsonb,
  p_reason text,
  p_idempotency_key text
) returns uuid
language plpgsql security definer set search_path=public
as $$
declare v_admin uuid; v_operation uuid; v_inserted integer;
begin
  v_admin := public.current_admin_id();
  if v_admin is null then raise exception 'ADMIN_REQUIRED'; end if;
  if not public.has_admin_permission(p_permission) then raise exception 'FORBIDDEN'; end if;
  if nullif(trim(p_idempotency_key),'') is null then raise exception 'IDEMPOTENCY_KEY_REQUIRED'; end if;
  insert into public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,idempotency_key,completed_at)
  values(public.generate_public_code('OP'),p_operation_type,'ADMIN',v_admin,p_entity_type,p_entity_id,'COMPLETED',trim(p_idempotency_key),now())
  returning id into v_operation;
  insert into public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id)
  values(v_admin,p_operation_type,trim(p_idempotency_key),v_operation)
  on conflict(actor_id,operation_type,idempotency_key) do nothing;
  get diagnostics v_inserted = row_count;
  if v_inserted = 0 then raise exception 'IDEMPOTENCY_KEY_ALREADY_USED'; end if;
  insert into public.audit_log(actor_type,actor_id,operation_id,entity_type,entity_id,action,permission_code,before_state,after_state,reason)
  values('ADMIN',v_admin,v_operation,p_entity_type,p_entity_id,p_operation_type,p_permission,p_before,p_after,nullif(trim(p_reason),''));
  return v_operation;
end $$;

-- -----------------------------------------------------------------------------
-- A02 customer profile: editable identity/status only; no hard delete and no
-- arbitrary USER -> SUBSCRIBER transition.
-- -----------------------------------------------------------------------------
create or replace function public.admin_update_customer_profile(
  p_customer_id uuid, p_name text, p_email text, p_account_status text,
  p_reason text, p_idempotency_key text
) returns jsonb language plpgsql security definer set search_path=public
as $$
declare v_row public.customer_profile; v_before jsonb; v_permission text;
begin
  select * into v_row from public.customer_profile where id=p_customer_id for update;
  if v_row.id is null then raise exception 'CUSTOMER_NOT_FOUND'; end if;
  v_permission := case when v_row.account_type='SUBSCRIBER' then 'customers.update' else 'users.update' end;
  if not public.has_admin_permission(v_permission) then raise exception 'FORBIDDEN'; end if;
  if nullif(trim(p_name),'') is null then raise exception 'NAME_REQUIRED'; end if;
  if upper(coalesce(p_account_status,'')) not in ('ACTIVE','SUSPENDED','DISABLED') then raise exception 'INVALID_ACCOUNT_STATUS'; end if;
  v_before := to_jsonb(v_row);
  update public.customer_profile set name=trim(p_name), email=nullif(trim(p_email),''),
    account_status=upper(p_account_status)::public.account_status, updated_at=now()
  where id=p_customer_id returning * into v_row;
  perform public.admin_record_change('UPDATE_CUSTOMER_PROFILE','customer_profile',v_row.id,v_permission,v_before,to_jsonb(v_row),p_reason,p_idempotency_key);
  return to_jsonb(v_row);
end $$;

-- A03: state/archive only; never edit/delete the phone entity from admin.
create or replace function public.admin_set_customer_number_status(
  p_customer_number_id uuid, p_status text, p_reason text, p_idempotency_key text
) returns jsonb language plpgsql security definer set search_path=public
as $$
declare v_row public.customer_number; v_before jsonb;
begin
  if not public.has_admin_permission('numbers.update') then raise exception 'FORBIDDEN'; end if;
  if upper(coalesce(p_status,'')) not in ('ACTIVE','INACTIVE','ARCHIVED') then raise exception 'INVALID_NUMBER_STATUS'; end if;
  select * into v_row from public.customer_number where id=p_customer_number_id for update;
  if v_row.id is null then raise exception 'CUSTOMER_NUMBER_NOT_FOUND'; end if;
  v_before:=to_jsonb(v_row);
  update public.customer_number set status=upper(p_status)::public.customer_number_status, updated_at=now()
    where id=v_row.id returning * into v_row;
  perform public.admin_record_change('SET_CUSTOMER_NUMBER_STATUS','customer_number',v_row.id,'numbers.update',v_before,to_jsonb(v_row),p_reason,p_idempotency_key);
  return to_jsonb(v_row);
end $$;

-- A05 telecom company.
create or replace function public.admin_save_telecom_company(
  p_id uuid, p_code text, p_name text, p_status text, p_idempotency_key text
) returns jsonb language plpgsql security definer set search_path=public
as $$
declare v_row public.telecom_company; v_before jsonb;
begin
  if not public.has_admin_permission('providers.write') then raise exception 'FORBIDDEN'; end if;
  if nullif(trim(p_code),'') is null or nullif(trim(p_name),'') is null then raise exception 'COMPANY_FIELDS_REQUIRED'; end if;
  if upper(coalesce(p_status,'')) not in ('ACTIVE','INACTIVE') then raise exception 'INVALID_STATUS'; end if;
  if p_id is null then
    insert into public.telecom_company(code,name,status) values(trim(p_code),trim(p_name),upper(p_status)::public.generic_status) returning * into v_row;
  else
    select * into v_row from public.telecom_company where id=p_id for update;
    if v_row.id is null then raise exception 'COMPANY_NOT_FOUND'; end if;
    v_before:=to_jsonb(v_row);
    update public.telecom_company set code=trim(p_code),name=trim(p_name),status=upper(p_status)::public.generic_status,updated_at=now()
      where id=p_id returning * into v_row;
  end if;
  perform public.admin_record_change(case when p_id is null then 'CREATE_TELECOM_COMPANY' else 'UPDATE_TELECOM_COMPANY' end,
    'telecom_company',v_row.id,'providers.write',v_before,to_jsonb(v_row),null,p_idempotency_key);
  return to_jsonb(v_row);
end $$;

create or replace function public.admin_save_telecom_prefix(
  p_id uuid, p_telecom_company_id uuid, p_prefix text, p_status text, p_idempotency_key text
) returns jsonb language plpgsql security definer set search_path=public
as $$
declare v_row public.telecom_prefix; v_before jsonb;
begin
  if not public.has_admin_permission('providers.write') then raise exception 'FORBIDDEN'; end if;
  if p_telecom_company_id is null or nullif(trim(p_prefix),'') is null then raise exception 'PREFIX_FIELDS_REQUIRED'; end if;
  if upper(coalesce(p_status,'')) not in ('ACTIVE','INACTIVE') then raise exception 'INVALID_STATUS'; end if;
  if not exists(select 1 from public.telecom_company where id=p_telecom_company_id) then raise exception 'COMPANY_NOT_FOUND'; end if;
  if p_id is null then
    insert into public.telecom_prefix(telecom_company_id,prefix,status)
      values(p_telecom_company_id,trim(p_prefix),upper(p_status)::public.generic_status) returning * into v_row;
  else
    select * into v_row from public.telecom_prefix where id=p_id and telecom_company_id=p_telecom_company_id for update;
    if v_row.id is null then raise exception 'PREFIX_NOT_FOUND'; end if;
    v_before:=to_jsonb(v_row);
    update public.telecom_prefix set prefix=trim(p_prefix),status=upper(p_status)::public.generic_status,updated_at=now()
      where id=p_id returning * into v_row;
  end if;
  perform public.admin_record_change(case when p_id is null then 'CREATE_TELECOM_PREFIX' else 'UPDATE_TELECOM_PREFIX' end,
    'telecom_prefix',v_row.id,'providers.write',v_before,to_jsonb(v_row),null,p_idempotency_key);
  return to_jsonb(v_row);
end $$;

-- A06 points packages: visibility and activation are distinct database fields.
create or replace function public.admin_save_points_package(
  p_id uuid,p_code text,p_name text,p_points bigint,p_price numeric,p_currency text,
  p_display_order integer,p_is_visible boolean,p_is_active boolean,p_idempotency_key text
) returns jsonb language plpgsql security definer set search_path=public
as $$
declare v_row public.points_package; v_before jsonb;
begin
  if not public.has_admin_permission('packages.write') then raise exception 'FORBIDDEN'; end if;
  if nullif(trim(p_code),'') is null or nullif(trim(p_name),'') is null or p_points is null or p_points<=0 or p_price is null or p_price<=0 or nullif(trim(p_currency),'') is null then raise exception 'INVALID_PACKAGE'; end if;
  if p_id is null then
    insert into public.points_package(code,name,points,price,currency,display_order,is_visible,is_active)
    values(trim(p_code),trim(p_name),p_points,p_price,trim(p_currency),coalesce(p_display_order,0),coalesce(p_is_visible,true),coalesce(p_is_active,true)) returning * into v_row;
  else
    select * into v_row from public.points_package where id=p_id for update;
    if v_row.id is null then raise exception 'PACKAGE_NOT_FOUND'; end if;
    v_before:=to_jsonb(v_row);
    update public.points_package set code=trim(p_code),name=trim(p_name),points=p_points,price=p_price,currency=trim(p_currency),
      display_order=coalesce(p_display_order,0),is_visible=coalesce(p_is_visible,false),is_active=coalesce(p_is_active,false),updated_at=now()
      where id=p_id returning * into v_row;
  end if;
  perform public.admin_record_change(case when p_id is null then 'CREATE_POINTS_PACKAGE' else 'UPDATE_POINTS_PACKAGE' end,
    'points_package',v_row.id,'packages.write',v_before,to_jsonb(v_row),null,p_idempotency_key);
  return to_jsonb(v_row);
end $$;

-- A07 payment methods.
create or replace function public.admin_save_payment_method(
  p_id uuid,p_code text,p_name text,p_type text,p_receiving_account text,p_transfer_instructions text,
  p_display_order integer,p_is_visible boolean,p_is_active boolean,p_idempotency_key text
) returns jsonb language plpgsql security definer set search_path=public
as $$
declare v_row public.payment_method; v_before jsonb;
begin
  if not public.has_admin_permission('payment_methods.write') then raise exception 'FORBIDDEN'; end if;
  if nullif(trim(p_code),'') is null or nullif(trim(p_name),'') is null or nullif(trim(p_type),'') is null or nullif(trim(p_receiving_account),'') is null then raise exception 'INVALID_PAYMENT_METHOD'; end if;
  if p_id is null then
    insert into public.payment_method(code,name,type,receiving_account,transfer_instructions,display_order,is_visible,is_active)
    values(trim(p_code),trim(p_name),trim(p_type),trim(p_receiving_account),nullif(trim(p_transfer_instructions),''),coalesce(p_display_order,0),coalesce(p_is_visible,true),coalesce(p_is_active,true)) returning * into v_row;
  else
    select * into v_row from public.payment_method where id=p_id for update;
    if v_row.id is null then raise exception 'PAYMENT_METHOD_NOT_FOUND'; end if;
    v_before:=to_jsonb(v_row);
    update public.payment_method set code=trim(p_code),name=trim(p_name),type=trim(p_type),receiving_account=trim(p_receiving_account),
      transfer_instructions=nullif(trim(p_transfer_instructions),''),display_order=coalesce(p_display_order,0),
      is_visible=coalesce(p_is_visible,false),is_active=coalesce(p_is_active,false),updated_at=now()
      where id=p_id returning * into v_row;
  end if;
  perform public.admin_record_change(case when p_id is null then 'CREATE_PAYMENT_METHOD' else 'UPDATE_PAYMENT_METHOD' end,
    'payment_method',v_row.id,'payment_methods.write',v_before,to_jsonb(v_row),null,p_idempotency_key);
  return to_jsonb(v_row);
end $$;

-- A05/A11 task configuration is versioned: existing plans/tasks keep snapshots.
create or replace function public.admin_save_task_configuration(
  p_telecom_company_id uuid,p_interval_days integer,p_task_amount numeric,p_currency text,
  p_visibility_days_before integer,p_allow_reschedule boolean,p_allow_post_expiry_creation boolean,
  p_post_expiry_grace_days integer,p_create_first_task_on_activation boolean,p_create_first_task_on_renewal boolean,
  p_effective_from timestamptz,p_idempotency_key text
) returns jsonb language plpgsql security definer set search_path=public
as $$
declare v_row public.task_configuration; v_old public.task_configuration; v_operation uuid;
begin
  if not public.has_admin_permission('task_settings.write') then raise exception 'FORBIDDEN'; end if;
  if p_telecom_company_id is null or p_interval_days is null or p_interval_days<=0 or p_task_amount is null or p_task_amount<0 or nullif(trim(p_currency),'') is null or p_visibility_days_before is null or p_visibility_days_before<0 or p_post_expiry_grace_days is null or p_post_expiry_grace_days<0 or p_effective_from is null then raise exception 'INVALID_TASK_CONFIGURATION'; end if;
  if not exists(select 1 from public.telecom_company where id=p_telecom_company_id) then raise exception 'COMPANY_NOT_FOUND'; end if;
  select * into v_old from public.task_configuration where telecom_company_id=p_telecom_company_id and status='ACTIVE' and effective_to is null order by effective_from desc limit 1 for update;
  if v_old.id is not null then
    if p_effective_from<=v_old.effective_from then raise exception 'EFFECTIVE_FROM_MUST_ADVANCE'; end if;
    update public.task_configuration set effective_to=p_effective_from,updated_at=now() where id=v_old.id;
  end if;
  insert into public.task_configuration(telecom_company_id,interval_days,task_amount,currency,visibility_days_before,
    allow_reschedule,allow_post_expiry_creation,post_expiry_grace_days,create_first_task_on_activation,create_first_task_on_renewal,
    status,effective_from)
  values(p_telecom_company_id,p_interval_days,p_task_amount,trim(p_currency),p_visibility_days_before,
    coalesce(p_allow_reschedule,false),coalesce(p_allow_post_expiry_creation,false),p_post_expiry_grace_days,
    coalesce(p_create_first_task_on_activation,false),coalesce(p_create_first_task_on_renewal,false),'ACTIVE',p_effective_from)
  returning * into v_row;
  v_operation:=public.admin_record_change('CREATE_TASK_CONFIGURATION','task_configuration',v_row.id,'task_settings.write',
    case when v_old.id is null then null else to_jsonb(v_old) end,to_jsonb(v_row),null,p_idempotency_key);
  return jsonb_build_object('configuration',to_jsonb(v_row),'operation_id',v_operation,'existing_task_plans_rebuilt',false);
end $$;

-- A04/A09 operations. External payment remains outside AMAN; execute is called
-- only after the operator returns with the external reference.
create or replace function public.admin_execute_periodic_task(
  p_task_id uuid,p_external_reference text,p_idempotency_key text
) returns jsonb language plpgsql security definer set search_path=public
as $$
declare v_task public.periodic_task; v_before jsonb; v_exec public.task_execution;
begin
  if not public.has_admin_permission('tasks.execute') then raise exception 'FORBIDDEN'; end if;
  if nullif(trim(p_external_reference),'') is null then raise exception 'EXTERNAL_REFERENCE_REQUIRED'; end if;
  select * into v_task from public.periodic_task where id=p_task_id for update;
  if v_task.id is null then raise exception 'TASK_NOT_FOUND'; end if;
  if v_task.status<>'OPEN' then raise exception 'TASK_NOT_OPEN'; end if;
  v_before:=to_jsonb(v_task);
  update public.periodic_task set status='COMPLETED',executed_at=now(),updated_at=now() where id=p_task_id returning * into v_task;
  insert into public.task_execution(task_id,execution_result,external_reference,executed_at,executed_by,amount,currency)
  values(v_task.id,'SUCCESS',trim(p_external_reference),now(),public.current_admin_id(),v_task.amount,v_task.currency) returning * into v_exec;
  perform public.admin_record_change('EXECUTE_PERIODIC_TASK','periodic_task',v_task.id,'tasks.execute',v_before,to_jsonb(v_task),trim(p_external_reference),p_idempotency_key);
  return jsonb_build_object('task',to_jsonb(v_task),'execution',to_jsonb(v_exec));
end $$;

create or replace function public.admin_reschedule_periodic_task(
  p_task_id uuid,p_new_due_at timestamptz,p_reason text,p_idempotency_key text
) returns jsonb language plpgsql security definer set search_path=public
as $$
declare v_task public.periodic_task; v_before jsonb; v_old_due timestamptz;
begin
  if not public.has_admin_permission('tasks.reschedule') then raise exception 'FORBIDDEN'; end if;
  if p_new_due_at is null or p_new_due_at<=now() then raise exception 'INVALID_DUE_AT'; end if;
  select * into v_task from public.periodic_task where id=p_task_id for update;
  if v_task.id is null then raise exception 'TASK_NOT_FOUND'; end if;
  if v_task.status<>'OPEN' then raise exception 'TASK_NOT_OPEN'; end if;
  if not exists(select 1 from public.task_configuration tc where tc.telecom_company_id=v_task.telecom_company_id and tc.status='ACTIVE' and tc.effective_from<=now() and (tc.effective_to is null or tc.effective_to>now()) and tc.allow_reschedule) then raise exception 'RESCHEDULE_NOT_ALLOWED'; end if;
  v_before:=to_jsonb(v_task); v_old_due:=v_task.due_at;
  update public.periodic_task set due_at=p_new_due_at,updated_at=now() where id=p_task_id returning * into v_task;
  insert into public.task_schedule_history(task_id,old_due_at,new_due_at,reason,changed_by)
  values(p_task_id,v_old_due,p_new_due_at,nullif(trim(p_reason),''),public.current_admin_id());
  perform public.admin_record_change('RESCHEDULE_PERIODIC_TASK','periodic_task',v_task.id,'tasks.reschedule',v_before,to_jsonb(v_task),p_reason,p_idempotency_key);
  return to_jsonb(v_task);
end $$;

create or replace function public.admin_cancel_periodic_task(
  p_task_id uuid,p_reason text,p_idempotency_key text
) returns jsonb language plpgsql security definer set search_path=public
as $$
declare v_task public.periodic_task; v_before jsonb;
begin
  if not public.has_admin_permission('tasks.cancel') then raise exception 'FORBIDDEN'; end if;
  if nullif(trim(p_reason),'') is null then raise exception 'CANCELLATION_REASON_REQUIRED'; end if;
  select * into v_task from public.periodic_task where id=p_task_id for update;
  if v_task.id is null then raise exception 'TASK_NOT_FOUND'; end if;
  if v_task.status<>'OPEN' then raise exception 'TASK_NOT_OPEN'; end if;
  v_before:=to_jsonb(v_task);
  update public.periodic_task set status='CANCELLED',cancelled_at=now(),cancellation_reason=trim(p_reason),updated_at=now()
    where id=p_task_id returning * into v_task;
  perform public.admin_record_change('CANCEL_PERIODIC_TASK','periodic_task',v_task.id,'tasks.cancel',v_before,to_jsonb(v_task),p_reason,p_idempotency_key);
  return to_jsonb(v_task);
end $$;

-- A10 expenses are append-only and balance checked; posted ledger entries are
-- corrected by separate correction records, never silently edited.
create or replace function public.admin_create_expense(
  p_expense_type_id uuid,p_amount numeric,p_currency text,p_description text,p_reference text,p_idempotency_key text
) returns jsonb language plpgsql security definer set search_path=public
as $$
declare v_expense public.expense; v_account public.financial_account; v_ledger public.financial_ledger;
begin
  if not public.has_admin_permission('finance.write') then raise exception 'FORBIDDEN'; end if;
  if p_amount is null or p_amount<=0 or nullif(trim(p_currency),'') is null or nullif(trim(p_description),'') is null then raise exception 'INVALID_EXPENSE'; end if;
  select * into v_account from public.financial_account where currency=trim(p_currency) and status='ACTIVE' for update;
  if v_account.id is null or v_account.balance<p_amount then raise exception 'INSUFFICIENT_FINANCIAL_BALANCE'; end if;
  insert into public.expense(expense_type_id,amount,currency,description,reference,posted_by)
  values(p_expense_type_id,p_amount,trim(p_currency),trim(p_description),nullif(trim(p_reference),''),public.current_admin_id()) returning * into v_expense;
  insert into public.financial_ledger(entry_type,direction,amount,currency,source_type,source_id,description,created_by)
  values('OPERATING_EXPENSE','OUTFLOW',p_amount,trim(p_currency),'expense',v_expense.id,trim(p_description),public.current_admin_id()) returning * into v_ledger;
  update public.financial_account set balance=balance-p_amount,updated_at=now() where id=v_account.id;
  perform public.admin_record_change('CREATE_EXPENSE','expense',v_expense.id,'finance.write',null,to_jsonb(v_expense),trim(p_description),p_idempotency_key);
  return jsonb_build_object('expense',to_jsonb(v_expense),'ledger',to_jsonb(v_ledger),'balance_after',v_account.balance-p_amount);
end $$;

-- A11 support replies and closure.
create or replace function public.admin_send_support_reply(
  p_conversation_id uuid,p_body text,p_idempotency_key text
) returns jsonb language plpgsql security definer set search_path=public
as $$
declare v_conversation public.support_conversation; v_message public.support_message;
begin
  if not public.has_admin_permission('support.write') then raise exception 'FORBIDDEN'; end if;
  if nullif(trim(p_body),'') is null then raise exception 'MESSAGE_REQUIRED'; end if;
  select * into v_conversation from public.support_conversation where id=p_conversation_id for update;
  if v_conversation.id is null then raise exception 'CONVERSATION_NOT_FOUND'; end if;
  if v_conversation.status<>'OPEN' then raise exception 'CONVERSATION_CLOSED'; end if;
  insert into public.support_message(conversation_id,sender_type,sender_id,body)
  values(p_conversation_id,'ADMIN',public.current_admin_id(),trim(p_body)) returning * into v_message;
  update public.support_conversation set updated_at=now() where id=p_conversation_id;
  perform public.admin_record_change('SEND_SUPPORT_REPLY','support_conversation',p_conversation_id,'support.write',null,to_jsonb(v_message),null,p_idempotency_key);
  return to_jsonb(v_message);
end $$;

create or replace function public.admin_close_support_conversation(
  p_conversation_id uuid,p_reason text,p_idempotency_key text
) returns jsonb language plpgsql security definer set search_path=public
as $$
declare v_conversation public.support_conversation; v_before jsonb;
begin
  if not public.has_admin_permission('support.write') then raise exception 'FORBIDDEN'; end if;
  select * into v_conversation from public.support_conversation where id=p_conversation_id for update;
  if v_conversation.id is null then raise exception 'CONVERSATION_NOT_FOUND'; end if;
  if v_conversation.status='CLOSED' then raise exception 'CONVERSATION_ALREADY_CLOSED'; end if;
  v_before:=to_jsonb(v_conversation);
  update public.support_conversation set status='CLOSED',closed_at=now(),closed_by=public.current_admin_id(),updated_at=now()
    where id=p_conversation_id returning * into v_conversation;
  perform public.admin_record_change('CLOSE_SUPPORT_CONVERSATION','support_conversation',v_conversation.id,'support.write',v_before,to_jsonb(v_conversation),p_reason,p_idempotency_key);
  return to_jsonb(v_conversation);
end $$;

-- A20 send campaign to all active users, all active subscribers, or one selected
-- user/subscriber. Dynamic segments whose eligibility rules are not closed in
-- V11 are deliberately not guessed here.
create or replace function public.admin_send_notification(
  p_target_type text,p_target_id uuid,p_title text,p_body text,p_idempotency_key text
) returns jsonb language plpgsql security definer set search_path=public
as $$
declare v_admin uuid; v_campaign public.admin_notification_campaign; v_event public.notification_event;
  v_count integer; v_type text; v_profile public.customer_profile;
begin
  if not public.has_admin_permission('notifications.send') then raise exception 'FORBIDDEN'; end if;
  if nullif(trim(p_title),'') is null or length(trim(p_title))>160 or nullif(trim(p_body),'') is null or length(trim(p_body))>4000 then raise exception 'INVALID_NOTIFICATION'; end if;
  if lower(coalesce(p_target_type,'')) not in ('all','all_subscribers','user','subscriber') then raise exception 'INVALID_TARGET_TYPE'; end if;
  if lower(p_target_type) in ('user','subscriber') and p_target_id is null then raise exception 'TARGET_REQUIRED'; end if;
  v_admin:=public.current_admin_id();
  v_type:=upper(trim(p_target_type));
  if v_type='USER' then
    select * into v_profile from public.customer_profile where id=p_target_id and account_type='USER' and account_status='ACTIVE';
    if v_profile.id is null then raise exception 'TARGET_NOT_FOUND'; end if;
  elsif v_type='SUBSCRIBER' then
    select * into v_profile from public.customer_profile where id=p_target_id and account_type='SUBSCRIBER' and account_status='ACTIVE';
    if v_profile.id is null then raise exception 'TARGET_NOT_FOUND'; end if;
  end if;
  insert into public.admin_notification_campaign(created_by,target_type,target_definition,title,body,status,sent_at)
  values(v_admin,v_type,jsonb_build_object('target_id',p_target_id),trim(p_title),trim(p_body),'SENT',now()) returning * into v_campaign;
  insert into public.notification_event(event_type,source_type,source_id)
  values('ADMIN_CAMPAIGN_SENT','admin_notification_campaign',v_campaign.id) returning * into v_event;
  with recipients as (
    select id from public.customer_profile where account_status='ACTIVE' and (
      v_type='ALL' or (v_type='ALL_SUBSCRIBERS' and account_type='SUBSCRIBER') or
      (v_type='USER' and id=p_target_id) or (v_type='SUBSCRIBER' and id=p_target_id)
    )
  ), ins as (
    insert into public.admin_notification_recipient(campaign_id,customer_id,delivery_status)
    select v_campaign.id,id,'SENT' from recipients returning customer_id
  ), notifications as (
    insert into public.customer_notification(customer_id,event_id,notification_type,title,body,reference_type,reference_id)
    select customer_id,v_event.id,'ADMIN_CAMPAIGN',v_campaign.title,v_campaign.body,'admin_notification_campaign',v_campaign.id from ins
    returning id
  ) select count(*) into v_count from ins;
  if coalesce(v_count,0)=0 then raise exception 'NO_RECIPIENTS'; end if;
  perform public.admin_record_change('SEND_ADMIN_NOTIFICATION','admin_notification_campaign',v_campaign.id,'notifications.send',null,to_jsonb(v_campaign),null,p_idempotency_key);
  return jsonb_build_object('campaign_id',v_campaign.id,'status',v_campaign.status,'recipient_count',v_count,'event_id',v_event.id);
end $$;

create or replace function public.admin_account_info()
returns jsonb language plpgsql stable security definer set search_path=public
as $$
declare v_admin public.admin_identity; v_user_id uuid; v_email text; v_roles jsonb; v_permissions jsonb;
begin
  select ai into v_admin
  from public.admin_identity ai join public.auth_account aa on aa.id=ai.auth_account_id
  where aa.auth_user_id=auth.uid() and ai.status='ACTIVE';
  if v_admin.id is null then raise exception 'ADMIN_REQUIRED'; end if;
  select aa.auth_user_id,u.email into v_user_id,v_email
  from public.auth_account aa join auth.users u on u.id=aa.auth_user_id
  where aa.auth_user_id=auth.uid();
  select coalesce(jsonb_agg(distinct r.name order by r.name),'[]'::jsonb) into v_roles
  from public.admin_assignments a join public.roles r on r.id=a.role_id and r.status='ACTIVE' where a.admin_id=v_admin.id and a.status='ACTIVE';
  select coalesce(jsonb_agg(distinct p.code order by p.code),'[]'::jsonb) into v_permissions
  from public.admin_assignments a join public.role_permissions rp on rp.role_id=a.role_id
    join public.permissions p on p.id=rp.permission_id and p.status='ACTIVE'
  where a.admin_id=v_admin.id and a.status='ACTIVE' and (select status from public.roles where id=a.role_id)='ACTIVE';
  return jsonb_build_object('admin_id',v_admin.id,'admin_code',v_admin.admin_code,'status',v_admin.status,
    'profile',jsonb_build_object('email',v_email),'roles',v_roles,'permissions',v_permissions,'system_version','AMAN V11');
end $$;

-- Restrict internal helper; expose only the explicit administrative RPC surface.
revoke all on function public.admin_record_change(text,text,uuid,text,jsonb,jsonb,text,text) from public,anon,authenticated;
revoke all on function public.admin_update_customer_profile(uuid,text,text,text,text,text) from public,anon;
revoke all on function public.admin_set_customer_number_status(uuid,text,text,text) from public,anon;
revoke all on function public.admin_save_telecom_company(uuid,text,text,text,text) from public,anon;
revoke all on function public.admin_save_telecom_prefix(uuid,uuid,text,text,text) from public,anon;
revoke all on function public.admin_save_points_package(uuid,text,text,bigint,numeric,text,integer,boolean,boolean,text) from public,anon;
revoke all on function public.admin_save_payment_method(uuid,text,text,text,text,text,integer,boolean,boolean,text) from public,anon;
revoke all on function public.admin_save_task_configuration(uuid,integer,numeric,text,integer,boolean,boolean,integer,boolean,boolean,timestamptz,text) from public,anon;
revoke all on function public.admin_execute_periodic_task(uuid,text,text) from public,anon;
revoke all on function public.admin_reschedule_periodic_task(uuid,timestamptz,text,text) from public,anon;
revoke all on function public.admin_cancel_periodic_task(uuid,text,text) from public,anon;
revoke all on function public.admin_create_expense(uuid,numeric,text,text,text,text) from public,anon;
revoke all on function public.admin_send_support_reply(uuid,text,text) from public,anon;
revoke all on function public.admin_close_support_conversation(uuid,text,text) from public,anon;
revoke all on function public.admin_send_notification(text,uuid,text,text,text) from public,anon;
revoke all on function public.admin_account_info() from public,anon;

grant execute on function public.admin_update_customer_profile(uuid,text,text,text,text,text) to authenticated;
grant execute on function public.admin_set_customer_number_status(uuid,text,text,text) to authenticated;
grant execute on function public.admin_save_telecom_company(uuid,text,text,text,text) to authenticated;
grant execute on function public.admin_save_telecom_prefix(uuid,uuid,text,text,text) to authenticated;
grant execute on function public.admin_save_points_package(uuid,text,text,bigint,numeric,text,integer,boolean,boolean,text) to authenticated;
grant execute on function public.admin_save_payment_method(uuid,text,text,text,text,text,integer,boolean,boolean,text) to authenticated;
grant execute on function public.admin_save_task_configuration(uuid,integer,numeric,text,integer,boolean,boolean,integer,boolean,boolean,timestamptz,text) to authenticated;
grant execute on function public.admin_execute_periodic_task(uuid,text,text) to authenticated;
grant execute on function public.admin_reschedule_periodic_task(uuid,timestamptz,text,text) to authenticated;
grant execute on function public.admin_cancel_periodic_task(uuid,text,text) to authenticated;
grant execute on function public.admin_create_expense(uuid,numeric,text,text,text,text) to authenticated;
grant execute on function public.admin_send_support_reply(uuid,text,text) to authenticated;
grant execute on function public.admin_close_support_conversation(uuid,text,text) to authenticated;
grant execute on function public.admin_send_notification(text,uuid,text,text,text) to authenticated;
grant execute on function public.admin_account_info() to authenticated;

commit;
