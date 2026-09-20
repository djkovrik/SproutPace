# Navigation ownership

`AppRootComponent` is the only navigation/composition component and owns the serializable screen stack and confirmation slot. It is rendered as the app shell, so it has a component-owned preview. There are no preview exclusions among SCREEN-001 through SCREEN-016: every rendered screen contract has a named `*ComponentPreview` implementation in `shared:component`.

Compact layouts use a bottom bar and a one-pane forward stack. Medium layouts use a collapsed rail. Expanded and larger layouts preserve selection and expose two or (only at extra-large width) three presentation panes without changing screen identity. Primary destination selection atomically replaces the stack and `pushNew` makes repeated detail clicks safe.
