# Design

## Lazyweb evidence and direction

Lazyweb was consulted before screen selection.

Queries:

- `daily task dashboard` — strong adjacent evidence for Today hierarchy: current work first, concise status, stable bottom navigation, meaningful empty state.
- `plant growth tracker` — weak coverage; do not copy gamified “growth garden” or streak patterns.
- `harvest schedule planner` — adjacent planning evidence only; SproutPace uses a domain timeline rather than a generic calendar.
- `crop catalog cards` — weak coverage; use Material feed/grid semantics rather than treating results as validated microgreens patterns.
- Advertising research found no matching measured experiment. The qualitative [Agentic Search](https://www.lazyweb.com/agentic-search/2c55a632-370e-4a01-9e89-a6a5198460e8) supports clearly labeled inline browse/history placement and avoiding blocking promotional modals.

After implementation goldens exist, run first-release Lazyweb review over every primary screen and applicable light/dark/stress state. The post-golden report, not this preliminary search, is the implementation review evidence.

## Product expression

Use a calm botanical Material 3 system:

Brand update approved 2026-09-23: logo 1B-1 and palette A (Forest & Lime).
Light actions use forest green, dark actions use lime with dark-green content;
surface elevations, inverse colors and outlines use botanical neutrals. Native
icons preserve the full-color logo; in-app decorative marks retain semantic tint.
See `../docs/design-review/brand-refresh-20260923.md` for the approved mapping.

- warm neutral surfaces;
- green primary roles;
- restrained soil/gold tertiary accents;
- rounded, shallow-tray silhouettes and organic but readable illustration shapes;
- one hero moment on Today: the most important next action and capacity context;
- no streaks, mascots, confetti loops, diagnostic imagery, or novelty controls.

Prefer M3 Expressive variants when the repository's Compose Multiplatform version provides stable/accepted Android and iOS APIs. Otherwise use the semantically equivalent baseline Material 3 component and record the fallback. Do not custom-draw a missing expressive control merely to imitate its appearance.

## Navigation and adaptive layout

Primary destinations are stable:

1. Today
2. Trays
3. Plan
4. Catalog

Settings is a top-app-bar action, not a fifth primary destination.

| Width | Navigation | Content adaptation |
| --- | --- | --- |
| Compact `<600dp` | Flexible/baseline navigation bar with icon + label | One pane; details navigate forward; 16dp semantic outer margin |
| Medium `600–839dp` | Collapsed navigation rail | One pane by default; optional supporting pane for Plan conflicts |
| Expanded `840–1199dp` | Collapsed/expanded navigation rail | List-detail for Trays, Catalog, Zones, and presets |
| Large `1200–1599dp` | Expanded navigation rail | Fixed list + flexible detail; Plan may show capacity supporting pane |
| Extra-large `>=1600dp` | Expanded navigation rail | Two panes by default; third pane only for a justified Plan conflict detail |

Each screen has one safe-area owner. Scaffold bars/rails own their corresponding inset; content consumes scaffold padding exactly once. Android predictive back and iOS back behavior map to the same platform-neutral navigation outputs.

## Screen inventory and hierarchy

| Screen | Primary task | Canonical layout | Highest-priority action |
| --- | --- | --- | --- |
| SCREEN-001 First run | Understand value or configure first cycle | Supporting pane / compact step flow | Continue setup or browse catalog |
| SCREEN-002 Today | Act on current and overdue work | Feed | Complete/open next action; New batch FAB when appropriate |
| SCREEN-003 Trays | Browse planned, active, and history | List-detail | New batch; open selected batch |
| SCREEN-004 Batch editor | Create/correct a batch | Form/supporting pane | Save/confirm batch |
| SCREEN-005 Batch detail | Understand state and record reality | Supporting pane | Confirm next actual stage |
| SCREEN-006 Plan | Edit cadence and resolve future conflicts | List-detail/supporting pane | Confirm suggestion or resolve conflict |
| SCREEN-007 Catalog | Browse built-in and personal recipes | Feed/grid | Open recipe; Create personal recipe |
| SCREEN-008 Recipe detail/editor | Read or edit recipe and sources | Supporting pane/form | Use recipe or Save personal recipe |
| SCREEN-009 Observation editor | Add/edit dated note and photo | Form | Save observation |
| SCREEN-010 Close batch | Record terminal outcome | Form/dialog on compact | Save outcome |
| SCREEN-011 Settings | Control product preferences/privacy/data | List | Contextual setting action |
| SCREEN-012 Zones | Manage capacity | List-detail | Add zone |
| SCREEN-013 Tray sizes | Manage reusable metric presets | List-detail | Add preset |
| SCREEN-014 Backup and restore | Export, preview, replace | List/supporting pane | Create backup or confirm restore |
| SCREEN-015 Advertising privacy | Make or change applicable consent | Full-screen decision on compact | Accept or decline; neither is visually deceptive |
| SCREEN-016 Notifications | Configure summary and permission recovery | Form/list | Enable/disable or change time |

## Material component map

- App framing: `Scaffold`, flexible/baseline top app bars, flexible/baseline navigation bar, collapsed/expanded navigation rail.
- Collections: standard/expressive lists, cards only where one subject and its actions form a coherent object; grids for catalog at wider sizes.
- Primary creation: one labeled extended FAB on Today/Trays when available; remove rather than disable when context makes it invalid.
- Actions: filled/tonal/outlined/text buttons by prominence; icon buttons only for familiar actions with localized semantics.
- Peer categories: primary tabs for Planned / Active / History within Trays. Tabs never represent sequential setup steps.
- Selection: radio buttons or connected button groups for zone type, tray shape, plan mode, and outcome; switches only for independent immediate settings such as Crashlytics and daily summary.
- Inputs: Material text fields with units/supporting text, date/time pickers with manual entry, and system photo/document pickers.
- Temporary decisions: basic dialog for irreversible delete/reset; bottom/side sheet for compact conflict explanation; persistent supporting pane at expanded widths.
- Feedback: actionable snackbars do not auto-dismiss; dialogs are reserved for decisions, not passive success.
- Progress/loading: show only for real database/file/network work. Core local screens prefer stable content/skeleton boundaries over decorative spinners.

## Today hierarchy

1. Page title and Settings action.
2. Hero next-action surface: action, tray/culture, due state, and one primary completion/open action.
3. Overdue actions.
4. Due today.
5. Next sow/harvest suggestion and capacity warning.
6. Calm empty state when no work exists; never replace it with a streak.

Completing a task provides an undo/review path where semantics permit. State is communicated with text/icon/shape as well as color.

## Stage and capacity presentation

Batch detail uses a vertical stage timeline with actual events, predicted windows, skipped markers, and the next action. Stage illustrations are decorative support and never diagnostic evidence.

Capacity is shown as named zones and occupied/available counts. Future conflicts use warning roles plus explicit text and actions: choose another date, choose/add a zone, change capacity, or retain `needs_resolution`. Actual overflow uses the same language but includes `Temporarily unassigned` so a user can record reality.

## Ads and privacy UI

Allowed slots only:

- SCREEN-007: one clearly labeled inline ad after the fourth culture card.
- SCREEN-003 History tab: one clearly labeled inline ad after the fifth historical record.

At most one ad is visible per eligible screen visit. No app-forced refresh loop is required. Ads are absent from short lists, empty/loading/error states, first run, Today, active batch detail, editors, stage confirmation, completion, planning decisions, settings, permissions, backup, and privacy.

No fill, offline, missing/stale eligibility, declined/unresolved consent, missing ID, SDK error, or iOS no-ID collapses the slot without blocking or leaving a misleading placeholder.

The app-owned protected-region consent screen appears at the first ad-eligible moment only when a fresh endpoint response requires it. It provides accept, decline, and privacy-policy actions with equal clarity. Non-protected responses show no global ad-consent popup or opt-out. Settings exposes withdrawal only while fresh applicability requires consent.

## Theme and tokens

- Default theme policy is system. Settings options are System, Light, and Dark.
- Use semantic color roles and correct `on*` pairings; define a complete accessible warning/success role family if Material roles are insufficient.
- Use Material typography roles and production font glyph coverage for Latin/Cyrillic.
- Use Material spacing tokens based on the 8dp system and minimum 48×48dp targets.
- Prefer tonal surface hierarchy over heavy shadows; keep elevation levels few.
- Use one app-level Material motion scheme. Motion explains selection, expansion, and stage change, never delays task completion.
- Reduced motion replaces translation/morphs with subtle opacity or immediate state changes.

## Typography and text layout

- All bundled copy, errors, accessibility labels, catalog text, and preview text resolves from production EN/RU resources with shared keys.
- User names and notes wrap as data; do not attempt to localize them.
- Titles may use two lines at compact widths before truncation. Critical culture/batch names remain visible; secondary metadata may ellipsize only after its most useful prefix.
- Buttons prefer short sentence-case labels. At 200% font scale, buttons and rows grow/reflow instead of clipping or hiding an action.
- Dates/times use system locale conventions; metric values keep explicit units.
- Never concatenate translated sentence fragments.

## Accessibility

- Meaningful headings and logical reading/focus order.
- Screen-reader action/state names for every control and dynamic status announcement for completed/rescheduled/conflict actions.
- Important state uses at least two cues; capacity, skipped stage, overdue status, selection, and errors cannot rely only on color.
- Minimum 48dp interactive targets, visible keyboard focus, pointer/keyboard alternatives to swipe/drag/long press, and accessible manual date/time input.
- Decorative logos/culture/stage art has null semantics when adjacent copy already carries meaning.
- No time-limited destructive interaction and no auto-dismiss for an actionable snackbar.

## Asset direction

ASSET-001 is the original sprout-over-tray mark. ASSET-016 through ASSET-021 are original vectors. ASSET-022 through ASSET-029 are generated editorial culture covers at the declared size. They must be botanically plausible at microgreen scale, must not include localized text, symptoms, diagnosis claims, logos, or copied public photography, and require visual/content review before delivery.

Standard actions use one Material Symbols Rounded family and the exact resources in `assetRequirements`. Platform launcher/store derivatives follow Android/iOS mask, background, and export rules and are recorded in the delivery manifest.

## Screen state policy

Every screen file decides loading/content/empty/error/offline/permission applicability. General rules:

- Local content never shows a false network-offline error.
- Catalog is bundled and available offline.
- Ads disappear when network/privacy is unavailable.
- Backup/restore shows determinate progress only when measurable and never leaves partial success.
- Permission denial keeps the relevant product action available with explanation and system-settings recovery where appropriate.
- Empty states contain one relevant next action, not marketing filler.

## Preview and golden contract

Every primary screen has deterministic content and applicable empty/error/permission/conflict previews in light and dark. Risk matrix includes:

- EN 100% compact phone;
- RU 200% compact phone using production strings/font;
- expanded-width list-detail layout;
- long user-authored names;
- permission denied;
- offline ad-hidden state;
- capacity conflict/unassigned state;
- destructive confirmation;
- restoration preview/error;
- protected consent and no-consent-needed states.

ComposablePreviewScanner must discover production component-owned preview implementations and generate stable Paparazzi IDs. Record, inspect, approve, then verify goldens. First-release Lazyweb review covers all primary screens after approved goldens.
