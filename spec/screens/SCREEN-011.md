# SCREEN-011 — Settings

Linked flows: FLOW-001, FLOW-009, FLOW-010, FLOW-011, FLOW-012  
Linked requirements: REQ-001, REQ-009, REQ-010, REQ-011, REQ-012

## Purpose and hierarchy

Provide stable entry points for appearance, notifications, zones, tray presets, data portability, applicable advertising privacy, crash reporting, privacy policy, and destructive reset.

## States

- Content grouped by Growing setup, Preferences, Privacy, and Data.
- Crash reporting effective/failed initialization state.
- Advertising consent row appears only while fresh applicability requires management.
- Notification permission denied summary.
- Local loading/error only where a preference write is in progress; offline does not block settings.

## Actions and iconography

Navigation rows open SCREEN-012–016 and SCREEN-014. Theme uses a choice control; Crashlytics uses an independent switch with explanatory text. Delete all data is a destructive labeled action. Standard settings-category icons have text labels.

## Text layout expectations

Each row has a concise title and optional state summary. Crashlytics copy says disabling affects future collection, not already transmitted reports. At 200% trailing switches remain aligned without squeezing titles into unusable columns.

## Responsive, accessibility, and insets

Compact is one grouped list; expanded uses list-detail for selected settings destination. Switch labels expose current state, rows have unique accessible names, and keyboard focus is visible. Scaffold/navigation own safe areas once.

## Allowed ad slots

None.

## Asset requirements

Standard theme, notification, zone, tray, backup, privacy, crash-reporting, policy, chevron, and delete icons.

## Preview and golden matrix

EN compact light/dark; RU 200%; notification denied; consent-management row shown/hidden; Crashlytics off and init-failed; expanded list-detail; destructive reset confirmation.
