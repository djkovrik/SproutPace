# FLOW-011 — Configure privacy-conscious crash reporting

Linked requirements: REQ-011  
Linked screens: SCREEN-011

## Goal

Collect release crash diagnostics by default while giving the user a durable, immediate opt-out and minimizing collected context.

## Entry and exit conditions

Enter during release startup or from Privacy in SCREEN-011. Exit after applying and persisting the effective Crashlytics collection preference.

## Ordered steps

1. Resolve the stored preference, defaulting to enabled only for release builds.
2. Apply the preference before routine reporting can continue.
3. Let the user disable or re-enable collection from Settings.
4. Keep debug, preview, screenshot-test, and unit-test processes disabled.

## Branches and resume

Missing platform configuration fails without blocking app startup. The visible preference reflects the effective state rather than claiming collection is active when initialization failed.

## AC-110

Given a correctly configured Android or iOS release build starts for the first time
When crash-reporting preference is resolved
Then Crashlytics automatic collection is enabled by default without enabling Analytics, user identifiers, breadcrumbs, or product-content attachments.

## AC-111

Given the user previously selected a Crashlytics collection preference on SCREEN-011
When a later release process starts
Then that stored choice is restored and applied before routine collection continues.

## AC-112

Given the Crashlytics control is visible on SCREEN-011
When the user disables or re-enables it
Then the effective collection state changes immediately where supported, the choice persists, and explanatory copy distinguishes future collection from already transmitted reports.

## AC-113

Given the app runs as debug, preview, screenshot test, or unit/integration test
When Crashlytics initialization is evaluated
Then collection stays disabled and no user ID, note, observation, culture content, ad identifier, or navigation breadcrumb is added in any build.
