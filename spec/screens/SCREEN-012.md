# SCREEN-012 — Zones

Linked flows: FLOW-001, FLOW-003, FLOW-004, FLOW-005  
Linked requirements: REQ-001, REQ-003, REQ-004, REQ-005

## Purpose and hierarchy

Manage named Light and Dark growing zones with positive integer capacity and understandable occupied/available counts.

## States

- Content list with type, capacity, occupancy, and conflicts.
- Empty setup state with Add zone.
- Add/edit validation for blank name, duplicate identity handling, invalid capacity, and capacity below real occupancy.
- Delete blocked or explained when referenced; local save failure preserves input.
- Offline: fully available.

## Actions and iconography

Add zone is primary; rows expose Edit and Delete. Capacity conflict offers reassign, increase capacity, or keep affected future batches needing resolution. Use standard light, dark/covered, capacity, add, edit, delete, and warning icons.

## Text layout expectations

Zone type is always stated in text. Occupancy reads as localized `occupied of capacity`; names wrap. Numeric validation specifies positive whole places, not area or geometry.

## Responsive, accessibility, and insets

Compact navigates list to editor; expanded uses list-detail. Counters have semantic descriptions, and warnings use icon plus text. Keyboard users can reach every row action without long press or swipe.

## Allowed ad slots

None.

## Asset requirements

Standard light, covered/dark, capacity, add, edit, delete, and warning icons.

## Preview and golden matrix

Light/dark zones with occupancy; empty; add/edit; invalid capacity; referenced delete; future conflict; RU 200%; expanded list-detail; offline.
