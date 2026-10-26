# Mã, số văn bản, mật khẩu mặc định; gộp Cài đặt Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Quản trị đặt trên web tiền tố + mã loại số văn bản, tiền tố mã nhân viên, mật khẩu mặc định (mặc định mới `Doit@2024`); mục Quản trị gộp vào Cài đặt.

**Architecture:** 10 cột mới (nullable) trong `company_setting`; `NumberingSettingsService` trả giá trị hiệu lực (null → mặc định) cho cấp số văn bản, cấp mã nhân viên, nhập file; `DefaultPasswordService` tính mật khẩu hiệu lực (web → `.env` → `Doit@2024`) cho mọi nơi tạo/đặt lại mật khẩu. Giao diện: tab mới "Mã & số văn bản", thẻ mật khẩu trong tab "Tài khoản", bỏ mục Quản trị, chuyển hướng địa chỉ cũ.

**Tech Stack:** Spring Boot (Java 21, Liquibase, Envers, Jackson `tools.jackson`), React 19 + react-bootstrap, Vitest.

**Spec:** `docs/superpowers/specs/2026-10-26-hrm-ma-so-van-ban-gop-cai-dat-design.md`

## Global Constraints

- Đổi cấu hình chỉ áp dụng cho văn bản/nhân viên/tài khoản tạo mới; không sửa dữ liệu đã có (Liquibase chỉ thêm cột; không UPDATE dữ liệu).
- Cột mới (nullable) trong `company_setting` và `company_setting_aud`: `document_prefix`, `document_code_hdld`, `document_code_hdtv`, `document_code_hddt`, `document_code_hddv`, `document_code_qdcns`, `staff_code_prefix`, `intern_code_prefix`, `service_code_prefix`, `default_password`. Độ dài cột: mã/tiền tố `varchar(10)`, mật khẩu `varchar(100)`.
- Mặc định khi null: tiền tố `DOIT`; mã loại = `DocumentType.code()` hiện nay (`HĐLĐ`, `HĐTV`, `HĐĐT`, `HĐDV`, `QĐCNS`); tiền tố mã `DOIT`, `TT`, `HDDV`; mật khẩu = `application.account.default-password` (`.env`) nếu có, không thì `Doit@2024` (`ApplicationProperties.Account.DEFAULT_PASSWORD` đổi thành `Doit@2024`).
- Giá trị lưu `trim()`; rỗng → null.
- Quy tắc và lỗi (`throw new EmployeeRuleException("<câu>", "<mã>")`; câu tiếng Việt thêm vào `ERROR_TEXT` trong `app/src/main/webapp/app/entities/employee/detail/detail-api.ts`):
  - `numberingdocinvalid` — tiền tố/mã loại văn bản không khớp `^[\p{Lu}0-9-]{1,10}$` — "Tiền tố và mã loại văn bản chỉ gồm chữ in hoa, số, gạch ngang, tối đa 10 ký tự, không có dấu \"/\"."
  - `numberingdocduplicate` — 5 mã loại (hiệu lực, sau khi thay mặc định) trùng nhau — "Mã các loại văn bản phải khác nhau."
  - `numberingcodeinvalid` — tiền tố mã không khớp `^[A-Z][A-Z0-9]{0,9}$` — "Tiền tố mã nhân viên chỉ gồm chữ A–Z và số, bắt đầu bằng chữ, tối đa 10 ký tự."
  - `numberingcodeduplicate` — 3 tiền tố (hiệu lực) trùng nhau — "Ba tiền tố mã nhân viên phải khác nhau."
  - `defaultpasswordinvalid` — mật khẩu < 8 hoặc > 100 ký tự — "Mật khẩu mặc định phải có từ 8 đến 100 ký tự."
- API: `GET /api/company-setting/numbering` (ADMIN, HR, KE_TOAN), `PUT` (ADMIN); `GET /api/admin/account-settings/default-password` (ADMIN, HR — giữ nguyên, thêm `source`), `PUT` (ADMIN). Không log giá trị mật khẩu: resource/service liên quan mật khẩu gắn `@NoArgumentLogging` (`vn.doit.hrm.aop.logging.NoArgumentLogging`).
- Comment, thông báo, commit tiếng Việt; commit kết thúc `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Maven: `JAVA_HOME=/opt/homebrew/opt/openjdk@21`; IT cần `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock`, chạy ITs bằng `./mvnw -q verify -Dit.test=<IT> -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false` (failsafe), unit test bằng `./mvnw -q test -Dtest=<Test>`.

## Review Focus

1. Đổi tiền tố số văn bản giữa năm khi đã có số tay `DOIT/2026/HĐLĐ/13` — số kế tiếp phải bỏ qua số đã có dưới tiền tố mới, không cấp trùng. → test trong Task 2.
2. Mã loại nhập chữ thường hoặc có khoảng trắng (`hđlđ `) — máy chủ chuẩn hóa `trim()` rồi kiểm tra; chữ thường bị từ chối với câu rõ ràng (không tự viết hoa ngầm). → test trong Task 1.
3. HR mở tab Mã & số văn bản hoặc Tài khoản bằng địa chỉ trực tiếp — HR thấy chỉ đọc ở Mã & số; tab Tài khoản không hiện và địa chỉ `/cai-dat/tai-khoan` chuyển về tab đầu HR được xem. → test trong Task 4.
4. Link sâu cũ `/admin/user-management/abc/edit` — chuyển đúng tới `/cai-dat/tai-khoan/abc/edit`. → test trong Task 4.
5. Tạo tài khoản ngay sau khi Quản trị đổi mật khẩu mặc định — dùng giá trị mới (không cache). → test trong Task 3.

---

### Task 1: Cấu hình số văn bản & mã — dữ liệu, quy tắc, API

**Files:**
- Create: `app/src/main/resources/config/liquibase/changelog/20261105000100_company_setting_numbering.xml` (+ include trong `master.xml` sau changeset cuối)
- Modify: `app/src/main/java/vn/doit/hrm/domain/CompanySetting.java` (10 trường + getter/setter)
- Create: `app/src/main/java/vn/doit/hrm/service/numbering/NumberingSettings.java`, `NumberingRules.java`, `NumberingSettingsService.java`
- Create: `app/src/main/java/vn/doit/hrm/service/dto/NumberingSettingsDTO.java`
- Create: `app/src/main/java/vn/doit/hrm/web/rest/NumberingSettingsResource.java`
- Test: `app/src/test/java/vn/doit/hrm/service/numbering/NumberingRulesTest.java`, `app/src/test/java/vn/doit/hrm/web/rest/NumberingSettingsResourceIT.java`

**Interfaces:**
- Produces:
  - `record NumberingSettings(String documentPrefix, Map<DocumentType, String> documentCodes, String staffCodePrefix, String internCodePrefix, String serviceCodePrefix)`; `static NumberingSettings defaults()`; `String documentCode(DocumentType t)`; `String sequencePrefix(PersonType t)` (NHAN_VIEN → staff, THUC_TAP → intern, DICH_VU → null); `List<String> sequencePrefixes()` (staff, intern).
  - `record NumberingSettingsDTO(String documentPrefix, Map<DocumentType, String> documentCodes, String staffCodePrefix, String internCodePrefix, String serviceCodePrefix, NumberingSettingsDTO defaults)` — JSON keys của `documentCodes`: `HDLD`, `HDTV`, `HDDT`, `HDDV`, `QD_CU_NHAN_SU`.
  - `final class NumberingRules { static NumberingSettingsDTO normalizeAndValidate(NumberingSettingsDTO in) }` — trim, rỗng → null, kiểm tra theo Global Constraints trên giá trị hiệu lực.
  - `@Service NumberingSettingsService { NumberingSettings current(); NumberingSettingsDTO view(); NumberingSettingsDTO update(NumberingSettingsDTO in) }` (`@Transactional`; đọc qua `CompanySettingService.current()`).
  - `GET/PUT /api/company-setting/numbering` (quyền theo Global Constraints).

- [ ] **Step 1: Viết test**
  - `NumberingRulesTest`: `acceptsVietnameseUppercase` (`HĐLĐ`, `QĐ-CNS`, `ABC1`); `rejectsSlashLongLowercase` (`A/B`, `ABCDEFGHIJK`, `hđlđ` → `numberingdocinvalid`); `trimsAndBlanksToNull` (`" ABC "` → `ABC`, `"  "` → null); `rejectsDuplicateDocCodes` (HDLD=`X`, HDTV=`X` → `numberingdocduplicate`; HDTV=`HĐLĐ` trùng mặc định của HDLD → `numberingdocduplicate`); `rejectsBadCodePrefix` (`1AB`, `A-B`, `ab` → `numberingcodeinvalid`); `rejectsDuplicateCodePrefix` (intern=`DOIT` khi staff null → `numberingcodeduplicate`).
  - `NumberingSettingsResourceIT`: `getDefaults` → `documentPrefix`=`DOIT`, `documentCodes.HDLD`=`HĐLĐ`, `staffCodePrefix`=`DOIT`, `defaults.serviceCodePrefix`=`HDDV`; `adminSavesAndNullResets` (PUT prefix `ABC`, staff `NV` → GET trả `ABC`/`NV`; PUT tất cả null → về mặc định); `hrReadsButCannotWrite` (HR GET 200, PUT 403); `nhanVienForbidden` (GET 403); `invalidReturns400` (PUT `A/B` → 400 `error.numberingdocinvalid`); `auditRow` (sau PUT, `company_setting_aud` có bản ghi chứa `document_prefix`=`ABC`).
- [ ] **Step 2: Chạy, thấy FAIL** — `cd app && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -q test -Dtest=NumberingRulesTest`; IT bằng lệnh failsafe trong Global Constraints.
- [ ] **Step 3: Cài đặt** theo Interfaces; changeset `addColumn` cho cả hai bảng, theo phong cách `20261101000100_company_setting.xml`.
- [ ] **Step 4: Chạy lại** → PASS; chạy thêm IT có sẵn của company setting (`CompanySettingResourceIT` nếu có) để chắc không vỡ.
- [ ] **Step 5: Commit** — "Cài đặt: lưu tiền tố và mã số văn bản, tiền tố mã nhân viên (API /api/company-setting/numbering)".

### Task 2: Áp dụng cấu hình vào cấp số văn bản, mã nhân viên, nhập file

**Files:**
- Modify: `app/src/main/java/vn/doit/hrm/service/DocumentNumberService.java` (`format` dùng cấu hình; javadoc bỏ "DOIT")
- Modify: `app/src/main/java/vn/doit/hrm/service/EmployeeCodes.java` (bỏ 3 hằng tiền tố cố định khỏi đường cấp mã; `prefix` nhận cấu hình)
- Modify: `app/src/main/java/vn/doit/hrm/service/EmployeeService.java` (~254–259, ~293)
- Modify: `app/src/main/java/vn/doit/hrm/service/employee/EmployeeImportCheck.java`, `EmployeeImportService.java` (~182)
- Modify: `app/src/main/java/vn/doit/hrm/domain/enumeration/DocumentType.java` (javadoc)
- Test: cập nhật test hiện có của các lớp trên + `app/src/test/java/vn/doit/hrm/service/numbering/NumberingAppliedIT.java`

**Interfaces:**
- Consumes: `NumberingSettings`, `NumberingSettingsService.current()` (Task 1).
- Produces: `static String DocumentNumberService.format(NumberingSettings s, DocumentType type, int year, int n)`; `EmployeeCodes.prefix(PersonType type, NumberingSettings s)`; `EmployeeImportCheck.run(parsed, ImportDirectory dir, LocalDate today, NumberingSettings s)`; mã HĐ dịch vụ = `s.serviceCodePrefix() + nextServiceCodeNumber()`. `DocumentSequenceDTO.code` trả mã loại hiệu lực.

- [ ] **Step 1: Viết test**
  - `NumberingAppliedIT` (IT, Spring): `docPrefixChangeMidYearContinues` — cấp 1 số HĐLĐ 2026 (dạng `DOIT/2026/HĐLĐ/n`), đổi prefix `ABC` → số kế tiếp `ABC/2026/HĐLĐ/n+1`; `skipsManualNumberUnderNewPrefix` — đã có hợp đồng số tay `ABC/2026/HĐLĐ/<n+1>` → số cấp là `ABC/2026/HĐLĐ/<n+2>` (Review Focus 1); `customTypeCode` — mã HDLD=`LD` → `ABC/2026/LD/…`; `staffPrefixChangeStartsNewSequence` — có nhân viên `DOIT2601`, đổi staff=`NV` → mã gợi ý/cấp mới `NV2601`, `DOIT2601` không đổi; `servicePrefixKeepsCounter` — đổi service=`DV` → mã mới `DV<k>` với k > số của mã HDDV trước; `importUsesConfiguredPrefixes` — file nhập có mã `NV2605` được nhận là mã đúng dạng (không cảnh báo), mã bắt đầu `DV` → lỗi dành cho HĐ dịch vụ, câu cảnh báo nêu tiền tố thực tế.
  - Cập nhật unit test hiện có (`EmployeeCodes`, `EmployeeImportCheck`, `DocumentNumberService` nếu có) truyền `NumberingSettings.defaults()`.
- [ ] **Step 2: Chạy, thấy FAIL.**
- [ ] **Step 3: Cài đặt.** Mọi nơi gọi đọc `numberingSettingsService.current()` một lần cho mỗi thao tác. Câu cảnh báo nhập file: "Mã không theo dạng <staff>/<intern> + số; dãy tự cấp bỏ qua mã này", "Mã bắt đầu bằng <service> dành riêng cho HĐ dịch vụ (tự cấp)", "HĐ dịch vụ luôn được cấp mã <service> tự động; bỏ qua mã đã ghi".
- [ ] **Step 4: Chạy lại** → PASS; chạy toàn bộ test có chữ `Employee`, `Contract`, `Decision`, `DocumentNumber`, `Import` (unit + IT) để chắc không vỡ.
- [ ] **Step 5: Commit** — "Cấp số văn bản và mã nhân viên theo cấu hình (đổi cấu hình chỉ áp dụng về sau)".

### Task 3: Mật khẩu mặc định cấu hình trên web

**Files:**
- Create: `app/src/main/java/vn/doit/hrm/service/DefaultPasswordService.java`
- Modify: `app/src/main/java/vn/doit/hrm/config/ApplicationProperties.java` (`DEFAULT_PASSWORD = "Doit@2024"`; thêm `String configuredDefaultPassword()` trả giá trị `.env` hoặc null)
- Modify: `app/src/main/java/vn/doit/hrm/service/UserService.java` (~122, ~392, ~405), `EmployeeAccountService.java` (~130), `app/src/main/java/vn/doit/hrm/web/rest/AccountSettingsResource.java`, `config/SecurityConfiguration.java` (~102 nếu cần cho PUT)
- Test: `app/src/test/java/vn/doit/hrm/service/DefaultPasswordServiceTest.java`, `app/src/test/java/vn/doit/hrm/web/rest/DefaultPasswordSettingsIT.java`; cập nhật test hiện có đang dùng `Doit@2014`

**Interfaces:**
- Consumes: `CompanySetting.defaultPassword` (Task 1), `CompanySettingService.current()`.
- Produces: `@Service DefaultPasswordService { String effective(); Source source(); View view(); View update(String defaultPassword) }`; `enum Source { WEB, ENV, DEFAULT }`; `record View(String defaultPassword, Source source)`. `GET /api/admin/account-settings/default-password` → `{ defaultPassword, source }`; `PUT` body `{ defaultPassword: string|null }` → View.

- [ ] **Step 1: Viết test**
  - `DefaultPasswordServiceTest`: web có giá trị → WEB; web null, `.env` có → ENV; cả hai trống → `Doit@2024` / DEFAULT; `update("ngan")` → `defaultpasswordinvalid`; `update(" ")` → null (về nguồn dưới).
  - `DefaultPasswordSettingsIT`: HR GET 200 có `source`, HR PUT 403; ADMIN PUT `Moi@12345` → GET `WEB`; tạo tài khoản mới (qua API tạo user không kèm mật khẩu, như luồng hiện có) ngay sau đó → đăng nhập được bằng `Moi@12345` (Review Focus 5); đặt lại mật khẩu về mặc định dùng giá trị mới; PUT null → nguồn quay về ENV/DEFAULT.
  - Mọi test hiện có khẳng định `Doit@2014` đổi thành `Doit@2024`.
- [ ] **Step 2: Chạy, thấy FAIL.**
- [ ] **Step 3: Cài đặt**; thay mọi `applicationProperties.getAccount().getDefaultPassword()` bằng `defaultPasswordService.effective()`; `@NoArgumentLogging` trên `DefaultPasswordService` và `AccountSettingsResource`; giữ header `Cache-Control: no-store` của GET.
- [ ] **Step 4: Chạy lại** → PASS; chạy test `UserService*`, `EmployeeAccount*`, `AccountSettings*`, `UserResourceIT`.
- [ ] **Step 5: Commit** — "Mật khẩu mặc định cấu hình trên web (ưu tiên web → .env → Doit@2024)".

### Task 4: Gộp Quản trị vào Cài đặt (menu, route, chuyển hướng)

**Files:**
- Modify: `app/src/main/webapp/app/shared/layout/sidebar/menu-items.ts` (bỏ mục `quan-tri`), `sidebar.tsx` (bỏ `ADMIN_LINKS` và khối link con `quan-tri`)
- Delete: `app/src/main/webapp/app/shared/layout/menus/admin.tsx` (không nơi nào dùng)
- Modify: `app/src/main/webapp/app/modules/settings/settings-page.tsx` (tab `tai-khoan/*`, `nhat-ky-xoa-nhan-vien`; render `UserManagementRoutes`, `EmployeeDeletions`)
- Modify: `app/src/main/webapp/app/modules/administration/index.tsx` (bỏ tab bar/PageHeader Quản trị; `user-management/*` và `nhat-ky-xoa-nhan-vien` → `Navigate` sang `/cai-dat/...` giữ phần đuôi; giữ route health/metrics/configuration/logs/docs)
- Modify: `app/src/main/webapp/app/modules/administration/user-management/user-management-update.tsx`, `user-management-detail.tsx`, `user-management-delete-dialog.tsx`, `user-management.tsx` (link `/admin/user-management` → `/cai-dat/tai-khoan`)
- Test: `menu-items.spec.ts`, `sidebar.spec.tsx`, `settings-page.spec.tsx`, specs user-management, mới `app/src/main/webapp/app/modules/administration/admin-redirect.spec.tsx`

**Interfaces:**
- Produces: route `/cai-dat/tai-khoan/*` (index, `new`, `:login`, `:login/edit`, `:login/delete`), `/cai-dat/nhat-ky-xoa-nhan-vien`; tab `tai-khoan` roles `ADMIN_ONLY`, tab `nhat-ky-xoa-nhan-vien` chỉ khi `login === SYSTEM_ADMIN_LOGIN` (`app/entities/employee/archive/archive-api`). Task 5 thêm thẻ mật khẩu vào đầu tab Tài khoản và tab `ma-so-van-ban`.

- [ ] **Step 1: Viết test**: menu Quản trị không còn (ADMIN); `SETTINGS_TABS` có `tai-khoan` sau `sao-luu`, rồi `nhat-ky-xoa-nhan-vien`; HR không thấy Tài khoản, mở `/cai-dat/tai-khoan` → về tab đầu HR được xem (Review Focus 3); `/admin/user-management` → `/cai-dat/tai-khoan`; `/admin/user-management/abc/edit` → `/cai-dat/tai-khoan/abc/edit` (Review Focus 4); `/admin/nhat-ky-xoa-nhan-vien` → `/cai-dat/nhat-ky-xoa-nhan-vien`; nút Quay lại/Hủy trong màn tài khoản trỏ `/cai-dat/tai-khoan`.
- [ ] **Step 2: Chạy, thấy FAIL** — `cd app && npx vitest run src/main/webapp/app/shared/layout src/main/webapp/app/modules/settings src/main/webapp/app/modules/administration`.
- [ ] **Step 3: Cài đặt.**
- [ ] **Step 4: Chạy lại** → PASS; `npx vitest run` toàn bộ; prettier + eslint file đã sửa.
- [ ] **Step 5: Commit** — "Gộp Quản trị vào Cài đặt: tab Tài khoản và Nhật ký xóa nhân viên, chuyển hướng địa chỉ cũ".

### Task 5: Tab "Mã & số văn bản", thẻ mật khẩu mặc định, gợi ý theo cấu hình

**Files:**
- Create: `app/src/main/webapp/app/modules/settings/numbering-tab.tsx` (+ spec), `app/src/main/webapp/app/modules/settings/default-password-card.tsx` (+ spec)
- Modify: `app/src/main/webapp/app/modules/settings/settings-api.ts` (API numbering + default password), `settings-page.tsx` (tab `ma-so-van-ban` sau `nghi-phep`, roles ADMIN_HR, `canEdit` = ADMIN; thẻ mật khẩu đầu tab `tai-khoan`)
- Modify: `app/src/main/webapp/app/modules/hop-dong/index.tsx` (bỏ tab/route Số văn bản; `so-van-ban` → `Navigate` `/cai-dat/ma-so-van-ban`)
- Modify: `app/src/main/webapp/app/modules/administration/document-numbers/document-numbers.tsx` (câu `DOIT/năm/mã loại/số` → tiền tố hiệu lực)
- Modify: `app/src/main/webapp/app/shared/util/default-password.tsx` (đọc `source` nếu cần), `app/src/main/webapp/app/entities/employee/employee-form.tsx` (~382), `entities/employee/import/employee-import.tsx` (~201), `modules/payroll/row-mapping.tsx` (~65, ~145): câu gợi ý dùng tiền tố cấu hình
- Modify: `app/src/main/webapp/app/entities/employee/detail/detail-api.ts` (`ERROR_TEXT` 5 mã)
- Test: specs tương ứng

**Interfaces:**
- Consumes: `GET/PUT api/company-setting/numbering` (JSON Task 1), `GET/PUT api/admin/account-settings/default-password` (`{ defaultPassword, source }`, Task 3), `DocumentNumbers` component, `DOCUMENT_TYPE_LABEL`.
- Produces: `settingsApi.numbering()`, `saveNumbering(input)`, `defaultPassword()`, `saveDefaultPassword(value: string | null)`; hook `useNumbering()` (cache trong module, dùng cho các câu gợi ý).

- [ ] **Step 1: Viết test**
  - `numbering-tab.spec`: hiện giá trị hiệu lực và "mặc định: …"; gõ tiền tố `ABC` → dòng xem trước "Số tiếp theo: ABC/<năm>/HĐLĐ/<nextValue>"; Lưu gửi PUT đúng body (rỗng → null); lỗi 400 `error.numberingdocduplicate` hiện câu tiếng Việt; HR (canEdit=false) ô khóa, không có nút Lưu; có câu "Chỉ áp dụng cho văn bản và nhân viên tạo mới; số và mã đã cấp giữ nguyên."; bảng `DocumentNumbers` hiện bên dưới.
  - `default-password-card.spec`: hiện giá trị + nguồn ("Cài trên web" / "Từ file .env trên máy chủ" / "Mặc định Doit@2024"); Lưu < 8 ký tự bị chặn trên trang; "Dùng lại mặc định" gửi `{ defaultPassword: null }`.
  - `settings-page.spec`: tab "Mã & số văn bản" có với ADMIN và HR.
  - Chuyển hướng `/hop-dong/so-van-ban` → `/cai-dat/ma-so-van-ban`.
  - Form nhân viên với cấu hình service=`DV` hiện "Mã DV sẽ được tự sinh".
- [ ] **Step 2: Chạy, thấy FAIL** — `npx vitest run src/main/webapp/app/modules/settings src/main/webapp/app/modules/hop-dong src/main/webapp/app/entities/employee`.
- [ ] **Step 3: Cài đặt**; nút/ô cao ≥ 44px; thẻ theo `SectionCard` của `app/shared/ui`.
- [ ] **Step 4: Chạy lại** → PASS; `npx vitest run` toàn bộ; prettier + eslint.
- [ ] **Step 5: Commit** — "Cài đặt → Mã & số văn bản và mật khẩu mặc định trên web".

### Task 6: Tài liệu và kiểm tra toàn bộ

**Files:**
- Modify: `README.md`, `.env.example` (mọi `Doit@2014` → `Doit@2024`; mục "Cấu hình mã và số văn bản"; mật khẩu mặc định đặt ở Cài đặt → Tài khoản, `.env` là dự phòng; Quản trị → Tài khoản đổi thành Cài đặt → Tài khoản)

- [ ] **Step 1: README + .env.example** theo spec mục 6; `grep -rn "Doit@2014" README.md .env.example app/src/main` không còn kết quả (trừ changelog lịch sử nếu có).
- [ ] **Step 2: Kiểm tra toàn bộ**: backend `cd app && ./mvnw -q verify` đầy đủ (chạy nền, ghi file; đọc tổng từ surefire/failsafe reports); `npx vitest run`.
- [ ] **Step 3: Commit** — "Tài liệu: cấu hình mã, số văn bản, mật khẩu mặc định Doit@2024".
