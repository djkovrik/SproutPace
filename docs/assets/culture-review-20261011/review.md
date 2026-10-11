# Culture illustration review — 2026-10-11

Scope: ASSET-022..029. Eight PNGs generated with built-in image_gen using the approved flat stage-art family. Exact prompts, selected tool outputs and runtime destinations are in docs/assets/generation/material-flat-cultures.json. No vector conversion or raster postprocessing. Transparent 1536x1024 exports.

Lazyweb evidence: search `plant catalog cards` returned PictureThis plant library cards and Plantin collection references. Retained the existing labeled card structure and used the approved SproutPace flat palette; no product flow change. Workflow capability guide returned 404, screenshot search succeeded.

Fixed actual cropping: culture images used ContentScale.Crop in catalog, recipe, batch selected-culture and plan thumbnails. Changed these sites to Fit, preserving container sizes and decorative semantics. Source transparency and aspect ratio now survive narrow, square and expanded placements.

Reviewed all 39 captures in manifest.json (7 sheets), including two new full-catalog production previews at393x1800dp in light/dark. All eight plant/tray silhouettes are complete with padding; no source-edge clipping, solid background rectangles or visible glow. Thin details remain supplemental to localized labels. Tall catalog screenshots prove lower-row sunflower, basil and garden cress; catalog-themes.png places both complete captures side by side. Phone viewports naturally scroll: partially visible lower cards at viewport boundaries are not image cropping.

Also inspected recipe light/dark, Russian200%, expanded split views, plan64dp thumbnails and selected-culture112x84dp. Catalog images occupy124dp height, recipe156dp. No introduced layout overlap or truncation. Existing floating action button may overlay a lower viewport card while scrolling; complete illustration can be brought into view. No change to user-photo cropping.

This review supersedes prior screenshot evidence for the changed recipe captures, while preserving the previously accepted stage PNGs. Focused maintenance only; iOS runtime/Simulator/Xcode remains a macOS CI obligation.
