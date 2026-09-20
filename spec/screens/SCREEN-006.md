# SCREEN-006 — Plan

Linked flows: FLOW-001, FLOW-003, FLOW-005, FLOW-006, FLOW-008, FLOW-012  
Linked requirements: REQ-001, REQ-003, REQ-005, REQ-006, REQ-008, REQ-012

## Purpose and hierarchy

Show the linear sow-to-harvest queue, target harvest frequency, confirmed and suggested batches, zone capacity, and future conflicts without introducing a monthly calendar.

## States

- Content: chronological grouped timeline with capacity summary.
- Suggestions pending confirmation.
- Future conflict and `needs_resolution` states.
- Empty: invite cadence setup or first batch.
- Local loading/error; offline remains fully usable.

## Actions and iconography

Confirm suggestion, edit cadence, add batch, resolve conflict, change date/zone/capacity, and keep Needs resolution are labeled actions. Use standard timeline, date, capacity, add, accept, edit, and warning icons.

## Text layout expectations

Use localized dates and complete explanations such as why a suggestion exists or conflicts. Culture and sow/harvest window remain visible at 200%; dense capacity metadata wraps below the title.

## Responsive, accessibility, and insets

Compact is one vertical timeline with conflict sheet. Medium may add a supporting pane; expanded uses list-detail and capacity pane only when justified. Ordered events and conflict severity have semantic text; no drag-only rescheduling. Scaffold owns insets.

## Allowed ad slots

None.

## Asset requirements

Standard timeline, calendar/date, capacity, add, accept, edit, warning, and zone icons.

## Preview and golden matrix

Mixed confirmed/suggested timeline light/dark; empty; future conflict; needs resolution; RU 200%; long culture name; fixed date; expanded capacity pane; offline.
