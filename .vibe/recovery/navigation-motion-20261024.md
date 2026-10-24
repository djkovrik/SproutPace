# Navigation motion — 2026-10-24

Focused maintenance request: slide between tabs; fade + scale for opening/closing supplementary screens and Decompose confirmation windows. Navigation ownership remains in AppRootComponentDefault (ChildStack and ChildSlot). RootUiAdapter supplies the serialized configuration as UI identity. NavigationMotion owns Compose transitions; App keeps its navigation chrome outside them. Includes Trays categories, RTL direction, suppressed outgoing actions/focus/ads, and retained dialog/source snapshots through exit. No dependency, translation, AppSpec or navigation-rule changes.

Lazyweb evidence: https://www.lazyweb.com/agentic-search/00ff3d71-177e-4c93-bafe-c8107e55e7c4 — distinguishes primary navigation and supplementary surfaces. Static references do not establish motion timings; slide and fade/scale implement the user's explicit instruction.

Targeted motion tests: 10 passed in RECEIPT-82fdda76-ccee-4852-9bb0-1b1dcdd645d9. Intermediate pixels prove direction/fade/scale, same-screen entity identity, immediate model updates, dialog exit retention, inert outgoing callbacks, and stationary real compact/expanded navigation. Production frames: shared/compose/build/reports/navigation-motion/.

Full Compose/Paparazzi attempt RECEIPT-7ec8e59b-edbc-4027-a423-1427d5a540c3: 473/474 passed; one pre-existing test assumed SetLayout arrived after its first frame. ReviewAccessibilityProductionTest now asserts exact card actions separately from the exact single COMPACT layout event. No production callback behavior changed or assertion dropped. Follow-up RECEIPT-705f25b4-c141-4934-a47a-02096120544c passed all 10 motion tests plus that EN/RU accessibility scenario. Wide motion fixture now explicitly sets landscape to retain its requested width. Representative compact and wide production motion frames were visually inspected.

Final affected matrix PASSED in RECEIPT-3e98be5d-2091-44e7-8d0d-1af3b9767d34 (6m12s): Detekt, Android debug APK, Compose iOS Arm64 and Simulator Arm64 production compilation, 30 root tests, all 474 Compose host tests (including the 10 new motion tests), and Paparazzi verification against existing goldens. Zero test failures/errors/skips; no golden was recorded or changed. git diff --check passed. No production or test source changed after this run; this recovery-result update is metadata only.

Next action: report this focused request complete. This does not close prior whole-app native acceptance gaps or establish release readiness; iOS runtime/Xcode cannot be verified on Windows.
