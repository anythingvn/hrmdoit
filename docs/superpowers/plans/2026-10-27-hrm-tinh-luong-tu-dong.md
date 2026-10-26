# Tính lương tự động Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kế toán bấm "Tính lương" cho kỳ có bảng công đã chốt, hệ thống tính đủ 36 khoản từ dữ liệu sẵn có, Kế toán nhập/sửa trên web rồi xác nhận thành phiên bản của kỳ (duyệt, phiếu lương giữ nguyên).

**Architecture:** Bộ tính thuần trong module `core` (`vn.doit.hr.payroll.calc`): đầu vào → 36 khoản + cảnh báo, theo thông số và mức luật của kỳ. App gom đầu vào (`PayrollInputCollector`), lưu bản nháp (`payroll_calc_draft*`), xác nhận tạo `PayrollVersion` nguồn `TINH_TOAN` + file Excel 36 cột. Thông số lưu `payroll_calc_setting` theo tháng hiệu lực; UI ở Cài đặt → Bảng lương.

**Tech Stack:** Java 21 (core: thuần, Apache POI cho file; app: Spring Boot, Liquibase, Envers), React 19 + react-bootstrap, Vitest.

**Spec:** `docs/superpowers/specs/2026-10-27-hrm-tinh-luong-tu-dong-design.md`

## Global Constraints

- Công thức cố định theo spec §2.2–2.4; mọi tỷ lệ/ngưỡng lấy từ thông số (spec §2.1), mặc định đúng bảng §2.1. Mức luật (giảm trừ, trần, biểu thuế) lấy từ `RuleBookService.current()` (app) / `RuleBook` (core) như hiện nay.
- Làm tròn từng khoản đến đồng `RoundingMode.HALF_UP` ngay khi tính; khoản sau dùng số đã làm tròn.
- Không đổi dữ liệu cũ: Liquibase chỉ thêm bảng/cột; cột nguồn `payroll_version.source` có `defaultValueComputed`/`defaultValue` `NHAP_FILE`. Kỳ đã duyệt và bộ thông số đã được kỳ đã duyệt dùng không bị sửa.
- Quyền: tính / sửa / xác nhận bản nháp: ADMIN, KE_TOAN; HR, BGD, NHAN_VIEN 403. Thông số: sửa ADMIN; xem ADMIN, KE_TOAN, HR.
- Lỗi nghiệp vụ: `throw new EmployeeRuleException("<câu>", "<mã>")` → 400 `error.<mã>`; câu tiếng Việt thêm vào `ERROR_TEXT` (`app/src/main/webapp/app/entities/employee/detail/detail-api.ts`). Mã mới: `payrollattendancenotclosed` ("Hãy chốt bảng công tháng MM/YYYY trước khi tính lương." — câu cụ thể qua `detail`, thêm vào `DETAIL_FIRST`), `calcdraftclosed` ("Bản tính này đã xác nhận hoặc đã hủy."), `calcnotready` ("Bản tính còn lỗi hoặc chưa xác nhận cảnh báo."), `calcsettinglocked` ("Bộ thông số này đã dùng cho kỳ đã duyệt, hãy thêm bộ mới."), `calcsettinginvalid` ("Thông số tính lương không hợp lệ."). Kỳ đã duyệt dùng mã sẵn có `periodapproved`.
- Dữ liệu lương thật không vào repo: kiểm thử đối chiếu đọc file `../../DO it - Bang luong thang 06_2026 them luon.xlsx` (hoặc `-Dpayroll.file`) như `core/src/test/java/vn/doit/hr/PoiIntegrationTest.java`, bỏ qua khi không có file.
- Comment, thông báo, commit tiếng Việt; commit kết thúc `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Maven `JAVA_HOME=/opt/homebrew/opt/openjdk@21`; core test: `cd core && ../app/mvnw -q test` (hoặc `cd app && ./mvnw -q -f ../pom.xml -pl core test`); app unit `cd app && ./mvnw -q test -Dtest=<T>`; IT `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./mvnw -q verify -Dit.test=<IT> -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false` (chạy nhóm nhỏ — tránh cạn kết nối DB). Sau khi đổi `core`, cài lại cho app: `cd /Users/anythingvn/Desktop/hrmdoit && app/mvnw -q -f pom.xml install -DskipTests -pl core`.
- Không log số tiền lương cá nhân ở mức INFO; không đưa dữ liệu lương thật vào fixture.

## Review Focus

1. Đổi lương giữa tháng khi ngày công thực tế không chia đều (nghỉ không lương rơi vào một đoạn) — lương gộp vẫn hợp lý và tổng không vượt lương cả tháng khi đủ công. → test Task 1.
2. Người nghỉ việc giữa tháng, bảng công đã chốt có dòng của họ — có trong bảng, có trả phép chưa dùng (khi bật), không có lương ngày sau ngày nghỉ. → test Task 3.
3. Kế toán sửa ô "Ngày công thực tế" rồi "Tính lại từ hồ sơ" — ô sửa tay giữ nguyên, các khoản sau tính lại theo số sửa. → test Task 4.
4. Hai người cùng bấm Xác nhận một bản nháp — chỉ một phiên bản được tạo. → test Task 5.
5. NET với người có người phụ thuộc và có thưởng trong tháng — phần thực nhận từ lương vẫn ≥ NET theo ngày công; thưởng chịu thuế bình thường. → test Task 1.

---

### Task 1: Bộ tính lương trong `core`

**Files:**
- Create: `core/src/main/java/vn/doit/hr/payroll/calc/CalcSettings.java`, `CalcInput.java`, `CalcResult.java`, `PayrollCalculator.java`
- Test: `core/src/test/java/vn/doit/hr/payroll/calc/PayrollCalculatorTest.java`, `core/src/test/java/vn/doit/hr/payroll/calc/PayrollCalculatorRealFileTest.java`

**Interfaces:**
- Produces:
  - `record CalcSettings(BigDecimal bhxhEmployee, BigDecimal bhytEmployee, BigDecimal bhtnEmployee, BigDecimal bhxhCompany, BigDecimal bhytCompany, BigDecimal bhtnCompany, boolean unionFeeEnabled, BigDecimal unionFeeRate, BigDecimal hoursPerDay, boolean overtimeBaseIncludesInsuredAllowance, Set<DayKind> actualDayKinds, Map<PayrollField, List<String>> allowanceKeywords, BigDecimal withholdingThreshold, BigDecimal withholdingRate, boolean payUnusedLeaveOnTermination)`; `static CalcSettings defaults()` (giá trị spec §2.1; từ khóa: `PC_CHUYEN_CAN`→["chuyen can"], `PC_XANG_XE`→["xang"], `PC_TRACH_NHIEM`→["trach nhiem"]); `enum DayKind { WORK, PAID_LEAVE, HOLIDAY, TRIP, SICK }`.
  - `record CalcInput(PersonKind kind /* HDLD, THUC_TAP, DICH_VU */, boolean insured, List<SalarySegment> salary, BigDecimal insuranceSalaryOverride /* null = lương+PC */, Map<DayKind, BigDecimal> days, BigDecimal standardDays, List<NamedAllowance> allowances, List<OvertimeItem> overtime, BigDecimal unusedLeaveDays /* null = không nghỉ việc trong kỳ */, int dependants, boolean pitCommitment08, Map<PayrollField, BigDecimal> manual /* 6 khoản nhập tay */, Map<PayrollField, BigDecimal> overrides)`; `record SalarySegment(BigDecimal baseSalary, BigDecimal insuredAllowance, boolean net, BigDecimal workingDayShare /* tỷ lệ ngày làm việc của đoạn trong tháng, tổng = 1 */)`; `record NamedAllowance(String name, BigDecimal amountPerMonth)`; `record OvertimeItem(BigDecimal hours, int ratePercent)`.
  - `record CalcResult(Map<PayrollField, BigDecimal> amounts, List<String> warnings)`.
  - `final class PayrollCalculator { static CalcResult calculate(CalcInput in, CalcSettings s, PayrollRules rules, boolean rulesEstimated) }`.
  - Thứ tự tính: cột đầu vào → override (nếu có trong `overrides`, dùng số override thay số tự tính cho cột đó) → các cột phụ thuộc. Override được phép cho mọi cột trừ tổng `TONG_LUONG_GOP`, `TONG_BH_NLD`, `TONG_BH_NSDLD`, `THUC_NHAN`, `TONG_CHI_PHI` (tổng luôn tính).

- [ ] **Step 1: Viết test quy tắc** (`PayrollCalculatorTest`, số tự đặt — không dùng dữ liệu thật):
  - `grossFullMonth`: lương 20.000.000, PC đóng BH 1.000.000, đủ 22/22 công, 1 người phụ thuộc, mức luật `RuleBook.defaults()` kỳ 2026-06 → kiểm từng cột BH (8/1,5/1%), giảm trừ, thuế lũy tiến (so với `PayrollRules.monthlyPit`), thực nhận, BH công ty 17,5/3/1%, KPCĐ 2%, tổng chi phí = tổng lương gộp + BH công ty + KPCĐ + thừa/thiếu.
  - `midMonthRaise` (Review Focus 1): 2 đoạn 10.000.000 (share 0,5) và 12.000.000 (share 0,5), đủ công → lương gộp 11.000.000; thiếu 2 ngày công → lương gộp = Σ đoạn × công đoạn ÷ chuẩn, nhỏ hơn 11.000.000.
  - `insuranceCap`: mức đóng vượt trần → BHXH tính trên trần; `notInsured` → mọi cột BH = 0, KPCĐ = 0.
  - `insuranceOverride`: `insuranceSalaryOverride` 15.000.000 dùng thay lương+PC.
  - `allowanceMapping`: "Phụ cấp chuyên cần" → PC_CHUYEN_CAN; "PC xăng xe" → PC_XANG_XE; "Ăn trưa" → cộng THUA_THIEU + cảnh báo chứa "Ăn trưa".
  - `overtimeSplit`: 4 giờ 150%, 8 giờ 200%, 2 giờ 300% → OT_CHIU_THUE = 14 × lương giờ; OT_KHONG_CHIU_THUE = (4×0,5 + 8×1 + 2×2) × lương giờ; OT_KHONG_CHIU_THUE không vào thu nhập tính thuế; `overtimeBaseIncludesInsuredAllowance=true` đổi lương giờ.
  - `unusedLeave`: unusedLeaveDays 3 → PHEP_CHUA_SU_DUNG = 3 × (lương+PC) ÷ chuẩn; tắt thông số → 0.
  - `netSalary` (Review Focus 5): NET 15.000.000, 2 người phụ thuộc, đủ công, không thưởng → THUC_NHAN trong [15.000.000; 15.001.000); thêm thưởng KPI 2.000.000 → THUC_NHAN ≥ 15.000.000 + (2.000.000 − phần thuế tăng thêm do thưởng) và LUONG_CO_BAN không đổi so với khi không thưởng; `rulesEstimated=true` → có cảnh báo.
  - `internWithholding`: thực tập tổng 3.000.000 → thuế 300.000; 1.900.000 → 0; có cam kết 08 → 0; không BH, không giảm trừ.
  - `overridesRecompute`: override NGAY_CONG_THUC_TE = 20 → LUONG_GOP, BH, thuế, thực nhận tính theo 20; override THUC_NHAN bị bỏ qua (tổng luôn tính).
  - `settingsChange`: `unionFeeEnabled=false` → KPCĐ 0; tỷ lệ BHXH công ty 0,17 → BHXH_NSDLD theo 17%.
  - `roundingHalfUp`: số lẻ ,5 làm tròn lên ở từng khoản.
- [ ] **Step 2: Viết test đối chiếu file thật** (`PayrollCalculatorRealFileTest`, `assumeTrue(Files.exists(...))`): đọc file bằng `PayrollSheetParser` + `PoiSheetGrid`; mỗi dòng dựng `CalcInput` từ chính các cột đầu vào của dòng (LUONG_CO_BAN, PHU_CAP_DONG_BH, LUONG_DONG_BHXH_BHYT làm `insuranceSalaryOverride`, NGAY_CONG_CHUAN, NGAY_CONG_THUC_TE làm WORK, 3 cột phụ cấp làm `NamedAllowance` tên khớp từ khóa, OT hai cột → dựng ngược thành override OT_CHIU_THUE/OT_KHONG_CHIU_THUE, 6 khoản nhập tay, SO_NGUOI_PHU_THUOC, `insured` = BHXH_NLD > 0, kind theo mã: `TT`→THUC_TAP, `HDDV`/không mã+không BH→DICH_VU nếu thuế = 10%… — dùng heuristic ghi trong test); mức luật kỳ 2026-06. So **từng** cột còn lại, chênh ≤ 1. Test in bảng các ô lệch (mã dòng, cột, file, tính) trước khi assert, để người dùng quyết. Nếu có dòng lệch không giải thích được bằng heuristic đầu vào, **dừng và báo NEEDS_CONTEXT kèm bảng lệch** (không chỉnh công thức cho khớp mà không có xác nhận).
- [ ] **Step 3: Chạy, thấy FAIL** (lớp chưa có).
- [ ] **Step 4: Cài đặt** theo spec §2.2–2.4; NET dùng tìm kiếm nhị phân trên Gross (số nguyên đồng) với hàm thực nhận từ lương, tái dùng logic `NetGrossConverter` nếu phù hợp (không đổi hành vi hiện có của `NetGrossConverter`).
- [ ] **Step 5: Chạy lại** → PASS (`PayrollCalculatorRealFileTest` PASS khi có file; ghi rõ trong báo cáo đã chạy với file thật hay bị bỏ qua). Chạy toàn bộ test `core`.
- [ ] **Step 6: Commit** — "Bộ tính lương trong lõi: 36 khoản theo thông số và mức luật của kỳ".

### Task 2: Lưu thông số, cột mới (cam kết 08, nguồn phiên bản)

**Files:**
- Create: Liquibase `app/src/main/resources/config/liquibase/changelog/20261106000100_payroll_calc.xml` (+ include cuối `master.xml`): bảng `payroll_calc_setting` (id, `effective_from` varchar(7) unique, `settings` text JSON, `created_by`, `created_at`) + `_aud`; cột `employee.pit_commitment_08` boolean not null default false (+ `employee_aud`); cột `payroll_version.source` varchar(20) not null default `NHAP_FILE` (+ `_aud` nếu có).
- Create: `app/src/main/java/vn/doit/hrm/domain/PayrollCalcSetting.java`, `repository/PayrollCalcSettingRepository.java`, `service/payroll/calc/CalcSettingsService.java`, `service/dto/CalcSettingDTO.java`, `web/rest/PayrollCalcSettingResource.java`
- Create: `app/src/main/java/vn/doit/hrm/domain/enumeration/PayrollVersionSource.java` (`NHAP_FILE`, `TINH_TOAN`)
- Modify: `domain/Employee.java` (+ `pitCommitment08`), DTO/mapper hồ sơ để đọc/ghi trường; `domain/PayrollVersion.java` (+ `source`, mặc định `NHAP_FILE`)
- Test: `app/src/test/java/vn/doit/hrm/web/rest/PayrollCalcSettingResourceIT.java`

**Interfaces:**
- Consumes: `CalcSettings` (Task 1).
- Produces: `CalcSettingsService { CalcSettings forPeriod(YearMonth p); List<CalcSettingDTO> list(); CalcSettingDTO add(CalcSettingDTO); CalcSettingDTO update(Long id, CalcSettingDTO); void delete(Long id) }`; `record CalcSettingDTO(Long id, String effectiveFrom, <các trường như CalcSettings, tỷ lệ dạng số thập phân>, boolean locked)`; API `/api/payroll/calc-settings` (GET list ADMIN/KE_TOAN/HR; POST/PUT/DELETE ADMIN). `locked` = có phiên bản đã duyệt của kỳ ≥ effectiveFrom dùng bộ này (kỳ đã duyệt trong khoảng hiệu lực của bộ).

- [ ] **Step 1: Viết IT**: không có bộ → `forPeriod` = `CalcSettings.defaults()`; thêm bộ 2026-07 → kỳ 2026-06 dùng mặc định, 2026-07 và 2026-09 dùng bộ mới; trùng `effectiveFrom` → 400 `calcsettinginvalid`; tỷ lệ âm hoặc > 1 → 400; bộ có kỳ đã duyệt trong khoảng hiệu lực → PUT/DELETE 400 `calcsettinglocked`, `locked=true` trong list; HR GET 200, HR POST 403, NHAN_VIEN GET 403; hồ sơ nhân viên đọc/ghi `pitCommitment08` (qua API hồ sơ hiện có); phiên bản cũ có `source=NHAP_FILE` sau migration.
- [ ] **Step 2: Chạy, thấy FAIL.**
- [ ] **Step 3: Cài đặt** (JSON đọc/ghi bằng Jackson `tools.jackson`; `@Audited` cho entity mới).
- [ ] **Step 4: Chạy lại** → PASS; chạy IT hồ sơ nhân viên + bảng lương hiện có (`EmployeeResourceIT`, `PayrollPeriod*IT`) để chắc không vỡ.
- [ ] **Step 5: Commit** — "Thông số tính lương theo tháng hiệu lực; ô cam kết 08; nguồn phiên bản lương".

### Task 3: Gom đầu vào của kỳ (`PayrollInputCollector`)

**Files:**
- Create: `app/src/main/java/vn/doit/hrm/service/payroll/calc/PayrollInputCollector.java`
- Test: `app/src/test/java/vn/doit/hrm/service/payroll/calc/PayrollInputCollectorIT.java`

**Interfaces:**
- Consumes: `CalcInput` & co (Task 1), `CalcSettingsService.forPeriod` (Task 2), repositories: `AttendanceMonth` (trạng thái `DA_CHOT`), `AttendanceSnapshot` (theo `month` + `employeeId`), `SalaryChange`, `Allowance`, `OvertimeRequest` (`status=HOAN_TAT`, `compensation=TRA_TIEN`, `workDate` trong kỳ, dùng `actualHours`, `rate`), `LeaveLedgerService.balanceAsOf`/`balance` (phép năm còn tại ngày nghỉ việc), `Employee` (`personType`, `insuranceExemption`, `socialInsuranceSalary`, `dependants`, `pitCommitment08`, ngày vào/nghỉ, `taxCode`).
- Produces: `record CollectedRow(Long employeeId, String code, String fullName, String position, CalcInput input, List<String> errors, List<String> warnings)`; `List<CollectedRow> collect(YearMonth period)` — ném `EmployeeRuleException(…, "payrollattendancenotclosed")` nếu tháng chưa `DA_CHOT`; lỗi dòng "Chưa có mức lương" khi không có `SalaryChange` hiệu lực; cảnh báo "Chưa có mã số thuế" cho thực tập/HĐDV có thu nhập dự kiến ≥ ngưỡng được thêm ở Task 4 (sau khi tính).
- Quy tắc: người trong kỳ = có `AttendanceSnapshot` của tháng và (ngày vào ≤ cuối tháng) và (chưa nghỉ hoặc ngày nghỉ ≥ đầu tháng). `SalarySegment.workingDayShare` = số ngày làm việc (theo lịch làm việc công ty) của đoạn trong tháng ÷ tổng ngày làm việc của tháng (chỉ phần trước ngày nghỉ việc). Ngày theo `DayKind` từ snapshot: WORK=`workDays`, PAID_LEAVE=`paidLeaveDays`, HOLIDAY=`holidayDays`, TRIP=`tripDays`, SICK=`sickDays`. NET: `SalarySegment.net=true`, `baseSalary` = `amount` (NET). `PersonKind` từ `personType` (NHAN_VIEN→HDLD).

- [ ] **Step 1: Viết IT** (dữ liệu tự tạo): tháng chưa chốt → `payrollattendancenotclosed`; nhân viên đủ dữ liệu → CalcInput đúng (lương, ngày theo loại, phụ cấp đang hiệu lực, chỉ OT HOAN_TAT + TRA_TIEN trong kỳ, dependants, insured theo `insuranceExemption`); đổi lương ngày 16 → 2 đoạn với share theo ngày làm việc; nghỉ việc ngày 20 (Review Focus 2) → có trong danh sách, `unusedLeaveDays` = phép còn tại ngày nghỉ, share chỉ tính đến ngày nghỉ; không có lương → lỗi dòng; người vào làm sau tháng / nghỉ trước tháng → không có.
- [ ] **Step 2: Chạy, thấy FAIL.** **Step 3: Cài đặt.** **Step 4: Chạy lại** → PASS.
- [ ] **Step 5: Commit** — "Gom đầu vào tính lương của kỳ từ hồ sơ, bảng công đã chốt, làm thêm giờ, phép".

### Task 4: Bản tính nháp — lưu, sửa, tính lại, API

**Files:**
- Create: Liquibase `20261106000200_payroll_calc_draft.xml`: `payroll_calc_draft` (id, `period_id` fk, `status` varchar(15) `DANG_LAM|DA_XAC_NHAN|DA_HUY`, `created_by`, `created_at`, `warnings_acknowledged` boolean), unique một bản `DANG_LAM` mỗi kỳ (partial unique index hoặc kiểm tra trong service có khóa); `payroll_calc_draft_line` (id, `draft_id` fk, `employee_id` fk, `code`, `full_name`, `position`, `input` text JSON, `manual` text JSON, `overrides` text JSON, `amounts` text JSON, `errors` text, `warnings` text, `stale` boolean) + `_aud` cho cả hai.
- Create: domain + repositories; `service/payroll/calc/PayrollCalcDraftService.java`; DTOs; `web/rest/PayrollCalcResource.java`
- Test: `app/src/test/java/vn/doit/hrm/web/rest/PayrollCalcResourceIT.java`

**Interfaces:**
- Consumes: `PayrollInputCollector.collect`, `PayrollCalculator.calculate`, `CalcSettingsService.forPeriod`, `RuleBookService.current().forPeriodOrLatest(period)`, `PayrollValidator` (đối chiếu kết quả như dòng nhập file).
- Produces (API spec §4): `POST /api/payroll/periods/{period}/calc` → `CalcDraftDTO`; `GET /api/payroll/calc/{id}`; `PUT /api/payroll/calc/{id}/lines/{lineId}` body `{ manual: {FIELD: number|null}, overrides: {FIELD: number|null} }` (null = xóa) → `CalcLineDTO`; `DELETE /api/payroll/calc/{id}/lines/{lineId}`; `POST /api/payroll/calc/{id}/recalculate`; `POST /api/payroll/calc/{id}/acknowledge`. `CalcDraftDTO { id, period, status, lines: CalcLineDTO[], totals: {FIELD: number}, canConfirm, warningsAcknowledged, columns: [{field, header, subHeader, input: boolean}] }`; `CalcLineDTO { id, employeeId, code, fullName, amounts, manual, overrides, errors, warnings, stale }`. Cột nhập tay: `THUONG_DU_AN`, `THUONG_KPI`, `TRO_CAP_THOI_VIEC`, `THUA_THIEU`, `QUYET_TOAN_THUE`, `GIAM_TRU_KHAC`.
- Quy tắc: kỳ `DA_DUYET` → `periodapproved`; bản không `DANG_LAM` → `calcdraftclosed`; sửa bất kỳ → tính lại dòng và bỏ `warningsAcknowledged`; recalculate giữ `manual`/`overrides`, thêm người mới, người không còn thuộc kỳ → `stale=true` + lỗi "Không còn thuộc kỳ" (bỏ dòng để đi tiếp); khóa bi quan trên kỳ + bản nháp khi tạo/sửa (như `PayrollPeriodService.confirm`).

- [ ] **Step 1: Viết IT**: chưa chốt bảng công → 400 `payrollattendancenotclosed`; kỳ đã duyệt → 400 `periodapproved`; tạo → dòng đủ 36 khoản, gọi lại POST trả cùng bản; PUT manual THUONG_KPI → THUC_NHAN tăng đúng (sau thuế); PUT override NGAY_CONG_THUC_TE rồi recalculate (Review Focus 3) → override giữ, LUONG_GOP theo số override; PUT null xóa override; DELETE dòng; người thiếu lương → lỗi, `canConfirm=false` đến khi bỏ dòng; cảnh báo → phải acknowledge; HR 403 mọi endpoint; Envers ghi bản sửa dòng.
- [ ] **Step 2: Chạy, thấy FAIL.** **Step 3: Cài đặt.** **Step 4: Chạy lại** → PASS.
- [ ] **Step 5: Commit** — "Bản tính lương nháp: tạo, nhập/sửa ô, tính lại từ hồ sơ (API /api/payroll/calc)".

### Task 5: Xác nhận bản tính thành phiên bản + file Excel

**Files:**
- Modify: `core/src/main/java/vn/doit/hr/payroll/PayrollTemplateWriter.java` — thêm `write(OutputStream out, YearMonth period, List<FullRow> rows)` với `record FullRow(String code, String name, String position, LocalDate contractStart, Map<PayrollField, BigDecimal> amounts)` ghi đủ 36 cột (giá trị số, không công thức) vào đúng vị trí mẫu; test core đọc lại bằng `PayrollSheetParser` ra đúng số.
- Modify: `app/src/main/java/vn/doit/hrm/service/payroll/PayrollPeriodService.java` (thêm `ConfirmedDTO confirmCalc(Long draftId, boolean warningsAcknowledged)` dùng chung phần tạo version/line), `PayrollCalcResource` (`POST /api/payroll/calc/{id}/confirm`)
- Test: `core/.../PayrollTemplateWriterTest` (bổ sung), `app/src/test/java/vn/doit/hrm/web/rest/PayrollCalcConfirmIT.java`

**Interfaces:**
- Consumes: bản nháp (Task 4), `FileStorageService` (lưu file như file nhập: thư mục `payroll/<period>/`), `PayrollVersionSource.TINH_TOAN` (Task 2).
- Produces: phiên bản `source=TINH_TOAN`, `payrollImport=null`, `fileName` `bang-luong-<YYYY-MM>-tinh-toan-v<n>.xlsx`, dòng `PayrollLineRecord` (`excelRow` = thứ tự dòng, `mappedManually=false`, các cột tóm tắt), `rulesSnapshot` (thêm khối thông số đã dùng), `issuesSnapshot`; bản nháp → `DA_XAC_NHAN`. `ConfirmedDTO(period, version)`.

- [ ] **Step 1: Viết test**: confirm tạo phiên bản n+1 nguồn TINH_TOAN với đúng số dòng và THUC_NHAN; file tải về (endpoint tải file phiên bản hiện có) đọc lại đúng số; còn lỗi/chưa acknowledge → 400 `calcnotready`; kỳ đã duyệt → `periodapproved`; confirm lần 2 → `calcdraftclosed`; hai confirm song song (Review Focus 4: hai thread, latch) → đúng một phiên bản; Quản trị duyệt kỳ có phiên bản TINH_TOAN được, phiếu lương của nhân viên đọc được (endpoint phiếu lương hiện có).
- [ ] **Step 2: Chạy, thấy FAIL.** **Step 3: Cài đặt.** **Step 4: Chạy lại** → PASS; chạy IT bảng lương/phiếu lương hiện có (nhóm nhỏ).
- [ ] **Step 5: Commit** — "Xác nhận bản tính lương thành phiên bản của kỳ, kèm file Excel 36 cột".

### Task 6: Giao diện bản tính nháp

**Files:**
- Create: `app/src/main/webapp/app/modules/payroll/calc/calc-draft.tsx` (+ spec), `calc-api.ts` (+ spec)
- Modify: `app/src/main/webapp/app/modules/payroll/period-detail.tsx` (+ spec) — nút "Tính lương" cạnh "Nhập file"; `modules/payroll/index.tsx` (route `tinh-luong/:id`); `entities/employee/detail/detail-api.ts` (`ERROR_TEXT` 5 mã + `DETAIL_FIRST`)

**Interfaces:**
- Consumes: API Task 4–5 (JSON như Interfaces Task 4).
- Produces: route `/bang-luong/tinh-luong/:id`.

- [ ] **Step 1: Viết test**: nút "Tính lương" gọi POST rồi chuyển tới bản nháp; lỗi `payrollattendancenotclosed` hiện câu + link `/bang-luong-cong`/bảng công tháng; bảng: cột tự tính nền xám (class `calc-cell-auto`), cột nhập tay input (class `calc-cell-input`), ô có override class `calc-cell-overridden` + nút "↺"; nhập THUONG_KPI rồi rời ô → PUT `{manual:{THUONG_KPI: n}}` và dòng cập nhật theo trả về; "↺" gửi `{overrides:{FIELD:null}}`; dòng tổng; lỗi/cảnh báo theo dòng; "Xác nhận" disabled khi `canConfirm=false`, cần tích "Đã xem và xác nhận" khi có cảnh báo; xác nhận xong chuyển về chi tiết kỳ; "Tính lại từ hồ sơ" gọi recalculate.
- [ ] **Step 2: Chạy, thấy FAIL** — `cd app && npx vitest run src/main/webapp/app/modules/payroll`. **Step 3: Cài đặt** (bảng cuộn ngang, cột mã/họ tên cố định bên trái; nút ≥ 44px). **Step 4: Chạy lại** → PASS; `npx vitest run` toàn bộ; prettier + eslint.
- [ ] **Step 5: Commit** — "Bảng lương → Tính lương: màn bản tính nháp".

### Task 7: Cài đặt → Bảng lương, ô cam kết 08

**Files:**
- Create: `app/src/main/webapp/app/modules/settings/payroll-settings-tab.tsx` (+ spec) — danh sách bộ thông số + form (ADMIN sửa; `locked` thì chỉ xem) và component `PayrollRules` hiện có (`modules/payroll/rules/payroll-rules.tsx`) bên dưới
- Modify: `settings-page.tsx` (+ spec) — tab `bang-luong` sau `ma-so-van-ban`, roles ADMIN, HR, KE_TOAN, `canEdit` ADMIN; `app/src/main/webapp/app/routes.tsx` (route `cai-dat/*` thêm KE_TOAN; Kế toán chỉ thấy tab Bảng lương — các tab khác giữ roles hiện có); `shared/layout/sidebar/menu-items.ts` (Kế toán thấy mục Cài đặt); `modules/payroll/index.tsx` và `entities/routes.tsx`: tab/route "Mức luật" → `Navigate` `/cai-dat/bang-luong`; form hồ sơ nhân viên (`entities/employee/employee-form.tsx` hoặc nơi có `taxCode`) — ô "Có cam kết 08 (không khấu trừ 10%)" cạnh mã số thuế, chỉ hiện khi loại người là thực tập hoặc HĐ dịch vụ
- Test: specs tương ứng

**Interfaces:**
- Consumes: `/api/payroll/calc-settings` (Task 2), `pitCommitment08` trên hồ sơ (Task 2).

- [ ] **Step 1: Viết test**: tab Bảng lương hiện với ADMIN, HR, KE_TOAN; Kế toán chỉ thấy tab này; HR/KE_TOAN không có nút sửa; form thông số hiện % (nhập 17,5 → gửi 0.175), thêm bộ mới với tháng hiệu lực; bộ `locked` không sửa/xóa; `/bang-luong/muc-luat` và `/danh-muc/muc-luat` chuyển tới `/cai-dat/bang-luong`; ô cam kết 08 chỉ hiện với thực tập/HĐ dịch vụ và gửi `pitCommitment08`.
- [ ] **Step 2: Chạy, thấy FAIL.** **Step 3: Cài đặt.** **Step 4: Chạy lại** → PASS; `npx vitest run` toàn bộ; prettier + eslint.
- [ ] **Step 5: Commit** — "Cài đặt → Bảng lương: thông số tính lương và mức luật; ô cam kết 08 trên hồ sơ".

### Task 8: Tài liệu và kiểm tra toàn bộ

**Files:**
- Modify: `README.md` (mục "Tính lương tự động": điều kiện bảng công đã chốt, các khoản tự tính / nhập tay, thông số ở Cài đặt → Bảng lương, cam kết 08, nhập file vẫn dùng được; bảng tab Cài đặt thêm "Bảng lương")

- [ ] **Step 1: README.**
- [ ] **Step 2: Kiểm tra toàn bộ**: core `test`; app `./mvnw -q verify` đầy đủ (chạy nền, ghi file, cộng tổng từ surefire/failsafe theo thời điểm chạy); `npx vitest run`.
- [ ] **Step 3: Commit** — "Tài liệu: tính lương tự động".
