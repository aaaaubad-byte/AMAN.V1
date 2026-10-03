# AMAN Database

## Migration order

Apply migration files in lexical order to a **clean, authorized PostgreSQL/Supabase project**:

1. `migrations/001_initial_schema.sql` — Phase 1 core schema, constraints, indexes, base RPCs and customer RLS.
2. `migrations/002_admin_contracts.sql` — permission-scoped Admin reads, least-privilege policies and audited Admin RPCs.

Both files are source artifacts. **Neither migration has been executed on a database in this task.** Migration 002 is an additive contract extension; review it as a whole before applying it in the separately authorized database phase.

## Important

- No Supabase database was created or connected to, and no SQL was executed.
- Static PostgreSQL grammar parsing is not database execution and does not verify object resolution, privileges, PL/pgSQL runtime behavior, RLS outcomes, concurrency or data migration effects.
- Live-schema verification, RPC and RLS tests, rollback/concurrency tests, and comparison against the intended environment remain pending.
- An Admin account must be securely provisioned with an active role and the needed permission mappings; the Android client must never self-assign Admin.
- Never embed `service_role` in either Android application.
