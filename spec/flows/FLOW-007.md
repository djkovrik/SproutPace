# FLOW-007 — Observe a batch

Linked requirements: REQ-007  
Linked screens: SCREEN-005, SCREEN-009

## Goal

Keep private dated text and optional local photos attached to a batch through restart and explicit edits/deletes.

## Entry and exit conditions

Enter from the batch timeline. Exit after saving, editing, deleting, or cancelling an observation.

## Ordered steps

1. Open the observation editor.
2. Enter dated text and optionally choose one photo through the system picker.
3. Save atomically.
4. Reopen, edit, or delete from the batch timeline.

## Branches and resume

Picker cancellation leaves the editor intact. Import/file failure preserves the prior observation. No broad photo-library permission is requested when a system picker suffices.

## AC-070

Given a GrowBatch exists on SCREEN-005
When the user saves valid dated text on SCREEN-009
Then a new Observation is persisted and appears in chronological batch history.

## AC-071

Given an Observation was saved
When the app restarts and SCREEN-005 opens
Then its timestamp, user text, and optional photo link are restored.

## AC-072

Given an Observation is open on SCREEN-009
When the user changes its text/date/photo reference and saves
Then the same Observation identity is updated without changing other timeline items.

## AC-073

Given an Observation exists with or without a photo
When the user confirms deletion on SCREEN-009
Then the Observation and its app-owned photo file are removed from SCREEN-005.

## AC-074

Given an Observation editor is open on SCREEN-009
When the user selects a valid image through the system picker and saves
Then an app-owned private copy is linked to the Observation and renders again on SCREEN-005 after restart.

