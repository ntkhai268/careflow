#!/bin/sh
set -eu
cd "$(dirname "$0")"
if ! command -v node >/dev/null 2>&1; then
  export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
fi
if [ ! -f app/build/web/main.dart.js ]; then
  echo "Chưa có bản build. Chạy npm run build trước."
  exit 1
fi
printf '\n%s\n\n' 'Mở http://localhost:3000 trên trình duyệt. Ctrl+C để dừng.'
exec node server.mjs
