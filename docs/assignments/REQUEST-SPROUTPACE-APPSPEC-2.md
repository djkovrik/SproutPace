# SproutPace AppSpec 2.0 delivery request

Captured from the user request in this Codex task on 2026-09-20.

Implement the approved AppSpec 2.0 for SproutPace from `spec/app-spec.json` and all normative files under `spec/`. Do not repeat product discovery or change product decisions unless a contradiction is found. Preserve all existing user changes.

Required delivery constraints:

- Android is the publication target.
- iOS must be fully implemented and buildable on macOS and CI, including required `expect`/`actual` and native integrations.
- Yandex advertising must remain safely disabled on iOS because no iOS ad project or ad unit exists.
- Missing external secrets and identifiers must not block implementation. Add safe configuration contracts, templates, and setup instructions for Firebase Android/iOS, the Android Yandex ad unit, the privacy-policy URL, and approved consent copy.
- Do not add external approvals, signatures, or approval workflows for the built-in catalog. Local version, source, and review date are sufficient.
- Do not expand scope beyond the approved requirements.
- Put every user-visible string in production EN/RU resources.
- Implement the specified tests, Compose previews, Paparazzi goldens, accessibility/adaptive checks, Android/iOS CI, and release-readiness documentation.
- After goldens exist, run the specified Lazyweb design review and apply justified improvements without changing product scope.
- Verify each stage with builds and tests. Do not claim completion with open acceptance criteria or unverified quality gates.
- Do not publish the app or create external Firebase or advertising projects.

Final handoff must include requirement/acceptance traceability, Android/iOS build and quality-gate results, only the remaining external configuration actions, links to key changed files, and an independent final audit using `vibe-acceptance-auditor`.
