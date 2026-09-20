# SproutPace production foundation

## Module graph

```text
androidApp ──> shared:compose
iosApp ─────> shared:compose static framework (`compose`)

shared:compose ──> Compose resources and UI/runtime libraries
shared:domain ───> Kotlin/coroutines/datetime only
```

`shared:domain` is the inward product-rule boundary. It must not depend on Compose, platform APIs, persistence, or transport. Future screen-level Decompose modules should own their public component contract, integration/default and preview implementations, Store, and component-local domain orchestration when that creates an enforceable boundary. `shared:compose` remains the resource owner and Android-hosted screenshot-test owner.

## Production root ownership

`productionSproutPaceRoot()` is the single process-scoped construction boundary. It is lazy so preview/test class loading does not initialize production dependencies.

- Android constructs and retains the root in `SproutPaceApplication`, before `AppActivity` enters Compose.
- iOS constructs and retains the same Kotlin singleton in `ContentView`, before `MainViewController` enters Compose.
- `App` accepts the root explicitly. Preview functions pass the separate `PreviewSproutPaceRoot`, so previews never initialize production services.

The current root owns no feature services because those implementations are outside the foundation assignment. Add module factories and lifecycle-managed database/client ownership to the root as feature modules arrive; do not construct repositories, managers, stores, or platform services directly in platform activities/controllers or serialized navigation configurations.

## Platform build contract

- Gradle wrapper: 9.5.1, checksum-pinned.
- Java toolchain used by CI: Temurin 21; Android bytecode target: 17.
- Android: application ID `com.sedsoftware.sproutpace`, minimum SDK 23, compile/target SDK 37.
- iOS: arm64 device plus arm64 simulator Kotlin targets; deployment target 16.2 in Xcode.
- iOS dependency model: direct Gradle-built static framework. CocoaPods is not used, so there is no Podfile or lockfile contract.
- The Xcode build phase calls `:shared:compose:embedAndSignAppleFrameworkForXcode`; CI also links the simulator framework directly before building the unsigned simulator host.

No signing identity, Firebase file, Yandex production ad-unit ID, privacy-policy URL, key, or token is embedded in this foundation.

## Quality ownership

- Detekt `2.0.0-alpha.6` analyzes production Kotlin and Kotlin build scripts from the repository root. Generated/build/test sources are excluded. Configuration validation warnings and rule findings at warning/error severity fail the task.
- `detekt/base-config.yml` is the complete Detekt 2.0.0-alpha.6 generated configuration with the approved SproutPace complexity thresholds applied.
- Kover `0.9.9` aggregates only domain and future `com.sedsoftware.sproutpace.component` packages. UI, preview, generated resource, factory, and module wiring classes are excluded.
- `sproutpace.kover.minimum` intentionally defaults to `0` during foundation work. It must be replaced by a fixed, measured value after meaningful domain/Decompose tests exist. Until then, Kover wiring/report generation can be checked, but the numeric coverage gate is not satisfied.
- Paparazzi `2.0.0-alpha05` and ComposablePreviewScanner `0.9.1` live in `shared:compose`'s Android host-test surface. `generatePreviewSmokeTest` declares its generated Kotlin output under `build/generated/previewSmokeTest` as a source-set input, so Android host-test compilation and Paparazzi tasks depend on generation through Gradle's built-by relationship. The smoke suite requires nonzero discovery and specifically exercises light, dark, and Russian at 200% font scale using production theme/font/resources.

## Verification commands

Gradle execution is serialized by the delivery orchestrator. The foundation requests these checks:

```text
detekt
:shared:domain:testAndroidHostTest
:shared:compose:compileKotlinAndroid
:androidApp:assembleDebug
koverXmlReport
koverVerify
:shared:compose:testAndroidHostTest
:shared:compose:recordPaparazzi
:shared:compose:verifyPaparazzi
:shared:compose:iosSimulatorArm64Test
:shared:domain:iosSimulatorArm64Test
:shared:compose:linkDebugFrameworkIosSimulatorArm64
```

Run `recordPaparazzi` on Linux for the initial baseline, inspect every generated PNG, then run `verifyPaparazzi`. Recording is not approval. The full primary-screen/state matrix remains feature/UI work beyond this early foundation smoke.
