# Chấm công – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trưởng nhóm, trưởng nhóm onsite và HR gõ giờ vào/ra. Hệ thống tính công theo ngưỡng muộn và về sớm. HR/Quản trị chốt tháng. Kế toán tải file bảng lương có sẵn ngày công, và khi nhập bảng lương hệ thống cảnh báo các dòng lệch với số đã chốt.

**Architecture:**
- **Lần chấm:** mỗi lần chấm là một dòng `attendance_punch` (vào/ra, nguồn `GO_TAY`, sau này thêm `APP`). Đánh dấu vắng là `attendance_mark`.
- **Tính công:** công một ngày được tính khi đọc, không lưu sẵn. Hàm tính nằm ở lõi thuần (`vn.doit.hr.attendance.DayCredit`). Đầu vào gồm các lần chấm, bộ giờ có hiệu lực ngày đó (của công ty hoặc của khách hàng onsite), và các ô tự điền (đơn nghỉ, công tác, lễ, ngày nghỉ).
- **Chốt tháng:** ghi `attendance_month` và một ảnh chụp `attendance_snapshot` cho mỗi người. Kế toán, file bảng lương và phần đối chiếu chỉ đọc ảnh chụp này.

**Tech Stack:** Java 21 / Spring Boot 4, PostgreSQL + Liquibase + Envers, React 19 + Vitest + react-bootstrap, Apache POI (đã có).

**Spec:** `docs/superpowers/specs/2026-10-23-hrm-cham-cong-design.md`

## Global Constraints

### Giờ làm và quy tắc tính công
- **Giờ mặc định của công ty:**
  - Giờ làm 08:00–17:00, nghỉ trưa 12:00–13:00.
  - Muộn cho phép 5 phút.
  - Mốc nửa ngày 90 phút.
  - Mỗi bộ giờ có `effective_from`; ngày D dùng bộ giờ có `effective_from` lớn nhất nhưng không sau D.
  - Không thêm hoặc sửa bộ giờ có `effective_from` rơi vào một tháng đã chốt; trả 400 `attendancescheduleclosedmonth`.
- **Bộ giờ của khách hàng:**
  - `WorkSite` loại `KHACH_HANG` có thể có bộ giờ riêng: giờ vào, giờ ra, nghỉ trưa, cũng có `effective_from`.
  - Phút muộn cho phép và mốc nửa ngày luôn lấy theo công ty.
  - Khách hàng chưa có bộ giờ riêng thì dùng giờ của công ty.
  - Ngày D tính là onsite ở khách hàng X khi có `WorkAssignment` loại `KHACH_HANG`, `site` = X, và `fromDate ≤ D ≤ toDate` (`toDate` null nghĩa là còn hiệu lực).
  - Phân công loại `TU_XA` dùng giờ công ty và có nhãn "Từ xa".
  - Phân công loại `CONG_TAC` cho 1 công, không cần gõ giờ.
- **Tính công một ngày làm việc:**
  - Giờ vào là lần VÀO sớm nhất; giờ ra là lần RA muộn nhất.
  - Đi muộn ≤ mốc 90 phút: 1 công, vẫn ghi số phút muộn. Muộn quá 90 phút: 0,5 công.
  - Về sớm tính đối xứng với đi muộn.
  - Vừa muộn quá mốc vừa về sớm quá mốc, hoặc số giờ làm trong ca < nửa số giờ chuẩn: 0 công, trạng thái vắng.
  - Thiếu giờ vào hoặc giờ ra: trạng thái `THIEU_GIO`, chưa có công.
  - Không có lần chấm, không có ô tự điền, và ngày đã qua: trạng thái `CHUA_NHAP`.
  - Đánh dấu vắng: trạng thái `VANG`, 0 công.
- **Đơn nghỉ nửa ngày:**
  - Nghỉ `SANG`: buổi làm bắt đầu từ cuối giờ nghỉ trưa.
  - Nghỉ `CHIEU`: buổi làm kết thúc ở đầu giờ nghỉ trưa.
  - Có đủ giờ vào và giờ ra của buổi còn lại thì được 0,5 công đi làm cộng 0,5 công nghỉ. Đi muộn hoặc về sớm trong buổi đó chỉ ghi lại, không trừ thêm công.
- **Công thực tế:** gồm công đi làm, công tác, nghỉ có lương (`PHEP_NAM`, `NGHI_BU`, `VIEC_RIENG`) và ngày lễ.
  - Không tính: `OM`, `KHONG_LUONG`, vắng, `CHUA_NHAP`, `THIEU_GIO`.
- **Công chuẩn:** số ngày làm việc trong tuần của tháng theo `WorkCalendarService`, kể cả ngày lễ, tính cho cả tháng kể cả với người vào làm hoặc nghỉ việc giữa tháng.
  - Ngày trước `joinDate` hoặc sau `lastWorkingDay` để trống, không tính.

### Đối tượng và quyền
- **Đối tượng chấm công:** chỉ `PersonType.NHAN_VIEN`, chưa lưu trữ. Thực tập sinh, HĐ dịch vụ và hồ sơ đã lưu trữ không xuất hiện.
- **Quyền gõ:**
  - Trưởng nhóm (là `manager` của nhân viên) gõ cho người trong nhóm mình.
  - Trưởng nhóm onsite (danh sách gán theo từng `WorkSite`) gõ cho người onsite ở khách hàng đó vào đúng ngày đó.
  - `ROLE_HR` và `ROLE_ADMIN` gõ cho cả công ty.
  - Không ai gõ cho chính mình: 400 `attendanceself`.
  - Không gõ cho giờ chưa tới: 400 `attendancefuture`.
  - Không gõ vào tháng đã chốt: 400 `attendancemonthclosed`.
  - Hồ sơ đã lưu trữ trả `employeearchived` (guard hiện có).
  - Người không có quyền nhận 403.
- **Quyền xem:**
  - Trưởng phòng: phòng mình. Giám đốc (`ROLE_BGD`): toàn công ty. `ROLE_KE_TOAN`: bảng tháng đã chốt.
  - Nhân viên: bảng tháng của mình.
  - Tất cả đều chỉ đọc.
- **Chốt và mở lại:**
  - `ROLE_HR` và `ROLE_ADMIN` được chốt và mở lại.
  - Chốt khi còn ô đỏ phải gửi `force=true`; nếu không, trả 400 `attendanceunresolved` kèm danh sách ô đỏ.
  - Mở lại cần lý do (`reason`). Chỉ mở lại được khi chưa có kỳ lương cho tháng đó, hoặc kỳ lương đang `NHAP`. Nếu kỳ `DA_DUYET` thì trả 400 `attendancepayrollapproved`.
  - Mỗi lần chốt hoặc mở lại ghi một dòng `attendance_month_event`.
  - Dữ liệu nguồn đổi sau khi chốt không bị chặn. Bảng tháng đánh dấu `changedAfterClose` cho người có số tính lại khác ảnh chụp.

### Bảng lương
- **File có ngày công:**
  - Dùng `PayrollTemplateWriter` hiện có, điền sẵn mã, họ tên, vị trí, ngày ký HĐLĐ và hai cột ngày công từ ảnh chụp.
  - Thực tập sinh và HĐ dịch vụ cũng có dòng nhưng để trống cột ngày công.
  - Chỉ tải được khi tháng đã chốt; chưa chốt thì trả 400 `attendancenotclosed`.
- **Đối chiếu khi nhập:**
  - Tháng đã chốt: dòng có "Ngày công thực tế" hoặc "Số ngày công chuẩn" khác ảnh chụp nhận một cảnh báo WARNING, nội dung "File ghi X, bảng công đã chốt Y".
  - Tháng chưa chốt: một cảnh báo chung "Bảng công tháng này chưa chốt".
  - Duyệt kỳ lương khi công chưa chốt vẫn được, chỉ cảnh báo.

### Ràng buộc chung của dự án
- **Liquibase:** chỉ thêm, file mới dạng `20261031…`. Các entity `@Audited` cần bảng `_aud` viết tay.
- **Khóa ngoại tới `employee`:** mọi bảng mới có khóa ngoại tới `employee` phải được thêm vào `EmployeeDeletionService.HANDLED_TABLES` và vào thao tác xóa trong service đó. Test `EmployeeForeignKeyCoverageIT` phải xanh.
- **Mã lỗi:**
  - Dùng `EmployeeRuleException(message, key)`.
  - Mỗi key mới cần câu tiếng Việt trong `webapp/i18n/vi/global.json` và trong `ERROR_TEXT` ở `entities/employee/detail/detail-api.ts`.
- **Giao diện:** tiếng Việt, ngày dạng `dd/MM/yyyy`, giờ dạng `HH:mm`, vùng bấm ≥ 44px.
- **Thời gian:** dùng đồng hồ `Clock` theo Asia/Ho_Chi_Minh.
- **Git:** commit kết thúc bằng `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Không push. Không `git stash`.

### Lệnh chạy
- **Biến môi trường:**
  - `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`
  - `export DOCKER_HOST=unix://$HOME/.docker/run/docker.sock` (IT phải chạy ngoài sandbox).
- **IT tập trung:** `mvn -q -pl app -am verify -Dskip.npm -Dskip.installnodenpm -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=<IT> -Dfailsafe.failIfNoSpecifiedTests=false`
- **Unit test lõi:** `mvn -q -pl core test -Dtest=<Test>`
- **Toàn bộ backend:** `mvn -q -pl app -am verify -Dskip.npm -Dskip.installnodenpm`
- **Frontend:** `cd app && npm test`, `npx tsc --noEmit`, `npm run lint`
- **Fixed Clock trong IT:** dùng một `@TestConfiguration` lồng có `@Primary Clock.fixed(...)`, như các IT nghỉ phép.

## Review Focus

1. **Bộ giờ đổi giữa tháng.** Ví dụ HR đặt giờ vào 08:30 từ 15/11. Kỳ vọng: ngày 14/11 vẫn tính theo 08:00, từ 15/11 tính theo 08:30. Tháng đã chốt không đổi. Test ở Task 2: `scheduleChangeAppliesFromEffectiveDateOnly` và `cannotChangeScheduleInClosedMonth`.
2. **Một người vừa có trưởng nhóm gốc vừa có trưởng nhóm onsite, cả hai cùng gõ một ngày.** Kỳ vọng: cả hai đều được phép gõ. Giờ vào lấy sớm nhất, giờ ra lấy muộn nhất, không bị nhân đôi công. Test ở Task 3: `bothLeadsMayEnterSameDay`.
3. **Ngày onsite ở khách hàng có giờ bắt đầu khác công ty.** Ví dụ khách hàng vào 08:30, nhân viên vào 09:55. Kỳ vọng: 1 công, muộn 85 phút. Nếu vào 10:01 thì 0,5 công. Test ở Task 1 (lõi) và Task 3: `onsiteUsesCustomerHours`.
4. **Đơn nghỉ được duyệt hoặc hủy sau khi tháng đã chốt.** Kỳ vọng: ảnh chụp giữ nguyên, bảng tháng hiện `changedAfterClose`, file bảng lương vẫn lấy số đã chốt. Test ở Task 4: `leaveChangeAfterCloseFlagsButKeepsSnapshot`.
5. **Mở lại kỳ lương đã duyệt rồi mở lại bảng công.** Kỳ vọng: khi kỳ `DA_DUYET` thì mở lại bảng công bị chặn. Sau khi Quản trị mở lại kỳ lương thì mở lại bảng công được. Test ở Task 4: `reopenFollowsPayrollPeriodState`.

---

### Task 1: Lõi tính công theo ngưỡng (`core`)

**Files:**
- Create: `core/src/main/java/vn/doit/hr/attendance/DayCredit.java`
- Test: `core/src/test/java/vn/doit/hr/attendance/DayCreditTest.java`

**Interfaces:**
- Produces:
  - `DayCredit.Rules(int graceMinutes, int halfDayThresholdMinutes)`.
  - `DayCredit.Hours(LocalTime start, LocalTime end, LocalTime breakStart, LocalTime breakEnd)`.
  - `enum DayCredit.Status { DI_LAM, THIEU_GIO, VANG, CHUA_NHAP }`.
  - `record DayCredit.Result(Status status, LocalTime firstIn, LocalTime lastOut, int lateMinutes, int earlyMinutes, BigDecimal workCredit)`.
  - `static Result DayCredit.compute(List<LocalTime> ins, List<LocalTime> outs, Hours hours, Rules rules, vn.doit.hr.leave.DayPart leavePart, boolean markedAbsent)`:
    - `leavePart` null nghĩa là không có đơn nghỉ.
    - `CA_NGAY` không được gọi vào đây.
    - `workCredit` chỉ là phần đi làm (0, 0.5 hoặc 1). Phần nghỉ do lớp gọi cộng thêm.
- `Timesheet` và `WorkSchedule` để nguyên. Phần GPS dùng sau.

- [ ] **Step 1: Viết test hỏng.** Tạo `DayCreditTest` với giờ công ty 08:00–17:00, nghỉ trưa 12:00–13:00, `Rules(5, 90)`:
  - `onTime`: vào 07:55, ra 17:05 → `DI_LAM`, 1, muộn 0.
  - `lateWithinThreshold`: vào 09:30, ra 17:00 → 1, muộn 90.
  - `lateBeyondThreshold`: vào 09:31 → 0.5, muộn 91.
  - `earlyWithinThreshold`: ra 15:30 → 1, sớm 90.
  - `earlyBeyondThreshold`: ra 15:29 → 0.5.
  - `lateAndEarlyBeyond`: vào 09:40, ra 15:20 → `VANG`, 0.
  - `lessThanHalfScheduled`: vào 08:00, ra 11:00 → `VANG`, 0. Ca có 480 phút, 180 < 240.
  - `missingOut`: chỉ có giờ vào → `THIEU_GIO`, `workCredit` null.
  - `nothingEntered` → `CHUA_NHAP`.
  - `markedAbsent` → `VANG`, 0.
  - `morningLeaveAfternoonWork`: `SANG`, vào 13:00, ra 17:00 → 0.5. Vào 14:45 → vẫn 0.5, muộn 105.
  - `afternoonLeave`: `CHIEU`, vào 08:00, ra 12:00 → 0.5.
  - `customerHours`: giờ 08:30–17:30; vào 09:55 → 1, muộn 85. Vào 10:01 → 0.5.
  - `multiplePunches`: giờ vào 08:10 và 07:58, giờ ra 16:00 và 17:02 → giờ vào 07:58, giờ ra 17:02.
- [ ] **Step 2: Chạy test.** `mvn -q -pl core test -Dtest=DayCreditTest` → FAIL (chưa có class).
- [ ] **Step 3: Viết `DayCredit`.** Cách tính:
  - "Đi muộn" = giờ vào − giờ bắt đầu buổi. Muộn ≤ `graceMinutes` thì tính là 0.
  - Ngưỡng nửa ngày so sánh với `halfDayThresholdMinutes`.
  - Số giờ làm trong ca tính bằng phần giao giữa khoảng vào–ra và ca, trừ giờ nghỉ trưa.
- [ ] **Step 4: Chạy lại test.** Lệnh như Step 2 → PASS.
- [ ] **Step 5: Commit.**

### Task 2: Dữ liệu, cài đặt giờ làm, giờ khách hàng, trưởng nhóm onsite

**Files:**
- Create:
  - entity `AttendanceSchedule` (`attendance_schedule`: `effective_from`, `start_time`, `end_time`, `break_start`, `break_end`, `grace_minutes`, `half_day_threshold_minutes`, `work_site_id` nullable với null = công ty; audited).
  - `OnsiteLead` (`work_site_onsite_lead`: `work_site_id`, `employee_id`, unique cặp).
  - `AttendancePunch` (`attendance_punch`: `employee_id`, `work_day`, `punch_time`, `kind` VAO/RA, `source` GO_TAY/APP, `entered_by`, `entered_at`; audited, có index (`employee_id`, `work_day`)).
  - `AttendanceMark` (`attendance_mark`: `employee_id`, `work_day`, `kind` VANG, `entered_by`; audited, unique (`employee_id`, `work_day`)).
  - `AttendanceMonth` (`attendance_month`: `month` dạng yyyy-MM unique, `status` DANG_MO/DA_CHOT, `closed_by`, `closed_at`).
  - `AttendanceMonthEvent` (`month`, `action` CHOT/MO_LAI, `actor`, `at`, `reason`).
  - `AttendanceSnapshot` (`attendance_snapshot`: `month`, `employee_id`, `standard_days`, `actual_days`, `work_days`, `paid_leave_days`, `unpaid_leave_days`, `sick_days`, `holiday_days`, `trip_days`, `late_count`, `late_minutes`, `early_count`, `half_days`, `absent_days`, `missing_days`; unique (`month`, `employee_id`)).
  - Repository cho từng entity.
  - Changelog `20261031000100_attendance.xml`, thêm vào `master.xml`.
  - `service/attendance/AttendanceScheduleService.java`.
  - `web/rest/AttendanceSettingResource.java`.
  - Frontend: mục "Giờ làm" ở trang cài đặt hiện có (`modules/work-calendar/work-calendar.tsx`), bộ giờ và trưởng nhóm onsite ở form `WorkSite`.
- Modify:
  - `EmployeeDeletionService` (`HANDLED_TABLES` và các thao tác xóa cho `work_site_onsite_lead`, `attendance_punch`, `attendance_mark`, `attendance_snapshot` cùng các bảng `_aud`).
  - Các guard lưu trữ cho `OnsiteLead`.
- Test: `AttendanceScheduleIT`, cập nhật `EmployeeDeletionIT` (người có lần chấm, ảnh chụp và vai trò trưởng nhóm onsite bị xóa hẳn).

**Interfaces:**
- Produces:
  - `AttendanceScheduleService.hoursFor(LocalDate day, Long workSiteIdOrNull): DayCredit.Hours`. Khách hàng không có bộ giờ riêng thì trả bộ giờ của công ty.
  - `AttendanceScheduleService.rules(LocalDate day): DayCredit.Rules`.
  - `AttendanceScheduleService.onsiteLeadSiteIds(Long employeeId): Set<Long>`.
  - REST, ADMIN/HR ghi, mọi vai trò đăng nhập đọc:
    - `GET /api/attendance/schedules?siteId=`
    - `POST /api/attendance/schedules`
    - `DELETE /api/attendance/schedules/{id}`, chỉ khi `effective_from` > hôm nay.
    - `PUT /api/work-sites/{id}/onsite-leads` `{employeeIds}`
  - Changelog seed một bộ giờ công ty `effective_from` 2020-01-01 với giá trị mặc định.

- [ ] **Step 1: Viết test hỏng.** Các test trong `AttendanceScheduleIT`:
  - `defaultCompanyScheduleSeeded`: `hoursFor(any, null)` = 08:00/17:00/12:00/13:00, rules (5, 90).
  - `scheduleChangeAppliesFromEffectiveDateOnly`: thêm bộ giờ 08:30 từ 15/11. Ngày 14/11 vẫn 08:00, ngày 15/11 là 08:30.
  - `customerHoursFallBackToCompany`.
  - `cannotChangeScheduleInClosedMonth`: tạo `AttendanceMonth` DA_CHOT 11/2026, thêm bộ giờ `effective_from` 20/11 → 400 `attendancescheduleclosedmonth`.
  - `onlyAdminHrWrite`: KE_TOAN gửi POST → 403.
  - `onsiteLeadsSaved`: PUT rồi `onsiteLeadSiteIds` trả đúng site.
  - Mở rộng `EmployeeDeletionIT` cho các bảng mới.
- [ ] **Step 2: Chạy test.** `AttendanceScheduleIT` FAIL. `EmployeeForeignKeyCoverageIT` cũng FAIL khi đã có bảng mà chưa thêm vào `HANDLED_TABLES`.
- [ ] **Step 3: Làm phần còn lại.** Gồm changelog, các entity, service, resource, phần xử lý trong service xóa hẳn, giao diện cài đặt và giao diện `WorkSite`, các key lỗi mới. Viết test vitest cho form bộ giờ: kiểm tra giờ ra phải sau giờ vào.
- [ ] **Step 4: Chạy kiểm tra.** Hai IT trên, `EmployeeDeletionIT`, `EmployeeForeignKeyCoverageIT` và các lệnh frontend → PASS.
- [ ] **Step 5: Commit.**

### Task 3: Tính công từng ngày, quyền và màn gõ theo ngày (kiểu A)

**Files:**
- Create:
  - `service/attendance/AttendanceDayService.java` (ghép ô tự điền, bộ giờ và lần chấm thành kết quả ngày).
  - `service/attendance/AttendanceAccess.java` (ai được gõ hoặc xem ai, vào ngày nào).
  - `web/rest/AttendanceResource.java`.
  - Frontend `modules/attendance/attendance-api.ts`, `modules/attendance/day-entry.tsx` cùng spec, route `/cham-cong` và mục trên sidebar.
- Test: `AttendanceDayIT`, `day-entry.spec.tsx`.

**Interfaces:**
- Consumes: `DayCredit.compute`, `AttendanceScheduleService.*` (Task 2).
- Produces:
  - `AttendanceDayService.day(Employee e, LocalDate d): DayView`, trong đó:
    - `DayView(Long employeeId, String code, String name, LocalDate day, String group, String groupLabel, String autoKind, BigDecimal credit, BigDecimal actualCredit, DayCredit.Status status, LocalTime in, LocalTime out, int lateMinutes, int earlyMinutes, boolean remote)`.
    - `group` là "VP" hoặc "SITE:<id>".
    - `autoKind` là một trong `PHEP_NAM|NGHI_BU|VIEC_RIENG|OM|KHONG_LUONG|CONG_TAC|LE|NGHI|NGOAI_HD`, hoặc null.
    - `credit` là công đi làm. `actualCredit` là phần đóng góp vào công thực tế theo Global Constraints.
  - `AttendanceDayService.month(Employee e, YearMonth m): MonthView`, trong đó:
    - `MonthView(List<DayView> days, BigDecimal standardDays, BigDecimal actualDays, …counts tương ứng các cột của snapshot)`.
  - `AttendanceAccess.canEnter(String login, Employee target, LocalDate day): boolean`.
  - `AttendanceAccess.enterableEmployees(String login, LocalDate day): List<Employee>`.
  - REST:
    - `GET /api/attendance/day?date=yyyy-MM-dd` → danh sách `DayView` những người người gọi được gõ, chia nhóm.
    - `PUT /api/attendance/day` `{date, entries:[{employeeId, in:"HH:mm"|null, out:"HH:mm"|null, absent:boolean}]}`. Thao tác này thay toàn bộ lần chấm `GO_TAY` của người đó trong ngày, không động tới lần chấm `APP`. Trả về các `DayView` sau khi lưu.
  - Đơn nghỉ lấy các đơn `DA_DUYET` (và trạng thái `CHO_HUY`, vì trạng thái này vẫn tính là nghỉ), dùng `firstPart`/`lastPart` cho ngày đầu và ngày cuối.

- [ ] **Step 1: Viết test hỏng.** Các test trong `AttendanceDayIT`, chạy với Clock cố định 20/11/2026:
  - `leadSeesOnlyOwnTeam`
  - `onsiteLeadSeesOnlySiteOnThatDay`: hết hạn phân công thì không còn thấy.
  - `hrSeesAll`
  - `internsAndArchivedExcluded`
  - `cannotEnterSelf` → `attendanceself`
  - `cannotEnterFuture` → `attendancefuture`. Gõ 21/11 bị chặn; gõ hôm nay với giờ ra 18:00 khi đồng hồ đang 10:00 cũng bị chặn.
  - `closedMonthRejected` → `attendancemonthclosed`
  - `onsiteUsesCustomerHours`
  - `bothLeadsMayEnterSameDay`
  - `approvedLeaveAutoFilled`: phép năm cả ngày → `autoKind` `PHEP_NAM`, `actualCredit` 1, không cần gõ.
  - `halfDayLeavePlusWork`: `actualCredit` 1.
  - `tripAutoFilled`
  - `holidayCountsActual`
  - `remoteFlagged`
  - `appPunchesUntouchedByPut`: chèn trực tiếp một lần chấm `APP`, gửi PUT, lần chấm `APP` vẫn còn.
  - `nonLeadForbidden` → 403
- [ ] **Step 2: Chạy test.** `AttendanceDayIT` → FAIL.
- [ ] **Step 3: Làm phần còn lại.** Gồm service, quyền, resource và màn gõ theo ngày đúng bản phác A:
  - Có khối "Văn phòng" và các khối "Onsite · <khách hàng> (giờ)".
  - Mỗi người có ô Vào, ô Ra, công tính lại ngay ở client (công thức giống lõi, dùng chung số liệu bộ giờ trả về từ API), nhãn Đúng giờ / Muộn X' / → nửa công / Thiếu giờ / tên loại nghỉ, nút Vắng và nút Lưu.
  - Test vitest: gõ 09:50 → hiện "nửa công"; ô thiếu giờ ra được tô đỏ; dòng ô tự điền không có ô nhập.
- [ ] **Step 4: Chạy kiểm tra.** IT ở trên và các lệnh frontend → PASS.
- [ ] **Step 5: Commit.**

### Task 4: Bảng công tháng (kiểu B), bảng công của tôi, chốt và mở lại

**Files:**
- Create:
  - `service/attendance/AttendanceMonthService.java`.
  - Thêm endpoint vào `AttendanceResource`.
  - Frontend `modules/attendance/month-board.tsx` (cho HR, Quản trị và các vai trò chỉ xem), `modules/attendance/my-attendance.tsx` cùng các spec.
- Modify:
  - `PayrollPeriodService.reopen` không cần sửa. Trạng thái kỳ lương được đọc trong `AttendanceMonthService`.
- Test: `AttendanceMonthIT`, các spec vitest.

**Interfaces:**
- Consumes: `AttendanceDayService.month`, `AttendanceAccess` (Task 3).
- Produces:
  - `AttendanceMonthService.board(String login, YearMonth m, Long departmentId?, Long siteId?): BoardDTO`, trong đó `BoardDTO(String month, String status, List<Row> rows)` và `Row(employee, days, totals, boolean changedAfterClose)`. Tháng đã chốt thì `totals` lấy từ snapshot.
  - `AttendanceMonthService.close(YearMonth m, boolean force): void`. Hàm ghi snapshot cho mọi `NHAN_VIEN` có ngày làm việc trong tháng.
  - `AttendanceMonthService.reopen(YearMonth m, String reason): void`.
  - `AttendanceMonthService.snapshot(YearMonth m): Map<Long, AttendanceSnapshot>`. Trả về rỗng khi tháng chưa chốt. Task 5 dùng hàm này.
  - `AttendanceMonthService.isClosed(YearMonth m): boolean`.
  - REST:
    - `GET /api/attendance/month?month=yyyy-MM&departmentId=&siteId=`
    - `GET /api/attendance/my?month=`
    - `POST /api/attendance/month/{month}/close?force=`
    - `POST /api/attendance/month/{month}/reopen` `{reason}`
    - `GET /api/attendance/month/{month}/export.xlsx`
  - `PUT /api/attendance/day` (Task 3) cũng dùng cho việc sửa ô trên bảng tháng.

- [ ] **Step 1: Viết test hỏng.** Các test trong `AttendanceMonthIT`:
  - `closeWithUnresolvedNeedsForce` → `attendanceunresolved` kèm danh sách. Gửi lại với `force=true` thì chốt được.
  - `closeWritesSnapshotWithExactTotals`. Tháng 11/2026 có 21 ngày làm việc. Một người gõ đủ 19 ngày, 1 ngày vào 09:50, 1 ngày phép năm. Kỳ vọng: chuẩn 21, thực tế 20,5, `half_days` 1, `late_count` 1.
  - `standardDaysIncludeHoliday`.
  - `closedMonthRejectsEdits`.
  - `reopenFollowsPayrollPeriodState`:
    - Kỳ `DA_DUYET` → `attendancepayrollapproved`.
    - Quản trị mở lại kỳ lương → mở lại bảng công được.
    - Mở lại thiếu lý do → 400.
  - `leaveChangeAfterCloseFlagsButKeepsSnapshot`.
  - `scheduleChangeAfterCloseDoesNotAlterSnapshot`.
  - `viewRights`:
    - Trưởng phòng chỉ thấy phòng mình.
    - BGD thấy toàn bộ.
    - KE_TOAN gọi tháng chưa chốt → 403. Tháng đã chốt → 200.
    - Nhân viên chỉ gọi được `/my`.
  - `eventsLogged`.
- [ ] **Step 2: Chạy test.** → FAIL.
- [ ] **Step 3: Làm phần còn lại.** Gồm service và giao diện bảng tháng đúng bản phác B:
  - Lọc theo phòng, nhóm và khách hàng. Có cột tổng công.
  - Ô đỏ cho `THIEU_GIO`/`CHUA_NHAP`, dấu cho `changedAfterClose`.
  - Nút Chốt: khi server trả `attendanceunresolved` thì hiện danh sách ô đỏ, kèm nút "Vẫn chốt".
  - Nút Mở lại có hộp nhập lý do. Nút tải Excel.
  - Trang "Bảng công của tôi".
  - Mục sidebar hiện theo vai trò.
- [ ] **Step 4: Chạy kiểm tra.** IT ở trên và các lệnh frontend → PASS.
- [ ] **Step 5: Commit.**

### Task 5: Ngày công cho bảng lương và README

**Files:**
- Modify:
  - `core/.../payroll/PayrollTemplateWriter.java`: thêm overload `write(OutputStream, List<PrefillRow>)`. `PrefillRow(String code, String name, String position, LocalDate contractStart, BigDecimal standardDays, BigDecimal actualDays)`. Hai số ngày được phép null.
  - `web/rest/PayrollImportResource.java`: thêm `GET /api/payroll-imports/template.xlsx?period=yyyy-MM` (KE_TOAN và ADMIN).
  - `service/payroll/PayrollCheck.java`: đối chiếu với snapshot.
  - `service/payroll/PayrollPeriodService.approve`: cảnh báo khi chưa chốt công, trả kèm `warnings` trong `PeriodDTO`.
  - Frontend `modules/payroll/*`: nút "Tải file bảng lương có ngày công" ở kỳ lương, hiện cảnh báo.
  - `README.md`: thêm mục Chấm công.
- Test: `PayrollTemplateWriterTest` (core), `PayrollAttendanceIT`, spec vitest của trang kỳ lương.

**Interfaces:**
- Consumes: `AttendanceMonthService.snapshot`, `AttendanceMonthService.isClosed` (Task 4).

- [ ] **Step 1: Viết test hỏng.**
  - `PayrollTemplateWriterTest.prefillWritesDays`: đọc lại file, dòng đầu có mã, họ tên và hai cột ngày công đúng giá trị. Dòng có số ngày null thì ô để trống.
  - Các test trong `PayrollAttendanceIT`:
    - `templateNeedsClosedMonth` → `attendancenotclosed`.
    - `templatePrefilledFromSnapshot`: thực tập sinh có dòng nhưng không có số ngày.
    - `importWarnsOnMismatch`: file ghi 22 trong khi snapshot là 20,5 → WARNING "File ghi 22, bảng công đã chốt 20,5". Nhập vẫn được.
    - `importWarnsWhenNotClosed`.
    - `approveWarnsWhenNotClosed`.
- [ ] **Step 2: Chạy test.** → FAIL.
- [ ] **Step 3: Làm phần còn lại.** Gồm các thay đổi ở file liệt kê trên, giao diện, và README. README mô tả quy tắc tính công, quyền, cách chốt và mở lại, và quy trình bảng lương 5 bước trong spec.
- [ ] **Step 4: Chạy kiểm tra.** Test lõi, IT, toàn bộ backend và các lệnh frontend → PASS.
- [ ] **Step 5: Commit.**
