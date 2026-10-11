# Navigation ownership

`AppRootComponent` is the only navigation/composition component and owns the serializable screen stack and confirmation slot. It is rendered as the app shell, so it has a component-owned preview. There are no preview exclusions among SCREEN-001 through SCREEN-016: every rendered screen contract has a named `*ComponentPreview` implementation in `shared:component`.

Compact layouts use a bottom bar and a one-pane forward stack. Medium layouts use a collapsed rail. Expanded and larger layouts preserve selection and expose two or (only at extra-large width) three presentation panes without changing screen identity. Primary destination selection atomically replaces the stack and `pushNew` makes repeated detail clicks safe.

App Back, system Back, primary selection, and opening another screen use the same unsaved-change guard. A dirty screen remains active until the user explicitly confirms discard; cancelling or dismissing the confirmation preserves its draft. The confirmation config serializes the requested navigation destination, and confirmed navigation clears the draft before changing the stack. Stale dialog callbacks cannot resolve a newer confirmation.

Explicit dependency creation from the batch editor preserves its draft beneath the zone or tray editor. A serialized return field selects the saved dependency on return. Browsing primary destinations from onboarding retains the unfinished setup beneath them; resuming reuses that component and successful setup clears its navigation history. Screen drafts also register with StateKeeper so pending navigation and draft content restore together.
