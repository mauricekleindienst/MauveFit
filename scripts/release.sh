#!/usr/bin/env bash
# Builds the phone and watch APKs and publishes them as a GitHub release, which the app's updater
# picks up. The version comes from app/build.gradle.kts; the notes from the matching
# "## Erledigt in <version>" section of ROADMAP.md.
#
#   scripts/release.sh            build, tag v<version>, upload
#   scripts/release.sh --dry-run  build and print what would be published
#
# Needs: the GitHub CLI (gh) logged in, and the same signing key as earlier releases (the debug key
# of this machine), or installed apps cannot update.
set -euo pipefail
cd "$(dirname "$0")/.."

version=$(sed -nE 's/.*versionName = "([^"]+)".*/\1/p' app/build.gradle.kts | head -1)
tag="v$version"
[ -n "$version" ] || { echo "no versionName found" >&2; exit 1; }

if gh release view "$tag" >/dev/null 2>&1; then
  echo "release $tag exists already; raise versionName first" >&2
  exit 1
fi

gradle=./gradlew
[ -f gradlew.bat ] && [ "${OS:-}" = "Windows_NT" ] && gradle=./gradlew.bat
"$gradle" :app:testDebugUnitTest :app:assembleRelease :wear:assembleRelease -q

out=build/release
rm -rf "$out" && mkdir -p "$out"
cp app/build/outputs/apk/release/app-release.apk "$out/MauveFit-$version.apk"
cp wear/build/outputs/apk/release/wear-release.apk "$out/MauveFit-Watch-$version.apk"

# Notes: the roadmap section of this version, without its heading.
notes=$(awk -v v="$version" '
  $0 ~ "^## Erledigt in " v "( |$)" { on = 1; next }
  on && /^## / { exit }
  on { print }
' ROADMAP.md)
[ -n "$notes" ] || notes="Mauve Fit $version"
notes="$notes

Phone: MauveFit-$version.apk. Watch: MauveFit-Watch-$version.apk (install with adb or Wear Installer)."

if [ "${1:-}" = "--dry-run" ]; then
  echo "would publish $tag with:"; ls -la "$out"; echo; echo "$notes"
  exit 0
fi

# main is only changed through pull requests, so the release is built from what is on GitHub.
if [ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]; then
  echo "HEAD is not origin/main: merge the release PR and check out origin/main first" >&2
  exit 1
fi
gh release create "$tag" "$out/MauveFit-$version.apk" "$out/MauveFit-Watch-$version.apk" \
  --target main --title "Mauve Fit $version" --notes "$notes"
echo "published $tag"
