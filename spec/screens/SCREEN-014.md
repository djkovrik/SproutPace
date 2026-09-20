# SCREEN-014 — Backup and restore

Linked flows: FLOW-009  
Linked requirements: REQ-009

## Purpose and hierarchy

Create a complete local backup, preview and confirm replacement restore, export history CSV, or reach destructive reset with explicit consequences.

## States

- Idle action list.
- Backup/CSV destination selection and measurable progress.
- Valid restore preview with version, date, counts, photos, and replacement warning.
- Invalid/unsupported archive error with no mutation.
- Restore/export success or failure; cancelled picker returns to idle.

## Actions and iconography

Create backup, Restore backup, Export history CSV, and Delete all data are labeled rows/buttons. Confirm restore and delete use destructive confirmation; system pickers own file browsing. Use standard backup, restore, file, table, warning, and delete icons.

## Text layout expectations

File metadata and counts wrap; technical format errors are translated into actionable copy without leaking paths unnecessarily. Replacement and no-merge behavior is stated before confirmation. Progress labels do not rely on animation alone.

## Responsive, accessibility, and insets

Compact pushes restore preview as a full page; expanded uses a supporting pane. Progress is announced without excessive updates. Focus moves to preview/error heading after picker return. Safe areas are consumed once.

## Allowed ad slots

None.

## Asset requirements

Standard backup, restore, document, table/export, warning, delete, and close icons.

## Preview and golden matrix

Idle light/dark; valid preview; replacement confirmation; invalid archive; in-progress export; failed write; success; RU 200%; expanded preview; delete-all confirmation.
