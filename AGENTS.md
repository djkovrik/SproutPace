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

## Delivery mode

- Use `vibe-developer` lean delivery. `.vibe/work.json` is authoritative; `.vibe/delivery-ledger.json` and its archived copy retain strict-mode history only.
- Read `docs/decisions/DEC-20260920-lean-delivery.md` for the approved transition. Keep all acceptance scenarios and required checks; historical statuses are not current evidence.
- One owner delivers a complete scenario across domain, data, Store/component and UI, applying relevant skills directly. Delegate only a useful independent bounded task, with a compact packet and no full history by default.
- Keep one recovery note and next action. Do not create capability assignments, immutable handoffs or receipt bindings for lean work.
- A retained Store owns independent mutable/async state. Read-only child projections may use the parent owner; a separate Manager is optional for a forwarding-only injectable service. Group modules by stable dependencies. Preserve working code unless the actual task needs a change.
- Reuse pinned tested tooling. Do not upgrade dependencies as an automatic part of feature work.

## Working rules

- Prefer `rg` for discovery and `apply_patch` for edits.
- For UI changes, preserve accessibility, adaptive layouts, Compose previews, EN/RU coverage, and Paparazzi goldens. Use Lazyweb evidence before material product-UI changes when available.
- Add or update tests with behavior changes. Do not weaken tests, Detekt, coverage, or quality gates to make a check pass.
- Treat generated goldens as reviewed artifacts: record them only for intentional UI changes, inspect them, then run verification.

## Verification

- Run the smallest relevant tests while iterating.
- Run the final applicable matrix once at a stable integration snapshot, not before each handoff. The existing `.vibe/scripts/run-final-local-gates.ps1` is a legacy strict runner: do not use it in lean mode because it binds the historical ledger. Use `lean-delivery.py <repo> check --runner gradle -- <tasks>` for its applicable Gradle tasks and command checks directly.
- The final matrix includes the applicable Gradle checks: Detekt, Android host tests, Compose compilation, SQLDelight migration verification, Paparazzi verification, Android debug/release builds, iOS target compilation, and Kover verification.
- Windows cannot prove iOS Simulator execution or `xcodebuild`. Report that boundary explicitly and rely on the macOS CI job; never describe unexecuted iOS gates as passed.
- Obtain a fresh independent acceptance audit after the final matrix; known gaps may be audited and must prevent a false PASS. After bounded fixes, the same independent auditor may review the delta and previous findings. Broader architecture/spec changes require a fresh audit.
- Report locally verified, implementation complete and release ready separately. Do not claim global completion while an applicable criterion or quality gate remains unverified. No mandatory build rerun after an audit that changed no source.
- A focused maintenance change uses affected tests and a concise report; it does not require new full-app acceptance unless global completion is claimed.
