# SproutPace asset inventory

This inventory records the production state verified on 2026-09-20. Every approved asset is referenced from production `commonMain` UI through a typed `Res.drawable` accessor. Standard Material Symbols use the shared screen-level golden review allowed by their `evidenceMode: standard-icon`; custom vectors and generated covers retain explicit visual evidence in `asset-manifest.json`.

## Production usage

| Asset | Resource | Exact production mapping | Approved screen coverage | Status |
|---|---|---|---|---|
| ASSET-001 | `logo_sproutpace` | `ScreenContent.kt` → `screenHero` | SCREEN-001, SCREEN-011 | Complete |
| ASSET-002 | `ic_nav_today` | `App.kt` → `destinations` | SCREEN-002 | Complete |
| ASSET-003 | `ic_nav_trays` | `App.kt` → `destinations` | SCREEN-003 | Complete |
| ASSET-004 | `ic_nav_plan` | `App.kt` → `destinations` | SCREEN-006 | Complete |
| ASSET-005 | `ic_nav_catalog` | `App.kt` → `destinations` | SCREEN-007 | Complete |
| ASSET-006 | `ic_settings` | `App.kt` → `SproutPaceApp`; `ScreenContent.kt` → `screenHero` | SCREEN-002, SCREEN-011 | Complete |
| ASSET-007 | `ic_add` | `ScreenContent.kt` → `actionIcon` → `LabeledActionContent` | SCREEN-002, SCREEN-003, SCREEN-004 | Complete |
| ASSET-008 | `ic_arrow_back` | `App.kt` → `SproutPaceApp` | SCREEN-004, SCREEN-005, SCREEN-008, SCREEN-009, SCREEN-010, SCREEN-012, SCREEN-013, SCREEN-014, SCREEN-015, SCREEN-016 | Complete |
| ASSET-009 | `ic_edit` | `ScreenContent.kt` → `actionIcon` → `LabeledActionContent` | SCREEN-005, SCREEN-008, SCREEN-009, SCREEN-012, SCREEN-013 | Complete |
| ASSET-010 | `ic_delete` | `ScreenContent.kt` → `actionIcon` → `LabeledActionContent` | SCREEN-005, SCREEN-008, SCREEN-009, SCREEN-011, SCREEN-012, SCREEN-013 | Complete |
| ASSET-011 | `ic_add_photo` | `ScreenContent.kt` → `actionIcon` and `ItemCard` missing-photo state | SCREEN-005, SCREEN-009 | Complete |
| ASSET-012 | `ic_notifications` | `ScreenContent.kt` → `actionIcon` → `LabeledActionContent` | SCREEN-002, SCREEN-016 | Complete |
| ASSET-013 | `ic_backup` | `ScreenContent.kt` → `actionIcon` → `LabeledActionContent` | SCREEN-011, SCREEN-014 | Complete |
| ASSET-014 | `ic_repeat` | `ScreenContent.kt` → `actionIcon` → `LabeledActionContent` | SCREEN-003, SCREEN-005 | Complete |
| ASSET-015 | `ic_check_circle` | `ScreenContent.kt` → `actionIcon` → `LabeledActionContent` | SCREEN-002, SCREEN-005, SCREEN-010 | Complete |
| ASSET-016 | `stage_soaking` | `ScreenContent.kt` → `stageImage` → `ItemCard` | SCREEN-005, SCREEN-008 | Complete |
| ASSET-017 | `stage_sown` | `ScreenContent.kt` → `stageImage` → `ItemCard` | SCREEN-005, SCREEN-008 | Complete |
| ASSET-018 | `stage_blackout` | `ScreenContent.kt` → `stageImage` → `ItemCard` | SCREEN-005, SCREEN-008 | Complete |
| ASSET-019 | `stage_light_growth` | `ScreenContent.kt` → `stageImage` → `ItemCard` | SCREEN-005, SCREEN-008 | Complete |
| ASSET-020 | `stage_harvest` | `ScreenContent.kt` → `stageImage` → `ItemCard` | SCREEN-005, SCREEN-010 | Complete |
| ASSET-021 | `illustration_empty_today` | `ScreenContent.kt` → `statusItems` guarded by `showsEmptyIllustration` | EMPTY SCREEN-002, SCREEN-003, SCREEN-006 | Complete |
| ASSET-022 | `culture_radish` | `ScreenContent.kt` → `cultureImage`; catalog `ItemCard` and Recipe hero | SCREEN-007, SCREEN-008 | Complete |
| ASSET-023 | `culture_broccoli` | `ScreenContent.kt` → `cultureImage`; catalog `ItemCard` and Recipe hero | SCREEN-007, SCREEN-008 | Complete |
| ASSET-024 | `culture_arugula` | `ScreenContent.kt` → `cultureImage`; catalog `ItemCard` and Recipe hero | SCREEN-007, SCREEN-008 | Complete |
| ASSET-025 | `culture_mustard` | `ScreenContent.kt` → `cultureImage`; catalog `ItemCard` and Recipe hero | SCREEN-007, SCREEN-008 | Complete |
| ASSET-026 | `culture_pea` | `ScreenContent.kt` → `cultureImage`; catalog `ItemCard` and Recipe hero | SCREEN-007, SCREEN-008 | Complete |
| ASSET-027 | `culture_sunflower` | `ScreenContent.kt` → `cultureImage`; catalog `ItemCard` and Recipe hero | SCREEN-007, SCREEN-008 | Complete |
| ASSET-028 | `culture_basil` | `ScreenContent.kt` → `cultureImage`; catalog `ItemCard` and Recipe hero | SCREEN-007, SCREEN-008 | Complete |
| ASSET-029 | `culture_garden_cress` | `ScreenContent.kt` → `cultureImage`; catalog `ItemCard` and Recipe hero | SCREEN-007, SCREEN-008 | Complete |

## ASSET-001 platform derivatives

The launcher and store exports are deterministic derivatives of the approved `logo_sproutpace.xml` geometry and palette. `docs/assets/generation/render-platform-icons.py` is the reproducible export source; no generated imagery or third-party artwork is involved. Exact file hashes, dimensions, alpha modes, and usage evidence are recorded under `platformDerivatives` in `asset-manifest.json`.

| Platform surface | Delivered inventory | Production usage | Validation |
|---|---|---|---|
| Android legacy launcher | `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` | `AndroidManifest.xml` references `@mipmap/ic_launcher` | 48/72/96/144/192 px RGBA; transparent rounded-corner pixels and visible full-color mark |
| Android adaptive launcher | `mipmap-anydpi-v26/ic_launcher.xml` plus foreground/background/monochrome PNGs in all five density buckets | Adaptive XML binds all three layers; the manifest resolves the same launcher name | Layers are 108/162/216/324/432 px; foreground/monochrome have real transparent padding, backgrounds are opaque, and the mark remains inside the platform mask-safe composition |
| iPhone, iPad, and CarPlay AppIcon | 20–180 px slot exports declared by `AppIcon.appiconset/Contents.json` | Xcode build setting `ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon` | Every declared filename parses at the slot's exact pixel size and is opaque RGB so the OS owns final masking |
| App Store marketing icon | `AppIcon~ios-marketing.png` | `Contents.json` `ios-marketing` 1x slot | Exact 1024×1024 opaque RGB export; no alpha channel or pre-rounded corners |

`docs/assets/platform-icon-contact-sheet.png` was inspected across Android legacy/adaptive/monochrome density extremes and iOS 20 px, 167/180 px, and 1024 px exports. The sprout, stem, and tray remain distinguishable at the smallest size; adaptive transparent padding is visible; the opaque warm-neutral background reaches every iOS edge; no clipping, alpha fringe, embedded text, or obsolete template-rocket artwork remains. This is a technical export of the already approved ASSET-001 brief, so the existing post-golden Lazyweb product-UI review remains applicable and no new product layout or visual direction was introduced.

## Visual evidence review

- The clean Paparazzi recording contains 78 current snapshots: light/dark coverage for all 16 primary screens, exactly 16 compact RU/200% stress variants, five expanded adaptive variants, and the required behavioral/asset inspection states. The RU/200% contact sheet was inspected for Cyrillic glyph coverage, reflow, scrollability, non-color cues, and visible controls.
- Logo: First Run and Settings light/dark goldens show the same tintable vector at its production 88dp and 64dp placements without clipping; the Settings mark remains distinguishable in both themes.
- Stage illustrations: `RecipeAssetInspectionPreview` renders all five vectors at the production 96×72dp size. Their silhouettes, strokes and semantic multicolor palette remain distinct on the light surface; no global tint is applied.
- Empty-state illustration: dedicated Today, Trays and Plan light/dark goldens render the vector at 240×160dp. The inspected layouts preserve navigation clearance and theme contrast, and the image remains decorative beside localized explanatory copy.
- Culture covers: `generated-catalog-contact-sheet.png` was inspected at original resolution and contains all eight labeled 768×480 outputs. `CatalogLoadedAdInspectionPreview` demonstrates production card placement; `RecipeAssetInspectionPreview` demonstrates the selected radish cover on SCREEN-008. The covers retain their neutral background and readable crop in light/dark cards without text embedded in the images.
- Standard icons: the shared navigation, action, editor, Settings, Backup, Notifications, Today, Batch, Recipe and Close Batch goldens exercise the unchanged Material Symbols through their production mappings. Accessible meaning comes from localized adjacent labels or content descriptions, not color alone.

No asset blockers remain in this inventory. Output hashes, generation provenance and rights statements remain in `asset-manifest.json`; generated-cover provenance remains in `docs/assets/generation/catalog-covers.json` and the asset-level inspection remains in `docs/assets/visual-review.md`.
