# AMAN Database

## Phase 1

The initial production schema is defined in:

- `migrations/001_initial_schema.sql`

Apply migrations in lexical order to a clean PostgreSQL/Supabase project. The migration includes the core AMAN entities, constraints, indexes, triggers, RPC functions, RLS policies, ledgers, audit records, notifications, and task-plan structures.

## Important

- This migration was prepared without applying changes to a live Supabase project, as instructed for this phase.
- Live-schema verification, RPC execution tests, RLS tests, rollback/concurrency tests, and comparison against a live database remain pending until the correct database connection is available.
- `service_role` must never be embedded in either Android application.
