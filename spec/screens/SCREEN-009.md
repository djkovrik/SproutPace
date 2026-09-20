# SCREEN-009 — Observation editor

Linked flows: FLOW-007  
Linked requirements: REQ-007

## Purpose and hierarchy

Add or edit a dated observation with optional note and one optional app-owned photo selected through the system picker.

## States

- Create/edit content with date, note, and photo preview.
- Photo absent, selected, replaced, or removed.
- Picker permission/failure explanation that preserves text.
- Validation and local save failure with retry.
- Offline: fully available.

## Actions and iconography

Save is primary; Choose/Replace photo, Remove photo, Delete observation, and Cancel are labeled. Use standard photo, date, save, delete, and navigation icons; photo thumbnails never become unlabeled controls.

## Text layout expectations

User notes preserve entered language and line breaks. Date follows system locale. At 200% supporting/error text wraps below its field; destructive labels remain complete.

## Responsive, accessibility, and insets

Compact uses a scrolling form; expanded gives the photo a bounded supporting pane. Picker use is not the only path to save. Preview has a useful description derived from localized context, not image diagnosis. Scaffold owns insets once.

## Allowed ad slots

None.

## Asset requirements

Standard photo, date, save, delete, and back icons.

## Preview and golden matrix

Empty create light/dark; note plus photo; edit; RU 200%; long note; picker failure; delete confirmation; local save error; expanded photo pane.
