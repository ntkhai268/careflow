#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT/app"
"$ROOT/scripts/flutter.sh" pub get --offline
"$ROOT/scripts/flutter.sh" build web --release --no-pub --base-href=/mobile/ --no-web-resources-cdn --pwa-strategy=none
