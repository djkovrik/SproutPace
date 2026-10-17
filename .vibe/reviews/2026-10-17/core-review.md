# Independent domain/data review — 2026-10-17

Scope: read-only review of REQ-002 through REQ-009 against `spec/app-spec.json`, `spec/domain.md`, `spec/data.md`, and FLOW-002 through FLOW-009. Production, tests, configuration, specification, and delivery state were not changed. No Gradle checks or runtime reproductions were run by this reviewer. Findings below are deterministic source-path deductions; test inspection is evidence of coverage, not an assertion that tests currently pass.

## Findings

### C1 — P1 — Validate archive photo IDs before writing staged files

Primary location: `shared/data/src/androidMain/kotlin/com/sedsoftware/sproutpace/data/AndroidStorage.kt:64`.
Also: `shared/data/src/iosMain/kotlin/com/sedsoftware/sproutpace/data/IosStorage.kt:82`.

Obligation: AC-093 and `spec/data.md` Backup archive: invalid archives must leave existing database and files untouched; photos remain app-owned private data.

Both native `replaceAll` methods concatenate an archive-supplied photo ID directly into the staging path. `PhotoId` only checks nonblank (`shared/domain/src/commonMain/kotlin/com/sedsoftware/sproutpace/domain/model/Identity.kt:16`). Neither archive decode nor preview constrains it to a filename. The normal Android `file(id)` and iOS `path(id)` guards are bypassed by `replaceAll`.

Smallest tamper of a valid archive containing one observation/photo:

1. Decode the hex JSON payload of its `O` record; change `photoId` to `../outside-photos.txt`; encode that JSON back to UTF-8 hex.
2. Change the corresponding `M` record photo-integrity key to the UTF-8 hex of `../outside-photos.txt`.
3. Change the `F` record photo-ID field to the same hex. Keep photo bytes and SHA-256 unchanged.

Counts, referenced-photo sets, and integrity hashes remain correct. `O` decoding constructs `PhotoId` in `DomainJsonCodec.kt:226`; `F` and manifest decoding do so at `BackupAndExport.kt:164` and `:241`. `SnapshotValidation.kt:31-32` checks set equality; `BackupAndExport.kt:342-362` checks duplicates, byte digests, and digest-map equality only. Thus preview is Valid and replacement writes a sibling of the private photo directory. A chosen existing app-private sibling file can be overwritten. Compensation only replaces the photo directory, so an out-of-directory write is outside its rollback boundary. Reject separators/traversal/absolute IDs during validation and enforce resolved path containment at both native staged writes.

### C2 — P1 — Stop using stale editor anchors for ordinary batch corrections

Primary location: `shared/data/src/commonMain/kotlin/com/sedsoftware/sproutpace/data/RecipeBatchCommandHandler.kt:213` (through line 221).
Source of stale fields: `ProductionScreenInteractionBuilder.kt:292-293` in the same directory.

Obligation: AC-032; dependent future actions must reflect corrected actual dates, and changing metadata must not silently move the planned schedule.

Every existing-batch editor initializes `anchorState` to the current state and `anchorAt` to the old latest event time or `now()`. `rebuildDependentFutureActions` treats these defaults as an explicit correction and prefers `command.arguments["anchorAt"]` over the corrected event timestamp. Therefore:

- Open a PLANNED batch whose sow date is next month, change only seed mass, and save the prepared editor command. `withCorrectedSchedule` first builds next month's correct dates; the unconditional explicit anchor then rebuilds all flexible stage dates from today's `now()`, while `plannedSowDate` still says next month.
- For an active batch, change the latest `actualSownAt`/other actual timestamp without also manually changing the separate anchor field. Event history receives the new actual timestamp, but future actions are rebuilt from its old timestamp.

The direct correction test at `ProductionScreenDataManagerIntegrationTest.kt:397-412` supplies a manually consistent anchor and fixed dates, so it does not cover these normal prepared-editor cases. Derive the effective anchor from corrected events, and preserve the planned schedule for ordinary planned-batch edits.

### C3 — P1 — Reject unknown plan rotation cultures before destructive restore

Primary location: `shared/data/src/commonMain/kotlin/com/sedsoftware/sproutpace/data/SnapshotValidation.kt:35` (through line 39).

Obligation: AC-091/AC-093; validate the complete archive before replacing current data.

Validation checks built-in IDs in batch recipe snapshots but never `HarvestPlan.cultureRotation`. Change an `L` record's hex JSON `rotation` to `["unknown_culture"]` in an otherwise valid archive; counts and photo digests need no changes. `CultureId` accepts any nonblank string and `HarvestPlan` requires only a nonempty rotation. The preview therefore succeeds, the database can be replaced, and reminder reconciliation need not fail because it schedules batch actions rather than plan suggestions. The subsequent Plan/Today/zone forecast resolves the unknown ID using `SproutPaceCatalog.cultures.first` (`PlannedSuggestion.kt:66`, and `WorkspaceProjections.kt:46`) and throws. Validate all catalog references before commit, with an invalid-archive result that preserves the original workspace.

### C4 — P2 — Replenish the cadence horizon after its first three slots are accepted

Primary location: `shared/data/src/commonMain/kotlin/com/sedsoftware/sproutpace/data/PlannedSuggestion.kt:56-58`.

Obligation: REQ-005/FLOW-005 future cadence suggestions and ongoing repeated harvest planning.

The cadence branch always generates indexes 0, 1, and 2 from the original persisted anchor (`FORECAST_COUNT = 3`, line 127), then filters out saved slots. Accept/save each of those three suggestions: all future cadence suggestions disappear permanently, even after time passes or the batches close. There is no fourth index or rolling-window computation. `confirm-suggestion` is deliberately nonpersistent (`ProductionScreenDataManager.kt:250`); save-batch only persists the batch; the only plan-anchor writes are initial/save/update plan (`:539`) and pause/resume preserves it (`:547`). This is a missing behavior, not merely absent test evidence. Keep stable identities for existing slots while generating the next unfulfilled slots beyond the fixed initial count.

### C5 — P2 — Reject impossible personal stage order before saving the recipe

Primary location: `shared/data/src/commonMain/kotlin/com/sedsoftware/sproutpace/data/RecipeDraftStages.kt:28-31`.

Obligation: AC-021 valid recipe stage configuration; `spec/domain.md` allows omission of soaking/blackout while retaining the ordered cultivation lifecycle.

The production editor allows every stage in every slot (`shared/component/src/commonMain/kotlin/com/sedsoftware/sproutpace/component/ScreenContractRegistry.kt:570-577`), but production validation checks only nonempty/unique stages and existence of SOWN. A recipe ordered LIGHT_GROWTH, SOWN, READY_FOR_HARVEST saves successfully. After sowing, the component derives LIGHT_GROWTH as the next stage using the hardcoded lifecycle (`ProductionScreenInteractionBuilder.kt:624-627`), while `BatchLifecycleEngine.allowedSuccessors` uses recipe list order and permits READY_FOR_HARVEST instead. The ordinary Confirm stage command fails; alternatively required light/harvest stages can be omitted altogether. Enforce an ordered applicable stage subsequence with the required growing/harvest stages, or make all lifecycle/presentation consumers support the accepted recipe shape consistently.

### C6 — P2 — Carry reduced-capacity conflicts into the Trays projection

Primary location: `shared/data/src/commonMain/kotlin/com/sedsoftware/sproutpace/data/ProductionScreenSnapshotRenderer.kt:84-87`.

Obligation: AC-012 explicitly requires a resulting zone-edit conflict to remain visible on SCREEN-003.

With two active batches assigned to a capacity-two zone, reduce capacity to one. saveZone persists this correctly and Zones reports its overcapacity. However, Trays builds plain `batchItem` rows with no occupancy, forecast, or conflict-resolution data; `batchItem` at line 452 adds a warning only for Placement.Unassigned. Both batches remain Assigned, so SCREEN-003 receives ordinary rows and no resulting conflict. Carry relevant zone conflict state into Trays or resolve assignments explicitly on save.

### C7 — P1 — Erase app-owned imported backup copies during local reset

Primary location: `shared/data/src/commonMain/kotlin/com/sedsoftware/sproutpace/data/BackupAndExport.kt:402-408`.
Import locations: `shared/platform/src/androidMain/kotlin/com/sedsoftware/sproutpace/platform/AndroidSystemPickers.kt:98-119` and `iosApp/iosApp/iosApp.swift:506-514` (directory at lines 544-556).

Obligation: AC-095 and `spec/data.md` Retention and erasure: Delete all local data removes product data and app-owned photos. The exception covers externally saved backup/CSV files, not an internal copy made by the application.

Android's backup picker always copies the entire selected archive into `filesDir/imports/<UUID>.<ext>`; iOS copies it into Application Support/SproutPace/Imports. The archive can contain all private observation text, photos, historical batches, and preferences. After selecting such a backup, cancel the preview or complete restore, then confirm Delete all local data. `eraseAll` clears the observation photo store, database, settings and reminders; it never receives or clears the imported-archive store. The manager's preview cancel/restore/reset branches only clear in-memory references (`ProductionScreenDataManager.kt:352-383`). The root integration event only resets navigation (`ProductionAppGraph.kt:403-405`). Startup clears pending photo imports (`ProductionAppGraph.kt:191`), not backup imports. Thus full private data survives on disk inside the application after successful reset, including copies from cancelled previews; repeated selections also accumulate complete copies.

Cleanup code exists but is unconnected: `AndroidAppOwnedFileStore.deleteAll` (`AndroidSystemPickers.kt:154`) and `CompleteErasureCoordinator` (`shared/platform/src/commonMain/kotlin/com/sedsoftware/sproutpace/platform/PlatformContracts.kt:279-289`) have no production call sites; Swift `eraseImportedFiles` (`iosApp.swift:469`) has no callers. Wire owned-import cleanup into the successful reset boundary (and clear discarded preview imports when no longer needed). Include app-owned imported archives in reset failure/rollback handling rather than silently reporting complete erasure while retaining them.

## Requirement coverage

| Requirement | Production coverage inspected | Assessment |
| --- | --- | --- |
| REQ-002 | Zone/tray CRUD, transactionality, deleted-zone unassignment, immutable tray/zone-name snapshots, current/future occupancy | Implemented core paths; C6 violates required Trays conflict visibility. |
| REQ-003 | Eight-culture bundled v2 catalog, localization keys/source/density metadata, recipe CRUD/duplicate, immutable batch recipe snapshots, seed-area calculation | Substantial coverage; C5 allows unusable authored recipe configurations. |
| REQ-004 | Planned creation; snapshot and metadata correction; lifecycle, events, skip/undo; placement; batch/photo deletion | Substantial coverage; C2 breaks ordinary planned/actual-date corrections. Optional soaking bypass also lacks an explicit SKIPPED event when Confirm sowing is used directly from PLANNED; no separate finding because the UI/lifecycle intent warrants joint review. |
| REQ-005 | Cadence/continuous forecasts; committed vs proposed capacity; dark/light routes; pause/edit/delete; editor confirmation | Substantial coverage; C4 exhausts cadence after three saved slots. Continuous mode has dedicated rollover tests. |
| REQ-006 | Stable IDs, derived effective action statuses, complete/snooze/skip, finite elapsed soak, civil-zone transforms, persisted reminder preferences and restore reconciliation port | Domain/data implementation present; native delivery/permission/reboot integration is outside this bounded review and must be covered by platform reviewer/root gates. C2 also affects due dates. |
| REQ-007 | Verbatim dated observations; stable photo import IDs; private copy/readback; replace/remove/delete compensation; chronological projection | Production paths and focused integration tests found. Disk reopen evidence exists. No independent runtime execution here. |
| REQ-008 | Explicit terminal outcomes, mass/assessment/note, resource release/cancellation, snapshot-based repeat draft and independent new save | Production paths and tests found; native reminder cancellation depends on platform reconciliation review. |
| REQ-009 | Versioned line-framed archive; manifest/photo hashes; cached validated preview; transactional DB + staged native files + durable recovery journal; cancellation compensation; settings; CSV; reset integration | Extensive implementation/test coverage; C1/C3 mean complete validation and safe replacement are not satisfied, and C7 leaves full private archives after reset. |

## Tests actually inspected

Complete files read:

- Domain: `SetupAndCatalogTest`, `CatalogV2Test`, `BatchLifecycleTest`, `ActionScheduleEngineTest`, `BatchPlanningEngineTest`, `EffectiveScheduledActionsTest`, `PlannerAndSchedulingTest`, `ContinuousQueueTest`.
- Android host data: `LocalDatabaseIntegrationTest`, `FileBackedRestartTest`, `PlanningForecastIntegrationTest`, `ZoneForecastTest`, `RecipeGuideIntegrationTest`, `ReminderSettingsIntegrationTest`, `CommandInvocationIntegrationTest`.

Relevant sections read:

- `ProductionScreenDataManagerIntegrationTest`: actual-date correction with explicit anchors/fixed dates; lifecycle transitions, overflow, undo/skip; editor prepared values; action availability; photo replacement; backup/CSV paths.
- `LocalDataAcceptanceTest`: observation update/delete/import/replace/remove and failed commit; outcomes/repeat/history; archive creation/preview/repeat replacement; invalid photo/unsupported/duplicate archive rejection; UTF-8 CSV; erase; preference roundtrip; coroutine cancellation and compensation.

These demonstrate meaningful database/file/recovery tests, including actual file-backed SQL close/reopen and Android photo-directory recovery; several older tests named "restart" only perform same-instance readback, so the file-backed test is the stronger evidence. Existing inspected archive rejection tests cover photo mismatch/digests, duplicates, versions, and orphan observations, but not safe path IDs or invalid plan culture references. Existing cadence tests check the initial fixed three slots and deterministic identity, but not replenishment after accepting all three. No test executions were performed by this reviewer; root owns the final matrix.
