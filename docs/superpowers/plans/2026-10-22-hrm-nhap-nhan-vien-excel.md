# Nhập danh sách nhân viên từ Excel – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Quản trị/HR tải file mẫu, điền danh sách nhân viên, tải lên, xem lỗi/cảnh báo từng dòng và tạo toàn bộ hồ sơ trong một lần lưu.

**Architecture:** Lõi `core` (gói mới `vn.doit.hr.employee`, không Spring) dựng file mẫu và đọc sheet thành các dòng đã định kiểu, cả hai từ enum `EmployeeField`. App kiểm tra nghiệp vụ bằng một lớp thuần (`EmployeeImportCheck`) nhận dữ liệu tra cứu qua interface, rồi `EmployeeImportService` dùng chung lớp đó cho cả `/check` lẫn lưu (lưu = khóa cấp mã → kiểm tra lại → tạo phòng ban → tạo hồ sơ → gán quản lý, một giao dịch). Không có bản nháp, không lưu file. Giao diện là wizard 3 bước theo khuôn `modules/payroll/import-wizard.tsx`.

**Tech Stack:** Java 21 / Spring Boot 4 (app), `core` Java 17 không Spring, Apache POI, PostgreSQL + Envers, React 19 + Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-10-22-hrm-nhap-nhan-vien-excel-design.md`

## Global Constraints

- Mỗi dòng tạo **một hồ sơ mới**; không cập nhật hồ sơ có sẵn, không HĐ/lương, không tài khoản (`user` luôn null), không `WorkAssignment`; trạng thái luôn `DANG_LAM_VIEC`.
- Bắt buộc: **Họ và tên**, **Số CCCD**. Trùng CCCD hoặc mã (với hệ thống, kể cả người đã nghỉ, hoặc giữa các dòng) là **lỗi**.
- Còn lỗi → không tạo gì. Có cảnh báo → phải `warningsAcknowledged=true`. Lưu trong **một giao dịch**.
- Mã: trống → `EmployeeCodes.next(prefix, năm vào làm hoặc năm nay, existing)`; có ghi → giữ (sau `EmployeeCodes.normalize`); HĐ dịch vụ luôn `HDDVn` tự sinh.
- Chỉ `.xlsx` (đuôi + chữ ký `PK`), ≤ 20 MB, ≤ 2.000 dòng dữ liệu. Ngày: ô ngày Excel hoặc chữ `d/M/yyyy`.
- Quyền: mọi endpoint `/api/employees/import/**` = `ROLE_ADMIN`, `ROLE_HR`.
- File chỉ đọc trong bộ nhớ; không lưu đĩa, không cache; log không chứa nội dung dòng. Kết quả kiểm tra không trả MST, số tài khoản, lương đóng BHXH.
- Lỗi nghiệp vụ: `EmployeeRuleException(message, key)` → 400 `error.<key>` qua `ExceptionTranslator`.
- Dữ liệu kiểm thử dựng bằng code, tên và số giả; không có file Excel thật trong repo.
- Chữ trên giao diện và thông báo: tiếng Việt, đúng như spec.
- Route giao diện theo quy ước hiện có của module hồ sơ: `/nhan-vien/nhap-excel` (spec ghi `/employee/import`; danh sách nhân viên đang ở `/nhan-vien`).

## Review Focus

1. **Mã ghi tay ở dòng sau trùng với mã tự gán của dòng trước** (vd dòng 2 trống mã, dòng 5 ghi `DOIT2601`) → không được cấp `DOIT2601` cho dòng 2; mã dự kiến và mã thật đều tính cả mã ghi tay của *mọi* dòng trong file. Test ở Task 3 (`provisionalCodesSkipManualCodesLaterInFile`) và Task 4 (`saveUsesSamePlanAsCheck`).
2. **CCCD gõ thành số trong Excel** (mất số 0, hoặc hiển thị dạng `1.23E+11`) → 11 chữ số thì thêm số 0 + cảnh báo; số khác thì đọc ra chữ số nguyên, không có `.0` hay dấu mũ. Test ở Task 1 (`numericIdNumberReadAsDigits`).
3. **Người khác tạo hồ sơ giữa lúc kiểm tra và lúc lưu** (cùng CCCD hoặc lấy mất mã) → lưu kiểm tra lại sau khi khóa cấp mã, trả 400 kèm kết quả mới, không tạo gì. Test ở Task 4 (`conflictAfterCheckRollsBack`).
4. **Ô có khoảng trắng hoặc chữ thường** (" nữ ", "thực tập", "  P. Kỹ thuật ") → nhận đúng lựa chọn/phòng ban, không báo lỗi giả. Test ở Task 1 (`choicesIgnoreCaseAccentAndSpaces`) và Task 3 (`departmentMatchIgnoresAccentAndCase`).
5. **Một dòng chỉ có ô Ghi chú hoặc chỉ STT** (người dùng để dư dòng) → không bị bỏ qua lặng lẽ: báo lỗi thiếu Họ tên/CCCD ở dòng đó; chỉ dòng trống hoàn toàn mới bỏ qua. Test ở Task 1 (`partiallyFilledRowIsKept`).

---

### Task 1: Lõi – cột và bộ đọc sheet

**Files:**
- Create: `core/src/main/java/vn/doit/hr/employee/EmployeeField.java`, `EmployeeRow.java`, `EmployeeIssue.java`, `ParsedEmployees.java`, `EmployeeSheetParser.java`, `Fold.java` (cùng gói `vn.doit.hr.employee`)
- Test: `core/src/test/java/vn/doit/hr/employee/EmployeeSheetParserTest.java`, `core/src/test/java/vn/doit/hr/employee/ListGrid.java`

**Interfaces:**
- Consumes: `vn.doit.hr.payroll.SheetGrid` (giá trị ô: `null`, `String`, `BigDecimal`, `LocalDate`, `CellError`).
- Produces:
  - `enum EmployeeField` theo đúng thứ tự §4.3: `CODE("Mã NV")`, `FULL_NAME("Họ và tên")`, `PERSON_TYPE("Loại hồ sơ")`, `GENDER("Giới tính")`, `BIRTH_DATE("Ngày sinh")`, `ID_NUMBER("Số CCCD")`, `ID_ISSUE_DATE("Ngày cấp")`, `ID_ISSUE_PLACE("Nơi cấp")`, `RESIDENCE("Địa chỉ thường trú")`, `PHONE("Điện thoại")`, `EMAIL("Email")`, `EDUCATION("Trình độ")`, `TAX_CODE("MST cá nhân")`, `BANK_ACCOUNT("Số tài khoản")`, `BANK_NAME("Ngân hàng")`, `SOCIAL_INSURANCE_NO("Số sổ BHXH")`, `SOCIAL_INSURANCE_START("Ngày bắt đầu đóng BHXH")`, `SOCIAL_INSURANCE_SALARY("Lương đóng BHXH")`, `INSURANCE_EXEMPTION("Không thuộc diện BH bắt buộc")`, `INSURANCE_EXEMPTION_NOTE("Lý do khác")`, `DEPENDANTS("Số người phụ thuộc")`, `DEPARTMENT("Phòng ban")`, `POSITION("Vị trí")`, `MANAGER("Quản lý trực tiếp")`, `JOIN_DATE("Ngày vào làm")`, `NOTE("Ghi chú")`. Mỗi hằng có `header()`, `required()` (chỉ `FULL_NAME`, `ID_NUMBER`), `kind()` ∈ `TEXT|DATE|INTEGER|CHOICE`, `maxLength()` (theo §4.3; `NOTE` không giới hạn = 0), `choices()` (`List<String>`, rỗng nếu không phải CHOICE), `textFormat()` (true cho CODE, ID_NUMBER, PHONE, TAX_CODE, BANK_ACCOUNT, SOCIAL_INSURANCE_NO, MANAGER), `hint()` (một câu cho sheet Hướng dẫn, kèm ví dụ giả).
  - Hằng lựa chọn: `EmployeeField.PERSON_TYPES = List.of("Nhân viên", "Thực tập", "HĐ dịch vụ")`, `GENDERS = List.of("Nam", "Nữ")`, `EXEMPTIONS = List.of("Đang hưởng lương hưu", "Cộng tác viên, HĐ dịch vụ", "HĐLĐ dưới 1 tháng", "Thử việc theo HĐ thử việc riêng", "Khác")`.
  - `record EmployeeRow(int excelRow, Map<EmployeeField, Object> values)` với `String text(EmployeeField)`, `LocalDate date(EmployeeField)`, `Long integer(EmployeeField)`; CHOICE lưu nhãn chuẩn (đúng chuỗi trong `choices()`); ô trống không có khóa.
  - `record EmployeeIssue(Severity severity, int excelRow, String fullName, EmployeeField field, String message)`, `enum Severity { ERROR, WARNING }`, `static fileError(String)`, `static fileWarning(String)` (excelRow 0).
  - `record ParsedEmployees(Map<EmployeeField, Integer> columns, List<EmployeeRow> rows, List<EmployeeIssue> issues)`.
  - `EmployeeSheetParser`: `static final String SHEET = "Danh sách nhân viên"`, `static final int MAX_ROWS = 2000`, `ParsedEmployees parse(SheetGrid grid)`.
  - `final class Fold { static String fold(Object) }`: bỏ dấu (đ→d), chữ thường, gộp khoảng trắng, cắt hai đầu; null → "" (cùng quy tắc `VietnameseText.fold` của app).

- [ ] **Step 1: Viết `ListGrid` (test helper, `SheetGrid` từ `List<List<Object>>`) và `EmployeeSheetParserTest`:**
  - `readsColumnsInAnyOrder`: tiêu đề ở dòng 3 (dòng 1–2 là tiêu đề trang), cột đảo thứ tự, thiếu các cột không bắt buộc → `columns` đúng chỉ số, `rows.get(0).excelRow() == 4`.
  - `missingRequiredColumnIsFileError`: thiếu cột "Số CCCD" → một `fileError` "Không tìm thấy cột \"Số CCCD\".", `rows` rỗng.
  - `noHeaderInFirstTenRows`: → `fileError` "Không tìm thấy dòng tiêu đề có cột \"Họ và tên\". Sai mẫu file?".
  - `unknownColumnIsFileWarning`: cột "Sở thích" → `fileWarning` chứa "Sở thích", vẫn đọc dòng.
  - `datesFromCellOrText`: `LocalDate.of(1990,5,1)` và chữ `"1/5/1990"` cùng ra `1990-05-01`; `"31/02/1990"` → ERROR ở `BIRTH_DATE` "Ngày không hợp lệ: \"31/02/1990\" (dạng dd/MM/yyyy)".
  - `numericIdNumberReadAsDigits`: `new BigDecimal("1.23456789012E+11")` → `"123456789012"`; `new BigDecimal("79123456789")` (11 số) → `"079123456789"` + WARNING ở `ID_NUMBER` "Đã thêm số 0 ở đầu số CCCD, kiểm tra lại".
  - `choicesIgnoreCaseAccentAndSpaces`: `" nữ "` → `"Nữ"`; `"thuc tap"` → `"Thực tập"`; `"Khác loại"` ở `PERSON_TYPE` → ERROR "Giá trị không có trong danh sách: Nhân viên, Thực tập, HĐ dịch vụ".
  - `integersAndLengths`: `DEPENDANTS` = `"2"` hoặc `BigDecimal 2` → `2L`; `"-1"`, `"1,5"` → ERROR "Phải là số nguyên ≥ 0"; `FULL_NAME` 101 ký tự → ERROR "Dài quá 100 ký tự".
  - `partiallyFilledRowIsKept`: dòng chỉ có `NOTE` → vẫn nằm trong `rows`; dòng trống hoàn toàn (mọi ô null/khoảng trắng) → bỏ qua.
  - `tooManyRows`: 2.001 dòng dữ liệu → `fileError` "File có 2001 dòng, tối đa 2000 dòng mỗi lần nhập", `rows` rỗng.
  - `cellErrorIsRowError`: ô `new SheetGrid.CellError("#REF!")` → ERROR "Ô bị lỗi công thức #REF!".
- [ ] **Step 2:** `mvn -pl core test -Dtest=EmployeeSheetParserTest` → FAIL (không biên dịch).
- [ ] **Step 3:** Cài các lớp ở Produces. Nhận tiêu đề: so `Fold.fold(ô)` với `Fold.fold(header())`. Ô chữ: `String` cắt hai đầu; `BigDecimal` → `stripTrailingZeros().toPlainString()`. Kiểm tra độ dài sau khi đọc; lỗi định dạng của một ô không chặn các ô khác cùng dòng. `fullName` của issue lấy từ ô Họ và tên của dòng (có thể null).
- [ ] **Step 4:** `mvn -pl core test -Dtest=EmployeeSheetParserTest` → PASS.
- [ ] **Step 5:** Commit `Lõi: đọc sheet danh sách nhân viên (cột theo tên, ngày, CCCD dạng số, lựa chọn)`.

### Task 2: Lõi – file mẫu

**Files:**
- Create: `core/src/main/java/vn/doit/hr/employee/EmployeeTemplateWriter.java`
- Test: `core/src/test/java/vn/doit/hr/employee/EmployeeTemplateWriterTest.java`

**Interfaces:**
- Consumes: `EmployeeField`, `EmployeeSheetParser`, `vn.doit.hr.payroll.PoiSheetGrid.of(Sheet)`.
- Produces: `void EmployeeTemplateWriter.write(OutputStream out) throws IOException`.

- [ ] **Step 1: Viết `EmployeeTemplateWriterTest`:**
  - `templateRoundTrips`: ghi file; sheet 0 tên `"Danh sách nhân viên"`, sheet 1 `"Hướng dẫn"`; đọc lại sheet 0 bằng `PoiSheetGrid.of` + parser → `columns.size() == EmployeeField.values().length`, `rows` rỗng, không có issue. Điền dòng 2 (Họ và tên "Nguyễn Văn Mẫu", CCCD "001090000001") → đọc ra 1 dòng đúng giá trị.
  - `textColumnsAndDropdowns`: ô dòng 2 của các cột `textFormat()` có định dạng `"@"`; sheet có `DataValidation` cho `PERSON_TYPE`, `GENDER`, `INSURANCE_EXEMPTION` với đúng `choices()`; dòng tiêu đề cố định (`getPaneInformation().isFreezePane()`).
  - `guideHasOneLinePerField`: sheet "Hướng dẫn" có một dòng cho mỗi `EmployeeField` (cột A = `header()`), và có đoạn chứa "DOIT" + "TT" + "HDDV" giải thích mã tự gán.
- [ ] **Step 2:** `mvn -pl core test -Dtest=EmployeeTemplateWriterTest` → FAIL.
- [ ] **Step 3:** Cài `write`, theo kiểu `PayrollTemplateWriter`: tiêu đề dòng 1, cột bắt buộc nền `LIGHT_ORANGE`, cột khác `PALE_BLUE`, định dạng chữ cho cột `textFormat()` áp tới dòng 2001, data validation dạng danh sách tới dòng 2001, cố định dòng 1. Đoạn quy tắc mã ở sheet Hướng dẫn: "Mã NV để trống: hệ thống tự cấp. Nhân viên: DOIT + 2 số cuối năm vào làm + số thứ tự (vd DOIT2601); Thực tập: TT + năm + số (vd TT2601); HĐ dịch vụ: luôn tự cấp HDDV1, HDDV2… Có ghi mã thì giữ nguyên mã đó."
- [ ] **Step 4:** `mvn -pl core test` → PASS (toàn bộ lõi).
- [ ] **Step 5:** Commit `Lõi: file mẫu nhập danh sách nhân viên`.

### Task 3: App – kiểm tra nghiệp vụ và mã dự kiến

**Files:**
- Create: `app/src/main/java/vn/doit/hrm/service/employee/EmployeeImportCheck.java`, `app/src/main/java/vn/doit/hrm/service/employee/ImportDirectory.java`
- Test: `app/src/test/java/vn/doit/hrm/service/employee/EmployeeImportCheckTest.java`

**Interfaces:**
- Consumes: `ParsedEmployees`, `EmployeeRow`, `EmployeeIssue`, `EmployeeField` (Task 1); `EmployeeCodes.prefix/next/normalize`, `IdNumbers.trim`, `VietnameseText.fold`, `PersonType`, `Gender`, `InsuranceExemption`.
- Produces:
  - `interface ImportDirectory`: `List<String> codesStartingWith(String prefix)`, `Optional<Long> employeeIdByCode(String code)`, `Optional<Long> employeeIdByIdNumber(String trimmedIdNumber)`, `List<DepartmentRef> departments()`; `record DepartmentRef(Long id, String name, boolean active)`.
  - `final class EmployeeImportCheck`, `static Result run(ParsedEmployees parsed, ImportDirectory dir, LocalDate today)`.
  - `record Result(List<PlannedRow> rows, List<EmployeeIssue> issues, List<NewDepartment> newDepartments)` với `long errorCount()`, `long warningCount()`, `boolean canSave()` (= `errorCount() == 0`).
  - `record PlannedRow(EmployeeRow row, PersonType personType, Gender gender, InsuranceExemption exemption, String code, boolean codeProvisional, String idNumber, Long departmentId, String newDepartmentName, ManagerRef manager)`; `code == null` cho HĐ dịch vụ (tự sinh); `record ManagerRef(Long employeeId, Integer excelRow)` (một trong hai khác null; null nếu ô trống).
  - `record NewDepartment(String name, List<Integer> excelRows)`.
  - Ánh xạ nhãn → enum: `static PersonType personType(String label)` (null → `NHAN_VIEN`), `static Gender gender(String)`, `static InsuranceExemption exemption(String)` (so với `InsuranceExemption.label()`).

- [ ] **Step 1: Viết `EmployeeImportCheckTest`** (dựng `ParsedEmployees` bằng tay, `ImportDirectory` giả bằng map; `today = 2026-10-22`). Một test cho mỗi quy tắc §5.1/§5.2; thông báo đúng chuỗi:
  - `exemptionLabelsMatchCore`: `EmployeeField.EXEMPTIONS` == nhãn `InsuranceExemption.values()` theo thứ tự.
  - `missingNameOrIdNumber`: "Thiếu Họ và tên", "Thiếu Số CCCD".
  - `idNumberFormat`: `"12345"` → "Số CCCD phải gồm 12 chữ số (hoặc 9 chữ số CMND cũ)".
  - `idNumberDuplicates`: trùng hồ sơ có sẵn → "Số CCCD đã có trong hệ thống"; hai dòng trùng → mỗi dòng "Số CCCD trùng với dòng N".
  - `codeDuplicates`: tương tự với "Mã NV đã có trong hệ thống" / "Mã NV trùng với dòng N"; so sau `normalize` (`" doit2601"` trùng `"DOIT2601"`).
  - `exemptionOtherNeedsNote`: "Khác" + trống ghi chú → ERROR ở `INSURANCE_EXEMPTION_NOTE` "Chọn \"Khác\" thì phải ghi Lý do khác".
  - `invalidEmail`: `"a@"` → "Email không hợp lệ".
  - `provisionalCodes`: dir có `DOIT2401, DOIT2517, TT2601`; dòng NV vào làm 2026 trống mã → `DOIT2618`, dòng TT trống mã → `TT2602`, dòng NV thứ hai → `DOIT2619`; `codeProvisional` true; dòng ghi `DOIT2401X` (sai dạng) giữ nguyên + WARNING "Mã không theo dạng DOIT/TT + số; dãy tự cấp bỏ qua mã này".
  - `provisionalCodesSkipManualCodesLaterInFile`: dir rỗng; dòng 2 NV 2026 trống mã, dòng 5 ghi `DOIT2601` → dòng 2 nhận `DOIT2602`.
  - `serviceRowsIgnoreCode`: HĐ dịch vụ có ghi mã → `code == null` + WARNING "HĐ dịch vụ luôn được cấp mã HDDV tự động; bỏ qua mã đã ghi".
  - `missingJoinDateUsesThisYear`: → mã `DOIT26..` + WARNING "Thiếu Ngày vào làm: mã tự cấp lấy năm nay".
  - `ageAndFutureJoin`: sinh 2015-01-01 vào làm 2026 → WARNING "Dưới 15 tuổi tại ngày vào làm"; vào làm 2026-12-01 → WARNING "Ngày vào làm sau hôm nay".
  - `departmentMatchIgnoresAccentAndCase`: dir có `(1,"Phòng Kỹ thuật",true)`; ô `"  phong ky thuat "` → `departmentId == 1`, không cảnh báo.
  - `newDepartmentWarned`: hai dòng "Kinh doanh" (một dòng viết "kinh doanh") → một `NewDepartment("Kinh doanh",[2,3])` (tên theo dòng đầu) + WARNING mỗi dòng "Phòng ban \"Kinh doanh\" chưa có, sẽ được tạo mới".
  - `ambiguousDepartment`: dir có hai phòng cùng tên đều đang hoạt động → ERROR "Tên phòng ban không rõ: có nhiều phòng ban trùng tên"; một hoạt động một ngưng → chọn phòng hoạt động.
  - `managerResolution`: theo thứ tự mã hệ thống → mã ghi tay trong file → CCCD hệ thống → CCCD trong file; không thấy → ERROR "Không tìm thấy người quản lý \"X\" (theo mã NV hoặc số CCCD)"; tự trỏ → "Không thể tự làm quản lý của mình"; A↔B trong file → cả hai "Quản lý tạo thành vòng (dòng 2 → dòng 3 → dòng 2)".
- [ ] **Step 2:** `mvn -pl app -am verify -Dskip.npm -Dskip.installnodenpm -Dtest=EmployeeImportCheckTest -Dsurefire.failIfNoSpecifiedTests=false -DskipITs` → FAIL.
- [ ] **Step 3:** Cài `EmployeeImportCheck.run`. Thứ tự: (1) chuẩn hóa từng dòng và lỗi ô; (2) trùng CCCD/mã; (3) gom mọi mã ghi tay đúng dạng của file vào `existing` theo tiền tố, *trước* khi cấp mã tạm theo thứ tự dòng; (4) phòng ban; (5) quản lý và dò vòng (đi theo chuỗi `excelRow` trong file, dừng ở hồ sơ hệ thống). Issue của parser được giữ nguyên trong `Result.issues` (đứng trước), sắp theo `excelRow`.
- [ ] **Step 4:** Chạy lệnh Step 2 → PASS.
- [ ] **Step 5:** Commit `Kiểm tra danh sách nhân viên nhập: trùng CCCD/mã, mã dự kiến, phòng ban, quản lý`.

### Task 4: App – dịch vụ, lưu một giao dịch, REST

**Files:**
- Create: `app/src/main/java/vn/doit/hrm/service/employee/EmployeeImportService.java`, `app/src/main/java/vn/doit/hrm/service/employee/EmployeeImportInvalidException.java`, `app/src/main/java/vn/doit/hrm/service/employee/dto/EmployeeImportCheckDTO.java`, `.../dto/EmployeeImportResultDTO.java`, `app/src/main/java/vn/doit/hrm/service/employee/EmployeeIssueReportWriter.java`, `app/src/main/java/vn/doit/hrm/web/rest/EmployeeImportResource.java`
- Modify: `app/src/main/java/vn/doit/hrm/service/EmployeeService.java` (thêm `createImported`), `app/src/main/java/vn/doit/hrm/repository/DepartmentRepository.java`, `app/src/main/java/vn/doit/hrm/web/rest/errors/ExceptionTranslator.java:285-291`
- Test: `app/src/test/java/vn/doit/hrm/web/rest/EmployeeImportResourceIT.java`, `app/src/test/java/vn/doit/hrm/service/employee/EmployeeImportFiles.java` (dựng file `.xlsx` giả bằng `EmployeeTemplateWriter` + POI)

**Interfaces:**
- Consumes: Task 1–3; `EmployeeRepository.findCodesStartingWith`, `findOneByCode`, `findAllByTrimmedIdNumber`, `lockCodeAssignment`; `PoiSheetGrid`.
- Produces:
  - `EmployeeService.createImported(Employee e)`: `e.setUser(null)`, `applyRules(e, null)`, `save` và trả `Employee` (dùng lại đúng quy tắc tạo hồ sơ; mã đã điền sẵn thì `applyRules` chỉ chuẩn hóa và kiểm trùng; HĐ dịch vụ tự sinh `HDDVn`).
  - `EmployeeImportService`: `byte[] template()`, `EmployeeImportCheckDTO check(MultipartFile)`, `byte[] issueReport(MultipartFile)`, `EmployeeImportResultDTO save(MultipartFile, boolean warningsAcknowledged)`.
  - `EmployeeImportInvalidException extends EmployeeRuleException` (key `importinvalid`, message "File còn lỗi hoặc cảnh báo chưa xác nhận; xem lại kết quả kiểm tra"), mang `EmployeeImportCheckDTO check`; `ExceptionTranslator.badRequest` đặt `body.setProperty("check", …)` như `MissingFieldsException`.
  - `EmployeeImportCheckDTO(int rowCount, List<Row> rows, List<Issue> issues, List<NewDepartment> newDepartments, long errorCount, long warningCount, boolean canSave)`; `Row(int excelRow, String fullName, String idNumber, String personType, String code, boolean codeProvisional, String departmentName, String managerRef)`; `Issue(String severity, int excelRow, String fullName, String field, String fieldLabel, String message)`; `NewDepartment(String name, List<Integer> excelRows)`. `personType` là tên enum; `code` null cho HĐ dịch vụ.
  - `EmployeeImportResultDTO(int createdCount, List<Created> created, List<String> createdDepartments)`; `Created(int excelRow, String fullName, String code)`.
  - REST (`/api/employees/import`, `@PreAuthorize` ADMIN, HR): `GET /template.xlsx` (tên `mau-nhap-nhan-vien.xlsx`), `POST /check` (multipart `file`), `POST /issues.xlsx` (multipart `file`, tên `loi-nhap-nhan-vien.xlsx`), `POST ""` (multipart `file`, param `warningsAcknowledged`) → 201 + `EmployeeImportResultDTO`.

- [ ] **Step 1: Viết `EmployeeImportFiles`** (`static byte[] xlsx(List<Map<EmployeeField,Object>> rows)` điền vào file mẫu) **và `EmployeeImportResourceIT`** (`@IntegrationTest @AutoConfigureMockMvc @WithMockUser(authorities = "ROLE_HR")`, xóa dữ liệu tạo ra ở `@AfterEach`):
  - `templateDownloads`: 200, content-type xlsx, header tên `mau-nhap-nhan-vien.xlsx`.
  - `forbiddenForAccountantAndEmployee`: `@WithMockUser(authorities="ROLE_KE_TOAN")` và `ROLE_USER` → 403 ở cả 4 endpoint.
  - `checkWritesNothing`: 3 dòng hợp lệ → `canSave` true, `rows[0].code` `DOIT26..`, `codeProvisional` true; số `Employee` và `Department` không đổi; JSON không có khóa `taxCode`, `bankAccount`, `socialInsuranceSalary`.
  - `notXlsxRejected`: file `.csv` hoặc nội dung không bắt đầu `PK` → 400 `error.employeeimportnotxlsx`, message "Chỉ nhận file Excel .xlsx (Excel 2007 trở lên), lưu từ mẫu nhập nhân viên".
  - `saveCreatesEmployeesDepartmentsAndManagers`: 3 dòng — NV trống mã (phòng "Kinh doanh" mới, quản lý = CCCD dòng 3), TT trống mã, NV ghi `DOIT2690` → lưu với `warningsAcknowledged=true` → 201, `createdCount` 3, `createdDepartments` ["Kinh doanh"]; DB: 3 hồ sơ `DANG_LAM_VIEC`, `user` null, manager của dòng 2 là hồ sơ dòng 3, mã TT dạng `TT26..`.
  - `saveUsesSamePlanAsCheck`: mã trong kết quả lưu == mã dự kiến của `/check` khi không có ai chen giữa (gồm trường hợp mã ghi tay ở dòng sau).
  - `errorsBlockSave`: một dòng thiếu CCCD → 400 `error.importinvalid`, thân có `check.errorCount` 1; không hồ sơ nào được tạo.
  - `warningsNeedAcknowledgement`: phòng ban mới, `warningsAcknowledged=false` → 400 `error.importinvalid`; `true` → 201.
  - `conflictAfterCheckRollsBack`: `/check` OK, rồi tạo tay một hồ sơ cùng CCCD với dòng 2, rồi lưu → 400 `error.importinvalid`; dòng 1 cũng không được tạo, phòng ban mới không được tạo.
  - `issueReport`: file có lỗi → `.xlsx` sheet "Lỗi và cảnh báo", tiêu đề `Dòng | Họ tên | Mức độ | Nội dung`, có dòng lỗi.
- [ ] **Step 2:** `mvn -pl app -am verify -Dskip.npm -Dskip.installnodenpm -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=EmployeeImportResourceIT` → FAIL.
- [ ] **Step 3:** Cài. Đọc file: kiểm đuôi/size (`MAX_BYTES = 20 MB`, `MaxUploadSizeExceededException`), chữ ký `PK`, `PoiSheetGrid.of(workbook.getSheet(EmployeeSheetParser.SHEET) ?? sheet 0)`; lỗi đọc → `EmployeeRuleException(…, "employeeimportnotxlsx")`; parser trả lỗi cấp file khi chưa nhận được cột → `EmployeeRuleException(<các thông báo>, "employeeimportlayout")`. `ImportDirectory` cài trên repository (thêm `DepartmentRepository.findAll()` là đủ; không cần query mới). `save`: `employeeRepository.lockCodeAssignment()` → `EmployeeImportCheck.run` lại → chưa hợp lệ thì ném `EmployeeImportInvalidException` → tạo `Department` mới (`active=true`) → mỗi `PlannedRow` dựng `Employee` (map các ô theo §4.3, `dependants` trống = 0, mã = `PlannedRow.code`) rồi `employeeService.createImported` → lượt hai gán `manager` theo `ManagerRef` → trả kết quả. Toàn bộ `@Transactional`; mọi exception làm rollback. Log `LOG.info("Nhập {} hồ sơ bởi {}", n, login)` — không log nội dung dòng.
- [ ] **Step 4:** Chạy lệnh Step 2 → PASS; rồi chạy lại `EmployeeResourceIT` (`-Dit.test=EmployeeResourceIT`) → PASS (không làm hỏng tạo hồ sơ tay).
- [ ] **Step 5:** Commit `Nhập danh sách nhân viên: kiểm tra, báo cáo lỗi, lưu một giao dịch (Quản trị, HR)`.

### Task 5: Giao diện – wizard nhập Excel

**Files:**
- Create: `app/src/main/webapp/app/entities/employee/import/employee-import.tsx`, `app/src/main/webapp/app/entities/employee/import/employee-import.model.ts`, `app/src/main/webapp/app/entities/employee/import/employee-import.spec.tsx`
- Modify: `app/src/main/webapp/app/entities/employee/index.tsx` (route `nhap-excel` trong `PrivateRoute hasAnyAuthorities={WRITE_ROLES}`), `app/src/main/webapp/app/entities/employee/employee-list.tsx:105-112` (nút "Nhập từ Excel" khi `canWrite`, `Link` tới `/nhan-vien/nhap-excel`, cạnh "Xuất Excel")

**Interfaces:**
- Consumes: REST của Task 4; `downloadFile` (`app/shared/util/download`).
- Produces: `IEmployeeImportCheck`, `IEmployeeImportResult` trong `employee-import.model.ts`, khớp từng trường DTO của Task 4.

- [ ] **Step 1: Viết `employee-import.spec.tsx`** (mock `axios` và `downloadFile` như `import-wizard.spec.tsx`):
  - `downloads template`: bấm "Tải file mẫu" → `downloadFile('api/employees/import/template.xlsx', 'mau-nhap-nhan-vien.xlsx')`.
  - `checks file then shows rows`: chọn file → `post('api/employees/import/check', FormData)`; hiện "Bước 2. Kiểm tra", "3 dòng · 0 lỗi · 1 cảnh báo", phòng ban sẽ tạo "Kinh doanh", mã kèm nhãn "dự kiến", HĐ dịch vụ hiện "HDDV (tự sinh)".
  - `save disabled with errors`: `canSave` false → nút "Lưu 3 hồ sơ" disabled; nút "Tải báo cáo lỗi" gọi `post('api/employees/import/issues.xlsx', FormData, {responseType:'blob'})`.
  - `save needs warning acknowledgement`: có cảnh báo → nút disabled cho đến khi tick "Đã xem cảnh báo"; bấm lưu → `post('api/employees/import', FormData)` với `warningsAcknowledged=true` trong FormData.
  - `shows result`: 201 → "Đã tạo 3 hồ sơ", bảng mã thật, link "Về danh sách nhân viên" tới `/nhan-vien`.
  - `400 with check refreshes step 2`: lỗi `{ response: { status: 400, data: { check: {...errorCount: 1} } } }` → ở lại bước 2 với kết quả mới, hiện thông báo lỗi.
  - `choose another file`: "Chọn file khác" → về bước 1.
- [ ] **Step 2:** `cd app && npx vitest run src/main/webapp/app/entities/employee/import` → FAIL.
- [ ] **Step 3:** Cài wizard: giữ `File` trong state để gửi lại; dòng có lỗi `table-danger`, có cảnh báo `table-warning`; danh sách lỗi/cảnh báo bên dưới bảng (`Dòng N – <cột>: <nội dung>`); thanh bước dùng lại cách vẽ của `import-wizard.tsx` (tách `Steps` ra `app/shared/layout/steps.tsx` nếu cần dùng chung, sửa import ở `import-wizard.tsx`). Báo cáo lỗi tải bằng blob + link tạm như `downloadFile`.
- [ ] **Step 4:** `npx vitest run src/main/webapp/app/entities/employee` → PASS (gồm `employee-list.spec.tsx`); `npx tsc --noEmit` và `npm run lint` → không lỗi.
- [ ] **Step 5:** Commit `Giao diện nhập danh sách nhân viên từ Excel: tải mẫu, kiểm tra, lưu`.

### Task 6: README và kiểm tra toàn bộ

**Files:**
- Modify: `README.md` (mục mới ngay sau "### Hồ sơ: mã nhân viên, thực tập, bảo hiểm bắt buộc")

- [ ] **Step 1:** Viết mục "### Nhập danh sách nhân viên từ Excel": ai dùng (Quản trị, HR), các bước, cột bắt buộc, quy tắc mã, trùng là lỗi, phòng ban tự tạo, quản lý theo mã/CCCD, lưu tất cả hoặc không, file không được lưu lại, giới hạn 20 MB / 2.000 dòng, ngoài phạm vi (cập nhật hồ sơ, HĐ, tài khoản).
- [ ] **Step 2:** Backend `mvn -pl app -am verify -Dskip.npm -Dskip.installnodenpm` (chạy một mình) → BUILD SUCCESS. Sau đó `cd app && npm test && npx tsc --noEmit && npm run lint` → đạt.
- [ ] **Step 3:** Commit `README: nhập danh sách nhân viên từ Excel`.
