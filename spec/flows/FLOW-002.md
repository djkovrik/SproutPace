# FLOW-002 — Manage growing setup

Linked requirements: REQ-002  
Linked screens: SCREEN-003, SCREEN-012, SCREEN-013

## Goal

Maintain named capacity zones and reusable metric tray sizes without corrupting active or historical batches.

## Entry and exit conditions

Enter from first run or Settings. Exit after a saved zone/preset is visible or a confirmed deletion completes.

## Ordered steps

1. Open Zones or Tray sizes.
2. Add, inspect, edit, or delete one entity.
3. Explain any effect on active capacity.
4. Persist atomically and refresh affected plan state.

## Branches and resume

Conflict resolution offers reassignment, capacity correction, or temporary unassigned placement. Cancellation preserves the previous setup.

## AC-010

Given the user is on SCREEN-012
When they save a valid zone name, type, and non-negative place capacity
Then a new GrowZone is persisted and becomes available to planning and batch assignment.

## AC-011

Given at least one GrowZone was saved
When the application restarts and SCREEN-012 opens
Then every saved zone, type, capacity, and current occupancy is restored.

## AC-012

Given an existing GrowZone has active or planned occupancy
When the user edits its name, type, or capacity on SCREEN-012
Then the app shows consequences, preserves actual batches through reassignment/unassigned resolution, and never hides a resulting conflict on SCREEN-003.

## AC-013

Given an existing GrowZone may be referenced by current and historical batches
When the user confirms deletion on SCREEN-012
Then current batches are resolved or unassigned and historical SCREEN-003 records retain the zone-name snapshot.

## AC-014

Given the user is on SCREEN-013
When they save positive rectangular centimetre dimensions or a positive circular diameter
Then a new TraySizePreset is persisted and can drive area and seed recommendations.

## AC-015

Given at least one TraySizePreset was saved
When the application restarts and SCREEN-013 opens
Then each preset's name, shape, and metric dimensions are restored.

## AC-016

Given a TraySizePreset is referenced by one or more batches
When the user changes the preset on SCREEN-013
Then future selections use the new dimensions while existing batch snapshots remain unchanged.

## AC-017

Given a TraySizePreset is referenced by historical data
When the user confirms deletion on SCREEN-013
Then the preset disappears from future choices and existing SCREEN-003 history remains readable from snapshots.

