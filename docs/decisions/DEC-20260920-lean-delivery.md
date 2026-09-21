# Process decision: lean delivery — 2026-09-20

## User authorization

> Хорошо, давай внесем правки по всем предложенным пунктам. Также если нужно сразу обнови AGENTS.md и spec в самом приложении d:\Sources\Android\SproutPace

This approves the workflow changes proposed in `D:/Sources/vibe-skills/docs/SPROUTPACE-WORKFLOW-RESEARCH-2026-09-20.md`.

## Decision

Use the Vibe lean process for subsequent SproutPace work: one scenario owner across layers, direct serialized checks, compact recovery notes and a fresh independent final auditor. The independent auditor can perform delta review after bounded fixes. Strict assignment/handoff/binding metadata is historical, not required for new work.

Preserve the original strict ledger and archive it without importing verification claims. `.vibe/work.json` is the current process state. Existing receipts remain historical unless their inputs are proven current. This transition is not an implementation or release PASS.

All 79 acceptance scenarios, required surfaces, 16 screens and 12 quality gates retain their approved product meaning. No existing test, golden, functional requirement or implementation is removed. Proportional architecture allows parent-state projections, direct injectable services and stable module boundaries for future changes; do not rewrite working Stores or modules solely for this convention.

For future visual additions, primary-screen light/dark coverage and distinct risk states are mandatory; stress combinations may be pairwise. Preserve already explicit screen-level requirements and existing accepted coverage. Shared M3 decisions and representative Lazyweb screen-family evidence avoid duplicate reports; inspect every affected screen locally.

Keep existing blocking lint/coverage configuration unchanged. For new projects the skills default to correctness-focused blocking lint with advisory style/complexity. Toolchain upgrades are separate work; reuse SproutPace's pinned versions.

## Verification boundary

Process/spec validation does not revalidate application behavior. A future completeness claim needs current checks and independent acceptance. Windows cannot verify iOS runtime/Xcode; the missing native proof remains visible. External publication conditions remain conditional and are never inferred as active authorization to publish.
