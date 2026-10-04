# AMAN — Database Phase Status

## Repository

- **Repository:** `aaaaubad-byte/AMAN.V1`
- **Working directory:** `/home/ubuntu/AMAN.V1`
- **Remote:** `https://github.com/aaaaubad-byte/AMAN.V1.git`
- **Branch:** `main`
- **Starting commit:** `dc3fa27f2bef0bf05eb864cf84461457dcf14d10`
- **Initial working tree:** clean after cloning a fresh isolated copy.
- **Scope check:** only `database/` was changed in this phase; `customer/` and `admin/` were not inspected or modified.

## Existing SQL Reviewed

The following files were read in the required order and used as historical implementation evidence only:

- `database/README.md` — documented the old additive migration path and its non-canonical `notifications` model.
- `database/migrations/001_initial_schema.sql` — old base schema and initial customer RPCs.
- `database/migrations/002_admin_contracts.sql` — old admin permissions, policies, and admin RPCs.
- `database/migrations/003_aman1_backend_repairs.sql` — historical naming, lifecycle, support, and ledger repairs.
- `database/migrations/004_admin_provider_catalog_rpcs.sql` — historical provider-prefix and tariff RPCs.
- `database/AMAN_DATABASE_FINAL.sql` — old concatenated artifact; it was not used as the final source because V7 ARP identifies duplicate definitions and missing canonical entities.

The authoritative requirements were read from `V7.md`, especially `V7 CANONICAL DATABASE CONTRACT`, `V7 RPC CONTRACT`, `V7 RLS / RBAC CONTRACT`, and the maintenance/support amendments. The database findings F-004 through F-015 in `V7 ARP.md` were used for the repair scope.

## V7 Database

Created a clean-from-zero artifact:

- `database/AMAN_V7_DATABASE.sql`
- 33 tables: the 32 canonical entities implied by the V7 contract plus the explicit `maintenance_config` extension.
- Canonical activated-number lifecycle: `customer_numbers → activated_numbers → protections`.
- Public identifiers: U/S/A/X/O/T code columns and transaction-local generation helper.
- Points model: `point_balances` and append-oriented `point_ledger` with direction and entry type.
- Financial model: `financial_ledger`, `aman_financial_balance`, and `expenses` kept separate from points.
- Notifications: canonical `system_notifications` and `admin_notifications`; no legacy `notifications` table.
- Task model: task code, original due date, sequence, reschedule history fields, and idempotency key.
- Support, operation logs, audit logs, maintenance configuration, constraints, indexes, triggers, RLS, and permission helpers.
- Canonical RPC names are present for the 16 core contracts and 5 action extensions listed by V7.
- No runtime database was created, seeded, or modified.

## V7 ARP Findings

| Finding | Status | Notes |
|---|---|---|
| F-004 canonical schema incomplete | **PARTIAL** | Canonical entities were rebuilt in the new artifact; live schema/type verification is not available. |
| F-005 activated-number lifecycle | **PARTIAL** | Entity, relationship, and current-protection link are present; activation transaction remains unresolved pending executable verification. |
| F-006 financial model | **PARTIAL** | Separate financial balance, ledger, expenses, points, and operations are present; configured-value posting paths require final policy/runtime verification. |
| F-007 notification model | **CLOSED (static)** | Legacy `notifications` is absent; canonical system/admin notification tables and policies are present. Runtime delivery is not verified. |
| F-008 clean reproducible SQL | **CLOSED (static)** | New file is independent of old migrations and has one definition per table/function/policy/trigger. PostgreSQL execution is not verified. |
| F-009 canonical RPC set | **PARTIAL** | All required RPC names exist; some mutation bodies are explicitly marked `UNRESOLVED` rather than being claimed complete. |
| F-010 RPC signatures | **PARTIAL / UNRESOLVED** | Signatures were derived where V7 specifies inputs; V7 does not fully specify SQL return types and all operational parameters. |
| F-011 maintenance | **PARTIAL** | `maintenance_config` and `set_maintenance_mode` exist with permission/audit paths; live enforcement and policy verification remain untested. |
| F-012 support lifecycle | **PARTIAL** | Create/send/close functions and authorization paths exist; live authorization and audit behavior are not verified. |
| F-013 public identifiers | **PARTIAL** | Canonical storage and generation are included; full application traceability is outside Phase 1 and was not inspected. |
| F-014 payment task engine | **PARTIAL / UNRESOLVED** | Canonical task fields and plan structure are present; execution/reschedule/cancel semantics are intentionally not claimed verified. |
| F-015 manual points adjustment | **UNRESOLVED** | V7 requires a configured monetary value per point, but no authoritative value/configuration is supplied; the RPC fails explicitly instead of guessing. |

## Final SQL

- **File:** `database/AMAN_V7_DATABASE.sql`
- **Function:** single deterministic build artifact from an empty authorized PostgreSQL/Supabase database.
- **Legacy files:** retained unchanged for historical auditability; they are not prerequisites for the new artifact.

## Static Verification

- Required canonical tables: **33 found, 0 missing**.
- Required RPC names: **21 found, 0 missing**.
- Duplicate table definitions: **none**.
- Duplicate function definitions: **none**.
- Duplicate policy definitions: **none**.
- Duplicate trigger definitions: **none**.
- Forbidden legacy table names detected in the new artifact: **none**.
- Customer/admin source changes: **none**.
- SQL parser or live PostgreSQL execution: **NOT VERIFIED**; `psql`, `pg_dump`, `sqlfluff`, and `pg_format` are unavailable in the sandbox.
- Supabase execution, RLS behavior, transaction rollback, concurrency, and RPC runtime behavior: **NOT VERIFIED**.

## Remaining

1. Resolve the exact V7 SQL return contracts and executable business logic for activation, extension, renewal, task reschedule/execute/cancel, and manual points adjustment against an authorized PostgreSQL test environment.
2. Supply/approve the configured monetary value per point required by `admin_adjust_points`; it is not defined in V7 and was not guessed.
3. Run PostgreSQL parse/build, RLS, concurrency, rollback, and T01–T22 acceptance tests in a real authorized environment.
4. Phase 2 must consume this database contract before any Admin work begins.

## Git

- **SQL artifact commit:** `6e2eaad7e0b29cea72a0a398f73fd4c04533d033`
- **Status finalization commit:** to be recorded after the status hash is written.
- **Push result:** pending until the final status commit is pushed; no success is claimed in advance.
