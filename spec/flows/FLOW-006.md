# FLOW-006 — Today actions and reminders

Linked requirements: REQ-006  
Linked screens: SCREEN-002, SCREEN-005, SCREEN-016

## Goal

Keep due work visible in-app and optionally notify without duplicates or dependence on privileged exact alarms.

## Entry and exit conditions

Enter on Today, batch detail, notification settings, app restart, or time-zone change. Exit after reading/completing/snoozing/skipping work or changing reminder settings.

## Ordered steps

1. Derive overdue and today actions from persisted batch events.
2. Present the next useful action first.
3. Complete, snooze, skip, or open its batch.
4. Schedule one daily summary only when useful and a separate best-effort soak target.
5. Reconcile stable notification IDs after changes/restart.

## Branches and resume

Permission denial shows recovery but never hides the plan. Notifications are a convenience; Today is authoritative.

## AC-060

Given one or more batches have current or overdue actions
When SCREEN-002 opens
Then the next useful action, overdue group, today group, and relevant capacity/suggestion context are shown from local state.

## AC-061

Given a ScheduledAction is incomplete on SCREEN-002 or SCREEN-005
When the user completes it
Then it is applied exactly once and duplicate callbacks cannot repeat the domain operation.

## AC-062

Given a ScheduledAction can be deferred
When the user chooses a new valid time on SCREEN-002
Then the existing action is snoozed/rescheduled under its stable identity without a duplicate.

## AC-063

Given a non-stage ScheduledAction is visible
When the user confirms Skip on SCREEN-002 or SCREEN-005
Then the skip is recorded and the batch remains visible with subsequent applicable work.

## AC-064

Given notification permission is denied on SCREEN-016
When the user returns to SCREEN-002
Then every action and plan remains usable and a non-blocking permission explanation/recovery path is available.

## AC-065

Given daily summaries are enabled at the default or user-selected local time
When the delivery window arrives
Then one grouped summary is delivered only if current or overdue actions exist.

## AC-066

Given a batch starts a finite soak duration on SCREEN-005 and notifications are allowed
When scheduling succeeds
Then one best-effort notification targets the elapsed-duration end without requesting Android exact-alarm special access.

## AC-067

Given future actions/reminders exist
When the process or device restarts
Then the scheduler reconciles stable IDs once, cancels obsolete work, and produces no duplicates on SCREEN-002 or SCREEN-016.

## AC-068

Given the device time zone changes
When schedules are recalculated
Then civil-date actions follow the new local zone while an active soak retains its original elapsed duration on SCREEN-005.

