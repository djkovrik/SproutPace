# Platform services

`shared:platform` owns platform-neutral contracts, deterministic fakes, physical reminder reconciliation, notification authorization, system picker adapters, app-owned imports, complete-erasure coordination, and the privacy-minimal Crashlytics bridge. Product UI owns rationale and Settings presentation; domain/data own action meaning, schedules, persistence, backup transactions, and the externally persisted `ProductSettings` preference.

## Permission and lifecycle contract

Checking and requesting notification permission are different operations. The state model includes unknown, checking, requesting, granted, denied with requestability, and unavailable. Android `AppActivity.onResume` and iOS active-scene transitions recheck after Settings without hiding local plans when permission is denied.

## Reminder ownership

`SnapshotReminderPlanSource` derives logical work from the current persisted product snapshot and typed preferences. Daily summary and soak delivery have independent switches; summary time is a local civil value with a 09:00 default. `ReminderReconciler` registers exactly one stable daily summary only when current/overdue work exists, drops past physical work, and treats soak reminders as best effort. Stable `PhysicalReminderId` values replace rather than duplicate registrations and support per-ID cancellation. Android records boot, package-replacement, and time-zone signals durably for the application graph to consume at process start. Restore completion uses `RESTORE_COMPLETED`; product mutations use `PRODUCT_DATA_CHANGED`; only future reminders are rebuilt. iOS reconciliation compares desired IDs with pending `UNNotificationRequest` identifiers.

The production graph owns one OS-backed permission service. Cold-start `unknown/checking/requesting` states preserve existing registrations until the OS check finishes; denied/revoked permission cancels only physical registrations. It never changes persisted grow actions, so Today remains authoritative. Android attaches its Activity Result launcher to the application-scoped service and rechecks on resume. iOS uses `UNUserNotificationCenter` in the KMP host and rechecks on every active-scene transition.

Android uses inexact `AlarmManager.setAndAllowWhileIdle`; no exact-alarm access is requested. Today remains authoritative. Active soak timestamps are instants and therefore do not shift with a time-zone change; the caller recalculates civil daily-summary times before reconciliation.

Reminder preferences are included in local backup archives. The preference row accepts the earlier four-field reminder shape and defaults missing summary time/soak values to 09:00/enabled, while new archives preserve all independent values.

## Pickers and owned files

Android uses the system Photo Picker and Storage Access Framework. iOS uses `PHPickerViewController` and `UIDocumentPickerViewController`. Neither platform requests broad photo/media permission.

Photos use a two-phase app-private lifecycle. A successful picker result is copied immediately into `PendingPhotos` under a validated opaque random token; no external URI or local path crosses into the component draft or database. Cancellation leaves the current draft and persisted observation unchanged. Re-picking and cancelling an editor discard only its previous pending token, while cold-start cleanup removes abandoned pending imports deterministically.

On save, the data manager reads the pending bytes and derives a stable `photo-<sha256>` identity from observation identity plus content. `LocalWorkspaceRepository` applies a typed `Keep`, `Replace`, or `Remove` change: replacement bytes are written before the observation commit, the previous managed file is deleted only after that commit, and failures compensate the row and both old/new files. A successful save deletes its pending token. Managed bytes live only in `observation_photos`, are resolved again for batch-detail rendering after restart, and a missing file suppresses only the image rather than the observation. Backup archives continue to use the separate private `Imports` area; export destinations remain external document handles.

`CompleteErasureCoordinator` composes product-data erasure, imported-file removal, and cancellation of every known stable physical reminder. Already transmitted Crashlytics reports are intentionally outside local-erasure control.

## Crash reporting boundary

`CrashReportingCoordinator` reads and writes the existing `ProductSettings.crashReportingEnabled` value. Release defaults come from that settings contract. Debug, preview, screenshot-test, unit/integration-test, and XCTest environments force collection off. Missing SDK/configuration produces `Unavailable` without blocking startup.

The driver surface cannot set user identifiers, breadcrumbs, custom keys/messages, or attach user-authored content. Only a reviewed `Throwable` may be recorded as a technical non-fatal. Android loads the optional SDK reflectively; iOS compiles Firebase calls only when the package is linked. Both native manifests disable automatic collection so the stored preference is applied before routine upload. See `docs/configuration/FIREBASE-CRASHLYTICS.md` for external setup and controlled symbolication.

## Localization

Native notification text uses stable `NativeTextKey` values. Android default English and Russian resources are under `shared/platform/src/androidMain/res`; iOS uses the same key set in `Localizable.xcstrings`. The operating system selects the locale and falls back to English. No app language override is installed.
