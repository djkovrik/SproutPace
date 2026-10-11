# CI pipeline maintenance — 2026-10-11

Scope: focused repair of https://github.com/djkovrik/SproutPace/actions/runs/38120805727. No product UI or golden changes.

- Android gates passed; Paparazzi failed 71 snapshots. Inspected CI deltas show device-zone timestamps shifted by three hours (reviewed Moscow goldens versus UTC runner). Compose host-test JVM now pins `user.timezone=Europe/Moscow`; production device-zone behavior remains unchanged.
- Kotlin iOS compilation, simulator tests and framework linkage passed. Xcode host build requested unconfigured `ios_x64` for its generic simulator destination. Debug/Release simulator build settings now exclude `x86_64`, matching the existing ARM64 Kotlin targets.
- Verified through lean Gradle runner with `--no-configuration-cache -Duser.timezone=UTC :shared:compose:verifyPaparazzi`: 383 tests, 231 snapshots, zero failures/skips. Receipt: `.vibe/receipts/RECEIPT-ab3aa966-a233-42ce-919c-f9c148b0ce9f.json`. `git diff --check` passed.

Follow-up run 38122250962: Android job passed, including Paparazzi. Xcode successfully embedded the ARM64 framework, then failed at `iosApp.swift:579` because Swift could not find `IosCrashReportingBridge`. The protocol lives in `shared:platform`, previously an implementation dependency without framework export. Added `api(project(":shared:platform"))` and explicit framework `export(project(":shared:platform"))`. Both iOS target compilations and Android Compose compilation passed via lean runner; receipt `.vibe/receipts/RECEIPT-a6978282-2687-4854-b2f2-5dc2b37c37af.json`.

Follow-up run 38123269627: Android and Kotlin iOS gates passed; framework export now exposes `IosCrashReportingBridge` to Swift. Xcode diagnosed one conformance error at `iosApp.swift:588`: generated requirement is `func setCollectionEnabled(enabled_ enabled: Bool) -> Bool`, while the host used `enabled:`. Changed the external argument label to match the compiler-reported protocol; the local parameter and method body are unchanged. This Swift-only fix requires macOS CI; rerunning Kotlin/Android compilation would not verify it.

Follow-up run 38124175263: Android and Kotlin iOS gates passed; Swift compilation now passed. Host linkage failed on 30 undefined symbols, all `sqlite3_*` from SQLiter/SQLDelight in the static `compose` framework. Added `$(inherited)` and `-lsqlite3` to the target's Debug and Release `OTHER_LDFLAGS`, as required by SQLDelight's static-framework integration documentation. This Xcode-only fix requires macOS CI; Kotlin/Android reruns cannot verify host linkage.

Next action: push the SQLite host-linker fix to PR #1 and confirm the unsigned Xcode host build in macOS CI. Windows cannot execute Xcode; no new full-app acceptance or release-readiness claim is made.
