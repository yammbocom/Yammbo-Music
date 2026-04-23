# GitHub Actions Workflows

## `release.yml` — Signed release APK on tag push

### One-time setup (required before first use)

Add the following secrets in **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value | How to obtain |
|---|---|---|
| `KEYSTORE_BASE64` | base64-encoded keystore file | `base64 -w 0 yambo-digital.keystore > keystore.b64` → paste contents |
| `SIGNING_STORE_PASSWORD` | Keystore password | Same as `SIGNING_STORE_PASSWORD` in `local.properties` |
| `SIGNING_KEY_ALIAS` | Key alias inside the keystore | Same as `SIGNING_KEY_ALIAS` in `local.properties` (e.g. `yambo`) |
| `SIGNING_KEY_PASSWORD` | Key password | Same as `SIGNING_KEY_PASSWORD` in `local.properties` |
| `GOOGLE_SERVICES_JSON_BASE64` | base64-encoded `composeApp/google-services.json` | `base64 -w 0 composeApp/google-services.json > gs.b64` → paste contents |

### Encoding commands (Git Bash on Windows)

```bash
# Keystore
base64 -w 0 /c/Users/river/Yammbo-Music/yambo-digital.keystore > /c/tmp/keystore.b64
# google-services.json
base64 -w 0 /c/Users/river/Yammbo-Music/composeApp/google-services.json > /c/tmp/gs.b64
```

Then open each `.b64` file and paste the **entire single-line content** into the GitHub secret value.

### Triggering a release

**Option A — tag push (recommended):**
```bash
# Bump composeApp/build.gradle.kts versionCode + versionName, commit, then:
git tag v0.7.73
git push origin v0.7.73
```

**Option B — manual from GitHub UI:**
Go to **Actions → Release → Run workflow**, enter the version (e.g. `v0.7.73`).

### What the workflow does

1. Checks out source
2. Decodes the keystore and `google-services.json` into the runner's temp directory (never committed)
3. Builds the **signed release APK** (`assembleFullRelease`)
4. Verifies the APK signature with `apksigner`
5. Computes SHA-256 for integrity verification
6. Creates a GitHub Release (or updates existing) with the APK attached and auto-generated changelog

### Security notes

- The keystore and google-services.json are **never committed**. They are injected as runtime-only files from encrypted GitHub Secrets.
- Secrets are masked in CI logs automatically by GitHub.
- Only users with `contents: write` permission can trigger releases.
- The workflow uses `--no-daemon` to prevent daemon-related caching issues across runs.

### Troubleshooting

- **"KEYSTORE_BASE64 secret is missing"** — you haven't added the secret yet. See setup above.
- **"No APK produced"** — check the gradle log above. Usually a ProGuard/R8 rule issue; run `./gradlew :composeApp:assembleFullRelease` locally to reproduce.
- **Signature verification fails** — keystore mismatch. Re-encode the exact keystore file that signed the previous release.

---

## `desktop-release.yml` — Windows `.msi` on tag push

Runs alongside `release.yml` on the same `v*` tag. Builds the Windows desktop installer (`.msi`) and attaches it to the same GitHub Release as the APK.

### Secrets required

Reuses `GOOGLE_SERVICES_JSON_BASE64` — no additional secrets. The installer is **not code-signed** yet (see TODO below).

### What the workflow does

1. Checks out source on `windows-latest`
2. Decodes `google-services.json` (needed because the shared Gradle plugin graph references it even though the desktop JVM build doesn't use Firebase)
3. Runs `scripts/download-vlc-win64.sh` to fetch VLC 3.0.21 into `composeApp/lib/`
4. Builds the `.msi` via `./gradlew :composeApp:packageReleaseMsi`
5. Attaches the installer to the existing GitHub Release (created by `release.yml`) with `append_body: true`

### Matrix build (Windows + macOS + Linux)

The workflow uses a 3-entry matrix — each job runs on its native OS runner, downloads the corresponding VLC binaries (`scripts/download-vlc-{win64,mac,linux-x64}.sh`), and produces one installer:

| OS | VLC script | Gradle task | Artifact |
|---|---|---|---|
| Windows | `download-vlc-win64.sh` | `packageReleaseMsi` | `YammboMusic-<tag>-win64.msi` |
| macOS | `download-vlc-mac.sh` | `packageReleaseDmg` | `YammboMusic-<tag>-macos.dmg` |
| Linux | `download-vlc-linux-x64.sh` | `packageReleaseDeb` | `YammboMusic-<tag>-amd64.deb` |

`fail-fast: false` means one OS failing does not cancel the others.

### TODO: code signing

Unsigned installers trigger warnings on Windows (SmartScreen) and macOS (Gatekeeper). Signing requires paid certificates and the full workflow scaffolding lives in [`docs/desktop-code-signing.md`](../../docs/desktop-code-signing.md) — copy the relevant step into this workflow once you have the certs and GitHub Secrets configured.

### Version format quirk

Native installers reject leading-zero major versions (`0.7.73` → `1.7.73` in `build.gradle.kts::desktopPackageVersion`). The version shown inside the app UI still reads from the Android `versionName`, so users see "0.7.73" regardless — only the filename / metadata shows "1.7.73".

### Building locally

The `packageReleaseMsi` task requires a JDK with `jpackage.exe`. Android Studio's bundled JBR does **not** include `jpackage`, so running the task with `JAVA_HOME` pointed at `C:\Program Files\Android\Android Studio\jbr` fails with *"Failed to check JDK distribution: 'jpackage.exe' is missing"*.

To test locally, install a full JDK 21 (e.g. [Eclipse Adoptium Temurin 21](https://adoptium.net/temurin/releases/?version=21)) and point `JAVA_HOME` at it before running:

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.x.x-hotspot"
./gradlew :composeApp:packageReleaseMsi
```

On CI this is a non-issue — `actions/setup-java@v4` with `distribution: corretto` ships `jpackage` by default.
