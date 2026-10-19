#!/usr/bin/env bash
# Backup: pg_dump -Fc + tar thư mục files vào /backups/YYYY-MM-DD/, giữ 14 bản mới nhất trên VPS (không mã hóa: cùng
# vùng tin cậy với database). Tùy chọn đẩy ra ngoài VPS: gói bản mới nhất thành một file mã hóa bằng age
# (public key BACKUP_AGE_RECIPIENT), rsync sang BACKUP_OFFSITE_RSYNC/YYYY-MM-DD/, giữ 30 bản mới nhất ở đó.
# Thiếu khóa mã hóa thì KHÔNG đẩy (không để bản chưa mã hóa rời VPS) và status.json ghi lý do.
# Ghi trạng thái vào status.json.
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/backups}"
FILES_DIR="${FILES_DIR:-/data/files}"
STATUS_FILE="${STATUS_FILE:-/data/backup-status/status.json}"
KEEP="${BACKUP_KEEP:-14}"
OFFSITE="${BACKUP_OFFSITE_RSYNC:-}"
OFFSITE="${OFFSITE%/}"
AGE_RECIPIENTS="${BACKUP_AGE_RECIPIENT:-}"
KEEP_OFFSITE="${BACKUP_OFFSITE_KEEP:-30}"
SSH_DIR="${BACKUP_SSH_DIR_IN_CONTAINER:-/ssh}"

today=$(date +%F)
target="$BACKUP_DIR/$today"
tmp="$BACKUP_DIR/.tmp-$today-$$"
mkdir -p "$BACKUP_DIR" "$(dirname "$STATUS_FILE")"
stage="$BACKUP_DIR/.offsite-$today-$$"
empty="$BACKUP_DIR/.empty-$$"
trap 'rm -rf "$tmp" "$stage" "$empty"' EXIT
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
offsite_reason=""
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

  if [ -z "${AGE_RECIPIENTS// /}" ]; then
    echo "[backup] CẢNH BÁO: chưa đặt BACKUP_AGE_RECIPIENT, không đẩy bản backup chưa mã hóa ra ngoài VPS" >&2
    offsite_ok=false
    offsite_reason="Chưa đặt khóa mã hóa BACKUP_AGE_RECIPIENT nên không đẩy bản sao lưu ra ngoài máy chủ"
  else
    # Một hoặc nhiều public key age (age1...) hoặc SSH (ssh-ed25519 ...), cách nhau bởi dấu phẩy.
    age_args=()
    IFS=',' read -r -a recipients <<< "$AGE_RECIPIENTS"
    for r in "${recipients[@]}"; do
      r="$(echo "$r" | sed 's/^ *//; s/ *$//')"
      [ -n "$r" ] && age_args+=(-r "$r")
    done
    mkdir -p "$stage/$today"
    archive="$stage/$today/hrm-$today.tar.age"
    echo "[backup] mã hóa $target -> $(basename "$archive")"
    if tar -C "$target" -cf - db.dump files.tar.gz | age "${age_args[@]}" -o "$archive"; then
      echo "[backup] rsync $today (đã mã hóa) -> $OFFSITE/"
      if rsync -a -e "$ssh_cmd" "$stage/$today" "$OFFSITE/"; then
        offsite_ok=true
        # Dọn bản cũ ngoài VPS: giữ KEEP_OFFSITE thư mục ngày mới nhất. Chỉ dùng rsync (chạy được cả với tài khoản
        # chỉ cho phép rsync): liệt kê thư mục ở đích, rồi xóa thư mục cũ bằng --delete từ một thư mục rỗng.
        if listing=$(rsync --list-only -e "$ssh_cmd" "$OFFSITE/"); then
          old_dirs=$(echo "$listing" | awk '{print $NF}' | grep -E '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' | sort -r | tail -n +"$((KEEP_OFFSITE + 1))" || true)
          if [ -n "$old_dirs" ]; then
            filters=()
            for d in $old_dirs; do
              echo "[backup] xóa bản cũ ngoài VPS $d"
              filters+=(--include="/$d/***")
            done
            mkdir -p "$empty"
            if ! rsync -a --delete "${filters[@]}" --exclude='*' -e "$ssh_cmd" "$empty/" "$OFFSITE/"; then
              echo "[backup] CẢNH BÁO: không dọn được bản cũ ngoài VPS" >&2
            fi
          fi
        else
          echo "[backup] CẢNH BÁO: không liệt kê được thư mục ngoài VPS để dọn bản cũ" >&2
        fi
      else
        echo "[backup] CẢNH BÁO: rsync ra ngoài VPS bị lỗi" >&2
        offsite_ok=false
        offsite_reason="Lỗi khi sao chép ra ngoài máy chủ (rsync)"
      fi
    else
      echo "[backup] CẢNH BÁO: mã hóa bằng age thất bại (kiểm tra BACKUP_AGE_RECIPIENT)" >&2
      offsite_ok=false
      offsite_reason="Không mã hóa được bản sao lưu: kiểm tra khóa BACKUP_AGE_RECIPIENT"
    fi
    rm -rf "$stage"
  fi
fi

# Ghi nguyên tử để app không đọc phải file dở dang.
status_tmp="$STATUS_FILE.tmp"
if [ -n "$offsite_reason" ]; then
  reason_json="\"$offsite_reason\""
else
  reason_json=null
fi
printf '{"lastBackupAt": "%s", "offsiteConfigured": %s, "offsiteOk": %s, "offsiteReason": %s}\n' \
  "$last_at" "$offsite_configured" "$offsite_ok" "$reason_json" > "$status_tmp"
mv "$status_tmp" "$STATUS_FILE"
chmod 0644 "$STATUS_FILE"
echo "[backup] xong $(cat "$STATUS_FILE")"
