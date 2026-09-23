# SproutPace brand refresh — 2026-09-23

The user approved logo 1B-1 and chose palette A, Forest & Lime, from three light/dark proposals in `brand-palettes.html`.

Static brand colors remain deterministic on Android and iOS. Light actions use forest green #105C3B; dark actions use lime #A5D66D with dark-green text. Primary containers use fresh pale green / deep foliage. Secondary is botanical olive and tertiary remains restrained warm harvest gold. Error colors retain their established semantics. All neutral surface elevations, inverse roles and outlines now belong to the same palette. Layout, strings, typography, navigation and theme preference behavior remain unchanged.

Research: [Lazyweb plant-care references](https://www.lazyweb.com/agentic-search/5fa3e3b9-6b29-4a1d-b6c6-65a3525d47ea) (PlantIn dark mode and PictureThis task dashboard). These are qualitative adjacent references, not proof of conversion or a copied interface. The approved logo owns the actual palette.

Implementation uses existing Material 3 color roles; no new or experimental component API is introduced. [Material 3 role guidance](https://developer.android.com/develop/ui/compose/designsystems/material3). Native adaptive layers follow the [Android icon contract](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).

Verification results and boundaries are recorded in `docs/assets/brand-refresh-review.md` after rendering.
