# Khôi phục từ backup

Mỗi bản backup nằm trong volume `backups`, thư mục `/backups/YYYY-MM-DD/` gồm:

- `db.dump` – bản `pg_dump -Fc` của database `hrm`
- `files.tar.gz` – toàn bộ thư mục file tải lên (`/data/files`)

Chạy các lệnh dưới đây ở thư mục chứa `docker-compose.yml` trên VPS. Thay `2026-10-19` bằng ngày cần khôi phục.

## 1. Dừng ứng dụng (giữ database chạy)

```bash
docker compose stop app
```

## 2. (Nếu bản backup nằm ngoài VPS) chép về volume `backups`

```bash
rsync -a user@host:/duong/dan/2026-10-19 /tmp/restore/
docker compose cp /tmp/restore/2026-10-19 backup:/backups/
```

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
  postgres:16.4 \
  sh -c 'tar xzf /backups/2026-10-19/files.tar.gz -C /data/files && chown -R 1001:1001 /data/files'
```

(`doit-hrm_` là tiền tố project của Compose; xem tên chính xác bằng `docker volume ls`.)

## 5. Chạy lại ứng dụng và kiểm tra

```bash
docker compose start app
curl -fsS http://127.0.0.1:8080/management/health
```

Đăng nhập, mở một vài nhân viên và tải thử một file đính kèm để chắc chắn dữ liệu và file đã về đúng.
