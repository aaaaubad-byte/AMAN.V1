# AMAN Phase 1 State

## Phase

Phase 1 — Database

## Current status

**SQL migration created and committed locally for review. Phase is not complete.**

## Implemented in repository

- `database/migrations/001_initial_schema.sql`
- `database/README.md`
- Core AMAN schema entities, enums, constraints, indexes, triggers, RPC definitions, ledgers, audit table, notifications, task plans, and RLS policies are represented in the migration.

## Not verified yet

- The migration has not been applied to a live Supabase project.
- Live schema comparison has not been performed.
- RPC execution tests have not been run against PostgreSQL.
- RLS and role-boundary tests have not been run against a live project.
- Atomicity, rollback, idempotency, and concurrency tests have not been run against a live project.
- No live database was modified in this phase.

## Database connection status

The requested `AMAN.V1` Supabase project is not available through the currently connected Supabase account/tool. The migration was therefore prepared offline and must be verified on the correct project before Phase 1 can be declared complete.

## Remaining work

1. Apply the migration to the correct clean Supabase project.
2. Resolve any PostgreSQL/Supabase compatibility findings.
3. Execute schema, RPC, RLS, authorization, atomicity, rollback, idempotency, and concurrency tests.
4. Compare the live schema with Git and record the result here.
5. Only then mark Phase 1 complete.
