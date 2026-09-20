# SCREEN-016 — Notifications

Linked flows: FLOW-001, FLOW-003  
Linked requirements: REQ-001, REQ-003

## Purpose and hierarchy

Configure the 09:00-local daily work summary, optional separate soak target reminder, and recover from notification permission denial without weakening Today as the source of truth.

## States

- Summary enabled/disabled with local time selection; default 09:00.
- Soak reminder enabled/disabled according to batch applicability.
- Runtime permission not requested, granted, denied, or permanently denied.
- Scheduled summary contains work / no work, where no-work means no notification.
- Scheduling error exposes recovery while in-app actions remain intact.

## Actions and iconography

Independent switches control summary and soak reminders; time picker changes summary time. Request permission and Open system settings are labeled contextual actions. Use standard notification, notification-off, time, soak, and settings icons.

## Text layout expectations

Explain best-effort target timing and that SproutPace does not request exact-alarm special access. Permission copy says Today remains current. Times use locale conventions; at 200% state summaries wrap below switches.

## Responsive, accessibility, and insets

Compact and expanded both use a bounded settings column. Switches expose state and consequence, time picker has manual entry, and permission changes are announced. Scaffold owns insets once.

## Allowed ad slots

None.

## Asset requirements

Standard notification, notification-off, time, soak, system-settings, and warning icons.

## Preview and golden matrix

Enabled at 09:00 light/dark; disabled; permission not requested/denied/permanently denied; soak applicable; schedule error; RU 200%; expanded settings; offline.
