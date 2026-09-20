# SCREEN-007 — Catalog

Linked flows: FLOW-001, FLOW-004, FLOW-006, FLOW-010, FLOW-012  
Linked requirements: REQ-001, REQ-004, REQ-006, REQ-010, REQ-012

## Purpose and hierarchy

Browse eight bundled culture guides and personal recipes, open details, and create a personal recipe. The bundled catalog remains readable offline and identifies source/version/review metadata.

## States

- Content: bundled and personal recipe cards in feed/grid form.
- Search/filter result empty state; catalog itself is never network-empty.
- Local loading/error for personal data only.
- Offline: all recipes remain available and the ad slot collapses.
- Ad state: one eligible inline item after four real culture cards.

## Actions and iconography

Cards open SCREEN-008; Create personal recipe is labeled. Search/filter, favorite if specified later, source info, and personal edit use standard icons with semantics. ASSET-022–029 visually distinguish the eight bundled cultures.

## Text layout expectations

Common localized culture name is primary; scientific name, typical window, and source metadata are secondary. Names wrap before truncation. Ad label and recipe provenance are explicit complete strings.

## Responsive, accessibility, and insets

Compact uses one/two columns according to available width; expanded uses a grid with optional detail pane. Cards have one clear click target and separate accessible overflow only when needed. Images with redundant names are decorative. Scaffold owns insets.

## Allowed ad slots

One labeled inline Yandex ad after the fourth culture card, only on eligible Android visits with sufficient content. Hidden state occupies zero space.

## Asset requirements

ASSET-022, ASSET-023, ASSET-024, ASSET-025, ASSET-026, ASSET-027, ASSET-028, ASSET-029; standard search, filter, add, source, and edit icons.

## Preview and golden matrix

EN compact catalog light/dark; RU 200%; expanded grid; personal recipes; search-empty; offline hidden-ad; eligible ad; missing optional image fallback; long personal recipe name.
