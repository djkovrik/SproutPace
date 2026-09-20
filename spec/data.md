# Data

## Local persistence

Use a shared Kotlin Multiplatform database for relational product data, typed Multiplatform Settings for small preferences/privacy caches, and private app storage for imported observation photos. Managers own persistence boundaries; Stores never call database or platform APIs directly.

Expected local records include:

- grow zones and capacity;
- tray-size presets;
- personal recipes and recipe stages;
- grow batches with immutable recipe, tray, substrate, culture, supplier/cultivar/lot, and zone-name snapshots;
- stage events and derived scheduled actions;
- harvest plans and suggestions;
- observations and photo references;
- terminal outcomes and optional actual mass;
- notification preferences and stable scheduled IDs;
- theme preference;
- Crashlytics collection preference;
- one versioned advertising privacy payload containing only the minimal endpoint response, current choice, and matching policy version.

Use stable language-neutral IDs and foreign keys. Do not persist resolved built-in translations.

## Managed operations

`app-spec.json#managedEntities` is authoritative. Important consequences:

- Deleting zones, presets, or recipes does not erase immutable batch snapshots.
- Delete and terminal batch outcomes are different operations.
- Backup archives are immutable external files; create another archive rather than updating one.
- Ad-consent withdrawal is an update, not deletion.
- Crash reporting always has a default-on release preference and is disabled by updating that preference.

## Built-in catalog

Ship catalog content as versioned, deterministic local seed/reference data with stable culture IDs and string-resource keys. Recommended structure:

- `catalogVersion`: a simple monotonic content version;
- `reviewedOn`: content update date;
- culture ID and scientific/common-name resource keys;
- source URL/title metadata;
- stage applicability and duration ranges;
- blackout/weight and presoak flags/ranges;
- metric density input and its source/version;
- substrate/lighting notes as resource keys;
- safety note keys;
- illustration resource name.

Updating the catalog needs no external approval, signature, or attestation. It must change the version/date, preserve IDs where semantics are unchanged, document sources, and pass QG-011. A breaking identity change requires an explicit migration.

## Localized text storage

The complete English key set lives in `shared/compose/src/commonMain/composeResources/values/strings.xml`; Russian lives in `values-ru/strings.xml`. Catalog seed data and database rows contain only IDs/resource keys. Resolve keys to generated `Res.string.*` accessors at the presentation boundary.

User-authored recipe names, supplier/lot notes, observations, and batch notes are stored verbatim and are not resource strings. Native-only Android/iOS permission and platform text uses native localized resources with English base and Russian addition.

## Backup archive

The full backup is a versioned local archive selected through the platform document provider. It contains:

- a manifest with schema/version and counts;
- all product records and stable relationships;
- app-owned observation photos with integrity metadata;
- user preferences needed to restore product behavior;
- no Crashlytics server data, advertising SDK cache, detected country/IP, advertising ID, or secrets.

Before restore, parse and validate the complete archive, show a localized preview, and require explicit replacement confirmation. Replacement is transactional: stage files and database content, commit both, then rebuild only future reminders. Invalid or unsupported archives leave existing data and files untouched. Restoring the same archive again replaces rather than merges, so it cannot duplicate records.

CSV export contains a localized or documented stable header contract for batch history, culture/recipe identity, relevant dates, outcome, actual mass, and user notes as appropriate. It excludes photographs and does not serve as a restorable backup.

## Remote APIs

### Privacy-region endpoint

Use `GET https://168.222.252.178/v1/privacy/region` exactly as defined by the monetization contract. Send no query parameters or user/install/location identifiers. Accept only strict schema 1 JSON with exact JSON content type, a supported policy version string, and `expiresAt` no more than 72 hours ahead. Reject redirects, non-200 responses, malformed/oversized/non-UTF-8 bodies, unknown schema, stale/overlong expiry, and transport failures.

The endpoint is an accepted shared service. Do not create a backend, alternative service, live qualification, readiness attestation, or endpoint-specific release gate.

### Yandex Ads

Send only the configured ad-unit ID unless a later approved privacy inventory adds something. No product data, notes, schedules, culture choices, photos, user ID, location, or account data enters an ad request. Debug/test uses test IDs; previews and automated tests make no real requests.

### Firebase Crashlytics

Crashlytics is configured separately for Android and iOS release/release-candidate builds. Do not add Firebase Analytics. Do not set a user ID or log user-authored text, crop/recipe selections, schedules, photos, advertising state, or breadcrumbs. Allowed context is limited to app version, platform/build, stack traces, ANR/fatal data, selected technical non-fatals, and reviewed non-PII technical keys.

The release default is collection enabled. The persisted user toggle disables or re-enables future collection according to the platform SDK contract. Local reset does not claim to delete reports already sent to Firebase.

## Offline and cache behavior

- Core database, catalog, photos, plan, history, backup, export, and Settings work offline.
- A valid privacy-region response may be cached only until its accepted expiry, clamped to 72 hours.
- Missing/stale privacy data disables ads for that launch; it does not delay app startup or core screens.
- Crash reports may remain queued by the native SDK contract, but upload never blocks the UI.
- There is no cloud sync, merge policy, login token, or remote product cache.

## Retention and erasure

Product data remains until the user deletes individual records, uses Delete all local data, clears app storage, or uninstalls. Delete all removes database rows, private photos, product/settings state, ad-consent payload, and scheduled reminders, then returns to first run. It explains that previously sent Crashlytics reports and externally saved backup/CSV files are not deleted.

