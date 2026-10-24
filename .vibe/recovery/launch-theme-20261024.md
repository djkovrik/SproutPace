# Launch theme — 2026-10-24

Focused maintenance: fix the dark native launch window during the first launch on a light Android system. The manifest previously inherited Theme.Material.NoActionBar unconditionally. Native light/night themes now use the existing Compose surface colors (#FAF8F0 / #101710), including Android 12+ splash backgrounds and system bar appearance. Disable automatic Force Dark. Preserve the existing launcher artwork and launch timing.

The host theme callback now runs inside MaterialTheme. Android applies its resolved background to the underlying window alongside system bar icon appearance, including explicit in-app theme choices. The system starting window follows the host system mode; this change does not add persisted per-app Android night-mode overrides.

Lazyweb queries: `light dark splash screen`, `minimal splash screen` (mobile). References show the established centered-logo/single-background structure; preserve it. Static screenshot references do not establish system-theme behavior. Implementation follows Android's launch and dark-theme contracts: https://developer.android.com/develop/ui/views/launch/splash-screen and https://developer.android.com/develop/ui/views/theming/darktheme . A bounded platform correction needs no new hosted design report.

Regression before fix: LaunchThemeContractTest failed both tests (forced-dark manifest and missing palette variants), RECEIPT-542344b2-c45d-481f-aca3-b097659088a6. The test covers manifest wiring, qualified platform parents, window/splash background resources, bar contrast, Force Dark and equality with the real Compose palette.

Affected matrix PASSED in RECEIPT-3e0849f9-3dcf-4bfb-8287-3535468a4d47: Android debug/release builds, Detekt, all 474 Compose host tests, Paparazzi verification (existing goldens unchanged), and iOS Arm64/Simulator Arm64 production compilation. After the bounded native-resource follow-up preserving dark navigation bars for API 24/25 light-only buttons, RECEIPT-b60eae15-af4d-44d4-85ec-6234f99fb8bd PASSED all 7 Android app unit tests, debug/release builds and Detekt. Shared sources did not change after the matrix. git diff --check passed.

Android API 37 emulator cold launches with no saved theme override: captured and visually inspected both native splash and settled real app in light/dark modes. Exact edge colors stay #FAF8F0 / #101710 through the handoff; system icons have contrasting appearance. Evidence: build/reports/launch-theme/result.json, light-01.png + light-06.png, dark-01.png + dark-05.png. Capture script restored the initial system mode and did not clear application data. The launch icon artwork is unchanged, including its light adaptive-icon badge in dark mode.

Next action: report this focused fix complete. This does not close existing full-app acceptance gaps or establish release readiness; Windows cannot verify iOS runtime/Xcode. Existing Coil/Skiko compatibility warnings remain unchanged; no dependency was upgraded.
