# Independent acceptance review — informational, 2026-10-17

**Result: GAPS. Full implementation and release readiness are not established.** This is an independent source/evidence review, not a successfully launched formal lean audit. The request is blocked by the adopted AppSpec fingerprint; no delivery state was changed and no request, launch receipt or PASS was fabricated.

Source snapshot: `37d0da6e761012548d8eb1c75afd4d72e3f4a942`. Approved AppSpec SHA256: `11673c5c2c7e50a0c9713bac12f7ddeb2cd229c53e6acadebc70b18cacdc7812`.

## Scope and meaning

Inventory: 12 approved requirements, 9 exclusions, 79 ACs, 12 quality gates, 50 required managed operations, 4 explicit N/A operations, 350 canonical obligation/surface pairs. All 33 normative Markdown files plus AppSpec were reviewed. The JSON includes each AC, gate, managed operation, actual anchors, assertions and surface status.

`evidenced` means bounded inspected host assertions support that pair, subject to receipt qualification. `source-inspected` means implementation/test anchors exist but this reviewer has not substantiated the full required behavior. It is not a pass. `gap` is a known implementation or evidence deficit. `unavailable` names Windows/native execution boundaries. `conditional-inactive` reflects explicit external conditions only.

## Concrete findings

### IR-01 — P1: Archive and CSV work runs on the UI coroutine; iOS document I/O is synchronous there too

Store Executor uses Main. withContext inside createArchive/database/document operations returns before codec.encode/decode and BatchHistoryCsv.encode execute. iOS document port directly performs NSData read/write without dispatcher switch. Large photo archives can freeze interactions and cannot promptly cancel; tests inject Unconfined and do not assert the execution dispatcher. This contradicts quality.md non-functional requirement to keep import/hashing/backup/restore/CSV off main.

Anchors: `shared/data/src/commonMain/kotlin/com/sedsoftware/sproutpace/data/BackupAndExport.kt:333` (suspend fun createArchiveBytes); `shared/data/src/commonMain/kotlin/com/sedsoftware/sproutpace/data/BackupAndExport.kt:335` (suspend fun readPreview); `shared/data/src/commonMain/kotlin/com/sedsoftware/sproutpace/data/BackupAndExport.kt:337` (suspend fun writeCsv); `shared/component/src/commonMain/kotlin/com/sedsoftware/sproutpace/component/store/ScreenStoreProvider.kt:67` (CoroutineExecutor<); `shared/compose/src/androidMain/kotlin/com/sedsoftware/sproutpace/compose/AndroidProductionAppGraph.kt:55` (mainContext =); `shared/compose/src/iosMain/kotlin/com/sedsoftware/sproutpace/compose/IosProductionAppGraph.kt:72` (mainContext =); `shared/compose/src/iosMain/kotlin/com/sedsoftware/sproutpace/compose/IosProductionAppGraph.kt:130` (internal class IosArchiveDocumentPort).

Smallest useful proof: Record execution thread or dispatchers for encode/decode/CSV/native document access through real Store, use a multiple-photo fixture and verify cancellation can interrupt expensive processing.

### IR-02 — P2: Public command preparation strips authored whitespace

prepare() applies trim() to every string argument, including observation text and user-authored recipe metadata. Entered text with leading/trailing spaces/newlines is changed before saveObservation preserves it. The existing verbatim-text test constructs Observation directly and bypasses the broken production command path.

Anchors: `shared/component/src/commonMain/kotlin/com/sedsoftware/sproutpace/component/ScreenInteractionContracts.kt:152` (val arguments = applicableArguments.mapNotNull); `shared/data/src/commonMain/kotlin/com/sedsoftware/sproutpace/data/ProductionScreenDataManager.kt:595` (Observation(observationId); `shared/domain/src/commonTest/kotlin/com/sedsoftware/sproutpace/domain/ContractsAndSnapshotTest.kt:24` (observationsAreOrderedByActualTimestampAndPreserveText).

Smallest useful proof: Public OBSERVATION_EDITOR EditField(text, "  note\n  ") -> Submit(save-observation) -> reopen must equal exact input, including whitespace; repeat for edit and archive round-trip.

### IR-03 — P2: AC-named component forwarding fixtures do not substantiate all declared component behavior

ScenarioManager ignores command semantics and returns a result supplied by each test. These tests prove forwarding, model projection and generic deduplication only. Real SQLite component/root tests now prove selected stage/harvest/abandon/repeat/preference flows; they do not supply independent proof for every CRUD/restart/permission/backup declared component surface. Source-inspected matrix entries intentionally remain short of verified.

Anchors: `shared/component/src/commonTest/kotlin/com/sedsoftware/sproutpace/component/ScreenComponentContractTest.kt:986` (private class ScenarioManager); `shared/component/src/commonTest/kotlin/com/sedsoftware/sproutpace/component/ScreenComponentContractTest.kt:951` (private suspend fun assertCommandScenario); `shared/compose/src/androidUnitTest/kotlin/com/sedsoftware/sproutpace/compose/ProductionLifecycleFlowTest.kt:41` (class ProductionLifecycleFlowTest); `shared/compose/src/androidUnitTest/kotlin/com/sedsoftware/sproutpace/compose/ProductionUxRegressionTest.kt:33` (class ProductionUxRegressionTest).

Smallest useful proof: For uncovered required component pairs use public component/router actions connected to production manager and actual persistence, assert observable state/navigation and failure/restart behavior. Do not count an AC name alone.

### IR-04 — P2: Required native runtime evidence is incomplete

Windows Kotlin iOS compilation does not prove framework/app Xcode linkage, Simulator tests, notification/picker lifecycle or VoiceOver. Host fake-driver tests do not prove Android OS process/device restart, all picker paths, notification permission recovery or configured SDK device behavior. Bounded historical Android smoke exists but its own report lists unverified cases.

Anchors: `.github/workflows/AnalysisAndTest.yml:85` (  ios:); `shared/compose/src/iosMain/kotlin/com/sedsoftware/sproutpace/compose/IosProductionAppGraph.kt:156` (internal class IosReminderDriver); `shared/platform/src/androidMain/kotlin/com/sedsoftware/sproutpace/platform/AndroidReminderDriver.kt:41` (class AndroidReminderDriver).

Smallest useful proof: Current macOS CI artifacts plus scenario-level Android/iOS platform runs with exact source/APK fingerprints and OS observations.

### IR-05 — P2: Accessibility and keyboard operation remain unproven by source token assertions

Tests assert source contains heightIn/roles/headings/live-region strings and compare resource keys. They cannot establish real semantic traversal, focus movement, spoken announcements, visible keyboard focus, or operation of each primary screen at 200%. Golden verification provides pixel stability only.

Anchors: `shared/compose/src/androidUnitTest/kotlin/com/sedsoftware/sproutpace/compose/AccessibilityContractTest.kt:11` (interactiveRendererDeclaresMinimumTargetsRolesErrorsAndStateDescriptions).

Smallest useful proof: Traverse and operate primary screens with keyboard and TalkBack/VoiceOver at compact and expanded widths/200% text; document exact actionable failures or successful behavior.

### IR-06 — P2: Formal lean audit cannot launch against the stale adopted AppSpec fingerprint

Parent reported audit-request rejects current AppSpec with "AppSpec changed: adopt-spec with saved authorization first". This independent review neither changes delivery state nor invents request/launch/PASS artifacts. Final stable matrix receipt is completed/exit0 and supersedes the earlier workspace-changed receipt, but cannot replace a formal acceptance audit.

Anchors: `.vibe/work.json:3` ("appSpecFingerprint").

Smallest useful proof: Owner resolves the approved-spec fingerprint through authorized lean process, freezes inputs, qualifies receipts, then requests fresh formal acceptance; informational report alone cannot close QG-004.

### IR-07 — P2: First-run setup omits its specified optional dark zone and notification-time choices

SCREEN-001 specifies optional setup of local notification time and optional dark zone. Actual FirstRunStep has only ZONE/TRAY/PLAN; first-run command requires a single light-capable zone and exact argument whitelist without a second zone or summaryTime. Settings supports later edits but does not fulfill these first-run choices. FLOW-001 minimum light/tray/plan is compatible with, and does not explicitly remove, the richer screen contract.

Anchors: `shared/compose/src/commonMain/kotlin/com/sedsoftware/sproutpace/compose/ScreenContent.kt:206` (ZONE("first_run_step"); `shared/data/src/commonMain/kotlin/com/sedsoftware/sproutpace/data/ProductionScreenDataManager.kt:403` (Unexpected first-run setup arguments).

Smallest useful proof: Reach optional dark-zone and local notification-time inputs from actual first-run public UI; complete setup and verify stored zone/preferences and no unconfirmed batch.

## Execution evidence and limits

The final stable combined matrix receipt `RECEIPT-5c878c69-89da-4a53-b7e5-f7ce806ef507.json` is completed/exit0 and its log ends BUILD SUCCESSFUL. It covers repository/Android checks plus 14 Kotlin iOS target compilation tasks and supersedes the earlier workspace-changed Android receipt. UP-TO-DATE tasks validly reuse unchanged inputs. Successful tasks do not prove every scenario assertion or native runtime gate. Existing XML/test source and logs were reused; this reviewer did not rerun Gradle.

Current host matrix reported 722 host tests and 234 golden checks. Actual component/root SQLite regressions now provide meaningful evidence for sowing, skip, harvest, observation/abandon/repeat/reopen and immediate preferences. Other AC-named component tests often use `ScenarioManager`, which returns predefined values. Fake platform driver tests support scheduling logic, not OS notification/picker behavior.

No full independent visual inspection of all current goldens, spoken accessibility/keyboard traversal, Android complete native matrix, Xcode linkage/Simulator execution or external configured Crashlytics/Yandex release candidate was performed here. Historical review documents are leads and bounded evidence, not current blanket acceptance. No physical grow validation is claimed.

## AC inventory

| AC | Requirement | Status | Inspected assertion / remaining boundary |
| --- | --- | --- | --- |
| AC-001 | REQ-001 | source-inspected | Empty setup exposes resume; catalog is bundled; root navigation tests use a fake manager and do not prove complete first-run product content. |
| AC-002 | REQ-001 | source-inspected | Finishing setup persists minimum setup and generates a recommendation without a GrowBatch until explicit save; navigation and data tests are separate. |
| AC-010 | REQ-002 | source-inspected | Real manager saves zones into SQLite; typed component fixture tests establish forwarding only. |
| AC-011 | REQ-002 | source-inspected | Persistence readback restores zone fields; actual close/reopen is additionally covered by FileBackedRestartTest; component AC fixture itself only returns a predefined zone. |
| AC-012 | REQ-002 | source-inspected | Zone save/projection and CapacityEngine expose conflicts; source inspection traces occupancy to typed zone projection. Component contract result is fixture supplied. |
| AC-013 | REQ-002 | source-inspected | Deleting a zone unassigns current batches and preserves historical name/placement snapshots in SQLite. |
| AC-014 | REQ-002 | source-inspected | Rectangle area equals width times length; circle area equals pi*d*d/4; zero/negative dimensions rejected. Real manager persists presets. |
| AC-015 | REQ-002 | source-inspected | Persisted preset name, dimensions and shape are read back; separate actual-file reopen test covers storage persistence. |
| AC-016 | REQ-002 | source-inspected | Preset update changes live selection but existing batch tray snapshot remains equal. |
| AC-017 | REQ-002 | source-inspected | Deleting live preset leaves saved historical tray snapshot readable. |
| AC-020 | REQ-003 | source-inspected | Catalog asserts exactly eight stable IDs and version. Recipe guides/rendering resolve local resource keys; golden execution does not establish botanical or source correctness by itself. |
| AC-021 | REQ-003 | source-inspected | Domain rejects empty stage list; actual manager/database save personal recipe separately from built-ins. |
| AC-022 | REQ-003 | source-inspected | Recipe persistence restores stage, seed metadata and optional germination values; whitespace is lost earlier by public command preparation. |
| AC-023 | REQ-003 | source-inspected | Updating personal recipe leaves existing batch recipe snapshot unchanged. |
| AC-024 | REQ-003 | source-inspected | Deleting personal recipe retains historical snapshot and batch readability. |
| AC-025 | REQ-003 | source-inspected | Duplicate receives a distinct ID and copied fields; manager contains built-in and personal branches. |
| AC-026 | REQ-003 | source-inspected | 200 cm2 yields the fixture 4g recommendation; actual 5.5g stays independent. Editable projected field is wired through batch save. |
| AC-027 | REQ-003 | source-inspected | Each culture contains safety keys, HTTPS sources and non-diagnostic image key; recipe source/safety sections are local. |
| AC-030 | REQ-004 | source-inspected | Real commands persist planned batch with immutable recipe/tray inputs; manager is connected through retained Store in ProductionAppGraph. |
| AC-031 | REQ-004 | source-inspected | File-backed SQLite and private photo directory are closed/reopened; historical and assigned batch fields compare equal. |
| AC-032 | REQ-004 | source-inspected | Actual stage correction preserves explicit fixed dates and reports conflicts; history and prior completed actions remain explicit. |
| AC-033 | REQ-004 | source-inspected | Planned row deletion creates neither terminal history nor trash; confirmation is separately generic component tested. |
| AC-034 | REQ-004 | source-inspected | Repository deletes batch observations and managed photos. Native file/provider failure and process-kill windows are not fully runtime demonstrated. |
| AC-035 | REQ-004 | source-inspected | Soaking creates actual event and keeps Placement.None. |
| AC-036 | REQ-004 | source-inspected | Sowing creates SOWN state and exactly one assigned place; production component regression also asserts assignment. |
| AC-037 | REQ-004 | evidenced | Real component+Store+SQLite requires preview confirmation; changes SOAKING to SOWN, writes one event, assigns place, shifts dependent light date, suppresses duplicate command and obsolete soak target. |
| AC-038 | REQ-004 | source-inspected | Capacity overflow offers LEAVE_UNASSIGNED and records a real transition with explicit Unassigned placement. |
| AC-039 | REQ-004 | source-inspected | Undo keeps only previous event, restores assignment, rebuilds stable actions and cancels obsolete IDs; CommandInvocationIntegrationTest repeats transition/undo with distinct invocations. |
| AC-040 | REQ-004 | evidenced | Real component+Store rejects blank skip reason; confirmed skip writes SKIPPED event, reason and LIGHT_GROWTH once. |
| AC-050 | REQ-005 | source-inspected | Cadence/mode/culture rotation persist and generate suggestions. Setup/suggestion tests verify no automatic batch write. |
| AC-051 | REQ-005 | source-inspected | Plan persisted fields survive readback; deterministic forecasts come from local workspace and dates. |
| AC-052 | REQ-005 | source-inspected | Plan edits operate on plan/suggestions; confirmed batches retain their persisted snapshots and fixed dates. |
| AC-053 | REQ-005 | source-inspected | Deleting plan preserves related batch list. |
| AC-054 | REQ-005 | source-inspected | Successive pause/resume intents persist each new invocation; paused plan suppresses future suggestions. |
| AC-055 | REQ-005 | source-inspected | Forecast uses actual plus assigned planned occupancy over date intervals; over-capacity result is needs-resolution with date/zone/capacity options. Golden files cover conflict presentation but native operation not proven. |
| AC-056 | REQ-005 | source-inspected | Opening accepted suggestion does not write; explicit save uses exact rotation culture, dates and draft identity. |
| AC-057 | REQ-005 | source-inspected | One continuous suggestion belongs to current plan; accepted target disappears and stale route is rejected. |
| AC-060 | REQ-006 | source-inspected | Production Today projection partitions due/overdue and excludes future work, de-duplicates hero from remaining list. |
| AC-061 | REQ-006 | source-inspected | Invocation-scoped deduplication suppresses repeated callback, permits subsequent new intent. Stage tasks cannot bypass lifecycle confirmation through bare complete-action. |
| AC-062 | REQ-006 | source-inspected | Snooze retains action stable identity and changes due state; domain tests cover dependencies/readiness. Full public router snooze sequence not independently established. |
| AC-063 | REQ-006 | source-inspected | Manager rejects skip of a stage action; non-stage skip changes action status. Generic Store forwarding tests do not prove entire public skip sequence. |
| AC-064 | REQ-006 | source-inspected | Denied permission adds recovery context while Today tasks remain; fake permission service, native settings recovery unverified. |
| AC-065 | REQ-006 | source-inspected | Production plan emits one useful summary and honors separate soak toggle; delivery tests suppress no-work/duplicate windows. |
| AC-066 | REQ-006 | source-inspected | Only pending elapsed soak targets remain and sowing removes obsolete reminder; native AlarmManager/UN scheduling runtime unverified. |
| AC-067 | REQ-006 | source-inspected | Reconciler fake driver compares registered and desired IDs; cancels obsolete reminders and avoids duplicates. Actual OS device/process restart is separate missing proof. |
| AC-068 | REQ-006 | source-inspected | Civil summary follows DST/new zone and elapsed soak target remains unchanged; production bridge persists civil changes before reconciliation. |
| AC-070 | REQ-007 | gap | Real router creates dated observation and reopens it from file-backed database; command preparation trims authored text, violating verbatim preservation. |
| AC-071 | REQ-007 | evidenced | Actual storage reopen restores observation identity/text in public batch detail; private-photo reopen separately covered in FileBackedRestartTest. |
| AC-072 | REQ-007 | gap | Repository keeps observation identity and unrelated timeline items; public prepare trims leading/trailing authored whitespace. |
| AC-073 | REQ-007 | source-inspected | Confirmed repository deletion removes observation and managed photo; generic destructive component fixture proves confirmation mechanism, not this complete path. |
| AC-074 | REQ-007 | source-inspected | Opaque pending picker token becomes managed file ID; old file removed on replace and remove, missing/raw path rejected. Native picker execution not demonstrated by staged fake bytes. |
| AC-080 | REQ-008 | source-inspected | Real component closes harvested outcome, releases place and removes pending actions; Today then has no task. |
| AC-081 | REQ-008 | source-inspected | Failed outcome remains distinct, keeps reason and Placement.None. Public failure-specific closing sequence not independently runtime proven. |
| AC-082 | REQ-008 | source-inspected | Real router saves abandoned+Travel reason, releases placement and cancels logical pending actions; OS cancellation uses separately tested fake driver. |
| AC-083 | REQ-008 | evidenced | Repeat opens prefilled new draft; cancel writes nothing, save creates distinct planned ID/events empty, original unchanged and both survive file reopen. |
| AC-084 | REQ-008 | source-inspected | Persistence restores terminal outcome, snapshots, observations and actual mass; real root lifecycle test reopens abandoned history. |
| AC-085 | REQ-008 | source-inspected | Actual harvested mass 125.5g stored; applying it to failed outcome rejected, no forecast mass introduced. |
| AC-090 | REQ-009 | source-inspected | Complete fixture exports all relationships, preferences and referenced bytes without live mutation. Codec work is synchronous on Store main context. |
| AC-091 | REQ-009 | source-inspected | Production preview projects actual archive time/version/named counts; wrong-document restore rejected. Decode happens on caller main context. |
| AC-092 | REQ-009 | source-inspected | Replacement compares complete records/files, repeated restore does not merge/duplicate; failure compensation preserves stores and future-only reminder plan. |
| AC-093 | REQ-009 | source-inspected | Invalid archive clears previous preview, returns failure and preserves database; archive tests cover corrupt/missing/unsupported rollback. |
| AC-094 | REQ-009 | source-inspected | UTF-8 CSV handles quoted/newline/RU text and actual mass, excludes photo bytes; localized headers injected. Encoding on Store main context remains a quality gap. |
| AC-095 | REQ-009 | source-inspected | Erase clears database/files/settings, reconciles reminders and emits first-run output only after success; cancel compensates. |
| AC-100 | REQ-010 | source-inspected | Placement contract allows only catalog after item4/history after item5, session owns a single inline request and no prohibited surface. |
| AC-101 | REQ-010 | source-inspected | Unknown/stale/declined/offline/short/missing-ID/no-fill failure states never become ready; Android real SDK/native view integration remains source checked. |
| AC-102 | REQ-010 | source-inspected | No SDK call for unresolved consent; acceptance then setUserConsent precedes initialize; tests use fake SDK. |
| AC-103 | REQ-010 | evidenced | Only fresh matching endpoint/policy response uses persisted choice; settings integration stores canonical privacy payload. |
| AC-104 | REQ-010 | source-inspected | Withdrawal disposes owned ads, stops new requests even if persistence fails; product data untouched. |
| AC-105 | REQ-010 | source-inspected | Fresh changed policy invalidates accepted choice, requires new unresolved decision. |
| AC-106 | REQ-010 | evidenced | Mock network tests reject non200/content type/schema/malformed/oversized/invalid UTF8/redirect/stale/overlong/transport failures; controller stays fail closed. |
| AC-107 | REQ-010 | unavailable | Common fake-host test asserts iOS cannot resolve/init; iOS production composable is safe-disabled. iosTest execution unavailable on Windows. |
| AC-110 | REQ-011 | source-inspected | Configured fake release driver receives default true and stored preference synchronously; unconfigured native build remains unavailable safely. |
| AC-111 | REQ-011 | source-inspected | Persisted false is applied before routine reporting; settings storage tests restore typed preference. |
| AC-112 | REQ-011 | source-inspected | Public router+Store+manager writes false/true/false durably in current settings object; common coordinator applies effective driver state. Configured native upload not tested. |
| AC-113 | REQ-011 | source-inspected | Facade lacks userId/log/key/breadcrumb/attachment APIs and non-release coordinator forces disabled; source scan shows Analytics dependency absent. No controlled configured crash/symbolication receipt. |
| AC-120 | REQ-012 | source-inspected | Production EN/RU XML key sets compare equal; native localized resources exist. Static key parity/goldens cannot prove OS live locale switching or screen-reader pronunciation. |
| AC-121 | REQ-012 | source-inspected | Real router persists light/dark/light and mirrors component field; theme observer and rendering use actual preference. |
| AC-122 | REQ-012 | gap | Accessibility test checks source contains role/error/state/heading/48dp tokens. This is not operation/traversal/focus proof; keyboard-test and spoken-order evidence missing. |
| AC-123 | REQ-012 | source-inspected | Production graph uses local SQL/files/catalog; real repeat/history cycle runs without network ports. Every public core offline branch not exhaustively exercised. |

## Quality gates

| Gate | Status | Evidence / condition |
| --- | --- | --- |
| QG-001 | gap | Logged host tests, Detekt, coverage, migrations and compilation succeeded; non-main archive processing, per-scenario production component proof, native localization/accessibility operation remain incomplete. |
| QG-002 | source-inspected | Android debug/release build succeeded and minSdk=24 is configured. Host tests and bounded prior smoke do not prove the entire required native process restart/permission/picker/SDK/accessibility matrix. |
| QG-003 | unavailable | Fourteen Kotlin iOS target compilations logged success. No current xcodebuild/framework/native Simulator/CI/VoiceOver result independently established on Windows. |
| QG-004 | gap | Formal request rejected AppSpec fingerprint; informational review and passed logs do not close current release acceptance or remove known implementation/evidence gaps. |
| QG-005 | conditional-inactive | Condition inactive: no production store publication requested. No signing/store listing/publication claim. |
| QG-006 | source-inspected | Manifest, production resource wiring and image assets exist; prior derivative reviews exist. This review does not substitute a fresh full artifact hash/visual/rights validation. |
| QG-007 | gap | 234 golden verifications logged; preview fixture rendering is production projected. Source token checks do not establish keyboard/screen-reader operation, and full current visual/Lazyweb disposition was not independently re-inspected here. |
| QG-008 | conditional-inactive | Safe defaults leave Firebase unavailable until external configuration is supplied. No configured Crashlytics-enabled release/RC or controlled symbolicated report was established; this becomes active when such build is prepared. |
| QG-009 | conditional-inactive | No production advertising-enabled Android RC established. Missing IDs fail closed; device/privacy-before-init proof required when enabled. |
| QG-010 | conditional-inactive | No external distribution request. Final legal policy URL/copy and store disclosures remain external prerequisites when distributing. |
| QG-011 | source-inspected | Catalog v2 has eight stable IDs and source/date/resource contracts, plus bounded catalog audit records. Domain contract tests establish identity/formulas; this review does not re-open external source evidence or claim physical grow validation. |
| QG-012 | gap | Broad archive fixtures test full equality, photos, malformed/corrupt rejection, repeated replacement, compensation, cancellation and real file reopen. Archive/CSV processing and iOS provider I/O still run on UI coroutine; cancellation/dispatcher responsiveness is unproven. |

## Managed-operation parity

All required operations inherit the union of their linked AC surfaces via canonical `verification_surface_map`; every such pair appears explicitly in JSON. Explicit non-applicable archive update/delete, consent delete and crash-preference delete remain scoped out.

| Operation | Linked ACs | Status |
| --- | --- | --- |
| GrowZone:create | AC-010 | source-inspected |
| GrowZone:read | AC-011 | source-inspected |
| GrowZone:update | AC-012 | source-inspected |
| GrowZone:delete | AC-013 | source-inspected |
| TraySizePreset:create | AC-014 | source-inspected |
| TraySizePreset:read | AC-015 | source-inspected |
| TraySizePreset:update | AC-016 | source-inspected |
| TraySizePreset:delete | AC-017 | source-inspected |
| UserGrowRecipe:create | AC-021 | source-inspected |
| UserGrowRecipe:read | AC-022 | source-inspected |
| UserGrowRecipe:update | AC-023 | source-inspected |
| UserGrowRecipe:delete | AC-024 | source-inspected |
| UserGrowRecipe:duplicate | AC-025 | source-inspected |
| GrowBatch:create | AC-030 | source-inspected |
| GrowBatch:read | AC-031 | source-inspected |
| GrowBatch:update | AC-032 | source-inspected |
| GrowBatch:delete | AC-033, AC-034 | source-inspected |
| GrowBatch:start-soaking | AC-035 | source-inspected |
| GrowBatch:start-growing | AC-036 | source-inspected |
| GrowBatch:confirm-stage | AC-037 | evidenced |
| GrowBatch:assign-overflow | AC-038 | source-inspected |
| GrowBatch:undo-stage | AC-039 | source-inspected |
| GrowBatch:skip-stage | AC-040 | evidenced |
| GrowBatch:complete-harvest | AC-080 | source-inspected |
| GrowBatch:fail | AC-081 | source-inspected |
| GrowBatch:abandon | AC-082 | source-inspected |
| GrowBatch:repeat | AC-083 | evidenced |
| GrowBatch:record-actual-mass | AC-085 | source-inspected |
| HarvestPlan:create | AC-050 | source-inspected |
| HarvestPlan:read | AC-051 | source-inspected |
| HarvestPlan:update | AC-052 | source-inspected |
| HarvestPlan:delete | AC-053 | source-inspected |
| HarvestPlan:pause-resume | AC-054 | source-inspected |
| HarvestPlan:confirm-suggestion | AC-056 | source-inspected |
| HarvestPlan:suggest-continuous-batch | AC-057 | source-inspected |
| Observation:create | AC-070 | gap |
| Observation:read | AC-071 | evidenced |
| Observation:update | AC-072 | gap |
| Observation:delete | AC-073 | source-inspected |
| Observation:attach-photo | AC-074 | source-inspected |
| BackupArchive:create | AC-090 | source-inspected |
| BackupArchive:read | AC-091 | source-inspected |
| BackupArchive:restore | AC-092 | source-inspected |
| BackupArchive:reject-invalid | AC-093 | source-inspected |
| AdvertisingConsent:create | AC-102 | source-inspected |
| AdvertisingConsent:read | AC-103 | evidenced |
| AdvertisingConsent:update | AC-104 | source-inspected |
| CrashReportingPreference:create | AC-110 | source-inspected |
| CrashReportingPreference:read | AC-111 | source-inspected |
| CrashReportingPreference:update | AC-112 | source-inspected |

## Completion boundary

Locally verified: **partial**, with successful host compilation/test logs and qualified receipts. Implementation complete: **no**, including IR-01/IR-02. Release ready: **no**, with required native/platform/audit evidence still unresolved. Publication, production ads, final distribution policy and configured Firebase conditions are not inferred from a safe-default local release APK.

The report is read-only analysis. It intentionally does not adopt AppSpec, alter `.vibe/work.json`, modify implementation/tests/configuration, contact external services, or close the formal delivery audit.
