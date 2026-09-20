# SproutPace agent guide

## Source of truth

- `spec/app-spec.json` is the approved product specification; `spec/` and `docs/` provide supporting detail.
- Do not repeat product discovery or change product decisions unless the specification is internally contradictory.
- Keep work inside the approved scope. Preserve all existing user changes and never discard unrelated work.

## Project constraints

- This is a Kotlin Multiplatform application. Android is the publication target; iOS must remain fully implemented and buildable on macOS/CI.
- Keep shared business rules in `shared/domain`, persistence in `shared/data`, platform APIs behind common contracts with Android/iOS implementations, and product UI in `shared/compose`.
- All user-visible text must use production EN/RU resources. Built-in catalog records use stable language-neutral IDs and localization keys.
- The bundled catalog is local, versioned, source-attributed, and dated. It needs no external approval workflow.
- iOS advertising stays safely disabled until an iOS ad project and ad unit are explicitly supplied.
- Missing Firebase, Yandex Ads, signing, or privacy-policy values must not block development. Use safe defaults and the templates under `docs/configuration/`; never commit real secrets.
- Do not publish the app or create external Firebase, advertising, or store projects without an explicit request.

## Working rules

- Prefer `rg` for discovery and `apply_patch` for edits.
- For UI changes, preserve accessibility, adaptive layouts, Compose previews, EN/RU coverage, and Paparazzi goldens. Use Lazyweb evidence before material product-UI changes when available.
- Add or update tests with behavior changes. Do not weaken tests, Detekt, coverage, or quality gates to make a check pass.
- Treat generated goldens as reviewed artifacts: record them only for intentional UI changes, inspect them, then run verification.

## Verification

- Run the smallest relevant tests while iterating.
- Before handoff, run `powershell -NoProfile -ExecutionPolicy Bypass -File .vibe/scripts/run-final-local-gates.ps1` when the delivery harness is present.
- Otherwise run the applicable Gradle checks, including Detekt, Android host tests, Compose compilation, SQLDelight migration verification, Paparazzi verification, Android debug/release builds, iOS target compilation, and Kover verification.
- Windows cannot prove iOS Simulator execution or `xcodebuild`. Report that boundary explicitly and rely on the macOS CI job; never describe unexecuted iOS gates as passed.
- Do not claim completion while an applicable acceptance criterion or quality gate remains unverified.
