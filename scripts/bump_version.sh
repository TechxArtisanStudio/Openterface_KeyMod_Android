#!/usr/bin/env bash
# bump_version.sh - Increment versionCode and versionName in app/build.gradle
#
# Usage:
#   ./scripts/bump_version.sh              # Auto-increment patch (1.0 -> 1.1)
#   ./scripts/bump_version.sh <version>    # Set specific version (e.g., 2.0.0)

set -euo pipefail

BUILD_GRADLE="app/build.gradle"

if [ ! -f "$BUILD_GRADLE" ]; then
  echo "Error: $BUILD_GRADLE not found"
  exit 1
fi

# Extract current values
current_code=$(grep 'versionCode' "$BUILD_GRADLE" | grep -oE '[0-9]+' | head -1)
current_name=$(grep 'versionName' "$BUILD_GRADLE" | sed 's/.*"\(.*\)".*/\1/' | head -1)

if [ $# -ge 1 ]; then
  new_name="$1"
else
  # Increment patch: split on dots, bump last segment
  IFS='.' read -ra parts <<< "$current_name"
  last_idx=$(( ${#parts[@]} - 1 ))
  parts[$last_idx]=$(( ${parts[$last_idx]} + 1 ))
  new_name=$(IFS='.'; echo "${parts[*]}")
fi

new_code=$(( current_code + 1 ))

echo "Bumping: $current_name -> $new_name (code: $current_code -> $new_code)"

# Update versionName and versionCode (macOS sed syntax)
sed -i '' -E "s/versionCode [0-9]+/versionCode $new_code/" "$BUILD_GRADLE"
sed -i '' -E "s/versionName \"[^\"]+\"/versionName \"$new_name\"/" "$BUILD_GRADLE"

echo "Done: versionName=$new_name versionCode=$new_code"
