# Cài đặt → Sao lưu (Google Drive trên web) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Người quản trị bật sao lưu Google Drive ngay trên web (Cài đặt → Sao lưu): tải khóa service account, dán link Shared Drive, tạo khóa age trên trình duyệt, kiểm tra kết nối, lưu.

**Architecture:** App ghi `service-account.json`, `rclone.conf`, `offsite.conf` vào volume `backup-config` (app rw, backup ro); `backup.sh` ưu tiên `offsite.conf` hơn `.env`. App tự kiểm tra kết nối bằng JWT RS256 + Drive API v3 (`java.net.http`), không thêm thư viện Java. Khóa age tạo bằng `age-encryption` trên trình duyệt; chỉ khóa công khai lên máy chủ.

**Tech Stack:** Spring Boot (Java 21, Jackson `tools.jackson`), React 19 + react-bootstrap, Vitest, bash + rclone + age trong `ops/backup`.

**Spec:** `docs/superpowers/specs/2026-10-26-hrm-sao-luu-google-drive-web-design.md`

## Global Constraints

- Chỉ `ROLE_ADMIN` (`AuthoritiesConstants.ADMIN`) gọi API và thấy tab; tab `{ path: 'sao-luu', label: 'Sao lưu', roles: ADMIN_ONLY }`.
- API gốc `/api/admin/backup-settings`; không bao giờ trả hoặc ghi log nội dung `private_key`/file JSON.
- Lỗi: `throw new EmployeeRuleException("<câu tiếng Việt>", "<mã>")` → 400 `error.<mã>`; câu tiếng Việt thêm vào `ERROR_TEXT` trong `app/src/main/webapp/app/entities/employee/detail/detail-api.ts` (codebase không dùng `global.json` cho các mã này — sai lệch nhỏ so với spec, theo code hiện có). Mã và câu đúng bảng mục 4 "Lỗi" của spec.
- URL Google mặc định `https://oauth2.googleapis.com/token`, `https://www.googleapis.com/drive/v3`; bỏ qua `token_uri` trong file JSON.
- File JSON tối đa 16 KB (16384 byte UTF-8). Khóa công khai age khớp `^age1[02-9ac-hj-np-z]{58}$`.
- Thư mục Drive cố định `HRM-backup`; remote rclone tên `gdrive`; `offsite.conf` ghi sau cùng, xóa đầu tiên; mọi file ghi tmp + `ATOMIC_MOVE`.
- Khóa age tạo bằng `generateX25519Identity()` (KHÔNG `generateIdentity()`, có thể đổi sang khóa hậu lượng tử mà age 1.1 trong container không đọc được); `age-encryption` ghim `0.3.1`.
- Comment, thông báo, commit tiếng Việt; commit kết thúc `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Maven cần `JAVA_HOME=/opt/homebrew/opt/openjdk@21`; IT cần `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock`.

## Review Focus

1. File JSON có BOM UTF-8 hoặc xuống dòng CRLF (tải từ Windows) — vẫn đọc được như file thường. → test trong Task 2.
2. Link Shared Drive dạng `https://drive.google.com/drive/u/1/folders/<ID>?usp=sharing` hoặc có `#` — tách đúng ID. → test trong Task 2.
3. Sửa cấu hình khi nguồn đang là `.env` (chưa có khóa lưu trên web) mà không tải khóa → `error.backupnokey`, không ghi file nào. → test trong Task 4.
4. Trình duyệt chặn hoặc người dùng lỡ đóng tải file khóa bí mật — vẫn có nút "Tải lại file khóa" tới khi Lưu xong (khóa chỉ ở bộ nhớ trang). → test trong Task 5.
5. Lưu khi kiểm tra kết nối lỗi giữa chừng — không để lại `offsite.conf` cũ trỏ tới khóa mới (thứ tự ghi đúng; lỗi trước khi ghi thì không đụng file). → test trong Task 4.

---

### Task 1: `backup.sh` đọc cấu hình web; volume `backup-config`

**Files:**
- Modify: `ops/backup/backup.sh` (đầu file, sau khối biến môi trường)
- Modify: `ops/backup/test/offsite-test.sh` (thêm mục 11–13 trước `TẤT CẢ ĐẠT`)
- Modify: `docker-compose.yml` (service `app`, `backup`, khối `volumes:`)
- Modify: `Dockerfile` (dòng 17–18: tạo `/data/backup-config` thuộc `hrm`)

**Interfaces:**
- Produces: định dạng `offsite.conf` (dòng `KEY=value`, khóa: `BACKUP_OFFSITE_RCLONE`, `BACKUP_AGE_RECIPIENT`, `UPDATED_BY`, `UPDATED_AT`, `SHARED_DRIVE_NAME`); biến `BACKUP_CONFIG_DIR` (mặc định `/data/backup-config`) cho cả app và backup.

- [ ] **Step 1: Viết test (offsite-test.sh mục 11–13)**, dùng lại `run_rclone`, `$pub`, remote `gdrive` local:
  - 11: tạo `$work/cfg/offsite.conf` với `BACKUP_OFFSITE_RCLONE=gdrive:$work/drive2/HRM-backup`, `BACKUP_AGE_RECIPIENT=$pub` và `$work/cfg/rclone.conf` (`[gdrive]\ntype = local`); chạy `run_rclone env BACKUP_CONFIG_DIR="$work/cfg" BACKUP_AGE_RECIPIENT= BACKUP_OFFSITE_RCLONE="gdrive:$work/drive/HRM-backup"` → `"offsiteOk": true`, có `$work/drive2/HRM-backup/$today/hrm-$today.tar.age`, `$work/drive/HRM-backup/$today` không được tạo mới (xóa trước khi chạy).
  - 12: `offsite.conf` có `BACKUP_AGE_RECIPIENT=$(touch $work/pwned)` → không có `$work/pwned`; status `"offsiteOk": false` và lý do chứa `offsite.conf`.
  - 13: `BACKUP_CONFIG_DIR="$work/khong-co"` → dùng `.env` như mục 7 (`"offsiteOk": true`, đẩy vào `$work/drive`).
- [ ] **Step 2: Chạy, thấy mục 11 FAIL**
  Run: `docker build -q -t hrm-backup-test ops/backup && docker run --rm -v "$PWD/ops/backup:/src:ro" hrm-backup-test bash /src/test/offsite-test.sh`
- [ ] **Step 3: Sửa `backup.sh`**: sau khối biến, nếu `[ -f "$CONFIG_DIR/offsite.conf" ]` (`CONFIG_DIR="${BACKUP_CONFIG_DIR:-/data/backup-config}"`): hàm `conf_get KEY` = `sed -n "s/^KEY=//p" | tail -1`; giá trị không khớp `^[A-Za-z0-9:_.@,+/ -]*$` → bỏ, thêm vào `reasons` "Giá trị KEY trong offsite.conf không hợp lệ" (khai báo `reasons=()` lên trước khối này); đặt `RCLONE_DEST`, `AGE_RECIPIENTS`, `RCLONE_CONF="$CONFIG_DIR/rclone.conf"`; khi có lý do lỗi thì `offsite_configured=true`. Không `source`/`eval`.
- [ ] **Step 4: Chạy lại test** → `TẤT CẢ ĐẠT`.
- [ ] **Step 5: Compose + Dockerfile**: volume `backup-config`; `app`: `- backup-config:/data/backup-config`, env `BACKUP_CONFIG_DIR: /data/backup-config`, `BACKUP_OFFSITE_RCLONE: ${BACKUP_OFFSITE_RCLONE:-}`, `BACKUP_OFFSITE_RSYNC: ${BACKUP_OFFSITE_RSYNC:-}`; `backup`: `- backup-config:/data/backup-config:ro`, `BACKUP_CONFIG_DIR: /data/backup-config`. Dockerfile: `mkdir -p /app /data/files /data/backup-config && chown -R hrm:hrm /app /data/files /data/backup-config`. Verify: `docker compose config -q`.
- [ ] **Step 6: Commit** `ops/backup docker-compose.yml Dockerfile` — "Sao lưu: backup.sh ưu tiên cấu hình web (offsite.conf trong volume backup-config)".

### Task 2: Đọc và kiểm tra dữ liệu nhập (khóa, link, khóa age)

**Files:**
- Create: `app/src/main/java/vn/doit/hrm/service/backup/ServiceAccountKey.java`
- Create: `app/src/main/java/vn/doit/hrm/service/backup/SharedDriveRef.java`
- Create: `app/src/main/java/vn/doit/hrm/service/backup/AgeRecipient.java`
- Test: `app/src/test/java/vn/doit/hrm/service/backup/BackupInputsTest.java`

**Interfaces:**
- Produces:
  - `record ServiceAccountKey(String clientEmail, String projectId, java.security.interfaces.RSAPrivateKey privateKey, String rawJson)`; `static ServiceAccountKey parse(String json)` ném `EmployeeRuleException` mã `backupkeyoauth` / `backupkeyinvalid`; `toString()` không chứa khóa.
  - `final class SharedDriveRef { static String parseId(String linkOrId) }` ném mã `backupdrivelink`.
  - `final class AgeRecipient { static String validate(String recipient) }` trả chuỗi đã `trim()`, ném mã `backupagerecipient`.

- [ ] **Step 1: Viết test** (khóa RSA test sinh bằng `KeyPairGenerator` 2048, ghép JSON service account giả):
  - `parsesValidKey` → email, projectId, `privateKey` không null; `toString()` không chứa `BEGIN PRIVATE KEY`.
  - `acceptsBomAndCrlf` → JSON có tiền tố `﻿` và `\r\n` vẫn parse.
  - `rejectsOauthClient` → `{"web":{"client_id":"x"}}` và `{"installed":{...}}` → mã `backupkeyoauth`.
  - `rejectsInvalid` → `"abc"`, `{"type":"user"}`, thiếu `private_key`, `private_key` rác, chuỗi > 16384 byte → mã `backupkeyinvalid`.
  - `parsesDriveLinks` → `https://drive.google.com/drive/folders/0AAfBVsNi11ezUk9PVA`, `.../drive/u/1/folders/0AAfBVsNi11ezUk9PVA?usp=sharing`, `...#abc`, ID trần `0AAfBVsNi11ezUk9PVA` (có khoảng trắng hai đầu) → đều `0AAfBVsNi11ezUk9PVA`; `"https://example.com"`, `""`, `"a b"` → mã `backupdrivelink`.
  - `validatesAgeRecipient` → `age1e3tlg3rkq2fjze8gzynkhx4dlsl3wn2pk9ahkj8hawx5x253rv0qyktdll` đạt; `age1abc`, `AGE-SECRET-KEY-1...`, chữ hoa → mã `backupagerecipient`.
- [ ] **Step 2: Chạy, thấy FAIL** — Run: `cd app && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -q test -Dtest=BackupInputsTest` (các task sau dùng cùng lệnh, đổi `-Dtest`).
- [ ] **Step 3: Cài đặt.** `ServiceAccountKey.parse`: bỏ BOM, kiểm độ dài byte, đọc bằng `ObjectMapper` (`tools.jackson.databind`), có `web`/`installed` → oauth; `type` phải `service_account`; PEM → bỏ dòng `-----`, `Base64.getMimeDecoder()`, `PKCS8EncodedKeySpec`, `KeyFactory.getInstance("RSA")`. `SharedDriveRef`: regex `/folders/([A-Za-z0-9_-]{10,100})` trên link, hoặc toàn chuỗi khớp `[A-Za-z0-9_-]{10,100}`.
- [ ] **Step 4: Chạy lại** → PASS.
- [ ] **Step 5: Commit** — "Sao lưu: đọc khóa service account, link Shared Drive, khóa age".

### Task 3: `GoogleDriveProbe` (kiểm tra kết nối Google)

**Files:**
- Create: `app/src/main/java/vn/doit/hrm/service/backup/GoogleDriveProbe.java`
- Modify: `app/src/main/java/vn/doit/hrm/config/ApplicationProperties.java` (lớp `Backup`: `configDir`, `googleTokenUrl`, `googleDriveUrl`)
- Test: `app/src/test/java/vn/doit/hrm/service/backup/GoogleDriveProbeTest.java`

**Interfaces:**
- Consumes: `ServiceAccountKey` (Task 2).
- Produces: `@Component GoogleDriveProbe(ApplicationProperties)`; `record SharedDrive(String id, String name)`; `SharedDrive check(ServiceAccountKey key, String driveId)` ném `EmployeeRuleException` mã `backupkeyrevoked` / `backupdriveapioff` / `backupdrivenotfound` / `backupdriverole` / `backupgoogleunreachable`. `ApplicationProperties.Backup`: `getConfigDir()` mặc định `"./data/backup-config"`, `getGoogleTokenUrl()` mặc định `"https://oauth2.googleapis.com/token"`, `getGoogleDriveUrl()` mặc định `"https://www.googleapis.com/drive/v3"`.

- [ ] **Step 1: Viết test** với `com.sun.net.httpserver.HttpServer` trên cổng 0 làm cả `/token` và `/drive/v3/drives/{id}`; probe tạo với properties trỏ vào server giả; JSON khóa test có `"token_uri": "http://127.0.0.1:1/evil"`:
  - `returnsDriveWhenAllowed` → token 200 `{"access_token":"t"}`; drive 200 `{"id":"0Aabc","name":"HRM backup","capabilities":{"canAddChildren":true,"canTrashChildren":true}}` → `SharedDrive("0Aabc","HRM backup")`; request drive có header `Authorization: Bearer t`; request token có `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer` và `assertion` là JWT 3 phần, chữ ký verify được bằng khóa công khai test (`SHA256withRSA`), claim `iss` = email, `scope` = `https://www.googleapis.com/auth/drive`, `aud` = URL token của server giả.
  - `ignoresTokenUriInKey` → server giả nhận đúng 1 request `/token` (không gọi `token_uri` trong file).
  - `mapsInvalidGrant` → token 400 `{"error":"invalid_grant"}` → `backupkeyrevoked`.
  - `mapsApiDisabled` → drive 403 body chứa `accessNotConfigured` (và riêng một case `SERVICE_DISABLED`) → `backupdriveapioff`; câu lỗi chứa projectId.
  - `mapsNotFound` → drive 404 → `backupdrivenotfound`; câu lỗi chứa email service account.
  - `mapsRole` → `canTrashChildren:false` → `backupdriverole`.
  - `mapsUnreachable` → server đã `stop` → `backupgoogleunreachable`; drive 500 → `backupgoogleunreachable`.
- [ ] **Step 2: Chạy, thấy FAIL** — `-Dtest=GoogleDriveProbeTest`.
- [ ] **Step 3: Cài đặt** bằng `java.net.http.HttpClient` (connect timeout 10 s, request timeout 10 s). JWT: header `{"alg":"RS256","typ":"JWT"}`, `iat` = now, `exp` = now + 600, base64url không padding, `Signature.getInstance("SHA256withRSA")`. Drive URL: `{googleDriveUrl}/drives/{id}?fields=id,name,capabilities(canAddChildren,canTrashChildren)` (encode `fields`). Câu lỗi lấy đúng bảng spec, chèn email/projectId vào `backupdrivenotfound`/`backupdriveapioff`.
- [ ] **Step 4: Chạy lại** → PASS.
- [ ] **Step 5: Commit** — "Sao lưu: kiểm tra kết nối Google Drive bằng service account (JWT RS256)".

### Task 4: `BackupSettingsService` + `BackupSettingsResource`

**Files:**
- Create: `app/src/main/java/vn/doit/hrm/service/backup/BackupSettingsService.java`
- Create: `app/src/main/java/vn/doit/hrm/web/rest/BackupSettingsResource.java`
- Test: `app/src/test/java/vn/doit/hrm/web/rest/BackupSettingsResourceIT.java`

**Interfaces:**
- Consumes: Task 2, `GoogleDriveProbe.check` (Task 3), `SecurityUtils.getCurrentUserLogin()`.
- Produces (JSON cho Task 5):
  - `record BackupSettingsView(String source /* "WEB"|"ENV"|"NONE" */, String serviceAccountEmail, String projectId, String sharedDriveId, String sharedDriveName, String ageRecipient, String updatedBy, String updatedAt)`
  - `record KeyCheckRequest(String serviceAccountJson)` → `record KeyInfo(String serviceAccountEmail, String projectId)`
  - `record ConnectionRequest(String serviceAccountJson, String sharedDrive)` → `GoogleDriveProbe.SharedDrive` (`{id, name}`)
  - `record SaveRequest(String serviceAccountJson, String sharedDrive, String ageRecipient, boolean keySaved)` → `BackupSettingsView`
  - Endpoint: `GET ""`, `POST "/kiem-tra-khoa"`, `POST "/kiem-tra"`, `PUT ""`, `DELETE ""` (204), tất cả `@PreAuthorize` ADMIN.
  - Biến môi trường app đọc qua `@Value("${BACKUP_OFFSITE_RCLONE:}")`, `@Value("${BACKUP_OFFSITE_RSYNC:}")` để tính `ENV`.

- [ ] **Step 1: Viết IT** (thư mục cấu hình tạm qua `@DynamicPropertySource` `application.backup.config-dir`; `@MockitoBean GoogleDriveProbe`; JSON khóa test sinh như Task 2):
  - `hrIsForbidden` → `@WithMockUser(authorities="ROLE_HR")` GET → 403.
  - `getNoneWhenEmpty` → `source` = `NONE`.
  - `checkKeyReturnsEmail` → POST `/kiem-tra-khoa` → email, projectId; file OAuth → 400 `error.backupkeyoauth`.
  - `saveWritesThreeFiles` → probe trả `SharedDrive("0Aabc","HRM backup")`; PUT với link Shared Drive, `age1e3tlg…ktdll`, `keySaved:true` → 200 `source`=`WEB`; `rclone.conf` chứa `team_drive = 0Aabc` và `service_account_file = /data/backup-config/service-account.json`; `service-account.json` quyền `rw-------` (`Files.getPosixFilePermissions`); `offsite.conf` chứa `BACKUP_OFFSITE_RCLONE=gdrive:HRM-backup`, `BACKUP_AGE_RECIPIENT=age1e3tlg…`, `UPDATED_BY=user` (login của `@WithMockUser`), `SHARED_DRIVE_NAME=HRM backup`.
  - `getNeverLeaksKey` → sau khi lưu, body GET không chứa `PRIVATE KEY` và không chứa `private_key`.
  - `saveFailsCheckWritesNothing` → probe ném `EmployeeRuleException(..., "backupdrivenotfound")` → 400, thư mục không có file nào; khi đã có cấu hình cũ, ba file cũ giữ nguyên nội dung.
  - `editKeepsStoredKey` → đã lưu; PUT không có `serviceAccountJson`, cùng `ageRecipient`, `keySaved:false` → 200 (dùng khóa đã lưu, không bắt xác nhận khi khóa age không đổi).
  - `newAgeKeyNeedsConfirmation` → PUT với `ageRecipient` khác khóa đang lưu và `keySaved:false` → 400 `error.backupkeynotsaved`.
  - `noKeyFromEnvSource` → thư mục trống; PUT không có `serviceAccountJson` → 400 `error.backupnokey`, không có file.
  - `deleteRemovesAll` → DELETE → 204, thư mục trống, GET `source`=`NONE`.
  - `missingConfigDir` → `config-dir` trỏ tới đường dẫn không tồn tại (một test class con hoặc property riêng) → PUT 400 `error.backupconfigdir`.
- [ ] **Step 2: Chạy, thấy FAIL** — `-Dtest=BackupSettingsResourceIT` (cần `DOCKER_HOST`, ngoài sandbox).
- [ ] **Step 3: Cài đặt service**: đọc `offsite.conf` → view (email/projectId lấy bằng `ServiceAccountKey.parse` file đã lưu); `save`: parse + validate → `probe.check` → ghi `service-account.json` (quyền `OWNER_READ, OWNER_WRITE`, bỏ qua nếu hệ thống tệp không POSIX), `rclone.conf`, `offsite.conf` (cuối), mỗi file ghi `*.tmp` rồi `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`; `delete`: `offsite.conf` → `rclone.conf` → `service-account.json`; `LOG.info("Cấu hình sao lưu Google Drive: {} bởi {}", "lưu"/"tắt", login)`. Thư mục không tồn tại/không ghi được → `backupconfigdir`.
- [ ] **Step 4: Chạy lại** → PASS; chạy cả `BackupStatusResourceIT` để chắc không ảnh hưởng.
- [ ] **Step 5: Commit** — "Sao lưu: API cấu hình Google Drive (/api/admin/backup-settings) ghi vào thư mục backup-config".

### Task 5: Tab "Sao lưu" trên giao diện

**Files:**
- Modify: `app/package.json` (+ lockfile) — `"age-encryption": "0.3.1"`
- Create: `app/src/main/webapp/app/modules/settings/backup-api.ts`
- Create: `app/src/main/webapp/app/modules/settings/age-key.ts`
- Create: `app/src/main/webapp/app/modules/settings/backup-tab.tsx`
- Modify: `app/src/main/webapp/app/modules/settings/settings-page.tsx` (thêm tab sau `email`)
- Modify: `app/src/main/webapp/app/entities/employee/detail/detail-api.ts` (`ERROR_TEXT` 12 mã; `backupdrivenotfound`, `backupdriveapioff` vào `DETAIL_FIRST`)
- Test: `app/src/main/webapp/app/modules/settings/backup-tab.spec.tsx`, `backup-api.spec.ts`, cập nhật `settings-page.spec.tsx`

**Interfaces:**
- Consumes: JSON Task 4; `GET api/admin/backup-status` (`BackupStatus` trong `app/shared/layout/backup-banner/backup-banner.tsx`).
- Produces:
  - `backup-api.ts`: kiểu `IBackupSettings`, `ISharedDrive`; `backupApi = { get(), checkKey(json: string), test(json: string | null, sharedDrive: string), save(req), disable() }`; `parseSharedDriveId(input: string): string | null` (cùng quy tắc Task 2); `MAX_KEY_BYTES = 16384`.
  - `age-key.ts`: `createAgeKeyPair(): Promise<{ secretKey: string; publicKey: string }>` (dùng `generateX25519Identity` + `identityToRecipient`); `keyFileText(pair, isoDate: string): string` — dòng `# created: <isoDate>`, `# public key: <publicKey>`, dòng giải mã mẫu `# age -d -i hrm-backup-key-<ngày>.txt hrm-YYYY-MM-DD.tar.age | tar -x`, rồi `secretKey`; `keyFileName(isoDate) = hrm-backup-key-<YYYY-MM-DD>.txt`.

- [ ] **Step 1: Viết test** (`vi.mock('axios')`, `vi.mock('./age-key')` trả cặp khóa cố định, giả `URL.createObjectURL`):
  - `backup-api.spec`: `parseSharedDriveId` đúng các link ở Task 2 Step 1; trả `null` cho link sai.
  - `backup-tab.spec`:
    - `NONE` → hiện 3 bước "Khóa Google (service account)", "Shared Drive", "Khóa mã hóa"; nút "Lưu" disabled.
    - Chọn file JSON → gọi `api/admin/backup-settings/kiem-tra-khoa` với nội dung file; hiện email và nút "Sao chép"; lỗi 400 `error.backupkeyoauth` → hiện câu "Đây là OAuth client ID…".
    - Dán link sai → "Link Shared Drive không hợp lệ" ngay trên trang, không gọi API.
    - "Tạo khóa mã hóa" → `createObjectURL` được gọi với Blob chứa `AGE-SECRET-KEY-`, `a.download` = `hrm-backup-key-<hôm nay>.txt`; hiện khóa công khai; có nút "Tải lại file khóa"; "Lưu" vẫn disabled đến khi đánh dấu ô xác nhận.
    - Đủ 3 bước + đánh dấu → "Lưu" gửi PUT `{ serviceAccountJson, sharedDrive, ageRecipient, keySaved: true }`, sau đó hiện bản tóm tắt.
    - `WEB` → tóm tắt (email, "HRM backup", khóa rút gọn, người/lúc cấu hình) + nút "Kiểm tra kết nối", "Sửa", "Tắt sao lưu Google Drive"; Tắt mở hộp xác nhận, xác nhận mới gọi DELETE.
    - "Sửa" khi đã có cấu hình: không bắt chọn lại file, PUT gửi `serviceAccountJson: null` và `ageRecipient` cũ.
    - `ENV` → khối Tình trạng ghi "Cấu hình bằng file .env trên máy chủ".
  - `settings-page.spec`: tab "Sao lưu" có với `ROLE_ADMIN`, không có với `ROLE_HR`.
- [ ] **Step 2: Chạy, thấy FAIL** — `cd app && npx vitest run src/main/webapp/app/modules/settings`.
- [ ] **Step 3: Cài đặt** `npm install age-encryption@0.3.1 --save-exact`, rồi các file trên; khối Tình trạng dùng `backupWarnings`/`BackupStatus` có sẵn; thẻ/chip từ `app/shared/ui`, nút cao ≥ 44px; khóa bí mật chỉ giữ trong state, xóa khi Lưu thành công hoặc rời trang; hướng dẫn mỗi bước có link `https://console.cloud.google.com/apis/library/drive.googleapis.com`, `https://console.cloud.google.com/iam-admin/serviceaccounts`, `https://drive.google.com/drive/shared-drives` (`target="_blank" rel="noreferrer"`).
- [ ] **Step 4: Chạy lại** → PASS; `npx vitest run` toàn bộ; `npx prettier --check` và `npx eslint` cho các file đã sửa.
- [ ] **Step 5: Commit** — "Cài đặt → Sao lưu: cấu hình Google Drive trên web, tạo khóa mã hóa trên trình duyệt".

### Task 6: Tài liệu, build và thử thật

**Files:**
- Modify: `README.md` (mục "Backup & khôi phục")
- Modify: `ops/backup/restore.md`
- Modify: `.env.example` (chú thích `BACKUP_OFFSITE_RCLONE`: nên cấu hình trên web)

- [ ] **Step 1: README**: phần Google Drive rút còn: chuẩn bị trên Google (Workspace, bật Drive API, tạo service account + khóa JSON, tạo Shared Drive) → "Vào Cài đặt → Sao lưu, làm 3 bước". Đoạn rclone.conf/.env hiện tại chuyển xuống tiểu mục "Cấu hình thủ công (không dùng web)". Ghi: cấu hình web được ưu tiên hơn `.env`.
- [ ] **Step 2: restore.md**: file khóa bí mật có thể tên `hrm-backup-key-YYYY-MM-DD.txt` (tạo trên web); khôi phục trên VPS mới: cấu hình lại ở Cài đặt → Sao lưu sau khi app chạy.
- [ ] **Step 3: Kiểm tra toàn bộ**: `cd app && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -q test -Dtest='BackupInputsTest,GoogleDriveProbeTest,BackupSettingsResourceIT,BackupStatusResourceIT'` + `BackupStatusResourceIT`; `npx vitest run`; `offsite-test.sh` → `TẤT CẢ ĐẠT`.
- [ ] **Step 4: Build và chạy**: `docker compose up -d --build app backup` → app `healthy`; `docker compose exec app ls -ld /data/backup-config` thuộc `hrm`.
- [ ] **Step 5: Thử thật (người dùng thao tác phần khóa bí mật)**: người dùng mở Cài đặt → Sao lưu, tải file khóa service account, dán link Shared Drive "HRM backup", tạo khóa, Lưu; Claude kiểm tra `docker compose exec backup cat /data/backup-config/offsite.conf` (không in `service-account.json`), chạy `docker compose exec backup /ops/backup/backup.sh` → `"offsiteOk": true`.
- [ ] **Step 6: Commit** — "Sao lưu: tài liệu cấu hình Google Drive trên web".
