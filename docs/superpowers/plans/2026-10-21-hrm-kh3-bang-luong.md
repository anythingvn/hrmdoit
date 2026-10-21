# DOIT HRM – Kế hoạch 3: Bảng lương

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kế toán import file bảng lương Excel theo 4 bước. Dòng thiếu hoặc trùng mã được gắn tay vào hồ sơ; người chưa có hồ sơ thì tạo hồ sơ rút gọn. Mỗi kỳ lưu theo phiên bản, Quản trị duyệt hoặc mở lại. Chỉ Kế toán và Quản trị xem bảng lương. Mức luật định chuyển ra bảng cấu hình theo ngày hiệu lực.

**Architecture:**
- Lõi `core/` có thêm `RuleBook` (danh sách mốc luật cùng mốc "đã biết luật đến tháng"). `PayrollValidator` và `NetGrossConverter` nhận `RuleBook` từ bên ngoài; bộ mặc định chỉ dùng cho test và dữ liệu nạp ban đầu.
- App thêm package `vn.doit.hrm.service.payroll`, gồm:
  - mức luật lưu trong cơ sở dữ liệu, kèm cache;
  - bản nháp import (file gốc cộng bảng gắn tay; mỗi lần cần thì đọc lại file bằng lõi);
  - kỳ, phiên bản và dòng lương;
  - duyệt và mở lại.
- Frontend có module `modules/payroll` và một khối trên hồ sơ nhân viên.

**Tech Stack:** JHipster 9.4.0 / Spring Boot 4.1 / Java 21, React 19 + Vite/Vitest + react-bootstrap, PostgreSQL 16 (Testcontainers), Liquibase, Envers, Apache POI (đã có trong `core`).

**Spec:** `docs/superpowers/specs/2026-10-21-hrm-kh3-bang-luong-design.md`; tham chiếu thêm spec đợt 1, §5.3 và §5.6.

## Global Constraints

- Mọi ràng buộc chung của KH1, KH1b và KH2 vẫn áp dụng:
  - giao diện tiếng Việt; ngày `dd/MM/yyyy`; tiền `1.234.567`; múi giờ `Asia/Ho_Chi_Minh`; vùng bấm ≥ 44px;
  - `core/` không phụ thuộc Spring, vẫn biên dịch ở Java 17;
  - kiểm tra quyền ở backend (`@PreAuthorize`) và Quản trị có mọi quyền;
  - changeset Liquibase chỉ thêm mới;
  - lỗi nghiệp vụ ném `EmployeeRuleException(message, key)`; tầng resource đổi sang `BadRequestAlertException`, và `detail` mang câu báo tiếng Việt.
- **Quyền:**
  - Xem, import, gắn hồ sơ, tạo hồ sơ rút gọn, xác nhận: `ROLE_ADMIN`, `ROLE_KE_TOAN`.
  - Duyệt và mở lại kỳ, thêm mốc luật, đổi "đã biết luật đến tháng": chỉ `ROLE_ADMIN`.
  - Xem mức luật: thêm `ROLE_HR`.
  - HR, Ban giám đốc và Nhân viên gọi API bảng lương → 403. Menu Bảng lương và khối Bảng lương trên hồ sơ bị ẩn với các vai trò này.
- Kế toán chỉ **tạo** hồ sơ rút gọn qua màn Kiểm tra: họ tên, loại người, mã; HĐDV tự sinh mã `HDDVn` theo quy tắc đang có trong `EmployeeService`. Kế toán vẫn không sửa hồ sơ có sẵn (các rule `writeRules` trên `/api/employees/**` giữ nguyên).
- Mọi dòng lương phải gắn với một hồ sơ. Không gắn một hồ sơ cho hai dòng trong cùng một lần import.
- Kỳ đã duyệt (`DA_DUYET`): không tải file mới, không xác nhận bản nháp (400 `periodapproved`). Mở lại bắt buộc có lý do.
- File gốc tối đa 20 MB, lưu qua `FileStorageService` trong thư mục `payroll/{yyyy-MM}`.
- **File bảng lương thật không được commit.** Test dùng bản ẩn danh `app/src/test/resources/payroll/bang-luong-mau-an-danh.xlsx`, sinh ở Task 3.
- Kết quả chuẩn với file mẫu, kỳ `2026-06`:
  - 14 dòng, 36/36 cột;
  - 7 lỗi khi chưa gắn: 4 dòng trùng mã (14, 15, 18, 19) và 3 dòng thiếu mã (21, 22, 23);
  - tổng `THUC_NHAN` = 219.821.925, tổng `TONG_CHI_PHI` = 245.709.000.

## Review Focus

1. **Tìm hồ sơ không dấu.** Kế toán gõ "nguyen tuan anh" hoặc "TUAN ANH" vào ô Chọn hồ sơ thì vẫn thấy "Nguyễn Tuấn Anh". Hồ sơ trùng tên hoàn toàn đứng đầu. → Task 4.
2. **Sai loại file.** Tải `.xls`, `.xlsm`, file không phải Excel, hoặc file không có dòng tiêu đề "Mã nhân viên": nhận 400 với câu báo tiếng Việt, không phải 500, và không tạo bản nháp. → Task 3.
3. **Duyệt trùng lúc xác nhận.** Quản trị duyệt kỳ đúng lúc Kế toán xác nhận một bản nháp: không có phiên bản nào được ghi vào kỳ đã duyệt (cả hai cùng khóa dòng kỳ). → Task 5.
4. **Mốc luật thêm sau, cùng tháng.** Mốc thêm sau thay mốc trước. Gợi ý Gross trên hợp đồng và đối chiếu bảng lương đổi theo mốc mới ngay, không cần khởi động lại (cache được xóa khi thêm). → Task 2.
5. **Gắn rồi bỏ gắn.** Kế toán gắn một dòng, bỏ gắn, rồi gắn hồ sơ đó cho dòng khác: được phép. Hồ sơ đã được dòng khác dùng (qua mã hoặc gắn tay): 400 `employeemappedtwice`. → Task 4.

---

### Task 1: Lõi nhận mức luật từ ngoài (`RuleBook`)

**Files:**
- Create: `core/src/main/java/vn/doit/hr/payroll/RuleBook.java`
- Modify: `core/src/main/java/vn/doit/hr/payroll/PayrollRules.java` (6 mốc chuyển sang `RuleBook.defaults()`; giữ `forPeriod` và `forPeriodOrLatest` dạng static, ủy quyền cho `RuleBook.defaults()` để mã cũ và test cũ vẫn chạy), `PayrollValidator.java`, `NetGrossConverter.java`
- Test: `core/src/test/java/vn/doit/hr/payroll/RuleBookTest.java`; các test lõi hiện có phải vẫn đạt.

**Interfaces:**
- Produces:
  - `public record RuleBook(List<RuleBook.Milestone> milestones, YearMonth knownThrough)`, với `public record Milestone(YearMonth from, PayrollRules rules)`.
    - Hàm khởi tạo sắp `milestones` theo `from` (ổn định). Nếu nhiều mốc cùng `from` thì **mốc đứng sau trong danh sách đầu vào thắng**.
  - `static RuleBook defaults()`: 6 mốc hiện có, `knownThrough = 2026-12`.
  - `Optional<PayrollRules> forPeriod(YearMonth)` và `Optional<PayrollRules.RulesLookup> forPeriodOrLatest(YearMonth)`: cùng ngữ nghĩa với bản static hiện nay.
  - `new PayrollValidator(RuleBook)`. Hàm khởi tạo không tham số dùng `defaults()`.
  - `NetGrossConverter.suggestGross(BigDecimal net, int dependants, YearMonth period, RuleBook book)` và `suggestGrossOrEstimate(..., RuleBook book)`. Các overload cũ dùng `defaults()`.

- [ ] **Step 1: Viết test** `RuleBookTest`:
  - `defaultsMatchCurrentLaw`: `defaults().forPeriod(2026-06)` có `personalDeduction` = 15.500.000 và 5 bậc thuế; `forPeriod(2025-08)` có 11.000.000; `forPeriod(2027-01)` rỗng; `forPeriodOrLatest(2027-01).estimated()` = true.
  - `laterSameMonthWins`: hai mốc cùng `2026-01`, mốc thứ hai có giảm trừ 16.000.000 → `forPeriod(2026-03)` trả 16.000.000.
  - `validatorUsesInjectedBook`: thêm mốc `2027-01` với giảm trừ 20.000.000 và `knownThrough = 2027-12`. Kiểm tra một dòng lương kỳ `2027-02`, giảm trừ bản thân 15.500.000, bằng `new PayrollValidator(book)` → có WARNING "khác mức luật định 20.000.000". Cùng dòng đó dùng `new PayrollValidator()` → có WARNING "Chưa khai báo mức luật định".
  - `converterUsesInjectedBook`: `suggestGross(net 20.000.000, 0, 2027-02, book)` khác `suggestGross(..., 2026-06)` theo hướng giảm trừ cao hơn.
- [ ] **Step 2:** Chạy `mvn -pl core test`. Expected: FAIL (biên dịch: chưa có `RuleBook`).
- [ ] **Step 3:** Cài đặt. Javadoc nguồn văn bản luật chuyển theo dữ liệu sang `RuleBook.defaults()`.
- [ ] **Step 4:** Chạy `mvn -pl core test`. Expected: PASS, gồm cả `PayrollRulesTest`, `PayrollRulesHistoryTest`, `NetGrossConverterTest`, `PayrollImportTest`.
- [ ] **Step 5:** Commit `"Lõi: mức luật định nhận từ ngoài (RuleBook)"`.

### Task 2: Mức luật định trong cơ sở dữ liệu

**Files:**
- Create:
  - changelog `20261022000100_payroll_rules.xml`, gồm:
    - `payroll_rule_milestone`: `id`, `effective_from` varchar(7) dạng `yyyy-MM`, `personal_deduction`, `dependant_deduction`, `social_insurance_cap`, `unemployment_insurance_cap` (numeric(15,0)), `legal_basis` varchar(500), `created_by`, `created_at`;
    - `payroll_rule_bracket`: `id`, `milestone_id` FK, `position` int, `up_to` numeric(15,0) nullable, `rate` numeric(5,4);
    - `payroll_rule_setting`: một dòng, `id` = 1, `known_through` varchar(7);
    - các bảng `_aud`;
    - changeset nạp 6 mốc và các bậc thuế đúng như `RuleBook.defaults()` (`created_by` = `system`), `known_through` = `2026-12`.
  - `domain/PayrollRuleMilestone.java`, `PayrollRuleBracket.java`, `PayrollRuleSetting.java`, các repository tương ứng.
  - `service/payroll/RuleBookService.java`, `service/payroll/dto/RuleMilestoneDTO.java`, `web/rest/PayrollRuleResource.java`.
  - frontend `modules/payroll/rules/payroll-rules.tsx` (+ spec).
- Modify:
  - `EmploymentContractService`, `SalaryChangeService`, `SalaryToolsResource`: dùng `ruleBookService.current()`.
  - `entities/routes.tsx`: thêm tab "Mức luật định" `/danh-muc/muc-luat` cho Quản trị và HR. Route `/bang-luong/muc-luat` được thêm ở Task 6.
  - README, mục "Cập nhật luật hằng năm": thay hướng dẫn sửa code bằng hướng dẫn thêm mốc trên màn hình.
- Test: `RuleBookServiceIT`, `PayrollRuleResourceIT`, Vitest.

**Interfaces:**
- Produces:
  - `RuleBook RuleBookService.current()`: cache trong bộ nhớ, xóa cache khi thêm mốc hoặc đổi `knownThrough`.
  - `RuleMilestoneDTO RuleBookService.add(RuleMilestoneDTO)`.
  - `void RuleBookService.setKnownThrough(YearMonth)`.
  - `record RuleMilestoneDTO(Long id, String effectiveFrom, BigDecimal personalDeduction, BigDecimal dependantDeduction, BigDecimal socialInsuranceCap, BigDecimal unemploymentInsuranceCap, List<BracketDTO> brackets, String legalBasis, String createdBy, Instant createdAt)`, với `record BracketDTO(BigDecimal upTo, BigDecimal rate)`.
  - `GET /api/payroll/rules` (ADMIN, KE_TOAN, HR) → `{knownThrough, milestones[]}`, mốc mới nhất đứng trước.
  - `POST /api/payroll/rules` (ADMIN) → 201.
  - `PUT /api/payroll/rules/known-through` `{knownThrough: "2027-12"}` (ADMIN).
  - Dữ liệu sai → 400 `rulesinvalid`, `detail` nêu lý do. Dữ liệu sai gồm:
    - `effectiveFrom` không đúng dạng `yyyy-MM`;
    - số tiền ≤ 0;
    - bậc thuế không tăng dần, hoặc bậc cuối không phải "không giới hạn" (`upTo` = null);
    - `rate` ngoài khoảng (0, 1].

- [ ] **Step 1: Viết test.**
  - `seededBookEqualsDefaults`: với cơ sở dữ liệu mới nạp, `current()` cho mọi kỳ từ 2020-07 đến 2026-12 trả đúng các giá trị như `RuleBook.defaults()`.
  - `adminAddsMilestone` (Review Focus 4):
    - thêm mốc 2026-01 với giảm trừ 16.000.000 → `current().forPeriod(2026-06)` trả 16.000.000 mà không cần khởi động lại;
    - `GET /api/salary-tools/gross-suggestion` (endpoint hiện có) cho một kỳ năm 2026 đổi theo mốc mới.
  - `invalidMilestone`: các trường hợp dữ liệu sai ở trên → 400 `rulesinvalid`.
  - `knownThrough`: đặt `2027-06` → `forPeriod(2027-03)` có giá trị (theo mốc mới nhất).
  - Quyền:
    - HR và KE_TOAN gọi `GET` → 200;
    - HR và KE_TOAN gọi `POST` → 403;
    - BGD gọi `GET` → 403.
  - Vitest: bảng mốc hiển thị tiền dạng `15.500.000` và thuế suất dạng `5%`. Chỉ Quản trị thấy form "Thêm mốc". Thêm mốc thì gọi `POST`; lỗi 400 hiện `detail`.
- [ ] **Step 2:** Chạy các IT và spec trên. Expected: FAIL.
- [ ] **Step 3:** Cài đặt. Seed bằng `<insert>` trong changelog, không đọc từ code lõi. Test `seededBookEqualsDefaults` giữ seed và lõi khớp nhau.
- [ ] **Step 4:** Chạy toàn bộ backend (`mvn -pl app -am verify`) và `npm test`. Expected: PASS. Các IT gợi ý Gross trên hợp đồng và điều chỉnh lương hiện có phải vẫn đạt.
- [ ] **Step 5:** Commit `"Mức luật định theo ngày hiệu lực trong cơ sở dữ liệu"`.

### Task 3: Bản nháp import: tải file, đọc, kiểm tra, báo cáo lỗi

**Files:**
- Create:
  - **file test ẩn danh** `app/src/test/resources/payroll/bang-luong-mau-an-danh.xlsx`. Sinh bằng một script **không commit** (đặt ở scratchpad), đọc từ `/Users/anythingvn/Desktop/DO it - Bang luong thang 06_2026 them luon.xlsx`:
    - chỉ giữ sheet "Bảng lương";
    - thay họ tên ở các dòng 10–23 bằng tên giả (bảng ở cuối task);
    - giữ nguyên mã, chức danh, mọi số liệu và công thức (giá trị đã tính);
    - xóa phần tác giả trong thuộc tính tài liệu.
    - Sau khi sinh, kiểm tra lại bằng lõi: 14 dòng, cùng 2 tổng, cùng 7 lỗi.
  - changelog `20261022000200_payroll_import.xml`:
    - `payroll_import`: `id`, `period` varchar(7), `file_name`, `storage_path`, `status` varchar(20), `warnings_acknowledged` boolean, `created_by`, `created_at`;
    - `payroll_import_mapping`: `id`, `import_id` FK, `excel_row` int, `employee_id` FK, `mapped_by`, `mapped_at`; unique (`import_id`, `excel_row`);
    - các bảng `_aud`.
  - `domain/PayrollImport.java`, `PayrollImportMapping.java`, enum `PayrollImportStatus { DANG_LAM, DA_XAC_NHAN, HUY }`, các repository.
  - `service/payroll/PayrollImportService.java`, `service/payroll/PayrollCheck.java`, `service/payroll/IssueReportWriter.java`, `service/payroll/dto/ImportCheckDTO.java`, `web/rest/PayrollImportResource.java`.
- Test: `PayrollCheckTest` (unit, dựng `PayrollCheck` với danh bạ giả), `PayrollImportResourceIT`.

**Interfaces:**
- Consumes: `RuleBookService.current()` (Task 2), `PoiSheetGrid`, `PayrollSheetParser`, `PayrollValidator(RuleBook)` (Task 1), `FileStorageService`.
- Produces:
  - `PayrollCheck.Result PayrollCheck.run(ParsedPayroll parsed, YearMonth period, Map<Integer, Employee> mappings, Function<String, Optional<Employee>> byCode, RuleBook book)`. Thuật toán (vì đây là chỗ dễ làm sai):
    1. Với dòng có gắn tay, thay `employeeCode` của `PayrollLine` bằng mã của hồ sơ đã gắn.
    2. Chạy `new PayrollValidator(book).validate(...)` với danh bạ là `byCode`. Nhờ vậy, dòng trùng mã hay thiếu mã đã gắn tay sẽ hết lỗi; việc so tên và đối chiếu luật dựa trên hồ sơ đã gắn.
    3. `Result` gồm:
       - `List<RowDTO> rows`: excelRow, mã và tên trong file, chức danh, `employeeId`/`employeeCode`/`employeeName` đã giải được hoặc null, `mappedManually`;
       - `List<IssueDTO> issues`;
       - `errorCount`, `warningCount`;
       - `Map<PayrollField, Integer> columns`;
       - `List<PayrollField> missingRequired`.
  - `ImportCheckDTO` gồm `id`, `period`, `fileName`, `status`, `warningsAcknowledged`, các trường của `Result`, và `canConfirm` (= không lỗi, và không có cảnh báo hoặc đã xác nhận cảnh báo).
  - API (ADMIN, KE_TOAN):
    - `POST /api/payroll/imports` (multipart `file`, `period` dạng `yyyy-MM`) → 201 `ImportCheckDTO`;
    - `GET /api/payroll/imports?status=DANG_LAM` → danh sách nháp, mới nhất trước;
    - `GET /api/payroll/imports/{id}` → `ImportCheckDTO` (đọc lại file gốc mỗi lần);
    - `POST /api/payroll/imports/{id}/acknowledge-warnings`;
    - `DELETE /api/payroll/imports/{id}` → trạng thái `HUY`;
    - `GET /api/payroll/imports/{id}/issues.xlsx`: báo cáo lỗi, các cột "Dòng", "Mã", "Họ tên", "Mức độ" ("Lỗi"/"Cảnh báo"), "Nội dung".
  - Lỗi khi tải file (không tạo bản nháp, không lưu file):
    - không phải `.xlsx` hoặc nội dung không phải zip OOXML → 400 `payrollfilenotxlsx`;
    - lõi trả lỗi cấp file (không có tiêu đề hoặc cột "Họ và tên") → 400 `payrollfilelayout`, `detail` lấy từ câu báo của lõi;
    - `period` sai dạng → 400 `periodinvalid`;
    - kỳ đã duyệt → 400 `periodapproved`. Trước Task 5 chưa có bảng kỳ: kiểm tra này được thêm ở Task 5.

- [ ] **Step 1: Viết test.**
  - `PayrollCheckTest`, trên file ẩn danh, danh bạ gồm các mã ở bảng dưới:
    - chưa gắn → `errorCount` = 7, gồm các dòng 14, 15, 18, 19, 21, 22, 23;
    - gắn dòng 18 cho hồ sơ `DOIT2515` → lỗi trùng ở dòng 14 và 18 biến mất, `errorCount` = 5;
    - đúng 3 cảnh báo: sai kỳ trên tiêu đề, và 2 dòng không đóng BH (dòng 12, 16).
  - `PayrollImportResourceIT`:
    - tải file ẩn danh với kỳ `2026-06` → 201, `rows.length` = 14, `errorCount` = 7, `columns` có 36 mục, `missingRequired` rỗng, `canConfirm` = false;
    - `GET` lại cho cùng kết quả;
    - báo cáo lỗi tải về là `.xlsx` có 1 dòng tiêu đề và 10 dòng (7 lỗi + 3 cảnh báo);
    - Review Focus 2: tải `.xls` (đuôi), file văn bản đặt đuôi `.xlsx`, và file `.xlsx` không có tiêu đề "Mã nhân viên" (tạo bằng POI) → lần lượt 400 `payrollfilenotxlsx`, `payrollfilenotxlsx`, `payrollfilelayout`; không có `payroll_import` nào được tạo;
    - HR, BGD → 403.
- [ ] **Step 2:** Chạy test. Expected: FAIL.
- [ ] **Step 3:** Cài đặt.
- [ ] **Step 4:** Chạy test. Expected: PASS.
- [ ] **Step 5:** Commit `"Bảng lương: bản nháp import, đọc và kiểm tra file, báo cáo lỗi"` (gồm cả file ẩn danh).

**Dữ liệu test: dòng Excel → (mã trong file, tên giả, hồ sơ dùng trong IT).**

| Dòng | Mã trong file | Tên giả | Hồ sơ dùng trong test |
|---|---|---|---|
| 10 | DOIT2401 | Phạm Văn An | DOIT2401 |
| 11 | DOIT2402 | Lê Văn Bình | DOIT2402 |
| 12 | DOIT2403 | Hoàng Văn Cường | DOIT2403 |
| 13 | DOIT2505 | Vũ Đức Dũng | DOIT2505 |
| 14 | DOIT2508 | Đặng Minh Giang | DOIT2508 |
| 15 | DOIT2513 | Bùi Thị Hà | DOIT2513 |
| 16 | DOIT2509 | Đỗ Quang Khải | DOIT2509 |
| 17 | DOIT2507 | Ngô Thị Lan | DOIT2507 |
| 18 | DOIT2508 | Dương Tuấn Minh | DOIT2515 (gắn tay) |
| 19 | DOIT2513 | Lý Thu Ngân | DOIT2516 (gắn tay) |
| 20 | DOIT2514 | Trịnh Hồng Oanh | DOIT2514 |
| 21 | (trống), Thực tập | Mai Văn Phúc | tạo rút gọn `TT2601` (Task 4) |
| 22 | (trống), HĐDV | Tạ Văn Quý | hồ sơ HĐDV có sẵn (gắn tay) |
| 23 | (trống), HĐDV | Hồ Thị Sen | tạo rút gọn HĐDV → `HDDVn` (Task 4) |

### Task 4: Gắn hồ sơ tay, gợi ý theo tên, tạo hồ sơ rút gọn

**Files:**
- Modify: `PayrollImportService`, `PayrollImportResource`, `EmployeeRepository` (thêm `Optional<Employee> findOneByCode(String)` và một truy vấn tìm theo `searchText`).
- Create: `service/payroll/dto/CandidateDTO.java`, `service/payroll/dto/NewEmployeeDTO.java`.
- Test: `PayrollMappingIT`.

**Interfaces:**
- Consumes: `PayrollImportService`, `ImportCheckDTO` (Task 3); `EmployeeService.save(EmployeeDTO)` (đã có; tự sinh mã HĐDV và kiểm trùng mã).
- Produces (ADMIN, KE_TOAN; bản nháp phải ở trạng thái `DANG_LAM`, nếu không → 400 `importclosed`):
  - `PUT /api/payroll/imports/{id}/rows/{excelRow}/employee` `{employeeId}` → `ImportCheckDTO`.
    - Hồ sơ đã được dòng khác dùng (gắn tay, hoặc tự khớp theo mã ở dòng không gắn tay) → 400 `employeemappedtwice`, `detail` nêu dòng kia.
    - `excelRow` không có trong file → 404.
  - `DELETE /api/payroll/imports/{id}/rows/{excelRow}/employee` → bỏ gắn.
  - `GET /api/payroll/imports/{id}/rows/{excelRow}/candidates?q=` → `List<CandidateDTO(Long id, String code, String fullName, String position, PersonType personType, EmploymentStatus status, boolean exactName)>`, tối đa 10.
    - Không có `q` thì dùng họ tên trong file.
    - So khớp không dấu, không phân biệt hoa thường, qua `VietnameseText.fold`, trên `searchText`.
    - Thứ tự: khớp tên hoàn toàn trước, rồi các hồ sơ chứa mọi từ của `q`, xếp theo họ tên.
  - `POST /api/payroll/imports/{id}/rows/{excelRow}/new-employee` `{fullName, personType, code}` → `ImportCheckDTO`.
    - Tạo hồ sơ qua `EmployeeService.save` với `status = DANG_LAM_VIEC` và `dependants = 0`; `code` bỏ qua khi `DICH_VU`; sau đó gắn luôn vào dòng.
    - Lỗi của `EmployeeService` (`codenull`, `codeexists`) trả nguyên khóa.
    - Kế toán gọi endpoint này được, nhưng `PUT`/`PATCH /api/employees/{id}` vẫn 403 với Kế toán.

- [ ] **Step 1: Viết test** `PayrollMappingIT`. Dựng hồ sơ theo bảng của Task 3, trong đó có một hồ sơ HĐDV cho "Tạ Văn Quý". Các test:
  - Gắn dòng 18 → `DOIT2515`, dòng 19 → `DOIT2516`, dòng 22 → HĐDV có sẵn. Tạo rút gọn dòng 21 (`NHAN_VIEN`, mã `TT2601`) và dòng 23 (`DICH_VU`; mã trả về bắt đầu bằng `HDDV`). Kết quả: `errorCount` = 0. `canConfirm` = false vì còn 3 cảnh báo; sau khi xác nhận cảnh báo thì `canConfirm` = true.
  - Review Focus 5:
    - gắn dòng 18 vào `DOIT2508` (đã tự khớp ở dòng 14) → 400 `employeemappedtwice`;
    - gắn dòng 18 → `DOIT2515`, bỏ gắn, rồi gắn dòng 19 → `DOIT2515` → 200.
  - Review Focus 1: candidates của dòng 18 với `q=duong tuan minh` và `q=TUAN MINH` đều có `DOIT2515`. Không có `q` thì `DOIT2515` đứng đầu với `exactName` = true.
  - Kế toán tạo rút gọn → 200, nhưng `PATCH /api/employees/{id}` hồ sơ vừa tạo → 403. Tạo rút gọn với mã trùng → 400 `codeexists`.
  - Bản nháp `HUY` → `PUT` gắn → 400 `importclosed`. HR → 403.
- [ ] **Step 2:** Chạy. Expected: FAIL.
- [ ] **Step 3:** Cài đặt.
- [ ] **Step 4:** Chạy `PayrollMappingIT` cùng `PayrollImportResourceIT` và `EmployeePermissionIT`. Expected: PASS.
- [ ] **Step 5:** Commit `"Bảng lương: gắn hồ sơ tay, gợi ý theo tên, tạo hồ sơ rút gọn"`.

### Task 5: Xác nhận, kỳ lương, phiên bản, duyệt và mở lại

**Files:**
- Create:
  - changelog `20261022000300_payroll_period.xml`, gồm:
    - `payroll_period`: `id` IDENTITY, `period` varchar(7) unique, `status`, `approved_version` int, `approved_by`, `approved_at`;
    - `payroll_period_event`: `id`, `period_id` FK, `action` varchar(10), `actor`, `at`, `reason` varchar(500);
    - `payroll_version`: `id`, `period_id` FK, `version` int, `import_id` FK, `file_name`, `storage_path`, `imported_by`, `imported_at`; unique (`period_id`, `version`);
    - `payroll_line`: `id`, `version_id` FK, `employee_id` FK, `excel_row`, `file_code`, `file_name`, `position`, `mapped_manually`, `amounts` text (JSON `{FIELD: "số"}`), `thuc_nhan`, `tong_chi_phi`, `thue_tncn`, `tong_bh_nld` (numeric(15,0));
    - các bảng `_aud`.
  - domain `PayrollPeriod`, `PayrollPeriodEvent`, `PayrollVersion`, `PayrollLineRecord` (tránh trùng tên với `PayrollLine` của lõi); enum `PayrollPeriodStatus { NHAP, DA_DUYET }` và `PayrollPeriodAction { DUYET, MO_LAI }`; các repository (`PayrollPeriodRepository.insertIfMissing` + `findForUpdate` theo cách của `DocumentSequenceRepository`).
  - `service/payroll/PayrollPeriodService.java`, `web/rest/PayrollPeriodResource.java`.
- Modify: `PayrollImportService` (thêm `confirm`; chặn tải file khi kỳ đã duyệt).
- Test: `PayrollPeriodIT`.

**Interfaces:**
- Consumes: `PayrollImportService`, `PayrollCheck` (Task 3–4).
- Produces:
  - `POST /api/payroll/imports/{id}/confirm` (ADMIN, KE_TOAN) → 201 `{period, version}`.
    - `canConfirm` = false → 400 `importnotready`.
    - Kỳ đã duyệt → 400 `periodapproved`.
    - Trong một transaction:
      1. khóa dòng kỳ (tạo nếu chưa có, trạng thái `NHAP`);
      2. tạo phiên bản mới = phiên bản lớn nhất + 1;
      3. ghi các `payroll_line` từ kết quả kiểm tra (`employee_id` = hồ sơ đã giải, `mapped_manually`);
      4. đánh dấu bản nháp `DA_XAC_NHAN`.
  - `POST /api/payroll/periods/{period}/approve` (ADMIN): khóa dòng kỳ; `approved_version` = phiên bản mới nhất; ghi event `DUYET`.
    - Chưa có phiên bản → 400 `noversion`.
    - Đã duyệt → 400 `alreadyapproved`.
  - `POST /api/payroll/periods/{period}/reopen` (ADMIN) `{reason}`: trạng thái về `NHAP`, giữ `approved_version` cũ trong lịch sử event, ghi event `MO_LAI` kèm lý do.
    - Lý do trống → 400 `reasonrequired`.
    - Chưa duyệt → 400 `notapproved`.
  - `PayrollPeriodService.officialVersion(PayrollPeriod)`: `approved_version` nếu đang `DA_DUYET`, ngược lại là phiên bản mới nhất. Task 6 dùng hàm này.

- [ ] **Step 1: Viết test** `PayrollPeriodIT`, dùng lại phần dựng dữ liệu của `PayrollMappingIT` (trích ra lớp hỗ trợ test `PayrollTestData`).
  - Xác nhận → kỳ `2026-06` phiên bản 1, `NHAP`, 14 dòng. Tổng `thuc_nhan` = 219.821.925, tổng `tong_chi_phi` = 245.709.000. Dòng Excel 18 có `employee_id` của `DOIT2515`, `mapped_manually` = true, `file_code` = `DOIT2508`.
  - Import lại và xác nhận → phiên bản 2. Phiên bản 1 vẫn còn đủ 14 dòng.
  - Kế toán duyệt → 403. Quản trị duyệt → `DA_DUYET`, `approved_version` = 2.
  - Sau khi duyệt:
    - tải file mới cho `2026-06` → 400 `periodapproved`;
    - xác nhận một bản nháp tạo trước khi duyệt → 400 `periodapproved`.
  - Mở lại không lý do → 400 `reasonrequired`. Mở lại có lý do → `NHAP`, có 2 event. Sau đó import được phiên bản 3.
  - Bản nháp còn lỗi → xác nhận → 400 `importnotready`.
  - Review Focus 3: hai luồng song song, một `approve` và một `confirm` (bản nháp đã sẵn sàng) trên cùng kỳ đang có phiên bản 1. Kết quả hợp lệ chỉ có hai khả năng:
    - `approve` thắng: `approved_version` = 1, `confirm` nhận 400 `periodapproved`;
    - `confirm` thắng: phiên bản 2 được tạo, `approved_version` = 2.

    Không bao giờ có phiên bản 2 được tạo mà `approved_version` = 1.
- [ ] **Step 2:** Chạy. Expected: FAIL.
- [ ] **Step 3:** Cài đặt.
- [ ] **Step 4:** Chạy `PayrollPeriodIT` và các IT bảng lương của Task 3–4. Expected: PASS.
- [ ] **Step 5:** Commit `"Bảng lương: xác nhận theo phiên bản, duyệt và mở lại kỳ"`.

### Task 6: Xem bảng lương và giao diện

**Files:**
- Modify: `PayrollPeriodResource`; `web/rest/EmployeeRecordsResource.java` hoặc một resource mới cho `/api/employees/{id}/payroll`.
- Create (frontend):
  - `modules/payroll/index.tsx`: các route `/bang-luong` (danh sách kỳ và nháp đang dở), `/bang-luong/import/:id` (4 bước), `/bang-luong/:period` (chi tiết kỳ), `/bang-luong/muc-luat` (dùng lại component Task 2). Bọc bằng `PrivateRoute` cho ADMIN và KE_TOAN.
  - `period-list.tsx`, `import-wizard.tsx`, `row-mapping.tsx` (ô Chọn hồ sơ kèm danh sách gợi ý và hộp "Tạo hồ sơ rút gọn"), `period-detail.tsx`, `payroll-api.ts`.
  - `entities/employee/detail/payroll-section.tsx`.
- Modify: `routes.tsx`; `menu-items.ts` (+ spec): thêm mục `bang-luong` "Bảng lương" `/bang-luong` cho ADMIN và KE_TOAN, đặt sau "Hợp đồng & mẫu"; `employee-detail.tsx`: khối Bảng lương ở tab "Hợp đồng & lương", chỉ ADMIN và KE_TOAN.
- Test: `PayrollViewIT`, Vitest cho từng component.

**Interfaces:**
- Consumes: Task 3–5.
- Produces (ADMIN, KE_TOAN):
  - `GET /api/payroll/periods` → `[{period, status, officialVersion, latestVersion, headcount, totalNet, totalCost, approvedBy, approvedAt}]`, kỳ mới nhất đứng trước.
  - `GET /api/payroll/periods/{period}` → thông tin kỳ cộng `versions[] {version, importedBy, importedAt, fileName, headcount, totalNet, totalCost}` và `events[] {action, actor, at, reason}`.
  - `GET /api/payroll/periods/{period}/versions/{v}/lines` → `[{excelRow, employeeId, employeeCode, employeeName, fileCode, fileName, position, mappedManually, amounts: {FIELD: số}, thucNhan, tongChiPhi, thueTncn, tongBhNld}]`.
  - `GET /api/payroll/periods/{period}/versions/{v}/file` → file Excel gốc.
  - `GET /api/employees/{id}/payroll` → `[{period, status, version, thucNhan, tongChiPhi, thueTncn, tongBhNld}]` theo `officialVersion` của mỗi kỳ.

- [ ] **Step 1: Viết test.**
  - `PayrollViewIT` (sau một lần xác nhận):
    - danh sách có `2026-06` với `headcount` = 14, `totalNet` = 219821925, `totalCost` = 245709000;
    - các dòng có đủ 36 khóa trong `amounts`;
    - file gốc tải về giống hệt byte với file đã tải lên;
    - `/api/employees/{id DOIT2515}/payroll` trả 1 kỳ;
    - HR, BGD, NHAN_VIEN gọi mọi endpoint trên → 403.
  - Vitest:
    - `menuFor(['ROLE_KE_TOAN'])` có `bang-luong`; `menuFor(['ROLE_HR'])` không có;
    - import-wizard:
      - bước 1 gửi multipart kèm `period`;
      - bước 2 liệt kê cột và báo cột bắt buộc thiếu;
      - bước 3 hiện lỗi theo dòng, chỉ cho "Tiếp tục" khi `canConfirm`, có ô "Đã xem và xác nhận" khi còn cảnh báo;
      - bước 4 gọi `confirm` rồi chuyển sang trang chi tiết kỳ;
    - row-mapping:
      - gõ tìm thì gọi `candidates?q=`;
      - chọn thì gọi `PUT .../employee`;
      - lỗi `employeemappedtwice` hiện `detail`;
      - "Tạo hồ sơ rút gọn": chọn "Hợp đồng dịch vụ" thì ẩn ô mã;
    - period-detail:
      - Quản trị thấy nút "Duyệt kỳ" hoặc "Mở lại" (mở lại hỏi lý do);
      - Kế toán không thấy các nút này;
      - tiền định dạng `219.821.925`;
    - payroll-section: không render và không gọi API khi vai trò là HR.
- [ ] **Step 2:** Chạy. Expected: FAIL.
- [ ] **Step 3:** Cài đặt. Bảng 36 khoản dùng nhãn của `PayrollField`: tiêu đề + tiêu đề phụ, lấy qua `GET /api/payroll/fields`, trả `[{field, label}]` từ enum lõi. Bảng cuộn ngang trên màn hẹp. Hai cột "Họ tên" và "Thực nhận" luôn hiện.
- [ ] **Step 4:** Chạy toàn bộ backend, `npm test`, `tsc`, `lint`. Expected: PASS.
- [ ] **Step 5:** Commit `"Bảng lương: danh sách kỳ, chi tiết, import 4 bước, khối trên hồ sơ"`.

## Kiểm tra cuối kế hoạch

- [ ] Backend `verify`, `npm test`, `tsc`, `lint`, `docker compose build` đều đạt.
- [ ] Chạy end-to-end trên stack `docker compose` tạm (cổng riêng, gỡ bỏ sau khi kiểm), dùng **file thật** của người dùng (không lưu vào repo):
  - Quản trị tạo các hồ sơ có sẵn;
  - Kế toán import kỳ 06/2026 → gắn 4 dòng trùng mã, tạo 3 hồ sơ rút gọn → xác nhận cảnh báo → xác nhận;
  - kiểm tra tổng 219.821.925 và 245.709.000;
  - Quản trị duyệt; import lại bị chặn; mở lại; import phiên bản 2;
  - HR không thấy menu Bảng lương.
- [ ] README: mục "Bảng lương". Nội dung gồm 4 bước, gắn hồ sơ tay, hồ sơ rút gọn, duyệt và mở lại, quyền xem; mục mức luật cập nhật theo Task 2.
