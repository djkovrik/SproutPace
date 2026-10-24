# iOS conditional summary scheduling — 2026-10-24

## User authorization

The user was asked whether to explicitly accept the iOS future-only scheduling limitation: a local-only app cannot guarantee both indefinite daily delivery beginning at an arbitrary future due date and silence before that date without guaranteed background execution or a backend. Existing work must no longer stop notifying after 30 days; future-only notifications may use a finite queue replenished when the app opens.

The user approved:

> Да, зафиксировать ограничение iOS

This authorizes the scoped AC-065/QG-003 clarification, not removal of acceptance scenarios, native tests, no-work silence, timezone behavior, or the native verification gate.

## Platform evidence

Apple documents an [hour/minute calendar trigger with repeats enabled](https://developer.apple.com/documentation/usernotifications/uncalendarnotificationtrigger) for daily civil-time delivery. It reschedules after delivery and the system can deliver while the app is not running; a completed repeating request must be [explicitly canceled](https://developer.apple.com/documentation/usernotifications/scheduling-a-notification-locally-from-your-app).

Calendar matching supplies no independent delayed-start date for a repeating hour/minute rule. Adding a full fixed year/month/day restricts the matching dates rather than enabling a future daily start. Interval repetition would not preserve local civil time across DST.

Apple states that [the system decides when to run background refresh](https://developer.apple.com/documentation/backgroundtasks/choosing-background-strategies-for-your-app). Background refresh therefore cannot establish guaranteed replenishment. This change adds no claim of guaranteed background execution and no external push/backend dependency.

## Implementation

- If work exists by the next configured local summary window, register one repeating calendar notification under `sproutpace:daily-summary`, containing only the civil hour/minute and a Gregorian calendar. It occupies one native request slot and has no 30-day cutoff. Pending work remains overdue until a product operation changes it; product operations reconcile and cancel obsolete registrations.
- Otherwise, enqueue at most 30 useful dated, non-repeating summary windows starting at the first due window, even if that date is more than 30 days away. No summary is queued for an empty or earlier no-work window. Reopening replenishes the queue or replaces it with the stable daily repeat when work reaches the next window.
- Keep existing registered-ID reconciliation, permission checks and the 64-request native budget accounting for other app features. Capacity failures remain explicit; adding this feature does not promise all requests fit when the total native budget is exhausted.
- Keep soaking targets as non-repeating elapsed intervals. A timezone change does not recalculate their elapsed target. Repeating summaries follow native civil time; semantic action reconciliation retains the existing host timezone-change/foreground lifecycle path.
- Cancel legacy dated summary IDs when converting to the single repeating ID, and cancel the repeat when work disappears or summaries are disabled.

## Accepted boundary and verification

Without reopening, future-only notifications are not guaranteed after the prequeued useful windows are exhausted. The app does not execute a conditional work query at each iOS local-notification delivery. Native delivery is subject to OS authorization, scheduling and device policy; an hour/minute repeat follows the device calendar, while semantic schedule changes are reconciled when the host receives its existing lifecycle/environment callbacks. In particular, do not claim suspended execution or delivery-time re-evaluation after a timezone change. Today remains the authoritative local plan.

Deterministic production-source tests cover recurrence selection, work beyond the old horizon, future-only silence and queue start, legacy cancellation, completion/disable, civil-time projection and elapsed soak independence. Native iOS trigger tests assert repeating hour/minute components with no fixed date, dated one-shot components, and the separate elapsed soak trigger. Android permission policy tests cover first denial, permanent denial/recheck, Settings restoration, app-level disabled notifications, and pre-33 behavior.

Windows cannot execute the iOS trigger suite or prove device behavior. macOS CI/native acceptance must compile and execute the native tests and verify actual authorization, repeated delivery, cancellation, local-time/DST changes, pending-request budget behavior and lifecycle reconciliation. This decision is not a macOS verification receipt or a release-readiness claim.
