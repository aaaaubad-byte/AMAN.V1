# Admin Phase 2 — Contract and Environment Blockers

**Status:** Open. This file records real gaps; it does not substitute fake APIs, mock data, or placeholder actions.

## B-DB-001 — Database remains unverified

- **Location:** `database/migrations/001_initial_schema.sql`, Phase 1 status records.
- **Missing requirement:** Live application/schema verification and database authorization/transaction tests.
- **Why required:** Admin screens and mutations must match the deployed source of truth.
- **Affected database objects:** All objects in migration `001_initial_schema.sql`.
- **Affected screens/actions:** All screens; particularly approval, task execution, roles, and settings.
- **Current handling:** Admin code consumes the checked-in SQL contract only. No database connection, SQL execution, or database modification was performed for this phase.
- **Resolution:** Verify/apply the migration to the intended clean Supabase project in the separately authorized database phase, then compare the live schema and run RPC/RLS/security tests.

## B-DB-002 — Supabase runtime values are not supplied

- **Location:** `admin/app/build.gradle.kts`.
- **Missing requirement:** The actual project URL and public anon key for runtime authentication/API use.
- **Why required:** Login and live data access cannot be exercised without the intended endpoint.
- **Affected screens/actions:** Login and all data-backed screens.
- **Current handling:** Build fields default to empty; the app reports an explicit configuration error. No URL/key/service-role value is committed.
- **Resolution:** Supply `SUPABASE_URL` and `SUPABASE_ANON_KEY` via Gradle properties or environment in the authorized build/deployment environment. Never supply `service_role` to the APK.

## B-API-001 — Missing rejection RPC

- **Location:** `database/migrations/001_initial_schema.sql`; RPC list in the execution reference.
- **Missing requirement:** `reject_points_purchase` with reason, audit, and notification behavior. The migration has no rejection function and does not grant a direct request-update path.
- **Affected screen/action:** A06 — reject purchase.
- **Current handling:** No reject button/API is fabricated; only the migration's `approve_points_purchase` RPC is wired.
- **Resolution:** Add and verify the required atomic rejection RPC and its authorization/grants in a later database change.

## B-API-002 — Missing payment-task management contracts

- **Location:** `database/migrations/001_initial_schema.sql`.
- **Missing requirement:** The final authenticated `GRANT SELECT` list does not grant access to `payment_tasks`. RPCs/policies/grants are also absent for reschedule and cancel, including cancellation reason and plan rebuild/history behavior.
- **Affected screens/actions:** A07 list/detail, execute, reschedule, and cancel.
- **Current handling:** The client uses only the existing task table and `execute_payment_task` RPC; with the checked-in grants, the task list cannot be read through PostgREST, so the screen cannot reach its record actions. No task data is fabricated.
- **Resolution:** Grant and test least-privilege task reads plus the required server-side mutation contracts before enabling A07.

## B-API-003 — Missing administrative write policies/contracts

- **Location:** `database/migrations/001_initial_schema.sql`.
- **Missing requirement:** Authorized CRUD/update contracts for providers/prefixes/tariffs, point packages, payment methods, task settings, user/subscriber administration, and profile/security settings.
- **Why required:** UI-side writes would bypass the reference's authorization/transaction requirements; no authenticated grants/policies or appropriate RPCs are present for these writes.
- **Affected screens/actions:** A02–A05, A08–A11, A15, and their edit/create/status actions.
- **Current handling:** Read paths are bound only where the migration grants them; unsupported writes are not exposed as successful actions.
- **Resolution:** Define least-privilege RBAC policies and server-side mutations; then bind the app to those exact contracts.

## B-API-004 — Admin notification domain absent

- **Location:** `database/migrations/001_initial_schema.sql`.
- **Missing requirement:** `admin_notifications` table/contract plus `send_admin_notification` function and target lookup behavior.
- **Affected screen/action:** A13 — compose, target, send, and administrative history.
- **Current handling:** The screen can read the existing `notifications` contract; no administrative send UI/API is fabricated.
- **Resolution:** Add and verify the admin-notification domain, recipient search, transaction/audit rules, and delivery semantics.

## B-API-005 — RBAC granularity and reporting gaps

- **Location:** `database/migrations/001_initial_schema.sql`.
- **Missing requirement:** Permission-specific grants/policies (the available `is_admin()` check is a boolean role test), and an authorized contract for financial/task reports/export.
- **Affected screens/actions:** A01–A15 according to role; especially A07, A11, and A14 export.
- **Current handling:** The app rejects sessions for which `is_admin()` is false and makes no claim of per-action RBAC completeness. Reports query only current schema-backed sources.
- **Resolution:** Define permission-level RPCs/RLS and reporting/export contracts; verify them against real roles.

## B-DESIGN-001 — Reference image assets are not in the repository

- **Location:** Project root.
- **Missing requirement:** Source screenshots/assets listed in the execution reference are absent from the checkout.
- **Affected screens:** Visual pixel-level comparison for all screens, especially A01/A02/A08/A09/A11.
- **Current handling:** Implemented from the written charcoal/red/RTL, fixed-panel, and scroll-list specifications only.
- **Resolution:** Add the approved image references to the repository or provide them to the implementation environment for a visual comparison pass.

## B-API-006 — External payment execution is not integrated

- **Location:** `database/migrations/001_initial_schema.sql` (`execute_payment_task`).
- **Missing requirement:** A verified provider/payment execution adapter or external transaction reference. The SQL RPC records completion, ledger, audit, and plan changes; it does not itself perform the external payment described by the reference.
- **Affected screen/action:** A07 — execute.
- **Current handling:** The UI requires explicit confirmation that the operator completed the payment through the official external channel before it calls the RPC. The app does not claim to execute the external payment or fabricate a provider API.
- **Resolution:** Integrate and verify the approved external service, or retain the documented manual-execution step with any required transaction evidence before enabling completion recording in production.

## B-API-007 — Missing authenticated read grants for supporting Admin data

- **Location:** The final `GRANT SELECT` list in `database/migrations/001_initial_schema.sql`.
- **Missing requirement:** Authenticated SELECT is not granted on `task_settings` or `provider_tariffs`; related company rate/task-rule details therefore cannot be loaded directly. The migration also does not grant SELECT on `financial_ledger` for complete financial reporting.
- **Affected screens/actions:** A05, A08, A11, and A14.
- **Current handling:** The app does not bypass table privileges or switch to a service-role key; direct optional joins are omitted on denied reads and unavailable direct screen reads surface their Backend error.
- **Resolution:** Define least-privilege read grants/policies or an authorized read RPC for the exact Admin fields, then verify them against deployed RLS.
