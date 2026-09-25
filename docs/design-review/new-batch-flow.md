# New batch flow — Material 3 contract

Scope: `SCREEN-004`, `AC-026`, `AC-030`, `AC-032`, `AC-056`, and `AC-083`.

## User task and hierarchy

The editor is a three-step, reversible flow:

1. **Basics** — choose one recipe or catalog culture and a tray. The selected culture is reinforced with image, label, and radio state rather than color alone.
2. **Details** — confirm substrate, seed mass, sowing date, and zone. Supplier, cultivar, lot, fixed dates, time zone, and correction history remain in an explicit advanced section.
3. **Review** — scan the values that affect the plan, then perform exactly one commit action: Save for a new batch or Correct for an existing batch.

The scrollable content never owns the primary action. A tonal bottom action surface remains visible while step content scrolls independently.

## Material 3 component map

- Page context: existing `TopAppBar` and Decompose back action.
- Progress: determinate `LinearProgressIndicator` plus localized “Step n of 3” text.
- Single selection: full-row `RadioButton` controls from the existing field renderer.
- Structured input: existing outlined text fields with required/optional/error supporting text.
- Advanced disclosure: outlined button, followed by the same canonical fields when expanded.
- Commit: one filled button; navigation uses outlined/text emphasis.
- Review values: low-emphasis `Surface` containers, not another editable form or duplicate preview.

Baseline Material 3 is the cross-platform implementation. The repository uses Compose Multiplatform `1.12.0` and Material3 `1.12.0-alpha03`; no additional experimental expressive API or dependency change is introduced.

## Adaptive, accessibility, and localization rules

- Compact and medium: one bounded form pane with 16 dp margins.
- Expanded and larger: the same supporting task is centered and capped at 840 dp rather than stretched; no unrelated list pane competes with the form.
- App scaffold owns safe drawing insets; the editor consumes scaffold padding once.
- Every action is at least 48 dp high. At 200% font scale, paired navigation buttons stack vertically.
- Headings and progress state are announced; native M3 roles/state semantics remain intact.
- Selection uses radio state and text in addition to color. Culture imagery is decorative.
- All new copy is present in production EN/RU resources. Labels and buttons wrap rather than truncate at the required stress scale.

## Preview and verification matrix

- Compact light and dark content.
- Compact RU at 200% font scale.
- Validation error routing to the step containing the first invalid field.
- New-batch Save mode and existing-batch Correct mode.
- Compact width is the reviewed golden surface; expanded behavior is covered by the shared breakpoint implementation and Android/iOS compilation.
