# SproutPace delivery plan

This is a full AppSpec 2.0 delivery. The authoritative state is `.vibe/delivery-ledger.json`; this document explains the planned capability sequence and ownership.

## Delivery invariants

- Preserve all pre-existing tracked and untracked files. Specialist assignments use disjoint write boundaries.
- Build a reachable Android/iOS production root and one persisted restart flow before expanding feature breadth.
- Keep English as the complete base resource set and Russian as the complete additional locale; system locale only.
- Keep domain contracts platform-neutral, persistence and platform services behind managers, observable component state in MVIKotlin Stores, and navigation/lifecycle in Decompose components.
- Keep iOS production ads disabled. Gate Android Yandex initialization behind a strict privacy-region/consent state machine.
- Treat Firebase files, Android production ad unit, privacy-policy URL/copy, signing, and store credentials as external configuration, not implementation blockers.
- Run one serialized Gradle owner on Windows. Specialists request checks; the orchestrator executes and binds receipts.

## Capability packages

| Order | Package | AppSpec coverage | Primary specialization | Integrated outcome |
| --- | --- | --- | --- | --- |
| 1 | Foundation | QG-001, QG-002, QG-003, QG-007 | Project Architect | Android/iOS entry points render one production root; strict quality, preview/golden smoke, and CI contracts exist. |
| 1 | Core domain | AC-010/011/012/014, AC-020/021/026/027, AC-030/031/032/035-040, AC-050/051/052/054/055/057, AC-060/062/066/068, AC-080/081/085; QG-011 | Domain Engineer | Stable entities, invariants, calculations, scheduling semantics, manager contracts, and catalog identity are tested. |
| 1 | Assets | QG-006 | Assets Creator | Every ASSET-001…029 is delivered with provenance and static validation; later UI/goldens add reachability evidence. |
| 2 | Local data and backup | AC-010-017, AC-020-025, AC-030-034, AC-050-056, AC-070-074, AC-080-085, AC-090-095; QG-011, QG-012 | Persistence Engineer | SQLDelight/settings/private-photo storage, versioned catalog, transactional backup/restore, CSV export, and erasure work offline. |
| 2 | Root/components/stores | All ACs with component-test/store-test surfaces | Decompose + MVIKotlin | Root navigation, screen component contracts, retained Stores, manager orchestration, and public acceptance behavior are wired. |
| 2 | Platform services | AC-064-068, AC-074, AC-090-095, AC-110-113, AC-120-123; QG-002, QG-003 | Platform Engineer | Android/iOS notifications, document/photo pickers, file ownership, lifecycle, Crashlytics contract, and native localization are implemented. |
| 2 | Privacy and ads | AC-100-107; QG-009, QG-010 | Monetization Engineer | Strict privacy endpoint/cache/consent flow gates Android Yandex ads; iOS remains safely disabled. |
| 3 | Product UI | SCREEN-001…016 and all ui-golden/accessibility surfaces | Product Designer + Compose Expert | Adaptive Material 3 UI, EN/RU production resources, accessibility semantics, and all specified states are reachable. |
| 3 | Non-visual verification | All domain/store/component/database/platform/integration surfaces | Test Engineer | Acceptance-led test pyramid and measured filtered Kover threshold are complete. |
| 3 | Visual verification | All primary screens/states/themes plus RU/200% and adaptive stress | Visual Testing | Scanner-generated Paparazzi matrix records, is inspected, and verifies stable goldens. |
| 4 | Post-golden review | QG-007 | Product Designer + Lazyweb | Full first-release Lazyweb review compares all primary screens; objective findings are fixed and re-recorded. |
| 4 | CI/release readiness | QG-001…012 | Project Architect | Linux Android/quality and macOS iOS CI, configuration templates, and release-readiness guide are validated without publication. |
| 5 | Independent closure | QG-004 and every AC/surface | Acceptance Auditor | Fresh-context audit, closure manifest, generated traceability/audit reports, and final ledger validation establish the actual verdicts. |

## Verification cadence

1. Foundation: AppSpec validator, module compilation, Android debug assembly, iOS framework/test contract where host permits, nonzero preview discovery, and three fresh smoke PNGs (light, dark, RU/200%).
2. Per capability: smallest compile and behavioral tests, immutable handoff inspection, targeted receipt binding, and production-flow integration evidence.
3. Stable UI: record, inspect, and verify affected Paparazzi snapshots; never update goldens without reviewing the change.
4. Freeze: strict Detekt, measured Kover verify/report, all tests, localization/catalog/asset/backup validators, Android debug/release build, and macOS iOS build/test/linkage in CI.
5. Closure: immutable fresh-context acceptance audit, report rendering, closure manifest binding, and `validate-delivery-ledger.py --mode closure`.

## External-only completion boundary

Implementation may finish locally while release readiness remains conditional on real Firebase Android/iOS configuration, the Android Yandex production ad-unit ID, final privacy-policy URL and approved consent/disclosure copy, signing/store credentials, and execution of macOS/Xcode or publication gates not available on this host. No external project or publication is created by this delivery.
