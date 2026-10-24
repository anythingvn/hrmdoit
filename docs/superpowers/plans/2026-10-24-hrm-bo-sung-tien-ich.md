# Bổ sung tiện ích – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thêm 5 nhóm việc:
- xuất và nhập ngày lễ bằng Excel;
- phép thâm niên chỉnh được trong cài đặt;
- sửa, xoá mốc luật định chưa dùng;
- giới hạn 5 mẫu văn bản cho mỗi loại, kèm nút xoá;
- khung Lịch sử trên màn chấm công của nhân viên, cùng các tiện ích nhỏ.

**Architecture:** Mỗi task là một phần độc lập, dựa trên code đang có:
- `WorkCalendarService` và `WorkCalendarResource` (ngày lễ);
- `AnnualAccrual.annualDays` trong `core`, `LeaveAccrualService` và `LeaveSetting` (phép);
- `RuleBookService` và `PayrollRuleResource` (mức luật);
- `DocumentTemplateService` (mẫu);
- màn `self-punch.tsx` và `GET /api/attendance/my` (chấm công).

**Tech Stack:** Java 21 / Spring Boot 4, PostgreSQL + Liquibase + Envers, Apache POI, React 19 + Vitest + react-bootstrap.

**Spec:** `docs/superpowers/specs/2026-10-24-hrm-bo-sung-tien-ich-design.md` (binding, gồm cả mục "Làm rõ sau rà soát").

## Global Constraints

### Không áp dụng ngược
- Ngày lễ trong tháng đã chốt công (`AttendanceMonth` DA_CHOT), hoặc trong tháng có kỳ lương `DA_DUYET`, thì không được thêm, đổi tên hay xoá. Áp dụng cho cả thêm/sửa/xoá từng ngày lẫn nhập Excel.
  - Lỗi trả về: 400 `holidaymonthlocked`, câu tiếng Việt "Tháng MM/yyyy đã chốt công hoặc đã duyệt lương – không sửa ngày lễ".
- Phép đã cộng thì giữ nguyên. Cài đặt thâm niên chỉ ảnh hưởng những lần cộng phép sau đó.
- Mốc luật đã dùng thì không sửa, không xoá.

### Ngày lễ – Excel
- Cột A là "Ngày" theo dạng dd/MM/yyyy, nhận cả ô kiểu ngày của Excel. Cột B là "Tên ngày lễ", tối đa 100 ký tự. Dòng 1 là tiêu đề.
- Xuất: `GET /api/holidays/export.xlsx?year=YYYY`, tên file `ngay-le-YYYY.xlsx`.
- Xem trước: `POST /api/holidays/import/preview`, multipart `file`. Trả về `{years:[...], added:[{day,name}], renamed:[{day,oldName,name}], removed:[{day,name}], unchanged:[...], errors:[{row,message}]}`.
- Áp dụng: `POST /api/holidays/import/apply` với cùng file. Server tính lại diff rồi mới ghi, không tin dữ liệu xem trước từ client. Nếu còn lỗi thì trả 400 `holidayimporterrors`.
- Chỉ xoá những ngày nằm trong các năm có trong file. Hai dòng cùng ngày trong file thì báo lỗi.
- Quyền: ghi dành cho ADMIN và HR (như hiện nay). Xuất cho mọi vai trò được xem lịch.

### Phép thâm niên
- `LeaveSetting` thêm `seniority_cycle_years` (int, mặc định 5, khoảng 1..10) và `seniority_extra_days` (int, mặc định 1, khoảng 0..5).
  - Giá trị ngoài khoảng trả 400 `leavesettinginvalid`. Dùng lại key này nếu đã có, chưa có thì thêm mới.
- Mức phép năm của tháng M = `annualLeaveDays` + ⌊thâm niên tính đến **ngày cuối của tháng M** ÷ chu kỳ⌋ × số ngày cộng.
  - Đây là thay đổi có chủ đích so với code hiện tại (đang tính đến cuối tháng trước, cố định 5 năm / 1 ngày).
- Thâm niên = tổng số ngày của từng lần làm nhân viên chính thức (`employment_period` có `person_type` là NHAN_VIEN hoặc null, đếm từ `join_date` đến `last_working_day`), cộng lần làm hiện tại (từ `joinDate` đến ngày tính).
  - Đổi ra năm bằng cách nối các đoạn liên tiếp: lấy `Period` tổng. Cách tính: tổng số ngày đã làm, cộng vào `joinDate` đầu tiên làm mốc ảo, rồi tính `Period` từ mốc ảo đó.
  - Thực tập và HĐ dịch vụ không được cộng (phần nghỉ phép vốn chỉ áp dụng cho NHAN_VIEN).
- Hiển thị: "Phép năm: 12 + 1 (thâm niên 5 năm) = 13 ngày".

### Mức luật định
- Endpoint: `PUT /api/payroll/rules/{id}` và `DELETE /api/payroll/rules/{id}`, chỉ ADMIN.
- Một mốc gọi là "đã dùng" khi:
  - `effectiveFrom` ≤ kỳ lương đã duyệt gần nhất; hoặc
  - có phiên bản bảng lương tham chiếu tới mốc (nếu `PayrollVersion` hay RulesSnapshot có lưu tham chiếu đó).
- Mốc đã dùng: trả 400 `rulemilestoneused`.
- Xoá mốc cuối cùng: trả 400 `rulemilestonelast`.
- Khi sửa, `effectiveFrom` mới vẫn phải sau kỳ lương đã duyệt gần nhất (dùng lại key đang có cho ràng buộc này khi thêm mốc).
- GET trả thêm `used: boolean` cho mỗi mốc.

### Mẫu văn bản
- Tối đa 5 mẫu đang lưu cho mỗi `TemplateKind`. Tải mẫu thứ 6 trả 400 `templatelimit`, câu "Đã có 5 mẫu, hãy xoá bớt mẫu không cần".
- `DELETE /api/document-templates/{kind}/versions/{version}`:
  - Xoá mẫu đang là mặc định (active) trả 400 `templateactive`.
  - Xoá file trong bộ nhớ sau khi commit. Nếu xoá file lỗi thì chỉ ghi log.
  - Văn bản đã sinh từ mẫu đó giữ nguyên.

### Màn chấm công của nhân viên
- Hai cột ở màn rộng (≥ 992px), một cột trên điện thoại (khung Lịch sử nằm dưới).
- Số liệu lấy từ `GET /api/attendance/my?month=yyyy-MM`, dùng lại API đang có. Nếu API thiếu trường cần hiển thị thì bổ sung ở backend, không tính lại ở client.
- Tháng hiển thị dạng "Tháng 10/2026". Hiển thị số **công**, không hiển thị giờ.

### Tiện ích nhỏ
- Bảng công tháng mặc định mở tháng hiện tại.
- Nút **Đặt mật khẩu** trên từng dòng của danh sách người dùng. Ẩn nút ở dòng login `admin` và ở dòng của chính người đang đăng nhập.

### Ràng buộc chung
- Liquibase chỉ thêm, dùng file mới `20261102…`; nếu entity có audit thì thêm cột tương ứng vào bảng `_aud`.
- Mọi key lỗi mới đều có câu tiếng Việt trong `global.json` và `ERROR_TEXT`.
- Giao diện tiếng Việt, vùng bấm ≥ 44px.
- Commit kết thúc bằng `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Không push. Không `git stash`.

### Lệnh chạy
- Thiết lập: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21; export DOCKER_HOST=unix://$HOME/.docker/run/docker.sock` (IT phải chạy ngoài sandbox).
- IT tập trung: `mvn -q -pl app -am verify -Dskip.npm -Dskip.installnodenpm -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=<IT> -Dfailsafe.failIfNoSpecifiedTests=false`
- Lõi: `mvn -q -pl core test -Dtest=<Test>`
- Toàn bộ backend: bỏ các tham số `-Dtest`.
- Frontend: `cd app && npm test && npx tsc --noEmit && npm run lint`.

## Review Focus

1. **Nhập file ngày lễ có một dòng thuộc tháng đã chốt công, các dòng khác hợp lệ.**
   - Kỳ vọng: bản xem trước báo lỗi đúng dòng đó, áp dụng bị từ chối, dữ liệu không đổi.
   - Test ở Task 1: `importRejectsLockedMonthRow`.
2. **Người được nhận làm lại, cộng các lần làm thì đủ 5 năm, riêng lần hiện tại chưa đủ.**
   - Kỳ vọng: được cộng thêm phép ngay từ tháng đủ năm.
   - Test ở Task 2: `seniorityIncludesPriorPeriods`.
3. **Sửa mốc luật có hiệu lực đúng bằng tháng của kỳ đã duyệt gần nhất.**
   - Kỳ vọng: 400 `rulemilestoneused`.
   - Test ở Task 3: `editUsedMilestoneRejected`.
4. **Xoá mẫu mặc định, hoặc tải lên khi đã có 5 mẫu.**
   - Kỳ vọng: 400 với key tương ứng, file và bản ghi còn nguyên.
   - Test ở Task 4: `deleteActiveRejected`, `sixthUploadRejected`.
5. **Nhân viên xem tháng trước trong khung Lịch sử khi tháng đó đã chốt.**
   - Kỳ vọng: số liệu hiển thị đúng như số đã chốt, không có nút Giải trình.
   - Test ở Task 5: `closedMonthHistoryNoExplain`.

---

### Task 1: Ngày lễ – xuất/nhập Excel và khoá tháng đã chốt
**Files:**
- Modify: `service/reminder/WorkCalendarService.java` (khoá tháng cho add/update/delete/copy)
- Modify: `web/rest/WorkCalendarResource.java`
- Create: `service/HolidayExcelService.java`
- Modify: `webapp/app/modules/work-calendar/work-calendar.tsx`
- Test: `HolidayExcelIT`, `work-calendar.spec.tsx`

**Interfaces:**
- Produces: `HolidayExcelService.export(int year): byte[]`, `preview(MultipartFile): HolidayImportPreview`, `apply(MultipartFile): HolidayImportPreview`.

- [ ] **Step 1: Viết test hỏng.** `HolidayExcelIT` gồm các ca:
  - `exportRoundTrip`: xuất năm 2027 có 01/01 "Tết Dương lịch", đọc lại file và thấy đúng dòng đó.
  - `previewCategorises`: file có 1 ngày mới, 1 ngày đổi tên, 1 ngày trùng hệt, và thiếu 1 ngày đang có. Kết quả: added 1, renamed 1, unchanged 1, removed 1.
  - `importOnlyTouchesYearsInFile`: file chỉ có năm 2027 thì các ngày của năm 2026 còn nguyên.
  - `duplicateDateInFileIsError`.
  - `importRejectsLockedMonthRow`: tháng 11/2026 đã chốt, dòng 20/11/2026 báo lỗi `holidaymonthlocked`, apply trả 400 và dữ liệu không đổi.
  - `singleEditLockedMonthRejected`: thêm/xoá ngày trong tháng có kỳ lương DA_DUYET thì trả 400.
  - `nhanVienCannotImport` → 403.

  Vitest: nút Xuất gọi đúng URL. Chọn file xong thì hiện bảng xem trước theo nhóm. Có lỗi thì nút Áp dụng bị tắt.
- [ ] **Step 2: Chạy test** → FAIL.
- [ ] **Step 3: Viết code.** Đọc ô ngày bằng POI, nhận cả ô kiểu ngày lẫn chuỗi dd/MM/yyyy.
- [ ] **Step 4: Chạy lại test** → PASS (cả các IT về lịch làm việc đang có).
- [ ] **Step 5: Commit.**

### Task 2: Phép thâm niên chỉnh được trong cài đặt
**Files:**
- Modify: `core/.../leave/AnnualAccrual.java`. Đổi chữ ký thành `annualDays(int baseRate, int seniorityYears, int cycleYears, int extraDays)` và thêm hàm `seniorityYears(List<DateRange> spans, LocalDate asOf)`.
- Modify: `domain/LeaveSetting.java` và changelog `20261102000100_leave_seniority.xml`.
- Modify: `LeaveAccrualService` (dòng ~337), lấy các lần làm qua `EmploymentPeriodRepository`.
- Modify: phần hiển thị mức phép trên giao diện (`leave-settings.tsx` có 2 ô mới; tab Nghỉ phép trong hồ sơ và màn Nghỉ phép hiện công thức).
- Test: `AnnualAccrualTest` (core), `LeaveSeniorityIT`.

**Interfaces:**
- Produces: `LeaveBalanceService` (hoặc DTO số dư đang có) trả thêm `baseAnnualDays`, `seniorityYears`, `seniorityExtraDays`, `annualDays`.

- [ ] **Step 1: Viết test hỏng.**
  - Core `AnnualAccrualTest`:
    - `annualDays(12, 5, 5, 1) == 13`, `(12, 9, 5, 1) == 13`, `(12, 10, 5, 1) == 14`, `(12, 10, 5, 0) == 12`, `(12, 6, 3, 2) == 16`.
    - `seniorityYears`: các đoạn 01/01/2018–31/12/2020 và 01/01/2023–nay; tính tại 31/12/2024 ra 5 năm; tính tại 30/11/2024 ra 4 năm.
  - `LeaveSeniorityIT`:
    - `monthOfAnniversaryGetsExtra`: vào làm 15/06/2022, chu kỳ 5. Tháng 06/2027 được cộng 13/12, tháng 05/2027 được cộng 12/12.
    - `seniorityIncludesPriorPeriods`: lần làm trước dài 3 năm, lần hiện tại đến ngày tính dài 2 năm → tháng đó được cộng thêm.
    - `internPeriodsNotCounted`.
    - `settingChangeNotRetroactive`: đã cộng tháng 05, đổi chu kỳ sang 3 năm → phép đã cộng tháng 05 không đổi, tháng 06 tính theo chu kỳ mới.
    - `settingValidation`: chu kỳ 0 hoặc 11, số ngày 6 → 400.
- [ ] **Step 2: Chạy test** → FAIL.
- [ ] **Step 3: Viết code.** Cập nhật mọi chỗ đang gọi `annualDays(...)` (tìm hết bằng grep).
- [ ] **Step 4: Chạy lại test** → PASS (cả các IT về nghỉ phép, cộng phép, nhận làm lại).
- [ ] **Step 5: Commit.**

### Task 3: Mức luật định – sửa và xoá mốc chưa dùng
**Files:**
- Modify: `service/payroll/RuleBookService.java`, `web/rest/PayrollRuleResource.java`
- Modify: trang Mức luật định ở giao diện (`PayrollRules`, dưới `entities/routes.tsx` `muc-luat`)
- Test: `PayrollRuleEditIT`, spec vitest

- [ ] **Step 1: Viết test hỏng.** `PayrollRuleEditIT` gồm:
  - `editUnusedMilestone` → 200, giá trị được cập nhật.
  - `editUsedMilestoneRejected`: mốc có hiệu lực ≤ kỳ đã duyệt gần nhất → `rulemilestoneused`.
  - `deleteUnused` → 204.
  - `deleteLastRejected` → `rulemilestonelast`.
  - `editMovesBeforeApprovedRejected`.
  - `getMarksUsed`.
  - `hrForbidden`.

  Vitest: mốc đã dùng thì không có nút Sửa/Xoá và hiện ghi chú khoá. Xoá phải qua bước xác nhận.
- [ ] **Step 2: Chạy test** → FAIL.
- [ ] **Step 3: Viết code.** Đọc cách `PayrollVersion`/RulesSnapshot lưu mốc đã dùng. Nếu không lưu, chỉ dựa vào điều kiện ngày hiệu lực.
- [ ] **Step 4: Chạy lại test** → PASS (cả các IT bảng lương đang có).
- [ ] **Step 5: Commit.**

### Task 4: Mẫu văn bản – tối đa 5 mẫu, nút xoá
**Files:**
- Modify: `service/DocumentTemplateService.java`, `web/rest/DocumentTemplateResource.java`
- Modify: trang mẫu ở giao diện (`modules/hop-dong`)
- Test: `DocumentTemplateLimitIT`, spec vitest

- [ ] **Step 1: Viết test hỏng.** `DocumentTemplateLimitIT` gồm:
  - `sixthUploadRejected` → `templatelimit`.
  - `deleteInactiveRemovesRowAndFile`: file bị xoá sau commit.
  - `deleteActiveRejected` → `templateactive`.
  - `generatedDocsUnaffected`: văn bản đã sinh vẫn tải về được sau khi xoá mẫu.
  - `afterDeleteCanUploadAgain`.

  Vitest: mẫu mặc định không có nút Xoá. Hiện "x/5 mẫu".
- [ ] **Step 2: Chạy test** → FAIL.
- [ ] **Step 3: Viết code.**
- [ ] **Step 4: Chạy lại test** → PASS.
- [ ] **Step 5: Commit.**

### Task 5: Khung Lịch sử ở màn chấm công, bảng công mở tháng hiện tại, nút đặt mật khẩu trên danh sách, README
**Files:**
- Modify: `webapp/app/modules/attendance/self-punch.tsx` (bố cục 2 cột)
- Create: `modules/attendance/punch-history.tsx` cùng spec
- Modify: `modules/attendance/month-board.tsx` (mặc định tháng hiện tại)
- Modify: `modules/administration/user-management/user-management.tsx` (thêm nút trên từng dòng, dùng `SetPasswordDialog`)
- Modify: `README.md` (ghi lại cả 5 task)
- Backend (nếu cần): bổ sung trường còn thiếu cho `GET /api/attendance/my`
- Test: `punch-history.spec.tsx`, `month-board.spec.tsx`, spec user-management; thêm `AttendanceMyIT` nếu có sửa backend

- [ ] **Step 1: Viết test hỏng.**
  - `punch-history.spec`:
    - Phần tổng hiện "Công thực tế 20,5 / 22", "Đi muộn 1", "Thiếu giờ 1".
    - Mỗi dòng hiện "09:50 → 17:00 · 0,5 công" kèm nhãn "Muộn 80'".
    - Ngày thiếu giờ ra có nút Giải trình.
    - `closedMonthHistoryNoExplain`.
    - Dòng tiêu đề tháng hiện "Tháng 10/2026".
  - `month-board.spec`: lần đầu gọi API với tháng hiện tại.
  - Spec user-management:
    - Dòng `nhanvien1` có nút Đặt mật khẩu và nút mở được hộp thoại.
    - Dòng `admin` và dòng của chính mình không có nút.
- [ ] **Step 2: Chạy test** → FAIL.
- [ ] **Step 3: Viết code**, rồi cập nhật README.
- [ ] **Step 4: Chạy frontend và toàn bộ backend** → PASS.
- [ ] **Step 5: Commit.**
