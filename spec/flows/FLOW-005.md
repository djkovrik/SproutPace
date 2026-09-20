# FLOW-005 — Plan cadence and continuous queue

Linked requirements: REQ-005  
Linked screens: SCREEN-002, SCREEN-003, SCREEN-004, SCREEN-006, SCREEN-012

## Goal

Generate user-confirmed future suggestions that respect harvest cadence, culture rotation, and real place capacity.

## Entry and exit conditions

Enter from first setup, Today, or Plan. Exit with a saved/paused/deleted plan, a resolved conflict, or a confirmed suggestion that opens Batch editor.

## Ordered steps

1. Select cadence, culture rotation, and cadence/continuous mode.
2. Generate a forecast from versioned recipe ranges and capacity.
3. Mark impossible overlaps and offer resolutions.
4. Require confirmation before creating a planned batch.

## Branches and resume

Plans may pause without disturbing existing batch tasks. Deleting a plan removes suggestions only. Unresolved conflicts persist as `needs_resolution`.

## AC-050

Given the user opens SCREEN-006 with no HarvestPlan
When they save a positive harvest cadence, selected culture rotation, and mode
Then a new HarvestPlan is persisted and generates suggestions rather than automatic batches.

## AC-051

Given a HarvestPlan exists
When the application restarts and SCREEN-006 opens
Then its cadence, rotation, mode, paused state, suggestions, and conflicts are restored/recomputed deterministically.

## AC-052

Given a HarvestPlan has confirmed or active batches visible on SCREEN-003
When the user changes cadence, rotation, or mode on SCREEN-006
Then future suggestions update and confirmed/active batches do not change.

## AC-053

Given a HarvestPlan exists with related batches
When the user confirms plan deletion on SCREEN-006
Then suggestions and plan state are removed while SCREEN-003 batches remain.

## AC-054

Given a HarvestPlan exists
When the user pauses or resumes it on SCREEN-006
Then future suggestions stop or resume and actions for existing batches continue.

## AC-055

Given a proposed schedule would exceed a light/dark place capacity
When SCREEN-006 validates it
Then the conflict is labeled, the plan is not called feasible, and another date, zone, or capacity change through SCREEN-012 is offered.

## AC-056

Given a feasible or explicitly unresolved suggestion is visible on SCREEN-006
When the user chooses to accept it
Then SCREEN-004 opens for confirmation and no GrowBatch exists until the user saves it.

## AC-057

Given continuous-queue mode is active and a next batch is needed
When SCREEN-002 or SCREEN-006 evaluates current batches and capacity
Then one next-batch suggestion is shown without tracking household consumption or promising uninterrupted edible stock.

