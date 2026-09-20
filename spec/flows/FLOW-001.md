# FLOW-001 — First run and first plan

Linked requirements: REQ-001  
Linked screens: SCREEN-001, SCREEN-002, SCREEN-006, SCREEN-007

## Goal

Let a user understand the product, browse before committing, or complete the minimum setup and see a feasible first plan.

## Entry and exit conditions

Enter on a fresh install or after local reset. Exit in Catalog without setup, or on Today/Plan with a configured zone, tray preset, cadence, rotation, and first suggestion.

## Ordered steps

1. Explain the promise and offline-first boundary.
2. Offer Continue setup and Browse catalog.
3. During setup create a light zone, tray preset, cadence, and selected cultures.
4. Show the first plan and request notification permission only after reminders are enabled.

## Branches and resume

The user may leave and resume incomplete setup. Catalog remains readable. Today and Plan show a single Configure first place action until setup is sufficient.

## AC-001

Given a fresh workspace has no growing setup
When the user chooses Browse catalog on SCREEN-001
Then SCREEN-007 opens with the complete offline built-in catalog and Today/Plan remain in a guided not-configured state.

## AC-002

Given GrowZone AC-010, TraySizePreset AC-014, and HarvestPlan AC-050 have produced a feasible minimum setup
When the user finishes setup on SCREEN-001
Then SCREEN-002 and SCREEN-006 show the first proposed plan without creating an unconfirmed grow batch.

