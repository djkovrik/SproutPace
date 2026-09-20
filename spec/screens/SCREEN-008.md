# SCREEN-008 — Recipe detail and editor

Linked flows: FLOW-001, FLOW-004, FLOW-006  
Linked requirements: REQ-001, REQ-004, REQ-006

## Purpose and hierarchy

Read a bundled guide or create/edit a personal recipe with stage guidance, typical ranges, source links, review date, and a simple local version. Built-in content is read-only; personal content is editable.

## States

- Bundled detail with provenance and Use recipe.
- Personal create/edit with validation.
- Outdated-review metadata warning without external approval workflow.
- Local save/delete failure with retained input.
- Offline: content and editing work; external source link reports connectivity only when opened.

## Actions and iconography

Use recipe opens SCREEN-004; Save, Edit, Duplicate, Delete, and Open source appear by mode. Use labeled Material actions and standard edit/copy/delete/link icons. No signature, approver, or attestation control exists.

## Text layout expectations

Stage ranges always pair value and unit. Source title/URL may wrap; version and review date remain readable. EN/RU built-in content uses shared resource keys; personal content is preserved verbatim.

## Responsive, accessibility, and insets

Compact stacks culture header, stages, and provenance. Expanded places structured stages beside supporting metadata/editor. Form errors are programmatically associated; links state destination. One scaffold owns insets.

## Allowed ad slots

None.

## Asset requirements

Applicable ASSET-022–029 culture cover; ASSET-016–020 stage vectors; standard edit, copy, delete, source, save, and navigation icons.

## Preview and golden matrix

Bundled radish detail light/dark; garden cress RU 200%; personal create/edit; invalid range; overdue review warning; long source URL; offline source-open failure; expanded stages.
