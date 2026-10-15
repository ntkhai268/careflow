#!/bin/sh
# Copy installed tooling and cache for sandboxed work; never alter source SDK.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SOURCE="${1:-$HOME/develop/flutter}"
DEST="${CAREFLOW_FLUTTER_SDK:-/private/tmp/careflow-web-sdk}"
if [ ! -x "$SOURCE/bin/flutter" ]; then
  echo "Không thấy Flutter SDK tại $SOURCE" >&2
  exit 1
fi
if [ "$SOURCE" = "$DEST" ]; then
  echo "Source và destination SDK phải khác nhau." >&2
  exit 1
fi
mkdir -p "$DEST" "$ROOT/.runtime/pub-cache"
rsync -a '--exclude=/bin/cache/artifacts/engine/android*' '--exclude=/bin/cache/artifacts/engine/ios*' '--exclude=/bin/cache/artifacts/engine/linux*' '--exclude=/bin/cache/artifacts/engine/windows*' '--exclude=/bin/cache/artifacts/engine/darwin-x64-profile*' '--exclude=/bin/cache/artifacts/engine/darwin-x64-release*' --exclude=/examples/ --exclude=/dev/ "$SOURCE/" "$DEST/"
mkdir -p "$DEST/examples" "$DEST/dev"
if [ -d "$HOME/.pub-cache" ]; then
  rsync -a "$HOME/.pub-cache/" "$ROOT/.runtime/pub-cache/"
fi
printf '%s\n' "SDK riêng đã sẵn sàng tại $DEST"
