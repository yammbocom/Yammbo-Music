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
