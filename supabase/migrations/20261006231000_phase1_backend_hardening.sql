-- AMAN phase 1: additive, transactional hardening of the existing schema.
-- This migration intentionally does not create protection_operation_history or rename existing RPCs.

ALTER TABLE public.telecom_company
  ADD COLUMN IF NOT EXISTS extension_warning_days integer NOT NULL DEFAULT 0;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='telecom_company_extension_warning_days_check') THEN
    ALTER TABLE public.telecom_company ADD CONSTRAINT telecom_company_extension_warning_days_check
      CHECK (extension_warning_days >= 0 AND extension_warning_days <= 36500);
  END IF;
END $$;
COMMENT ON COLUMN public.telecom_company.extension_warning_days IS
  'UI/filter/statistics threshold only; does not alter protection expiry or task scheduling. Zero disables the warning.';

ALTER TABLE public.task_plan
  ADD COLUMN IF NOT EXISTS first_task_due boolean NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS planned_through timestamptz,
  ADD COLUMN IF NOT EXISTS planned_task_count integer NOT NULL DEFAULT 0;
ALTER TABLE public.points_purchase
  ADD COLUMN IF NOT EXISTS submit_idempotency_key text;
ALTER TABLE public.customer_profile
  ADD COLUMN IF NOT EXISTS password_change_required boolean NOT NULL DEFAULT false;
COMMENT ON COLUMN public.customer_profile.public_user_code IS
  'Stable, unique Customer ID and recovery identifier. Generated server-side; never supplied by the customer.';

-- Preserve existing support chats as approved, then make all new submissions requests pending approval.
ALTER TABLE public.support_conversation
  ADD COLUMN IF NOT EXISTS request_status text NOT NULL DEFAULT 'APPROVED',
  ADD COLUMN IF NOT EXISTS requested_at timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN IF NOT EXISTS approved_at timestamptz,
  ADD COLUMN IF NOT EXISTS approved_by uuid REFERENCES public.admin_identity(id) ON DELETE RESTRICT;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='support_conversation_request_status_check') THEN
    ALTER TABLE public.support_conversation ADD CONSTRAINT support_conversation_request_status_check
      CHECK (request_status IN ('PENDING','APPROVED','REJECTED'));
  END IF;
END $$;
ALTER TABLE public.support_conversation ALTER COLUMN request_status SET DEFAULT 'PENDING';
CREATE INDEX IF NOT EXISTS idx_support_request_status_created
  ON public.support_conversation(request_status, requested_at DESC);

CREATE TABLE IF NOT EXISTS public.customer_consent (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id uuid NOT NULL REFERENCES public.customer_profile(id) ON DELETE RESTRICT,
  consent_type text NOT NULL CHECK (consent_type IN ('TERMS','PRIVACY')),
  consent_version text NOT NULL CHECK (length(trim(consent_version)) > 0),
  accepted_at timestamptz NOT NULL DEFAULT now(),
  source text NOT NULL DEFAULT 'SIGNUP',
  UNIQUE(customer_id, consent_type, consent_version)
);
ALTER TABLE public.customer_consent ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS customer_consent_self_read ON public.customer_consent;
CREATE POLICY customer_consent_self_read ON public.customer_consent
  FOR SELECT TO authenticated
  USING (customer_id = public.current_customer_profile_id());
GRANT SELECT ON public.customer_consent TO authenticated;

-- These unique keys are the final concurrency barriers; existing business rows are preserved.
CREATE UNIQUE INDEX IF NOT EXISTS uq_periodic_task_plan_sequence
  ON public.periodic_task(task_plan_id, sequence_no);
CREATE UNIQUE INDEX IF NOT EXISTS uq_points_purchase_customer_idempotency
  ON public.points_purchase(customer_id, submit_idempotency_key)
  WHERE submit_idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_points_purchase_transfer_reference_live
  ON public.points_purchase(customer_id, lower(trim(transfer_reference)))
  WHERE status IN ('PENDING','APPROVED');
CREATE UNIQUE INDEX IF NOT EXISTS uq_points_ledger_purchase_source
  ON public.points_ledger(source_type, source_id, entry_type)
  WHERE source_type='points_purchase' AND source_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_financial_ledger_source_entry
  ON public.financial_ledger(source_type, source_id, entry_type)
  WHERE source_type IS NOT NULL AND source_id IS NOT NULL;

CREATE OR REPLACE FUNCTION public.provision_customer_from_auth_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','auth'
AS $$
DECLARE
  v_auth_account_id uuid;
  v_customer_id uuid;
  v_email text;
  v_name text;
  v_terms text;
  v_privacy text;
BEGIN
  v_email := lower(trim(coalesce(NEW.email,'')));
  v_name := nullif(trim(coalesce(NEW.raw_user_meta_data->>'full_name','')),'');
  IF v_name IS NULL THEN v_name := nullif(split_part(v_email,'@',1),''); END IF;
  IF v_email = '' OR v_name IS NULL THEN
    RAISE EXCEPTION 'CUSTOMER_PROFILE_FIELDS_REQUIRED';
  END IF;

  INSERT INTO public.auth_account(auth_user_id) VALUES (NEW.id)
    ON CONFLICT(auth_user_id) DO UPDATE SET updated_at=now()
    RETURNING id INTO v_auth_account_id;
  INSERT INTO public.customer_profile(auth_account_id,public_user_code,name,email)
    VALUES (v_auth_account_id,public.generate_public_code('U'),v_name,v_email)
    ON CONFLICT(auth_account_id) DO UPDATE SET email=excluded.email,updated_at=now()
    RETURNING id INTO v_customer_id;
  INSERT INTO public.points_balance(customer_id,balance)
    VALUES (v_customer_id,0) ON CONFLICT(customer_id) DO NOTHING;

  v_terms := nullif(trim(NEW.raw_user_meta_data->>'terms_version'),'');
  v_privacy := nullif(trim(NEW.raw_user_meta_data->>'privacy_version'),'');
  IF lower(coalesce(NEW.raw_user_meta_data->>'accepted_terms','false'))='true' AND v_terms IS NOT NULL THEN
    INSERT INTO public.customer_consent(customer_id,consent_type,consent_version,accepted_at,source)
      VALUES(v_customer_id,'TERMS',v_terms,now(),'SIGNUP') ON CONFLICT DO NOTHING;
  END IF;
  IF lower(coalesce(NEW.raw_user_meta_data->>'accepted_privacy','false'))='true' AND v_privacy IS NOT NULL THEN
    INSERT INTO public.customer_consent(customer_id,consent_type,consent_version,accepted_at,source)
      VALUES(v_customer_id,'PRIVACY',v_privacy,now(),'SIGNUP') ON CONFLICT DO NOTHING;
  END IF;
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS aman_provision_customer_after_auth_signup ON auth.users;
CREATE TRIGGER aman_provision_customer_after_auth_signup
  AFTER INSERT ON auth.users FOR EACH ROW EXECUTE FUNCTION public.provision_customer_from_auth_user();

CREATE OR REPLACE FUNCTION public.get_active_consent_versions()
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $$
  SELECT coalesce(jsonb_object_agg(x.content_key,x.version::text),'{}'::jsonb)
  FROM (
    SELECT DISTINCT ON (content_key) content_key,version
    FROM public.app_content
    WHERE status='ACTIVE' AND content_key IN ('TERMS','PRIVACY')
    ORDER BY content_key,version DESC
  ) x
$$;

CREATE OR REPLACE FUNCTION public.create_customer_profile(p_name text, p_email text DEFAULT NULL::text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public','auth'
AS $$
DECLARE
  v_auth_account_id uuid;
  v_profile public.customer_profile;
  v_auth_email text;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED'; END IF;
  IF nullif(trim(p_name),'') IS NULL OR length(trim(p_name))>120 THEN RAISE EXCEPTION 'INVALID_CUSTOMER_NAME'; END IF;
  SELECT lower(email) INTO v_auth_email FROM auth.users WHERE id=auth.uid();
  IF p_email IS NOT NULL AND lower(trim(p_email)) IS DISTINCT FROM v_auth_email THEN
    RAISE EXCEPTION 'AUTH_EMAIL_MISMATCH';
  END IF;
  INSERT INTO public.auth_account(auth_user_id) VALUES(auth.uid())
    ON CONFLICT(auth_user_id) DO UPDATE SET updated_at=now()
    RETURNING id INTO v_auth_account_id;
  SELECT * INTO v_profile FROM public.customer_profile WHERE auth_account_id=v_auth_account_id FOR UPDATE;
  IF v_profile.id IS NULL THEN
    INSERT INTO public.customer_profile(auth_account_id,public_user_code,name,email)
      VALUES(v_auth_account_id,public.generate_public_code('U'),trim(p_name),v_auth_email)
      RETURNING * INTO v_profile;
    INSERT INTO public.points_balance(customer_id,balance) VALUES(v_profile.id,0) ON CONFLICT(customer_id) DO NOTHING;
  ELSE
    UPDATE public.customer_profile SET name=trim(p_name),email=v_auth_email,updated_at=now()
      WHERE id=v_profile.id RETURNING * INTO v_profile;
  END IF;
  RETURN jsonb_build_object('customer_id',v_profile.id,'customer_id_code',v_profile.public_user_code,
    'public_user_code',v_profile.public_user_code,'account_type',v_profile.account_type,
    'account_status',v_profile.account_status);
END;
$$;

CREATE OR REPLACE FUNCTION public.verify_customer_recovery_id(p_customer_id text,p_email text,p_name text)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_customer public.customer_profile;
BEGIN
  IF nullif(trim(p_customer_id),'') IS NULL OR nullif(trim(p_email),'') IS NULL OR nullif(trim(p_name),'') IS NULL THEN RETURN false; END IF;
  SELECT * INTO v_customer FROM public.customer_profile
    WHERE upper(public_user_code)=upper(trim(p_customer_id))
      AND lower(email)=lower(trim(p_email)) AND lower(trim(name))=lower(trim(p_name)) AND account_status='ACTIVE'
    FOR UPDATE;
  IF v_customer.id IS NULL THEN RETURN false; END IF;
  UPDATE public.customer_profile SET password_change_required=true,updated_at=now() WHERE id=v_customer.id;
  RETURN true;
END;
$$;

CREATE OR REPLACE FUNCTION public.complete_customer_password_recovery()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_customer_id uuid;
BEGIN
  v_customer_id:=public.current_customer_profile_id();
  IF v_customer_id IS NULL THEN RAISE EXCEPTION 'CUSTOMER_NOT_FOUND'; END IF;
  UPDATE public.customer_profile SET password_change_required=false,updated_at=now()
    WHERE id=v_customer_id AND password_change_required=true;
  RETURN jsonb_build_object('password_change_required',false,'updated',FOUND);
END;
$$;

CREATE OR REPLACE FUNCTION public.enforce_single_active_protection_per_phone()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_phone_id uuid;
BEGIN
  IF NEW.status <> 'ACTIVE' OR NEW.end_at <= now() THEN RETURN NEW; END IF;
  SELECT cn.phone_number_id INTO v_phone_id
  FROM public.number_protection_identity ni
  JOIN public.customer_number cn ON cn.id=ni.customer_number_id
  WHERE ni.id=NEW.protection_identity_id;
  IF v_phone_id IS NULL THEN RAISE EXCEPTION 'CUSTOMER_NUMBER_NOT_FOUND'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(v_phone_id::text,0));
  IF EXISTS (
    SELECT 1 FROM public.protection_period other
    JOIN public.number_protection_identity oni ON oni.id=other.protection_identity_id
    JOIN public.customer_number ocn ON ocn.id=oni.customer_number_id
    WHERE ocn.phone_number_id=v_phone_id AND other.id<>NEW.id
      AND other.status='ACTIVE' AND other.end_at>now()
  ) THEN RAISE EXCEPTION 'PROTECTION_ALREADY_ACTIVE_FOR_PHONE'; END IF;
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS protection_period_single_phone_active ON public.protection_period;
CREATE TRIGGER protection_period_single_phone_active
  BEFORE INSERT OR UPDATE OF protection_identity_id,status,end_at ON public.protection_period
  FOR EACH ROW EXECUTE FUNCTION public.enforce_single_active_protection_per_phone();

CREATE OR REPLACE FUNCTION public.generate_task_plan_tasks(p_task_plan_id uuid)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  v_plan public.task_plan;
  v_period public.protection_period;
  v_config public.task_configuration;
  v_company public.telecom_company;
  v_customer_number_id uuid;
  v_horizon timestamptz;
  v_intervals integer;
  v_required integer;
  v_first_offset integer;
  v_inserted integer;
BEGIN
  SELECT * INTO v_plan FROM public.task_plan WHERE id=p_task_plan_id FOR UPDATE;
  IF v_plan.id IS NULL THEN RAISE EXCEPTION 'TASK_PLAN_NOT_FOUND'; END IF;
  SELECT * INTO v_period FROM public.protection_period WHERE id=v_plan.protection_period_id FOR UPDATE;
  IF v_period.id IS NULL THEN RAISE EXCEPTION 'PROTECTION_NOT_FOUND'; END IF;
  SELECT * INTO v_config FROM public.task_configuration WHERE id=v_plan.task_configuration_id;
  IF v_config.id IS NULL OR v_config.interval_days<=0 THEN RAISE EXCEPTION 'TASK_CONFIGURATION_NOT_FOUND'; END IF;
  SELECT * INTO v_company FROM public.telecom_company WHERE id=v_config.telecom_company_id;
  IF v_company.id IS NULL THEN RAISE EXCEPTION 'COMPANY_NOT_FOUND'; END IF;
  SELECT cn.id INTO v_customer_number_id
  FROM public.number_protection_identity ni JOIN public.customer_number cn ON cn.id=ni.customer_number_id
  WHERE ni.id=v_period.protection_identity_id AND cn.customer_id=v_period.customer_id;
  IF v_customer_number_id IS NULL THEN RAISE EXCEPTION 'CUSTOMER_NUMBER_NOT_FOUND'; END IF;

  v_horizon := v_period.end_at + CASE WHEN v_config.allow_post_expiry_creation
    THEN make_interval(days=>v_config.post_expiry_grace_days) ELSE interval '0' END;
  v_first_offset := CASE WHEN v_plan.first_task_due THEN 0 ELSE 1 END;
  v_intervals := greatest(0,floor(extract(epoch FROM (v_horizon-v_plan.anchor_at)) /
    (v_config.interval_days::numeric*86400))::integer + 1 - v_first_offset);
  v_required := v_intervals;

  INSERT INTO public.periodic_task(
    public_task_code,task_plan_id,protection_period_id,customer_id,customer_number_id,
    telecom_company_id,sequence_no,planned_total,due_at,amount,currency,status,
    company_snapshot,amount_snapshot,currency_snapshot,interval_snapshot
  )
  SELECT public.generate_public_code('T'),v_plan.id,v_period.id,v_period.customer_id,
    v_customer_number_id,v_company.id,g.seq,v_required,
    v_plan.anchor_at+make_interval(days=>((g.seq-1+v_first_offset)*v_config.interval_days)),
    v_config.task_amount,v_config.currency,'OPEN',v_company.name,v_config.task_amount,
    v_config.currency,v_config.interval_days
  FROM generate_series(1,v_required) AS g(seq)
  ON CONFLICT(task_plan_id,sequence_no) DO NOTHING;
  GET DIAGNOSTICS v_inserted=ROW_COUNT;
  UPDATE public.task_plan SET planned_through=v_horizon,
    planned_task_count=greatest(planned_task_count,v_required),
    version=version+CASE WHEN v_inserted>0 OR planned_through IS DISTINCT FROM v_horizon THEN 1 ELSE 0 END,
    updated_at=now()
  WHERE id=v_plan.id;
  RETURN v_inserted;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_task_plan_for_protection(p_period_id uuid,p_is_renewal boolean)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  v_period public.protection_period;
  v_company_id uuid;
  v_config public.task_configuration;
  v_plan_id uuid;
  v_first boolean;
BEGIN
  SELECT * INTO v_period FROM public.protection_period WHERE id=p_period_id FOR UPDATE;
  IF v_period.id IS NULL THEN RAISE EXCEPTION 'PROTECTION_NOT_FOUND'; END IF;
  SELECT pn.telecom_company_id INTO v_company_id
  FROM public.number_protection_identity ni JOIN public.customer_number cn ON cn.id=ni.customer_number_id
  JOIN public.phone_number pn ON pn.id=cn.phone_number_id
  WHERE ni.id=v_period.protection_identity_id AND cn.customer_id=v_period.customer_id;
  IF v_company_id IS NULL THEN RAISE EXCEPTION 'CUSTOMER_NUMBER_NOT_FOUND'; END IF;
  SELECT * INTO v_config FROM public.task_configuration
  WHERE telecom_company_id=v_company_id AND status='ACTIVE'
    AND effective_from<=v_period.start_at AND (effective_to IS NULL OR effective_to>v_period.start_at)
  ORDER BY effective_from DESC LIMIT 1 FOR SHARE;
  IF v_config.id IS NULL THEN RAISE EXCEPTION 'TASK_CONFIGURATION_REQUIRED_FOR_COMPANY'; END IF;
  v_first := CASE WHEN p_is_renewal THEN v_config.create_first_task_on_renewal
    ELSE v_config.create_first_task_on_activation END;
  INSERT INTO public.task_plan(protection_period_id,task_configuration_id,status,anchor_at,version,first_task_due)
  VALUES(v_period.id,v_config.id,'ACTIVE',v_period.start_at,1,v_first)
  ON CONFLICT(protection_period_id) DO UPDATE SET status='ACTIVE'
  RETURNING id INTO v_plan_id;
  PERFORM public.generate_task_plan_tasks(v_plan_id);
  RETURN v_plan_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.on_protection_period_change_generate_tasks()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_plan_id uuid; v_is_renewal boolean;
BEGIN
  IF NEW.status <> 'ACTIVE' THEN RETURN NEW; END IF;
  SELECT id INTO v_plan_id FROM public.task_plan WHERE protection_period_id=NEW.id;
  IF v_plan_id IS NULL THEN
    SELECT EXISTS(SELECT 1 FROM public.protection_period old
      WHERE old.protection_identity_id=NEW.protection_identity_id AND old.id<>NEW.id
        AND (old.status='EXPIRED' OR old.end_at<=NEW.start_at)) INTO v_is_renewal;
    PERFORM public.create_task_plan_for_protection(NEW.id,coalesce(v_is_renewal,false));
  ELSE
    PERFORM public.generate_task_plan_tasks(v_plan_id);
  END IF;
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS protection_period_task_plan_generator ON public.protection_period;
CREATE TRIGGER protection_period_task_plan_generator
  AFTER INSERT OR UPDATE OF end_at,status ON public.protection_period
  FOR EACH ROW EXECUTE FUNCTION public.on_protection_period_change_generate_tasks();

CREATE OR REPLACE FUNCTION public.enforce_single_active_protection_per_phone()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_phone_id uuid;
BEGIN
  IF NEW.status<>'ACTIVE' OR NEW.end_at<=now() THEN RETURN NEW; END IF;
  SELECT pn.id INTO v_phone_id
  FROM public.number_protection_identity ni
  JOIN public.customer_number cn ON cn.id=ni.customer_number_id
  JOIN public.phone_number pn ON pn.id=cn.phone_number_id
  WHERE ni.id=NEW.protection_identity_id;
  IF v_phone_id IS NULL THEN RAISE EXCEPTION 'PHONE_NUMBER_NOT_FOUND'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(v_phone_id::text||':ACTIVE_PROTECTION',0));
  IF EXISTS (
    SELECT 1 FROM public.protection_period pp
    JOIN public.number_protection_identity ni ON ni.id=pp.protection_identity_id
    JOIN public.customer_number cn ON cn.id=ni.customer_number_id
    WHERE cn.phone_number_id=v_phone_id AND pp.status='ACTIVE' AND pp.end_at>now()
      AND pp.id IS DISTINCT FROM NEW.id
  ) THEN RAISE EXCEPTION 'PHONE_ALREADY_HAS_ACTIVE_PROTECTION'; END IF;
  RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS protection_period_single_active_phone ON public.protection_period;
CREATE TRIGGER protection_period_single_active_phone
  BEFORE INSERT OR UPDATE OF status,end_at,protection_identity_id ON public.protection_period
  FOR EACH ROW EXECUTE FUNCTION public.enforce_single_active_protection_per_phone();

CREATE OR REPLACE FUNCTION public.generate_scheduled_tasks()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_plan record; v_count integer:=0;
BEGIN
  FOR v_plan IN
    SELECT tp.id FROM public.task_plan tp
    JOIN public.protection_period pp ON pp.id=tp.protection_period_id
    JOIN public.task_configuration tc ON tc.id=tp.task_configuration_id
    WHERE tp.status='ACTIVE'
      AND (pp.status='ACTIVE' OR (tc.allow_post_expiry_creation AND
        pp.end_at+make_interval(days=>tc.post_expiry_grace_days)>now()))
    ORDER BY tp.updated_at,tp.id
  LOOP
    v_count:=v_count+public.generate_task_plan_tasks(v_plan.id);
  END LOOP;
  RETURN v_count;
END;
$$;

CREATE OR REPLACE FUNCTION public.submit_points_purchase(
  p_package_id uuid,p_payment_method_id uuid,p_transfer_reference text,p_idempotency_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  v_customer_id uuid;
  v_package public.points_package;
  v_method public.payment_method;
  v_purchase public.points_purchase;
  v_operation uuid;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'AUTH_REQUIRED'; END IF;
  IF nullif(trim(p_idempotency_key),'') IS NULL THEN RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED'; END IF;
  IF nullif(trim(p_transfer_reference),'') IS NULL OR length(trim(p_transfer_reference))>240 THEN RAISE EXCEPTION 'PAYMENT_REFERENCE_REQUIRED'; END IF;
  v_customer_id:=public.current_customer_profile_id();
  IF v_customer_id IS NULL THEN RAISE EXCEPTION 'CUSTOMER_NOT_FOUND_OR_INACTIVE'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(v_customer_id::text||':SUBMIT_POINTS_PURCHASE:'||trim(p_idempotency_key),0));
  SELECT operation_id INTO v_operation FROM public.operation_idempotency
    WHERE actor_id=v_customer_id AND operation_type='SUBMIT_POINTS_PURCHASE' AND idempotency_key=trim(p_idempotency_key);
  IF v_operation IS NOT NULL THEN
    RETURN jsonb_build_object('purchase_id',(SELECT entity_id FROM public.operation WHERE id=v_operation),
      'operation_id',v_operation,'idempotent',true);
  END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(v_customer_id::text||':TRANSFER:'||lower(trim(p_transfer_reference)),0));
  IF EXISTS(SELECT 1 FROM public.points_purchase WHERE customer_id=v_customer_id
    AND lower(trim(transfer_reference))=lower(trim(p_transfer_reference)) AND status IN ('PENDING','APPROVED'))
    THEN RAISE EXCEPTION 'PAYMENT_REFERENCE_ALREADY_USED'; END IF;
  SELECT * INTO v_package FROM public.points_package WHERE id=p_package_id AND is_active AND is_visible FOR SHARE;
  IF v_package.id IS NULL THEN RAISE EXCEPTION 'PACKAGE_NOT_AVAILABLE'; END IF;
  SELECT * INTO v_method FROM public.payment_method WHERE id=p_payment_method_id AND is_active AND is_visible FOR SHARE;
  IF v_method.id IS NULL THEN RAISE EXCEPTION 'PAYMENT_METHOD_NOT_AVAILABLE'; END IF;

  INSERT INTO public.points_purchase(public_purchase_code,customer_id,package_id,payment_method_id,
    package_name_snapshot,points_snapshot,price_snapshot,currency_snapshot,payment_method_name_snapshot,
    receiving_account_snapshot,payment_instructions_snapshot,transfer_reference,submit_idempotency_key)
  VALUES(public.generate_public_code('P'),v_customer_id,v_package.id,v_method.id,v_package.name,v_package.points,
    v_package.price,v_package.currency,v_method.name,v_method.receiving_account,v_method.transfer_instructions,
    trim(p_transfer_reference),trim(p_idempotency_key)) RETURNING * INTO v_purchase;
  INSERT INTO public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,
    idempotency_key,result_reference,completed_at)
  VALUES(public.generate_public_code('OP'),'PURCHASE_POINTS','CUSTOMER',v_customer_id,'points_purchase',
    v_purchase.id,'COMPLETED',trim(p_idempotency_key),v_purchase.id::text,now()) RETURNING id INTO v_operation;
  INSERT INTO public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id)
    VALUES(v_customer_id,'SUBMIT_POINTS_PURCHASE',trim(p_idempotency_key),v_operation);
  INSERT INTO public.audit_log(actor_type,actor_id,operation_id,entity_type,entity_id,action,after_state)
    VALUES('CUSTOMER',v_customer_id,v_operation,'points_purchase',v_purchase.id,'SUBMIT_POINTS_PURCHASE',to_jsonb(v_purchase));
  RETURN jsonb_build_object('purchase_id',v_purchase.id,'purchase_code',v_purchase.public_purchase_code,
    'status',v_purchase.status,'operation_id',v_operation);
EXCEPTION WHEN unique_violation THEN
  IF SQLERRM LIKE '%uq_points_purchase_transfer_reference_live%' THEN RAISE EXCEPTION 'PAYMENT_REFERENCE_ALREADY_USED'; END IF;
  RAISE;
END;
$$;

CREATE OR REPLACE FUNCTION public.approve_points_purchase(p_purchase_id uuid,p_idempotency_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  v_purchase public.points_purchase;
  v_balance public.points_balance;
  v_customer public.customer_profile;
  v_subscriber public.subscriber_identity;
  v_admin uuid;
  v_operation uuid;
  v_event uuid;
  v_financial_balance numeric;
BEGIN
  v_admin:=public.current_admin_id();
  IF v_admin IS NULL OR NOT public.has_admin_permission('points_purchases.approve') THEN RAISE EXCEPTION 'FORBIDDEN'; END IF;
  IF nullif(trim(p_idempotency_key),'') IS NULL THEN RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(v_admin::text||':APPROVE_PURCHASE:'||trim(p_idempotency_key),0));
  SELECT operation_id INTO v_operation FROM public.operation_idempotency
    WHERE actor_id=v_admin AND operation_type='APPROVE_PURCHASE' AND idempotency_key=trim(p_idempotency_key);
  IF v_operation IS NOT NULL THEN RETURN jsonb_build_object('purchase_id',(SELECT entity_id FROM public.operation WHERE id=v_operation),
    'operation_id',v_operation,'idempotent',true); END IF;
  SELECT * INTO v_purchase FROM public.points_purchase WHERE id=p_purchase_id FOR UPDATE;
  IF v_purchase.id IS NULL THEN RAISE EXCEPTION 'PURCHASE_NOT_FOUND'; END IF;
  IF v_purchase.status<>'PENDING' THEN RAISE EXCEPTION 'PURCHASE_NOT_PENDING'; END IF;
  SELECT * INTO v_customer FROM public.customer_profile WHERE id=v_purchase.customer_id AND account_status='ACTIVE' FOR UPDATE;
  IF v_customer.id IS NULL THEN RAISE EXCEPTION 'CUSTOMER_NOT_ACTIVE'; END IF;
  INSERT INTO public.points_balance(customer_id,balance) VALUES(v_customer.id,0) ON CONFLICT(customer_id) DO NOTHING;
  SELECT * INTO v_balance FROM public.points_balance WHERE customer_id=v_customer.id FOR UPDATE;
  UPDATE public.points_purchase SET status='APPROVED',reviewed_at=now(),reviewed_by=v_admin,updated_at=now()
    WHERE id=v_purchase.id RETURNING * INTO v_purchase;
  INSERT INTO public.subscriber_identity(customer_profile_id,public_subscriber_code)
    VALUES(v_customer.id,public.generate_public_code('S')) ON CONFLICT(customer_profile_id) DO UPDATE SET updated_at=now()
    RETURNING * INTO v_subscriber;
  UPDATE public.customer_profile SET account_type='SUBSCRIBER',updated_at=now() WHERE id=v_customer.id;
  INSERT INTO public.points_ledger(customer_id,direction,amount,balance_before,balance_after,entry_type,source_type,source_id,description,created_by)
    VALUES(v_customer.id,'CREDIT',v_purchase.points_snapshot,v_balance.balance,v_balance.balance+v_purchase.points_snapshot,
      'PURCHASE_CREDIT','points_purchase',v_purchase.id,'Approved points purchase',v_admin);
  UPDATE public.points_balance SET balance=balance+v_purchase.points_snapshot,updated_at=now() WHERE id=v_balance.id;
  INSERT INTO public.financial_ledger(entry_type,direction,amount,currency,source_type,source_id,description,created_by)
    VALUES('POINTS_SALE','INFLOW',v_purchase.price_snapshot,v_purchase.currency_snapshot,'points_purchase',v_purchase.id,
      'Approved points purchase',v_admin);
  SELECT coalesce(sum(CASE WHEN direction='INFLOW' THEN amount ELSE -amount END),0)
    INTO v_financial_balance FROM public.financial_ledger WHERE currency=v_purchase.currency_snapshot;
  INSERT INTO public.financial_account(currency,balance) VALUES(v_purchase.currency_snapshot,v_financial_balance)
    ON CONFLICT(currency) DO UPDATE SET balance=excluded.balance,updated_at=now();
  INSERT INTO public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,idempotency_key,result_reference,completed_at)
    VALUES(public.generate_public_code('OP'),'APPROVE_PURCHASE','ADMIN',v_admin,'points_purchase',v_purchase.id,'COMPLETED',
      trim(p_idempotency_key),v_purchase.id::text,now()) RETURNING id INTO v_operation;
  INSERT INTO public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id)
    VALUES(v_admin,'APPROVE_PURCHASE',trim(p_idempotency_key),v_operation);
  INSERT INTO public.notification_event(event_type,source_type,source_id)
    VALUES('POINTS_PURCHASE_APPROVED','points_purchase',v_purchase.id) RETURNING id INTO v_event;
  INSERT INTO public.customer_notification(customer_id,event_id,notification_type,title,body,reference_type,reference_id)
    VALUES(v_customer.id,v_event,'POINTS_PURCHASE','تم قبول طلب شراء النقاط','تمت الموافقة على طلب شراء النقاط وإضافة الرصيد.','points_purchase',v_purchase.id);
  INSERT INTO public.audit_log(actor_type,actor_id,operation_id,entity_type,entity_id,action,permission_code,after_state)
    VALUES('ADMIN',v_admin,v_operation,'points_purchase',v_purchase.id,'APPROVE_PURCHASE','points_purchases.approve',to_jsonb(v_purchase));
  RETURN jsonb_build_object('purchase_id',v_purchase.id,'status',v_purchase.status,'subscriber_id',v_subscriber.id,
    'balance',v_balance.balance+v_purchase.points_snapshot,'operation_id',v_operation);
END;
$$;

CREATE OR REPLACE FUNCTION public.reject_points_purchase(p_purchase_id uuid,p_reason text,p_idempotency_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_purchase public.points_purchase; v_admin uuid; v_operation uuid; v_event uuid;
BEGIN
  v_admin:=public.current_admin_id();
  IF v_admin IS NULL OR NOT public.has_admin_permission('points_purchases.reject') THEN RAISE EXCEPTION 'FORBIDDEN'; END IF;
  IF nullif(trim(p_reason),'') IS NULL THEN RAISE EXCEPTION 'REJECTION_REASON_REQUIRED'; END IF;
  IF nullif(trim(p_idempotency_key),'') IS NULL THEN RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(v_admin::text||':REJECT_PURCHASE:'||trim(p_idempotency_key),0));
  SELECT operation_id INTO v_operation FROM public.operation_idempotency
    WHERE actor_id=v_admin AND operation_type='REJECT_PURCHASE' AND idempotency_key=trim(p_idempotency_key);
  IF v_operation IS NOT NULL THEN RETURN jsonb_build_object('purchase_id',(SELECT entity_id FROM public.operation WHERE id=v_operation),
    'operation_id',v_operation,'idempotent',true); END IF;
  SELECT * INTO v_purchase FROM public.points_purchase WHERE id=p_purchase_id FOR UPDATE;
  IF v_purchase.id IS NULL THEN RAISE EXCEPTION 'PURCHASE_NOT_FOUND'; END IF;
  IF v_purchase.status<>'PENDING' THEN RAISE EXCEPTION 'PURCHASE_NOT_PENDING'; END IF;
  UPDATE public.points_purchase SET status='REJECTED',rejection_reason=trim(p_reason),reviewed_at=now(),reviewed_by=v_admin,updated_at=now()
    WHERE id=p_purchase_id RETURNING * INTO v_purchase;
  INSERT INTO public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,idempotency_key,result_reference,completed_at)
    VALUES(public.generate_public_code('OP'),'REJECT_PURCHASE','ADMIN',v_admin,'points_purchase',v_purchase.id,'COMPLETED',
      trim(p_idempotency_key),v_purchase.id::text,now()) RETURNING id INTO v_operation;
  INSERT INTO public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id)
    VALUES(v_admin,'REJECT_PURCHASE',trim(p_idempotency_key),v_operation);
  INSERT INTO public.notification_event(event_type,source_type,source_id)
    VALUES('POINTS_PURCHASE_REJECTED','points_purchase',v_purchase.id) RETURNING id INTO v_event;
  INSERT INTO public.customer_notification(customer_id,event_id,notification_type,title,body,reference_type,reference_id)
    VALUES(v_purchase.customer_id,v_event,'POINTS_PURCHASE','تم رفض طلب شراء النقاط',
      'تم رفض طلب شراء النقاط. السبب: '||trim(p_reason),'points_purchase',v_purchase.id);
  INSERT INTO public.audit_log(actor_type,actor_id,operation_id,entity_type,entity_id,action,permission_code,after_state,reason)
    VALUES('ADMIN',v_admin,v_operation,'points_purchase',v_purchase.id,'REJECT_PURCHASE','points_purchases.reject',to_jsonb(v_purchase),trim(p_reason));
  RETURN jsonb_build_object('purchase_id',v_purchase.id,'status',v_purchase.status,'reason',v_purchase.rejection_reason,'operation_id',v_operation);
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_execute_periodic_task(p_task_id uuid,p_external_reference text,p_idempotency_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE
  v_admin uuid;
  v_task public.periodic_task;
  v_execution public.task_execution;
  v_account public.financial_account;
  v_operation uuid;
  v_before jsonb;
  v_available numeric;
BEGIN
  v_admin:=public.current_admin_id();
  IF v_admin IS NULL OR NOT public.has_admin_permission('tasks.execute') THEN RAISE EXCEPTION 'FORBIDDEN'; END IF;
  IF nullif(trim(p_external_reference),'') IS NULL THEN RAISE EXCEPTION 'EXTERNAL_REFERENCE_REQUIRED'; END IF;
  IF nullif(trim(p_idempotency_key),'') IS NULL THEN RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(v_admin::text||':EXECUTE_PERIODIC_TASK:'||trim(p_idempotency_key),0));
  SELECT operation_id INTO v_operation FROM public.operation_idempotency
    WHERE actor_id=v_admin AND operation_type='EXECUTE_PERIODIC_TASK' AND idempotency_key=trim(p_idempotency_key);
  IF v_operation IS NOT NULL THEN
    SELECT * INTO v_execution FROM public.task_execution WHERE task_id=(SELECT entity_id FROM public.operation WHERE id=v_operation);
    RETURN jsonb_build_object('operation_id',v_operation,'execution',to_jsonb(v_execution),'idempotent',true);
  END IF;
  SELECT * INTO v_task FROM public.periodic_task WHERE id=p_task_id FOR UPDATE;
  IF v_task.id IS NULL THEN RAISE EXCEPTION 'TASK_NOT_FOUND'; END IF;
  IF v_task.status='COMPLETED' THEN
    SELECT * INTO v_execution FROM public.task_execution WHERE task_id=v_task.id;
    IF v_execution.id IS NOT NULL THEN RETURN jsonb_build_object('task',to_jsonb(v_task),'execution',to_jsonb(v_execution),'idempotent',true); END IF;
  END IF;
  IF v_task.status<>'OPEN' THEN RAISE EXCEPTION 'TASK_NOT_OPEN'; END IF;
  v_before:=to_jsonb(v_task);
  INSERT INTO public.financial_account(currency,balance) VALUES(v_task.currency,0)
    ON CONFLICT(currency) DO NOTHING;
  SELECT * INTO v_account FROM public.financial_account WHERE currency=v_task.currency FOR UPDATE;
  IF v_account.status<>'ACTIVE' THEN RAISE EXCEPTION 'FINANCIAL_ACCOUNT_INACTIVE'; END IF;
  SELECT coalesce(sum(CASE WHEN direction='INFLOW' THEN amount ELSE -amount END),0)
    INTO v_available FROM public.financial_ledger WHERE currency=v_task.currency;
  IF v_available<v_task.amount THEN RAISE EXCEPTION 'INSUFFICIENT_LEDGER_FUNDS'; END IF;
  INSERT INTO public.financial_ledger(entry_type,direction,amount,currency,source_type,source_id,description,created_by)
    VALUES('TASK_EXPENSE','OUTFLOW',v_task.amount,v_task.currency,'periodic_task',v_task.id,
      'سداد المهمة '||v_task.public_task_code,v_admin);
  UPDATE public.financial_account SET balance=v_available-v_task.amount,updated_at=now() WHERE id=v_account.id;
  UPDATE public.periodic_task SET status='COMPLETED',executed_at=now(),updated_at=now() WHERE id=v_task.id RETURNING * INTO v_task;
  INSERT INTO public.task_execution(task_id,execution_result,external_reference,executed_at,executed_by,amount,currency)
    VALUES(v_task.id,'SUCCESS',trim(p_external_reference),now(),v_admin,v_task.amount,v_task.currency) RETURNING * INTO v_execution;
  INSERT INTO public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,idempotency_key,result_reference,completed_at)
    VALUES(public.generate_public_code('OP'),'EXECUTE_PERIODIC_TASK','ADMIN',v_admin,'periodic_task',v_task.id,'COMPLETED',
      trim(p_idempotency_key),v_execution.id::text,now()) RETURNING id INTO v_operation;
  INSERT INTO public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id)
    VALUES(v_admin,'EXECUTE_PERIODIC_TASK',trim(p_idempotency_key),v_operation);
  INSERT INTO public.audit_log(actor_type,actor_id,operation_id,entity_type,entity_id,action,permission_code,before_state,after_state,reason)
    VALUES('ADMIN',v_admin,v_operation,'periodic_task',v_task.id,'EXECUTE_PERIODIC_TASK','tasks.execute',v_before,to_jsonb(v_task),trim(p_external_reference));
  RETURN jsonb_build_object('task',to_jsonb(v_task),'execution',to_jsonb(v_execution),'operation_id',v_operation);
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_create_expense(
  p_expense_type_id uuid,p_amount numeric,p_currency text,p_description text,p_reference text,p_idempotency_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_admin uuid; v_account public.financial_account; v_expense public.expense; v_ledger public.financial_ledger;
  v_operation uuid; v_available numeric;
BEGIN
  v_admin:=public.current_admin_id();
  IF v_admin IS NULL OR NOT public.has_admin_permission('finance.write') THEN RAISE EXCEPTION 'FORBIDDEN'; END IF;
  IF p_amount IS NULL OR p_amount<=0 OR nullif(trim(p_currency),'') IS NULL OR nullif(trim(p_description),'') IS NULL
    OR nullif(trim(p_idempotency_key),'') IS NULL THEN RAISE EXCEPTION 'INVALID_EXPENSE'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(v_admin::text||':CREATE_EXPENSE:'||trim(p_idempotency_key),0));
  SELECT operation_id INTO v_operation FROM public.operation_idempotency
    WHERE actor_id=v_admin AND operation_type='CREATE_EXPENSE' AND idempotency_key=trim(p_idempotency_key);
  IF v_operation IS NOT NULL THEN RETURN jsonb_build_object('expense_id',(SELECT entity_id FROM public.operation WHERE id=v_operation),'operation_id',v_operation,'idempotent',true); END IF;
  INSERT INTO public.financial_account(currency,balance) VALUES(trim(p_currency),0) ON CONFLICT(currency) DO NOTHING;
  SELECT * INTO v_account FROM public.financial_account WHERE currency=trim(p_currency) FOR UPDATE;
  IF v_account.status<>'ACTIVE' THEN RAISE EXCEPTION 'FINANCIAL_ACCOUNT_INACTIVE'; END IF;
  SELECT coalesce(sum(CASE WHEN direction='INFLOW' THEN amount ELSE -amount END),0)
    INTO v_available FROM public.financial_ledger WHERE currency=trim(p_currency);
  IF v_available<p_amount THEN RAISE EXCEPTION 'INSUFFICIENT_LEDGER_FUNDS'; END IF;
  INSERT INTO public.expense(expense_type_id,amount,currency,description,reference,posted_by)
    VALUES(p_expense_type_id,p_amount,trim(p_currency),trim(p_description),nullif(trim(p_reference),''),v_admin) RETURNING * INTO v_expense;
  INSERT INTO public.financial_ledger(entry_type,direction,amount,currency,source_type,source_id,description,created_by)
    VALUES('OPERATING_EXPENSE','OUTFLOW',p_amount,trim(p_currency),'expense',v_expense.id,trim(p_description),v_admin) RETURNING * INTO v_ledger;
  UPDATE public.financial_account SET balance=v_available-p_amount,updated_at=now() WHERE id=v_account.id;
  INSERT INTO public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,idempotency_key,result_reference,completed_at)
    VALUES(public.generate_public_code('OP'),'CREATE_EXPENSE','ADMIN',v_admin,'expense',v_expense.id,'COMPLETED',trim(p_idempotency_key),v_expense.id::text,now())
    RETURNING id INTO v_operation;
  INSERT INTO public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id)
    VALUES(v_admin,'CREATE_EXPENSE',trim(p_idempotency_key),v_operation);
  INSERT INTO public.audit_log(actor_type,actor_id,operation_id,entity_type,entity_id,action,permission_code,after_state,reason)
    VALUES('ADMIN',v_admin,v_operation,'expense',v_expense.id,'CREATE_EXPENSE','finance.write',to_jsonb(v_expense),trim(p_description));
  RETURN jsonb_build_object('expense',to_jsonb(v_expense),'ledger',to_jsonb(v_ledger),'balance_after',v_available-p_amount,'operation_id',v_operation);
END;
$$;

CREATE OR REPLACE FUNCTION public.create_support_conversation(p_subject text,p_body text,p_idempotency_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_customer_id uuid; v_conversation uuid; v_message uuid; v_operation uuid;
BEGIN
  v_customer_id:=public.current_customer_profile_id();
  IF v_customer_id IS NULL THEN RAISE EXCEPTION 'CUSTOMER_NOT_FOUND_OR_INACTIVE'; END IF;
  IF nullif(trim(p_subject),'') IS NULL OR length(trim(p_subject))>160 THEN RAISE EXCEPTION 'INVALID_SUBJECT'; END IF;
  IF nullif(trim(p_body),'') IS NULL OR length(trim(p_body))>10000 THEN RAISE EXCEPTION 'INVALID_MESSAGE'; END IF;
  IF nullif(trim(p_idempotency_key),'') IS NULL THEN RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(v_customer_id::text||':CREATE_SUPPORT_CONVERSATION:'||trim(p_idempotency_key),0));
  SELECT operation_id INTO v_operation FROM public.operation_idempotency WHERE actor_id=v_customer_id
    AND operation_type='CREATE_SUPPORT_CONVERSATION' AND idempotency_key=trim(p_idempotency_key);
  IF v_operation IS NOT NULL THEN RETURN jsonb_build_object('operation_id',v_operation,
    'conversation_id',(SELECT entity_id FROM public.operation WHERE id=v_operation),'idempotent',true); END IF;
  INSERT INTO public.support_conversation(customer_id,subject,status,request_status,requested_at)
    VALUES(v_customer_id,trim(p_subject),'OPEN','PENDING',now()) RETURNING id INTO v_conversation;
  INSERT INTO public.support_message(conversation_id,sender_type,sender_id,body)
    VALUES(v_conversation,'CUSTOMER',v_customer_id,trim(p_body)) RETURNING id INTO v_message;
  INSERT INTO public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,idempotency_key,result_reference,completed_at)
    VALUES(public.generate_public_code('OP'),'CREATE_SUPPORT_CONVERSATION','CUSTOMER',v_customer_id,'support_conversation',v_conversation,'COMPLETED',
      trim(p_idempotency_key),v_message::text,now()) RETURNING id INTO v_operation;
  INSERT INTO public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id)
    VALUES(v_customer_id,'CREATE_SUPPORT_CONVERSATION',trim(p_idempotency_key),v_operation);
  INSERT INTO public.audit_log(actor_type,actor_id,operation_id,entity_type,entity_id,action,after_state)
    VALUES('CUSTOMER',v_customer_id,v_operation,'support_conversation',v_conversation,'CREATE_SUPPORT_REQUEST',
      jsonb_build_object('subject',trim(p_subject),'request_status','PENDING','initial_message_id',v_message));
  RETURN jsonb_build_object('conversation_id',v_conversation,'message_id',v_message,'operation_id',v_operation,'request_status','PENDING');
END;
$$;

CREATE OR REPLACE FUNCTION public.send_support_message(p_conversation_id uuid,p_body text,p_idempotency_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_customer_id uuid; v_message uuid; v_operation uuid;
BEGIN
  v_customer_id:=public.current_customer_profile_id();
  IF v_customer_id IS NULL THEN RAISE EXCEPTION 'CUSTOMER_NOT_FOUND_OR_INACTIVE'; END IF;
  IF nullif(trim(p_body),'') IS NULL OR length(trim(p_body))>10000 THEN RAISE EXCEPTION 'INVALID_MESSAGE'; END IF;
  IF nullif(trim(p_idempotency_key),'') IS NULL THEN RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(v_customer_id::text||':SEND_SUPPORT_MESSAGE:'||trim(p_idempotency_key),0));
  SELECT operation_id INTO v_operation FROM public.operation_idempotency WHERE actor_id=v_customer_id
    AND operation_type='SEND_SUPPORT_MESSAGE' AND idempotency_key=trim(p_idempotency_key);
  IF v_operation IS NOT NULL THEN RETURN jsonb_build_object('operation_id',v_operation,'idempotent',true); END IF;
  PERFORM 1 FROM public.support_conversation WHERE id=p_conversation_id AND customer_id=v_customer_id
    AND status='OPEN' AND request_status='APPROVED' FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'SUPPORT_CONVERSATION_NOT_APPROVED_OPEN_OR_OWNED'; END IF;
  INSERT INTO public.support_message(conversation_id,sender_type,sender_id,body)
    VALUES(p_conversation_id,'CUSTOMER',v_customer_id,trim(p_body)) RETURNING id INTO v_message;
  UPDATE public.support_conversation SET updated_at=now() WHERE id=p_conversation_id;
  INSERT INTO public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,idempotency_key,result_reference,completed_at)
    VALUES(public.generate_public_code('OP'),'SEND_SUPPORT_MESSAGE','CUSTOMER',v_customer_id,'support_conversation',p_conversation_id,'COMPLETED',trim(p_idempotency_key),v_message::text,now()) RETURNING id INTO v_operation;
  INSERT INTO public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id)
    VALUES(v_customer_id,'SEND_SUPPORT_MESSAGE',trim(p_idempotency_key),v_operation);
  INSERT INTO public.audit_log(actor_type,actor_id,operation_id,entity_type,entity_id,action,after_state)
    VALUES('CUSTOMER',v_customer_id,v_operation,'support_conversation',p_conversation_id,'SEND_SUPPORT_MESSAGE',jsonb_build_object('message_id',v_message));
  RETURN jsonb_build_object('message_id',v_message,'operation_id',v_operation);
END;
$$;

CREATE OR REPLACE FUNCTION public.close_support_conversation(p_conversation_id uuid,p_idempotency_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_customer_id uuid; v_conversation public.support_conversation; v_operation uuid; v_before jsonb;
BEGIN
  v_customer_id:=public.current_customer_profile_id();
  IF v_customer_id IS NULL THEN RAISE EXCEPTION 'CUSTOMER_NOT_FOUND_OR_INACTIVE'; END IF;
  IF nullif(trim(p_idempotency_key),'') IS NULL THEN RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(v_customer_id::text||':CLOSE_SUPPORT_CONVERSATION:'||trim(p_idempotency_key),0));
  SELECT operation_id INTO v_operation FROM public.operation_idempotency WHERE actor_id=v_customer_id
    AND operation_type='CLOSE_SUPPORT_CONVERSATION' AND idempotency_key=trim(p_idempotency_key);
  IF v_operation IS NOT NULL THEN RETURN jsonb_build_object('operation_id',v_operation,'conversation_id',
    (SELECT entity_id FROM public.operation WHERE id=v_operation),'idempotent',true); END IF;
  SELECT * INTO v_conversation FROM public.support_conversation WHERE id=p_conversation_id AND customer_id=v_customer_id FOR UPDATE;
  IF v_conversation.id IS NULL THEN RAISE EXCEPTION 'SUPPORT_CONVERSATION_NOT_FOUND_OR_NOT_OWNED'; END IF;
  IF v_conversation.request_status<>'APPROVED' THEN RAISE EXCEPTION 'SUPPORT_REQUEST_NOT_APPROVED'; END IF;
  IF v_conversation.status='CLOSED' THEN RAISE EXCEPTION 'SUPPORT_CONVERSATION_ALREADY_CLOSED'; END IF;
  v_before:=to_jsonb(v_conversation);
  UPDATE public.support_conversation SET status='CLOSED',closed_at=now(),closed_by=v_customer_id,updated_at=now()
    WHERE id=p_conversation_id RETURNING * INTO v_conversation;
  INSERT INTO public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,idempotency_key,result_reference,completed_at)
    VALUES(public.generate_public_code('OP'),'CLOSE_SUPPORT_CONVERSATION','CUSTOMER',v_customer_id,'support_conversation',p_conversation_id,'COMPLETED',trim(p_idempotency_key),p_conversation_id::text,now()) RETURNING id INTO v_operation;
  INSERT INTO public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id)
    VALUES(v_customer_id,'CLOSE_SUPPORT_CONVERSATION',trim(p_idempotency_key),v_operation);
  INSERT INTO public.audit_log(actor_type,actor_id,operation_id,entity_type,entity_id,action,before_state,after_state)
    VALUES('CUSTOMER',v_customer_id,v_operation,'support_conversation',p_conversation_id,'CLOSE_SUPPORT_CONVERSATION',v_before,to_jsonb(v_conversation));
  RETURN jsonb_build_object('conversation_id',p_conversation_id,'status','CLOSED','operation_id',v_operation);
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_approve_support_request(p_conversation_id uuid,p_reason text,p_idempotency_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_admin uuid; v_conversation public.support_conversation; v_operation uuid; v_event uuid; v_before jsonb;
BEGIN
  v_admin:=public.current_admin_id();
  IF v_admin IS NULL OR NOT public.has_admin_permission('support.write') THEN RAISE EXCEPTION 'FORBIDDEN'; END IF;
  IF nullif(trim(p_idempotency_key),'') IS NULL THEN RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(v_admin::text||':APPROVE_SUPPORT_REQUEST:'||trim(p_idempotency_key),0));
  SELECT operation_id INTO v_operation FROM public.operation_idempotency WHERE actor_id=v_admin
    AND operation_type='APPROVE_SUPPORT_REQUEST' AND idempotency_key=trim(p_idempotency_key);
  IF v_operation IS NOT NULL THEN RETURN jsonb_build_object('conversation_id',(SELECT entity_id FROM public.operation WHERE id=v_operation),'idempotent',true); END IF;
  SELECT * INTO v_conversation FROM public.support_conversation WHERE id=p_conversation_id FOR UPDATE;
  IF v_conversation.id IS NULL THEN RAISE EXCEPTION 'CONVERSATION_NOT_FOUND'; END IF;
  IF v_conversation.request_status<>'PENDING' THEN RAISE EXCEPTION 'SUPPORT_REQUEST_NOT_PENDING'; END IF;
  v_before:=to_jsonb(v_conversation);
  UPDATE public.support_conversation SET request_status='APPROVED',approved_at=now(),approved_by=v_admin,status='OPEN',updated_at=now()
    WHERE id=p_conversation_id RETURNING * INTO v_conversation;
  INSERT INTO public.operation(operation_key,operation_type,actor_type,actor_id,entity_type,entity_id,status,idempotency_key,result_reference,completed_at)
    VALUES(public.generate_public_code('OP'),'APPROVE_SUPPORT_REQUEST','ADMIN',v_admin,'support_conversation',p_conversation_id,'COMPLETED',trim(p_idempotency_key),p_conversation_id::text,now()) RETURNING id INTO v_operation;
  INSERT INTO public.operation_idempotency(actor_id,operation_type,idempotency_key,operation_id)
    VALUES(v_admin,'APPROVE_SUPPORT_REQUEST',trim(p_idempotency_key),v_operation);
  INSERT INTO public.audit_log(actor_type,actor_id,operation_id,entity_type,entity_id,action,permission_code,before_state,after_state,reason)
    VALUES('ADMIN',v_admin,v_operation,'support_conversation',p_conversation_id,'APPROVE_SUPPORT_REQUEST','support.write',v_before,to_jsonb(v_conversation),nullif(trim(p_reason),''));
  INSERT INTO public.notification_event(event_type,source_type,source_id)
    VALUES('SUPPORT_REQUEST_APPROVED','support_conversation',p_conversation_id) RETURNING id INTO v_event;
  INSERT INTO public.customer_notification(customer_id,event_id,notification_type,title,body,reference_type,reference_id)
    VALUES(v_conversation.customer_id,v_event,'SUPPORT','تمت الموافقة على طلب الدعم','أصبحت محادثة الدعم متاحة الآن.','support_conversation',p_conversation_id);
  RETURN jsonb_build_object('conversation_id',p_conversation_id,'request_status','APPROVED','operation_id',v_operation);
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_close_support_conversation(p_conversation_id uuid,p_reason text,p_idempotency_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_admin uuid; v_conversation public.support_conversation; v_before jsonb; v_operation uuid;
BEGIN
  v_admin:=public.current_admin_id();
  IF v_admin IS NULL OR NOT public.has_admin_permission('support.write') THEN RAISE EXCEPTION 'FORBIDDEN'; END IF;
  IF nullif(trim(p_idempotency_key),'') IS NULL THEN RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(v_admin::text||':CLOSE_SUPPORT_CONVERSATION:'||trim(p_idempotency_key),0));
  SELECT operation_id INTO v_operation FROM public.operation_idempotency WHERE actor_id=v_admin
    AND operation_type='CLOSE_SUPPORT_CONVERSATION' AND idempotency_key=trim(p_idempotency_key);
  IF v_operation IS NOT NULL THEN RETURN jsonb_build_object('conversation_id',(SELECT entity_id FROM public.operation WHERE id=v_operation),'idempotent',true); END IF;
  SELECT * INTO v_conversation FROM public.support_conversation WHERE id=p_conversation_id FOR UPDATE;
  IF v_conversation.id IS NULL THEN RAISE EXCEPTION 'CONVERSATION_NOT_FOUND'; END IF;
  IF v_conversation.request_status<>'APPROVED' THEN RAISE EXCEPTION 'SUPPORT_REQUEST_NOT_APPROVED'; END IF;
  IF v_conversation.status='CLOSED' THEN RAISE EXCEPTION 'CONVERSATION_ALREADY_CLOSED'; END IF;
  v_before:=to_jsonb(v_conversation);
  UPDATE public.support_conversation SET status='CLOSED',closed_at=now(),closed_by=v_admin,updated_at=now()
    WHERE id=p_conversation_id RETURNING * INTO v_conversation;
  v_operation:=public.admin_record_change('CLOSE_SUPPORT_CONVERSATION','support_conversation',p_conversation_id,'support.write',v_before,to_jsonb(v_conversation),p_reason,p_idempotency_key);
  RETURN to_jsonb(v_conversation)||jsonb_build_object('operation_id',v_operation);
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_send_support_reply(p_conversation_id uuid,p_body text,p_idempotency_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_conversation public.support_conversation; v_message public.support_message; v_admin uuid; v_operation uuid;
BEGIN
  v_admin:=public.current_admin_id();
  IF v_admin IS NULL OR NOT public.has_admin_permission('support.write') THEN RAISE EXCEPTION 'FORBIDDEN'; END IF;
  IF nullif(trim(p_body),'') IS NULL OR length(trim(p_body))>10000 THEN RAISE EXCEPTION 'MESSAGE_REQUIRED'; END IF;
  IF nullif(trim(p_idempotency_key),'') IS NULL THEN RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED'; END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(v_admin::text||':SEND_SUPPORT_REPLY:'||trim(p_idempotency_key),0));
  SELECT operation_id INTO v_operation FROM public.operation_idempotency WHERE actor_id=v_admin
    AND operation_type='SEND_SUPPORT_REPLY' AND idempotency_key=trim(p_idempotency_key);
  IF v_operation IS NOT NULL THEN RETURN jsonb_build_object('operation_id',v_operation,'idempotent',true); END IF;
  SELECT * INTO v_conversation FROM public.support_conversation WHERE id=p_conversation_id AND status='OPEN' AND request_status='APPROVED' FOR UPDATE;
  IF v_conversation.id IS NULL THEN RAISE EXCEPTION 'SUPPORT_CONVERSATION_NOT_APPROVED_OPEN'; END IF;
  INSERT INTO public.support_message(conversation_id,sender_type,sender_id,body)
    VALUES(p_conversation_id,'ADMIN',v_admin,trim(p_body)) RETURNING * INTO v_message;
  UPDATE public.support_conversation SET updated_at=now() WHERE id=p_conversation_id;
  v_operation:=public.admin_record_change('SEND_SUPPORT_REPLY','support_conversation',p_conversation_id,'support.write',NULL,to_jsonb(v_message),NULL,p_idempotency_key);
  RETURN to_jsonb(v_message)||jsonb_build_object('operation_id',v_operation);
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_save_telecom_company(
  p_id uuid,p_code text,p_name text,p_status text,p_extension_warning_days integer,p_idempotency_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_admin uuid; v_row public.telecom_company; v_before jsonb; v_operation uuid; v_action text;
BEGIN
  v_admin:=public.current_admin_id();
  IF v_admin IS NULL OR NOT public.has_admin_permission('providers.write') THEN RAISE EXCEPTION 'FORBIDDEN'; END IF;
  IF nullif(trim(p_code),'') IS NULL OR nullif(trim(p_name),'') IS NULL OR upper(coalesce(p_status,'')) NOT IN ('ACTIVE','INACTIVE')
    OR p_extension_warning_days IS NULL OR p_extension_warning_days<0 OR p_extension_warning_days>36500 THEN RAISE EXCEPTION 'INVALID_COMPANY_FIELDS'; END IF;
  IF nullif(trim(p_idempotency_key),'') IS NULL THEN RAISE EXCEPTION 'IDEMPOTENCY_KEY_REQUIRED'; END IF;
  v_action:=CASE WHEN p_id IS NULL THEN 'CREATE_TELECOM_COMPANY' ELSE 'UPDATE_TELECOM_COMPANY' END;
  PERFORM pg_advisory_xact_lock(hashtextextended(v_admin::text||':SAVE_TELECOM_COMPANY:'||trim(p_idempotency_key),0));
  SELECT operation_id INTO v_operation FROM public.operation_idempotency WHERE actor_id=v_admin
    AND operation_type=v_action AND idempotency_key=trim(p_idempotency_key);
  IF v_operation IS NOT NULL THEN RETURN jsonb_build_object('company_id',(SELECT entity_id FROM public.operation WHERE id=v_operation),'idempotent',true); END IF;
  IF p_id IS NULL THEN
    INSERT INTO public.telecom_company(code,name,status,extension_warning_days)
      VALUES(trim(p_code),trim(p_name),upper(p_status)::public.generic_status,p_extension_warning_days) RETURNING * INTO v_row;
  ELSE
    SELECT * INTO v_row FROM public.telecom_company WHERE id=p_id FOR UPDATE;
    IF v_row.id IS NULL THEN RAISE EXCEPTION 'COMPANY_NOT_FOUND'; END IF;
    v_before:=to_jsonb(v_row);
    UPDATE public.telecom_company SET code=trim(p_code),name=trim(p_name),status=upper(p_status)::public.generic_status,
      extension_warning_days=p_extension_warning_days,updated_at=now() WHERE id=p_id RETURNING * INTO v_row;
  END IF;
  v_operation:=public.admin_record_change(v_action,'telecom_company',v_row.id,'providers.write',v_before,to_jsonb(v_row),NULL,p_idempotency_key);
  RETURN to_jsonb(v_row)||jsonb_build_object('operation_id',v_operation);
END;
$$;

-- Existing screen RPC remains the canonical source; this overload adds server-side filtering and paging.
CREATE OR REPLACE FUNCTION public.get_customer_screen_data(
  p_screen_id text,p_query text,p_page_size integer,p_page_offset integer)
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $$
DECLARE v_payload jsonb; v_key text; v_value jsonb; v_result jsonb:='{}'::jsonb; v_main_key text;
  v_limit integer; v_offset integer; v_total integer:=0; v_has_more boolean:=false; v_array jsonb;
  v_page jsonb:='[]'::jsonb; v_filtered jsonb:='[]'::jsonb;
BEGIN
  IF auth.uid() IS NULL OR public.current_customer_profile_id() IS NULL THEN RAISE EXCEPTION 'CUSTOMER_NOT_FOUND_OR_INACTIVE'; END IF;
  IF p_page_size IS NULL OR p_page_offset IS NULL OR p_page_offset<0 THEN RAISE EXCEPTION 'INVALID_PAGE'; END IF;
  v_limit:=least(greatest(p_page_size,1),100); v_offset:=p_page_offset;
  v_payload:=public.get_customer_screen_data(p_screen_id,p_query);
  FOR v_key,v_value IN SELECT key,value FROM jsonb_each(v_payload) LOOP
    IF jsonb_typeof(v_value)='array' AND v_key IN ('numbers','candidates','protections','purchases','ledger','operations','notifications','threads','messages','admin_messages') THEN
      IF p_screen_id='C16' AND nullif(trim(p_query),'') IS NOT NULL THEN
        SELECT coalesce(jsonb_agg(item),'[]'::jsonb),count(*) INTO v_filtered,v_total
        FROM (SELECT e.item FROM jsonb_array_elements(v_value) AS e(item)
          WHERE position(lower(trim(p_query)) in lower(e.item::text))>0
          ORDER BY e.item->>'id') s;
      ELSE
        v_filtered:=v_value;
        v_total:=jsonb_array_length(v_filtered);
      END IF;
      v_has_more:=v_has_more OR v_total>v_offset+v_limit;
      SELECT coalesce(jsonb_agg(item ORDER BY ordinal),'[]'::jsonb) INTO v_page
      FROM (SELECT item,ordinal FROM jsonb_array_elements(v_filtered) WITH ORDINALITY a(item,ordinal)
        WHERE ordinal>v_offset AND ordinal<=v_offset+v_limit) p;
      v_result:=v_result||jsonb_build_object(v_key,v_page);
    ELSE
      v_result:=v_result||jsonb_build_object(v_key,v_value);
    END IF;
  END LOOP;
  v_main_key:=CASE p_screen_id
    WHEN 'C04' THEN 'operations' WHEN 'C05' THEN 'numbers' WHEN 'C06' THEN 'purchases'
    WHEN 'C07' THEN 'ledger' WHEN 'C08' THEN 'numbers' WHEN 'C09' THEN 'protections'
    WHEN 'C10' THEN 'protections' WHEN 'C11' THEN 'candidates' WHEN 'C12' THEN 'protections'
    WHEN 'C13' THEN 'protections' WHEN 'C14' THEN 'notifications' WHEN 'C15' THEN 'threads'
    WHEN 'C16' THEN NULL WHEN 'C17' THEN 'operations' WHEN 'C18' THEN 'profile' ELSE NULL END;
  IF v_main_key IS NOT NULL THEN
    SELECT count(*) INTO v_total FROM jsonb_array_elements(coalesce(v_payload->v_main_key,'[]'::jsonb)) AS e(item)
      WHERE nullif(trim(p_query),'') IS NULL OR position(lower(trim(p_query)) in lower(e.item::text))>0;
    v_has_more:=v_total>v_offset+v_limit;
  END IF;
  SELECT coalesce(jsonb_agg(to_jsonb(cp)-'auth_account_id'),'[]'::jsonb) INTO v_page
    FROM public.customer_profile cp WHERE cp.id=public.current_customer_profile_id();
  IF jsonb_array_length(v_page)>0 THEN v_result:=jsonb_set(v_result,'{profile}',v_page,true); END IF;
  IF jsonb_typeof(v_result->'threads')='array' THEN
    SELECT coalesce(jsonb_agg(t.item||jsonb_build_object('request_status',sc.request_status,
      'requested_at',sc.requested_at,'approved_at',sc.approved_at) ORDER BY t.ordinal),'[]'::jsonb) INTO v_page
    FROM jsonb_array_elements(v_result->'threads') WITH ORDINALITY AS t(item,ordinal)
    JOIN public.support_conversation sc ON sc.id=(t.item->>'id')::uuid;
    v_result:=jsonb_set(v_result,'{threads}',v_page,true);
  END IF;
  RETURN v_result||jsonb_build_object('page_info',jsonb_build_object('page_size',v_limit,'offset',v_offset,
    'has_more',v_has_more,'total_returned',least(v_total,greatest(0,v_limit))));
END;
$$;

-- Ensure the new response also contains the support request gate in each conversation row.
-- The old screen function is intentionally retained for internal compatibility, but is not callable from clients.

-- Remove unnecessary direct table-management privileges and the implicit PUBLIC/anon EXECUTE grant on RPC functions.
REVOKE INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER ON ALL TABLES IN SCHEMA public FROM anon,authenticated;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC,anon,authenticated;

GRANT EXECUTE ON FUNCTION public.get_public_content(text) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION public.get_active_consent_versions() TO anon,authenticated;
GRANT EXECUTE ON FUNCTION public.verify_customer_recovery_id(text,text,text) TO anon,authenticated;
GRANT EXECUTE ON FUNCTION public.get_customer_screen_data(text,text,integer,integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_customer_profile(text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.update_customer_profile(text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.add_customer_number(text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.update_customer_number(uuid,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.delete_customer_number(uuid,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.submit_points_purchase(uuid,uuid,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.activate_protection(uuid,uuid,integer,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.extend_protection(uuid,uuid,integer,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.renew_protection(uuid,uuid,integer,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.mark_notification_read(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.mark_admin_message_read(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_support_conversation(text,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.send_support_message(uuid,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.close_support_conversation(uuid,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_account_info() TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_cancel_periodic_task(uuid,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_close_support_conversation(uuid,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_create_expense(uuid,numeric,text,text,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_execute_periodic_task(uuid,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_grant_points(uuid,bigint,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_has_permission(text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_reschedule_periodic_task(uuid,timestamptz,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_save_payment_method(uuid,text,text,text,text,text,integer,boolean,boolean,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_save_points_package(uuid,text,text,bigint,numeric,text,integer,boolean,boolean,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_save_provider_tariff(uuid,uuid,public.tariff_mode,integer,bigint,numeric,text,timestamptz,timestamptz,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_save_task_configuration(uuid,integer,numeric,text,integer,boolean,boolean,integer,boolean,boolean,timestamptz,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_save_telecom_company(uuid,text,text,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_save_telecom_company(uuid,text,text,text,integer,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_save_telecom_prefix(uuid,uuid,text,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_send_notification(text,uuid,text,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_send_support_reply(uuid,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_set_customer_number_status(uuid,text,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_update_customer_profile(uuid,text,text,text,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.approve_points_purchase(uuid,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.reject_points_purchase(uuid,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.is_admin() TO authenticated;
GRANT EXECUTE ON FUNCTION public.has_admin_permission(text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.has_admin_permission(text) TO anon;
GRANT EXECUTE ON FUNCTION public.current_customer_profile_id() TO authenticated;
GRANT EXECUTE ON FUNCTION public.admin_approve_support_request(uuid,text,text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.complete_customer_password_recovery() TO authenticated;
GRANT EXECUTE ON FUNCTION public.generate_scheduled_tasks() TO service_role;

-- pg_cron is optional at the platform level; use it when available, without aborting the data-safe migration otherwise.
DO $$
BEGIN
  BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname='pg_cron') THEN
      EXECUTE 'CREATE EXTENSION pg_cron';
    END IF;
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pg_cron could not be installed in this project: %',SQLERRM;
  END;
  IF to_regclass('cron.job') IS NOT NULL THEN
    BEGIN
      IF NOT EXISTS (SELECT 1 FROM cron.job WHERE jobname='aman-task-generator') THEN
        PERFORM cron.schedule('aman-task-generator','0 * * * *','SELECT public.generate_scheduled_tasks();');
      END IF;
    EXCEPTION WHEN OTHERS THEN
      RAISE NOTICE 'AMAN task generator was deployed but pg_cron scheduling failed: %',SQLERRM;
    END;
  END IF;
END;
$$;
