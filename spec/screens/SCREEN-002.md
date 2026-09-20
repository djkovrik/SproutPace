# SCREEN-002 — Today

Linked flows: FLOW-001, FLOW-002, FLOW-003, FLOW-009, FLOW-010, FLOW-012  
Linked requirements: REQ-001, REQ-002, REQ-003, REQ-009, REQ-010, REQ-012

## Purpose and hierarchy

Make the most important real-world action unmistakable: hero next action, overdue work, due today, then upcoming sow/harvest suggestion and capacity warning. When no work exists, show a calm next-step empty state.

## States

- Content: grouped action feed with due state and batch/culture context.
- Empty: ASSET-021 plus New batch or Browse catalog.
- Conflict: capacity warning with Resolve action.
- Permission denied: in-app work stays visible; notification recovery is secondary.
- Offline: identical local content; ads are never present.
- Loading/error: only for actual local persistence work, with retry and preserved last stable state.

## Actions and iconography

Primary row action opens or completes the due item; New batch uses labeled extended FAB when valid. Settings is the top-app-bar action. Standard today, overdue, add, settings, and warning icons supplement—not replace—text.

## Text layout expectations

Action verb and culture/tray name remain visible before secondary dates. Overdue and due-today wording is localized as complete phrases. Long names wrap to two lines; at 200% cards grow vertically.

## Responsive, accessibility, and insets

Compact is a single feed. Expanded keeps one readable action column with optional capacity pane, not a stretched grid. Scaffold owns bars/insets. Headings define group order; completion and reschedule changes are announced.

## Allowed ad slots

None.

## Asset requirements

ASSET-021 and standard today, warning, add, settings, stage-action icons.

## Preview and golden matrix

EN compact mixed due work light/dark; RU 200%; all-clear empty; overdue; capacity conflict; notification denied; offline; long user batch name; expanded supporting pane.
