#!/bin/sh
set -eu
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
cd "$(dirname "$0")"
/usr/bin/python3 scripts/mac-demo/demo.py status || true
printf '\nNhấn Enter để đóng.\n'
read -r _careflow_wait
