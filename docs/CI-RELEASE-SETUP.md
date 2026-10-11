# CI and release setup

## Implemented build and test workflow

`.github/workflows/AnalysisAndTest.yml` runs on pushes and pull requests to `master`, plus manual dispatch. It uses read-only repository permissions and cancels superseded runs for the same ref.

- `android-quality` runs strict Detekt, every shared module's Android host tests, the SQLDelight migration verifier, `:shared:compose:compileAndroidMain`, debug APK assembly, unsigned release APK/AAB assembly, filtered Kover XML/verification, and `:shared:compose:verifyPaparazzi` on Linux.
- `ios` explicitly compiles and tests the `iosSimulatorArm64` target for `domain`, `data`, `component`, `root`, `platform`, `monetization`, and `compose`; it then links the static Compose simulator framework and builds the Swift host from `iosApp/iosApp.xcodeproj`, scheme `iosApp`, without signing.
- Detekt, Kover, host-test and Paparazzi reports are retained even when a Linux gate fails. Paparazzi failure images are retained on failure. Kotlin/Native test reports and the Xcode result bundle are retained from the macOS job.

The workflow pins every action to an immutable full commit SHA. JDK 21 matches `gradle/gradle-daemon-jvm.properties`; Android compilation remains on JVM target 17. The workflow-level permission is only `contents: read`.

The repository uses a direct Gradle-built static iOS framework. CocoaPods is not used. The Xcode target's build phase invokes `:shared:compose:embedAndSignAppleFrameworkForXcode`; the CI job separately verifies `:shared:compose:linkDebugFrameworkIosSimulatorArm64` before the unsigned host build.

Compose Android host tests pin `user.timezone=Europe/Moscow`, the time zone used for the reviewed Paparazzi baselines. Production still uses the device time zone. This keeps timestamp snapshots identical on Windows and UTC Linux runners without relaxing pixel comparison or regenerating goldens.

The Xcode target excludes `x86_64` for the iOS Simulator in Debug and Release, matching the configured Kotlin targets (`iosArm64` and `iosSimulatorArm64`). Simulator builds require an Apple Silicon Mac; a generic simulator destination must not request an unconfigured Intel framework.

## Verification boundaries

### Locally verifiable Android and repository gates

The delivery orchestrator can run the same Linux job tasks through the repository wrapper on a configured Android development host. A successful local run is evidence only for the tasks actually executed; this document and the committed workflow are not execution receipts.

The workflow uses the repository's current task names:

- `detekt`, `koverXmlReport`, and `koverVerify`;
- `testAndroidHostTest` in all seven shared modules;
- `:shared:data:verifySqlDelightMigration`;
- `:shared:compose:compileAndroidMain`, `:androidApp:assembleDebug`, `:androidApp:assembleRelease`, and `:androidApp:bundleRelease`;
- `:shared:compose:verifyPaparazzi`.

Paparazzi is marked configuration-cache-incompatible by the module build, so the combined CI invocation explicitly uses `--no-configuration-cache`.

### macOS and CI-only iOS gates

The required iOS gate is successful only after a macOS receipt proves all seven shared `compileKotlinIosSimulatorArm64` and `iosSimulatorArm64Test` tasks, `:shared:compose:linkDebugFrameworkIosSimulatorArm64`, and the unsigned `xcodebuild` host build. Windows can compile some Kotlin/Native sources but cannot execute iOS Simulator tests or provide the required QG-003 receipt.

The Xcode build uses `CODE_SIGNING_ALLOWED=NO`, targets a generic iOS Simulator, and writes `build/ios-ci/SproutPace.xcresult` for diagnostics. It must not be described as a device, signing, App Store, Crashlytics symbolication, or publication receipt.

## Required repository protection

- [ ] Protect `master` against direct unreviewed changes.
- [ ] Require both `Android, quality, and screenshots` and `iOS compile, tests, framework, and app linkage` before merge.
- [ ] Keep Actions workflow permissions read-only unless a later, separately approved publishing workflow needs narrower write access.
- [ ] Retain uploaded Detekt, Kover, host-test, Kotlin/Native, Xcode, and Paparazzi artifacts long enough to diagnose a failed gate.

## Quality-gate status

- [x] **QG-011 — catalog accepted, 2026-10-11, version 2.** [Acceptance record](catalog-audit/2026-10-11/v2/acceptance.json) and [independent catalog-only PASS](catalog-audit/2026-10-11/v2/independent-audit.json) close the catalog F07 evidence gap. Eight source derivations, explicit total-cycle timing, EN/RU guidance, historical snapshot preservation and current tests/goldens are verified. This does not close full-app F04/F05/F06 or claim physical growing trials, iOS runtime acceptance, or release readiness. Renew this acceptance when its catalog/calculation/resource inputs change.
- [x] The domain, data, component, root, platform, monetization, and Compose Android host suites contain behavior-level coverage; Kover is pinned to the committed baseline and the current aggregate line coverage exceeds it.
- [x] The Paparazzi baseline covers all sixteen primary screens in light/dark, RU at 200% text, adaptive layouts, and the required error, permission, conflict, restore, privacy, ad, and empty states. Baseline updates use `:shared:compose:recordPaparazzi`; CI blocks on `:shared:compose:verifyPaparazzi`.
- [x] Approved goldens were inspected and reviewed through the required post-golden Lazyweb design pass; accepted findings and scope exclusions are recorded in `docs/design-review/product-ui-material-contract.md`.
- [ ] Validate the committed macOS job on hosted CI; Windows can compile Kotlin/Native sources but cannot execute iOS Simulator tests or `xcodebuild`.

## Distribution boundary

This workflow builds and tests only. It does not sign, retain a distributable release artifact, publish, create a GitHub release, upload to Google Play, create Firebase/Yandex projects, or enable production advertising. Debug and release-candidate CI remain usable without store, Firebase, Yandex, signing, or privacy-policy secrets. Android debug uses the official demo ad identifier. An Android release candidate built without `sproutpace.yandex.android.adUnitId` receives an empty ad-unit value, so advertising remains fail-closed and no demo or live identifier is substituted. Firebase remains optional unless both its opt-in property and real configuration file are supplied. iOS advertising remains compiled safe-disabled with no SDK initialization or production ad unit.

### Safe-disabled release candidate

The following tasks intentionally build unsigned, non-publishable release candidates without external values:

```text
./gradlew :androidApp:assembleRelease :androidApp:bundleRelease
```

Their success proves release compilation and packaging only. It does not prove signing, Firebase/Crashlytics delivery, a production Yandex request, final privacy copy, store readiness, device behavior, or publication. The CI workflow does not upload the unsigned APK/AAB as a release artifact.

`validateYandexReleaseAdUnit` is an explicit advertising-configuration check and is not attached to `preReleaseBuild`. Therefore missing external ad configuration cannot block ordinary compilation, tests, or safe-disabled release-candidate assembly.

### Publication-configuration validator

Before a separately authorized Android publication workflow is added or run, place approved values in a local or CI secret-backed Gradle properties source and execute:

```text
./gradlew :androidApp:validateAndroidPublicationReadiness
```

The validator is deliberately not a dependency of compile, test, `assembleRelease`, or `bundleRelease`. It fails unless all of these external contracts are present:

- `sproutpace.yandex.android.adUnitId`: a real `R-M-<digits>-<digits>` Android inline ad unit, never the demo ID;
- `sproutpace.privacy.propertiesFile`: path to an approved UTF-8 properties file containing every non-empty EN/RU consent/disclosure key from `docs/configuration/advertising/privacy-policy.example.properties` and a final public HTTPS `privacyPolicy.url`; the committed example itself is rejected;
- `sproutpace.signing.storeFile`: path to the external Android upload keystore;
- `sproutpace.signing.storePassword`, `sproutpace.signing.keyAlias`, and `sproutpace.signing.keyPassword`: secret-backed upload-key credentials.

Passing this validator only confirms that the named inputs are structurally present. It does not configure signing for the current build, inspect certificate identity, create an external project, authorize a store upload, or prove publication. A future separately approved protected release workflow must consume and validate those values, sign the exact release artifact, verify its signature, and publish only to the approved track.

The only remaining configuration actions are external and do not block this workflow:

- [ ] Register the Android and iOS Firebase apps, then supply the real configuration files outside source control as described in `docs/configuration/FIREBASE-CRASHLYTICS.md`.
- [ ] Supply the real Android Yandex release ad-unit property only when an advertising-enabled publication build is intentionally prepared, following `docs/configuration/ADVERTISING-PRIVACY.md`.
- [ ] Publish the final privacy-policy URL and approved disclosure text before external distribution.
- [ ] Configure Android signing and store credentials only in a separately approved protected release environment.

There is intentionally no iOS Yandex project, production ad unit, advertising secret, or publication action in this scope. If Android publication is activated later, add a separately reviewed protected-environment workflow and document its exact secret/variable contracts. Never commit upload keys, service-account JSON, real Firebase plist/json files, live ad-unit IDs, or private credentials.
