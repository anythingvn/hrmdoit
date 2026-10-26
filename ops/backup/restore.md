# Khôi phục từ backup

Mỗi bản backup nằm trong volume `backups`, thư mục `/backups/YYYY-MM-DD/` gồm:

- `db.dump` – bản `pg_dump -Fc` của database `hrm`
- `files.tar.gz` – toàn bộ thư mục file tải lên (`/data/files`)

Bản trên VPS **không mã hóa** (cùng vùng tin cậy với database, giữ 14 bản). Bản **ngoài VPS** (Google Drive qua
`BACKUP_OFFSITE_RCLONE` và/hoặc máy khác qua `BACKUP_OFFSITE_RSYNC`) là một file mã hóa bằng [age](https://age-encryption.org): `YYYY-MM-DD/hrm-YYYY-MM-DD.tar.age` (giữ 30 bản mới nhất),
bên trong là `db.dump` + `files.tar.gz`. Chỉ giải mã được bằng private key tương ứng với `BACKUP_AGE_RECIPIENT`
(file khóa bí mật bạn đã cất ngoài VPS khi tạo khóa: `hrm-backup-key.txt` nếu tạo bằng `age-keygen`, hoặc
`hrm-backup-key-YYYY-MM-DD.txt` nếu tạo trên web ở Cài đặt → Sao lưu). Mỗi lần tạo khóa mới thì bản cũ vẫn cần file khóa cũ.

Chạy các lệnh dưới đây ở thư mục chứa `docker-compose.yml` trên VPS. Thay `2026-10-19` bằng ngày cần khôi phục.

## Khôi phục trên VPS mới

Khi VPS cũ hỏng hoặc mất, chỉ còn bản ngoài VPS (và file khóa bí mật, tên `hrm-backup-key.txt` hoặc `hrm-backup-key-YYYY-MM-DD.txt`):

1. Cài Docker + Docker Compose, rồi lấy mã nguồn **đúng phiên bản đang chạy** (cùng hoặc mới hơn bản đã tạo backup):
   `git clone https://github.com/anythingvn/hrmdoit.git && cd hrmdoit` (thư mục chứa `docker-compose.yml`; tên project Compose
   luôn là `doit-hrm` nên volume là `doit-hrm_backups`, `doit-hrm_files`…).
2. Tạo `.env` từ `.env.example`: **dùng lại `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`** của VPS cũ nếu còn (không bắt buộc:
   khóa mới chỉ làm mọi người đăng nhập lại), đặt `POSTGRES_PASSWORD` mới, `BACKUP_AGE_RECIPIENT`, `DOMAIN`. **Tạm để trống
   `BACKUP_OFFSITE_RSYNC` và `BACKUP_OFFSITE_RCLONE`** đến khi khôi phục xong, để bản backup của database còn trống không bị đẩy ra ngoài VPS.
3. Chỉ chạy database và container backup, **chưa chạy app** (app khởi động trên database trống sẽ tạo bảng và tài khoản mặc định):

   ```bash
   docker compose up -d db backup
   ```

4. Làm tiếp bước 2 → 4 bên dưới (tải và giải mã bản ngoài VPS, chép vào volume `backups`, khôi phục database và file). Bỏ qua bước 1
   (app chưa chạy).
5. Lần đầu chạy app (đã có code): `docker compose up -d --build` (thêm `--profile proxy` nếu dùng Caddy), rồi kiểm tra như bước 5.
   Sau đó điền lại `BACKUP_OFFSITE_RSYNC` + khóa SSH (`ops/backup/ssh/`) và/hoặc `BACKUP_OFFSITE_RCLONE` + `ops/backup/rclone/`
   (`rclone.conf`, `service-account.json`), rồi `docker compose up -d` để bật lại sao chép ngoài VPS.
   Với Google Drive: sau khi app chạy, vào **Cài đặt → Sao lưu** cấu hình lại (tải khóa service account, dán link Shared Drive,
   tạo khóa mã hóa mới và cất file khóa). Cấu hình web nằm trong volume `backup-config` nên không tự có trên VPS mới.

Lưu ý: container `backup` chạy lúc **02:00** mỗi ngày và chỉ giữ **14 bản mới nhất** (thư mục ngày mới nhất) trong volume `backups`.
Nên khôi phục xong trước 02:00. Nếu kéo dài qua 02:00 thì có thêm một bản backup của database đang dở; bản đã chép vào vẫn còn
(chỉ bị dọn khi đã có hơn 14 bản mới hơn). Đừng chép vào volume một bản cũ hơn 14 bản đang có (bản đó sẽ bị dọn lúc 02:00).

## 1. Dừng ứng dụng (giữ database chạy)

```bash
docker compose stop app
```

## 2. (Nếu bản backup nằm ngoài VPS) tải về, giải mã, chép vào volume `backups`

Giải mã trên máy có private key (không cần chép private key lên VPS nếu giải mã ở máy của bạn rồi gửi bản đã giải mã lên):

Lấy file `hrm-2026-10-19.tar.age` về `/tmp/restore/2026-10-19/` bằng một trong các cách:

- **Google Drive, qua trình duyệt:** mở Shared Drive → `HRM-backup/2026-10-19/` → tải `hrm-2026-10-19.tar.age` (cần là thành
  viên Shared Drive). File đã mã hóa nên tải về máy nào cũng không lộ dữ liệu.
- **Google Drive, trên VPS:** `docker compose exec backup rclone copy gdrive:HRM-backup/2026-10-19 /backups/.restore/2026-10-19`
  rồi `docker compose cp backup:/backups/.restore/2026-10-19 /tmp/restore/` (xóa `/backups/.restore` khi xong). Trên VPS mới
  cần chép lại `ops/backup/rclone/` trước.
- **Máy khác qua SSH:** `rsync -a user@host:/duong/dan/2026-10-19 /tmp/restore/`.

```bash
# Cài age: apt install age / brew install age. file khóa (hrm-backup-key.txt hoặc hrm-backup-key-YYYY-MM-DD.txt) chứa dòng AGE-SECRET-KEY-....
mkdir -p /tmp/restore/2026-10-19/plain
age -d -i hrm-backup-key.txt /tmp/restore/2026-10-19/hrm-2026-10-19.tar.age | tar -x -C /tmp/restore/2026-10-19/plain
ls /tmp/restore/2026-10-19/plain          # db.dump  files.tar.gz
# Nếu giải mã ở máy khác: chép thư mục plain lên VPS (scp/rsync) vào /tmp/restore/2026-10-19/plain.
docker compose cp /tmp/restore/2026-10-19/plain/. backup:/backups/2026-10-19/
rm -rf /tmp/restore                        # xóa bản giải mã tạm khi đã khôi phục xong
```

Kiểm tra định kỳ (khuyến nghị mỗi quý): tải một bản ngoài VPS về và giải mã thử như trên để chắc chắn private key còn đúng.

## 3. Khôi phục database

`--clean --if-exists` xóa các đối tượng cũ rồi tạo lại từ bản backup:

```bash
docker compose exec backup sh -c \
  'pg_restore --clean --if-exists --no-owner -d "$PGDATABASE" /backups/2026-10-19/db.dump'
```

Khôi phục vào một database trống khác (để kiểm tra, không đụng dữ liệu đang chạy):

```bash
docker compose exec backup sh -c \
  'createdb restore_test && pg_restore --no-owner -d restore_test /backups/2026-10-19/db.dump'
```

## 4. Khôi phục file tải lên

Giải nén vào volume `files` (container backup gắn volume này chỉ đọc, nên dùng container tạm):

```bash
docker run --rm \
  -v doit-hrm_files:/data/files \
  -v doit-hrm_backups:/backups:ro \
  postgres:16.15-bookworm \
  sh -c 'tar xzf /backups/2026-10-19/files.tar.gz -C /data/files && chown -R 1001:1001 /data/files'
```

(`doit-hrm_` là tiền tố project của Compose; xem tên chính xác bằng `docker volume ls`.)

## 5. Chạy lại ứng dụng và kiểm tra

```bash
docker compose start app
curl -fsS http://127.0.0.1:8080/management/health
```

Đăng nhập, mở một vài nhân viên và tải thử một file đính kèm để chắc chắn dữ liệu và file đã về đúng.
