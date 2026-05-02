#!/usr/bin/env bash
# Render Thought Input branding artwork from SVG masters into all required raster outputs.
#
# Single source of truth:
#   logo-full.svg       — full color mark (head + lightbulb + captured-thought lines), used for the
#                         macOS AppIcon. Contains its own navy background.
#   logo-mark.svg       — single-color silhouette (head + lightbulb only), used for the macOS menu bar
#                         template glyph. Renders pure black on transparent for system tinting.
#   dmg-background.svg  — installer window background (540x380) used by the macOS DMG release.
#
# Outputs:
#   macos-app/.../AppIcon.appiconset/icon_{16,32,128,256,512}{,@2x}.png  (10 files)
#   macos-app/.../MenuBarIcon.imageset/menubar_{18,36}.png               (2 files)
#   assets/branding/dmg-background.png and dmg-background@2x.png         (DMG installer art)
#
# Android assets are committed as VectorDrawable XML and don't need rendering — see the
# corresponding .xml files under android-app/app/src/main/res/drawable/.

set -euo pipefail

if ! command -v rsvg-convert >/dev/null 2>&1; then
  echo "rsvg-convert not found. Install with: brew install librsvg" >&2
  exit 1
fi

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"

FULL="$HERE/logo-full.svg"
MARK="$HERE/logo-mark.svg"

APPICON="$REPO/macos-app/Sources/ThoughtInput/Resources/Assets.xcassets/AppIcon.appiconset"
MENUBAR="$REPO/macos-app/Sources/ThoughtInput/Resources/Assets.xcassets/MenuBarIcon.imageset"

mkdir -p "$APPICON" "$MENUBAR"

echo "Rendering AppIcon PNGs from logo-full.svg…"
for size in 16 32 128 256 512; do
  rsvg-convert -w "$size"        -h "$size"        "$FULL" -o "$APPICON/icon_${size}.png"
  rsvg-convert -w "$((size*2))"  -h "$((size*2))"  "$FULL" -o "$APPICON/icon_${size}@2x.png"
  echo "  icon_${size}.png  +  icon_${size}@2x.png"
done

echo "Rendering menu bar template glyphs from logo-mark.svg…"
rsvg-convert -w 18 -h 18 "$MARK" -o "$MENUBAR/menubar_18.png"
rsvg-convert -w 36 -h 36 "$MARK" -o "$MENUBAR/menubar_36.png"

echo "Rendering DMG installer background from dmg-background.svg…"
DMG_BG_SVG="$HERE/dmg-background.svg"
rsvg-convert -w 540  -h 380  "$DMG_BG_SVG" -o "$HERE/dmg-background.png"
rsvg-convert -w 1080 -h 760  "$DMG_BG_SVG" -o "$HERE/dmg-background@2x.png"

echo "Done. Outputs:"
ls -1 "$APPICON"/*.png "$MENUBAR"/*.png "$HERE"/dmg-background*.png
