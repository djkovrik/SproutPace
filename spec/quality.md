# Quality

## Non-functional requirements

- Core local operations remain usable without network and do not wait for ads, privacy lookup, or Crashlytics.
- Database and file mutations are transactional where partial success could break relationships.
- Today and batch detail open from local state without visible network delay.
- Image import, hashing, backup, restore, and CSV export do not block the main thread and expose cancellation/failure cleanly.
- Notification scheduling, restart, undo, restore, and time-zone changes are idempotent.
- Sensitive/user-authored content is absent from logs, ad requests, and Crashlytics.

## Test matrix

Use deterministic clocks, IDs, file systems, notification schedulers, region responses, ad hosts, Crashlytics facades, and catalog fixtures.

Required repository coverage:

- domain tests for tray area, seed mass, stage applicability, early/late transitions, skip/undo, elapsed soak duration, local civil scheduling, cadence/continuous suggestions, and capacity conflicts;
- manager/persistence tests for every managed CRUD operation, snapshot immutability, terminal outcomes, referential integrity, photo cleanup, restart, and failure cause preservation;
- MVIKotlin Store tests for intent/action/message/reducer behavior, cancellation, retries, exactly-once completion, and no production mutation outside Stores;
- Decompose component tests for public component models, navigation outputs, tabs, editors, destructive confirmation, permission recovery, and expanded list-detail behavior;
- notification tests for summary grouping, no-work silence, soak target, permission denial, restart/restore idempotence, cancellation, and time-zone change;
- backup/archive tests in QG-012;
- privacy/network/ad lifecycle tests in QG-009 consumer scope only;
- Crashlytics facade/config tests proving release default-on, opt-out persistence, build-type disabling, and no Analytics/user identifiers;
- EN/RU resource completeness, stable catalog key mapping, system locale changes, unsupported-locale EN fallback, native fallback resources, and hardcoded-string scan;
- Detekt with no auto-regenerated baseline;
- filtered Kover baseline measured on owned product code before selecting a fixed threshold;
- applicable Gradle compilation/test tasks through the repository wrapper.

QG-001 owns this matrix. Warnings are reported separately from failures.

## Android platform gate

QG-002 requires:

- release/test build and process-restart smoke;
- notification permission denied/granted flows and system-settings recovery;
- best-effort soak target without `USE_EXACT_ALARM` or `SCHEDULE_EXACT_ALARM` declarations;
- system photo picker and document create/open flows;
- Yandex automatic initialization disabled until the privacy gate permits it;
- Android Yandex manifest/SDK linkage, debug test IDs, release ID guard, no overlap/inset defects, and no request in ineligible states;
- Crashlytics Android configuration, mapping upload path, opt-out behavior, and controlled test crash for release candidate;
- Compose resources, EN/RU/native strings, system theme/insets, and platform accessibility smoke.

## iOS platform gate

QG-003 requires evidence from macOS and CI for both declared iOS targets:

- framework/app compilation and native entry point;
- notification authorization/denial, scheduling, cancellation, restart, and time-zone behavior;
- recurring daily summary beyond day 30 for work due by the next window; future-only no-work silence, 30 useful queued windows, foreground replenishment/conversion to recurrence, stable-ID cancellation, native pending-request budget, civil-time/DST behavior, and elapsed soak independence, under the approved AC-065 boundary in `../docs/decisions/DEC-20261024-ios-summary-horizon.md`;
- photo and document pickers plus private file persistence;
- Firebase Crashlytics Apple configuration, dSYM upload/symbolication path, opt-out behavior, and controlled test crash;
- Yandex integration remains safe-disabled without an iOS production ad unit; no production ad request occurs;
- Compose/native EN/RU resources, system theme/safe areas, VoiceOver, and expanded/compact behavior.

Absence of a Mac receipt is an unresolved required platform gate, not evidence of success.

## Visual quality gate

QG-007 requires:

- component-owned preview implementations, not local Compose-only fakes;
- nonzero ComposablePreviewScanner discovery;
- early production render smoke containing real theme/font/resources in light/dark and RU at 200%;
- applicable content/empty/error/offline/permission/conflict/destructive states for all primary screens;
- compact and expanded layouts;
- Paparazzi record, human inspection, approval, and verify with stable IDs;
- accessibility inspection for reading order, labels, state announcements, focus, contrast, non-color cues, 48dp targets, reduced motion, clipping, and EN/RU glyphs;
- first-release Lazyweb review of the full primary-screen golden set and disposition of every blocking finding.

## Catalog quality

QG-011 verifies:

- exactly the approved eight stable culture IDs, including `garden_cress` as `Lepidium sativum` and no watercress alias;
- catalog version and review date;
- reachable source metadata and concise original paraphrases rather than copied instructions;
- stage ranges, applicability, density units/formulas, metric display, and editable recommendation behavior;
- food-safety copy and non-diagnostic image/copy boundaries;
- complete EN/RU resource keys and offline resolution;
- simple internal versioning only—no external approval, signature, attestation, or invented physical-test receipt.

## Backup quality

QG-012 uses fixtures covering every managed entity, historical snapshots, stage events, schedules, observations, UTF-8 EN/RU/user text, and multiple photo files.

Verify create → preview → replace restore → restart equality; future-only reminder rebuild; overdue in-app actions; no completed reminders; repeat restore without duplicates; invalid/malformed/unsupported archive rollback; cancellation; insufficient storage/write failure; missing/corrupt photo rejection; and CSV escaping/encoding without photos.

## Advertising and privacy verification

Use the canonical endpoint and test only the consuming app. Controlled responses cover fresh cache, expired cache, protected, non-protected, unknown, malformed/oversized/non-JSON/non-UTF-8, redirect, 429, 503, timeout, TLS/captive/offline, unknown schema, overlong expiry, policy change, accept, decline, withdrawal, and restart.

Assert:

- at most one refresh path and a maximum 72-hour accepted cache;
- app-owned consent appears only when required;
- no Yandex SDK initialization or load before eligibility;
- `YandexAds.setUserConsent(...)` precedes every allowed initialization;
- decline/unresolved/stale/error/no-ID states produce no init, request, or visible slot;
- withdrawal stops future requests without affecting product data;
- only SCREEN-007 after item four and SCREEN-003 History after item five are eligible;
- previews/tests use safe-disabled hosts and no real requests;
- no endpoint smoke, readiness attestation, backend gate, alternative endpoint, CMP/TCF claim, mediation, Google demand, advertising ID, ATT, or location inference is added.

## Crashlytics verification

- Release default is enabled; user can disable/re-enable from Settings and the choice survives restart.
- Preview, debug, unit, screenshot, and automated test hosts do not upload.
- Firebase Analytics is absent and breadcrumb collection is not enabled.
- No user ID, notes, photos, culture/recipe, schedule, advertising state, or private data is logged as keys/messages.
- Only reviewed technical non-fatals are recorded.
- Android mapping and iOS dSYM symbolication are proven on controlled release-candidate crashes when QG-008 applies.
- Privacy disclosures explain default collection, opt-out, and that local reset cannot erase reports already sent.

## Accessibility and localization

Require base EN and complete RU Compose Multiplatform resources with identical keys; OS-only locale selection; no language picker, locale preference, or app-specific override. Unsupported locales fall back to EN. Native-only text follows the same default/additional locale contract.

All screens support 200% text, screen readers, keyboard/pointer input where exposed, visible focus, reduced motion, 48dp targets, logical heading/order, manual date/time input, and non-color cues. Production previews use production resources and fonts.

## Architecture checks

Reject custom generic Success/Failure wrappers, nested `Result<Result<T>>`, lost nullable successes, swallowed cancellation, duplicate mutable state ownership, component-to-database calls, raw Store state exposed as UI model, stateful Compose previews, dependency cycles, and incorrect lifecycle/resource ownership.

Require Decompose public components, MVIKotlin Stores for independent mutable/async state (read-only children may project parent state), injectable service or Manager boundaries returning Kotlin `Result`, pure reducers, immutable component models, manual DI/factories, and separate preview components. The screenshot host remains the Compose UI/resource-owning module unless a documented build limitation requires the approved dedicated-host alternative.

## Asset delivery

QG-006 follows the asset contract. Every ASSET item must have a delivered runtime file, manifest hash, provenance/rights, reachable production usage, and applicable visual evidence. Generated culture covers need generation records and source/rights notes. Standard Material vectors use standard-icon evidence only when unchanged and correctly identified. All resources compile on Android and iOS and render at declared sizes in production previews.

Platform launcher/store icon derivatives are verified with platform-specific mask/background/export rules even though the shared logo source is in Compose Resources.

## External configuration

- QG-005: Android store publication, signing, store listing, countries, current name check, and publication result only when publishing.
- QG-008: separate Firebase Android/iOS app registrations, configuration files, symbol upload, and controlled readable crash reports for release candidates.
- QG-009: Android Yandex project/ad unit and device verification when production ads are enabled. No iOS production ad project is required in scope.
- QG-010: final privacy-policy URL/copy and store/platform disclosures when externally distributing.

Do not invent credentials, IDs, URLs, console state, publication success, or external receipts. Do not create a release gate for the accepted shared privacy endpoint.

## Release acceptance

QG-004 requires:

- strict AppSpec validation and canonical inventory parity;
- all active ACs verified on their declared surfaces;
- QG-001, QG-002, QG-003, QG-006, QG-007, QG-011, and QG-012 current and successful;
- each conditional external gate either proven when its condition is active or clearly reported as inactive/blocked-external;
- build/test CI for Android and iOS; publishing automation only when publication scope is explicitly activated;
- pinned tested dependency/SDK versions; separate upgrades use authoritative release/migration sources and verify artifact availability;
- one frozen integration matrix and independent acceptance audit with current fingerprints;
- separate reporting of locally verified, implementation complete, and release ready.

Validator success alone does not prove product content, platform behavior, external configuration, physical grow validation, implementation completion, or release readiness.

## Delivery and verification boundaries

The approved process transition is recorded in `../docs/decisions/DEC-20260920-lean-delivery.md`. Lean delivery is current: one owner per complete scenario, direct checks with execution logs, compact `.vibe/work.json`/recovery notes, early production-root integration, risk-based golden review and independent final audit. Strict ledger/assignment/handoff history is preserved but not extended. Do not import historical verified claims as current proof.

Use affected tests during iteration and one complete applicable matrix at a stable snapshot. After bounded fixes the same independent auditor reviews changed code and dependent behavior; changes to AppSpec or broad architecture start a fresh audit. Known gaps are auditable and prevent false completion. Preserve all approved acceptance surfaces, existing tests and goldens. Future visual variants cover primary screens in light/dark and distinct risk states; choose additional stress combinations by risk/pairwise coverage without reducing explicit screen contracts. Record shared M3 choices once and inspect every affected screen locally; reuse or group external review by screen family.

Architecture flexibility governs future work and does not require rewriting existing tested modules/Stores. Existing lint and coverage thresholds remain binding; any actual policy change needs a scoped decision, never a workaround for a failed test.

