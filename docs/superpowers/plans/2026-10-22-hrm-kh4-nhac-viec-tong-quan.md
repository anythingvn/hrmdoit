# DOIT HRM – Kế hoạch 4: Nhắc việc, Tổng quan, báo cáo

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hệ thống tự nhắc hết hạn HĐ, hết thử việc, đến hạn xét lương, sinh nhật trên trang Tổng quan và gửi email 7:30 mỗi ngày làm việc; việc đã xử lý thì dừng nhắc. Có trang Tổng quan (chỉ số, biến động) và báo cáo Excel.

**Architecture:** Lõi `core` thêm ba phần:

- lịch làm việc cấu hình được;
- danh sách sự kiện sắp tới (`UpcomingEvents`);
- tùy chọn xuất báo cáo không có chi phí lương.

App thêm package `vn.doit.hrm.service.reminder` gồm:

- dựng `EmployeeSnapshot` từ hồ sơ, HĐ, điều chỉnh lương;
- `reminder_log`;
- job gửi email (`JavaMailSender` gửi đồng bộ để biết gửi được hay không);
- lịch làm việc và ngày lễ.

Ngoài ra có `service/dashboard` (chỉ số, biến động, báo cáo). Frontend thêm trang Tổng quan, Báo cáo, Lịch làm việc.

**Tech Stack:** JHipster 9.4.0 / Spring Boot 4.1 / Java 21, React 19 + Vite/Vitest + react-bootstrap, PostgreSQL 16 (Testcontainers), Liquibase, Envers, Apache POI (đã có), `spring-boot-starter-mail` (đã có).

**Spec:** `docs/superpowers/specs/2026-10-22-hrm-kh4-nhac-viec-tong-quan-design.md` (cùng spec đợt 1 §5.4–5.6).

## Global Constraints

- Mọi ràng buộc chung KH1–KH3 vẫn áp dụng:
  - tiếng Việt; `dd/MM/yyyy`; tiền `1.234.567`; múi giờ `Asia/Ho_Chi_Minh`; vùng bấm ≥ 44px;
  - `core/` không phụ thuộc Spring, Java 17;
  - quyền kiểm ở backend;
  - changeset Liquibase chỉ thêm mới;
  - lỗi nghiệp vụ ném `EmployeeRuleException(message, key)` — `ExceptionTranslator` tự trả 400, resource **không** bọc try/catch.
- **Luật/lịch đổi không tác động ngược:** cấu hình mới chỉ ảnh hưởng các lần nhắc sau; `reminder_log` không bao giờ bị xóa hay tính lại.
- **Người nhận [Chốt]:**
  - HR, Kế toán, Quản trị (tài khoản đang hoạt động có vai trò này) nhận **mọi** nhắc;
  - quản lý trực tiếp (`Employee.manager`) nhận nhắc về nhân viên của mình, ở các loại mà `ReminderPolicy` có `MANAGER`;
  - email: của tài khoản; trống thì lấy email hồ sơ gắn tài khoản; quản lý dùng email hồ sơ.
- **Email không chứa số tiền** (thông điệp của lõi đã không có số tiền; app không thêm).
- **Ngày làm việc mặc định:** `MONDAY..FRIDAY`; ngày lễ theo năm.
- **Lịch chạy:** cron `0 30 7 * * *` `Asia/Ho_Chi_Minh`.
  - Hôm nay không phải ngày làm việc thì bỏ qua.
  - Khởi động sau 7:30 của một ngày làm việc mà chưa có lần chạy tự động thành công hôm đó thì chạy bù.
  - Chỉ ghi `reminder_log` **sau khi** gửi thành công tới người nhận đó.
- **SMTP coi là chưa cấu hình** khi `spring.mail.host` trống hoặc bằng `localhost`. Khi đó không gửi, không ghi log; lần chạy ghi lỗi "Chưa cấu hình SMTP".
- **Ô chi phí lương và sheet chi phí** chỉ cho `ROLE_ADMIN`, `ROLE_KE_TOAN`. API không trả số này cho vai trò khác.
- **Tổng nhân sự / biến động:** chỉ `PersonType.NHAN_VIEN`; chi phí lương gồm mọi dòng lương.

## Review Focus

1. **Quản lý trực tiếp cũng là HR** (hoặc trùng email với tài khoản HR): chỉ nhận **một** email mỗi ngày, không trùng mục (`ReminderDigest.byEmail` gộp theo email, phân biệt hoa thường). → Task 4.
2. **Gửi lỗi một phần:** gửi được cho A, lỗi với B. Chỉ các mục của A được ghi `reminder_log`; ngày làm việc sau B nhận lại, A không nhận trùng. → Task 4.
3. **Không lệch ngày quanh nửa đêm:** khởi động lúc 23:50 hay 00:10, "hôm nay" luôn theo `Asia/Ho_Chi_Minh` (dùng `Clock` của app), không theo giờ máy. → Task 4.
4. **Nhân viên nghỉ việc vẫn còn HĐ có thời hạn:** không còn nhắc trên Tổng quan lẫn email từ ngày nghỉ. → Task 4.
5. **HR gọi thẳng `/api/dashboard`:** không có trường chi phí trong JSON (trường vắng hẳn, không chỉ ẩn trên giao diện). → Task 5.

---

### Task 1: Lõi: lịch làm việc cấu hình, sự kiện sắp tới, báo cáo không có chi phí

**Files:**
- Modify:
  - `core/src/main/java/vn/doit/hr/reminder/WorkCalendar.java`: thêm `static WorkCalendar of(Set<DayOfWeek> workingDays, Set<LocalDate> holidays)`;
  - `core/src/main/java/vn/doit/hr/report/ReportExcelWriter.java`: thêm `write(HrReport, OutputStream, boolean includePayrollCost)`; bản cũ gọi với `true`.
- Create: `core/src/main/java/vn/doit/hr/reminder/UpcomingEvents.java`.
- Test: `core/src/test/java/vn/doit/hr/reminder/UpcomingEventsTest.java`, `WorkCalendarTest.java`; thêm 1 test trong `ReportExcelWriterTest`.

**Interfaces:**
- Produces:
  - `record UpcomingEvents.Event(ReminderType type, String employeeCode, String employeeName, LocalDate eventDate, long daysLeft, boolean urgent, String message)`.
  - `static List<Event> UpcomingEvents.list(LocalDate today, List<EmployeeSnapshot> employees, int horizonDays, ReminderPolicy policy)`:
    - sự kiện sắp tới trong `[today, today + horizonDays]`: hết hạn HĐ xác định thời hạn gần nhất, hết thử việc chưa có kết quả, đến hạn xét lương, sinh nhật (lần tới);
    - sự kiện đã quá hạn còn trong khoảng nhắc quá hạn của `policy` (`CONTRACT_OVERDUE`, `PROBATION_RESULT_MISSING`, `SALARY_REVIEW_OVERDUE`), `urgent = true`;
    - bỏ qua snapshot `active = false`;
    - sắp xếp: quá hạn trước, rồi theo `eventDate`;
    - thông điệp dùng lại cách viết của `ReminderEngine`: tách các hàm tạo thông điệp trong `ReminderEngine` thành package-private để dùng chung, không chép lại.

- [ ] **Step 1: Viết test.**
  - `WorkCalendarTest`:
    - với `of(MON..FRI, {2026-09-02})`: thứ Bảy 2026-10-24 không là ngày làm việc; `onOrBefore(2026-10-24)` = 2026-10-23; `onOrBefore(2026-09-02)` = 2026-09-01.
  - `UpcomingEventsTest`, `today` = 2026-10-22:
    - HĐLĐ hết hạn 2026-11-20 → 1 sự kiện `CONTRACT_EXPIRY`, `daysLeft` 29; horizon 20 → không có;
    - ký HĐ mới (HĐ gần nhất không xác định thời hạn) → không có;
    - HĐ hết hạn 2026-10-12, nhân viên còn làm → `CONTRACT_OVERDUE`, `urgent`, đứng đầu danh sách;
    - thử việc kết thúc 2026-10-30, chưa có kết quả → `PROBATION_END`; đã có kết quả → không có;
    - xét lương: điều chỉnh gần nhất 2025-11-01, chu kỳ 12 → `SALARY_REVIEW` 2026-11-01; `nextSalaryReviewOverride` 2027-02-01 → không có trong 30 ngày;
    - sinh nhật 1990-11-05 → `BIRTHDAY` 2026-11-05;
    - `active = false` → không có gì.
  - `ReportExcelWriterTest.withoutPayrollCost`: `write(report, out, false)` → 3 sheet, không có sheet "Chi phí lương", trong sheet "Tổng hợp" không có ô chứa "chi phí".
- [ ] **Step 2:** `mvn -pl core test`. Expected: FAIL (biên dịch).
- [ ] **Step 3:** Cài đặt. Giữ `WorkCalendar.mondayToSaturday` (test lõi cũ còn dùng).
- [ ] **Step 4:** `mvn -pl core test`. Expected: PASS toàn bộ.
- [ ] **Step 5:** Commit `"Lõi: lịch làm việc cấu hình, sự kiện sắp tới, báo cáo không có chi phí"`.

### Task 2: Lịch làm việc và ngày lễ

**Files:**
- Create:
  - changelog `20261023000100_work_calendar.xml`:
    - `work_calendar_setting` (`id` = 1, `working_days` varchar(60), nạp sẵn `MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY`);
    - `holiday` (`id`, `day` date unique, `name` varchar(200));
    - các bảng `_aud`.
  - domain `WorkCalendarSetting`, `Holiday`, các repository.
  - `service/reminder/WorkCalendarService.java`, `web/rest/WorkCalendarResource.java`.
  - frontend `modules/work-calendar/work-calendar.tsx` (+ spec): tab "Lịch làm việc" `/danh-muc/lich-lam-viec` trong `entities/routes.tsx`.
- Test: `WorkCalendarResourceIT`, Vitest.

**Interfaces:**
- Produces:
  - `WorkCalendar WorkCalendarService.calendar()`, dựng bằng `WorkCalendar.of(...)` từ cơ sở dữ liệu, không cache;
  - `boolean WorkCalendarService.isWorkingDay(LocalDate)`.
- API:
  - `GET /api/work-calendar` (ADMIN, HR, KE_TOAN) → `{workingDays: ["MONDAY",...]}`.
  - `PUT /api/work-calendar` (ADMIN) → cùng dạng. Trống → 400 `workingdaysrequired`.
  - `GET /api/holidays?year=2026` (ADMIN, HR, KE_TOAN) → `[{id, day, name}]` theo ngày.
  - `POST /api/holidays`, `PUT /api/holidays/{id}`, `DELETE /api/holidays/{id}` (ADMIN, HR). Ngày trùng → 400 `holidayexists`.
  - `POST /api/holidays/copy?fromYear=2026&toYear=2027` (ADMIN, HR): chép cùng ngày/tháng, bỏ qua ngày đã có; trả danh sách năm đích. Ngày 29/02 sang năm không nhuận thì bỏ qua.

- [ ] **Step 1: Viết test.**
  - Mặc định thứ Hai–thứ Sáu.
  - Thêm 2026-09-02 "Quốc khánh":
    - `isWorkingDay(2026-09-02)` = false;
    - thêm lại → 400 `holidayexists`;
    - chép 2026 → 2027 có 2027-09-02.
  - Quyền:
    - HR `PUT /api/work-calendar` → 403;
    - BGD `GET /api/holidays` → 403.
  - Vitest:
    - tick ô "Thứ Bảy" rồi lưu → gửi đủ 6 ngày;
    - thêm ngày lễ với ô ngày `dd/MM/yyyy` → `POST` với `day` ISO;
    - nút "Chép từ năm trước".
- [ ] **Step 2:** Chạy. Expected: FAIL.
- [ ] **Step 3:** Cài đặt.
- [ ] **Step 4:** Chạy IT + `npm test`. Expected: PASS.
- [ ] **Step 5:** Commit `"Lịch làm việc và ngày lễ theo năm"`.

### Task 3: Ghi kết quả thử việc, hẹn xét lại lương

**Files:**
- Modify:
  - changelog `20261023000200_probation_result.xml`: `employment_contract` (+ `_aud`) thêm `probation_result` varchar(20), `probation_result_date` date;
  - `domain/EmploymentContract.java`, `service/dto/EmploymentContractDTO.java`: các trường `probationResult` (enum mới `ProbationResult { DAT, KHONG_DAT }`), `probationResultDate`;
  - `EmploymentContractService.applyRules`: có kết quả mà HĐ không phải `HDTV` → 400 `probationresultnothdtv`; có kết quả mà thiếu ngày → mặc định hôm nay;
  - frontend:
    - `contract-dialog.tsx`: với HĐ thử việc thêm ô "Kết quả thử việc" (Chưa có / Đạt / Không đạt) và "Ngày ghi kết quả";
    - `salary-change-dialog.tsx`: thêm ô "Hẹn xét lại ngày" (`nextReviewOverride`);
    - `detail-api.ts`: kiểu dữ liệu, câu lỗi.
- Test: thêm vào `EmploymentContractResourceIT` (hoặc IT mới `ProbationResultIT`), Vitest các dialog.

**Interfaces:**
- Produces:
  - JSON HĐ có `probationResult`, `probationResultDate`; `PATCH /api/employment-contracts/{id}` nhận hai trường này (quyền như hiện có: ADMIN, HR).
  - `PATCH /api/salary-changes/{id}` với `nextReviewOverride` (đã có).

- [ ] **Step 1: Viết test.**
  - `PATCH` HĐTV `{probationResult: "DAT"}` → lưu, ngày = hôm nay.
  - `PATCH` HĐLĐ `{probationResult: "DAT"}` → 400 `probationresultnothdtv`.
  - Kế toán `PATCH` → 403.
  - Vitest:
    - dialog HĐ thử việc gửi `probationResult` / `probationResultDate`;
    - dialog điều chỉnh lương gửi `nextReviewOverride` ISO.
- [ ] **Step 2:** Chạy. Expected: FAIL.
- [ ] **Step 3:** Cài đặt.
- [ ] **Step 4:** Chạy `EmploymentContractResourceIT`, `SalaryChangeResourceIT`, IT mới, `npm test`. Expected: PASS.
- [ ] **Step 5:** Commit `"Ghi kết quả thử việc và hẹn xét lại lương"`.

### Task 4: Nhắc việc: dữ liệu, danh sách sắp tới, email hằng ngày

**Files:**
- Create:
  - changelog `20261023000300_reminder.xml`:
    - `reminder_log` (`event_key` varchar(160), `step` int, `sent_at`; PK `event_key` + `step`);
    - `reminder_run` (`id`, `run_date` date, `started_at`, `finished_at`, `automatic` boolean, `recipients` int, `items` int, `error` varchar(1000)).
  - `service/reminder/ReminderSnapshotService.java`, `ReminderRecipients.java`, `ReminderMailer.java`, `ReminderJob.java`, `JpaReminderLog.java`; domain + repository cho `reminder_log`, `reminder_run`.
  - `web/rest/ReminderResource.java`.
  - `service/reminder/dto/UpcomingDTO.java`, `ReminderStatusDTO.java`.
- Test: `ReminderSnapshotServiceIT`, `ReminderJobIT` (`@MockitoBean JavaMailSender`, `spring.mail.host` = `smtp.test` qua `DynamicPropertySource`, `@MockitoBean Clock` cố định), `ReminderResourceIT`.

**Interfaces:**
- Consumes: `WorkCalendarService.calendar()` (Task 2), trường thử việc (Task 3), `UpcomingEvents.list` (Task 1), `ReminderEngine`, `ReminderDigest`.
- Produces:
  - `List<EmployeeSnapshot> ReminderSnapshotService.activeEmployees()`, chỉ `NHAN_VIEN` đang làm việc:
    - `contracts`: các HĐLĐ đã ký (`DA_KY`, `DA_KET_THUC`), `Contract(contractNo ?? "", start, end)`;
    - `probationEnd`: ngày kết thúc HĐTV gần nhất; `probationResultRecorded` = có `probationResult`;
    - `lastSalaryChange`, `salaryReviewCycleMonths`, `nextSalaryReviewOverride`: lấy từ lần điều chỉnh lương gần nhất.
  - `Map<String, Long> ReminderSnapshotService.idsByCode()`: mã → id hồ sơ, để tạo liên kết.
  - `List<String> ReminderRecipients.emails(Recipient role, String employeeCode)`:
    - `HR`, `DIRECTOR` → email mọi tài khoản đang hoạt động có `ROLE_HR`, `ROLE_KE_TOAN` hoặc `ROLE_ADMIN`;
    - `MANAGER` → email hồ sơ của `manager`;
    - `EMPLOYEE` → rỗng.
    - `List<String> missing()`: tên/tài khoản không có email.
  - `ReminderJob.RunResult ReminderJob.run(boolean automatic)`: `record RunResult(int recipients, int items, String error)`. Các bước:
    1. hôm nay = `LocalDate.now(clock)`; không phải ngày làm việc → không làm gì, trả `error = "Không phải ngày làm việc"`;
    2. `ReminderEngine(ReminderPolicy.defaults(), calendar).run(today, snapshots, log)` → `ReminderDigest.byEmail`;
    3. với từng email: gửi qua `ReminderMailer.send(to, subject, text)` (đồng bộ, ném lỗi khi thất bại); thành công thì ghi `reminder_log` cho các mục của email đó; lỗi thì ghi vào `error`, không ghi log, sang email kế tiếp;
    4. ghi `reminder_run`.
  - `@Scheduled(cron = "0 30 7 * * *", zone = "Asia/Ho_Chi_Minh") void scheduled()`.
  - `@EventListener(ApplicationReadyEvent.class) void catchUp()`: sau 7:30, ngày làm việc, chưa có `reminder_run` tự động hôm nay mà không lỗi → `run(true)`.
  - `ReminderMailer.configured()`: `spring.mail.host` không trống và khác `localhost`.
  - `ReminderMailer.send(...)`: dùng `JavaMailSender` + `MimeMessageHelper` (UTF-8, text), người gửi `jhipster.mail.from`.
  - API:
    - `GET /api/reminders/upcoming?type=` (ADMIN, HR, KE_TOAN, BGD) → `[UpcomingDTO(type, typeLabel, employeeId, employeeCode, employeeName, eventDate, daysLeft, urgent, message, contractId?, salaryChangeId?)]`, horizon 30 ngày;
    - `GET /api/admin/reminders/status` (ADMIN) → `ReminderStatusDTO(mailConfigured, lastRun{runDate, startedAt, automatic, recipients, items, error}, missingRecipients[])`;
    - `POST /api/admin/reminders/test-send` (ADMIN): gửi bản tổng hợp hôm nay (mọi mục `UpcomingEvents` của 30 ngày, hoặc "Không có nhắc việc") tới email của chính Quản trị; không ghi log; chưa cấu hình SMTP → 400 `mailnotconfigured`.

- [ ] **Step 1: Viết test**, `Clock` cố định 2026-10-22 07:30 (thứ Năm):
  - Snapshot:
    - HĐLĐ hết hạn 2026-12-06 (45 ngày) → `upcoming` không có (ngoài 30 ngày), nhưng `run()` gửi một email nhắc mốc 45;
    - nhân viên đã nghỉ → không có trong snapshot (Review Focus 4).
  - Người nhận:
    - tài khoản HR, Kế toán, Quản trị mỗi người nhận 1 email;
    - quản lý A chỉ nhận mục của nhân viên A;
    - quản lý trùng email với tài khoản HR chỉ nhận 1 email (Review Focus 1).
  - Nội dung không chứa chuỗi số tiền của lương nhân viên trong dữ liệu test.
  - Gửi lỗi với B (mock ném `MailSendException` cho địa chỉ B):
    - log chỉ có mục của các người khác;
    - chạy lại cùng ngày: chỉ B được gửi (Review Focus 2).
  - Hôm sau là thứ Bảy (`Clock` 2026-10-24) → không gửi.
  - `spring.mail.host` = `localhost` (IT riêng hoặc test đơn vị `ReminderMailer`) → `configured()` = false; `run()` không gửi, ghi lỗi "Chưa cấu hình SMTP".
  - `catchUp()`:
    - `Clock` 2026-10-22 09:00 chưa có lần chạy → chạy;
    - đã có lần chạy tự động không lỗi → không chạy;
    - `Clock` 2026-10-22 23:50+07 → "hôm nay" là 22/10 (Review Focus 3).
  - API: KE_TOAN, BGD gọi `upcoming` → 200; NHAN_VIEN → 403; HR gọi `status` → 403; `test-send` gửi tới email Quản trị.
- [ ] **Step 2:** Chạy. Expected: FAIL.
- [ ] **Step 3:** Cài đặt. `reminder_log`: bảng có khóa chính kép; repository dùng `JdbcTemplate` (insert … on conflict do nothing), không cần entity JPA.
- [ ] **Step 4:** Chạy các IT + toàn bộ backend. Expected: PASS.
- [ ] **Step 5:** Commit `"Nhắc việc: dữ liệu, danh sách sắp tới, email hằng ngày"`.

### Task 5: Trang Tổng quan (API) và báo cáo Excel

**Files:**
- Create: `service/dashboard/DashboardService.java`, `service/dashboard/HrReportService.java`, `service/dashboard/dto/DashboardDTO.java`, `web/rest/DashboardResource.java`.
- Test: `DashboardResourceIT` (dùng `PayrollTestData` và `PayrollFlow` của KH3 để có một kỳ lương).

**Interfaces:**
- Consumes: `PayrollPeriodService.officialVersion`, `PayrollLineRecordRepository` (KH3), `HeadcountReport`, `HrReport`, `ReportExcelWriter.write(..., boolean)` (Task 1).
- Produces:
  - `GET /api/dashboard` (ADMIN, HR, KE_TOAN, BGD) → `DashboardDTO`, dùng `@JsonInclude(NON_NULL)` cho `payrollCost`:
    - `headcount`: NHAN_VIEN đang làm;
    - `onProbation`: HĐTV đang hiệu lực hôm nay;
    - `contractsExpiring30`: HĐLĐ đã ký có `endDate` trong `[hôm nay, hôm nay + 30]`, nhân viên đang làm;
    - `payrollCost {period, status, totalCost}`: kỳ lương lớn nhất có phiên bản, theo phiên bản chính thức; **chỉ** ADMIN, KE_TOAN, vai trò khác thì trường vắng;
    - `movement[] {month "yyyy-MM", hires, leavers, closing}`: 12 tháng gần nhất kết thúc ở tháng hiện tại, từ `HeadcountReport.monthly`.
  - `GET /api/reports/hr.xlsx?from=yyyy-MM&to=yyyy-MM&groupBy=DEPARTMENT|POSITION` (ADMIN, HR, KE_TOAN, BGD):
    - mặc định 12 tháng gần nhất và `DEPARTMENT`;
    - `from > to` hoặc khoảng quá 36 tháng → 400 `reportrangeinvalid`;
    - `includePayrollCost` = ADMIN hoặc KE_TOAN;
    - chi phí: mỗi kỳ trong khoảng → dòng lương của phiên bản chính thức, dựng lại thành `vn.doit.hr.payroll.PayrollLine` từ `amounts` JSON; nhóm theo phòng ban (tra hồ sơ theo id) hoặc vị trí (trong file);
    - `EmploymentRecord`: chỉ NHAN_VIEN; `hireDate = joinDate`; `terminationDate = lastWorkingDay` khi `DA_NGHI`; `exitType` theo `TerminationType` (cùng tên); phòng ban, vị trí hiện tại.

- [ ] **Step 1: Viết test.** Dữ liệu: 3 nhân viên, 1 đang thử việc, 1 có HĐ hết hạn sau 20 ngày, 1 người dịch vụ; một kỳ lương 2026-06 xác nhận từ file mẫu ẩn danh.
  - `KE_TOAN` `GET /api/dashboard`:
    - `headcount` đúng (không tính người dịch vụ);
    - `onProbation` = 1, `contractsExpiring30` = 1;
    - `payrollCost.totalCost` = 245709000, `payrollCost.period` = `2026-06`;
    - `movement.length()` = 12.
  - `HR` và `BGD` `GET /api/dashboard` → `$.payrollCost` không tồn tại (Review Focus 5).
  - `KE_TOAN` tải báo cáo `from=2026-01&to=2026-12`:
    - mở bằng POI có 4 sheet;
    - tổng cột "Tổng chi phí" ở sheet "Chi phí lương" = 245709000.
  - `HR` tải → 3 sheet, không có ô nào chứa "245.709.000" hoặc 245709000.
  - `from=2026-12&to=2026-01` → 400 `reportrangeinvalid`.
  - `NHAN_VIEN` → 403.
- [ ] **Step 2:** Chạy. Expected: FAIL.
- [ ] **Step 3:** Cài đặt.
- [ ] **Step 4:** Chạy IT + toàn bộ backend. Expected: PASS.
- [ ] **Step 5:** Commit `"Trang Tổng quan (API) và báo cáo Excel"`.

### Task 6: Giao diện Tổng quan, Nhắc việc, Báo cáo, trạng thái email

**Files:**
- Create:
  - `modules/dashboard/dashboard.tsx` (+ spec): ô chỉ số, khối "Nhắc việc sắp tới", biểu đồ biến động;
  - `modules/dashboard/movement-chart.tsx`: SVG tự vẽ, không thêm thư viện;
  - `modules/dashboard/reminder-list.tsx` (+ spec);
  - `modules/dashboard/quick-actions.tsx`: hộp "Ghi kết quả thử việc" (PATCH HĐ), "Hẹn xét lại" (PATCH điều chỉnh lương), "Thêm điều chỉnh lương" (mở hồ sơ ở tab Hợp đồng & lương);
  - `modules/reports/hr-report.tsx` (+ spec);
  - `modules/work-calendar/reminder-status.tsx` (+ spec): trạng thái email cho Quản trị, đặt trong trang Lịch làm việc.
- Modify:
  - `routes.tsx`: `/tong-quan`, `/bao-cao` cho ADMIN, HR, KE_TOAN, BGD;
  - `home-redirect.tsx`: vai trò nghiệp vụ → `/tong-quan` (spec của trang này cập nhật theo);
  - `menu-items.ts` (+ spec): `tong-quan` "Tổng quan" (đứng đầu, ADMIN/HR/KE_TOAN/BGD), `bao-cao` "Báo cáo" (cùng vai trò, sau "Bảng lương").
- Test: Vitest cho từng component.

**Interfaces:**
- Consumes: API Task 2–5.

- [ ] **Step 1:** Trước khi viết biểu đồ, tải skill `dataviz` và theo hướng dẫn màu, trục, chú thích của skill đó.
- [ ] **Step 2: Viết test (Vitest).**
  - `menuFor(['ROLE_HR'])` bắt đầu bằng `tong-quan` và có `bao-cao`; `menuFor(['ROLE_NHAN_VIEN'])` không có hai mục này.
  - `HomeRedirect`: HR → `/tong-quan`; chỉ NHAN_VIEN → `/ho-so-cua-toi`.
  - `Dashboard`:
    - Kế toán thấy ô "Chi phí lương kỳ 06/2026" `245.709.000`;
    - dữ liệu không có `payrollCost` → không có ô đó.
  - `ReminderList`:
    - lọc theo loại gọi `upcoming?type=PROBATION_END`;
    - mục quá hạn có nhãn "Quá hạn N ngày";
    - nút "Ghi kết quả thử việc" mở hộp, lưu → `PATCH api/employment-contracts/{contractId}` `{id, probationResult: 'DAT', probationResultDate}`;
    - Ban giám đốc không thấy các nút xử lý.
  - `HrReport`: chọn từ/đến tháng rồi bấm tải → gọi `downloadFile('api/reports/hr.xlsx', ..., {from, to, groupBy})`.
  - `ReminderStatus`:
    - chưa cấu hình SMTP → cảnh báo;
    - hiện lần gửi gần nhất;
    - "Gửi thử ngay" → `POST api/admin/reminders/test-send`.
- [ ] **Step 3:** Chạy. Expected: FAIL.
- [ ] **Step 4:** Cài đặt.
- [ ] **Step 5:** `npm test`, `tsc`, `lint`, toàn bộ backend. Expected: PASS.
- [ ] **Step 6:** Commit `"Giao diện Tổng quan, Nhắc việc, Báo cáo, trạng thái email"`.

## Kiểm tra cuối kế hoạch

- [ ] Backend `verify`, `npm test`, `tsc`, `lint`, `docker compose build` đều đạt.
- [ ] End-to-end trên stack `docker compose` tạm, thêm container SMTP giả `axllent/mailpit` (ghim phiên bản đã ra ≥ 2 tuần) **chỉ trong lần kiểm này**, không thêm vào `docker-compose.yml`:
  - tạo nhân viên có HĐ hết hạn sau 30 ngày và quản lý có email;
  - Quản trị bấm "Gửi thử ngay";
  - gọi chạy job (qua endpoint test-send và kiểm `reminder_run` sau khi khởi động lại app sau 7:30 với `Clock` thật, nếu thời điểm kiểm cho phép);
  - xem email trong mailpit: người nhận đúng, không có số tiền;
  - Tổng quan hiện nhắc việc;
  - tải báo cáo bằng tài khoản Kế toán và HR.

  Gỡ stack sau khi kiểm.
- [ ] README: mục "Nhắc việc và email" (người nhận, giờ gửi, cấu hình SMTP, lịch làm việc, ngày lễ, dừng nhắc), "Tổng quan và báo cáo".
