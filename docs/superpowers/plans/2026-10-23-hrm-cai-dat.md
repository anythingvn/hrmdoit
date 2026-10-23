# Trang Cài đặt hợp nhất – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gom tên công ty, logo, lịch và giờ làm, địa điểm chấm công, email và cài đặt nghỉ phép về một trang `/cai-dat`. Tên công ty và logo sửa trên web là có hiệu lực ngay.

**Architecture:**
- Bảng một dòng `company_setting` lưu tên, tên viết tắt, khẩu hiệu, người đại diện, chức danh và logo (bytea). Dòng này được tạo lúc khởi động từ `application.company.*`.
- API công khai `/api/public/branding` cho giao diện và màn đăng nhập.
- Các service in giấy tờ lấy tên và người đại diện qua `CompanySettingService` thay cho `ApplicationProperties.Company`.
- `work_site` có thêm toạ độ, bán kính và danh sách IP văn phòng.
- Frontend có một trang tab. Các trang cũ (lịch làm việc, email, cài đặt nghỉ phép) được nhúng làm tab, link cũ tự chuyển hướng sang tab tương ứng.

**Tech Stack:** Java 21 / Spring Boot 4, PostgreSQL + Liquibase + Envers, React 19 + Vitest + react-bootstrap.

**Spec:** `docs/superpowers/specs/2026-10-23-hrm-cai-dat-design.md`

## Global Constraints

- **Logo:**
  - Định dạng PNG, JPG hoặc SVG (`image/png`, `image/jpeg`, `image/svg+xml`), tối đa 1 MB (1 048 576 byte).
  - Sai định dạng trả 400 `logoinvalidtype`, quá dung lượng trả 400 `logotoolarge`.
  - Kiểm tra cả kiểu khai báo lẫn byte đầu file: PNG bắt đầu `89 50 4E 47`, JPG `FF D8 FF`, SVG là text chứa `<svg`.
  - Chỉ hiện trên web: menu, màn đăng nhập, favicon, tiêu đề tab. Không in lên giấy tờ.
- **Branding công khai:**
  - `GET /api/public/branding` → `{companyName, shortName, tagline, logoVersion}`, không cần đăng nhập.
  - `GET /api/public/branding/logo` → ảnh logo:
    - kèm `ETag` = `logoVersion` (epoch millis của `logo_updated_at`) và `Cache-Control: public, max-age=300`;
    - với SVG thêm `Content-Security-Policy: sandbox`;
    - chưa có logo tải lên thì trả `doit-logo.png` mặc định.
  - Người đại diện và chức danh không có trong API công khai.
- **Giá trị mặc định khi khởi động:**
  - Dòng `company_setting` id=1 được tạo nếu chưa có, lấy `name`, `representative-name`, `representative-title` từ `application.company.*`.
  - Tên viết tắt mặc định "DOIT HRM", khẩu hiệu mặc định "Do it if you can".
- **Giới hạn trường:**
  - Tên công ty bắt buộc, tối đa 255 ký tự.
  - Tên viết tắt tối đa 30, khẩu hiệu tối đa 100, người đại diện tối đa 100, chức danh tối đa 100.
  - Tên công ty để trống trả 400 `companynamerequired`.
- **Địa điểm chấm công (`work_site`):**
  - `latitude` numeric(9,6) trong khoảng −90..90, `longitude` numeric(9,6) trong khoảng −180..180, chỉ có cả hai hoặc không có cái nào.
  - `radius_m` int từ 20 đến 5000, mặc định 200.
  - `office_ips` là text, mỗi dòng một IPv4/IPv6 hoặc CIDR.
  - Sai thì trả 400 `worksitelocationinvalid`, `worksiteradiusinvalid` hoặc `worksiteipinvalid`.
  - Các cột thêm vào cả `work_site_aud`.
- **Quyền:**
  - Sửa cài đặt công ty và logo: `ROLE_ADMIN`.
  - Đọc người đại diện (`GET /api/company-setting`): ADMIN và HR.
  - Tab hiển thị:
    - Công ty: ADMIN.
    - Lịch & giờ làm: ADMIN, HR (giữ quyền con như trang cũ).
    - Địa điểm chấm công: ADMIN, HR.
    - Email: ADMIN.
    - Nghỉ phép: ADMIN.
  - Mục menu "Cài đặt" hiện cho ADMIN và HR.
- **Link cũ chuyển hướng:**
  - `/danh-muc/lich-lam-viec` → `/cai-dat/lich-lam-viec`
  - `/admin/cau-hinh-email` → `/cai-dat/email`
  - Phần cài đặt nghỉ phép rời khỏi trang Quản lý nghỉ phép, sang `/cai-dat/nghi-phep`.
  - Các tab khác: `/cai-dat/cong-ty`, `/cai-dat/dia-diem`.
- **Ràng buộc chung:**
  - Liquibase chỉ thêm, file mới `20261101…`.
  - Lỗi trả qua `EmployeeRuleException(message, key)`. Mọi key mới có câu tiếng Việt trong `webapp/i18n/vi/global.json` và `ERROR_TEXT` ở `entities/employee/detail/detail-api.ts`.
  - Giao diện tiếng Việt, vùng bấm ≥ 44px.
  - Commit kết thúc bằng `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Không push, không `git stash`.
- **Lệnh kiểm tra:**
  - Biến môi trường: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21; export DOCKER_HOST=unix://$HOME/.docker/run/docker.sock`. IT chạy ngoài sandbox.
  - IT tập trung: `mvn -q -pl app -am verify -Dskip.npm -Dskip.installnodenpm -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=<IT> -Dfailsafe.failIfNoSpecifiedTests=false`.
  - Toàn bộ backend: bỏ các cờ `-Dtest/-Dit.test`.
  - Frontend: `cd app && npm test && npx tsc --noEmit && npm run lint`.

## Review Focus

1. **Logo SVG có `<script>` hoặc `onload`.** Kỳ vọng: vẫn lưu được, nhưng chỉ hiển thị bằng `<img>`, response có `Content-Security-Policy: sandbox`, mở trực tiếp không chạy script. Test ở Task 1: `svgLogoServedSandboxed`.
2. **File đổi đuôi thành `.png` nhưng nội dung là PDF hoặc text.** Kỳ vọng: 400 `logoinvalidtype`. Test ở Task 1: `logoMagicBytesChecked`.
3. **Đổi tên công ty rồi xuất phiếu lương, báo cáo nhân sự hoặc hợp đồng mới.** Kỳ vọng: giấy tờ mới dùng tên mới; file đã sinh trước đó không đổi. Test ở Task 1: `documentsUseCompanySetting`.
4. **Lấy vị trí trên trình duyệt không cho phép** (HTTP không phải localhost, hoặc người dùng chặn quyền). Kỳ vọng: báo rõ lý do, ô toạ độ giữ nguyên, nhập tay vẫn được. Test ở Task 3: `geolocationDeniedShowsReason`.
5. **Toạ độ chỉ nhập một nửa, hoặc IP sai định dạng.** Kỳ vọng: 400 với key tương ứng, không lưu gì. Test ở Task 2: `halfCoordinateRejected`, `badIpRejected`.

---

### Task 1: Cài đặt công ty, logo và API branding

**Files:**
- Create:
  - `domain/CompanySetting.java` (bảng `company_setting`: `id`, `company_name`, `short_name`, `tagline`, `representative_name`, `representative_title`, `logo` bytea, `logo_content_type`, `logo_updated_at`).
  - `repository/CompanySettingRepository.java`.
  - `service/CompanySettingService.java`.
  - `web/rest/CompanySettingResource.java` (`/api/company-setting`).
  - `web/rest/BrandingResource.java` (`/api/public/branding`).
  - Changelog `20261101000100_company_setting.xml`.
- Modify:
  - `SecurityConfiguration` (`permitAll` cho `GET /api/public/branding/**`).
  - `AssignmentDecisionService`, `ContractDataFactory`, `PayslipDocumentService`, `HrReportService`: lấy tên và người đại diện qua `CompanySettingService`.
- Test: `CompanySettingIT`.

**Interfaces:**
- Produces:
  - `CompanySettingService.current(): CompanySetting`. Hàm này tạo dòng id=1 từ `ApplicationProperties.Company` nếu chưa có.
  - `companyName()`, `representativeName()`, `representativeTitle()`.
  - `update(CompanySettingDTO)`.
  - `uploadLogo(byte[] bytes, String contentType)`.
  - `resetLogo()`.
  - REST:
    - `GET /api/company-setting` (ADMIN, HR) trả `CompanySettingDTO {companyName, shortName, tagline, representativeName, representativeTitle, logoVersion}`.
    - `PUT /api/company-setting` (ADMIN).
    - `POST /api/company-setting/logo` multipart `file` (ADMIN).
    - `DELETE /api/company-setting/logo` (ADMIN).
    - `GET /api/public/branding`.
    - `GET /api/public/branding/logo`.

- [ ] **Step 1: Viết test hỏng** `CompanySettingIT`:
  - `seededFromApplicationProperties`: `GET /api/public/branding` trả `companyName` = "CÔNG TY TNHH CÔNG NGHỆ VÀ TRUYỀN THÔNG DO IT", `shortName` = "DOIT HRM", `tagline` = "Do it if you can". Gọi không cần đăng nhập.
  - `adminUpdates`: PUT tên mới → branding trả tên mới. Tên trống → 400 `companynamerequired`. HR gọi PUT → 403.
  - `representativeNotPublic`: branding không có `representativeName`. `GET /api/company-setting` với NHAN_VIEN → 403.
  - `logoUploadAndServe`: tải PNG 1×1 → `logoVersion` đổi. GET logo trả đúng byte, `Content-Type: image/png`, có `ETag`. Gửi kèm `If-None-Match` → 304.
  - `logoTooLarge`: file 1 048 577 byte → `logotoolarge`.
  - `logoMagicBytesChecked`: nội dung `%PDF` khai báo `image/png` → `logoinvalidtype`.
  - `svgLogoServedSandboxed`: SVG có `<script>` → lưu được, response có `Content-Security-Policy: sandbox`.
  - `defaultLogoWhenNone`: chưa tải gì thì trả byte của `doit-logo.png`.
  - `documentsUseCompanySetting`: đổi tên sang "CÔNG TY TEST", sinh báo cáo nhân sự Excel → ô A1 chứa "CÔNG TY TEST". Đổi người đại diện → `AssignmentDecisionService` defaults trả người mới.
- [ ] **Step 2: Chạy test** → FAIL.
- [ ] **Step 3: Viết code.**
  - Entity, changelog, service, hai resource, security.
  - Thay mọi chỗ gọi `properties.getCompany()` trong 4 service trên.
  - Thêm 4 key lỗi kèm câu tiếng Việt.
  - Logo mặc định đọc từ classpath. Copy `webapp/content/images/doit-logo.png` vào `src/main/resources/branding/default-logo.png`.
- [ ] **Step 4: Chạy kiểm tra.** `CompanySettingIT` và các IT đang dùng tên công ty (`PayslipDocumentServiceIT`, `HrReport*IT`, `AssignmentDecision*IT`, `Contract*IT` nếu có) → PASS.
- [ ] **Step 5: Commit.**

### Task 2: Địa điểm chấm công trên `work_site`

**Files:**
- Modify:
  - `domain/WorkSite.java` (`latitude`, `longitude` BigDecimal; `radiusM` Integer, mặc định 200; `officeIps` String).
  - `service/dto/WorkSiteDTO.java` và mapper.
  - `service/WorkSiteService.java` (kiểm tra dữ liệu).
- Create: changelog `20261101000200_work_site_location.xml` (thêm cột vào `work_site` và `work_site_aud`).
- Test: `WorkSiteLocationIT`.

**Interfaces:**
- Produces:
  - `WorkSiteDTO` có thêm `latitude`, `longitude` (number|null), `radiusM` (number), `officeIps` (string|null).
  - Hàm tĩnh `WorkSiteService.parseOfficeIps(String): List<String>`, trả danh sách IP/CIDR đã chuẩn hoá, dùng cho phần 2.

- [ ] **Step 1: Viết test hỏng** `WorkSiteLocationIT`:
  - `saveLocation`: PUT toạ độ 21.028511 / 105.804817, bán kính 150, IP "113.190.1.10\n10.0.0.0/8" → GET trả đúng các giá trị này.
  - `defaultRadius200`: site tạo mới không gửi bán kính → 200.
  - `halfCoordinateRejected`: chỉ có `latitude` → `worksitelocationinvalid`.
  - `outOfRangeRejected`: `latitude` 91 → `worksitelocationinvalid`; bán kính 10 → `worksiteradiusinvalid`; bán kính 5001 → `worksiteradiusinvalid`.
  - `badIpRejected`: "300.1.1.1" → `worksiteipinvalid`; "abc" → `worksiteipinvalid`.
  - `hrMayEdit`: HR được PUT; KE_TOAN gọi → 403.
- [ ] **Step 2: Chạy test** → FAIL.
- [ ] **Step 3: Viết code.** Dùng `InetAddress` với chuỗi đã được kiểm tra dạng số và `/prefix`. Không gọi DNS: chỉ chấp nhận chữ số, dấu `.`, `:` và `/`.
- [ ] **Step 4: Chạy kiểm tra.** `WorkSiteLocationIT`, `AttendanceScheduleIT` và các IT của `WorkSite` đang có → PASS.
- [ ] **Step 5: Commit.**

### Task 3: Trang Cài đặt, branding trên giao diện, README

**Files:**
- Create:
  - `webapp/app/modules/settings/settings-page.tsx`, chứa các tab và route `cong-ty`, `lich-lam-viec`, `dia-diem`, `email`, `nghi-phep`.
  - `modules/settings/company-tab.tsx`.
  - `modules/settings/locations-tab.tsx`.
  - `modules/settings/settings-api.ts`.
  - `shared/branding/branding.ts` (hook `useBranding()` đọc `/api/public/branding`, cache trong bộ nhớ, có hàm làm mới sau khi lưu).
  - Spec vitest cho từng file.
- Modify:
  - `routes.tsx` (thêm `/cai-dat/*` cho ADMIN, HR).
  - `entities/routes.tsx` (`lich-lam-viec` → `<Navigate>`).
  - `modules/administration/index.tsx` (`cau-hinh-email` → `<Navigate>`).
  - `modules/leave/hr-leave.tsx`: bỏ phần cài đặt nghỉ phép, đưa thành component dùng lại ở tab Nghỉ phép.
  - `shared/layout/sidebar/sidebar.tsx`, `modules/login/login-modal.tsx`: dùng logo, `shortName`, `tagline` từ `useBranding()`.
  - `index.html` / app root: cập nhật `document.title` = `shortName` và favicon = `/api/public/branding/logo?v=<logoVersion>`.
  - `shared/layout/sidebar/menu-items.ts`: thêm mục "Cài đặt" cho ADMIN, HR. Nếu "Danh mục" còn mục con trỏ tới lịch làm việc thì bỏ đi.
  - `README.md`: thêm mục "Cài đặt". Chỗ hướng dẫn `APPLICATION_COMPANY_*` ghi rõ các biến này giờ chỉ dùng làm giá trị khởi tạo lần đầu.

**Interfaces:**
- Consumes: các API của Task 1 và Task 2.
- Component cũ được nhúng nguyên: `WorkCalendar` (với `canEditWeek`, `canEditHolidays`, `canEditHours`, `ReminderStatus` như trang cũ), `MailSettings`.

- [ ] **Step 1: Viết test hỏng** (vitest):
  - `settings-page.spec`:
    - HR thấy 2 tab (Lịch & giờ làm, Địa điểm chấm công) và không thấy Công ty, Email, Nghỉ phép.
    - ADMIN thấy đủ 5 tab.
    - `/danh-muc/lich-lam-viec` chuyển sang `/cai-dat/lich-lam-viec`.
  - `company-tab.spec`:
    - Lưu tên → PUT đúng body.
    - Chọn file 2 MB → báo "Logo tối đa 1 MB" và không gọi API.
    - Tải logo thành công → `useBranding` được làm mới.
  - `locations-tab.spec`:
    - `useCurrentLocationFillsCoords`: mock `navigator.geolocation` trả 21.028511/105.804817, độ chính xác 12 → ô toạ độ được điền và hiện "Sai số khoảng 12 m".
    - `geolocationDeniedShowsReason`: lỗi `PERMISSION_DENIED` → hiện "Trình duyệt không cho lấy vị trí", ô toạ độ giữ nguyên.
    - Có link "Xem trên bản đồ" tới `https://www.google.com/maps?q=21.028511,105.804817`.
  - `sidebar.spec`: hiện `shortName` và `tagline` lấy từ branding.
- [ ] **Step 2: Chạy test** → FAIL.
- [ ] **Step 3: Viết code.**
  - Tab Công ty: form các trường, ô chọn logo có xem trước, nút "Khôi phục logo mặc định".
  - Tab Địa điểm: bảng các `WorkSite` loại `VAN_PHONG`/`KHACH_HANG`, form sửa toạ độ, bán kính, IP, nút "Lấy vị trí hiện tại" (`enableHighAccuracy: true`, `timeout` 15 s).
  - Cập nhật README.
- [ ] **Step 4: Chạy kiểm tra.** Frontend và toàn bộ backend → PASS.
- [ ] **Step 5: Commit.**
