# AMAN Phase 1 State

## Phase

Phase 1 — Database

## Current status

**AMAN-1 source repair migration created and parser-validated locally. Live database phase is not complete.**

## Implemented in repository

- `database/migrations/001_initial_schema.sql`
- `database/README.md`
- `database/migrations/002_admin_contracts.sql`
- `database/migrations/003_aman1_backend_repairs.sql`
- `database/AMAN_DATABASE_FINAL.sql`
- Core AMAN schema entities, enums, constraints, indexes, triggers, RPC definitions, ledgers, audit table, notifications, task plans, and RLS policies are represented in the migration.
- AMAN-1 repairs cover Auth→Profile provisioning, first-approved-purchase subscriber provisioning, RPC-only customer-number/support mutations, purchase/activation/extension idempotency, provider resolution, expiration/renewal, task post-expiry/visibility rules, canonical admin role/audit/financial vocabulary, and canonical final SQL.

## Not verified yet

- The migration has not been applied to a live Supabase project.
- Live schema comparison has not been performed.
- RPC execution tests have not been run against PostgreSQL.
- RLS and role-boundary tests have not been run against a live project.
- Atomicity, rollback, idempotency, and concurrency tests have not been run against a live project.
- No live database was modified in this phase.
- Static PostgreSQL parsing passed for migrations 001–003 and the concatenated final SQL; no SQL was executed.

## Database connection status

The requested `AMAN.V1` Supabase project is not available through the currently connected Supabase account/tool. The migration was therefore prepared offline and must be verified on the correct project before Phase 1 can be declared complete.

## Remaining work

1. Apply the migration to the correct clean Supabase project.
2. Resolve any PostgreSQL/Supabase compatibility findings.
3. Execute schema, RPC, RLS, authorization, atomicity, rollback, idempotency, and concurrency tests.
4. Compare the live schema with Git and record the result here.
5. Only then mark Phase 1 complete.
