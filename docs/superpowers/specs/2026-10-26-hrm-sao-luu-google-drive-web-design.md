# Cài đặt → Sao lưu: cấu hình sao lưu Google Drive trên web

Ngày: 26/10/2026. Trạng thái: chờ duyệt.

## 1. Mục đích

Khi mang DOIT HRM cài cho **công ty khác**, người quản trị bên đó tự bật sao lưu tự động lên Google Drive **ngay trên web**,
không mở terminal, không sửa file trên máy chủ. Hiện nay (commit 51a992f) việc này cần: tạo `rclone.conf` bằng tay, chép file
khóa service account vào `ops/backup/rclone/`, tạo khóa age bằng lệnh, sửa `.env`, khởi động lại container. Các lỗi hay gặp
(tải nhầm file OAuth client, dán ID thư mục thường thay vì Shared Drive, quên thêm service account làm thành viên) chỉ lộ ra
qua thông báo tiếng Anh của rclone.

**Thành công khi:** người quản trị làm theo 3 bước trên trang, mỗi lỗi cấu hình được báo bằng một câu tiếng Việt kèm cách
sửa, bấm Lưu chỉ thành công khi kết nối thật tới Shared Drive đã đạt, và đêm đó container backup đẩy bản mã hóa lên Drive.

**Người dùng đã chọn:** phục vụ công ty khác tự cài; khóa mã hóa tạo trên trình duyệt; chỉ cần nút "Kiểm tra kết nối"
(không có nút chạy sao lưu ngay); cách A (thư mục cấu hình dùng chung giữa app và container backup).

**Giả định:** chỉ tài khoản Quản trị (`ROLE_ADMIN`) thấy và dùng tab; vẫn bắt buộc Shared Drive (cần Google Workspace);
cách cấu hình bằng `.env` hiện có vẫn chạy.

**Ngoài phạm vi:** nút chạy sao lưu ngay; cấu hình rsync trên web; đổi tên thư mục `HRM-backup`; tài khoản Gmail cá nhân
(OAuth người dùng); khôi phục từ web.

## 2. Kiến trúc: thư mục cấu hình dùng chung

- Volume mới `backup-config`: app gắn **đọc–ghi** tại `/data/backup-config`, container backup gắn **chỉ đọc** cùng đường dẫn.
  Image app tạo sẵn `/data/backup-config` thuộc user `hrm` (như `/data/files`) để volume mới được app ghi. Thuộc tính
  `application.backup.config-dir` (`BACKUP_CONFIG_DIR`, mặc định `./data/backup-config` khi chạy dev).
- App ghi ba file, mỗi file ghi ra file tạm cùng thư mục rồi đổi tên (atomic):
  - `service-account.json` — nội dung file khóa người quản trị tải lên, quyền `0600`.
  - `rclone.conf` — app sinh:
    ```ini
    [gdrive]
    type = drive
    scope = drive
    service_account_file = /data/backup-config/service-account.json
    team_drive = <ID Shared Drive>
    ```
  - `offsite.conf` — ghi **sau cùng** (sự có mặt của nó nghĩa là cấu hình web đầy đủ):
    ```
    BACKUP_OFFSITE_RCLONE=gdrive:HRM-backup
    BACKUP_AGE_RECIPIENT=age1...
    UPDATED_BY=admin
    UPDATED_AT=2026-10-26T20:00:00+07:00
    SHARED_DRIVE_NAME=HRM backup
    ```
- Tắt: xóa `offsite.conf` **trước**, rồi hai file kia.
- **`backup.sh`**: nếu có `$BACKUP_CONFIG_DIR/offsite.conf` (mặc định `/data/backup-config`) thì lấy `BACKUP_OFFSITE_RCLONE`,
  `BACKUP_AGE_RECIPIENT` từ file này và `RCLONE_CONFIG=$BACKUP_CONFIG_DIR/rclone.conf`, thay cho giá trị từ `.env`. Đọc bằng
  `grep`/`sed` theo đúng tên khóa (không `source`), chấp nhận giá trị khớp `^[A-Za-z0-9:_.@,+/ -]*$`, giá trị khác bị bỏ qua
  và ghi lý do vào status. Không có file thì giữ nguyên cách cũ (`.env` + `ops/backup/rclone/`). rsync vẫn chỉ từ `.env`.
- Thư mục trên Drive cố định `HRM-backup`; giữ `BACKUP_OFFSITE_KEEP` (30) bản như hiện nay.

## 3. Giao diện: tab "Sao lưu" trong Cài đặt

Thêm vào `SETTINGS_TABS` (`app/modules/settings/settings-page.tsx`): `{ path: 'sao-luu', label: 'Sao lưu', roles: ADMIN_ONLY }`.
Component mới `app/modules/settings/backup-tab.tsx` (+ `backup-api.ts`, `age-key.ts`).

**Khối Tình trạng** (luôn hiện): lần sao lưu gần nhất, kết quả đẩy ra ngoài (đạt / lỗi + lý do) lấy từ
`GET /api/admin/backup-status` hiện có; nguồn cấu hình: "Cấu hình trên web" / "Cấu hình bằng file .env trên máy chủ" /
"Chưa cấu hình".

**Chưa cấu hình (hoặc bấm Sửa)** — 3 thẻ đánh số, mỗi thẻ có hướng dẫn ngắn và link tới trang Google tương ứng:

1. **Khóa Google (service account)**: hướng dẫn tạo (bật Google Drive API; IAM & Admin → Service Accounts → Keys → JSON),
   nút chọn file `.json`. Trình duyệt đọc nội dung, gửi `POST /api/admin/backup-settings/kiem-tra-khoa`. Đạt: hiện email
   service account + nút "Sao chép". Lỗi: câu tiếng Việt (vd file OAuth client).
2. **Shared Drive**: hướng dẫn tạo Shared Drive, thêm email ở bước 1 với vai trò **Người quản lý nội dung**. Ô "Link Shared
   Drive" nhận link `https://drive.google.com/drive/folders/<ID>` hoặc ID; trang tự tách ID.
3. **Khóa mã hóa**: nút "Tạo khóa mã hóa" — trình duyệt tạo cặp khóa age (thư viện `age-encryption` 0.3.1), tự tải file
   `hrm-backup-key-YYYY-MM-DD.txt` (khóa bí mật, kèm dòng chú thích khóa công khai và hướng dẫn giải mã) về máy, hiện khóa
   công khai. Ô đánh dấu bắt buộc: "Tôi đã cất file khóa bí mật ở nơi an toàn. Mất file này thì không khôi phục được bản
   sao lưu." Khóa bí mật không bao giờ gửi lên máy chủ.

Nút **Kiểm tra kết nối** và **Lưu** ở cuối. Lưu gửi `PUT`; máy chủ kiểm tra lại, chỉ ghi khi đạt.

**Đã cấu hình trên web**: bản tóm tắt (email service account, tên Shared Drive, khóa công khai rút gọn, ai/khi nào cấu hình)
và nút **Kiểm tra kết nối**, **Sửa**, **Tắt sao lưu Google Drive** (hộp xác nhận). Khi Sửa: phần nào không đổi thì giữ
(không bắt tải lại khóa Google, không bắt tạo khóa mã hóa mới). Tạo khóa mã hóa mới khi đã có khóa: cảnh báo "Các bản sao
lưu cũ vẫn cần khóa bí mật cũ để giải mã, hãy giữ cả file khóa cũ."

**Đang cấu hình bằng .env**: khối Tình trạng ghi rõ, 3 bước vẫn dùng được; lưu trên web sẽ thay cấu hình `.env` cho phần
Google Drive.

Vùng chạm ≥ 44px, thẻ và chip theo `app/shared/ui` như các tab khác.

## 4. Máy chủ

### API (`BackupSettingsResource`, `@PreAuthorize` ADMIN, `/api/admin/backup-settings`)

| Phương thức | Body | Trả |
|---|---|---|
| `GET` | — | `{ source: 'WEB'|'ENV'|'NONE', serviceAccountEmail, projectId, sharedDriveId, sharedDriveName, ageRecipient, updatedBy, updatedAt }` — **không bao giờ** có `private_key` |
| `POST /kiem-tra-khoa` | `{ serviceAccountJson }` | `{ serviceAccountEmail, projectId }` |
| `POST /kiem-tra` | `{ serviceAccountJson?, sharedDrive }` | `{ sharedDriveId, sharedDriveName }`; thiếu `serviceAccountJson` thì dùng khóa đã lưu |
| `PUT` | `{ serviceAccountJson?, sharedDrive, ageRecipient, keySaved }` | như `GET`; kiểm tra kết nối trước khi ghi |
| `DELETE` | — | 204 |

`source = ENV` khi không có `offsite.conf` nhưng biến `BACKUP_OFFSITE_RCLONE` hoặc `BACKUP_OFFSITE_RSYNC` của app khác rỗng
(compose truyền hai biến này cho app; chỉ là địa chỉ, không có bí mật).

### Các đơn vị

- `ServiceAccountKey` (record + `parse(String json)`): kiểm `type == "service_account"`, có `client_email`, `project_id`,
  `private_key` (PKCS#8 PEM, RSA). Nhận ra file OAuth client (khóa gốc `web`/`installed`). Giới hạn 16 KB.
- `SharedDriveRef.parse(String linkOrId)`: tách ID từ link `.../folders/<ID>` (bỏ query) hoặc nhận ID `[A-Za-z0-9_-]{10,100}`.
- `AgeRecipient.validate(String)`: `^age1[02-9ac-hj-np-z]{58}$`.
- `GoogleDriveProbe`: ký JWT RS256 (`iss` = email, `scope` = `https://www.googleapis.com/auth/drive`, `aud` = URL token,
  hạn 10 phút) bằng `java.security`, `POST` lấy token, `GET drive/v3/drives/{id}?fields=id,name,capabilities(canAddChildren,canTrashChildren)`.
  Dùng `java.net.http.HttpClient`, timeout 10 s. URL token và URL Drive lấy từ thuộc tính `application.backup.google-token-url`,
  `application.backup.google-drive-url` (mặc định `https://oauth2.googleapis.com/token`, `https://www.googleapis.com/drive/v3`;
  chỉ test đổi). **Bỏ qua `token_uri` trong file JSON.**
- `BackupSettingsService`: ghép các bước, đọc/ghi/xóa ba file (atomic), ghi log `INFO` ai lưu/tắt.

### Lỗi

Theo cách sẵn có: ném `EmployeeRuleException("error.<mã>")` → 400, câu tiếng Việt trong `i18n/vi/global.json` và
`ERROR_TEXT`. Mã và câu:

| Mã | Khi | Câu |
|---|---|---|
| `backupkeyoauth` | file có khóa `web`/`installed` | Đây là OAuth client ID, không phải khóa service account. Vào IAM & Admin → Service Accounts → Keys để tạo khóa JSON. |
| `backupkeyinvalid` | không phải JSON, sai `type`, thiếu trường, khóa riêng hỏng, quá 16 KB | File không phải khóa service account hợp lệ. |
| `backupkeyrevoked` | token trả `invalid_grant` | Khóa này đã bị xóa hoặc vô hiệu hóa trên Google Cloud. Hãy tạo khóa mới. |
| `backupdriveapioff` | Drive trả 403 `accessNotConfigured`/`SERVICE_DISABLED` | Chưa bật Google Drive API cho project của service account. Vào APIs & Services → Library để bật. |
| `backupdrivenotfound` | Drive trả 404 | Không thấy Shared Drive. Kiểm tra link, và đã thêm email service account làm thành viên chưa. |
| `backupdriverole` | thiếu `canAddChildren` hoặc `canTrashChildren` | Service account cần vai trò Người quản lý nội dung để thêm và dọn bản cũ. |
| `backupdrivelink` | link/ID không đọc được | Link Shared Drive không hợp lệ. |
| `backupgoogleunreachable` | lỗi mạng, timeout, 5xx | Máy chủ không kết nối được tới Google. Kiểm tra mạng rồi thử lại. |
| `backupagerecipient` | khóa công khai sai dạng | Khóa mã hóa không hợp lệ (phải bắt đầu bằng age1). |
| `backupkeynotsaved` | `keySaved` khác true khi `ageRecipient` khác khóa đang lưu (khóa mới) | Hãy xác nhận đã cất file khóa bí mật. |
| `backupnokey` | `kiem-tra`/`PUT` không gửi khóa và chưa có khóa đã lưu | Hãy tải lên file khóa service account. |
| `backupconfigdir` | thư mục cấu hình không tồn tại hoặc không ghi được | Máy chủ chưa có thư mục cấu hình sao lưu. Cập nhật docker-compose.yml và khởi động lại. |

## 5. Kiểm thử

- **Unit (máy chủ):** `ServiceAccountKey` (đúng, OAuth client, JSON hỏng, thiếu `private_key`, quá 16 KB);
  `SharedDriveRef` (link có query, ID trần, rác); `AgeRecipient`; `GoogleDriveProbe` với máy chủ HTTP giả (JDK
  `com.sun.net.httpserver.HttpServer`) cho từng dòng bảng lỗi, kiểm JWT gửi đi ký đúng bằng khóa test và không gọi `token_uri`
  trong file.
- **Tích hợp:** `BackupSettingsResource` — HR/NHAN_VIEN bị 403; `PUT` ghi đủ 3 file đúng nội dung, `service-account.json`
  quyền 0600, `offsite.conf` có `UPDATED_BY`; `GET` không chứa `private_key`; `DELETE` xóa hết; `PUT` khi kiểm tra kết nối
  lỗi thì không ghi gì.
- **Giao diện (Vitest):** 3 bước; tải file OAuth hiện lỗi; dán link tách ID; tạo khóa gọi tải file và Lưu bị khóa đến khi
  đánh dấu; tóm tắt khi đã cấu hình; Tắt hỏi xác nhận; tab chỉ hiện với Quản trị.
- **Script (`ops/backup/test/offsite-test.sh`):** có `offsite.conf` thì dùng cấu hình web thay `.env`; giá trị chứa `$(...)`
  không chạy và bị bỏ qua; không có file thì quay về `.env`.
- **Thử thật trên máy dev:** người dùng tự tải khóa lên tab Sao lưu (khóa bí mật — Claude không thao tác), kiểm tra kết nối
  với Shared Drive "HRM backup", chạy `backup.sh` thấy đẩy theo cấu hình web.

## 6. Triển khai và tài liệu

- `docker-compose.yml`: volume `backup-config`; app gắn rw + `BACKUP_CONFIG_DIR`, `BACKUP_OFFSITE_RCLONE`,
  `BACKUP_OFFSITE_RSYNC`; backup gắn ro + `BACKUP_CONFIG_DIR`. `Dockerfile` app: tạo `/data/backup-config` thuộc `hrm`.
- `package.json`: thêm `age-encryption` `0.3.1` (ghim đúng bản).
- README: mục Google Drive rút gọn thành "Vào Cài đặt → Sao lưu" + những gì cần chuẩn bị trên Google; cách `.env` chuyển
  xuống "Cấu hình thủ công". `restore.md`: file khóa bí mật tên `hrm-backup-key-YYYY-MM-DD.txt` khi tạo trên web.
- DOIT hiện tại (cấu hình bằng `.env`) chạy nguyên. Chuyển sang web: làm 3 bước; nên tạo khóa mới và giữ cả
  `~/hrm-backup-key.txt` cho các bản đã có.

## 7. Rủi ro đã chấp nhận

Máy chủ giữ khóa service account (như hiện nay). App bị chiếm quyền thì kẻ tấn công có thể xóa bản trên Shared Drive nhưng
không đọc được nội dung (đã mã hóa age); bản bị xóa vào thùng rác Shared Drive 30 ngày. Trang không bao giờ trả lại nội dung
khóa sau khi tải lên.
