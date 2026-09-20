# FLOW-003 — Browse catalog and manage recipes

Linked requirements: REQ-003  
Linked screens: SCREEN-004, SCREEN-005, SCREEN-007, SCREEN-008

## Goal

Use versioned, sourced offline guidance and maintain personal recipes without changing past batches.

## Entry and exit conditions

Enter from Catalog or a recipe selector. Exit after reading guidance, selecting a recipe, or saving/deleting a personal recipe.

## Ordered steps

1. Browse built-in and personal entries.
2. Open ranges, source metadata, safety guidance, and seed recommendation inputs.
3. Use, duplicate, create, edit, or delete a personal recipe.
4. Return a stable recipe ID/snapshot to the calling flow.

## Branches and resume

Built-in entries are read-only. Invalid personal values remain in the editor with actionable errors. Offline state does not remove bundled content.

## AC-020

Given the app is offline or online
When the user opens SCREEN-007 or a built-in entry on SCREEN-008
Then all eight stable cultures, catalog version/review date, localized ranges, source metadata, and illustrations are readable from bundled data.

## AC-021

Given the user opens personal recipe creation on SCREEN-008
When they save a valid name and stage configuration
Then a new UserGrowRecipe is persisted and appears separately from the built-in catalog on SCREEN-007.

## AC-022

Given a UserGrowRecipe was saved
When the app restarts and SCREEN-007 or SCREEN-008 opens
Then its user-authored name, stages, substrate, supplier/variety/lot notes, and optional germination value are restored.

## AC-023

Given a UserGrowRecipe has already produced a GrowBatch on SCREEN-005
When the recipe is edited on SCREEN-008
Then future batches use the update and the existing batch retains its original snapshot.

## AC-024

Given a UserGrowRecipe is referenced by old batches
When the user confirms recipe deletion on SCREEN-008
Then the recipe is removed from SCREEN-007 future choices and referenced SCREEN-003/SCREEN-005 history stays readable.

## AC-025

Given a built-in or personal recipe is open on SCREEN-008
When the user chooses Duplicate
Then a new editable UserGrowRecipe is created with copied parameters and a distinct identity.

## AC-026

Given SCREEN-004 or SCREEN-008 has a valid tray area and recipe density
When the recommendation is evaluated
Then seed mass is displayed in grams as an editable recommendation without a yield promise.

## AC-027

Given a built-in recipe is open on SCREEN-008 without network
When the user reads safety and source sections
Then original localized safety guidance and stored source metadata remain available and no image is presented as diagnostic evidence.

