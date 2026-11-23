#!/usr/bin/env bash
# Builds the signed release APK from a clean tree. Needs keystore.properties and the Android SDK.
#
#   tools/release.sh    ->  dist/Messages-<version>.apk
set -euo pipefail
cd "$(dirname "$0")/.."

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BUILD_TOOLS=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)

[ -f keystore.properties ] || { echo "keystore.properties is missing." >&2; exit 1; }
[ -z "$(git status --porcelain --untracked-files=no)" ] || { echo "Commit your changes first." >&2; exit 1; }

version=$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' app/build.gradle.kts)

./gradlew clean :app:assembleRelease

mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk "dist/Messages-$version.apk"
"$BUILD_TOOLS/apksigner" verify --print-certs "dist/Messages-$version.apk" | grep "SHA-256"
echo "dist/Messages-$version.apk: attach it to the v$version GitHub release."
