# Firebase Crashlytics configuration

SproutPace builds and runs without Firebase configuration. The checked-in adapters expose only collection enable/disable and reviewed technical non-fatals. They intentionally expose no user ID, custom key, breadcrumb, arbitrary message, note, photo, recipe, schedule, advertising-state, or other product-content API. Firebase Analytics is not a dependency and must remain disabled.

## Android app registration

1. In an existing Firebase project, register a separate Android app with package `com.sedsoftware.sproutpace`. Do not create a project as part of repository delivery.
2. Download its real `google-services.json` to `androidApp/google-services.json`. Never derive it from the example or commit it. The safe shape is documented by `docs/configuration/firebase/google-services.json.example`.
3. Build configured variants with `-Psproutpace.firebase.enabled=true`. Without both this property and the real file, the Google Services and Crashlytics plugins are not applied and the Crashlytics runtime is absent. Ordinary debug builds therefore need no Firebase file.
4. Keep only `com.google.firebase:firebase-crashlytics`; do not add `firebase-analytics`. The versions pinned in `androidApp/build.gradle.kts` were checked against the official setup page on 2026-09-20: Google Services plugin 4.5.0, Crashlytics plugin 3.0.8, and Firebase BoM 34.19.0.
5. Release builds must retain source/line information if minification is enabled. Run the release build with the Firebase property and inspect the Crashlytics Gradle output for the release mapping-file upload task. Preserve its log as the mapping receipt.

## Apple app registration (macOS only)

1. Register a separate Apple app with bundle ID `com.sedsoftware.sproutpace.iosApp` in the same or approved Firebase project.
2. Download the real `GoogleService-Info.plist`, add it to the `iosApp` target in Xcode, and keep it outside version control. `docs/configuration/firebase/GoogleService-Info.plist.example` is a placeholder-only schema, not a runnable configuration.
3. In Xcode, add `https://github.com/firebase/firebase-ios-sdk.git` through Swift Package Manager and select `FirebaseCrashlytics` only. Do not select `FirebaseAnalytics`.
4. Add the official `${BUILD_DIR%/Build/*}/SourcePackages/checkouts/firebase-ios-sdk/Crashlytics/run` script as the target's final build phase. Configure the Xcode 15+ input files exactly as listed in Firebase's current dSYM guide, including the built `GoogleService-Info.plist`, executable, dSYM, and optional `.debug.dylib` path.
5. Keep Release `DEBUG_INFORMATION_FORMAT = dwarf-with-dsym`. Archive the app and retain the successful upload-script log plus `dwarfdump --uuid` output as the dSYM receipt.

## Controlled release-candidate verification

Use non-production Firebase app registrations and a disposable physical-device/simulator install. Confirm the Settings preference is enabled, then introduce a temporary local-only fatal crash at a deliberate tester action. Do not commit the crash line. Relaunch once so the SDK can upload, and confirm the Firebase issue is readable and resolves to the exact temporary source line. Save a redacted console screenshot and the matching mapping/dSYM upload log. Repeat after disabling collection: a new controlled crash must not upload on the next launch. Re-enable only on the test installation.

The runtime preference key is `crash_reporting_enabled.v1`, shared with the existing product settings contract. Release defaults to enabled when the key is absent. Debug, preview, XCTest, and automated hosts force collection off. Disabling affects future collection; it cannot delete reports already transmitted.

Official references:

- Android setup: https://firebase.google.com/docs/crashlytics/android/get-started
- Android mapping files: https://firebase.google.com/docs/crashlytics/android/get-deobfuscated-reports
- Apple setup: https://firebase.google.com/docs/crashlytics/ios/get-started
- Apple dSYMs: https://firebase.google.com/docs/crashlytics/ios/get-deobfuscated-reports
