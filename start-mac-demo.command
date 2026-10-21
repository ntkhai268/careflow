#!/bin/sh
set -eu
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
cd "$(dirname "$0")"
if ! /usr/bin/python3 scripts/mac-demo/demo.py start --open; then
  printf '\nKhởi động chưa hoàn tất. Xem thông báo ở trên. Nhấn Enter để đóng.\n'
  read -r _careflow_wait
  exit 1
fi
printf '\nCó thể đóng cửa sổ Terminal này; các service tiếp tục chạy.\n'
