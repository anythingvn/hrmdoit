#!/usr/bin/env bash
# Backup: pg_dump -Fc + tar thư mục files vào /backups/YYYY-MM-DD/, giữ 14 bản mới nhất,
# tùy chọn rsync bản mới nhất ra ngoài VPS, ghi trạng thái vào status.json.
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/backups}"
FILES_DIR="${FILES_DIR:-/data/files}"
STATUS_FILE="${STATUS_FILE:-/data/backup-status/status.json}"
KEEP="${BACKUP_KEEP:-14}"
OFFSITE="${BACKUP_OFFSITE_RSYNC:-}"
SSH_DIR="${BACKUP_SSH_DIR_IN_CONTAINER:-/ssh}"

today=$(date +%F)
target="$BACKUP_DIR/$today"
tmp="$BACKUP_DIR/.tmp-$today-$$"
mkdir -p "$BACKUP_DIR" "$(dirname "$STATUS_FILE")"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp"

echo "[backup] $(date -Is) pg_dump -> $target/db.dump"
pg_dump -Fc -f "$tmp/db.dump"

echo "[backup] tar files -> $target/files.tar.gz"
# GNU tar thoát mã 1 khi file đổi trong lúc đọc: chấp nhận (chỉ cảnh báo); mã khác 0 còn lại là lỗi.
tar_rc=0
tar czf "$tmp/files.tar.gz" -C "$FILES_DIR" . || tar_rc=$?
if [ "$tar_rc" -eq 1 ]; then
  echo "[backup] CẢNH BÁO: tar báo file thay đổi trong lúc đọc (mã 1), vẫn coi là thành công" >&2
elif [ "$tar_rc" -ne 0 ]; then
  echo "[backup] LỖI: tar thất bại (mã $tar_rc)" >&2
  exit "$tar_rc"
fi

# Chỉ thay bản cũ cùng ngày khi bản mới đã đầy đủ.
rm -rf "$target"
mv "$tmp" "$target"
chmod 0750 "$target"
last_at=$(date -Is)

# Giữ KEEP thư mục ngày mới nhất.
ls -1d "$BACKUP_DIR"/[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] 2>/dev/null | sort -r | tail -n +"$((KEEP + 1))" | while read -r old; do
  echo "[backup] xóa bản cũ $old"
  rm -rf "$old"
done

offsite_configured=false
offsite_ok=null
if [ -n "$OFFSITE" ]; then
  offsite_configured=true
  ssh_cmd="ssh -o BatchMode=yes -o ConnectTimeout=20 -o IdentitiesOnly=yes"
  [ -f "$SSH_DIR/id_ed25519" ] && ssh_cmd="$ssh_cmd -i $SSH_DIR/id_ed25519"
  [ -f "$SSH_DIR/id_rsa" ] && [ ! -f "$SSH_DIR/id_ed25519" ] && ssh_cmd="$ssh_cmd -i $SSH_DIR/id_rsa"
  if [ -f "$SSH_DIR/known_hosts" ]; then
    ssh_cmd="$ssh_cmd -o UserKnownHostsFile=$SSH_DIR/known_hosts -o StrictHostKeyChecking=yes"
  else
    ssh_cmd="$ssh_cmd -o UserKnownHostsFile=/tmp/known_hosts -o StrictHostKeyChecking=accept-new"
  fi
  echo "[backup] rsync $target -> $OFFSITE"
  if rsync -a -e "$ssh_cmd" "$target" "$OFFSITE"; then
    offsite_ok=true
  else
    echo "[backup] CẢNH BÁO: rsync ra ngoài VPS bị lỗi" >&2
    offsite_ok=false
  fi
fi

# Ghi nguyên tử để app không đọc phải file dở dang.
status_tmp="$STATUS_FILE.tmp"
printf '{"lastBackupAt": "%s", "offsiteConfigured": %s, "offsiteOk": %s}\n' "$last_at" "$offsite_configured" "$offsite_ok" > "$status_tmp"
mv "$status_tmp" "$STATUS_FILE"
chmod 0644 "$STATUS_FILE"
echo "[backup] xong $(cat "$STATUS_FILE")"
