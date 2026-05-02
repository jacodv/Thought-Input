# Thought Input — Branding

This directory holds the master artwork for the Thought Input app icon ("Concept A": head profile + lightbulb + captured-thought lines). All downstream assets (macOS PNGs, Android VectorDrawables) are derived from these SVGs.

## Files

- `logo-full.svg` — Full color mark on navy background. Used for the macOS AppIcon at every size.
- `logo-mark.svg` — Single-color silhouette (head + lightbulb only, no text lines). Used for the macOS menu bar template glyph and as the visual reference for the Android Quick Settings tile and widget glyph (which are committed as VectorDrawable XML).
- `render.sh` — Renders the two SVGs to all required PNG outputs in the macOS asset catalog.

## Workflow

1. Edit `logo-full.svg` and/or `logo-mark.svg`.
2. If you need `rsvg-convert`: `brew install librsvg`.
3. Run `./render.sh` from this directory.
4. Inspect the rendered PNGs in `macos-app/Sources/ThoughtInput/Resources/Assets.xcassets/`.
5. If the Android marks need updating to match, hand-edit:
   - `android-app/app/src/main/res/drawable/ic_brain.xml` (adaptive launcher foreground — full mark)
   - `android-app/app/src/main/res/drawable/ic_logo_mark.xml` (widget glyph — silhouette)
   - `android-app/app/src/main/res/drawable/ic_capture.xml` (Quick Settings tile — silhouette)
6. Commit both the SVG sources and the rendered PNGs/XML — they are build artifacts that ship with the apps and aren't regenerated at build time.

## Color tokens (Concept A)

| Token | Hex | Use |
|---|---|---|
| Navy background | `#0F1B2D` | AppIcon background, Android adaptive icon background |
| Head outline / dark accents | `#1F1F2E` | Filament, base of bulb (on light backgrounds) |
| Lightbulb yellow | `#FFC83D` | Bulb body, glow rays |
| Cursor blue | `#3B82F6` | Captured-thought cursor in the full mark |
| Outline / text on navy | `#FFFFFF` | Head outline and text-line glyphs on the dark icon background |
