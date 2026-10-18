# F-Droid

Messages is built by F-Droid from this repository. F-Droid ships **our** signature, not its own: it
rebuilds each release from source, checks the result is byte-for-byte the APK on our GitHub release
(minus the signature), and then publishes our signed APK. So the F-Droid, GitHub and sideloaded builds
are the same app and update each other.

| File | What |
|---|---|
| `dev.redfox.messages.yml` | The build recipe, as it goes into [fdroiddata](https://gitlab.com/fdroid/fdroiddata) at `metadata/dev.redfox.messages.yml` |
| `../fastlane/metadata/android/en-US/` | Store listing: title, descriptions, icon, screenshots, and a changelog per version code |
| `../tools/release.sh` | Builds the signed release APK the way F-Droid builds it |
| `../tools/seed-demo-data.sh` | Fills an emulator with made-up conversations, for screenshots |

## How F-Droid builds it

F-Droid only downloads libraries from repositories it trusts (Maven Central, Google, …), and our Obsidian
Maven repository on GitHub Pages isn't one of them. So Obsidian comes from the `external/obsidian` git
submodule instead, built from source alongside the app: the recipe deletes the Pages repository line
from `settings.gradle.kts` and sets `obsidian.fromSource=true`. Keep the submodule on the same tag as
`obsidian` in `gradle/libs.versions.toml`.

## Releasing a version

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts` (literals; F-Droid reads them from
   there) and add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (500 characters max).
2. Commit, then `tools/release.sh`. It builds from a clean tree with Obsidian from source. Release APKs
   must come from this script: incremental or cached builds can differ by a few bytes, and then F-Droid's
   check fails.
3. Tag `v<versionName>`, push the tag, and create a GitHub release with `dist/Messages-<versionName>.apk`
   attached under exactly that name.

F-Droid notices the new tag by itself (`UpdateCheckMode: Tags`), adds a build entry to the recipe and
publishes it, usually within a few days.

## First submission (once)

1. Release v1.0.0 as above, so the tag and the signed APK exist.
2. Fork [fdroiddata](https://gitlab.com/fdroid/fdroiddata) on GitLab and copy `dev.redfox.messages.yml`
   to `metadata/` in the fork.
3. Check it with fdroidserver if you have it: `fdroid readmeta && fdroid lint dev.redfox.messages &&
   fdroid build -v -l dev.redfox.messages`.
4. Open a merge request with the "App inclusion" template and tick its checklist. Reviewers may ask for
   changes; answer them in the merge request.

The signing key's certificate SHA-256 is pinned in the recipe (`AllowedAPKSigningKeys`). If the key ever
changes, F-Droid refuses the release, so keep the keystore safe.
