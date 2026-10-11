# Illustration refresh — 2026-10-11

Focused maintenance ASSET-016..021 complete: six user-approved PNGs copied byte-for-byte; XML alternatives removed; three production consumers use painterResource with unchanged sizes and semantics. Added tall dark recipe preview.

Reviewed 59 golden deltas in both themes, RU 200% and adaptive contexts. Asset validation passed. Final Gradle execution passed: Detekt, 384 Compose host tests including 232 preview cases, Paparazzi verification, Android/iOS target compilation and Android debug/release builds. Receipt 2c040017-1c50-4a0c-a821-4b2eb60c8df3 records successful Gradle execution; wrapper marked workspace-changed because evidence documentation was refreshed during that run. Independent bounded visual review found no illustration defects.

Next action: macOS CI for iOS Simulator/Xcode before global release acceptance. Existing Skiko/Coil version warning remains outside this asset-only change.
