# FLOW-012 — Use the app across locale, theme, size, and connectivity states

Linked requirements: REQ-012  
Linked screens: SCREEN-001, SCREEN-002, SCREEN-003, SCREEN-005, SCREEN-006, SCREEN-007, SCREEN-011

## Goal

Keep every core workflow understandable and usable in EN/RU, light/dark/system themes, supported window sizes, assistive technology, and offline conditions.

## Entry and exit conditions

Enter whenever configuration, window size, accessibility scale, or connectivity changes. Exit when the current screen has re-rendered without losing in-progress product state.

## Ordered steps

1. Resolve locale and theme preference.
2. Select compact, medium, expanded, large, or extra-large adaptive structure.
3. Reflow text and controls while preserving focus, semantics, and destination.
4. Keep local product actions available regardless of network state.

## Branches and resume

Unsupported system locales fall back to English. Network-only secondary surfaces such as ads disappear offline; local data and bundled catalog remain authoritative.

## AC-120

Given the device locale is English, Russian, or another supported system locale
When SCREEN-001, SCREEN-002, SCREEN-003, SCREEN-005, SCREEN-006, SCREEN-007, or SCREEN-011 is opened
Then production EN or RU resources are used consistently, unsupported locales fall back to EN, and no user-authored text is translated.

## AC-121

Given theme preference is System, Light, or Dark on SCREEN-011
When system appearance changes or the user selects a preference
Then all listed core screens re-render with semantic Material roles, readable contrast, and the chosen preference persists.

## AC-122

Given font scale reaches 200%, a screen reader or keyboard is used, or the window crosses an adaptive breakpoint
When the user navigates and operates SCREEN-001, SCREEN-002, SCREEN-003, SCREEN-005, SCREEN-006, SCREEN-007, or SCREEN-011
Then content reflows without clipping critical text/actions, focus and reading order remain logical, touch targets remain at least 48dp, and one safe-area owner consumes insets once.

## AC-123

Given the device has no network connection
When the user opens SCREEN-002, SCREEN-003, SCREEN-005, SCREEN-006, or SCREEN-007 and performs a local core action
Then bundled catalog, plans, batches, observations, history, and settings remain usable while network-only ad content is absent.
