# AMAN Database

## Migration order

Apply migration files in lexical order to a **clean, separately authorized PostgreSQL/Supabase project**:

1. `migrations/001_initial_schema.sql` — Phase 1 core schema, constraints, indexes, base RPCs and customer RLS.
2. `migrations/002_admin_contracts.sql` — permission-scoped Admin reads and audited Admin RPCs.
3. `migrations/003_aman1_backend_repairs.sql` — AMAN-1 canonical repairs for provisioning, lifecycle, idempotency, support RPCs, role/audit/ledger vocabulary and task planning.
4. `migrations/004_admin_provider_catalog_rpcs.sql` — AMAN-2 source contract for audited provider-prefix and provider-tariff administration.

All migrations and `AMAN_DATABASE_FINAL.sql` are source artifacts. **No migration has been executed on a database in this task.** Apply them only in lexical order to a separately authorized clean PostgreSQL/Supabase project after review.

`AMAN_DATABASE_FINAL.sql` is the canonical concatenated source for migrations 001–004. Its digest should be recorded when the separately authorized database phase applies it.

## Admin catalog RPCs (AMAN-2)

- `admin_save_telecom_prefix` adds or updates a `telecom_prefixes` record, validates the prefix and number length, requires `admin_providers.manage`, and writes an audit entry. Status changes (active/inactive/archived) use the same RPC; rows are not hard-deleted.
- `admin_save_provider_tariff` adds or updates a `provider_tariffs` record, validates points/day and the effective-date interval, requires `admin_providers.manage`, and writes an audit entry. Existing protection snapshots are not rewritten.
- `admin_save_provider` remains the contract for provider name, code, status, and operational settings. All calls require an authenticated user and the corresponding server-side permission check.
- Admin number management follows the existing supported contract: view/copy and status/archive only. There is intentionally no Admin RPC to edit a phone number.

## Important

- No Supabase database was connected to and no SQL was executed.
- Static PostgreSQL parsing is not database execution and does not verify object resolution, privileges, PL/pgSQL runtime behavior, RLS outcomes, concurrency or data migration effects.
- Live-schema verification, RPC and RLS tests, rollback/concurrency tests, and comparison against the intended environment remain pending.
- An Admin account must be securely provisioned with an active role and the needed permission mappings; the Android client must never self-assign Admin.
- Never embed `service_role` in either Android application.
- Canonical role: `admin`; recipient-facing delivery uses `notifications` with `system`/`admin_alert`. The existing Admin campaign/audit history table `admin_notifications` is retained from migration 002 and is not created by AMAN-2. Audit fields are `actor_user_id`, `actor_role`, `before`, `after`; financial types are `points_purchase_income`, `task_payment`, `expense`, `adjustment`.
