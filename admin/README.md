# AMAN Admin — Native Android

This folder contains a standalone Kotlin/Jetpack Compose Android application. It is not a web app or a browser prototype.

## Build

Requires JDK 17 or later and Android SDK Platform 35 / Build Tools 35.0.0.

```bash
cd admin
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Runtime configuration

The build accepts `SUPABASE_URL` and `SUPABASE_ANON_KEY` through Gradle properties or environment variables. Example:

```bash
SUPABASE_URL=https://<project-ref>.supabase.co \
SUPABASE_ANON_KEY=<public-anon-key> \
./gradlew assembleDebug
```

The app intentionally does not include `service_role`, passwords, or any private key. Values are absent by default and the login screen reports missing configuration rather than simulating authentication.

## Data integrity and security

- Email/password authentication uses Supabase Auth; a successful login is followed by the base migration's `is_admin()` identity check. Migration 002 adds per-operation `admin_has_permission()` checks for the Admin data and RPC contracts.
- Auth session material and local read snapshots are stored with AndroidX encrypted preferences.
- Read requests use the tables and RPC contracts from migrations 001/002; those migrations have not been applied or tested against a live database.
- The app now binds purchase approval/rejection and task completion to real RPC contracts. Rejection requires a reason; task completion requires the operator to enter an external payment reference and does not itself execute the payment.
- Migration 002 defines audited task reschedule/cancel, profile/status, provider/package/payment-method/settings, notification, and role-aware contracts. Some of these flows still need forms/actions wired in the Android UI; see `IMPLEMENTATION_BLOCKERS.md` and `../MANUS/PHASE_2_STATE.md`.
- Background WorkManager refresh is read-only, network-constrained, and rechecks the Admin role. Offline snapshots are marked stale and cannot be used to perform mutations.
- Building and parsing SQL do not apply or alter a database.

## Readiness

Phase 2 remains in progress, not production-ready. No database exists or is connected in this work; migration 002 has only been statically parsed, and live auth, RLS, RPC, device/UI, rollback and concurrency acceptance tests remain pending. Do not claim completion until the blockers and screen matrix are closed.
