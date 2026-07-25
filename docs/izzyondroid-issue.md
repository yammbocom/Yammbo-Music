# IzzyOnDroid inclusion request

Open a new issue at https://codeberg.org/IzzyOnDroid/repo  (label: "App Inclusion")
and paste the following:

---

**App name:** Yammbo Music

**Package ID:** `com.yambo.music`

**Source / repository:** https://github.com/yammbocom/Yammbo-Music

**License:** GPL-3.0-or-later

**Brief description:** Open-source music and podcast player (YouTube Music frontend).

**Releases:** https://github.com/yammbocom/Yammbo-Music/releases

Please track the **FOSS** variant asset only: `YammboMusic-foss-release-<version>.apk`.
The `-full-` asset bundles Firebase and is meant for direct download; the `-foss-` build
contains **no ads, no trackers and no Google/Firebase dependencies**.

**Suggested APK filter (regex):** `YammboMusic-foss-release-.*\.apk`

**Anti-features:** `NonFreeNet` — online content is played through the YouTube IFrame
Player API.

**Fastlane metadata:** present at `fastlane/metadata/android/en-US/` in the repo.

---

Notes for us:
- Both `full` and `foss` share the same applicationId (`com.yambo.music`) and versionCode,
  so make sure the APK filter above pins IzzyOnDroid to the `-foss-` asset.
- After acceptance, add the IzzyOnDroid badge (`assets/images/getItIzzyOnDroid.png`) to the
  README / website.
