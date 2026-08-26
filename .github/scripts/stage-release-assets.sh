#!/usr/bin/env bash

set -euo pipefail

SOURCE_DIR="${1:?source directory is required}"
STAGING_DIR="${2:?staging directory is required}"

[ -d "$SOURCE_DIR" ] || {
  echo "Release output directory does not exist: $SOURCE_DIR" >&2
  exit 1
}

# The Nucleus build output contains both installable packages and the complete
# jpackage app-image. Publish only package files; the app-image contents are
# runtime internals, not independent GitHub Release assets.
if [ -e "$STAGING_DIR" ]; then
  echo "Staging directory already exists: $STAGING_DIR" >&2
  exit 1
fi
mkdir -p "$STAGING_DIR"

is_publishable_asset() {
  local file_name="$1"
  case "$file_name" in
    *.dmg|*.pkg|*.zip|*.7z|*.tar|*.tar.gz|*.exe|*.msi|*.appx|*.appxbundle|*.msix|*.deb|*.rpm|*.AppImage|*.pacman|*.pkg.tar.*|*.snap|*.flatpak|*.flatpakref|*.blockmap)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

staged_count=0
while IFS= read -r -d '' file; do
  file_name="$(basename "$file")"
  is_publishable_asset "$file_name" || continue

  # The raw jpackage launcher and bundled JDK launchers are not installers.
  if [[ "$file_name" == *.exe ]] && [[ "$file_name" != *-* ]]; then
    continue
  fi

  target="$STAGING_DIR/$file_name"
  if [ -e "$target" ]; then
    echo "Duplicate publishable asset basename: $file_name" >&2
    exit 1
  fi

  cp "$file" "$target"
  staged_count=$((staged_count + 1))
done < <(find "$SOURCE_DIR" -type f -print0)

if [ "$staged_count" -eq 0 ]; then
  echo "No publishable release assets found under: $SOURCE_DIR" >&2
  exit 1
fi

echo "Staged $staged_count publishable release asset(s) in $STAGING_DIR."
