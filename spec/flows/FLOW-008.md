# FLOW-008 — Close, review, and repeat a batch

Linked requirements: REQ-008  
Linked screens: SCREEN-003, SCREEN-004, SCREEN-005, SCREEN-006, SCREEN-010

## Goal

Close a cycle with an explicit result, release resources, preserve history, and start a separate repeat when wanted.

## Entry and exit conditions

Enter from batch detail or a ready-for-harvest action. Exit in History or a prefilled new Batch editor.

## Ordered steps

1. Choose harvested, failed, or abandoned.
2. Record date, optional reason/note/assessment, and optional actual mass for harvest.
3. Save terminal outcome, free capacity, and cancel future reminders.
4. Review history or repeat into a new planned batch.

## Branches and resume

Cancellation keeps the batch active. Repeat preserves the original. Permanent deletion remains a separate FLOW-004 operation.

## AC-080

Given an active batch is ready or the user chooses early harvest
When harvested outcome is saved on SCREEN-010
Then the batch becomes terminal, its place is released on SCREEN-006, future reminders are cancelled, and it appears in SCREEN-003 History.

## AC-081

Given an active batch cannot continue
When failed outcome with optional reason is saved on SCREEN-010
Then it becomes terminal, frees capacity, cancels future reminders, and remains distinguishable as failed in SCREEN-003.

## AC-082

Given a planned or active batch exists
When abandoned outcome with optional reason is saved on SCREEN-010
Then it becomes terminal, frees any place, cancels future reminders, and remains distinguishable as abandoned in SCREEN-003.

## AC-083

Given a terminal batch is visible on SCREEN-003 or SCREEN-005
When the user chooses Repeat
Then SCREEN-004 opens a new planned batch with copied recipe/tray/substrate inputs and a new identity while the original remains unchanged.

## AC-084

Given one or more terminal batches exist
When the user opens SCREEN-003 History or a history item on SCREEN-005
Then outcome, dates, preserved snapshots, observations, and optional actual mass are readable after restart.

## AC-085

Given harvested outcome is being recorded on SCREEN-010
When the user enters a valid optional metric mass
Then that actual mass is stored and shown on SCREEN-005 without creating a predicted-yield field.

