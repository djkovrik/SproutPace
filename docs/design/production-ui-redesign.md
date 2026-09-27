# Production UI redesign

Status: active lean delivery goal (2026-09-27).

## Outcome

Bring every approved SproutPace screen to one production-ready Material 3 system without changing product scope. The redesign must preserve the full behavior in `spec/app-spec.json`, EN/RU resources, offline-first operation, adaptive layouts, accessibility, deterministic previews, and Android/iOS Compose Multiplatform buildability.

## Evidence and direction

- Lazyweb screen research covers onboarding, task dashboards, segmented active/history lists, multi-step forms, lifecycle/timeline detail, planning, searchable catalogs, journal/photo forms, settings, capacity, dimensions, backup/restore, privacy consent, and notification recovery.
- Reusable patterns: a single dominant task, compact contextual summaries, tabbed peer categories, quiet grouped settings, full-width form actions, explicit empty states, and equal clarity for privacy choices.
- Rejected patterns: dashboards filled with low-value counters, card-on-card nesting, generic section labels, repeated summaries, long stacks of equal-prominence outlined buttons, and controls that expose storage IDs or raw timestamps.
- Material 3 baseline components are the cross-platform fallback. The repository uses Compose Multiplatform 1.12.0 and Material3 1.12.0-alpha03; experimental expressive APIs are not required for visual parity.

## Shared design contract

- Typography: one sans-serif EN/RU-safe Material role scale; semibold headlines/titles and controls; normal-weight body copy; no screen-local font sizes.
- Shape: 6/10/14/20/28dp theme scale. Large and extra-large shapes are reserved for meaningful cards, search, hero summaries, and dialogs.
- Color: forest/lime brand roles remain semantic. Primary marks the single highest-priority action. Tertiary containers communicate warnings; error roles are reserved for errors and destructive consequences.
- Surfaces: use whitespace and tonal hierarchy before borders or elevation. Do not wrap every section in a card.
- Forms: Material fields and selection controls, 48dp targets, inline errors, retained input, visible units, locale-aware dates/times, and no exposed technical identifiers.
- Actions: one filled primary action; tonal secondary actions; destructive actions use error-colored low-emphasis treatment and explicit confirmation where required.
- Navigation: four stable destinations (Today, Trays, Plan, Catalog), bottom navigation under 600dp and rail above it. One scaffold owns safe insets.
- Accessibility: headings describe real sections; state is never color-only; dynamic errors/progress are announced; all controls remain operable at 200% text scale and by keyboard/pointer where supported.
- Motion: restrained standard Material motion; no decorative loading or interaction-delaying transitions; reduced-motion behavior remains safe.

## Screen audit

| Screen | Spec-critical hierarchy | Redesign disposition |
|---|---|---|
| First run | Promise, optional setup, Continue/Browse catalog | Keep step flow; remove generic Items/Details/Actions framing and internal IDs. |
| Today | Next action, overdue, due, suggestion/conflict | Preserve hero; keep secondary work quieter and avoid dashboard counters. |
| Trays | Planned/Active/History peers, next action | Preserve tabs; maintain category-specific empty state and list-detail adaptation. |
| Batch editor | Recipe/tray, details, capacity, review | Preserve three-step form and safe bottom primary action. |
| Batch detail | Next action, ordered stages, assignment, observations | Use meaningful Stages heading; distinguish actual/expected/skipped/fixed without color alone. |
| Plan | Harvest rhythm, confirmed/suggested queue, conflict | Preserve linear timeline; avoid a calendar metaphor and reduce equal-weight action buttons. |
| Catalog | Search, bundled/personal cards, provenance | Treat search as a first-class rounded M3 field; keep offline/ad behavior and image-led cards. |
| Recipe | Guide/editor, stages, provenance | Use Stages rather than Items; retain source/version/review metadata without duplicate summaries. |
| Observation | Date, note, optional private photo | Remove repeated helper cards and technical observation ID; keep photo actions labeled. |
| Close batch | Outcome, date, optional details, consequence | Make outcome choice the focus; remove duplicated help and keep Save outcome singular. |
| Settings | Setup, preferences, privacy/data, reset | Current grouped list is the reference pattern; keep switches from squeezing text at 200%. |
| Zones | Name/type/capacity/occupancy/conflicts | Remove generic headings and record ID; keep add/edit/delete hierarchy and consequences. |
| Tray sizes | Shape/name/metric dimensions | Remove generic headings and record ID; show units and snapshot-safe delete consequence. |
| Backup/restore | Backup, preview restore, CSV, reset | Keep action-led rows; show preview metadata only when it changes the decision. |
| Advertising privacy | Neutral context, equal Accept/Decline, policy | Remove repeated context cards; keep actions equally clear and back non-consenting. |
| Notifications | Summary/soak switches, time, permission recovery | Remove repeated summary; keep immediate switches, locale time, and Today-as-source-of-truth copy. |

## Verification contract

- Keep light/dark previews for all primary screens and risk states.
- Keep RU at 200% font scale, long-authored text, empty/error/conflict/permission states, and materially distinct expanded layouts.
- Record Paparazzi goldens only after intentional review, then run verification.
- Run affected Compose tests while iterating. At a stable snapshot run applicable lean Gradle gates, report Windows/macOS boundaries, and obtain an independent acceptance audit before claiming global completion.

## Recovery note

Current milestone: research, implementation, and current-state visual review are complete. Shared typography and shape tokens now drive the theme; the generic layout no longer exposes technical IDs or meaningless Items/Details/Actions headings; action emphasis is consistent; search has a dedicated rounded field; repeated Backup actions, Close batch outcome shortcuts, and notification summary cards were removed. Batch Detail now exposes only lifecycle-applicable actions and routes its ready-state primary action to the Close batch form. The complete 78-image Paparazzi set was regenerated, with every screen family inspected across representative light, dark, expanded, and RU 200% states. The accepted review is recorded in `docs/design-review/production-ui-redesign-20260927.md`.

Verified on Windows: the full applicable lean Gradle matrix passed, including Detekt, Android host tests, Compose compilation, SQLDelight migration verification, Paparazzi verification, Android debug/release artifacts, iOS target compilation, and Kover verification. Asset validation passes. A Pixel 8 emulator smoke proves accessibility hierarchy, minimum targets, keyboard operation, permission denial/recovery, system settings recovery, manual time entry, save completion, and restart persistence. The iOS simulator and `xcodebuild` remain macOS/CI-only evidence.

Next action: refresh the stable receipts for the final source snapshot and obtain a fresh independent acceptance audit. Do not claim global release readiness without macOS/CI evidence for the remaining Apple-only gates.
