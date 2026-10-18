#!/usr/bin/env bash
# Builds the signed release APK exactly the way F-Droid builds it, so F-Droid can verify it and ship
# it with our signature (see fdroid/README.md). Needs keystore.properties and the Android SDK.
#
#   tools/release.sh    ->  dist/Messages-<version>.apk
set -euo pipefail
cd "$(dirname "$0")/.."

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BUILD_TOOLS=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)

[ -f keystore.properties ] || { echo "keystore.properties is missing." >&2; exit 1; }
[ -z "$(git status --porcelain --untracked-files=no)" ] || { echo "Commit your changes first." >&2; exit 1; }
git submodule update --init

version=$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' app/build.gradle.kts)

# Obsidian from source, from scratch: incremental or cached outputs can differ by a few bytes, and then
# F-Droid's build no longer matches ours.
./gradlew -Pobsidian.fromSource=true clean
./gradlew --no-build-cache -Pobsidian.fromSource=true :app:assembleRelease

mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk "dist/Messages-$version.apk"
"$BUILD_TOOLS/apksigner" verify --print-certs "dist/Messages-$version.apk" | grep "SHA-256"
echo "dist/Messages-$version.apk: attach it to the v$version GitHub release."
