# Firebase runtime configuration

SproutPace builds without Firebase secrets. Crash collection is disabled by default in native
configuration and is enabled at runtime only in release builds when the stored user preference is
on and the platform driver reports a valid configuration.

- Android: add the project-provided `google-services.json` through the release secret-injection
  workflow and link Firebase Crashlytics. Do not commit it.
- iOS: add the project-provided `GoogleService-Info.plist` to the application target and link
  Firebase Core and Crashlytics. Do not commit it.
- Debug, test and preview hosts force collection off regardless of the stored preference.
- The runtime bridge accepts only collection state and a reviewed technical failure type. It has
  no API for user identifiers, product content, custom keys, breadcrumbs, files or attachments.

Absence of either file or SDK is a supported `Unavailable` state and does not block development or
CI builds.
