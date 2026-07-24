# Publishing Yammbo Music to FOSS stores

Yammbo Music has two build flavors:

| Flavor | Firebase / Google | Auto-updater | Where it goes |
|--------|-------------------|--------------|---------------|
| `full` | Yes (messaging + remote config) | Yes (GitHub Releases) | Direct download, Obtainium |
| `foss` | **No** (Firebase-free) | No | F-Droid, IzzyOnDroid |

Build commands:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
# Full (direct download / Obtainium)
./gradlew :composeApp:assembleFullRelease
#   -> composeApp/build/outputs/apk/full/release/YammboMusic-full-release-<ver>.apk
# FOSS (F-Droid / IzzyOnDroid) — Firebase-free
./gradlew :composeApp:assembleFossRelease
#   -> composeApp/build/outputs/apk/foss/release/YammboMusic-foss-release-<ver>.apk
```

> The `google-services` plugin is only applied when the Gradle task name contains
> `Full`, so `assembleFossRelease` never touches `google-services.json`.

---

## 1. Obtainium — works today, zero review

Obtainium installs directly from your GitHub Releases. No submission needed.

1. Make sure each release keeps a stable APK asset name (`YammboMusic-*-release-<ver>.apk`).
2. Tell users to add the app in Obtainium with source URL:
   `https://github.com/yammbocom/Yammbo-Music`
3. Optional: publish a one-tap "Add to Obtainium" deep link on your site:
   `obtainium://add/https://github.com/yammbocom/Yammbo-Music`

You can point Obtainium at either flavor; the `foss` releases give a Google-free install.

---

## 2. IzzyOnDroid — takes your signed APK, no rebuild

Requirements (all met except the release + issue):
- Public source repo ✅ (`github.com/yammbocom/Yammbo-Music`)
- Release-signed APK, not debuggable ✅
- Fastlane metadata ✅ (`fastlane/metadata/android/en-US/`)

Steps:
1. Build `assembleFossRelease` and attach the `YammboMusic-foss-release-*.apk` to a
   GitHub Release tagged `v<versionName>` (e.g. `v0.7.105`).
2. Keep the Fastlane metadata up to date (title, short/full description, `changelogs/<versionCode>.txt`).
   Add screenshots at `fastlane/metadata/android/en-US/images/phoneScreenshots/`.
3. Open an inclusion request issue at the IzzyOnDroid repo (Codeberg):
   https://codeberg.org/IzzyOnDroid/repo  → new issue → "Request for adding an app".
   Provide: package `com.yambo.music`, repo URL, and note the `foss` flavor + `NonFreeNet` anti-feature.
4. Add the badge to your README/site (`assets/images/getItIzzyOnDroid.png`).

IzzyOnDroid keeps **your** signature (no rebuild). This is where RiMusic / InnerTune / Metrolist live.

---

## 3. F-Droid (official) — they build from source, re-sign

Most work + slow (1–2 week cycles), but max reach. Do it after IzzyOnDroid.

1. Fork `fdroiddata` on GitLab: https://gitlab.com/fdroid/fdroiddata
2. Add `metadata/com.yambo.music.yml` (template below).
3. Open a Merge Request. F-Droid builds the `foss` flavor from source with a 100% FLOSS
   toolchain and **re-signs with their key**.

Template `metadata/com.yambo.music.yml`:

```yaml
Categories:
  - Multimedia
License: GPL-3.0-or-later
AuthorName: Yammbo
WebSite: https://music.yammbo.com
SourceCode: https://github.com/yammbocom/Yammbo-Music
IssueTracker: https://github.com/yammbocom/Yammbo-Music/issues
Changelog: https://github.com/yammbocom/Yammbo-Music/releases

AntiFeatures:
  - NonFreeNet   # plays content from YouTube via the IFrame Player API

RepoType: git
Repo: https://github.com/yammbocom/Yammbo-Music.git

Builds:
  - versionName: 0.7.105
    versionCode: 105
    commit: v0.7.105
    subdir: composeApp
    sudo:
      - apt-get update
      - apt-get install -y openjdk-21-jdk-headless
    gradle:
      - foss
    rm:
      - composeApp/google-services.json
    prebuild: sed -i -e '/google-services.json/d' composeApp/build.gradle.kts   # if needed
    output: composeApp/build/outputs/apk/foss/release/YammboMusic-foss-release-0.7.105.apk

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags ^v[0-9.]+$
CurrentVersion: 0.7.105
CurrentVersionCode: 105
```

Notes / gotchas:
- The `foss` flavor already excludes Firebase, so no `Ads`/`Tracking`/`NonFreeDep` anti-features
  should apply — only `NonFreeNet` (YouTube).
- F-Droid needs the release tag/commit to be buildable with an open JDK (project targets JDK 21).
- Reproducible builds are optional but let F-Droid publish your own signature instead of theirs.

---

## 4. Accrescent (optional, invite-only)

Accrescent accepts proprietary code, so it could take the `full` build — but registration is
gated and it forbids self-updaters (disable the OTA updater for that build). Low priority.
