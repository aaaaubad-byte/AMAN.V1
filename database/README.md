# AMAN Database

## Migration order

Apply migration files in lexical order to a **clean, authorized PostgreSQL/Supabase project**:

1. `migrations/001_initial_schema.sql` — Phase 1 core schema, constraints, indexes, base RPCs and customer RLS.
2. `migrations/002_admin_contracts.sql` — permission-scoped Admin reads, least-privilege policies and audited Admin RPCs.
3. `migrations/003_aman1_backend_repairs.sql` — AMAN-1 canonical repairs for provisioning, lifecycle, idempotency, support RPCs, role/audit/ledger vocabulary and task planning.

All migrations and `AMAN_DATABASE_FINAL.sql` are source artifacts. **No migration has been executed on a database in this task.** Apply them only in lexical order to a separately authorized clean PostgreSQL/Supabase project after review.

`AMAN_DATABASE_FINAL.sql` is the canonical concatenated source for the complete chain and is generated from migrations 001–003. Its SHA-256 must be recorded when the separately authorized database phase applies it.

## Important

- No Supabase database was created or connected to, and no SQL was executed.
- Static PostgreSQL grammar parsing is not database execution and does not verify object resolution, privileges, PL/pgSQL runtime behavior, RLS outcomes, concurrency or data migration effects.
- Live-schema verification, RPC and RLS tests, rollback/concurrency tests, and comparison against the intended environment remain pending.
- An Admin account must be securely provisioned with an active role and the needed permission mappings; the Android client must never self-assign Admin.
- Never embed `service_role` in either Android application.
- The AMAN-1 product decisions are: canonical role `admin`; one `notifications` table with `system`/`admin_alert`; canonical audit fields `actor_user_id`, `actor_role`, `before`, `after`; financial types `points_purchase_income`, `task_payment`, `expense`, `adjustment`; number edits only before active protection and archive instead of hard delete; support mutations through RPC; customer report export is in contract scope.
