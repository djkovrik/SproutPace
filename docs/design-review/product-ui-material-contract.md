# SproutPace product UI implementation contract

## Evidence and scope

This implementation applies the approved AppSpec 2.0 without changing product scope. Pre-design evidence was already gathered through Lazyweb and recorded in `spec/design.md` and the delivery plan. The stable implementation research report is [Lazyweb Agentic Search](https://www.lazyweb.com/agentic-search/6e37f378-12e9-4857-bb35-8293331d48eb); the advertising-placement evidence remains the report linked from `spec/design.md`.

Patterns applied from that evidence:

- Today presents one unmistakable next action, then overdue and due work, with a calm useful empty state.
- Trays uses short peer tabs and a compact scannable list; expanded widths retain selection in a list-detail arrangement.
- Plan is a linear domain timeline with explicit capacity conflicts rather than a generic monthly calendar.
- Catalog is image-led and reflows from a compact feed to list-detail.
- Navigation stays at four stable destinations. Settings remains a top-app-bar action.
- The implementation excludes streaks, diagnostic claims, subscription/commerce UI, promotional modals, and other non-goals.

The root-owned post-golden Lazyweb review was completed against rendered Paparazzi output. The broad report is stored at `.lazyweb/design-improve/sproutpace-core-2026-09-20/report.html` with supporting evidence at [Lazyweb Agentic Search](https://www.lazyweb.com/agentic-search/b33bd652-1b1a-4279-ae3c-07cb61358220). Scope-safe findings were applied: realistic screen-specific fixtures, task/content-first ordering, progressive disclosure of contextual command inputs, one filled safe primary action, explicit destructive styling, and facts/actions before editing mechanics.

A final focused review of the recorded Today empty state is stored at `.lazyweb/design-improve/sproutpace-final-2026-09-20/report.html`, with the [hosted Lazyweb report](https://www.lazyweb.com/report/lazyweb/638f6950-0f2c-40f2-b379-af92e1700f23/?source=create) and [supporting references](https://www.lazyweb.com/agentic-search/695cb246-c2bb-43d7-b499-c1eca8617a57). It exposed a real AppSpec mismatch rather than a stylistic preference: the preview used generic copy and equal-weight contextual commands instead of the screen-specific calm next step. The accepted correction uses the existing Today/Trays/Plan empty resources, makes the snapshot primary action the only filled empty-state CTA, and removes inapplicable fields, facts, and contextual commands. The suggested 40% illustration reduction was rejected because ASSET-021 has an approved 240 × 160 dp contract and the compact golden keeps the CTA visible.

## Material 3 and adaptive contract

The repository's common Material 3 baseline is used because the newer expressive navigation, list, button-group, and motion APIs are not assumed stable and target-compatible across this project's Android/iOS Compose version. The semantic fallback preserves canonical `Scaffold`, top app bar, navigation bar/rail, tabs/filter chips, cards, buttons, text fields, switches, radio buttons, progress, and dialogs/message states. No visual imitation of an unavailable expressive component is added.

| Screens | Canonical layout | Compact | Expanded | Primary component families |
| --- | --- | --- | --- | --- |
| SCREEN-001 | Supporting pane | Single setup flow | Setup + cycle preview | Form, labeled buttons |
| SCREEN-002 | Feed | Bottom navigation, one feed | Rail + capacity pane | Hero card, grouped actions, FAB-equivalent labeled action |
| SCREEN-003 | List-detail | Tabs then list | Rail + persistent detail | Filter chips as available baseline peer selector, list cards |
| SCREEN-004 | Form/supporting | Scrolling form | Form + summary | Text fields, filled save button |
| SCREEN-005 | Supporting pane | Ordered timeline | Timeline + snapshot | Semantic list, stage illustrations, buttons |
| SCREEN-006 | List-detail/supporting | Linear timeline | Timeline + capacity pane | Cards, timeline, conflict surface |
| SCREEN-007 | Feed/grid | Adaptive grid | Grid + source/detail pane | Search field, single-target cards |
| SCREEN-008 | Supporting/form | Stacked stages | Stages + provenance | Cards, labeled actions |
| SCREEN-009 | Form | Form | Form + photo guidance | Text fields, system-picker action |
| SCREEN-010 | Form | Full-height form | Bounded form + consequences | Radio group, fields, filled save |
| SCREEN-011 | List-detail | Grouped list | List + selected detail | Settings rows, switch, destructive text action |
| SCREEN-012 | List-detail | List-to-editor callback | List + editor guidance | Cards, warnings, add action |
| SCREEN-013 | List-detail | List-to-editor callback | List + editor guidance | Cards, explicit shapes and units |
| SCREEN-014 | Supporting pane | Action list / full preview | Actions + preview | Progress, warning, file actions |
| SCREEN-015 | Supporting decision | Full-screen decision | Bounded decision + context | Equal outlined accept/decline actions |
| SCREEN-016 | Bounded settings | Single column | Bounded single column | Switches, time field, permission recovery |

The scaffold owns safe drawing insets once. Width below 600dp uses bottom navigation; width 600dp and above uses a rail for primary destinations; width 840dp and above enables the declared second pane for model-heavy screens. Five width classes (compact, medium, expanded, large, extra-large) are propagated to the typed component contract.

## Tokens, semantics, and localization

- Static botanical green schemes with warm neutral surfaces and restrained gold/soil tertiary roles provide deterministic Android/iOS light and dark rendering.
- Every resource-backed `TextKey` resolves through a strict `Res.allStringResources` lookup; an unknown production key fails immediately instead of leaking an identifier to the user. User-authored item/fact values remain verbatim. Default English and Russian resource sets have identical keys, and system locale is the only selector.
- Canonical controls retain their built-in roles. Full switch/radio/checkbox rows use `Role.Switch`/`Role.RadioButton`/`Role.Checkbox`; validation messages expose error semantics, selection and boolean values expose state descriptions, headings carry heading semantics, and icon-only back/settings actions have localized content descriptions.
- Custom and text actions have a minimum 48dp target. Reading order follows source order, tabs remain individually focusable, no action depends on swipe/drag/long press, and dates/time fields remain manually operable.
- Long content reflows vertically. Typed sibling component previews cover all 16 primary content screens in light/dark, all 16 in RU at 200% font scale in the declared test matrix, expanded high-risk list/detail surfaces, and permission denial, empty, validation, capacity conflict, temporarily unassigned, error, restore preview, privacy decision, and destructive confirmation states.
- Culture covers and stage vectors are decorative where adjacent text gives their meaning. All eight approved catalog covers and all five stage illustrations are consumed by typed `Res.drawable` accessors.

## Integration boundary

`SproutPaceUiState`, `UiScreen`, `UiVariant`, and `UiAction` form the stateless adapter boundary. The enum values exactly mirror the 16 stable AppSpec `ScreenId` names. `ScreenModel.toUiState()` maps stable item IDs, authored text, resource keys, selection, typed field kind/value/options/read-only/required state, facts, action descriptors, validation errors, permission state, conflicts, ads, busy/offline state, and unsaved changes without reconstructing domain commands.

`RootActionRouter` sends edit, option, submit, cancel, retry, selection, and layout events back as typed `ScreenAction` values. Root navigation uses an explicit action-to-screen table. File/photo pickers, notification permission, system settings, and privacy-policy launch use `PlatformActionRequest` through an injected `PlatformActionHandler`; its default is a safe no-op until the platform-host package wires native behavior. Production rendering contains no `sample_*` key path.

On task-oriented Today, Trays, Batch detail, and Plan surfaces, contextual command inputs remain fully mapped but start collapsed behind a 48dp labeled disclosure control; validation automatically reveals them. Editor/settings surfaces keep their fields visible. Each surface has at most one filled primary action, secondary actions are outlined, and destructive actions carry error color plus a localized destructive state description.
# Runtime theme contract

The retained production graph exposes the persisted `SYSTEM`, `LIGHT`, or `DARK` preference to the
shared Compose root. `SYSTEM` resolves against the host appearance on every relevant recomposition;
the explicit choices override it. The already-existing light/dark primary-screen previews remain
the deterministic source for Paparazzi recording and post-golden review.
