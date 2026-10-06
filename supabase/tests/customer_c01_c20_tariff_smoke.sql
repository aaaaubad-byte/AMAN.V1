-- Local/development smoke test only. Requires AMAN_V11_DATABASE_FINAL.sql and
-- 20261006190000_customer_c01_c20.sql already applied to a disposable database.
-- The transaction is rolled back; never run this against production/user data.
begin;

do $$
declare
  v_admin_auth uuid := gen_random_uuid();
  v_customer_auth uuid := gen_random_uuid();
  v_admin_id uuid;
  v_customer_id uuid;
  v_provider_id uuid;
  v_phone_id uuid;
  v_number_id uuid;
  v_tariff_id uuid;
  v_period_id uuid;
  v_result jsonb;
  v_balance bigint;
  v_days integer;
begin
  insert into auth.users(id) values(v_admin_auth),(v_customer_auth);
  insert into public.auth_account(auth_user_id) values(v_admin_auth),(v_customer_auth);

  insert into public.admin_identity(auth_account_id,admin_code)
    select id,public.generate_public_code('ADM') from public.auth_account where auth_user_id=v_admin_auth
    returning id into v_admin_id;
  insert into public.admin_assignments(admin_id,role_id)
    select v_admin_id,id from public.roles where code='SUPER_ADMIN';

  insert into public.customer_profile(auth_account_id,public_user_code,name,email,account_type)
    select id,public.generate_public_code('U'),'Smoke Customer','smoke@example.invalid','SUBSCRIBER'
    from public.auth_account where auth_user_id=v_customer_auth returning id into v_customer_id;
  insert into public.subscriber_identity(customer_profile_id,public_subscriber_code)
    values(v_customer_id,public.generate_public_code('S'));
  insert into public.points_balance(customer_id,balance) values(v_customer_id,1000);
  insert into public.telecom_company(code,name) values('SMOKE','Smoke Telco') returning id into v_provider_id;
  insert into public.telecom_prefix(telecom_company_id,prefix) values(v_provider_id,'96477');
  insert into public.phone_number(normalized_phone,display_phone,telecom_company_id)
    values('9647700000000','+9647700000000',v_provider_id) returning id into v_phone_id;
  insert into public.customer_number(customer_id,phone_number_id,public_added_number_code)
    values(v_customer_id,v_phone_id,public.generate_public_code('N')) returning id into v_number_id;

  perform set_config('request.jwt.claim.sub',v_admin_auth::text,true);
  if not public.admin_has_permission('providers.read') or not public.admin_has_permission('providers.write') then
    raise exception 'ADMIN_PERMISSION_BRIDGE_FAILED';
  end if;
  v_result:=public.admin_save_provider_tariff(null,v_provider_id,'MONTHLY',30,120,0,'SAR',now(),null,'active');
  v_tariff_id:=(v_result->>'tariff_id')::uuid;
  if (v_result->>'duration_unit_days')::integer<>30 or (v_result->>'points_per_unit')::bigint<>120 then
    raise exception 'ADMIN_TARIFF_CONTRACT_FAILED: %',v_result;
  end if;

  perform set_config('request.jwt.claim.sub',v_customer_auth::text,true);
  v_result:=public.activate_protection(v_number_id,v_tariff_id,4,'smoke-activate');
  v_period_id:=(v_result->>'protection_period_id')::uuid;
  if (v_result->>'duration_days')::integer<>120 or (v_result->>'points_cost')::bigint<>480 then
    raise exception 'UNIT_QUOTE_FAILED: expected 120 days / 480 points; got %',v_result;
  end if;
  select balance into v_balance from public.points_balance where customer_id=v_customer_id;
  if v_balance<>520 then raise exception 'ACTIVATION_BALANCE_FAILED: %',v_balance; end if;

  v_result:=public.extend_protection(v_period_id,v_tariff_id,2,'smoke-extend');
  if (v_result->>'duration_days')::integer<>60 or (v_result->>'points_cost')::bigint<>240 then
    raise exception 'EXTENSION_UNIT_QUOTE_FAILED: %',v_result;
  end if;
  update public.protection_period set start_at=now()-interval '100 days',end_at=now()-interval '1 day' where id=v_period_id;
  v_result:=public.renew_protection(v_period_id,v_tariff_id,1,'smoke-renew');
  if (v_result->>'duration_days')::integer<>30 or (v_result->>'points_cost')::bigint<>120 then
    raise exception 'RENEWAL_UNIT_QUOTE_FAILED: %',v_result;
  end if;
  select balance into v_balance from public.points_balance where customer_id=v_customer_id;
  if v_balance<>160 then raise exception 'FINAL_BALANCE_FAILED: %',v_balance; end if;
  if (select count(*) from public.points_ledger where customer_id=v_customer_id and direction='DEBIT')<>3 then
    raise exception 'EXPECTED_THREE_ATOMIC_DEBIT_LEDGER_ROWS';
  end if;
  raise notice 'PASS: admin tariff writes columns; activation=4*30d/120pts; extension=2*30d/120pts; renewal=1*30d/120pts; balance=160';
end $$;

rollback;
