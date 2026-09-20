# SCREEN-001 — First run

Linked flows: FLOW-001, FLOW-009, FLOW-012  
Linked requirements: REQ-001, REQ-009, REQ-012

## Purpose and hierarchy

Explain the promise, then offer a short optional setup for locale-aware notification time, one light zone, optional dark zone, and first tray preset. The primary action is `Continue`; `Browse catalog` skips setup without blocking later configuration.

## States

- Content: welcome, optional setup steps, and completion summary.
- Validation: inline capacity/dimension errors retain entered values.
- Permission denied: notification explanation offers retry/settings but permits completion.
- Loading, remote error, and offline: not applicable; all setup content is bundled/local.

## Actions and iconography

Use labeled buttons for Continue, Back, Skip setup, and Browse catalog. Use ASSET-001 as decorative brand art and standard arrow/notification icons only where accompanied by localized labels. Back never discards a completed prior step.

## Text layout expectations

The EN/RU promise may wrap to three lines. At 200% scale the step title, explanation, and actions stack vertically; no fixed-height copy containers. System date/time formats apply.

## Responsive, accessibility, and insets

Compact uses one step per page; expanded uses a supporting preview pane without adding steps. The scaffold owns safe areas. Step progress has text semantics, focus moves to the new heading, and every target is at least 48dp.

## Allowed ad slots

None.

## Asset requirements

ASSET-001; standard navigation and notification icons declared in `app-spec.json`.

## Preview and golden matrix

EN compact welcome light/dark; RU 200% setup; notification denied; invalid dimensions; expanded completion; setup-skipped path.
