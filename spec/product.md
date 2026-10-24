# Product

## Product promise

**SproutPace** helps a home grower with several trays know what each tray needs next and plan repeated harvests within the places actually available.

- Store name EN: **SproutPace: Microgreens**
- Store name RU: **SproutPace: Микрозелень**
- Promise EN: **Know what each tray needs next.**
- Promise RU: **Понятный следующий шаг для каждого лотка.**

The name describes a growing rhythm; it must never imply that the app accelerates biological growth. Brand, domain, trademark, and store-name availability must be rechecked before publication.

## Problem and audience

The primary user is a home grower who manages a small set of concurrent trays and may still be completing a first cycle. The user may grow on a windowsill, under a lamp, or use both. They need to coordinate soaking, sowing, blackout/weight, light growth, moisture checks, harvest, and the next sow without maintaining a spreadsheet or accepting an impossible capacity plan.

The product is intended for adults managing home growing. It is not directed to children. Android is the first publication target in all store-supported countries; the interface is EN/RU with English fallback. iOS is fully implemented and verified in the current scope but not published.

## Core outcome

The primary planning goal is a user-selected harvest frequency across a rotation of cultures. A secondary continuous-queue mode suggests the next batch so another tray approaches harvest after the preceding one. Neither mode models household consumption or guarantees continuous food availability.

The first-value loop is:

1. Define at least one light growing zone and a tray size.
2. Choose harvest cadence and cultures.
3. Accept a feasible proposed batch.
4. Confirm real stage changes and see recalculated future actions.
5. Complete or close the batch.
6. Start or accept the next cycle without an external calendar or spreadsheet.

## MVP scope

- Four stable destinations: Today, Trays, Plan, and Catalog.
- Optional first-run setup; catalog browsing remains available before setup.
- Named light and dark zones with integer place capacity. One growing batch occupies one practical place; geometry packing is out of scope.
- Rectangular and circular metric tray-size presets.
- Eight built-in cultures: radish, broccoli, arugula, mustard, pea, sunflower, basil, and garden cress (`Lepidium sativum`, not watercress).
- Versioned offline catalog with source metadata, EN/RU resource keys, ranges, food-safety guidance, and metric seed-density calculations.
- Personal recipes, supplier/variety/seed-lot notes, substrate choice, optional germination percentage, and immutable per-batch recipe snapshots.
- Batch stages: optional soaking, sown, blackout/weight when applicable, light growth, ready for harvest, and terminal harvested/failed/abandoned outcomes.
- Manual confirmation, correction, last-transition undo, explicit stage skip, capacity conflict resolution, and temporary unassigned placement.
- Today actions, one conditional daily summary, and a separate best-effort target-time soak reminder without Android exact-alarm permission.
- iOS summaries repeat without a horizon once work exists by the next window; future-only work uses a finite queue of up to 30 useful windows replenished on app opening, as approved in FLOW-006 AC-065. Today remains authoritative after that queue is exhausted without reopening.
- Cadence plan, future capacity validation, explicit confirmation before creating suggested batches, and continuous-queue suggestions.
- Dated observations with optional local photo.
- History, optional actual harvest mass, repeat-as-new-batch, explicit permanent deletion, and no trash.
- Local full backup/replace restore with photos and relationships; photo-free CSV history export.
- Local data reset.
- Two approved Android inline Yandex ad placements with fail-closed privacy gating; iOS production ads disabled because no iOS ad project/ID exists in scope.
- Firebase Crashlytics on Android and iOS release builds, default on with Settings opt-out, no Firebase Analytics.
- Light/dark/system theme, EN/RU, accessibility, adaptive widths, and offline-first core.

## Non-goals

The excluded requirements REQ-901 through REQ-909 are normative. In particular, the MVP has no account, cloud sync, app backend, AI/OCR diagnosis, IoT, social network, commerce, farm inventory, subscription, paid tray limit, video lessons, consumption/storage tracking, predicted yield, monthly calendar, Analytics, attribution, fullscreen/rewarded/app-open ads, iOS publication, or iOS production ads.

## Product principles

- Record reality before protecting the plan: an actual stage change may be saved even when configured capacity is full.
- Never shift a user-confirmed date silently.
- A forecast is a range or suggestion, not a biological guarantee.
- No streaks, artificial daily check-ins, or growth-speed promises.
- Core actions never wait for network, advertising, or Crashlytics.
- Ads never cover primary content or appear in a critical grow action.
- User-authored names, notes, recipes, and photographs remain user data.

## Success criteria

The MVP succeeds when a home grower can configure places, follow one batch to a recorded outcome, and create or accept a second cycle without an external spreadsheet. The leading product signal is progression to the second cycle, not a daily streak. Because product analytics are excluded, validate this through pilot task testing and interviews rather than hidden event collection.

## Assumptions and limitations

- Exact culture ranges vary by seed lot, cultivar, tray, medium, light, temperature, and user technique.
- Seed mass is a metric recommendation derived from growing area and versioned density guidance, always editable for a batch.
- Physical validation of several real cycles is not yet complete and must not be claimed.
- Built-in catalog maintenance is intentionally lightweight: change a simple catalog version and review date, update source metadata, and pass QG-011. No external approval, signature, or attestation workflow exists.
- Final privacy-policy URL/copy, Firebase files, Android Yandex ad ID, signing, store accounts, and publication state are external inputs tracked by quality gates.

## Evidence used during product definition

- [Penn State: A Step-By-Step Guide for Growing Microgreens at Home](https://extension.psu.edu/a-step-by-step-guide-for-growing-microgreens-at-home)
- [Penn State: The ABCs of Microgreens](https://extension.psu.edu/the-abcs-of-microgreens)
- [Penn State: Growing Microgreens](https://extension.psu.edu/growing-microgreens)
- [Penn State: Ensuring Food Safety in Microgreens Production](https://extension.psu.edu/ensuring-food-safety-in-microgreens-production)
- [Utah State University: Grow Your Own Microgreens](https://extension.usu.edu/yardandgarden/research/grow-your-own-microgreens)
- [Utah State University: Garden Cress](https://extension.usu.edu/yardandgarden/research/garden-cress-in-the-garden)
- [Lazyweb advertising placement evidence](https://www.lazyweb.com/agentic-search/2c55a632-370e-4a01-9e89-a6a5198460e8), used qualitatively; no matching measured experiment was found.

