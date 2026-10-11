# Catalog source review — 2026-10-10

Scope: the eight bundled cultures in [SproutPaceCatalog.kt](../shared/domain/src/commonMain/kotlin/com/sedsoftware/sproutpace/domain/catalog/SproutPaceCatalog.kt), against [QG-011](../spec/quality.md#catalog-quality). This review does not change catalog values, version 1, its recorded 2026-09-20 content-review date, or the approved specification. It identifies evidence still needed; it is not a content-quality pass.

## Confirmed sources and limits

The catalog cites two primary sources: [Penn State's growing guide](https://extension.psu.edu/a-step-by-step-guide-for-growing-microgreens-at-home) and [The ABCs of Microgreens](https://extension.psu.edu/the-abcs-of-microgreens). Direct retrieval returned HTTP 403 during this review; official search-indexed content was available. These sources support general growing practices and explain that seed density depends on species, seed weight and germinability. The ABCs page points to a seed-density calculator and gives broad growth-cycle guidance.

This confirms the sources' relevance, not the exact eight numerical recommendations. No checked-in derivation identifies the source table/calculator version, input assumptions or unit conversion for each density and stage range. Every density currently points to the general growing guide. Do not represent an exact constant as independently substantiated by that attribution alone.

## Values awaiting numerical substantiation

| Culture ID | Density, g/cm² | Soaking, hours | Blackout, days | Value named `harvestDays` |
|---|---:|---:|---:|---:|
| radish | 0.028 | Not configured | 3–4 | 7–10 |
| broccoli | 0.022 | Not configured | 3–4 | 8–12 |
| arugula | 0.018 | Not configured | 3–4 | 8–12 |
| mustard | 0.020 | Not configured | 3–4 | 7–10 |
| pea | 0.085 | 8–12 | 3–5 | 10–14 |
| sunflower | 0.090 | 8–12 | 3–5 | 9–13 |
| basil | 0.014 | Not configured | 4–6 | 14–21 |
| garden_cress | 0.018 | Not configured | 2–3 | 7–10 |

The code places `harvestDays` in the LIGHT_GROWTH stage. [BatchPlanningEngine](../shared/domain/src/commonMain/kotlin/com/sedsoftware/sproutpace/domain/engine/BatchPlanningEngine.kt) then accumulates that duration and blackout. Consequently radish spans 10–14 days after sowing and basil 18–27, excluding optional soaking. If the input means total sow-to-harvest time, blackout is counted twice; if it means time under light, the name and source evidence need to make that interpretation explicit. The present evidence does not resolve which interpretation was intended. Do not change timing merely from the variable name or a broad source range.

## Copy and evidence follow-up

[LocalizedText.kt](../shared/compose/src/commonMain/kotlin/com/sedsoftware/sproutpace/compose/LocalizedText.kt) resolves culture-specific guidance keys to shared generic EN/RU guidance. Resource completeness therefore does not by itself establish crop-specific instruction fidelity. Existing safety copy includes clean tools/water, untreated seed, drainage, allergy awareness and non-diagnostic images; its applicability and paraphrasing still belong in the content review.

The smallest evidence repair is a per-culture source/derivation record covering density units, stage applicability, range interpretation and any app-authored adaptation. Retrieve and review the primary numeric evidence before claiming a fresh content-review date. If a recommendation is an app estimate, describe it as such rather than attributing its exact value to a general article. No external approval or invented growing trial is required by QG-011.

Related evidence: [asset inventory](assets/ASSET-INVENTORY.md), [current preview review](design-review/ux-preview-review.json), and the independent audit's F09 finding in `.vibe/audits/AUDIT-REQUEST-73591e2f-df2a-4935-b3a2-216472ef3854/audit.json`. Screenshot review and passing resource tests cannot close the unresolved numerical-source questions.
