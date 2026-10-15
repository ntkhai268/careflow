#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
export FLUTTER_SUPPRESS_ANALYTICS=true
export DART_SUPPRESS_ANALYTICS=true
export XDG_CONFIG_HOME="$ROOT/.runtime/config"
export PUB_CACHE="${CAREFLOW_PUB_CACHE:-$ROOT/.runtime/pub-cache}"
SDK="${CAREFLOW_FLUTTER_SDK:-/private/tmp/careflow-web-sdk}"
if [ ! -x "$SDK/bin/flutter" ]; then
  echo "Flutter SDK chưa có tại $SDK. Đặt CAREFLOW_FLUTTER_SDK trỏ đến Flutter 3.41+ hoặc chạy scripts/prepare-sdk.sh." >&2
  exit 1
fi
exec "$SDK/bin/flutter" --no-version-check "$@"
