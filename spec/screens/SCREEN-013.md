# SCREEN-013 — Tray sizes

Linked flows: FLOW-001, FLOW-004  
Linked requirements: REQ-001, REQ-004

## Purpose and hierarchy

Manage reusable metric tray-size presets as rectangular width × length or circular diameter, without tracking empty tray inventory.

## States

- Content list grouped or labeled by shape.
- Empty setup state with Add preset.
- Add/edit validation for blank name and non-positive dimensions.
- Referenced delete confirmation explains snapshots remain intact.
- Local save error; offline fully available.

## Actions and iconography

Add preset is primary; rows expose Edit and Delete. Shape uses radio/connected selection with explicit Rectangle/Circle labels. Standard tray, rectangle, circle, add, edit, and delete icons supplement text.

## Text layout expectations

Every number includes `cm`; multiplication sign and diameter label are localized safely. Names wrap and dimensions remain readable at 200% without fixed-width fields.

## Responsive, accessibility, and insets

Compact is list-to-editor; expanded is list-detail. Shape is announced by name rather than inferred from art. Numeric fields support keyboard entry and clear error associations. One scaffold owns insets.

## Allowed ad slots

None.

## Asset requirements

Standard tray, rectangle, circle, add, edit, delete, and save icons.

## Preview and golden matrix

Mixed preset list light/dark; empty; rectangle editor; circle editor; invalid values; referenced delete; RU 200%; long name; expanded list-detail.
