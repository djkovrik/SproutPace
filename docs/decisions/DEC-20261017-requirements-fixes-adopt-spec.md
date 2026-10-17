# Adopt current approved specification — 2026-10-17

The user explicitly authorized `lean adopt-spec` in the attached implementation request:

> Это поручение включает обновление привязки к текущей утверждённой spec штатным lean adopt-spec с сохранённым основанием и проверкой фактического diff. Продуктовые требования ради устранения расхождения не ослабляй.

The current `spec/` is the approved source, not a proposed product revision. No specification files are changed by this maintenance work. The previously adopted `app-spec.json` byte hash was `060b435f6b00cdac581fb164dfb5b16368e56e145ef254632868fefc2bf56b81`; the current Windows working-file hash is `11673c5c2c7e50a0c9713bac12f7ddeb2cd229c53e6acadebc70b18cacdc7812`. All other normative file hashes still match the previous adopted inventory.

The old byte image is not retained in the available Git revisions, so an exact textual diff to that byte image cannot be reconstructed. The actual available Git diff (`863789b..37d0da6`, `spec/app-spec.json`) changes only asset briefs, acquisition/variant metadata and dimensions/transparency for ASSET-016–029. The current working spec equals HEAD (including normal Git CRLF checkout conversion). The canonical inventory remains 12 requirements, 9 exclusions, 79 acceptance scenarios, 50 required managed operations and 12 quality gates. No requirement or acceptance surface is removed or weakened. Preserve the previous inventory/hash in source evidence and revalidate all current surfaces independently.

This adoption invalidates prior audit claims. It does not declare implementation completeness or release readiness. Missing macOS/Xcode, native runtime, accessibility and conditional external evidence must remain visible in the fresh audit.
