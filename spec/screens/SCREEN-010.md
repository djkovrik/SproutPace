# SCREEN-010 — Close batch

Linked flows: FLOW-008  
Linked requirements: REQ-008

## Purpose and hierarchy

Record an explicit terminal outcome—harvested, failed, or abandoned—with date and optional outcome-specific details before freeing capacity.

## States

- Harvested: optional actual mass and note.
- Failed: optional reason and note.
- Abandoned: optional reason and note.
- Validation: invalid date or mass remains inline.
- Local save failure retains all fields; offline is fully available.

## Actions and iconography

Save outcome is primary; Cancel leaves the batch active. Outcome uses radio buttons or a connected group, never icon-only selection. Standard harvest, failed, abandoned, date, mass, and save icons supplement labels.

## Text layout expectations

Outcome consequences are plain complete sentences. Mass uses an explicit metric unit and accepts no implied forecast. At 200% choices and actions stack without clipping.

## Responsive, accessibility, and insets

Compact may use a full-height form instead of a cramped dialog; expanded may use a basic modal with bounded width. Focus starts at the heading/outcome, validation is announced, and cancellation has predictable back behavior.

## Allowed ad slots

None.

## Asset requirements

Standard harvest, failed, abandoned, date, mass, save, and close icons.

## Preview and golden matrix

Each outcome light/dark; harvested with mass; RU 200%; invalid mass/date; long reason; save failure; compact full-screen and expanded modal.
