# SCREEN-005 — Batch detail

Linked flows: FLOW-002, FLOW-003, FLOW-004, FLOW-007, FLOW-008, FLOW-010, FLOW-012  
Linked requirements: REQ-002, REQ-003, REQ-004, REQ-007, REQ-008, REQ-010, REQ-012

## Purpose and hierarchy

Explain current reality through next action, stage timeline, capacity assignment, recipe snapshot, observations, and history. Actual events and predicted windows must be visually distinct.

## States

- Planned, active, ready, harvested, failed, and abandoned content.
- Skipped stage and fixed-date variants.
- Actual overflow with Temporarily unassigned resolution.
- Empty observations with Add observation.
- Local loading/error and full offline operation.

## Actions and iconography

Primary action confirms the next actual stage or closes the batch. Secondary actions add observation, edit, skip stage, undo last transition, reassign, repeat, or delete as applicable. Use ASSET-016–020 stage art decoratively plus labeled standard action icons.

## Text layout expectations

Next action and culture name precede dates. Timeline labels distinguish Actual, Expected, Skipped, and Fixed. Notes wrap without arbitrary truncation; optional mass always displays its metric unit.

## Responsive, accessibility, and insets

Compact stacks summary, timeline, and observations. Expanded uses a supporting detail pane but preserves reading order. Timeline exposes an ordered semantic list; state never relies only on color. Keyboard focus and screen-reader announcement follow successful transitions.

## Allowed ad slots

None, including terminal history detail.

## Asset requirements

ASSET-016, ASSET-017, ASSET-018, ASSET-019, ASSET-020; standard edit, observation, photo, undo, skip, move, repeat, close, and delete icons.

## Preview and golden matrix

Each lifecycle stage light/dark; ready with fixed date; skipped stage; temporary unassigned; observations with photo; terminal outcomes; RU 200%; long note; offline; expanded timeline.
