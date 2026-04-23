# VLC native libraries (desktop only)

This directory holds libvlc.dll / libvlccore.dll / plugins/ that VLCJ loads
at runtime. **Not committed to git** — contents are too large (~140 MB) and
can always be regenerated from the official VLC release.

## How to populate it

### Windows / Linux / Mac (bash)
```bash
./scripts/download-vlc-win64.sh
```
This script is idempotent; running it twice is a no-op.

### Manual (if no bash)
1. Download https://download.videolan.org/pub/videolan/vlc/3.0.21/win64/vlc-3.0.21-win64.zip
2. Extract
3. Copy `vlc-3.0.21/libvlc.dll`, `vlc-3.0.21/libvlccore.dll`, and the whole
   `vlc-3.0.21/plugins/` directory here.

## Why VLC?

VLCJ is the Kotlin wrapper, but the actual decoding happens in `libvlc` —
same LGPL-licensed engine that powers the VLC player. It handles every
codec the app can throw at it (YouTube/OPUS/M4A/etc.) without the license
hassle of bundling ffmpeg ourselves.

## How it gets into the `.msi` / `.dmg`

The Gradle task `stageVlcForPackaging` (see `composeApp/build.gradle.kts`)
copies this directory into `composeApp/lib-dist/windows/lib/` before any
`package*` task. Compose Desktop's `appResourcesRootDir` then injects
those files into the installer, so end users never need VLC installed.

At runtime, `VlcjController.init {}` calls
`NativeLibrary.addSearchPath(..., {user.dir}/lib/libvlc.dll)` — on an
installed bundle, `{user.dir}` is the app's install directory, and the
DLL lives there.

## Trimming (future optimization)

The `plugins/` folder ships ~130 MB. Roughly 90 MB are plugins we don't
need (video output for X11, skins, browser integration, broken-stream
recovery for niche codecs). A later task will prune this to ~30 MB using
`uk.co.caprica:vlcj-natives-lite` style selective copying.
