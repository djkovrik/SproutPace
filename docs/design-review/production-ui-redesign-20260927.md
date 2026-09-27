# Production UI redesign review — 2026-09-27

Status: Accepted

Blocking findings: none

## Evidence reviewed

- Approved product behavior: `spec/app-spec.json` and `spec/screens/SCREEN-001.md` through `SCREEN-016.md`.
- Research baseline: [Lazyweb cross-screen product UI research](https://www.lazyweb.com/agentic-search/0d8da7b5-4c89-4516-99bb-ce0e2b7d76ba).
- Visual evidence: all 78 tracked Paparazzi goldens, including light, dark, expanded, error/conflict/permission states, and RU at 200% font scale.
- Runtime evidence: `.vibe/scripts/run-android-ui-smoke.ps1` on a Pixel 8 AVD at 420 dpi. It verifies the accessibility tree, 48dp targets, keyboard focus and activation, notification permission denial/recovery, Android system-settings recovery, process restart, manual time entry, save completion, and persistence after restart.
- Shared system: `SproutPaceTypography`, `SproutPaceShapes`, Material color roles, adaptive navigation, and common action/form presentation in `shared/compose`.

## Screen-by-screen closure

| Screen | Review result | Final disposition |
|---|---|---|
| SCREEN-001 — First run | Accepted | Keeps the approved setup and catalog escape route; the supporting visual is calm and no technical identifiers are exposed. |
| SCREEN-002 — Today | Accepted | One clear next action, quieter supporting state, meaningful empty state, and stable Settings access. |
| SCREEN-003 — Trays | Accepted | Planned/Active/History remain peer tabs with adaptive list-detail behavior and category-specific empty states. |
| SCREEN-004 — Batch editor | Accepted | The approved staged form remains intact; fields, capacity feedback, and primary completion action share the common form system. |
| SCREEN-005 — Batch detail | Accepted | Stage progression stays dominant; expected/actual/skipped/fixed meaning is not color-only, and only lifecycle-applicable actions are exposed for planned, soaking, active, ready, and terminal states. |
| SCREEN-006 — Plan | Accepted | The linear rhythm and conflict model remain legible without low-value dashboard counters or a misleading calendar metaphor. |
| SCREEN-007 — Catalog | Accepted | Search is a first-class rounded Material field; image-led cards retain provenance, offline behavior, and adaptive columns. |
| SCREEN-008 — Recipe | Accepted | Stage content and provenance remain available without generic section labels or repeated summaries. |
| SCREEN-009 — Observation | Accepted | Date, note, and private photo actions remain; storage IDs and redundant helper cards are absent. |
| SCREEN-010 — Close batch | Accepted | Outcome selection is the focus and one Save outcome action replaces redundant outcome shortcut buttons. |
| SCREEN-011 — Settings | Accepted | Quiet grouped rows, readable switch labels, and clear privacy/data consequences hold at RU 200% scale. |
| SCREEN-012 — Zones | Accepted | Capacity and conflict meaning remain visible; record IDs are hidden and destructive actions use low-emphasis error treatment. |
| SCREEN-013 — Tray sizes | Accepted | Metric dimensions and snapshot-safe consequences remain clear without record IDs or generic headings. |
| SCREEN-014 — Backup/restore | Accepted | Action-led rows avoid duplicate full-width actions; restore metadata appears only when it changes the decision. |
| SCREEN-015 — Advertising privacy | Accepted | Neutral context and equally understandable consent choices are preserved; declining remains a complete path. |
| SCREEN-016 — Notifications | Accepted | Editable switches/time are shown once, permission recovery is explicit, and device smoke proves denial, settings recovery, manual input, save, and restart persistence. |

## Cross-screen conclusions

- The common typography and shape scale are applied at the Material theme boundary; screens do not define competing type systems.
- Primary, secondary, outlined, and destructive actions now communicate hierarchy consistently.
- Generic Items/Details/Actions headings, technical IDs, duplicate Backup actions, duplicate Close batch outcomes, and repeated notification summaries were removed.
- Batch Detail now derives its primary and secondary actions from the real batch state: start/confirm/skip/undo/overflow/close/repeat/mass actions appear only when their domain preconditions hold.
- Current goldens show no clipping or overlap in the reviewed compact, expanded, dark, and RU/200% states.
- The redesign changes presentation, not approved product scope or business rules.

## Release boundary

This review closes the product-design surface, not every external release gate. Publication is not requested in this delivery, so store publication/signing, production Firebase symbolication, production Yandex ad-device validation, and store/privacy disclosure publication are inactive until their required external projects, credentials, policy URL, and explicit publication request exist. Safe development defaults remain required by the project guide. macOS Simulator execution and `xcodebuild` remain CI/macOS-only evidence and must not be reported as locally passed on Windows.
