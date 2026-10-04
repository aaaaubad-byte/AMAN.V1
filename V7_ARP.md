# AMAN \| V7 Audit and Repair Plan

**Document type:** Full Forensic Audit + Repair Master Plan\
**Reference authority:** `V7.md`\
**Audited artifact:** Current AMAN repository contents supplied in
`AMAN-V1.zip`\
**Audit mode:** Static forensic audit with non-destructive build/test
attempts\
**Delivery scope:** Source, database, contracts, tests, and repair
planning only\
**APK/AAB:** Explicitly excluded from this audit and all three repair
phases

------------------------------------------------------------------------

## 1. Executive Summary

The current AMAN repository is **V7 NON-COMPLIANT**.

The project contains two independent Android applications and a
substantial amount of implementation, but the implementation is not
currently converged on the V7 canonical contract.

The highest-impact problems are:

1.  The database does not contain the complete V7 canonical schema.
2.  Several V7 canonical RPC/function contracts are missing or have
    different names/signatures.
3.  The financial, points, activation, protection, task, notification,
    support, maintenance, and audit model is not fully V7-equivalent.
4.  The SQL delivery is not a clean, single-source canonical migration
    path because the final SQL contains repeated function and policy
    definitions.
5.  Both applications contain the expected 15 screen route IDs, but
    screen existence does not prove element/action/backend completeness.
6.  Customer UI traceability is incomplete; the customer registry
    contains 74 explicit element IDs while V7 defines 146 customer UI
    elements.
7.  The admin project has the 15 required route IDs but no equivalent
    element-level traceability registry.
8.  The current test suites contain 16 test methods and do not provide
    the V7 T01-T22 acceptance coverage.
9.  Build/test execution is blocked in the supplied environment because
    Gradle 8.9 is not locally available and the environment cannot
    resolve `services.gradle.org`.
10. No live Supabase/PostgreSQL environment was available, so live RLS,
    transaction, RPC, runtime, and end-to-end behavior cannot be
    certified.

The correct next step is **not APK generation**. The correct next step
is to repair the source in three controlled phases:

-   **Phase 1 --- Database / SQL**
-   **Phase 2 --- Admin**
-   **Phase 3 --- Customer**

Every phase must end with static verification, a status handoff file, a
Git commit, and a push to the original repository.

------------------------------------------------------------------------

# 2. Authority and Evidence Rules

## 2.1 Requirement authority

`V7.md` is the sole requirement authority for this audit.

The current source tree is the object being audited.

No implementation is accepted merely because a file, class, screen,
function, RPC, table, or policy exists.

## 2.2 Evidence classification

-   **PASS** --- directly proven by static evidence and matching V7.
-   **PARTIAL** --- some required implementation exists but the complete
    contract is not proven.
-   **FAIL** --- implementation exists but is demonstrably incorrect.
-   **MISSING** --- required implementation is absent.
-   **CONFLICT** --- implementation contradicts V7.
-   **LEGACY** --- obsolete implementation remains active.
-   **UNTRACED** --- implementation exists but cannot be traced to the
    required contract.
-   **NOT VERIFIED** --- evidence requires runtime/live infrastructure
    that is not available.
-   **BLOCKED** --- verification could not proceed because of an
    environment limitation.

No runtime success is inferred from static source inspection.

------------------------------------------------------------------------

# 3. Repository Inventory

## 3.1 Supplied repository contents

  ---------------------------------------------------------------------
  Item                                                   Verified value
  ----------------------------- ---------------------------------------
  Files                                                              60

  Directories                                                        42

  Kotlin files                                                       26

  SQL files                                                           5

  Customer Android project                                            1

  Admin Android project                                               1

  V7 reference                                                        1

  Audit/report artifacts              Not used as requirement authority
  excluded from the new         
  baseline                      

  Git metadata inside supplied                              Not present
  archive                       
  ---------------------------------------------------------------------

The archive contains:

``` text
admin/
customer/
database/
V7.md
AMAN_V7_FULL_FORENSIC_AUDIT_REPORT-1.md
```

The repair baseline must not depend on retaining any superseded audit
artifact.

## 3.2 Android independence

  Requirement                  Customer              Admin
  ---------------------------- --------------------- ------------------
  Independent Gradle project   Present               Present
  Independent package          `com.aman.customer`   `com.aman.admin`
  AndroidManifest              Present               Present
  Supabase gateway             Present               Present
  Repository/data layer        Present               Present
  ViewModel/state layer        Present               Present
  Local cache                  Present               Present
  Refresh/sync worker          Present               Present
  Tests                        Present               Present

The projects are structurally independent.

Runtime/build acceptance remains unverified.

------------------------------------------------------------------------

# 4. V7 Screen Inventory

V7 defines:

-   15 customer screens: `C01`--`C15`
-   15 admin screens: `A01`--`A15`
-   Total: **30 screens**
-   Total inherited source UI elements: **344**
-   Customer UI elements: **146**
-   Admin UI elements: **198**

The source contains all 30 route identifiers.

This is **not sufficient for PASS**, because V7 requires
element-by-element, action-by-action and backend traceability.

------------------------------------------------------------------------

# 5. Application Findings

## F-001 --- Customer screen implementation is only partially traceable

**Status:** PARTIAL\
**Severity:** P1\
**Observed in:** Customer application\
**Root cause:** UI traceability / incomplete screen contract\
**Repair owner:** CUSTOMER_APP

Evidence:

-   V7 defines 146 customer UI elements.
-   `CustomerUiTraceability.kt` contains 74 explicit customer trace IDs.
-   Static source contains 52 `traceElement(...)` calls.

Required fix:

-   Complete the V7 customer element registry.
-   Attach every required UI element to its actual composable.
-   Map every interactive element to its action.
-   Map every action to ViewModel → Use Case/Repository → Data Source →
    RPC/query → DB effect.
-   Mark explicitly UI-only actions where no backend effect is intended.

------------------------------------------------------------------------

## F-002 --- Admin screen implementation lacks equivalent element-level traceability

**Status:** PARTIAL\
**Severity:** P1\
**Observed in:** Admin application\
**Root cause:** UI traceability\
**Repair owner:** ADMIN_APP

Evidence:

-   All `A01`--`A15` route IDs are present.
-   No equivalent 198-element admin traceability registry was found.

Required fix:

-   Create an admin traceability registry aligned with V7.
-   Map each element, action, state, permission and backend effect.
-   Do not treat route presence as screen completion.

------------------------------------------------------------------------

## F-003 --- Authentication/profile contract is not fully canonical

**Status:** PARTIAL\
**Severity:** P1\
**Observed in:** Customer authentication and database\
**Root cause:** AUTH / DATABASE / RPC\
**Repair owner:** DATABASE + CUSTOMER_APP

V7 requires `create_profile_if_missing` and a canonical profile
provisioning flow.

The current database instead contains `provision_profile_from_auth`.

Required fix:

-   Establish the exact V7 profile provisioning contract.
-   Align database function, caller, input/output and profile creation.
-   Align customer registration/login state handling.
-   Preserve V7 security boundaries.

------------------------------------------------------------------------

# 6. Database Findings

## F-004 --- V7 canonical database schema is incomplete

**Status:** MISSING\
**Severity:** P0\
**Observed in:** `database/AMAN_DATABASE_FINAL.sql`\
**Root cause:** DATABASE SCHEMA\
**Repair owner:** DATABASE

V7 requires 32 canonical tables.

Current final SQL contains 28 tables.

Missing canonical tables identified statically:

``` text
activated_numbers
aman_financial_balance
expenses
system_notifications
operation_logs
```

The current schema instead contains:

``` text
notifications
```

which does not replace the V7 `system_notifications` contract.

Required fix:

-   Rebuild the canonical database schema according to V7.
-   Add all missing canonical entities.
-   Remove/replace conflicting non-canonical structures where required.
-   Preserve historical and relationship semantics.

------------------------------------------------------------------------

## F-005 --- Activated-number lifecycle entity is missing

**Status:** MISSING\
**Severity:** P0\
**Observed in:** Database\
**Root cause:** DATABASE SCHEMA / RELATIONSHIP\
**Repair owner:** DATABASE + RPC

V7 requires:

``` text
customer_numbers
    ↓
activated_numbers
    ↓
protections
```

The current schema does not contain `activated_numbers`.

Required fix:

-   Add the canonical activation entity.
-   Store the required activation identity and lifecycle fields.
-   Refactor protection ownership and task relationships to use the
    canonical activation entity.
-   Update related RPCs and application contracts after the DB contract
    is fixed.

------------------------------------------------------------------------

## F-006 --- Financial model is not V7-equivalent

**Status:** CONFLICT\
**Severity:** P0\
**Observed in:** Database\
**Root cause:** DATABASE BUSINESS LOGIC\
**Repair owner:** DATABASE + RPC

V7 separates:

``` text
financial_ledger
aman_financial_balance
expenses
point_ledger
point_balances
operations
audit_logs
```

The current schema lacks `aman_financial_balance` and `expenses`.

Required fix:

-   Implement the complete V7 financial model.
-   Preserve immutable ledger history.
-   Define atomic balance/ledger relationships.
-   Ensure purchase approval, task execution, points operations and
    expenses follow V7 rules.
-   Add audit and operation effects where required.

------------------------------------------------------------------------

## F-007 --- Notification model conflicts with V7

**Status:** CONFLICT\
**Severity:** P1\
**Observed in:** Database and application data layer\
**Root cause:** DATABASE SCHEMA\
**Repair owner:** DATABASE + RPC + APPS

Current schema uses:

``` text
notifications
```

V7 requires:

``` text
system_notifications
admin_notifications
```

Required fix:

-   Implement canonical notification entities.
-   Separate customer system notifications from admin notifications.
-   Implement read-state behavior.
-   Implement send/read/recipient authorization according to V7.

------------------------------------------------------------------------

## F-008 --- SQL delivery is not cleanly reproducible

**Status:** FAIL\
**Severity:** P0\
**Observed in:** `database/AMAN_DATABASE_FINAL.sql`\
**Root cause:** DATABASE DELIVERY / MIGRATION\
**Repair owner:** DATABASE

Static inspection found repeated definitions inside the final SQL:

-   multiple definitions of several functions;
-   repeated policy definitions;
-   `tasks_admin_only` appears three times;
-   several other policies appear twice;
-   several functions appear multiple times.

Examples include repeated definitions of:

``` text
activate_protection
approve_points_purchase
execute_payment_task
extend_protection
rebuild_task_plan
submit_points_purchase
write_admin_audit
```

Required fix:

-   Produce one authoritative clean SQL/migration path.
-   Ensure every object has one canonical definition.
-   Establish deterministic migration order from zero.
-   Ensure policies/functions/triggers are not duplicated.
-   Validate the resulting schema statically before application repair
    begins.

------------------------------------------------------------------------

# 7. RPC / Backend Contract Findings

## F-009 --- V7 canonical RPC set is incomplete

**Status:** MISSING\
**Severity:** P0\
**Observed in:** Database/application contract\
**Root cause:** RPC\
**Repair owner:** DATABASE + RPC

V7 defines 16 core RPC contracts plus 5 action extensions.

The 16 core contracts are:

``` text
create_profile_if_missing
add_customer_number
submit_points_purchase_request
approve_points_purchase
reject_points_purchase
cancel_points_purchase
resubmit_points_purchase
activate_protection
extend_protection
renew_protection
rebuild_task_plan
reschedule_payment_task
execute_payment_task
cancel_payment_task
mark_notification_read
send_admin_notification
```

V7 action extensions:

``` text
admin_adjust_points
set_maintenance_mode
create_support_thread
send_support_message
close_support_thread
```

Current SQL is missing:

``` text
create_profile_if_missing
submit_points_purchase_request
cancel_points_purchase
resubmit_points_purchase
renew_protection
mark_notification_read
admin_adjust_points
set_maintenance_mode
close_support_thread
```

Required fix:

-   Implement the exact V7 contracts.
-   Match names, parameters, types, return shapes, authorization,
    transaction behavior, idempotency and side effects.
-   Remove or isolate non-canonical alternatives once replacement
    contracts are proven.

------------------------------------------------------------------------

## F-010 --- Existing RPC signatures are not guaranteed to match V7

**Status:** CONFLICT\
**Severity:** P0\
**Observed in:** RPC and application callers\
**Root cause:** RPC CONTRACT\
**Repair owner:** DATABASE + RPC + affected APP

Examples of existing functions include:

``` text
activate_protection
extend_protection
execute_payment_task
approve_points_purchase
```

V7 requires specific input contracts and atomic effects.

Required fix:

For every V7 RPC create a matrix containing:

``` text
RPC
V7 purpose
Parameters
Types
Required/optional
Return
Tables read
Tables written
Transaction
Idempotency
Authorization
RLS interaction
Caller
UI result
Audit effect
Ledger effect
Notification effect
```

------------------------------------------------------------------------

## F-011 --- Maintenance contract is incomplete

**Status:** MISSING\
**Severity:** P1\
**Observed in:** Database/Admin/Customer\
**Root cause:** DATABASE / RPC\
**Repair owner:** DATABASE + RPC + ADMIN_APP + CUSTOMER_APP

V7 requires:

``` text
maintenance_config
set_maintenance_mode
```

Neither is currently implemented in the required canonical form.

Required fix:

-   Add singleton `maintenance_config`.
-   Add `set_maintenance_mode`.
-   Enforce admin permission `system.maintenance`.
-   Audit changes.
-   Make both applications observe the maintenance state and block
    operations according to V7.

------------------------------------------------------------------------

## F-012 --- Support lifecycle is incomplete

**Status:** PARTIAL\
**Severity:** P1\
**Observed in:** Customer/Admin/Database\
**Root cause:** RPC / SUPPORT\
**Repair owner:** DATABASE + ADMIN_APP + CUSTOMER_APP

V7 requires:

``` text
create_support_thread
send_support_message
close_support_thread
```

Current source contains creation/sending behavior but no canonical close
contract.

Required fix:

-   Implement the canonical support lifecycle.
-   Add authorization for customer and admin roles.
-   Add audit behavior.
-   Update UI state and message/thread refresh behavior.

------------------------------------------------------------------------

# 8. Public Identifier Findings

## F-013 --- V7 public identifiers are not fully traced into Android code

**Status:** UNTRACED\
**Severity:** P1\
**Observed in:** Customer/Admin source\
**Root cause:** MULTI_LAYER\
**Repair owner:** DATABASE + RPC + SHARED_CODE + APPS

Static search did not find the canonical V7 identifier vocabulary in
Android source:

``` text
user_code
subscriber_code
added_number_code
activation_code
task_code
```

Required fix:

-   Define generation timing in DB/RPC.
-   Persist identifiers.
-   Map them through models/DTOs.
-   Expose them where V7 requires.
-   Add admin search/display support.
-   Add customer-facing identity only where V7 requires it.

------------------------------------------------------------------------

# 9. Task Engine Findings

## F-014 --- Payment-task schema and lifecycle are not fully V7-equivalent

**Status:** CONFLICT\
**Severity:** P1\
**Observed in:** Database/Admin\
**Root cause:** DATABASE / RPC / TASK ENGINE\
**Repair owner:** DATABASE + RPC + ADMIN_APP

V7 requires task identity/history fields and rules including:

``` text
task_code
original_due_at
sequence_no
idempotency_key
historical reschedule/cancel behavior
future-plan rebuilding
execution anchor behavior
```

Required fix:

-   Align `payment_tasks`.
-   Align task-plan relationships.
-   Preserve completed/cancelled history.
-   Rebuild only future open tasks.
-   Implement exact execute/reschedule/cancel semantics.
-   Update admin task Work Environment after DB contract convergence.

------------------------------------------------------------------------

# 10. Points Findings

## F-015 --- Manual points adjustment is missing

**Status:** MISSING\
**Severity:** P1\
**Observed in:** Admin/Database\
**Root cause:** RPC / DATABASE\
**Repair owner:** DATABASE + RPC + ADMIN_APP

V7 requires:

``` text
admin_adjust_points
```

with:

-   permission `customers.points_adjust`;
-   selected subscriber context;
-   confirmation;
-   balance lock;
-   point ledger;
-   financial ledger;
-   operation;
-   audit;
-   idempotency;
-   full rollback on failure.

Required fix:

Implement the complete atomic contract before implementing the final
admin UI action.

------------------------------------------------------------------------

# 11. Cache / Offline Findings

## F-016 --- Cache encryption and runtime lifecycle are not proven

**Status:** NOT VERIFIED\
**Severity:** P2\
**Observed in:** Customer/Admin cache\
**Root cause:** CACHE / RUNTIME\
**Repair owner:** CUSTOMER_APP + ADMIN_APP

Both applications contain cache implementations and security-related
dependencies.

Static inspection cannot prove:

-   encryption at rest;
-   key lifecycle;
-   logout cleanup;
-   stale-data recovery;
-   conflict handling;
-   device storage behavior.

Required verification later:

-   runtime/device inspection;
-   cache encryption test;
-   logout/session cleanup test;
-   offline read test;
-   mutation authority test.

No local cache may be treated as authoritative for sensitive mutation
success.

------------------------------------------------------------------------

# 12. Testing Findings

## F-017 --- V7 acceptance tests are incomplete

**Status:** PARTIAL\
**Severity:** P1\
**Observed in:** Test source\
**Root cause:** TEST\
**Repair owner:** TESTS

Current test source contains **16 test methods**.

V7 defines T01-T22 acceptance expectations covering:

-   database state;
-   points;
-   protection;
-   tasks;
-   finance;
-   notifications;
-   audit;
-   rollback;
-   security;
-   workflow behavior.

Required fix:

-   Build the test harness around the final V7 DB/RPC contract.
-   Implement T01-T22 or explicitly map each test requirement to
    equivalent executable coverage.
-   Do not claim PASS until tests execute against the real contract.

Testing is a later verification layer and must not drive premature APK
production.

------------------------------------------------------------------------

# 13. Build Findings

## F-018 --- Build/test execution is blocked by environment

**Status:** BLOCKED\
**Severity:** P1\
**Observed in:** Customer and Admin build environments\
**Root cause:** BUILD ENVIRONMENT\
**Repair owner:** BUILD

Attempted:

``` text
bash gradlew test --offline
```

The Gradle wrapper attempted to obtain Gradle 8.9 and failed because:

``` text
UnknownHostException: services.gradle.org
```

Therefore:

-   build PASS is not established;
-   test PASS is not established;
-   this is not evidence that the source itself is broken.

Required later verification:

1.  environment with Gradle 8.9 available;
2.  dependencies available;
3.  clean build;
4.  tests;
5.  capture results.

------------------------------------------------------------------------

# 14. Git / Repository Handoff Finding

## F-019 --- Git state cannot be proven from the supplied archive

**Status:** NOT VERIFIED\
**Severity:** INFO\
**Observed in:** supplied archive\
**Root cause:** REPOSITORY METADATA\
**Repair owner:** BUILD / RELEASE PROCESS

The supplied archive does not contain `.git`.

Therefore the audit can inspect source files but cannot prove:

``` text
git status
git diff
git diff --stat
```

Required process:

-   Perform Git cleanliness checks in the actual repository.
-   Commit only intended repair changes.
-   Push after each repair phase.
-   Record the resulting commit hash in the phase handoff file.

------------------------------------------------------------------------

# 15. End-to-End Traceability Assessment

The required V7 chain is:

``` text
V7 Requirement
↓
Screen
↓
UI Element
↓
User Action
↓
Navigation
↓
State
↓
ViewModel
↓
Repository
↓
Data Source
↓
Supabase Gateway
↓
RPC / Query
↓
Database
↓
Table / Column
↓
Relationship / Constraint
↓
Trigger / Function
↓
RLS
↓
Authorization
↓
Security
↓
Result
↓
State
↓
UI
```

The current project demonstrates substantial portions of this chain, but
the chain is not complete because canonical DB/RPC contracts are
incomplete and live execution is unavailable.

Therefore end-to-end compliance is **NOT VERIFIED** and overall V7
compliance remains **NON-COMPLIANT**.

------------------------------------------------------------------------

# 16. Reverse Traceability Assessment

Reverse tracing exposes several classes of unconnected implementation:

``` text
Database object → no canonical V7 contract
RPC → no canonical V7 requirement
UI element → no complete action contract
Application identifier → no V7 public-ID mapping
Alternative notification table → conflicts with canonical model
Alternative profile provisioning function → conflicts with canonical RPC name
```

These must be resolved during repair rather than merely documented.

------------------------------------------------------------------------

# 17. Repair Ownership Summary

  -----------------------------------------------------------------------
  Owner                             Main scope
  --------------------------------- -------------------------------------
  DATABASE                          Canonical schema, relationships,
                                    constraints, snapshots, IDs,
                                    integrity

  RPC                               Canonical function contracts,
                                    transactions, idempotency, side
                                    effects

  RLS_SECURITY                      RLS and server-side authorization

  ADMIN_APP                         Admin
                                    screens/actions/states/repositories

  CUSTOMER_APP                      Customer
                                    screens/actions/states/repositories

  SHARED_CODE                       Shared contracts/models only where
                                    actually shared

  TESTS                             V7 acceptance/integration/security
                                    coverage

  BUILD                             Environment/build verification

  MULTI_LAYER                       Findings whose repair demonstrably
                                    crosses layers
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 18. Authoritative Three-Phase Repair Plan

The repair plan is intentionally limited to **three implementation
phases**.

APK/AAB creation is outside all three phases.

------------------------------------------------------------------------

## PHASE 1 --- DATABASE / SQL

### Scope

Repair only the database/backend contract required to make V7 canonical.

Primary directory:

``` text
database/
```

### Required work

1.  Establish one canonical SQL/migration source.
2.  Reconcile all 32 V7 tables.
3.  Add missing:
    -   `activated_numbers`
    -   `aman_financial_balance`
    -   `expenses`
    -   `system_notifications`
    -   `operation_logs`
4.  Reconcile `notifications` with the canonical V7 notification model.
5.  Reconcile every column/type/nullability/default.
6.  Reconcile PK/FK/unique/check/index constraints.
7.  Reconcile historical snapshot fields.
8.  Reconcile public identifiers.
9.  Reconcile protection ownership.
10. Reconcile task schema and history.
11. Reconcile financial/points ledgers.
12. Reconcile audit/operation structures.
13. Implement the complete V7 RPC set.
14. Implement maintenance contract.
15. Implement support lifecycle.
16. Implement RLS.
17. Remove duplicate SQL definitions.
18. Establish deterministic migration order from zero.
19. Add database-level integrity/atomicity rules required by V7.

### Explicit exclusions

Do not rebuild Android UI in Phase 1.

Do not create APK/AAB.

Do not deploy to the live Supabase project as part of this phase.

### Phase 1 verification

Static:

``` text
V7 schema → SQL schema
V7 RPC → SQL function
V7 relationship → FK/constraint
V7 rule → DB enforcement
V7 RLS → policy
V7 identifier → generation/storage
```

Runtime:

``` text
NOT VERIFIED
```

unless a real database environment is explicitly available.

### Phase 1 deliverables

``` text
database/
    canonical SQL/migrations
    corrected RPC/functions
    corrected RLS
    corrected constraints/triggers
```

and:

``` text
AMAN_PHASE_1_DATABASE_STATUS.md
```

The status file must contain:

-   completed repairs;
-   files changed;
-   SQL objects changed;
-   unresolved findings;
-   blocked items;
-   not-verified items;
-   static verification results;
-   exact Git commit hash;
-   exact next starting point.

Then:

``` text
git commit
git push
```

------------------------------------------------------------------------

# 19. PHASE 2 --- ADMIN

### Scope

Primary directory:

``` text
admin/
```

plus only the minimum shared files required by the canonical DB
contract.

### Required work order

``` text
V7
↓
Final database contract
↓
Models / DTOs / contracts
↓
Supabase Gateway
↓
Repository
↓
ViewModel / State
↓
Navigation
↓
Screen composition
↓
UI elements
↓
Actions
↓
Permissions
↓
Result states
↓
Traceability
```

### Priority areas

-   A01 dashboard
-   A02 subscribers
-   A03 users
-   A04 added numbers
-   A05 active numbers
-   A06 purchase requests
-   A07 payment tasks
-   A08 telecom providers
-   A09 points packages
-   A10 payment methods
-   A11 task settings
-   A12 search
-   A13 notifications
-   A14 reports
-   A15 account/maintenance

### Special requirements

-   Admin permissions must follow V7 vocabulary.
-   Admin actions must not be UI-only security.
-   Sensitive mutations must use authoritative backend transactions.
-   Every action must reach its correct backend contract.
-   Work Environment states must follow V7.
-   Admin element traceability must be complete.

### Phase 2 verification

Static verification required.

Build/test:

``` text
PASS
```

only if actually executed.

Otherwise:

``` text
BLOCKED
```

No APK/AAB.

### Phase 2 deliverables

``` text
AMAN_PHASE_2_ADMIN_STATUS.md
```

containing:

-   findings closed;
-   files changed;
-   actions repaired;
-   RPCs consumed;
-   static verification;
-   build/test status;
-   unresolved items;
-   exact Git commit hash;
-   next starting point.

Then:

``` text
git commit
git push
```

------------------------------------------------------------------------

# 20. PHASE 3 --- CUSTOMER

### Scope

Primary directory:

``` text
customer/
```

plus only the minimum shared files required by the canonical DB
contract.

### Required work order

``` text
V7
↓
Final database contract
↓
Models / DTOs / contracts
↓
Supabase Gateway
↓
Repository
↓
ViewModel / State
↓
Navigation
↓
Screen composition
↓
UI elements
↓
Actions
↓
Validation
↓
Result states
↓
Traceability
```

### Priority screens

-   C01 Home
-   C02 Active Numbers
-   C03 Inactive Numbers
-   C04 Add Points
-   C05 Operations
-   C06 Add Number
-   C07 Activate Number
-   C08 Extend Number
-   C09 Admin Alerts
-   C10 Support
-   C11 System Notifications
-   C12 Reports
-   C13 Account
-   C14 Search
-   C15 About AMAN

### Special requirements

-   Sensitive mutations require authoritative backend success.
-   Public identifiers must follow V7.
-   Provider detection must use the V7 canonical prefix model.
-   Protection lifecycle must use `activated_numbers`.
-   Notifications must use canonical notification entities.
-   Support must implement the complete lifecycle.
-   Cache must never falsely report backend mutation success.
-   Customer element traceability must cover all V7 customer UI
    elements.

### Phase 3 verification

Static verification required.

Build/test:

``` text
PASS
```

only when actually executed.

Otherwise:

``` text
BLOCKED
```

No APK/AAB.

### Phase 3 deliverables

``` text
AMAN_PHASE_3_CUSTOMER_STATUS.md
```

containing:

-   findings closed;
-   files changed;
-   actions repaired;
-   RPCs consumed;
-   static verification;
-   build/test status;
-   unresolved items;
-   exact Git commit hash;
-   next starting point.

Then:

``` text
git commit
git push
```

------------------------------------------------------------------------

# 21. Repair Dependency Order

The repair dependency graph is:

``` text
PHASE 1 DATABASE
        ↓
Canonical DB
        ↓
Canonical RPC
        ↓
Canonical RLS
        ↓
Canonical identifiers
        ↓
PHASE 2 ADMIN
        ↓
PHASE 3 CUSTOMER
```

Do not reverse this order for database-dependent functionality.

Do not repair an Android caller against an unstable RPC contract when
the contract itself is still being changed.

------------------------------------------------------------------------

# 22. What Counts as Completion of a Phase

A phase is complete only when all of the following are true:

1.  Its defined repair scope is completed.
2.  Every modified file is known.
3.  Every modified DB object is known.
4.  Every closed finding has evidence.
5.  Every unresolved finding has a status.
6.  `PASS`, `BLOCKED`, and `NOT VERIFIED` are not mixed.
7.  No runtime result is fabricated.
8.  No APK/AAB is used as completion evidence.
9.  Static verification has been performed.
10. Git commit exists.
11. Git push completed.
12. The phase status file identifies the exact continuation point.

------------------------------------------------------------------------

# 23. Final Verification After the Three Phases

After Phase 3, perform a new full source audit against V7.

The final source gate must verify:

``` text
30 screens
344 UI elements
all actions
all states
all workflows
all canonical tables
all canonical RPCs
all relationships
all constraints
all RLS
all public IDs
all transaction rules
all ledger effects
all audit effects
all notifications
all cache rules
all tests
```

Only after the source-level gate passes should the project move to live
database deployment and runtime verification.

------------------------------------------------------------------------

# 24. Runtime / Live Verification Gate

This is deliberately outside the three source-repair phases.

Required only after source convergence:

``` text
Supabase Auth
Registration
Login
Session
RLS
RPC
PostgreSQL constraints
Triggers
Points
Purchases
Protection
Extensions
Tasks
Notifications
Support
Finance
Audit
Maintenance
Customer ↔ Database
Admin ↔ Database
End-to-End workflows
Rollback/idempotency
```

Unavailable live infrastructure must be reported as:

``` text
NOT VERIFIED
```

or:

``` text
BLOCKED
```

Never PASS.

------------------------------------------------------------------------

# 25. Final Audit Judgment

## Current status

**V7 NON-COMPLIANT**

The project has a meaningful implementation foundation, including:

-   independent customer/admin projects;
-   30 route IDs;
-   repositories;
-   ViewModels;
-   Supabase gateways;
-   local cache components;
-   refresh workers;
-   tests.

However, the canonical V7 contract is not yet satisfied.

The dominant repair priority is:

``` text
DATABASE
→ RPC
→ RLS / integrity
→ ADMIN
→ CUSTOMER
```

The project should **not** move to APK/AAB production at this point.

------------------------------------------------------------------------

# 26. Immediate Next Action

The first repair task should be **Phase 1 --- Database / SQL**.

Its input should be only:

``` text
V7.md
current database/
current application contracts only where required to resolve DB/RPC callers
```

Its output should be:

``` text
canonical database implementation
+
static verification
+
AMAN_PHASE_1_DATABASE_STATUS.md
+
Git commit
+
Git push
```

No APK/AAB.

No live deployment requirement.

No claim of runtime success without a live database.

------------------------------------------------------------------------

# 27. Compact Handoff Contract

Every future repair instruction must preserve this model:

``` text
READ V7
↓
READ CURRENT SOURCE
↓
IDENTIFY FINDINGS IN THIS PHASE
↓
IDENTIFY EXACT FILES / DB OBJECTS
↓
REPAIR ONLY THIS PHASE
↓
STATIC VERIFY
↓
BUILD IF POSSIBLE
↓
DO NOT FABRICATE RUNTIME RESULTS
↓
WRITE PHASE STATUS
↓
COMMIT
↓
PUSH
```

The next account must be able to continue from Git plus the phase status
file without needing the conversation history.
