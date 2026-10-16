#!/usr/bin/env bash
# Imports Material Symbols Rounded (24dp) vector drawables from the local Android SDK icon set,
# as the Obsidian README prescribes. Strips the theme-attr tint so icons are tinted at use.
#
#   tools/import-icons.sh <res/drawable dir> <symbol> [<symbol>…]
#   tools/import-icons.sh core/ui/src/main/res/drawable chat star settings
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
SRC="$SDK/icons/material/materialsymbolsrounded"
DEST="$1"; shift
mkdir -p "$DEST"

for name in "$@"; do
  file="$SRC/$name/rounded_${name}_24.xml"
  if [[ ! -f "$file" ]]; then
    echo "missing symbol: $name" >&2
    exit 1
  fi
  sed -E 's/[[:space:]]*android:tint="[^"]*"//' "$file" > "$DEST/ic_${name}.xml"
done
echo "imported $# icon(s) into $DEST"
