# Lưu trữ, khôi phục, nhận làm lại và xóa hẳn nhân viên – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Quản trị/HR đưa người đã nghỉ việc vào vùng lưu trữ, khôi phục và nhận làm lại; chỉ tài khoản `admin` xóa hẳn được, và các dòng lương đã duyệt giữ nguyên.

**Architecture:** Lưu trữ là ba cột trên `employee` (`archived_at/by/note`). Các truy vấn liệt kê nhân viên mặc định loại hồ sơ đã lưu trữ. Mọi API sửa từ chối hồ sơ đã lưu trữ thông qua một guard dùng chung. Nhận làm lại ghi một bản ghi `employment_period` cho lần làm việc trước. Xóa hẳn chạy trong một giao dịch theo thứ tự khóa ngoại: dòng lương chỉ bỏ liên kết tới hồ sơ, mọi bản ghi khác bị xóa, kể cả `_aud`. File tài liệu bị xóa sau khi commit. Một test đọc metadata khóa ngoại để giữ dịch vụ xóa luôn đầy đủ.

**Tech Stack:** Java 21 / Spring Boot 4, PostgreSQL + Liquibase + Envers, React 19 + Vitest + react-bootstrap.

**Spec:** `docs/superpowers/specs/2026-10-22-hrm-luu-tru-nhan-vien-design.md`

## Global Constraints

- **Lưu trữ:**
  - Chỉ hồ sơ **Đã nghỉ** (việc cho nghỉ đã áp dụng) hoặc **hồ sơ tạo nhầm** mới lưu trữ được. Hồ sơ tạo nhầm là hồ sơ không có HĐ, thay đổi lương, phụ cấp, quyết định, tài liệu, đơn nghỉ, đơn làm thêm, dòng sổ phép, dòng lương, phiếu lương, lịch sử nghỉ việc.
  - Trường hợp khác trả 400 `archivenotterminated` với thông báo "Chỉ lưu trữ được nhân viên đã nghỉ việc. Hãy cho nghỉ việc trước".
  - Lưu trữ lại hồ sơ đã lưu trữ trả 400 `alreadyarchived`.
- **Không đổi dữ liệu:** lưu trữ và khôi phục không xóa, không sửa dữ liệu nào khác. Tài khoản giữ nguyên trạng thái khóa. Khôi phục giữ nguyên trạng thái hồ sơ.
- **Hồ sơ đã lưu trữ:**
  - Không hiện ở danh sách, tìm kiếm, xuất Excel, ô chọn, nhắc việc, Tổng quan, lịch nghỉ, số dư phép công ty, hộp duyệt đơn.
  - Vẫn hiện ở bảng lương và phiếu lương kỳ cũ, và ở tổng hợp nghỉ phép cho kế toán tháng cũ, kèm nhãn "Lưu trữ".
  - Mọi API sửa trên hồ sơ và bản ghi con trả 400 `employeearchived`.
- **Nhận làm lại** `{joinDate, departmentId?, position?, personType?}`:
  - Chỉ áp dụng cho hồ sơ Đã nghỉ, chưa lưu trữ, không có lịch nghỉ đang hẹn.
  - `joinDate` phải sau ngày làm việc cuối, nếu không trả 400 `rehiredatebeforeleave`.
  - Giữ mã nhân viên, ghi `employment_period`, xóa trắng 4 trường nghỉ việc, trạng thái `DANG_LAM_VIEC`.
  - Hoàn tác nghỉ việc cũ bị chặn với `undoconflict` "Nhân viên đã được nhận làm lại".
  - Mở lại tài khoản đã gắn, trừ `admin`.
  - Sổ phép và thâm niên tính từ `joinDate` mới.
- **Xóa hẳn:**
  - Chỉ login `admin` (`IdNumbers.SYSTEM_ADMIN_LOGIN`) được làm, người khác nhận 403 `deleterequiressystemadmin`.
  - Hồ sơ phải đang lưu trữ, nếu không trả 400 `deletenotarchived`.
  - Bị chặn bởi kỳ lương chưa duyệt, bản nháp import đang làm, hoặc lượt gửi phiếu đang chờ: trả 400 `deleteblocked` kèm danh sách lý do.
  - Nhận `{confirmCode, reason}`; mã xác nhận sai trả 400 `deleteconfirmmismatch`; lý do bắt buộc.
  - Không xóa được hồ sơ gắn tài khoản `admin`.
- **Kết quả xóa hẳn:**
  - Dòng lương giữ nguyên số tiền, `file_code`, `file_name`; chỉ `employee_id` thành NULL.
  - Nhật ký xóa ghi người xóa, thời điểm, lý do, mã, họ tên, và không có CCCD/NH/địa chỉ.
  - `manager` và `department.head` trỏ tới người bị xóa thành NULL. `approver_employee_id` trên đơn của người khác thành NULL; `decided_by` giữ nguyên.
- **Quyền:** lưu trữ, khôi phục, nhận làm lại, xem vùng lưu trữ dành cho `ROLE_ADMIN` và `ROLE_HR`. Xóa hẳn và nhật ký xóa chỉ dành cho login `admin`. Kiểm tra ở backend.
- **Ràng buộc chung của dự án:**
  - Liquibase chỉ thêm changeset mới, kèm cột `_aud` cho entity có `@Audited`.
  - Lỗi nghiệp vụ dùng `EmployeeRuleException(message, key)`, được dịch thành 400 `error.<key>`. Mọi key mới có câu tiếng Việt trong `webapp/i18n/vi/global.json` (khối `error`) và trong `ERROR_TEXT` ở `entities/employee/detail/detail-api.ts`.
  - Giao diện tiếng Việt, ngày `dd/MM/yyyy`, vùng bấm ≥ 44px.
  - Commit kết thúc bằng `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Không push. Không `git stash`.
- **Môi trường:** `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`; `export DOCKER_HOST=unix://$HOME/.docker/run/docker.sock` (IT cần tắt sandbox).
  - IT tập trung: `mvn -q -pl app -am verify -Dskip.npm -Dskip.installnodenpm -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=<IT> -Dfailsafe.failIfNoSpecifiedTests=false`.
  - Toàn bộ backend: bỏ các tham số `-Dtest` / `-Dit.test`.
  - Frontend: `cd app && npm test`, `npx tsc --noEmit`, `npm run lint`.
  - Fixed Clock trong IT: `@Import` một `@TestConfiguration` lồng có `@Primary Clock.fixed(...)`, như các IT nghỉ phép.

## Review Focus

1. **Hồ sơ đã lưu trữ lọt lại qua một truy vấn liệt kê bị bỏ sót**, ví dụ ô chọn người duyệt, danh sách gắn hồ sơ khi import bảng lương, hay báo cáo quân số. Kỳ vọng: không hiện. Test ở Task 2 (`archivedHiddenEverywhere`): đi qua mọi endpoint liệt kê trong bảng ở Task 2.
2. **Sau này có bảng mới thêm khóa ngoại tới `employee` mà dịch vụ xóa không biết.** Kỳ vọng: test đỏ, không phải xóa hỏng ở production. Test ở Task 4 (`everyEmployeeForeignKeyIsHandled`).
3. **Xóa hẳn người từng là quản lý, trưởng phòng hoặc người duyệt của người khác.** Kỳ vọng: hồ sơ và đơn của người khác vẫn nguyên, chỉ liên kết thành NULL. Test ở Task 4 (`deleteNullsReferencesFromOthers`).
4. **Nhận làm lại rồi bấm "Hoàn tác nghỉ việc" của lần cũ**, hoặc cho nghỉ lần hai rồi hoàn tác. Kỳ vọng: hoàn tác lần cũ bị chặn; lần nghỉ mới hoàn tác bình thường. Test ở Task 3 (`undoOldTerminationBlockedAfterRehire`, `secondTerminationUndoWorks`).
5. **Xóa hẳn bị lỗi giữa chừng**, ví dụ một bước xóa ném lỗi. Kỳ vọng: không mất gì, file tài liệu còn nguyên. Test ở Task 4 (`deleteRollsBackOnFailure`).

---

### Task 1: Lưu trữ, khôi phục, vùng lưu trữ (backend)

**Files:**
- Create:
  - `app/src/main/resources/config/liquibase/changelog/20261030000100_employee_archive.xml`: thêm `employee.archived_at timestamp`, `archived_by varchar(50)`, `archive_note varchar(500)`, cùng các cột này trong `employee_aud`. Include changelog này trong `master.xml`.
  - `app/src/main/java/vn/doit/hrm/service/EmployeeArchiveService.java`
  - `app/src/main/java/vn/doit/hrm/service/ArchivedEmployeeGuard.java`
  - `app/src/main/java/vn/doit/hrm/service/dto/ArchivedEmployeeDTO.java`
  - `app/src/main/java/vn/doit/hrm/service/dto/ArchiveResultDTO.java`
- Modify:
  - `domain/Employee.java`: 3 trường, có getter/setter.
  - `service/dto/EmployeeDTO.java`: thêm `archivedAt`, `archivedBy` (chỉ đọc).
  - `web/rest/EmployeeResource.java`: thêm `POST /{id}/archive` `{note?}`, `POST /{id}/restore`, `GET /archived?q=&page=` (Quản trị, HR).
  - Các service sửa hồ sơ và bản ghi con gọi guard: `EmployeeService` (update/partialUpdate), `EmploymentContractService`, `SalaryChangeService`, `AllowanceService`, `WorkAssignmentService`, `AssignmentDecisionService`, `EmployeeDocumentService` (upload/delete), `TerminationService` (terminate/cancel/undo), `EmployeeAccountService` (createAndLink/link/unlink), `LeaveBalanceService.adjust`, `LeaveRequestService.submit`, `OvertimeService.submit`.
- Test: `app/src/test/java/vn/doit/hrm/web/rest/EmployeeArchiveIT.java`

**Interfaces:**
- Produces:
  - `ArchivedEmployeeGuard.requireActive(Employee e)` và `requireActive(Long employeeId)`: ném `EmployeeRuleException("Hồ sơ đã lưu trữ, khôi phục trước khi sửa", "employeearchived")` khi `archivedAt != null`.
  - `EmployeeArchiveService`:
    - `ArchiveResultDTO archive(Long id, String note)`. `ArchiveResultDTO(List<Ref> managedEmployees, List<Ref> headedDepartments)`, với `Ref(Long id, String code, String name)`.
    - `void restore(Long id)`.
    - `Page<ArchivedEmployeeDTO> archived(String q, Pageable p)`. `q` khớp mã, `searchText` hoặc CCCD đã trim.
    - `boolean isMistakenProfile(Long id)`.
  - `ArchivedEmployeeDTO(Long id, String code, String fullName, String departmentName, LocalDate lastWorkingDay, Instant archivedAt, String archivedBy, String archiveNote)`.
  - `Employee.isArchived()`.

- [ ] **Step 1: Viết `EmployeeArchiveIT`**, `@WithMockUser(authorities="ROLE_HR")`:
  - `archiveTerminated`: hồ sơ DA_NGHI → 200. Kiểm `archivedAt`, `archivedBy` = login và ghi chú. Mọi bảng con giữ nguyên số dòng. Tài khoản gắn kèm vẫn `activated=false`.
  - `archiveActiveWithDataRejected`: hồ sơ DANG_LAM_VIEC có HĐ → 400 `error.archivenotterminated`.
  - `archiveMistakenProfile`: hồ sơ DANG_LAM_VIEC không có bản ghi con nào → 200. Tài khoản gắn kèm, nếu có, bị `activated=false`. Trạng thái giữ nguyên.
  - `archiveTwiceRejected` → 400 `error.alreadyarchived`.
  - `archiveReportsReferences`: hồ sơ là manager của 2 người và trưởng phòng của 1 phòng → `managedEmployees` có 2 phần tử, `headedDepartments` có 1.
  - `restoreReturnsIdenticalProfile`: lưu trữ rồi khôi phục. Hồ sơ (DTO) giống hệt trước khi lưu trữ, ngoại trừ các trường audit. Trạng thái vẫn DA_NGHI.
  - `archivedListSearch`: `GET /archived?q=<một phần tên không dấu>` tìm thấy; tìm theo CCCD cũng thấy; hồ sơ chưa lưu trữ không có trong kết quả.
  - `archivedProfileIsReadOnly`: với hồ sơ đã lưu trữ, `PUT /api/employees/{id}`, thêm HĐ, thêm phụ cấp, tải tài liệu, cho nghỉ việc và điều chỉnh phép đều trả 400 `error.employeearchived`. `GET /api/employees/{id}` vẫn 200.
  - `forbiddenForOtherRoles`: `ROLE_KE_TOAN` và `ROLE_NHAN_VIEN` nhận 403 ở archive, restore, archived.
- [ ] **Step 2:** Chạy IT tập trung `EmployeeArchiveIT` → FAIL.
- [ ] **Step 3:** Cài các phần ở Interfaces. Hồ sơ tạo nhầm được kiểm bằng các truy vấn `exists` trên từng bảng con trong định nghĩa ở Global Constraints. Ghi các key mới vào i18n và `ERROR_TEXT`.
- [ ] **Step 4:** Chạy IT tập trung `EmployeeArchiveIT`, sau đó `EmployeeResourceIT` và `TerminationServiceIT` (hoặc các IT nghỉ việc hiện có) → PASS.
- [ ] **Step 5:** Commit "Lưu trữ và khôi phục hồ sơ nhân viên, vùng lưu trữ, chặn sửa hồ sơ đã lưu trữ".

### Task 2: Ẩn hồ sơ đã lưu trữ khỏi mọi danh sách

**Files:**
- Modify, thêm điều kiện mặc định "không lưu trữ" vào mỗi truy vấn liệt kê sau:
  - `service/EmployeeQueryService.createSpecification`: danh sách, đếm, xuất Excel.
  - `repository/EmployeeRepository`:
    - `findTop200BySearchTextContainingOrderByFullNameAsc` (ô chọn người): đổi thành phiên bản `...AndArchivedAtIsNull...`.
    - `findByPersonType`, `findByPersonTypeAndStatus`, `findIdsByPersonType`, `findNhanVienIdsByManagerId`, `findNhanVienIdsByDepartmentHeadId`, `countByPersonTypeAndStatus`.
    - Riêng `findIdsByPersonType` dùng cho job cộng phép: giữ hồ sơ đã lưu trữ, vì người đã nghỉ vẫn cần job chạy chuyển năm/hết hạn trên sổ đã chốt. Thêm một biến thể có tên rõ ràng nếu cần, và ghi chú lý do.
  - `service/dashboard/DashboardService`, `service/dashboard/HrReportService`.
  - `service/reminder/ReminderSnapshotService`, `web/rest/ReminderResource`.
  - `service/leave/LeaveCalendarService`, `LeaveVisibility`, `LeaveBalanceService` (số dư công ty), `ApproverResolver` (người duyệt: không bao giờ chọn hồ sơ đã lưu trữ), `LeaveApprovalDigest`.
  - `service/payroll/PayrollMappingService`: danh sách gắn hồ sơ.
  - `service/DepartmentService`: danh sách ứng viên trưởng phòng.
  - `service/EmployeeExcelExporter`.
  - Giữ nguyên, không lọc: `LeaveAccountingSummaryService`, các màn bảng lương/phiếu lương (`PayrollPeriodService`, `PayslipResource`), và lịch sử.
- Modify, thêm nhãn "Lưu trữ" ở bảng lương/phiếu lương kỳ cũ và ở tổng hợp kế toán:
  - Các DTO dòng lương / dòng tổng hợp thêm `archived: boolean`.
  - Frontend hiện badge "Lưu trữ" cạnh tên.
- Test: `app/src/test/java/vn/doit/hrm/web/rest/EmployeeArchiveVisibilityIT.java`

**Interfaces:**
- Consumes: Task 1 (`archivedAt`).
- Produces: field `archived` (boolean) trên DTO dòng lương của màn kỳ lương và trên DTO dòng tổng hợp nghỉ phép kế toán.

- [ ] **Step 1: Viết `EmployeeArchiveVisibilityIT.archivedHiddenEverywhere`.** Có hai hồ sơ DA_NGHI: A đã lưu trữ, B chưa lưu trữ. Cả hai có dòng lương ở một kỳ đã duyệt và đơn nghỉ đã duyệt trong tháng nghỉ việc.
  - Các endpoint sau trả B và không trả A: `GET /api/employees`, `/api/employees/count`, `/api/employees/export.xlsx` (đọc file, B có, A không), ô chọn người (`/api/employees/search` hoặc endpoint đang dùng `findTop200...`), `/api/departments/{id}/head-candidates` (hoặc endpoint tương ứng), lịch nghỉ tháng nghỉ việc, `/api/leave/balances`.
  - Nhắc việc, Tổng quan và báo cáo quân số không đếm A.
  - Dòng lương của kỳ đã duyệt có cả A và B, A có `archived=true`. Tổng hợp kế toán tháng đó có A, `archived=true`.
  - Một đơn đang chờ với người duyệt được tính theo chuỗi không bao giờ trỏ vào A.
- [ ] **Step 2:** Chạy IT tập trung → FAIL.
- [ ] **Step 3:** Cài các bộ lọc. Với mỗi bộ lọc dùng JPQL, thêm `e.archivedAt is null`. Với SQL viết tay, thêm `e.archived_at is null`. Thêm badge "Lưu trữ" ở `payroll-section.tsx`, màn kỳ lương và màn tổng hợp kế toán (component dùng chung nếu đã có).
- [ ] **Step 4:** Chạy IT tập trung, sau đó toàn bộ backend → PASS. Chạy `npm test`, tsc, lint → PASS.
- [ ] **Step 5:** Commit "Ẩn hồ sơ đã lưu trữ khỏi danh sách, ô chọn, nhắc việc, lịch nghỉ; nhãn Lưu trữ ở bảng lương kỳ cũ".

### Task 3: Nhận làm lại

**Files:**
- Create:
  - changelog `20261030000200_employment_period.xml`: bảng `employment_period` với các cột `id`, `employee_id` (FK), `join_date`, `last_working_day`, `termination_date`, `termination_type`, `termination_reason`, `termination_record_id` (FK, null được), `created_at`, `created_by`. Kèm `employment_period_aud`.
  - `domain/EmploymentPeriod.java` (`@Audited`)
  - `repository/EmploymentPeriodRepository.java`
  - `service/EmployeeRehireService.java`
  - `service/dto/RehireRequestDTO.java`
- Modify:
  - `web/rest/EmployeeResource.java`: `POST /{id}/rehire`.
  - `service/TerminationService.undo`: nếu đã có `employment_period` gắn với `termination_record_id` của bản ghi cần hoàn tác, ném `undoconflict` với lý do "Nhân viên đã được nhận làm lại".
  - `service/EmployeeHistoryService`: thêm mục "Các lần làm việc", và nhãn cho thay đổi `archived_at` (Lưu trữ / Khôi phục).
- Test: `app/src/test/java/vn/doit/hrm/web/rest/EmployeeRehireIT.java`

**Interfaces:**
- Consumes: Task 1 guard; `TerminationService` và bản ghi `TerminationRecord`; logic mở lại tài khoản theo cách `TerminationService.undo` làm (`user.setActivated(true)`, trừ `admin`); `LeaveAccrualService.settle` hoặc job hằng ngày để cộng phép theo ngày vào làm mới.
- Produces: `EmployeeDTO EmployeeRehireService.rehire(Long id, RehireRequestDTO r)`, với `RehireRequestDTO(LocalDate joinDate, Long departmentId, String position, PersonType personType)`. Ba trường cuối không bắt buộc.

- [ ] **Step 1: Viết `EmployeeRehireIT`** (fixed Clock 2026-11-03, `ROLE_HR`):
  - `rehireRestoresActive`: nghỉ với ngày làm việc cuối 15/05/2026, rồi nhận làm lại `joinDate` 03/11/2026.
    - Trạng thái DANG_LAM_VIEC, `joinDate` 03/11/2026, mã giữ nguyên, 4 trường nghỉ việc null.
    - Có một `employment_period` với join cũ, ngày làm việc cuối 15/05/2026 và `termination_record_id` đúng.
    - Tài khoản `activated=true`.
  - `rehireDateBeforeLeaveRejected`: `joinDate` 10/05/2026 → 400 `error.rehiredatebeforeleave`.
  - `rehireRequiresTerminatedNotArchived`: hồ sơ đang làm → 400; hồ sơ đã lưu trữ → 400 `error.employeearchived`.
  - `rehireWithScheduledTerminationRejected` → 400.
  - `undoOldTerminationBlockedAfterRehire`: `DELETE /{id}/termination` (endpoint hoàn tác hiện có) → 400 `error.undoconflict`, message chứa "Nhân viên đã được nhận làm lại".
  - `secondTerminationUndoWorks`: sau khi nhận làm lại, cho nghỉ lần hai rồi hoàn tác → 200, trạng thái DANG_LAM_VIEC.
  - `leaveRestartsFromNewJoinDate`: với NHAN_VIEN, sau khi nhận làm lại và chạy `LeaveAccrualJob` → có CONG_THANG tháng 11/2026. Không có dòng nào cho các tháng 06–10/2026. Mức thâm niên tính từ 03/11/2026: tức 12 nếu không đặt mức riêng.
  - `adminAccountNotTouched`: hồ sơ gắn login `admin` → tài khoản không bị đổi.
  - `historyShowsPeriods`: `GET /api/employees/{id}/history` có mục lần làm việc, và có mục "Lưu trữ" / "Khôi phục" sau khi lưu trữ rồi khôi phục.
- [ ] **Step 2:** Chạy IT tập trung → FAIL.
- [ ] **Step 3:** Cài. Một giao dịch, khóa hồ sơ bằng `findByIdForUpdate`. Cập nhật phòng ban, vị trí, loại hồ sơ khi có giá trị.
- [ ] **Step 4:** Chạy IT tập trung, các IT nghỉ việc và nghỉ phép hiện có → PASS.
- [ ] **Step 5:** Commit "Nhận làm lại: giữ mã, ghi lần làm việc trước, mở lại tài khoản, chặn hoàn tác nghỉ việc cũ".

### Task 4: Xóa hẳn

**Files:**
- Create:
  - changelog `20261030000300_employee_deletion.xml`:
    - `dropNotNullConstraint` trên `payroll_line.employee_id`;
    - bảng `employee_deletion_log` gồm `id`, `deleted_at`, `deleted_by`, `reason varchar(500)`, `employee_code`, `employee_name`, không FK;
    - nếu `payroll_line_aud` tồn tại thì không cần đổi gì.
  - `domain/EmployeeDeletionLog.java` (`@Immutable`)
  - `repository/EmployeeDeletionLogRepository.java`
  - `service/EmployeeDeletionService.java`
  - `service/dto/EmployeeDeleteRequestDTO.java`
  - `web/rest/EmployeeDeletionResource.java`: `DELETE /api/employees/{id}` với body `{confirmCode, reason}`, và `GET /api/admin/employee-deletions`.
- Test:
  - `app/src/test/java/vn/doit/hrm/web/rest/EmployeeDeletionIT.java`
  - `app/src/test/java/vn/doit/hrm/service/EmployeeForeignKeyCoverageIT.java`

**Interfaces:**
- Consumes: Task 1 (đã lưu trữ), Task 3 (`employment_period`).
- Produces:
  - `void EmployeeDeletionService.delete(Long id, String confirmCode, String reason)`.
  - `static final Set<String> HANDLED_TABLES`: tên mọi bảng có FK tới `employee` mà dịch vụ xử lý (xóa hoặc đặt NULL).
  - Kiểm tra login `admin` bằng `SecurityUtils.getCurrentUserLogin()` so với `IdNumbers.SYSTEM_ADMIN_LOGIN`.

- [ ] **Step 1: Viết test.**
  - `EmployeeForeignKeyCoverageIT.everyEmployeeForeignKeyIsHandled`: đọc `information_schema` các FK có `referenced table = 'employee'` (schema public). Bỏ các bảng `_aud`, vì chúng không có FK. Mỗi bảng tìm được phải nằm trong `EmployeeDeletionService.HANDLED_TABLES`.
  - `EmployeeDeletionIT`. Dữ liệu: một hồ sơ X đầy đủ, gồm HĐ, lương, phụ cấp, nơi làm, quyết định, tài liệu có file, đơn nghỉ, OT, sổ phép, nghỉ việc, lần làm việc, phiếu lương đã gửi, nhật ký xem nhạy cảm, tài khoản; X có dòng lương ở kỳ đã duyệt. Đã lưu trữ.
    - `onlySystemAdmin`: `@WithMockUser(username="admin2", authorities="ROLE_ADMIN")` và `ROLE_HR` → 403 `error.deleterequiressystemadmin`.
    - `requiresArchived`: hồ sơ chưa lưu trữ → 400 `error.deletenotarchived`.
    - `blockedByOpenPayroll`: X có dòng trong kỳ chưa duyệt, hoặc bản nháp import đang làm có dòng gắn X → 400 `error.deleteblocked`, và danh sách lý do có kỳ `MM/yyyy`.
    - `confirmMismatch`: sai mã → 400 `error.deleteconfirmmismatch`. Lý do trống → 400.
    - `deleteKeepsApprovedPayroll`, chạy với `username="admin"`:
      - Ghi lại trước khi xóa: tổng tiền mọi cột của kỳ đã duyệt, và bytes của file xuất bảng lương kỳ đó (so theo nội dung các ô).
      - Sau khi xóa: hai thứ trên giống hệt.
      - Dòng lương của X còn, với `employee_id` null và `file_code`/`file_name` như cũ.
    - `deleteRemovesEverythingElse`:
      - Không còn dòng nào có `employee_id = X` trong mọi bảng của `HANDLED_TABLES` thuộc loại "xóa".
      - Không còn dòng `employee_aud` với id X, cũng không còn dòng `_aud` của các bản ghi con đã xóa.
      - File tài liệu không còn trên đĩa.
      - Tài khoản đăng nhập bị xóa.
      - `employee_deletion_log` có một dòng gồm `deleted_by='admin'`, lý do, mã, họ tên; không có cột nào chứa CCCD của X.
    - `deleteNullsReferencesFromOthers`:
      - Y có manager X, phòng P có trưởng phòng X, đơn của Z có `approver_employee_id = X` và `decided_by` là login của X.
      - Sau khi xóa: Y vẫn còn với manager null; P còn với head null; đơn của Z còn, `approver_employee_id` null, `decided_by` giữ nguyên.
    - `deleteRollsBackOnFailure`: dùng `@MockitoSpyBean` cho một repository được gọi ở giữa quá trình xóa (ví dụ `LeaveLedgerEntryRepository`) để ném lỗi → 500. Hồ sơ và mọi bản ghi con còn nguyên; file tài liệu vẫn còn.
    - `adminLinkedProfileNotDeletable`: hồ sơ gắn tài khoản `admin` → 400.
    - `deletionLogOnlyForAdmin`: `GET /api/admin/employee-deletions` với `admin` → 200; với `ROLE_ADMIN` khác → 403.
- [ ] **Step 2:** Chạy IT tập trung cho cả hai class → FAIL.
- [ ] **Step 3:** Cài `delete` trong một `@Transactional`.
  - **Thứ tự:**
    1. kiểm tra quyền và điều kiện;
    2. khóa X;
    3. đặt NULL các tham chiếu từ người khác (`employee.manager_id`, `department.head_id`, `approver_employee_id` của `leave_request` và `overtime_request` của người khác);
    4. đặt NULL `payroll_line.employee_id`;
    5. xóa bản ghi con theo thứ tự FK, từ lá lên gốc;
    6. xóa `_aud` của X và các bản ghi con theo id đã thu thập trước khi xóa;
    7. xóa tài khoản (`users` và các bảng phụ của user, theo cách `UserService.deleteUser` làm);
    8. xóa X;
    9. ghi nhật ký.
  - Đường dẫn file tài liệu được thu thập trước, và chỉ xóa trong `TransactionSynchronization.afterCommit`. Lỗi xóa file chỉ ghi log.
  - Dùng `JdbcTemplate` cho các bước xóa hàng loạt và `_aud`; dùng JPA/repository cho phần còn lại; flush trước bước JDBC.
  - Thêm key i18n.
- [ ] **Step 4:** Chạy IT tập trung, rồi toàn bộ backend → PASS.
- [ ] **Step 5:** Commit "Xóa hẳn nhân viên (chỉ admin): giữ dòng lương đã duyệt, xóa dữ liệu cá nhân và lịch sử thay đổi, nhật ký xóa".

### Task 5: Giao diện và README

**Files:**
- Create:
  - `app/src/main/webapp/app/entities/employee/archive/archived-list.tsx` và `archived-list.spec.tsx`
  - `app/src/main/webapp/app/entities/employee/archive/delete-employee-dialog.tsx` và `delete-employee-dialog.spec.tsx`
  - `app/src/main/webapp/app/entities/employee/detail/rehire-dialog.tsx` và `rehire-dialog.spec.tsx`
- Modify:
  - `entities/employee/index.tsx`: route `luu-tru` trong `PrivateRoute` `WRITE_ROLES`.
  - `shared/layout/sidebar/menu-items.ts`: mục con hoặc liên kết "Nhân viên đã lưu trữ" cho Quản trị/HR.
  - `entities/employee/detail/employee-detail.tsx`:
    - nút "Đưa vào lưu trữ", có hộp xác nhận và ô ghi chú, hiện khi đủ điều kiện;
    - nút "Nhận làm lại", hiện khi Đã nghỉ và chưa lưu trữ;
    - với hồ sơ đã lưu trữ: băng "Hồ sơ đã lưu trữ (dd/MM/yyyy, người)" kèm nút "Khôi phục", và ẩn mọi nút sửa.
  - `entities/employee/employee-list.tsx`: liên kết "Nhân viên đã lưu trữ".
  - `README.md`: mục "### Lưu trữ, khôi phục, nhận làm lại, xóa hẳn" đặt ngay sau mục "Nghỉ việc / hẹn ngày / hủy / hoàn tác".
- Test: các file `.spec.tsx` nêu trên, cùng `employee-detail.spec.tsx`.

**Interfaces:**
- Consumes: REST của Task 1, 3, 4. Kết quả lưu trữ `ArchiveResultDTO` dùng để hiện danh sách "Hãy đổi quản lý/trưởng phòng cho các hồ sơ sau".
- Produces: không có.

- [ ] **Step 1: Viết spec.**
  - `archived-list`: gọi `GET api/employees/archived` khi tìm; mỗi dòng có nút Xem và Khôi phục; nút "Xóa hẳn" chỉ hiện khi `account.login === 'admin'`.
  - `delete-employee-dialog`: nút Xóa bị tắt cho đến khi gõ đúng mã và có lý do; hộp thoại liệt kê "sẽ bị xóa" và "được giữ: dòng lương các kỳ đã duyệt"; gọi `DELETE api/employees/{id}` với body.
  - `rehire-dialog`: ngày `dd/MM/yyyy` bắt buộc; khi server trả 400 `rehiredatebeforeleave` thì hiện câu tiếng Việt.
  - `employee-detail`:
    - Đã nghỉ, chưa lưu trữ: hiện "Đưa vào lưu trữ" và "Nhận làm lại".
    - Đã lưu trữ: có băng lưu trữ, nút Khôi phục, không có nút sửa.
    - Sau khi lưu trữ hồ sơ có người được quản lý: hiện danh sách cần đổi quản lý.
- [ ] **Step 2:** `npx vitest run src/main/webapp/app/entities/employee` → FAIL.
- [ ] **Step 3:** Cài.
- [ ] **Step 4:** Chạy `npm test`, `npx tsc --noEmit`, `npm run lint` → đạt. Sau đó chạy toàn bộ backend một lần, riêng, ở nền → BUILD SUCCESS.
- [ ] **Step 5:** Commit "Giao diện lưu trữ, khôi phục, nhận làm lại, xóa hẳn nhân viên; README".
