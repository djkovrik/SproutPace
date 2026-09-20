# SCREEN-003 — Trays

Linked flows: FLOW-002, FLOW-003, FLOW-004, FLOW-007, FLOW-008, FLOW-009, FLOW-010, FLOW-012  
Linked requirements: REQ-002, REQ-003, REQ-004, REQ-007, REQ-008, REQ-009, REQ-010, REQ-012

## Purpose and hierarchy

Browse Planned, Active, and History peer categories, see status and next action, and open or create a batch. Tabs change category without implying a sequence.

## States

- Content: category list with selected detail on expanded widths.
- Empty: category-specific explanation and one useful action.
- Loading/error: local query progress/failure only; retry preserves selected tab.
- Offline: full list/detail access; History ad collapses.
- Ad state: eligible History inserts one inline item only after five real records.

## Actions and iconography

New batch is a labeled FAB where capacity does not forbid planning. Rows open detail; overflow exposes Edit, Repeat where terminal, and Delete. Use standard tray, history, add, edit, repeat, delete, and status icons with labels/semantics.

## Text layout expectations

Culture or user batch name is primary; next action/outcome and date are secondary. Tabs use localized short nouns. Long names wrap; metadata may ellipsize only after its useful prefix. Ad label is explicit in EN/RU.

## Responsive, accessibility, and insets

Compact navigates list to detail; expanded uses list-detail with persistent selection. Tabs remain reachable by keyboard without requiring swipe. Selected tab, batch stage, and outcome have non-color cues. Scaffold consumes insets once.

## Allowed ad slots

History only: one labeled inline Yandex ad after the fifth real history record, only when all eligibility conditions pass. No reserved gap when hidden.

## Asset requirements

Standard tray, history, add, overflow, edit, repeat, delete, warning, and status icons.

## Preview and golden matrix

Planned/Active/History content in light/dark; each empty state; RU 200%; long names; expanded list-detail; offline hidden-ad; eligible six-record History with ad; failed/abandoned outcomes.
