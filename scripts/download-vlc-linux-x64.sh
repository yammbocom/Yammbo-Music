#!/usr/bin/env bash
# Downloads VLC 3.0.21 Linux x86_64 runtime into composeApp/lib-linux/.
#
# Note: unlike Windows, VLC does NOT ship an "all-in-one zip" for Linux.
# The official distribution is per-distro (.deb / .rpm / flatpak / snap /
# source tarball). For our JVM packaging purposes, what we actually need at
# runtime is `libvlc.so.5` + `libvlccore.so.9` + the `plugins/` directory.
# The simplest way to get these cross-distro is to extract them from the
# Debian package for the current stable release.
#
# Runs from any CWD — resolves paths relative to script location.
set -euo pipefail

VLC_VER="3.0.21"
DEB_NAME="vlc_3.0.21-1_amd64.deb"   # adjust if Debian repo renames
# Debian Bookworm hosts stable VLC 3.x — we stick to `snapshot.debian.org`
# mirrors for deterministic builds independent of rolling repo state.
DEB_URL="https://deb.debian.org/debian/pool/main/v/vlc/${DEB_NAME}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEST="$REPO_ROOT/composeApp/lib-linux"

if [[ -f "$DEST/libvlc.so.5" && -d "$DEST/vlc/plugins" ]]; then
  echo "[vlc-linux] already present at $DEST — skipping"
  exit 0
fi

TMP="$(mktemp -d)"
trap "rm -rf '$TMP'" EXIT

echo "[vlc-linux] downloading $DEB_URL"
curl -fsSL -o "$TMP/vlc.deb" "$DEB_URL"
echo "[vlc-linux] extracting"
cd "$TMP"
ar x vlc.deb
# Debian packages have data.tar.{xz,zst}; handle both.
if [[ -f data.tar.xz ]]; then tar -xf data.tar.xz; fi
if [[ -f data.tar.zst ]]; then zstd -d data.tar.zst && tar -xf data.tar; fi

mkdir -p "$DEST"
# libvlc + libvlccore live under /usr/lib/x86_64-linux-gnu/
SRC_LIB="./usr/lib/x86_64-linux-gnu"
cp "$SRC_LIB/libvlc.so.5"       "$DEST/"
cp "$SRC_LIB/libvlccore.so.9"   "$DEST/"
# VLCJ looks for "libvlc.so" as the generic symlink name — create it.
( cd "$DEST" && ln -sf libvlc.so.5 libvlc.so && ln -sf libvlccore.so.9 libvlccore.so )
# Plugins live under /usr/lib/x86_64-linux-gnu/vlc/plugins
mkdir -p "$DEST/vlc"
cp -r "./usr/lib/x86_64-linux-gnu/vlc/plugins" "$DEST/vlc/"

echo "[vlc-linux] done — size: $(du -sh "$DEST" | cut -f1)"
