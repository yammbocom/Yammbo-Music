#!/usr/bin/env bash
# Downloads VLC 3.0.21 macOS runtime into composeApp/lib-mac/.
#
# VLC for macOS ships as a .dmg containing VLC.app. We only need the native
# libraries + plugins from inside the bundle, so this script mounts the
# .dmg, copies the files, and unmounts.
#
# Targets: Intel (x86_64) and Apple Silicon (arm64) are both covered by the
# universal binary that VideoLAN ships; we pull the x86_64 image since
# macOS runners default to that, but VLCJ picks up the right slice at
# runtime.
#
# Runs from any CWD — resolves paths relative to script location.
set -euo pipefail

VLC_VER="3.0.21"
DMG_NAME="vlc-${VLC_VER}-universal.dmg"
DMG_URL="https://download.videolan.org/pub/videolan/vlc/${VLC_VER}/macosx/${DMG_NAME}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEST="$REPO_ROOT/composeApp/lib-mac"

if [[ -f "$DEST/libvlc.dylib" && -d "$DEST/plugins" ]]; then
  echo "[vlc-mac] already present at $DEST — skipping"
  exit 0
fi

TMP="$(mktemp -d)"
trap "hdiutil detach /Volumes/VLC-${VLC_VER} >/dev/null 2>&1 || true; rm -rf '$TMP'" EXIT

echo "[vlc-mac] downloading $DMG_URL"
curl -fsSL -o "$TMP/vlc.dmg" "$DMG_URL"
echo "[vlc-mac] mounting"
hdiutil attach -quiet -nobrowse -mountpoint "$TMP/mnt" "$TMP/vlc.dmg"

mkdir -p "$DEST"
APP="$TMP/mnt/VLC.app"
cp "$APP/Contents/MacOS/lib/libvlc.5.dylib"     "$DEST/libvlc.dylib"
cp "$APP/Contents/MacOS/lib/libvlccore.9.dylib" "$DEST/libvlccore.dylib"
cp -R "$APP/Contents/MacOS/plugins"             "$DEST/plugins"

hdiutil detach -quiet "$TMP/mnt"

echo "[vlc-mac] done — size: $(du -sh "$DEST" | cut -f1)"
