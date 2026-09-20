# FLOW-010 — Determine ad eligibility and manage protected-region consent

Linked requirements: REQ-010  
Linked screens: SCREEN-002, SCREEN-003, SCREEN-005, SCREEN-007, SCREEN-011, SCREEN-015

## Goal

Monetize only eligible browsing contexts while failing closed on privacy uncertainty and never interrupting core growing work.

## Entry and exit conditions

Enter when an eligible Android catalog/history surface is opened or when the user manages applicable consent. Exit with a visible inline ad, a fully collapsed slot, or a recorded consent choice.

## Ordered steps

1. Read cached eligibility and policy metadata.
2. Refresh the fixed privacy-region endpoint when data is absent or stale.
3. If the response requires consent, collect an app-owned accept/decline choice on SCREEN-015.
4. Load at most one inline Yandex ad in the approved position; otherwise collapse the slot.

## Branches and resume

Offline, timeout, malformed response, stale policy, no fill, missing ad ID, SDK failure, or iOS no-ID all resolve to no ad. Core content remains usable.

## AC-100

Given fresh eligibility allows advertising, applicable consent is satisfied, Android has a configured Yandex ad ID, and enough content exists
When SCREEN-007 displays its fifth catalog position or SCREEN-003 History displays its sixth record position
Then one clearly labeled inline ad may appear after the fourth culture card or fifth history record, with at most one ad per eligible screen visit.

## AC-101

Given eligibility is unknown or false, consent is unresolved or declined where required, the device is offline, content is short, no fill occurs, an ID is absent, or an SDK error occurs
When SCREEN-007 or SCREEN-003 is rendered, or SCREEN-002 or SCREEN-005 is opened
Then the ad slot is fully collapsed and no ad appears on prohibited task, detail, editor, decision, settings, backup, permission, onboarding, or privacy surfaces.

## AC-102

Given a fresh endpoint response says protected-region consent is required and no valid choice exists
When the user first reaches an ad-eligible moment
Then SCREEN-015 presents clear Accept, Decline, and Privacy policy actions before any ad request is made.

## AC-103

Given stored privacy metadata exists
When advertising eligibility is evaluated for SCREEN-003 or SCREEN-007
Then the app uses it only while endpoint, policy version, region applicability, and freshness still match the contract.

## AC-104

Given fresh applicability still requires consent and the user previously accepted
When the user withdraws consent from SCREEN-011 or SCREEN-015
Then the choice is updated locally, future ad requests stop, and currently owned ad UI is removed without affecting product data.

## AC-105

Given stored consent references an older policy version
When a fresh endpoint response reports a changed policy version
Then prior consent is treated as unresolved, no ad request occurs, and SCREEN-015 is required again at the next eligible moment.

## AC-106

Given the fixed privacy-region endpoint times out, is unreachable, returns malformed data, or cannot be verified according to its response contract
When eligibility is checked
Then the app fails closed with no global consent guess, no ad request, and no interruption to SCREEN-003 or SCREEN-007 content.

## AC-107

Given the iOS build contains no Yandex ad project or ad-unit identifier
When SCREEN-003 or SCREEN-007 is rendered on iOS
Then advertising remains safely disabled and no placeholder, error, or blocked interaction is shown.
