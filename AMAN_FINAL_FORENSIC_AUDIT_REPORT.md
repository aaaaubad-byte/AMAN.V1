# AMAN | أمان
# FINAL FORENSIC AUDIT REPORT
## Reference ↔ Customer ↔ Admin ↔ Database

**Audit source:** `AMAN.V1-main.zip`  
**Repository state represented:** `main`  
**Audit mode:** Static / source-level / cross-system forensic audit  
**Live Supabase:** NOT CONNECTED  
**SQL execution:** NOT PERFORMED  
**Application source modifications during audit:** NONE

---

# 1. Executive Verdict

## FINAL STATUS

**NOT READY — DO NOT EXECUTE THE CURRENT SQL ON THE PRODUCTION DATABASE YET.**

The repository contains real Android source for both applications and substantial SQL, but the four sources do **not** currently form a complete executable system.

The most important finding is that the gaps are not limited to UI completeness. There are several **P0/P1 database and business-flow blockers** that can prevent the core customer lifecycle from working:

1. Auth signup does not create the required `profiles` record.
2. The approved points-purchase RPC is an invoker function but authenticated users have no `INSERT` grant on `points_purchase_requests`; therefore the core C04 purchase submission contract is not executable as currently defined.
3. First approved points purchase does not create the required `subscribers` record, while `activate_protection` requires an active subscriber.
4. `activate_protection` does not verify that the authenticated customer actually has a `customer_numbers` relationship to the requested phone. Because the RPC is `SECURITY DEFINER`, this is a real authorization/business-rule gap.
5. `extend_protection` is not safely idempotent: duplicate retry with the same operation key can deduct points and extend protection again because the operation insert uses `ON CONFLICT DO NOTHING` after the mutation.
6. Expiration/re-activation lifecycle rules from the reference are not implemented in SQL.
7. Post-expiry task creation settings and visibility-window settings are stored but not honored by the task-plan rebuild logic.
8. Admin application has the 15 route identifiers, but most administrative screens are generic read-only record lists. Required forms, tabs, CRUD actions, search, reporting, notification composition, task rescheduling/cancellation, and account-management flows are missing.
9. Admin A11 (`إعدادات المهام`) is defined but has no navigation entry, so it is effectively unreachable.
10. Admin A12 (`البحث`) has no search input in the UI and the repository search function ignores the supplied query.
11. Admin A13 (`الإشعارات`) cannot create/send administrative notifications despite the reference defining it as an authoring/sending tool.
12. Admin A14 (`التقارير`) is currently only an operations list, not the specified reporting system.
13. Customer C06 and C10 are intentionally disabled because their backend write contracts are missing.
14. The customer primary red token does not match the reference visual token: customer uses `#D9363E`, while the reference specifies `#FC0B39` / `#F40430` family.
15. The repository does not contain the six approved visual reference images, so pixel-level visual verification cannot be completed from the repository alone.

---

# 2. Severity Model

- **P0 — BLOCKER:** security, data corruption, or core lifecycle cannot safely operate.
- **P1 — CRITICAL:** major business capability or core end-to-end flow is incomplete/broken.
- **P2 — HIGH:** significant functional/contract/traceability problem.
- **P3 — MEDIUM:** important UX, completeness, maintainability, or visual problem.
- **P4 — LOW:** polish or non-blocking improvement.

---

# 3. Source Inventory

## Reference

`AMAN_EXECUTION_REFERENCE_REPOSITORY_FINAL_CLEAN.md`

- 7,366 lines.
- Defines 15 customer screens.
- Defines 15 admin screens.
- Defines UI element contracts.
- Defines business rules.
- Defines database model.
- Defines RPC contracts.
- Defines RLS.
- Defines idempotency.
- Defines task scheduling.
- Defines expiration/renewal.
- Defines functional tests T01–T12.
- Defines visual tokens and reference-image dimensions.

## Customer

`customer/`

Main implementation files:

- `CustomerScreens.kt`
- `CustomerViewModel.kt`
- `CustomerRepository.kt`
- `CustomerContracts.kt`
- `SupabaseGateway.kt`
- `CustomerCache.kt`
- `CustomerOutbox.kt`
- `CustomerRefreshWorker.kt`

15 route IDs are defined.

## Admin

`admin/`

Main implementation files:

- `AdminApp.kt`
- `AdminViewModel.kt`
- `AdminRepository.kt`
- `AdminContracts.kt`
- `SupabaseGateway.kt`
- `LocalSnapshotCache.kt`
- `AdminRefreshWorker.kt`

15 route IDs are defined.

## Database

Current database source consists of:

- `database/migrations/001_initial_schema.sql`
- `database/migrations/002_admin_contracts.sql`

No single `database/AMAN_DATABASE_FINAL.sql` exists in this checkout.

---

# 4. Customer Screen Audit

## C01 — الرئيسية

**Status: PARTIAL**

Present:
- AMAN branding.
- Customer name.
- Points balance.
- 3×3 section grid.
- Latest operations.
- Notification entry.
- Correct 9-grid logical order.
- Bottom navigation.

Issues:
- The notification entry is implemented as an extra outlined button rather than the reference's specified bell/header interaction.
- Latest operations are rendered as generic cards rather than a clearly defined operation summary structure.
- No stable `element_id` contract exists in code for the screen elements.

**Finding:** C-UI-001 — P2.

---

## C02 — الأرقام المفعلة

**Status: PARTIAL**

Present:
- Number.
- Protection status.
- Start/end.
- Duration.
- Snapshot tariff.
- Total points.
- Remaining duration.
- Copy/details.

Mismatch:
- Each protection row displays a direct button:
  `الانتقال إلى تمديد الحماية`
- Reference explicitly states extension is not the main path from C02; extension is C08.

**Finding:** C-UI-002 — P2 — navigation/UX mismatch.

---

## C03 — الأرقام غير المفعلة

**Status: PARTIAL**

Present:
- Number.
- Provider.
- Added date.
- State.
- Copy.
- Correct warning that another customer may have protection.

Mismatch:
- Each row has a direct `الانتقال إلى تفعيل رقم` action.
- Reference describes activation as an independent C07 route and lists C03 actions as copy/manage-record according to permissions.

**Finding:** C-UI-003 — P2.

---

## C04 — إضافة نقاط

**Status: PARTIAL / BACKEND BLOCKED**

UI present:
- Package selection.
- Payment method.
- Instructions.
- Payment data.
- Transfer reference.
- Confirmation.
- Pending concept.
- Local encrypted outbox.

Business separation is correct:
- purchase points ≠ protection activation.
- no points are locally credited before approval.

Critical backend problem:
- `submit_points_purchase` is `SECURITY INVOKER`.
- `authenticated` receives EXECUTE on the RPC.
- `authenticated` does NOT receive INSERT on `points_purchase_requests`.
- Therefore the RPC's INSERT cannot execute under the current privilege contract.

**Finding:** C-DB-002 — P0/P1.

Additional issue:
- The RPC accepts an idempotency key but does not verify that an existing key belongs to the current authenticated user before returning the conflicting row.

**Finding:** C-DB-003 — P1.

---

## C05 — العمليات

**Status: PARTIAL**

Present:
- operations.
- point ledger.
- purchase requests.
- details.

Issue:
- generic record rendering rather than a complete operation taxonomy/detail contract.
- reporting and operation semantics are not fully bound to reference element IDs.

**Finding:** C-UI-004 — P2.

---

## C06 — إضافة رقم

**Status: NOT FUNCTIONAL**

Present:
- phone input.
- longest-prefix detection.
- duplicate detection within current customer's list.
- existing numbers.

Missing:
- save.
- edit.
- delete/archive.
- backend number normalization/provisioning contract.
- provider persistence.

The button is intentionally disabled.

**Finding:** C-DB-004 — P1.

Additional database problem:
- `resolve_provider()` exists.
- `activate_protection()` does not call it.
- activation uses `phone_numbers.provider_id` directly.
- If provider_id is not already persisted, activation cannot resolve the provider as required by the reference.

**Finding:** C-DB-005 — P1.

---

## C07 — تفعيل رقم

**Status: UI PRESENT / CORE BACKEND UNSAFE**

Present:
- number selection.
- duration.
- cost estimate.
- balance.
- insufficient-balance handling.
- confirmation.
- online-only mutation.

Critical database gap:
`activate_protection()` does not verify that the selected phone belongs to the current customer's `customer_numbers` relationship.

Because the function is `SECURITY DEFINER`, it must perform this authorization check itself.

**Finding:** C-DB-006 — P0.

Additional:
- first approved points purchase does not create a subscriber.
- activation requires an active subscriber.

This breaks the intended first-purchase → subscriber → activation lifecycle.

**Finding:** C-DB-007 — P0/P1.

---

## C08 — تمديد رقم

**Status: PARTIAL / RETRY SAFETY BLOCKED**

Present:
- active protection selection.
- current expiry.
- extension days.
- cost.
- balance.
- expected new expiry.
- confirmation.
- online-only mutation.

Critical:
`extend_protection()` performs the financial/protection mutation before the operation idempotency conflict is encountered.

The operation insert uses:

`ON CONFLICT (idempotency_key) DO NOTHING`

after:
- balance deduction.
- expiry extension.
- duration update.

Therefore a repeated request with the same operation key can commit another extension and another points deduction.

**Finding:** C-DB-008 — P0.

Additional lifecycle gap:
- only `status='active'` protections can be extended.
- reference requires renewal/reactivation after expiry under configured rules.

**Finding:** C-BIZ-001 — P1.

---

## C09 — تنبيهات الإدارة

**Status: PARTIAL**

Read/list behavior exists through `notifications.notification_type = admin_alert`.

Mark-read exists.

Backend support for admin-generated notifications is incomplete from the application side because the admin authoring UI is absent.

**Finding:** C-CROSS-001 — P2.

---

## C10 — تواصل مع الإدارة

**Status: NOT FUNCTIONAL**

Read threads/messages exist.

Send button is disabled.

No customer write grant/RPC exists.

**Finding:** C-DB-009 — P1.

---

## C11 — إشعارات النظام

**Status: PARTIAL**

Read/list/mark-read exist.

Issue:
The reference calls this `system_notifications`, while the SQL/application use a generic `notifications` table containing both system and admin alerts.

This is a contract naming/domain mismatch, not merely a label difference.

**Finding:** C-DB-010 — P2.

---

## C12 — التقارير

**Status: PARTIAL**

Present:
- categories.
- periods.
- point calculations.
- operations.
- purchases.
- protection data.

Missing/limited:
- authoritative report/query contracts.
- complete reporting aggregation.
- explicit export behavior.
- report-specific backend contracts.

**Finding:** C-UI-005 — P2.

---

## C13 — الحساب

**Status: PARTIAL / PROVISIONING BLOCKED**

Present:
- profile.
- username.
- email.
- phone.
- dates.
- account status.
- subscriber status.
- balance.
- logout.

Critical:
No SQL trigger/RPC provisions `profiles` after Supabase Auth signup.

No subscriber creation mechanism exists after the first approved points purchase.

**Finding:** C-DB-011 — P0.

---

## C14 — البحث

**Status: PARTIAL**

Present:
- search UI.
- local filtering.
- user-scoped sources.

Missing:
- robust server-side search contract.
- complete source coverage if additional customer domains are introduced.

**Finding:** C-UI-006 — P2.

---

## C15 — عن أمان

**Status: PARTIAL**

Present:
- AMAN branding.
- slogan.
- description.
- version.

Not present:
- approved organization information.
- approved official terms/privacy/contact destinations.

The reference itself marks these as dependent on later approval, so this is not a blocker.

**Finding:** C-DEC-001 — P3 / Decision Required if those links are now desired.

---

# 5. Admin Screen Audit

## A01 — الرئيسية

**Status: PARTIAL**

Present:
- 3×3 navigation.
- latest mixed records.
- refresh.
- offline snapshot.

Issues:
- latest operations are not rendered according to the four distinct reference attention categories.
- dashboard cards have no actual counts/statistics, which may be acceptable because the reference says not to make it a giant dashboard, but the intended contextual summaries are not fully implemented.

**Finding:** A-UI-001 — P2.

---

## A02 — المشتركون

**Status: PARTIAL**

Present:
- list.
- profile enrichment.
- points.
- numbers count.
- protection count.

Missing:
- top subscriber profile/action panel at the reference level.
- tabs:
  - profile
  - numbers
  - points
  - history
- edit.
- status management.
- administrative actions.

**Finding:** A-UI-002 — P1.

---

## A03 — المستخدمون

**Status: PARTIAL**

Present:
- list.
- profile data.
- balance.
- subscriber state.

Missing:
- edit.
- account-state management UI.

Backend RPC exists for profile update, but the Android UI does not expose it.

**Finding:** A-UI-003 — P1.

---

## A04 — الأرقام المضافة

**Status: PARTIAL**

Present:
- list.
- phone/provider enrichment.
- copy.

Missing:
- edit.
- delete/archive UI.
- full action contract.

Backend only has a limited status-setting RPC.

**Finding:** A-UI-004 — P1.

---

## A05 — الأرقام المفعلة

**Status: PARTIAL**

Present:
- protections list.
- phone.
- customer.
- provider.
- tariff.
- plan interval.
- next task.

Missing:
- complete upper protection profile.
- task-plan detail.
- complete allowed record actions.
- complete historical schedule presentation.

**Finding:** A-UI-005 — P2.

---

## A06 — طلبات الشراء

**Status: PARTIAL**

Present:
- list.
- details.
- approve.
- reject.
- rejection reason.
- confirmation.

Backend:
- approve RPC.
- reject RPC.

Issue:
- subscriber creation after first approval is missing in the RPC.
- operation idempotency is incomplete.

**Finding:** A-DB-001 — P1.

---

## A07 — مهام السداد

**Status: PARTIAL / CRITICAL**

Present:
- task list contract.
- execute action.
- external reference field.
- confirmation.

Missing:
- reschedule UI.
- cancel UI.
- cancellation reason UI.
- complete task-plan control flow.

Database:
- reschedule/cancel RPCs exist.
- execute RPC exists.

Critical SQL issue:
- task rebuild logic does not implement all reference scheduling settings.
- task history handling during rebuild/reschedule is incomplete.

**Finding:** A-BIZ-001 — P1.

---

## A08 — إدارة الشركات

**Status: READ-ONLY PARTIAL**

Present:
- provider list.
- prefix enrichment.
- tariff enrichment.

Missing:
- add.
- edit.
- save.
- enable/disable.
- safe historical management.
- actual prefix management.
- tariff management UI.

Backend has provider save RPC but no Android binding.

**Finding:** A-UI-006 — P1.

---

## A09 — إدارة الشحن / باقات النقاط

**Status: READ-ONLY PARTIAL**

Present:
- package list.

Missing:
- add.
- edit.
- save.
- show/hide.

Backend RPC exists but Android does not expose it.

**Finding:** A-UI-007 — P1.

---

## A10 — وسائل الدفع

**Status: READ-ONLY PARTIAL**

Present:
- payment method list.

Missing:
- add.
- edit.
- save.
- show/hide.

Backend RPC exists but Android does not expose it.

**Finding:** A-UI-008 — P1.

---

## A11 — إعدادات المهام

**Status: INACCESSIBLE**

`AdminSection.TASK_SETTINGS` exists.

However:
- it is not included in `adminSections`.
- it is not included in bottom navigation.
- there is no other navigation path to it.

Therefore A11 is effectively unreachable.

**Finding:** A-UI-009 — P1.

Even if manually opened internally, the current generic screen has no settings form or save UI.

**Finding:** A-UI-010 — P1.

---

## A12 — البحث

**Status: BROKEN**

UI:
- no search input exists on the screen.

Repository:
- `searchAll(query)` declares a query parameter but suppresses it as unused.
- it fetches fixed tables and merges them.
- it does not apply the entered query server-side.
- there is no UI binding to update `searchText`.

Therefore the A12 search function is not actually implemented.

**Finding:** A-UI-011 — P1.

---

## A13 — الإشعارات

**Status: READ-ONLY / MISSING CORE FUNCTION**

Reference defines A13 as an authoring/sending tool.

Current implementation:
- reads notification records.
- no target selector.
- no notification type selector.
- no title input.
- no body input.
- no preview.
- no send action.
- no sending history UI.

Database has `send_admin_notification()` but Android does not call it.

**Finding:** A-UI-012 — P1.

---

## A14 — التقارير

**Status: INCOMPLETE**

Current screen is effectively an operations list.

Missing:
- report category selector.
- period.
- filters.
- results model.
- financial reports.
- points reports.
- protection reports.
- operational reports.
- export.

**Finding:** A-UI-013 — P1.

---

## A15 — الحساب

**Status: PARTIAL**

Current:
- profile.
- logout.

Missing:
- role.
- personal/admin account data.
- security settings.
- system information.
- `admin_account_info()` binding.

**Finding:** A-UI-014 — P2.

---

# 6. Database Full Inventory

## Tables Found

### Identity
- profiles
- admin_roles
- admin_permissions
- admin_role_permissions
- admin_user_roles

### Customer/subscriber
- subscribers
- phone_numbers
- customer_numbers

### Telecom
- telecom_providers
- telecom_prefixes
- provider_tariffs

### Points
- points_packages
- points_purchase_requests
- point_balances
- point_ledger

### Protection
- protections
- protection_extensions

### Operations/tasks
- operations
- task_settings
- protection_task_plans
- payment_tasks

### Finance
- financial_ledger

### Notifications/support/audit
- notifications
- admin_notifications
- support_threads
- support_messages
- audit_logs

---

# 7. Database Object-Level Findings

## DB-001 — `profiles` provisioning missing

**Severity: P0**

The reference requires AMAN identity data to map to `auth.users`.

No trigger/function is present to create `profiles` after signup.

Customer app can successfully create Auth identity while the AMAN database still has no `profiles` row.

Impact:
- profile reads fail/empty.
- subscriber relation cannot be created.
- point balance cannot be reliably provisioned.
- admin promotion SQL cannot find the profile.
- customer lifecycle breaks immediately after signup.

Required action:
Create an authoritative profile provisioning mechanism and test it.

---

## DB-002 — `submit_points_purchase` cannot insert under current grants

**Severity: P0**

`submit_points_purchase()` is `SECURITY INVOKER`.

`authenticated` has SELECT on `points_purchase_requests`, but no INSERT grant.

The function performs an INSERT into that table.

Therefore the RPC privilege contract is inconsistent with its implementation.

Required action:
Either:
- make the RPC a carefully designed `SECURITY DEFINER` transaction with explicit authorization, or
- provide a least-privilege INSERT path with correct RLS and immutable snapshots.

Do not simply grant unrestricted INSERT without reviewing the business rules.

---

## DB-003 — Purchase idempotency key is not user-bound

**Severity: P1**

`points_purchase_requests.idempotency_key` is globally unique.

`submit_points_purchase()` resolves an existing conflict by returning the existing row without verifying the existing row belongs to `auth.uid()`.

Required action:
If an idempotency key already exists:
- verify it belongs to the same authenticated user and same logical request.
- otherwise reject with a conflict.
- never return another user's purchase request.

---

## DB-004 — First approved purchase does not create subscriber

**Severity: P0/P1**

Reference:
first approved points purchase → subscriber status.

Current `approve_points_purchase()`:
- credits balance.
- inserts point ledger.
- updates request.
- financial ledger.
- operation.
- notification.
- audit.

It does NOT create `subscribers`.

Yet `activate_protection()` requires:

`active_subscriber_required`

Result:
The main customer journey can stop after the first approved purchase.

Required action:
Implement the subscriber transition atomically according to the approved reference rule.

---

## DB-005 — Activation does not verify customer-number relationship

**Severity: P0**

`activate_protection()` accepts `p_phone_number_id`.

It checks:
- phone exists.
- active protection does not exist.
- tariff exists.
- balance.

It does NOT check that the current user has an active `customer_numbers` relationship to that phone.

Because the function is `SECURITY DEFINER`, RLS on `phone_numbers` cannot be relied upon as the authorization check inside this function.

Required action:
Verify the authenticated user owns an active relationship to the selected number before activation.

---

## DB-006 — Activation does not perform provider resolution

**Severity: P1**

Reference requires longest-prefix provider resolution.

`resolve_provider()` exists.

But `activate_protection()` reads:

`v_phone.provider_id`

instead of resolving the provider from the normalized number.

Required action:
Make the authoritative activation transaction resolve/verify provider from the approved prefix logic and ensure the phone/provider relation is consistent.

---

## DB-007 — Extension is not safely idempotent

**Severity: P0**

`extend_protection()`:
1. locks protection.
2. checks balance.
3. deducts points.
4. extends expiry.
5. inserts operation using `ON CONFLICT DO NOTHING`.
6. inserts ledger.
7. inserts extension.
8. rebuilds plan.

A repeated request with the same `operation_key` can therefore repeat the financial/protection mutation.

Required action:
Idempotency must be checked before mutation, inside the transaction, and repeated calls must return the previous authoritative result.

---

## DB-008 — Expired protection lifecycle is incomplete

**Severity: P1**

Reference requires:
- active → expired.
- post-expiry task creation rules.
- renewal/reactivation after expiry.
- next task anchor rules.

SQL contains no expiration job/trigger/function that changes active protections to expired.

No renewal/reactivation RPC exists.

Required action:
Implement the complete expiration and renewal lifecycle.

---

## DB-009 — Post-expiry task settings are stored but ignored

**Severity: P1**

`task_settings` contains:
- `allow_post_expiry_creation`
- `post_expiry_creation_limit_days`

But `rebuild_task_plan()` stops at:

`v_due < v_protection.expires_at`

and never uses the post-expiry settings.

Required action:
Implement the approved post-expiry creation window exactly as the reference defines it.

---

## DB-010 — Task visibility setting is stored but not implemented

**Severity: P2**

`visibility_days_before` exists.

The rebuild function creates future tasks directly and no application query applies the visibility window.

Required action:
Define whether visibility is enforced:
- by authorized query/RPC,
- by view,
- or by application filtering.

The reference behavior must be enforced consistently.

---

## DB-011 — Reschedule/rebuild history is incomplete

**Severity: P1**

Reference:
rescheduling must preserve historical trace and old/new schedule.

Current logic:
- cancels selected task.
- deletes future open tasks.
- creates new task.
- rebuilds future schedule.

Deleted future open task rows are not individually preserved as historical records.

Required action:
Preserve the old schedule through status/history/audit/versioning without destructive deletion of traceable schedule information.

---

## DB-012 — Financial ledger vocabulary differs from reference

Reference examples:
- `points_purchase_income`
- `task_payment`
- `expense`
- `adjustment`

Current SQL uses values such as:
- `points_purchase`
- `payment_task`

No database constraint enforces the approved entry-type vocabulary.

Required action:
Normalize the ledger contract before final integration.

---

## DB-013 — Notification domain name differs

Reference defines:

`system_notifications`

Current implementation uses:

`notifications`

for both:
- system notifications.
- admin alerts.

This can be valid only if explicitly adopted as the final naming contract.

Required action:
Either formally normalize the reference to `notifications` or rename/split the database/application contract.

Do not leave both naming models conceptually active.

---

## DB-014 — Audit schema vocabulary differs

Reference fields:
- `actor_user_id`
- `actor_role`
- `before`
- `after`

Current table:
- `actor_id`
- `action`
- `before_data`
- `after_data`

Semantics overlap, but the final SSOT is not exact.

Required action:
Choose one canonical schema and update all SQL/application references consistently.

---

## DB-015 — Admin role seed/promotion contract is incomplete

Reference promotion example expects:

`admin_roles.code = 'admin'`

Current migration:
- creates `admin_roles`.
- does not seed the admin role.
- 002 grants all permissions to a role with `code = 'super_admin'`.
- does not create `super_admin` either.

Therefore the final SQL does not establish the role needed for the stated admin promotion workflow.

Required action:
Define and seed the canonical admin role(s), permissions, and promotion procedure.

---

## DB-016 — Admin permission model and login gate are inconsistent

Admin Android checks:

`is_admin()`

`is_admin()` only checks whether an active role exists.

Fine-grained authorization is later checked by `admin_has_permission()`.

This means a role with no effective permissions can pass the initial login gate and enter an unusable interface.

Required action:
Either:
- gate entry on a required baseline permission, or
- explicitly define active-role-only login semantics.

---

## DB-017 — No canonical final SQL file

The repository has two migrations but no:

`database/AMAN_DATABASE_FINAL.sql`

The current implementation depends on applying both files in sequence.

Required action:
Before final execution, produce one canonical final SQL artifact or an explicit verified migration chain, with an exact version/hash.

---

# 8. Database Constraints / Index Review

## Correct / useful

Present:
- unique normalized phone.
- unique customer-number relation.
- partial unique active protection.
- unique plan per protection.
- unique task per plan/due date.
- unique operation idempotency key.
- nonnegative point balance.
- positive tariff/day.
- positive durations.
- purchase rejection reason check.
- task cancellation reason field.
- snapshots for historical pricing.

## Missing / needs review

1. No explicit non-overlap constraint for provider tariff effective ranges.
2. No index on `protections(phone_number_id, status)` beyond the partial unique index; the unique partial index covers the critical active case but historical queries should be reviewed.
3. No index on `profiles.phone` although the reference lists it as a basic index.
4. No explicit index on `points_purchase_requests` for all common review filters beyond user/status and status/submitted.
5. No explicit index on `protection_extensions.protection_id`.
6. No explicit index on `admin_notifications.target_type/target_id`.
7. No final performance validation against expected query shapes.

These are mostly P2/P3 unless live query plans prove otherwise.

---

# 9. RPC Audit

## `submit_points_purchase`

**FAIL**
- privilege contract inconsistent.
- idempotency ownership not verified.

## `approve_points_purchase`

**PARTIAL**
- locking exists.
- pending check exists.
- balance lock exists.
- ledger exists.
- financial ledger exists.
- operation exists.
- notification exists.
- audit exists.

Missing:
- subscriber creation.
- robust idempotency semantics.

## `reject_points_purchase`

**PARTIAL**
- permission.
- pending check.
- reason.
- status.
- operation.
- notification.
- audit.

Needs:
- same-key semantic protection.
- final transaction tests.

## `activate_protection`

**FAIL**
- customer-number ownership missing.
- provider resolution missing.
- idempotency semantics incomplete.
- expiration/renewal relation not addressed.

## `extend_protection`

**FAIL**
- duplicate retry can mutate twice.

## `rebuild_task_plan`

**FAIL / PARTIAL**
- basic anchor rebuilding exists.
- future plan generation exists.
- settings are partially read.

Missing/incorrect:
- post-expiry rules.
- visibility rule.
- complete historical schedule preservation.
- expiration/renewal semantics.

## `reschedule_payment_task`

**PARTIAL**
- authorization.
- open-state check.
- future date validation.
- plan anchor update.
- new task creation.
- audit.

Needs:
- non-destructive history.
- complete idempotency semantics.
- explicit operation/history consistency.

## `cancel_payment_task`

**PARTIAL**
- permission.
- reason required.
- open-state check.
- audit.

Needs:
- complete plan semantics verification.
- operation/history contract if required by final reference.

## `execute_payment_task`

**PARTIAL**
- external reference required.
- execution key.
- lock.
- open-state check.
- completion.
- financial ledger.
- audit.
- rebuild.

Needs:
- exact idempotency tests.
- complete operation trace if the final contract requires it.
- verify that rebuild after execution preserves the required history.

## `send_admin_notification`

**DATABASE IMPLEMENTED / UI UNBOUND**

The SQL RPC exists and creates:
- admin notification history.
- customer notifications.
- recipient count.
- audit.

The Android Admin app does not expose this contract.

---

# 10. RLS / Authorization Audit

## Positive

- Customer reads are mostly scoped to `auth.uid()`.
- Payment tasks are admin-only at RLS level.
- Audit is admin-only.
- Admin permission-aware policies exist in migration 002.
- Customer cannot directly write point ledger.
- Customer cannot directly write protections.

## Critical concerns

### RLS cannot replace authorization inside SECURITY DEFINER

`activate_protection()` must explicitly validate customer-number ownership.

### Customer support

RLS has `FOR ALL`, but table privileges only provide SELECT to authenticated.

Therefore the customer cannot actually create support records.

### Purchase request

Same privilege/RPC inconsistency exists for purchase submission.

### Admin role

Role exists conceptually but is not seeded by SQL.

---

# 11. Admin ↔ Database Contract Matrix

| Admin screen | Read | Mutation DB contract | Android mutation UI | Status |
|---|---|---|---|---|
| A01 | Partial | N/A | N/A | PARTIAL |
| A02 | Yes | admin profile/subscriber status RPC | No | INCOMPLETE |
| A03 | Yes | admin_update_profile | No | INCOMPLETE |
| A04 | Yes | customer number status only | No | INCOMPLETE |
| A05 | Partial | N/A | No | INCOMPLETE |
| A06 | Yes | approve/reject | Approve/reject | PARTIAL |
| A07 | Yes after 002 | execute/reschedule/cancel | Execute only | INCOMPLETE |
| A08 | Yes | provider save | No | INCOMPLETE |
| A09 | Yes | package save | No | INCOMPLETE |
| A10 | Yes | payment method save | No | INCOMPLETE |
| A11 | Contract exists | task settings save | Screen unreachable | BLOCKED |
| A12 | Partial | N/A | Search input absent | BROKEN |
| A13 | Yes | send_admin_notification | Send UI absent | INCOMPLETE |
| A14 | Partial | reporting/export contracts absent | Reports UI absent | INCOMPLETE |
| A15 | Own profile | admin_account_info exists | Not used | PARTIAL |

---

# 12. Customer ↔ Database Contract Matrix

| Customer screen | Main contract | Current status |
|---|---|---|
| C01 | profile/balance/operations/notifications | PARTIAL |
| C02 | protections | PARTIAL |
| C03 | customer_numbers/protections | PARTIAL |
| C04 | submit_points_purchase | BLOCKED BY GRANT |
| C05 | operations/ledger/purchases | PARTIAL |
| C06 | number provisioning | MISSING |
| C07 | activate_protection | UNSAFE / BLOCKED |
| C08 | extend_protection | UNSAFE RETRY |
| C09 | notifications/admin_alert | PARTIAL |
| C10 | support write | MISSING |
| C11 | notifications/read_at | PARTIAL |
| C12 | report queries | PARTIAL |
| C13 | profiles/subscribers/balance | PROVISIONING BLOCKED |
| C14 | user-scoped search | PARTIAL |
| C15 | static content | PARTIAL |

---

# 13. Cross-System End-to-End Audit

## Flow 1 — Signup

Current:

Auth signup
→ Auth user

Missing:

Auth user
→ profiles

**FAIL — P0**

---

## Flow 2 — First points purchase

Current intended:

Auth
→ profile
→ select package
→ submit RPC
→ pending

Actual blockers:

- profile may not exist.
- submit RPC lacks INSERT privilege.

**FAIL — P0**

---

## Flow 3 — Approve points

Current:

pending
→ approve RPC
→ balance
→ point ledger
→ financial ledger
→ operation
→ notification
→ audit

Missing:

→ subscriber creation.

**FAIL — P0/P1**

---

## Flow 4 — Activate protection

Current:

number
→ activate RPC
→ balance deduction
→ protection
→ ledger
→ operation
→ task plan
→ audit

Missing/unsafe:

- customer-number ownership verification.
- authoritative provider resolution.
- subscriber provisioning dependency.
- robust idempotency.

**FAIL — P0**

---

## Flow 5 — Extend protection

Current:

protection
→ balance deduction
→ expiry extension
→ operation
→ ledger
→ extension
→ rebuild

Problem:

duplicate retry can repeat the mutation.

**FAIL — P0**

---

## Flow 6 — Expiration

Expected:

active
→ expired
→ no unauthorized post-expiry tasks
→ configured renewal/re-activation.

Current:

No authoritative expiration transition.

**FAIL — P1**

---

## Flow 7 — Admin payment task

Expected:

open
→ verify
→ external payment
→ external reference
→ execute RPC
→ financial ledger
→ completed
→ anchor update
→ future rebuild
→ audit

Current:
mostly present at DB level, but UI lacks reschedule/cancel and full task workflow.

**PARTIAL — P1**

---

# 14. Functional Test Audit T01–T12

| Test | Result |
|---|---|
| T01 Add number | FAIL — no customer write contract |
| T02 Purchase points | FAIL — insert privilege/provisioning issue |
| T03 Approve points | FAIL — subscriber creation missing |
| T04 Reject points | PARTIAL — RPC exists, needs full tests |
| T05 Activate | FAIL — ownership/provider/subscriber/idempotency issues |
| T06 Extend | FAIL — retry can duplicate mutation |
| T07 Prevent double protection | PARTIAL — unique active protection helps, but authorization must be fixed |
| T08 Reschedule | PARTIAL — UI absent, destructive future-task handling |
| T09 Early execution | PARTIAL — anchor update exists, full history tests missing |
| T10 Execute twice | PARTIAL — locking/state helps; full idempotency test still required |
| T11 RLS | NOT VERIFIED — no live DB |
| T12 Offline | PARTIAL — architecture exists, live behavior unverified |

---

# 15. UI Element Contract Audit

Reference requires every important element to have:

- element_id
- screen_id
- parent_id
- type
- label/icon
- visual role
- data binding
- action
- visibility
- enabled condition
- permission
- validation
- confirmation
- loading
- success
- error
- navigation
- backend binding
- database effect
- audit
- accessibility

Current code does not implement a formal stable element-ID registry.

This makes complete traceability impossible from source alone.

**Finding: TRACE-001 — P2**

Required action:
Create a formal UI traceability contract during repair, not as another separate mock document. IDs should map directly to implemented elements/actions.

---

# 16. Navigation Audit

## Customer

Required 5 primary destinations exist:

- C15
- C14
- C01
- C12
- C13

Operational screens are accessible through C01/context.

**Overall:** structurally good.

## Admin

Required 5 primary destinations exist:

- A15
- A14
- A01
- A13
- A12

9 dashboard sections exist.

But:
- A11 is not included in the dashboard.
- A11 is not in bottom navigation.
- A11 has no alternate route.

**Finding: NAV-001 — P1**

---

# 17. Offline / Cache Audit

## Customer

Good:
- encrypted cache.
- user-scoped cache keys.
- cache cleared on logout.
- stale/offline indication.
- mutation separation.
- purchase outbox.

Concern:
- queued purchase behavior depends on server-side idempotency that is currently incomplete.
- live sync has not been tested.

## Admin

Good:
- encrypted cache.
- read-only offline snapshots.
- no sensitive mutations allowed while showing cached snapshot.

Concern:
- periodic worker omits A11 and A12, and therefore does not refresh all intended read domains.
- no cache for a dedicated task-settings screen because the screen itself is not reachable.

**Finding: SYNC-001 — P2**

---

# 18. Security Audit

## Positive

- No `service_role` found in Android source.
- Public anon key architecture is used.
- Auth tokens stored using encrypted preferences.
- Customer reads are user-scoped.
- Sensitive operations are intended to use RPC.
- Admin permissions are checked inside security-definer RPCs in migration 002.

## Critical

1. `activate_protection` missing customer-number authorization.
2. `submit_points_purchase` privilege contract is broken.
3. Admin role seed is missing.
4. Profile provisioning is missing.
5. Live RLS has not been tested.
6. SECURITY DEFINER RPCs have not been concurrency-tested.
7. Idempotency has not been verified under network retry/concurrency.

---

# 19. Visual Audit

The reference defines:

- dark charcoal family.
- primary red approximately `#FC0B39`.
- alternate red approximately `#F40430`.
- primary text `#FFFFFF`.
- charcoal samples around `#0B0F12`, `#0D1216`, `#0F161C`, `#111519`.

## Admin

Admin theme uses:
- `#0B0F12`
- `#111519`
- `#FC0B39`

This aligns strongly with the written visual tokens.

## Customer

Customer theme uses:
- background `#151719`
- red `#D9363E`

The red does not match the approved primary reference token.

**Finding: VIS-001 — P2**

Required action:
Unify customer/admin primary design tokens around the approved reference palette.

## Reference images

The six PNG reference images are not present in the uploaded repository archive.

Therefore:
- no pixel-level comparison.
- no exact spacing comparison.
- no exact component-size comparison.
- no image-based screen-by-screen visual verification.

**Finding: VIS-002 — P2 / Evidence Missing**

---

# 20. Architecture Audit

## Correct

- Two independent Android projects.
- Shared backend concept.
- Shared database.
- Customer/admin source separation.
- Repository/data layer.
- encrypted local cache.
- WorkManager.
- Supabase REST/RPC abstraction.
- no service_role.

## Missing / incomplete

- complete backend contracts.
- complete admin feature layer.
- complete customer write layer.
- formal UI element traceability.
- full live authorization testing.
- complete SQL lifecycle implementation.

---

# 21. Exact Repair Classification

## MUST FIX BEFORE ANY LIVE SQL EXECUTION

### P0

1. Profile provisioning.
2. Purchase-request insertion privilege/RPC design.
3. Subscriber creation after approved first purchase.
4. Activation authorization against `customer_numbers`.
5. Extension idempotency.
6. Complete transaction/concurrency protection around activation/extension.

### P1

7. Provider resolution in activation.
8. Expiration transition.
9. Renewal/reactivation after expiry.
10. Post-expiry task creation rules.
11. Admin role seed and canonical role promotion.
12. A11 accessibility/navigation.
13. Admin core CRUD/action UI.
14. Customer C06 write flow.
15. Customer C10 support write flow.
16. Admin A12 search.
17. Admin A13 notification sending.
18. Admin A14 reporting.
19. Task reschedule/cancel UI and non-destructive history.

---

# 22. Must Normalize Before Final SSOT

1. `notifications` vs `system_notifications`.
2. Audit column vocabulary.
3. Financial ledger entry types.
4. Admin role name: `admin` vs `super_admin`.
5. Canonical final SQL artifact.
6. Canonical RPC names/parameter contracts.
7. Canonical UI element IDs.

---

# 23. Things NOT To Do During Repair

Do not:

- add mock data.
- bypass RLS.
- add service_role.
- solve missing backend contracts with local fake success.
- simply enable unrestricted table writes.
- hide broken buttons without implementing the required contract.
- create a second competing schema.
- silently rename database objects without updating all bindings.
- mark a screen complete because its route exists.
- treat a successful Gradle build as functional completion.

---

# 24. Recommended Repair Order

## Phase R0 — Canonicalize database contract

1. Resolve naming decisions.
2. Create canonical final SQL/migration chain.
3. Seed roles/permissions.
4. implement profile provisioning.
5. implement subscriber provisioning.
6. fix purchase RPC privilege/idempotency.
7. fix number provisioning.
8. fix activation authorization/provider resolution.
9. fix extension idempotency.
10. implement expiration/renewal.
11. implement task scheduling rules.
12. verify audit/ledger/notifications.

## Phase R1 — Customer

1. C06 number lifecycle.
2. C04 purchase after DB fix.
3. C07 activation.
4. C08 extension.
5. C10 support.
6. C09/C11 notification contracts.
7. C12 reports.
8. C13 provisioning/account.
9. UI traceability.
10. visual token correction.

## Phase R2 — Admin

1. A02–A05 details/actions.
2. A06 complete purchase review.
3. A07 task execution/reschedule/cancel.
4. A08 provider/prefix/tariff management.
5. A09 packages.
6. A10 payment methods.
7. A11 task settings.
8. A12 search.
9. A13 notification authoring.
10. A14 reports/export.
11. A15 account.

## Phase R3 — Final verification

Only after source repair:

- static cross-check.
- SQL execution on clean authorized Supabase.
- schema comparison.
- RLS tests.
- RPC tests.
- concurrency tests.
- idempotency tests.
- end-to-end customer flow.
- end-to-end admin flow.
- visual comparison using the approved images.
- final readiness report.

---

# 25. Decision Required From Product Owner

The following should not be guessed during repair:

1. Canonical role code:
   - `admin`
   - or `super_admin`

2. Canonical notification table name:
   - `notifications`
   - or split `system_notifications` + another admin-alert model.

3. Canonical audit field naming.

4. Canonical financial ledger entry-type vocabulary.

5. Exact customer number edit/delete semantics.

6. Exact support workflow:
   - customer creates thread directly,
   - or customer sends via RPC.

7. Exact visual reference assets to use for final pixel-level validation.

8. Whether customer report export is in current scope or only report display.

---

# 26. Final Finding Count

The audit identified the following actionable findings:

- **P0:** 6
- **P1:** 18
- **P2:** 20+
- **P3/P4:** additional non-blocking items
- **Decision Required:** 8 major contract decisions

The exact number may increase during implementation because the current source contains generic UI abstractions that hide screen-specific details; repair must not assume that a generic component equals a completed screen.

---

# 27. Final Readiness

## Database

**NOT READY**

## Customer

**NOT READY**

## Admin

**NOT READY**

## Cross-system integration

**NOT READY**

## Production SQL execution

**DO NOT EXECUTE YET**

## APK release

**NOT READY**

---

# 28. Final Conclusion

The repository is a meaningful implementation checkpoint, not a finished production system.

The good news is that the current implementation is structured enough that it does **not** need to be thrown away.

The correct next action is **targeted repair**, beginning with the database contract and core lifecycle, followed by Customer and Admin bindings.

The repair must be driven by the findings in this report rather than by rewriting either application from scratch.

**No source files were modified during this audit.**
