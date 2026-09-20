# Advertising and privacy configuration

SproutPace supports one Yandex adaptive inline banner on Android only. The only eligible positions are Catalog after four real culture cards and History after five real history records. Every other screen, short/empty/offline content, automated tests, unresolved privacy state, decline, withdrawal, missing ID, SDK failure, and iOS collapse the production slot to zero space. Inspection-only Compose previews may draw a deterministic non-requesting sample for golden review; they never initialize or call the SDK.

The production Compose root resolves privacy automatically on the first eligible visit and opens the full Advertising Privacy screen before an SDK request when a decision is required. Accept and Decline use equal visual weight; Back records nothing. A fresh applicable payload adds the management entry to Settings, while acceptance exposes withdrawal. The Android banner is a lifecycle-owned native `BannerAdView`: the localized Advertising label and slot become visible only after `onAdLoaded`, and composition disposal destroys the owned view. No-fill/load failure and every disabled state remain zero-height.

The app uses the fixed consumer endpoint `GET https://168.222.252.178/v1/privacy/region`. Do not replace, qualify, or smoke-test this accepted shared service. The client sends `Accept: application/json` and an optional app/version header only. It rejects redirects, non-200 responses, non-exact `application/json`, malformed or non-UTF-8 JSON, unknown fields/schema, bodies above 4 KiB, expired responses, and expiry beyond 72 hours. No locale, SIM, time-zone, device-location, country-list, account, installation, advertising, or product-data inference is present.

The minimal region response and policy-bound choice are stored as one private versioned `ProductSettings` payload. It records the fixed endpoint identity, payload/schema versions, applicability, policy version, fetch/expiry times, and choice. The same payload participates in local backup, replacement restore, and erase-all; there is no second advertising `SharedPreferences` store. Protected-region acceptance is required before SDK initialization. Decline, withdrawal, unknown, stale, future-fetched, overlong, endpoint-mismatched, malformed, and transport failures stop new requests. A policy-version change resets the effective choice to unresolved. Before each permitted SDK initialization the current state is passed to `YandexAds.setUserConsent`. Android SDK automatic initialization is disabled in the manifest.

## Build configuration

Debug builds use Yandex's official `demo-banner-yandex` ID. Automated and preview hosts never issue requests.

For a production-enabled Android release, supply the Gradle property from the local/CI secret store (never commit it):

```properties
sproutpace.yandex.android.adUnitId=R-M-123456-7
```

The release validation task rejects missing IDs, placeholders, and the demo ID. The value above is an illustrative format, not a live identifier.

The in-app privacy-policy action is safe-disabled unless the external privacy properties file is supplied. Android reads `privacyPolicy.url` from the same file used by publication validation:

```properties
sproutpace.privacy.propertiesFile=/absolute/path/to/approved-privacy.properties
```

The URL is embedded as a public BuildConfig value only when it is a non-placeholder HTTPS URL with a host. Missing, unreadable, non-HTTPS, and template values leave the action inert. Start from `docs/configuration/advertising/privacy-policy.example.properties`, keep the completed file outside the repository, and do not treat the example as approved copy.

On iOS, define the user build setting `SPROUTPACE_PRIVACY_POLICY_URL` in the local/CI Xcode configuration. `Info.plist` maps it to `SproutPacePrivacyPolicyURL`; an absent, unsubstituted, non-HTTPS, or hostless value leaves the action inert. Do not commit a live URL to the project.

iOS has no advertising dependency, project, or unit ID. `NativeAdvertisingBridge` and the shared iOS driver remain unconditionally disabled until a separately approved scope supplies those external objects.

## Remaining external actions

Only these actions remain outside the repository:

1. Supply the real Android inline ad unit ID for a production-ad release.
2. Supply the final public privacy-policy URL.
3. Supply legally approved complete EN/RU consent and advertising-disclosure copy; downstream localization wiring must use resource keys, not Kotlin/Swift literals.
4. Perform Android device verification using the checklist below and complete applicable store disclosures before distribution.

Do not create an ad project, ad unit, privacy site, legal approval workflow, mediation setup, analytics/attribution integration, ATT flow, or iOS advertising project from this repository.

## Device verification

- Use `adb logcat -v brief '*:S YandexAds'` and confirm the official integration analyzer reports the SDK and banner format integrated successfully.
- Verify the debug build renders only the official demo banner at the Catalog and History positions, never more than once per eligible visit.
- Exercise controlled protected, non-protected, unknown/failing, stale, malformed, policy-changed, decline, and withdrawal states. Confirm privacy resolution and `setUserConsent` precede initialization/load.
- Confirm offline/no-fill/load failure leaves core content usable and collapses the slot; retry is bounded and leaving the host disposes its banner.
- Confirm a release build without a real `R-M-<digits>-<digits>` property fails `validateYandexReleaseAdUnit`.
- Confirm the merged manifest removes `com.google.android.gms.permission.AD_ID` and keeps `com.yandex.mobile.ads.AUTOMATIC_SDK_INITIALIZATION=false`.

See the external templates in `docs/configuration/advertising/`.

## Current implementation sources

- Yandex Mobile Ads Android and Compose Multiplatform version: `8.5.0` (published 2026-09-17; selected from Maven Central metadata/current Yandex release documentation).
- Ktor project version: `3.5.2`; the repository pins it in the version catalog.
- Yandex quick start: <https://ads.yandex.com/helpcenter/en/dev/compose-multiplatform/quick-start>
- Android manual initialization: <https://ads.yandex.com/helpcenter/en/dev/android/quick-start>
- GDPR consent API: <https://ads.yandex.com/helpcenter/en/dev/android/gdpr>
- Android adaptive inline banner lifecycle: <https://ads.yandex.com/helpcenter/en/dev/android/adaptive-inline-banner>
- Ktor redirects, timeouts, and MockEngine: <https://ktor.io/docs/client-redirect.html>, <https://ktor.io/docs/client-timeout.html>, <https://ktor.io/docs/client-testing.html>
