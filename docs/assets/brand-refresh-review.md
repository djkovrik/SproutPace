# Brand review — 2026-09-23

Approved inputs: user-selected logo 1B-1 and Forest & Lime palette A.

Native contact sheet inspected after export: legacy rounded tiles, circular adaptive masks, monochrome silhouettes, iOS 20/167/180/1024px samples. An initially oversized adaptive leaf was corrected; final artwork passes a pixel-level 66dp safe-circle check at every density. Transparent foregrounds and monochrome share identical alpha. Background layers are solid ivory. All 21 iOS slots remain RGB without alpha, with no baked corner mask. The 20px slot is necessarily simplified by downsampling but keeps the sprout/tray silhouette.

In-app logo retains the existing primary-role tint in both themes; its new transparent raster is 512px for up to 88dp at 4x density. Source artwork and generation provenance are retained locally. The cutout is a generated derivative of the approved reference, not a claim of byte-identical background removal.

Native validation: `docs/assets/generation/validate-brand-icons.py` passed.

Compose review: all 78 freshly recorded goldens were inspected in seven contact sheets under `build/brand-review`. Coverage retains all 16 primary screens in light/dark, RU/200% stress cases, expanded layouts and the existing risk/asset states. Reviewed primary actions, tonal card separation, selection indicators, form outlines, errors and the 88dp/64dp tinted logos. No new layout changes were introduced. Existing large-font navigation/tab wrapping remains visible; this palette refresh does not claim to fix or re-audit pre-existing layout behavior. `docs/design-review/brand-theme-preview.png` shows three actual production previews in both themes.

`BrandContrastTest` passed against the actual Material color schemes, including fixed roles and surface elevations. Minimum audited base text-pair ratios are 6.08:1 light and 5.36:1 dark (fixed roles additionally checked against 4.5:1). System/manual theme resolution remains covered by the existing test.

Checks completed:

- `:shared:compose:recordPaparazzi :androidApp:assembleDebug detekt`: Gradle exit 0; 140 Compose host tests, zero failures/errors/skips. The recording runner reported `workspace-changed` because approved goldens and documentation were intentionally written during this recording pass; it is not the final verification receipt. Log: `.vibe/receipts/RECEIPT-1ebb4f6b-2297-4c50-8555-c7f909956078.log`.
- Stable verification: `:shared:compose:verifyPaparazzi :androidApp:assembleRelease :shared:compose:compileKotlinIosSimulatorArm64`, Gradle and lean runner exit 0. All 78 goldens matched. Log and receipt: `.vibe/receipts/RECEIPT-8bd260dd-24f6-4f00-827c-b9fc72fe790f.{log,json}`.
- Both passes used `--no-configuration-cache --no-daemon`; the initial configuration-cache attempt failed on the documented Paparazzi/KSP incompatibility before execution.
- Asset manifest validation passed with no warnings; native size/alpha/mask validation passed. Debug and unsigned release APKs are under `androidApp/build/outputs/apk/`.

The existing iOS dependency graph warns about Coil's Skiko 0.144.6 resolving to Compose's 0.150.1. Kotlin compilation passed; dependencies were not changed. macOS runtime verification remains necessary and this warning is not declared resolved. This is a focused brand/theme verification, not a full-app acceptance or release-readiness claim.

Boundary: Windows cannot run Xcode, iOS Simulator or verify native iOS icon appearance. Asset-slot validation is not an iOS build/runtime pass.
