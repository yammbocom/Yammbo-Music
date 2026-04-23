#!/usr/bin/env bash
# Downloads VLC 3.0.21 win64 binaries into composeApp/lib/
# Used by developers (local run) and GitHub Actions (packaging).
#
# Runs from any CWD — resolves paths relative to script location.
set -euo pipefail

VLC_VER="3.0.21"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEST="$REPO_ROOT/composeApp/lib"

if [[ -f "$DEST/libvlc.dll" && -d "$DEST/plugins" ]]; then
  echo "[vlc] already present at $DEST — skipping"
  exit 0
fi

TMP="$(mktemp -d)"
trap "rm -rf '$TMP'" EXIT

ZIP_URL="https://download.videolan.org/pub/videolan/vlc/${VLC_VER}/win64/vlc-${VLC_VER}-win64.zip"
echo "[vlc] downloading $ZIP_URL"
curl -fsSL -o "$TMP/vlc.zip" "$ZIP_URL"
echo "[vlc] extracting"
unzip -q "$TMP/vlc.zip" -d "$TMP/extract"

mkdir -p "$DEST"
SRC="$TMP/extract/vlc-${VLC_VER}"
cp "$SRC/libvlc.dll" "$DEST/"
cp "$SRC/libvlccore.dll" "$DEST/"
rm -rf "$DEST/plugins"
cp -r "$SRC/plugins" "$DEST/"

echo "[vlc] done — size: $(du -sh "$DEST" | cut -f1)"
