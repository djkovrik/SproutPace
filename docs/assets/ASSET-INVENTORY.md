# SproutPace asset inventory

Evidence update: 2026-10-10. This inventory separates artifact traceability from visual/runtime verification. It does not declare all asset or accessibility gates complete.

## Current artifact traceability

The [asset manifest](asset-manifest.json) lists 29 assets with runtime outputs, SHA-256 hashes, source/rights notes, production usage symbols and applicable visual evidence. On this review date, every declared output, usage file and visual-evidence file existed and matched its recorded hash; every declared Kotlin usage symbol was present. Symbol presence is not proof that every runtime branch executes.

| Asset | Runtime resource | Declared production mapping |
|---|---|---|
| ASSET-001 | `logo_sproutpace` | `ScreenContent.kt` → `FirstRunScreen`, `ScreenContent.kt` → `SettingsBrandHeader` |
| ASSET-002 | `ic_nav_today` | `App.kt` → `destinations` |
| ASSET-003 | `ic_nav_trays` | `App.kt` → `destinations` |
| ASSET-004 | `ic_nav_plan` | `App.kt` → `destinations` |
| ASSET-005 | `ic_nav_catalog` | `App.kt` → `destinations` |
| ASSET-006 | `ic_settings` | `App.kt` → `SproutPaceTopBar`, `ScreenContent.kt` → `SettingsBrandHeader` |
| ASSET-007 | `ic_add` | `ScreenContent.kt` → `actionIcon` |
| ASSET-008 | `ic_arrow_back` | `App.kt` → `SproutPaceTopBar` |
| ASSET-009 | `ic_edit` | `ScreenContent.kt` → `actionIcon` |
| ASSET-010 | `ic_delete` | `ScreenContent.kt` → `actionIcon` |
| ASSET-011 | `ic_add_photo` | `ScreenContent.kt` → `actionIcon` |
| ASSET-012 | `ic_notifications` | `ScreenContent.kt` → `actionIcon` |
| ASSET-013 | `ic_backup` | `ScreenContent.kt` → `actionIcon` |
| ASSET-014 | `ic_repeat` | `ScreenContent.kt` → `actionIcon` |
| ASSET-015 | `ic_check_circle` | `ScreenContent.kt` → `actionIcon` |
| ASSET-016 | `stage_soaking` | `ScreenContent.kt` → `stageImage` |
| ASSET-017 | `stage_sown` | `ScreenContent.kt` → `stageImage` |
| ASSET-018 | `stage_blackout` | `ScreenContent.kt` → `stageImage` |
| ASSET-019 | `stage_light_growth` | `ScreenContent.kt` → `stageImage` |
| ASSET-020 | `stage_harvest` | `ScreenContent.kt` → `stageImage` |
| ASSET-021 | `illustration_empty_today` | `ScreenContent.kt` → `statusItems` |
| ASSET-022 | `culture_radish` | `ScreenContent.kt` → `cultureImage` |
| ASSET-023 | `culture_broccoli` | `ScreenContent.kt` → `cultureImage` |
| ASSET-024 | `culture_arugula` | `ScreenContent.kt` → `cultureImage` |
| ASSET-025 | `culture_mustard` | `ScreenContent.kt` → `cultureImage` |
| ASSET-026 | `culture_pea` | `ScreenContent.kt` → `cultureImage` |
| ASSET-027 | `culture_sunflower` | `ScreenContent.kt` → `cultureImage` |
| ASSET-028 | `culture_basil` | `ScreenContent.kt` → `cultureImage` |
| ASSET-029 | `culture_garden_cress` | `ScreenContent.kt` → `cultureImage` |

## Provenance

Standard Material Symbols have pinned Google repository source URLs and Apache 2.0 attribution in the manifest. Custom stage/empty-state vectors are recorded as original project geometry. The eight catalog covers have prompts, export dimensions and selected generator outputs in [catalog-covers.json](generation/catalog-covers.json); all eight referenced originals were accessible on this review machine. Those absolute local paths are not portable copies, so retain them when transferring the evidence package.

The approved logo reference, transparent derivative and generation/export notes are retained under [source](source/) and [logo-1b-1.json](generation/logo-1b-1.json). Generated-art records explicitly avoid claiming independent legal or trademark clearance. This review found no concrete unsupported third-party-art ownership claim; it does not supply legal clearance.

## Platform derivatives and historical review

The manifest records Android legacy/adaptive/monochrome launcher outputs and iOS AppIcon slots derived from the approved logo. [brand-refresh-review.md](brand-refresh-review.md) records the 2026-09-23 size, alpha, masking and visual checks, with their original receipts. Those are dated historical results, not a fresh native-runtime pass. [visual-review.md](visual-review.md) records the earlier catalog-cover inspection and explicitly limits its scope.

## Current visual evidence and remaining boundaries

[ux-preview-review.json](../design-review/ux-preview-review.json) declares 231 production captures and records the inspected sets and unchanged-hash carry-forward across record2 through record7. That file is the current capture-level review record; the former inventory total of 78 described the earlier brand-review set. The current declared usage mappings above replace obsolete `screenHero` references.

The current review records visible layout, readability, hierarchy and fixture data. It does not establish gestures, IME behavior, spoken TalkBack order, or iOS runtime behavior. No new artwork, screenshot recording or image inspection was performed for this inventory update. Complete the final screen-family/state-to-golden mapping and post-golden design review against the final stable capture set before closing QG-006/QG-007. Asset hashes alone do not close those gates.

Catalog botanical appearance is illustrative, not diagnostic or evidence that numeric growing recommendations were source-reviewed. See [catalog-source-review-20261010.md](../catalog-source-review-20261010.md) for the unresolved per-culture density/range evidence and timing interpretation.
