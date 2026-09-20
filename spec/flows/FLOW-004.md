# FLOW-004 — Create and advance a grow batch

Linked requirements: REQ-004  
Linked screens: SCREEN-002, SCREEN-003, SCREEN-004, SCREEN-005, SCREEN-006, SCREEN-012

## Goal

Create one batch from a recipe snapshot and keep its real state, capacity, and future actions correct through edits and stage transitions.

## Entry and exit conditions

Enter from New batch, a plan suggestion, recipe Use action, or existing batch detail. Exit after a persisted edit/transition/deletion or return with no change.

## Ordered steps

1. Select recipe, tray, substrate, optional seed metadata, start/date intent, and place when applicable.
2. Save a planned batch snapshot.
3. Confirm real soaking/sowing/stage events.
4. Recalculate dependent future actions and capacity atomically.
5. Correct, undo, skip, or permanently delete only through explicit actions.

## Branches and resume

Actual reality may be saved as temporarily unassigned. Persistence failure leaves the prior state. Fixed user dates become visible conflicts instead of silently moving.

## AC-030

Given a valid recipe and tray choice exist on SCREEN-004
When the user saves a new planned batch
Then GrowBatch is created with a new ID and immutable recipe/tray/substrate snapshot and appears on SCREEN-003.

## AC-031

Given GrowBatch was persisted
When the application restarts and SCREEN-003 or SCREEN-005 opens
Then its planned/actual stages, snapshot, placement, actions, and terminal state are restored.

## AC-032

Given an existing GrowBatch is opened from SCREEN-005 into SCREEN-004
When the user corrects culture metadata, tray, place, seed amount, or actual dates and confirms the shown consequences
Then only dependent future actions are recalculated and past events remain explicit.

## AC-033

Given a GrowBatch is still planned on SCREEN-003 or SCREEN-004
When the user confirms permanent deletion
Then that batch is removed without creating history or a trash record.

## AC-034

Given a GrowBatch is active or terminal on SCREEN-005
When the user passes the stronger permanent-delete confirmation
Then its records, observations, and app-owned photo files are removed and no future reminder remains.

## AC-035

Given the recipe includes soaking and a planned batch exists
When the user starts soaking from SCREEN-005
Then an actual soaking event and elapsed target are stored while no GrowZone place becomes occupied on SCREEN-002.

## AC-036

Given a planned/soaking batch and a compatible configured place exist
When the user confirms sowing on SCREEN-005
Then the batch enters its first growing stage and exactly one place on SCREEN-012 becomes occupied.

## AC-037

Given an active GrowBatch has a predicted next stage
When the user confirms the actual transition on SCREEN-005
Then the actual timestamp is recorded, old placement is released/new placement claimed when needed, and only dependent future dates update on SCREEN-002 and SCREEN-006.

## AC-038

Given the user physically moves a batch but every configured destination place is occupied
When they confirm the actual move on SCREEN-005
Then SCREEN-012 offers choose/add/change-place options and permits a visible temporarily unassigned state instead of rejecting reality.

## AC-039

Given an active batch has at least one reversible latest stage event
When the user chooses Undo last transition on SCREEN-005
Then only that transition is reversed and affected placements/actions/reminders are rebuilt once.

## AC-040

Given an applicable configured stage has not occurred
When the user confirms Skip stage on SCREEN-005
Then the skip is recorded explicitly and future actions are derived from the next applicable state.

