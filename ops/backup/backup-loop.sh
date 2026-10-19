#!/usr/bin/env bash
# Chạy backup.sh mỗi ngày lúc 02:00 (giờ theo biến TZ của container).
set -u
HOUR="${BACKUP_HOUR:-02}"
while true; do
  now=$(date +%s)
  next=$(date -d "today ${HOUR}:00:00" +%s)
  if [ "$next" -le "$now" ]; then
    next=$(date -d "tomorrow ${HOUR}:00:00" +%s)
  fi
  wait=$((next - now))
  echo "[backup-loop] $(date -Is) chờ ${wait}s đến lần backup kế tiếp"
  sleep "$wait"
  /ops/backup/backup.sh || echo "[backup-loop] backup lỗi, thử lại vào ngày mai"
done
