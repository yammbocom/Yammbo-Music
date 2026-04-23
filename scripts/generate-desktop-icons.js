#!/usr/bin/env node
// Regenerates desktop installer icons from the canonical branding PNG.
//
// Inputs:
//   assets/branding/yammbo-music-app-icon.png (512x512 RGBA)
//
// Outputs (committed to git — see composeApp/desktop-icons/):
//   icon-windows.ico  (multi-res: 16/24/32/48/64/128/256)
//   icon-mac.icns     (multi-res ICNS, BILINEAR)
//   icon-linux.png    (straight copy of source)
//
// Usage:
//   cd <repo-root>
//   npm install --no-save png-to-ico@^2.1.8 png2icons
//   node scripts/generate-desktop-icons.js
//
// Why this script and not ImageMagick: we don't require dev machines to have
// ImageMagick installed. These two npm packages are pure-JS and work on any
// Node 18+ install. Run this when the source branding PNG changes, then
// commit the generated icons.

const fs = require('fs');
const path = require('path');

const REPO_ROOT = path.resolve(__dirname, '..');
const SRC = path.join(REPO_ROOT, 'assets', 'branding', 'yammbo-music-app-icon.png');
const OUT = path.join(REPO_ROOT, 'composeApp', 'desktop-icons');

const pngToIco = require('png-to-ico');
const png2icons = require('png2icons');

(async () => {
    if (!fs.existsSync(SRC)) {
        console.error(`source not found: ${SRC}`);
        process.exit(1);
    }
    fs.mkdirSync(OUT, { recursive: true });
    const srcBuf = fs.readFileSync(SRC);

    const ico = await pngToIco(SRC);
    fs.writeFileSync(path.join(OUT, 'icon-windows.ico'), ico);
    console.log(`wrote icon-windows.ico (${ico.length} bytes)`);

    const icns = png2icons.createICNS(srcBuf, png2icons.BILINEAR, 0);
    if (!icns) throw new Error('png2icons.createICNS returned null');
    fs.writeFileSync(path.join(OUT, 'icon-mac.icns'), icns);
    console.log(`wrote icon-mac.icns (${icns.length} bytes)`);

    fs.copyFileSync(SRC, path.join(OUT, 'icon-linux.png'));
    console.log(`wrote icon-linux.png (copy)`);
})().catch((err) => {
    console.error(err);
    process.exit(1);
});
