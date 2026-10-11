# PNG illustration review — 2026-10-11

Scope: ASSET-016..021, focused visual maintenance. The user explicitly chose approved PNG artwork over XML reconstruction. Runtime copies are byte-identical to material-flat candidates. Stage display size is 96x72dp; empty-state shelf is 240x160dp. No tint, shared transparent PNG fallback in both themes.

Reviewed all 59 changed/new production captures listed with hashes in manifest.json, on sheets 01–10. Coverage includes Today/Trays/Plan empty states, recipe stages, batch history and harvest forms, dark/light themes, Russian 200% text and expanded layouts. Added tall dark recipe coverage alongside the existing light preview.

Findings: silhouettes remain distinct at production sizes; seeds, lid weight, leaves and scissors render within their bounds. Pale rims and leaves separate the dark trays from dark card surfaces. The shelf retains the original PNG softness, but no conspicuous rectangular background or broad halo is visible at production size on either theme. No clipping, overlaps or text/layout displacement introduced by the artwork. Labels and decorative null descriptions remain unchanged. Approved original PNGs retained without further image edits.

This is visual acceptance of the illustration delta, not full-app release acceptance. Windows cannot establish iOS Simulator/Xcode execution; macOS CI remains required.
