# Domain

## Glossary

- **Grow zone**: a named physical area with a type and integer place capacity. Types are natural light, grow light, and dark.
- **Place**: the user's practical unit for one tray; SproutPace does not solve geometric packing.
- **Tray-size preset**: rectangular width/length or circular diameter in centimetres.
- **Built-in culture**: versioned app-owned catalog entry with stable ID and localized resource keys.
- **User grow recipe**: user-authored stages and guidance, clearly separated from built-in content.
- **Recipe snapshot**: immutable copy of the parameters used to create a batch.
- **Grow batch**: one planned or actual cultivation cycle.
- **Stage event**: a user-confirmed actual transition with timestamp and optional skip/correction context.
- **Scheduled action**: a derived next action for a batch; it is not a habit streak.
- **Harvest plan**: cadence, selected culture rotation, mode, and future suggestions.
- **Observation**: dated private text with an optional app-owned local photo.
- **Outcome**: harvested, failed, or abandoned terminal state.
- **Continuous queue**: a planner mode that suggests a next batch; it does not track consumption or stored harvest.

## Stable built-in identities

The first catalog version contains these language-neutral IDs:

| Stable ID | EN key | RU key meaning |
| --- | --- | --- |
| `radish` | `culture_radish_name` | редис |
| `broccoli` | `culture_broccoli_name` | брокколи |
| `arugula` | `culture_arugula_name` | руккола |
| `mustard` | `culture_mustard_name` | горчица |
| `pea` | `culture_pea_name` | горох |
| `sunflower` | `culture_sunflower_name` | подсолнечник |
| `basil` | `culture_basil_name` | базилик |
| `garden_cress` | `culture_garden_cress_name` | кресс-салат |

`garden_cress` means `Lepidium sativum`; do not label it Watercress or model `Nasturtium officinale` as the same culture.

## Grow-zone invariants

- Capacity is a non-negative integer.
- At least one light-capable place is required before the planner can call a plan feasible.
- A dark zone is optional. Covered or stacked germination on a light shelf continues to occupy that light place.
- Soaking occupies no grow place.
- A sown or growing batch occupies at most one configured place, or is explicitly `unassigned` with a visible warning.
- Moving between zones releases the prior place and claims the new place as one transaction.
- An actual stage event is never rejected solely because configured capacity is full; use another place, change capacity, or record `unassigned`.
- Historical batches preserve zone-name snapshots when a zone is renamed or deleted.

## Tray and seed recommendation invariants

- Rectangular area is width × length; circular area is π × diameter² ÷ 4.
- All persisted dimensions use metric units.
- Recommended seed mass is derived from area and the recipe's versioned density input, then displayed in grams.
- The recommendation is editable per batch and never represented as a guarantee.
- Supplier, cultivar, seed-lot text, and germination percentage are optional user data.

## Recipe invariants

- Built-in recipes are read-only, versioned, source-attributed, and resolved through resource keys.
- A built-in recipe may be duplicated into a user recipe.
- Personal recipes support create/read/update/delete/duplicate.
- Changing or deleting a recipe never mutates a batch's recipe snapshot.
- Built-in instructions use ranges for soaking, blackout/weight, transition-to-light, and harvest window when supported by sources.
- Food-safety guidance covers untreated/microgreen-intended seed, food-safe clean containers, drinking water, drainage, avoiding excessive moisture, and allergy awareness.
- Photographs and illustrations are not diagnostic references.

## Batch state machine

Planned batches may proceed through only applicable states:

`planned → soaking? → sown → blackout_or_weight? → light_growth → ready_for_harvest → harvested`

From any non-terminal state the user may close as `failed` or `abandoned`. A recipe may omit soaking or blackout/weight. A user may explicitly skip a configured stage; the event remains recorded.

Terminal outcomes are historical records, not deletion. Permanent deletion is a separate confirmed operation. Repeating creates a new planned batch with a new ID and snapshot; it never reopens or mutates the original.

## Scheduling rules

- Recommended dates are projections until the user confirms the real event.
- Confirming an early or late stage recalculates only dependent future actions from the actual transition.
- An explicitly user-fixed harvest date is never changed silently; conflicts are shown.
- Undo reverses only the most recent transition, restores the preceding state, and cancels/rebuilds affected future reminders idempotently.
- Short soak duration uses elapsed-time semantics. Calendar actions use local civil date/time and follow a changed time zone.
- Notification identifiers are stable by action ID so restart, restore, undo, and rescheduling cannot create duplicates.
- Android exact-alarm special access is not requested. Delivery near a soak target is best-effort and the authoritative due state remains visible in-app.

## Planner rules

- A harvest plan has a cadence, selected rotation, mode (`cadence` or `continuous_queue`), and paused state.
- Suggestions use recipe ranges and known place capacity; they are never batches until confirmed.
- A plan with unresolved overlap is labeled `needs_resolution`; the UI offers another date, another zone, or capacity change.
- Confirmed dates are not shifted silently.
- Editing, pausing, or deleting a plan changes future suggestions only. Confirmed and active batches persist.
- Continuous queue means another batch is approaching harvest; it does not guarantee edible stock at home.

## Observation and outcome rules

- Observations are ordered by actual timestamp and preserve user text verbatim.
- Photo files are private app-owned data selected through the system picker and linked by stable observation ID.
- Deleting an observation removes its linked file. Deleting a batch removes all linked observation files.
- Harvest outcome accepts optional actual mass, note, and result assessment. There is no predicted mass.
- Harvested, failed, and abandoned outcomes all release capacity and cancel future reminders.

## Error semantics

- Managers return Kotlin `Result` and preserve the original failure cause.
- Persistence/file failures do not partially report success.
- Restore validates into a staging boundary before replacement; any invalid archive leaves current data untouched.
- Capacity conflicts are domain results with resolution choices, not generic exceptions.
- Network, privacy endpoint, advertising, and Crashlytics failures never fail a core product operation.
- Coroutine cancellation is never swallowed by broad error capture.

