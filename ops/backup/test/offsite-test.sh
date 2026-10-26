#!/usr/bin/env bash
# Kiểm tra phần đẩy ra ngoài VPS của backup.sh, chạy BÊN TRONG image backup (không cần database: pg_dump được giả lập).
#   docker build -t hrm-backup-test ops/backup
#   docker run --rm -v "$PWD/ops/backup:/src:ro" hrm-backup-test bash /src/test/offsite-test.sh
# Kiểm: (1) thiếu BACKUP_AGE_RECIPIENT thì không đẩy, status báo lý do; (2) có khóa thì đích chỉ có file .age, giải mã ra
# đúng db.dump + files.tar.gz; (3) đích giữ 30 thư mục ngày mới nhất. Khóa age tạo tạm trong /tmp, xóa khi xong.
# Phần rclone (6–10) dùng "Drive giả": remote rclone kiểu local trỏ vào thư mục tạm, không cần tài khoản Google.
set -euo pipefail

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/bin" "$work/files/employees/1" "$work/remote" "$work/status"
cat > "$work/bin/pg_dump" <<'SH'
#!/usr/bin/env bash
# pg_dump giả: ghi nội dung cố định vào file chỉ định bởi -f.
while [ $# -gt 0 ]; do
  if [ "$1" = "-f" ]; then echo "PGDMP fake dump $(date +%s)" > "$2"; shift; fi
  shift
done
SH
chmod +x "$work/bin/pg_dump"
echo "%PDF-1.4 ho so" > "$work/files/employees/1/cccd.pdf"

run_backup() {
  PATH="$work/bin:$PATH" BACKUP_DIR="$work/backups" FILES_DIR="$work/files" STATUS_FILE="$work/status/status.json" \
    BACKUP_OFFSITE_RSYNC="$work/remote" BACKUP_SSH_DIR_IN_CONTAINER="$work/nossh" "$@" bash /src/backup.sh
}

fail() { echo "FAIL: $*" >&2; exit 1; }
today=$(date +%F)

echo "== 1. Thiếu BACKUP_AGE_RECIPIENT"
run_backup env BACKUP_AGE_RECIPIENT=
cat "$work/status/status.json"
grep -q '"offsiteConfigured": true' "$work/status/status.json" || fail "offsiteConfigured"
grep -q '"offsiteOk": false' "$work/status/status.json" || fail "offsiteOk phải false"
grep -q '"offsiteReason": "Chưa đặt khóa mã hóa BACKUP_AGE_RECIPIENT' "$work/status/status.json" || fail "thiếu lý do"
[ -z "$(ls -A "$work/remote")" ] || fail "không được đẩy gì ra ngoài khi thiếu khóa"
[ -f "$work/backups/$today/db.dump" ] || fail "bản local vẫn phải có"
echo "OK"

echo "== 2. Có khóa: chỉ đẩy file đã mã hóa, giải mã được"
age-keygen -o "$work/key.txt" 2> "$work/pub.txt"
pub=$(sed -n 's/^Public key: //p' "$work/pub.txt")
# 32 thư mục ngày cũ có sẵn ở đích (kể cả thư mục không phải ngày, phải được giữ nguyên).
for i in $(seq 1 32); do mkdir -p "$work/remote/$(date -d "today - $i days" +%F)"; touch "$work/remote/$(date -d "today - $i days" +%F)/old.tar.age"; done
mkdir -p "$work/remote/khac" && touch "$work/remote/khac/giu-nguyen.txt"
chmod 0751 "$work/remote"
run_backup env BACKUP_AGE_RECIPIENT="$pub"
cat "$work/status/status.json"
grep -q '"offsiteOk": true' "$work/status/status.json" || fail "offsiteOk phải true"
grep -q '"offsiteReason": null' "$work/status/status.json" || fail "offsiteReason phải null"
ls -la "$work/remote/$today"
[ "$(ls "$work/remote/$today")" = "hrm-$today.tar.age" ] || fail "đích chỉ được có file .age"
if find "$work/remote" -name 'db.dump' -o -name 'files.tar.gz' | grep -q .; then fail "có file chưa mã hóa ở đích"; fi
head -c 40 "$work/remote/$today/hrm-$today.tar.age" | grep -q "age-encryption.org/v1" || fail "không phải định dạng age"
mkdir "$work/restore"
age -d -i "$work/key.txt" "$work/remote/$today/hrm-$today.tar.age" | tar -x -C "$work/restore"
ls -la "$work/restore"
cmp "$work/restore/db.dump" "$work/backups/$today/db.dump" || fail "db.dump giải mã khác bản gốc"
tar -tzf "$work/restore/files.tar.gz" | grep -q "employees/1/cccd.pdf" || fail "files.tar.gz thiếu file"
echo "OK"

echo "== 3. Giữ 30 thư mục ngày mới nhất ở đích"
count=$(ls -1 "$work/remote" | grep -cE '^[0-9]{4}-[0-9]{2}-[0-9]{2}$')
echo "thư mục ngày còn lại: $count"
[ "$count" -eq 30 ] || fail "phải còn 30, thực tế $count"
[ -d "$work/remote/$today" ] || fail "bản hôm nay phải còn"
[ ! -d "$work/remote/$(date -d 'today - 30 days' +%F)" ] || fail "bản 30 ngày trước phải bị xóa"
[ -d "$work/remote/$(date -d 'today - 29 days' +%F)" ] || fail "bản 29 ngày trước phải còn"
[ -f "$work/remote/khac/giu-nguyen.txt" ] || fail "thư mục không phải ngày phải được giữ"
[ "$(stat -c %a "$work/remote")" = "751" ] || fail "dọn bản cũ không được đổi quyền thư mục gốc ở đích ($(stat -c %a "$work/remote"))"
echo "OK"

echo "== 4. Khóa sai định dạng: không đẩy, báo lý do"
run_backup env BACKUP_AGE_RECIPIENT="khong-phai-khoa" || true
grep -q '"offsiteOk": false' "$work/status/status.json" || fail "offsiteOk phải false khi khóa sai"
grep -q 'Không mã hóa được' "$work/status/status.json" || fail "thiếu lý do khóa sai"
echo "OK"

echo "== 5. Đích thiếu đường dẫn (user@host:) bị từ chối"
PATH="$work/bin:$PATH" BACKUP_DIR="$work/backups" FILES_DIR="$work/files" STATUS_FILE="$work/status/status.json" \
  BACKUP_OFFSITE_RSYNC="bk@may-nhan:" BACKUP_AGE_RECIPIENT="$pub" BACKUP_SSH_DIR_IN_CONTAINER="$work/nossh" bash /src/backup.sh
grep -q '"offsiteOk": false' "$work/status/status.json" || fail "offsiteOk phải false"
grep -q 'thiếu đường dẫn' "$work/status/status.json" || fail "thiếu lý do đích không hợp lệ"
echo "OK"

# --- rclone (Google Drive): remote "gdrive" kiểu local thay cho Drive thật ---
mkdir -p "$work/rclone" "$work/drive"
printf '[gdrive]\ntype = local\n' > "$work/rclone/rclone.conf"
run_rclone() {
  PATH="$work/bin:$PATH" BACKUP_DIR="$work/backups" FILES_DIR="$work/files" STATUS_FILE="$work/status/status.json" \
    BACKUP_OFFSITE_RSYNC= BACKUP_OFFSITE_RCLONE="gdrive:$work/drive/HRM-backup" RCLONE_CONFIG="$work/rclone/rclone.conf" \
    "$@" bash /src/backup.sh
}

echo "== 6. rclone thiếu BACKUP_AGE_RECIPIENT: không đẩy gì lên Drive"
run_rclone env BACKUP_AGE_RECIPIENT=
grep -q '"offsiteConfigured": true' "$work/status/status.json" || fail "rclone: offsiteConfigured"
grep -q '"offsiteOk": false' "$work/status/status.json" || fail "rclone: offsiteOk phải false khi thiếu khóa"
grep -q 'Chưa đặt khóa mã hóa BACKUP_AGE_RECIPIENT' "$work/status/status.json" || fail "rclone: thiếu lý do"
[ -z "$(ls -A "$work/drive")" ] || fail "rclone: không được đẩy gì khi thiếu khóa"
echo "OK"

echo "== 7. rclone có khóa: Drive chỉ có file .age, giải mã được, giữ 30 bản"
for i in $(seq 1 31); do d=$(date -d "today - $i days" +%F); mkdir -p "$work/drive/HRM-backup/$d"; touch "$work/drive/HRM-backup/$d/old.tar.age"; done
mkdir -p "$work/drive/HRM-backup/ghi-chu" && touch "$work/drive/HRM-backup/ghi-chu/giu-nguyen.txt"
run_rclone env BACKUP_AGE_RECIPIENT="$pub"
cat "$work/status/status.json"
grep -q '"offsiteOk": true' "$work/status/status.json" || fail "rclone: offsiteOk phải true"
grep -q '"offsiteReason": null' "$work/status/status.json" || fail "rclone: offsiteReason phải null"
[ "$(ls "$work/drive/HRM-backup/$today")" = "hrm-$today.tar.age" ] || fail "rclone: Drive chỉ được có file .age"
if find "$work/drive" -name 'db.dump' -o -name 'files.tar.gz' | grep -q .; then fail "rclone: có file chưa mã hóa trên Drive"; fi
rm -rf "$work/restore" && mkdir "$work/restore"
age -d -i "$work/key.txt" "$work/drive/HRM-backup/$today/hrm-$today.tar.age" | tar -x -C "$work/restore"
cmp "$work/restore/db.dump" "$work/backups/$today/db.dump" || fail "rclone: db.dump giải mã khác bản gốc"
tar -tzf "$work/restore/files.tar.gz" | grep -q "employees/1/cccd.pdf" || fail "rclone: files.tar.gz thiếu file"
count=$(ls -1 "$work/drive/HRM-backup" | grep -cE '^[0-9]{4}-[0-9]{2}-[0-9]{2}$')
[ "$count" -eq 30 ] || fail "rclone: phải còn 30 bản, thực tế $count"
[ ! -d "$work/drive/HRM-backup/$(date -d 'today - 30 days' +%F)" ] || fail "rclone: bản 30 ngày trước phải bị xóa"
[ -d "$work/drive/HRM-backup/$(date -d 'today - 29 days' +%F)" ] || fail "rclone: bản 29 ngày trước phải còn"
[ -f "$work/drive/HRM-backup/ghi-chu/giu-nguyen.txt" ] || fail "rclone: thư mục không phải ngày phải được giữ"
echo "OK"

echo "== 8. rclone lỗi (remote không có trong cấu hình): status ghi lỗi"
run_rclone env BACKUP_AGE_RECIPIENT="$pub" BACKUP_OFFSITE_RCLONE="khongco:HRM-backup"
grep -q '"offsiteOk": false' "$work/status/status.json" || fail "rclone lỗi: offsiteOk phải false"
grep -q 'Lỗi khi đẩy bản sao lưu bằng rclone' "$work/status/status.json" || fail "rclone lỗi: thiếu lý do"
echo "OK"

echo "== 9. rclone thiếu file cấu hình hoặc thiếu thư mục đích: không đẩy, báo lý do"
run_rclone env BACKUP_AGE_RECIPIENT="$pub" RCLONE_CONFIG="$work/khong-co.conf"
grep -q 'Không thấy file cấu hình rclone' "$work/status/status.json" || fail "thiếu lý do không có cấu hình"
run_rclone env BACKUP_AGE_RECIPIENT="$pub" BACKUP_OFFSITE_RCLONE="gdrive:"
grep -q '"offsiteOk": false' "$work/status/status.json" || fail "gdrive: phải bị từ chối"
grep -q 'BACKUP_OFFSITE_RCLONE thiếu thư mục' "$work/status/status.json" || fail "thiếu lý do đích rclone không hợp lệ"
echo "OK"

echo "== 10. Bật cả rsync và rclone: một nơi lỗi thì nơi kia vẫn nhận, status báo lỗi"
rm -rf "$work/drive/HRM-backup/$today"
run_rclone env BACKUP_AGE_RECIPIENT="$pub" BACKUP_OFFSITE_RSYNC="bk@may-nhan:"
[ -f "$work/drive/HRM-backup/$today/hrm-$today.tar.age" ] || fail "rclone vẫn phải đẩy khi rsync cấu hình sai"
grep -q '"offsiteOk": false' "$work/status/status.json" || fail "cả hai: offsiteOk phải false"
grep -q 'thiếu đường dẫn' "$work/status/status.json" || fail "cả hai: thiếu lý do rsync"
run_rclone env BACKUP_AGE_RECIPIENT="$pub" BACKUP_OFFSITE_RSYNC="$work/remote" BACKUP_SSH_DIR_IN_CONTAINER="$work/nossh"
grep -q '"offsiteOk": true' "$work/status/status.json" || fail "cả hai thành công: offsiteOk phải true"
[ -f "$work/remote/$today/hrm-$today.tar.age" ] || fail "cả hai: rsync phải nhận bản hôm nay"
echo "OK"

echo "TẤT CẢ ĐẠT"
