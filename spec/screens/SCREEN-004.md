# SCREEN-004 — Batch editor

Linked flows: FLOW-002, FLOW-004, FLOW-005, FLOW-008  
Linked requirements: REQ-002, REQ-004, REQ-005, REQ-008

## Purpose and hierarchy

Create, edit, or repeat a planned batch from a recipe snapshot, tray preset, substrate, planned sow date, and optional fixed dates. Show capacity impact before save.

## States

- Create/edit/repeat content with locally loaded choices.
- Validation: missing recipe, invalid dates, deleted preset, and capacity conflict are inline and actionable.
- Conflict: permit changing date/zone/capacity or saving `needs_resolution` where allowed.
- Loading/error: local save only; inputs remain recoverable.
- Offline: fully available.

## Actions and iconography

Save/Confirm is the sole highest-priority action; Cancel/Back requires discard confirmation only for unsaved changes. Recipe, tray, zone, substrate, date, and conflict selectors use standard labeled Material controls and icons.

## Text layout expectations

Units are explicit (`cm`); dates use locale conventions. Fixed-date explanation is a complete sentence. At 200% labels remain associated with inputs and trailing icons move rather than overlap text.

## Responsive, accessibility, and insets

Compact is a scrolling form with a safe bottom action area; expanded adds a plan summary pane. Error focus moves to the first invalid field; date and selection controls support keyboard/manual alternatives. One scaffold owns insets.

## Allowed ad slots

None.

## Asset requirements

Standard save, date, tray, zone, substrate, warning, and navigation icons.

## Preview and golden matrix

Create light/dark; edit; repeat; RU 200%; invalid dimensions/date; capacity conflict; needs-resolution save; long recipe name; expanded summary.
