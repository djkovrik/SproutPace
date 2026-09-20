# FLOW-009 — Export, restore, and erase local data

Linked requirements: REQ-009  
Linked screens: SCREEN-001, SCREEN-002, SCREEN-003, SCREEN-011, SCREEN-014

## Goal

Give the user a complete, understandable, and local-first way to move or erase their SproutPace data.

## Entry and exit conditions

Enter from Data in SCREEN-011. Exit after a successful export, a confirmed replacement restore, an exported CSV history, or a confirmed reset.

## Ordered steps

1. Choose backup, restore, CSV export, or delete all data.
2. Use the system document picker for the destination or source file.
3. Preview any replacement restore or irreversible reset before confirmation.
4. Apply the operation atomically and report a durable, actionable result.

## Branches and resume

Cancelling a picker or confirmation changes nothing. Invalid archives and failed writes preserve all existing data. A restored empty setup resumes at SCREEN-001; otherwise resume at SCREEN-002 or SCREEN-003.

## AC-090

Given product data or observation photos exist on SCREEN-002 or SCREEN-003
When the user creates a full backup from SCREEN-014 and selects a destination
Then one versioned archive containing all local product data and referenced photos is written without changing the live database.

## AC-091

Given the user selects a readable SproutPace archive on SCREEN-014
When archive validation finishes
Then SCREEN-014 shows its format version, creation time, counts, photo availability, and replacement effect before enabling confirmation.

## AC-092

Given a valid restore preview is visible on SCREEN-014
When the user confirms replacement restore
Then current product data and photos are atomically replaced, restored data is readable on SCREEN-002 and SCREEN-003, and only future reminders are rebuilt without duplicates.

## AC-093

Given a selected restore file is corrupt, unsupported, incomplete, or cannot be read
When SCREEN-014 validates or applies it
Then the operation fails with an actionable localized error and the existing database, photos, and reminders remain unchanged.

## AC-094

Given history records exist on SCREEN-003
When the user exports history as CSV from SCREEN-014
Then a UTF-8 localized-header CSV containing batch history and optional actual mass, but no photo binaries, is written to the chosen destination.

## AC-095

Given local product data, photos, or scheduled notifications exist
When the user confirms Delete all data from SCREEN-011 or SCREEN-014
Then product data, app-owned photos, and scheduled notifications are removed, previously sent Crashlytics reports are unaffected, and the app returns to SCREEN-001.
