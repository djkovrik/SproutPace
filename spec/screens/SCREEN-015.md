# SCREEN-015 — Advertising privacy

Linked flows: FLOW-010  
Linked requirements: REQ-010

## Purpose and hierarchy

Collect or manage consent only when a fresh response from the fixed privacy-region endpoint says protected-region consent is required.

## States

- First decision: neutral explanation, Accept, Decline, and Privacy policy.
- Previously accepted: withdrawal action and current policy/version context.
- Previously declined: retained state and policy link.
- Endpoint stale/unavailable: no consent guess and no ads; decision controls wait for fresh applicability.
- Policy version changed: prior choice is unresolved and a new decision is shown.

## Actions and iconography

Accept and Decline have equal clarity and neither uses deceptive prominence. Privacy policy opens the configured destination. Withdraw is explicit and confirmed only if needed to explain immediate effect. Use standard privacy, policy/link, accept, decline, and warning icons with labels.

## Text layout expectations

Copy distinguishes app consent choice from operating-system controls and avoids legal claims beyond supplied policy text. Endpoint/policy metadata is secondary. At 200%, buttons stack and full labels remain visible.

## Responsive, accessibility, and insets

Compact is a full-screen decision; expanded uses a bounded supporting surface, never a tiny modal. Initial focus is on the heading, actions have equal semantic weight, and back does not imply consent. One safe-area owner applies.

## Allowed ad slots

None.

## Asset requirements

Standard privacy, policy/link, accept, decline, withdraw, and warning icons.

## Preview and golden matrix

First decision EN/RU light/dark; RU 200%; accepted with withdrawal; declined; stale endpoint; endpoint failure; changed policy version; expanded decision surface.
