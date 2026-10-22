# DOIT HRM – Nghỉ phép & đơn từ: kế hoạch triển khai

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nhân viên DO IT (loại NHAN_VIEN) gửi đơn nghỉ và đơn làm thêm giờ trên hệ thống. Đơn được duyệt theo đúng cấp. Sổ phép năm và quỹ nghỉ bù tự tính, có dấu vết từng biến động. Có lịch tháng theo quyền xem, và tổng hợp hằng tháng cho kế toán.

**Architecture:**
- Quy tắc tính thuần nằm trong `core/`, package mới `vn.doit.hr.leave`: đếm ngày làm việc, cộng phép, chia ngăn sổ, hệ số và giới hạn làm thêm, hạn mức nghỉ ốm và việc riêng.
- `app/` thêm:
  - bảng sổ phép chỉ ghi thêm (`leave_ledger_entry`), đơn nghỉ (`leave_request`), đơn làm thêm (`overtime_request`), cài đặt (`leave_setting`);
  - trưởng phòng trên phòng ban; mức phép năm và số năm đóng BHXH trước khi vào trên hồ sơ;
  - các service duyệt, tính người duyệt, việc chạy tự động, API lịch;
  - giao diện React.
- Dùng lại những gì đã có: `WorkCalendarService` (ngày làm việc, ngày lễ), `ReminderMailer` (email), `TerminationService` (điểm móc khi nghỉ việc và hoàn tác), `FileStorageService` (giấy ốm), lịch sử vị trí (đi công tác trên lịch).

**Tech Stack:** JHipster 9.4.0 / Spring Boot 4 / Java 21, React 19 + Vite/Vitest + react-bootstrap, PostgreSQL 16 + Liquibase (chỉ thêm changeset), Envers, Apache POI (đã có), Testcontainers.

**Spec:** `docs/superpowers/specs/2026-10-22-hrm-nghi-phep-don-tu-design.md`

## Global Constraints

- Chỉ người có `personType = NHAN_VIEN` dùng phần này. THUC_TAP và DICH_VU: không sổ phép, không gửi đơn, không thấy trang, không có trong tổng hợp (spec 3.2 [Chốt]).
- Phép năm cộng mỗi tháng mức năm ÷ 12, ứng tối đa 1 ngày (số dư thấp nhất −1). Phép chuyển sang năm sau dùng đến hết 31/03, sau đó hết hạn; khi nghỉ thì trừ phép năm trước trước.
- Hai ngăn sổ `NAM_TRUOC` / `NAM_NAY`:
  - **01/01:** phần dương chuyển hết sang `NAM_TRUOC`; số âm chuyển thành số âm ở `NAM_NAY`.
  - **01/04:** phần dương của `NAM_TRUOC` hết hạn.
  - Quỹ nghỉ bù dùng cùng cơ chế.
- Đơn vị nghỉ là 0,5 ngày. Sổ lưu 2 chữ số thập phân. Số được xin = số dư làm tròn xuống 0,5, cộng 1 ngày được ứng.
- Mức năm mặc định 12 (HR đặt 14 hoặc 16 trên hồ sơ). Mỗi đủ 5 năm kể từ ngày vào công ty thì +1, áp dụng từ tháng kế tiếp. Không truy hồi, không áp dụng ngược.
- Người duyệt:
  - nghỉ ≤ 3 ngày làm việc, và làm thêm giờ → trưởng nhóm (`Employee.manager`);
  - nghỉ > 3 ngày → giám đốc (vai trò BGĐ, bất kỳ người BGĐ nào).
- Trường hợp đặc biệt về người duyệt:
  - không có trưởng nhóm → trưởng phòng;
  - người xin nghỉ là trưởng phòng → BGĐ;
  - người xin nghỉ là BGĐ → Quản trị;
  - người duyệt không có tài khoản đang hoạt động hoặc đã nghỉ → lên cấp kế tiếp;
  - không bao giờ để người xin nghỉ tự duyệt đơn của mình;
  - HR và Quản trị duyệt thay được mọi đơn.
- Trưởng phòng không duyệt và không nhận email; chỉ xem lịch phòng mình.
- Hệ số làm thêm giờ: 150% (ngày làm việc), 200% (ngày nghỉ hằng tuần), 300% (ngày lễ). Giới hạn 40 giờ/tháng và 200 giờ/năm (cấu hình được). Đổi sang nghỉ bù: giờ thực làm, không nhân hệ số, 8 giờ = 1 ngày, 4 giờ = nửa ngày.
- Hạn mức nghỉ ốm (BHXH): 30 ngày/năm nếu đóng BHXH < 15 năm, 40 ngày nếu 15–< 30 năm, 60 ngày nếu ≥ 30 năm. Vượt thì chỉ cảnh báo.
- Nghỉ việc riêng có lương: kết hôn 3 ngày, con kết hôn 1 ngày, tang 3 ngày.
- Lịch không hiện lý do nghỉ. Lý do và giấy ốm chỉ người gửi, người duyệt của đơn, HR và Quản trị xem được.
- Thay đổi quy định không áp dụng ngược. Dòng sổ không sửa, không xóa; muốn sửa thì ghi dòng đảo ngược.
- Mọi ràng buộc chung của dự án vẫn áp dụng:
  - quyền kiểm tra ở backend (`@PreAuthorize`, quy tắc URL, và kiểm tra theo dữ liệu với trưởng nhóm/trưởng phòng);
  - lỗi nghiệp vụ dùng `EmployeeRuleException(key)` và được `ExceptionTranslator` đổi thành 400;
  - giao diện tiếng Việt, ngày `dd/MM/yyyy`, vùng bấm ≥ 44px, theo màu và font canvas;
  - `core/` không phụ thuộc Spring;
  - mọi suite phải xanh.

## Review Focus

1. **Đơn trùm ngày lễ và cuối tuần:** chỉ trừ ngày làm việc. Ví dụ đơn 30/04/2026–04/05/2026, với 30/04 và 01/05 là ngày lễ, 02–03/05 là cuối tuần → trừ 1 ngày. → Task 1.
2. **Hai đơn của cùng một người được duyệt gần như cùng lúc:** số dư không bao giờ xuống dưới −1 (khóa sổ theo người). → Task 3.
3. **Người duyệt chính là người xin nghỉ**, ví dụ trưởng nhóm tự xin nghỉ, hoặc quản lý trực tiếp ghi nhầm là chính mình: đơn phải lên cấp kế tiếp, không ai tự duyệt. → Task 3.
4. **Cho nghỉ việc khi còn đơn chờ hoặc đơn đã duyệt có ngày sau ngày làm việc cuối:**
   - các đơn đó tự hủy và số dư được hoàn lại;
   - các đơn chờ mà người này đang là người duyệt được chuyển lên cấp kế tiếp;
   - hoàn tác nghỉ việc thì ghi dòng ngược lại cho số dư đã chốt.

   → Task 3.
5. **Đơn bắc qua năm** (28/12–05/01) **và đơn duyệt sau ngày 31/03:** trừ đúng ngăn tại thời điểm duyệt. Đơn duyệt sau 01/04 không được dùng phần phép năm trước đã hết hạn. → Task 2 và Task 3.

---

### Task 1: Lõi quy tắc nghỉ phép (`core`, package `vn.doit.hr.leave`)

**Files:**
- Create:
  - `core/src/main/java/vn/doit/hr/leave/`: `LeaveDays.java`, `AnnualAccrual.java`, `LeaveLedgerMath.java`, `OvertimeRules.java`, `PersonalLeaveReason.java`, `SickLeaveLimit.java`, `Pool.java`, `Bucket.java`
- Test: `core/src/test/java/vn/doit/hr/leave/*Test.java`

**Interfaces:**
- Consumes: `vn.doit.hr.reminder.WorkCalendar` (đã có; xem chữ ký trong file để biết cách hỏi ngày làm việc).
- Produces:
  - `enum Pool { PHEP_NAM, NGHI_BU }`; `enum Bucket { NAM_TRUOC, NAM_NAY }`.
  - `enum DayPart { CA_NGAY, SANG, CHIEU }`.
  - `BigDecimal LeaveDays.count(LocalDate from, LocalDate to, DayPart firstDay, DayPart lastDay, WorkCalendar cal)`:
    - số ngày làm việc, với 0,5 cho buổi sáng/chiều;
    - `firstDay` chỉ cho `CA_NGAY|CHIEU`, `lastDay` chỉ cho `CA_NGAY|SANG`;
    - `from == to` thì dùng `firstDay`.
  - `int AnnualAccrual.annualDays(int baseRate, LocalDate joinDate, YearMonth month)`: baseRate + số lần đủ 5 năm tính đến **ngày cuối của tháng trước** `month` (thay đổi áp dụng từ tháng kế tiếp sau ngày đủ năm).
  - `BigDecimal AnnualAccrual.monthly(int annualDays, LocalDate joinDate, LocalDate lastWorkingDay /*nullable*/, YearMonth month, WorkCalendar cal)`:
    - trả `annualDays / 12` làm tròn 2 chữ số (HALF_UP);
    - trả `0` nếu trong `month` số ngày làm việc thuộc khoảng [joinDate, lastWorkingDay] < 50% số ngày làm việc của tháng.
  - `record Entry(Pool pool, Bucket bucket, BigDecimal amount)`; `record Balance(BigDecimal namTruoc, BigDecimal namNay)` với `total()` và `requestable()` = `floorHalf(total) + 1` (`floorHalf` làm tròn xuống bội số 0,5).
  - `Balance LeaveLedgerMath.balance(Pool pool, List<Entry> entries)`.
  - `List<Entry> LeaveLedgerMath.deduct(Pool pool, Balance b, BigDecimal days, BigDecimal maxAdvance)`:
    - trừ `NAM_TRUOC` đến hết phần dương, phần còn lại trừ `NAM_NAY`;
    - ném `InsufficientLeaveException` (core, unchecked) nếu `total − days < −maxAdvance`.
    - Với `NGHI_BU` thì `maxAdvance = 0`, đơn vị là giờ.
  - `List<Entry> LeaveLedgerMath.yearEndCarry(Pool pool, Balance b)`: các dòng để chuyển năm (dương → `NAM_TRUOC` của năm mới; âm → `NAM_NAY` âm; phần `NAM_TRUOC` cũ còn dư cũng gộp vào).
  - `List<Entry> LeaveLedgerMath.expire(Pool pool, Balance b)`: dòng âm đưa phần dương của `NAM_TRUOC` về 0.
  - `int OvertimeRules.rate(LocalDate day, WorkCalendar cal, boolean holiday)` → 150 | 200 | 300.
  - `boolean OvertimeRules.hasNightHours(LocalTime start, LocalTime end)`: có phần nào trong 22:00–06:00.
  - `BigDecimal OvertimeRules.hours(LocalTime start, LocalTime end)`: cho phép qua nửa đêm.
  - `void OvertimeRules.checkLimits(BigDecimal monthHours, BigDecimal yearHours, BigDecimal added, int monthCap, int yearCap)` ném `OvertimeLimitException` (core) với message tiếng Việt nêu đã dùng bao nhiêu / giới hạn.
  - `enum PersonalLeaveReason { KET_HON(3,"Bản thân kết hôn"), CON_KET_HON(1,"Con kết hôn"), TANG(3,"Cha, mẹ, vợ/chồng, con mất") }`.
  - `int SickLeaveLimit.daysPerYear(int yearsContributed)` → 30 | 40 | 60.

- [ ] **Step 1: Viết test** (lịch giả: T2–T6 là ngày làm việc; ngày lễ 2026: 01/01, 30/04, 01/05, 02/09).
  - `LeaveDaysTest`:
    - 30/04–04/05/2026 cả ngày = 1 (Review Focus 1);
    - 22/10/2026 sáng = 0,5;
    - 22/10 chiều → 23/10 cả ngày = 1,5;
    - 24/10 (thứ Bảy) = 0;
    - `firstDay = SANG` với đơn nhiều ngày → `IllegalArgumentException`.
  - `AnnualAccrualTest`:
    - vào 10/03/2015, mức gốc 12:
      - `annualDays(…, 2020-03) = 12` (ngày đủ 5 năm 10/03/2020 thuộc tháng 03, cuối tháng trước là 29/02/2020 chưa đủ);
      - `annualDays(…, 2020-04) = 13`;
      - `annualDays(…, 2025-04) = 14`;
    - `monthly(12, …)` = 1,00;
    - `monthly(13, …)` = 1,08;
    - vào 20/10/2026 (6/22 ngày làm việc còn lại < 50%) → 0;
    - vào 12/10/2026 → 1,00;
    - nghỉ việc ngày làm việc cuối 09/10/2026 (7/22 < 50%) → 0.
  - `LeaveLedgerMathTest`:
    - dư `NAM_TRUOC` 3 / `NAM_NAY` 2, trừ 4 → dòng (−3 `NAM_TRUOC`, −1 `NAM_NAY`);
    - dư 0/0,5, trừ 1,5 → được (total −1);
    - trừ 2 → `InsufficientLeaveException`;
    - `requestable` của 4,75 = 5,75 (4,5 + 1);
    - `yearEndCarry`: dư 2,5 / 1 → các dòng cho năm mới ra `NAM_TRUOC` 3,5;
    - dư 0 / −1 → `NAM_NAY` −1;
    - `expire` với `NAM_TRUOC` 1,5 → −1,5; với `NAM_TRUOC` ≤ 0 → rỗng.
  - `OvertimeRulesTest`:
    - thứ Năm → 150; thứ Bảy → 200; 02/09/2026 → 300;
    - 18:00–23:30 = 5,5 giờ, có giờ đêm;
    - 22:00–02:00 = 4 giờ;
    - `checkLimits(38, 100, 3, 40, 200)` → ném, message chứa "38" và "40";
    - `checkLimits(10, 199, 2, 40, 200)` → ném.
  - `SickLeaveLimitTest`: 0 → 30, 14 → 30, 15 → 40, 29 → 40, 30 → 60.
- [ ] **Step 2:** `mvn -q -pl core test`. Expected: FAIL (lớp chưa có).
- [ ] **Step 3:** Cài đặt. Dùng `BigDecimal` scale 2; `floorHalf(x) = floor(x×2)/2`.
- [ ] **Step 4:** Chạy lại. Expected: PASS, test lõi cũ vẫn đạt.
- [ ] **Step 5:** Commit `"Lõi nghỉ phép: đếm ngày làm việc, cộng phép, ngăn sổ, làm thêm giờ"`.

### Task 2: Mô hình dữ liệu, sổ phép, việc chạy tự động, số dư đầu kỳ và điều chỉnh

**Files:**
- Create: changelog `2026102200xx_leave.xml`:
  - `leave_setting`: 1 dòng; `advance_days` 1, `director_threshold_days` 3, `ot_month_cap` 40, `ot_year_cap` 200, `carry_expiry_month` 3, `carry_expiry_day` 31, `past_request_days` 30, `ot_confirm_day` 5.
  - `leave_ledger_entry`: `id`, `employee_id`, `pool`, `bucket`, `year` int, `amount` numeric(7,2), `effective_date`, `reason` varchar(20), `ref_type`, `ref_id`, `note`, `created_by`, `created_at`. Unique `(employee_id, pool, reason, ref_type, ref_id)` khi `reason` thuộc `CONG_THANG`, `CHUYEN_NAM`, `HET_HAN`, `DUYET_DON`, `HUY_DON`, `LAM_THEM`, để chạy lại không ghi trùng.
  - `employee.annual_leave_days` int not null default 12; `employee.prior_social_insurance_years` int not null default 0; `department.head_id` FK employee nullable.
  - Các bảng `_aud` cho `leave_setting`, `department`, `employee` (cột mới).
- Create: `domain/LeaveLedgerEntry.java`, `domain/LeaveSetting.java`; enum `LedgerReason` (`CONG_THANG`, `DAU_KY`, `DUYET_DON`, `HUY_DON`, `HET_HAN`, `CHUYEN_NAM`, `DIEU_CHINH`, `NGHI_VIEC`, `HOAN_TAC`, `LAM_THEM`).
- Create:
  - `service/leave/LeaveLedgerService.java`
  - `service/leave/LeaveAccrualJob.java`
  - `service/leave/LeaveOpeningImportService.java` (Excel, theo mẫu nhập nhân viên đã có: tải mẫu, kiểm tra, lưu tất cả hoặc không)
  - `web/rest/LeaveBalanceResource.java`
  - `web/rest/LeaveSettingResource.java`
- Modify: `EmployeeDTO`/form và trang hồ sơ (ô "Mức phép năm" 12/14/16; ô "Số năm đóng BHXH trước khi vào DO IT"); `DepartmentDTO` và trang phòng ban (ô "Trưởng phòng" chọn nhân viên đang làm).
- Test: `LeaveLedgerServiceIT`, `LeaveAccrualJobIT`, `LeaveOpeningImportIT`, `LeaveBalanceResourceIT`

**Interfaces:**
- Consumes: Task 1 (`AnnualAccrual`, `LeaveLedgerMath`, `Pool`, `Bucket`, `Entry`, `Balance`); `WorkCalendarService.calendar(LocalDate)`; bean `Clock`.
- Produces:
  - `Balance LeaveLedgerService.balance(Long employeeId, Pool pool)`: tính tại hôm nay.
  - `List<LeaveLedgerEntry> post(Long employeeId, Pool pool, List<Entry> entries, LedgerReason reason, String refType, Long refId, String note)`: khóa dòng employee (`findByIdForUpdate`) trong transaction của người gọi.
  - `void lock(Long employeeId)`: khóa dòng để kiểm tra rồi ghi.
  - `LeaveAccrualJob`:
    - `@Scheduled(cron = "0 10 0 1 * *", zone = "Asia/Ho_Chi_Minh")` gọi `runMonth(YearMonth)`:
      - ngày 01/01: chạy `CHUYEN_NAM` trước, rồi `CONG_THANG`;
      - ngày 01/04: chạy `HET_HAN`;
    - có bắt bù khi khởi động (`ApplicationReadyEvent`) cho tháng hiện tại;
    - chạy lại không ghi trùng.
  - API:
    - `GET /api/leave/balances/me`: phép năm (2 ngăn, `requestable`), nghỉ bù (giờ), ngày ốm đã nghỉ trong năm / mức, giờ làm thêm tháng này / 40.
    - `GET /api/leave/balances?departmentId=` (HR, ADMIN): số dư cả công ty.
    - `GET /api/leave/ledger/{employeeId}`: chính mình, HR, ADMIN, trưởng nhóm hoặc trưởng phòng của người đó.
    - `POST /api/leave/adjustments` `{employeeId, pool, amount, note}` (HR, ADMIN; `note` bắt buộc → `DIEU_CHINH`).
    - `GET /api/leave/opening/template`, `POST /api/leave/opening/check`, `POST /api/leave/opening` (HR, ADMIN): mỗi dòng gồm mã NV, phép năm còn lại, trong đó phần năm trước, giờ nghỉ bù còn lại → `DAU_KY`; người đã có dòng `DAU_KY` → lỗi dòng.
    - `GET/PUT /api/leave/settings` (đọc: mọi vai trò nghiệp vụ; sửa: ADMIN).
  - Mọi API trên trả 403 cho THUC_TAP/DICH_VU khi xem của chính mình (không có sổ).

- [ ] **Step 1: Viết test.**
  - `LeaveAccrualJobIT` (dùng `Clock.fixed`):
    - nhân viên vào 01/03/2026: chạy 01/03 → +1,00 `CONG_THANG`; chạy lại → không thêm;
    - THUC_TAP → không có dòng;
    - người nghỉ việc (đã áp dụng) → không cộng các tháng sau;
    - chạy 01/01/2027 với dư 2,5 → `NAM_TRUOC` 2,5 và `CONG_THANG` tháng 1;
    - chạy 01/04/2027 → `HET_HAN` phần `NAM_TRUOC` còn dương;
    - khởi động lại giữa tháng → bắt bù tháng hiện tại một lần.
  - `LeaveLedgerServiceIT`: `post` ghi đúng ngăn; `balance` đúng tổng; dòng không sửa được (repository không có update/delete được dùng — kiểm tra bằng ArchUnit hoặc test API không có endpoint sửa).
  - `LeaveOpeningImportIT`: file 2 dòng hợp lệ → 2 người có `DAU_KY`; 1 dòng sai mã → không lưu gì, báo lỗi dòng; nhập lần 2 cho cùng người → lỗi.
  - `LeaveBalanceResourceIT`:
    - `me` cho NHAN_VIEN;
    - THUC_TAP → 403 key `leavenotapplicable`;
    - điều chỉnh không có ghi chú → 400;
    - BGĐ điều chỉnh → 403;
    - trưởng phòng xem sổ người trong phòng → 200, người phòng khác → 403.
  - Trưởng phòng là nhân viên đã nghỉ → 400 `departmentheadinactive`.
- [ ] **Step 2:** Chạy các IT. Expected: FAIL.
- [ ] **Step 3:** Cài đặt.
- [ ] **Step 4:** Chạy toàn bộ backend. Expected: PASS.
- [ ] **Step 5:** Commit `"Sổ phép, cộng phép hằng tháng, chuyển năm, hết hạn, số dư đầu kỳ"`.

### Task 3: Đơn nghỉ và luồng duyệt

**Files:**
- Create: changelog `leave_request`: `id`, `employee_id`, `type` (`PHEP_NAM`, `VIEC_RIENG`, `OM`, `KHONG_LUONG`, `NGHI_BU`), `personal_reason`, `from_date`, `to_date`, `first_part`, `last_part`, `days` numeric(5,2), `hours` numeric(6,2) (cho nghỉ bù), `reason_text`, `status` (`CHO_DUYET`, `DA_DUYET`, `TU_CHOI`, `DA_RUT`, `CHO_HUY`, `DA_HUY`), `approver_kind` (`TRUONG_NHOM`, `TRUONG_PHONG`, `BGD`, `QUAN_TRI`), `approver_employee_id`, `decided_by`, `decided_at`, `decision_note`, `on_behalf` boolean, `sick_doc_id` (FK `employee_document`), `created_at`, `submitted_by`. Kèm `_aud`.
- Create:
  - `service/leave/ApproverResolver.java`
  - `service/leave/LeaveRequestService.java`
  - `service/leave/LeaveNotifier.java` (dùng `ReminderMailer`; lỗi gửi chỉ ghi log)
  - `service/leave/LeaveTerminationHook.java`
  - `web/rest/LeaveRequestResource.java`
- Modify: `TerminationService` (gọi hook khi **áp dụng** nghỉ việc và khi **hoàn tác**); `LeaveAccrualJob` (việc hằng ngày 00:20 chuyển đơn chờ có người duyệt đã nghỉ hoặc bị khóa).

**Interfaces:**
- Consumes: Task 1 (`LeaveDays`, `PersonalLeaveReason`, `SickLeaveLimit`, `LeaveLedgerMath`), Task 2 (`LeaveLedgerService`, `LeaveSetting`), `WorkCalendarService`, `FileStorageService`, `PendingTerminationGuard`/lịch nghỉ việc đã hẹn.
- Produces:
  - `record Approver(ApproverKind kind, Long employeeId /*null nếu BGD hoặc QUAN_TRI*/)`.
  - `Approver ApproverResolver.resolve(Employee requester, BigDecimal workingDays, boolean overtime)`: theo Global Constraints; bỏ qua người duyệt là chính người xin, đã nghỉ việc, hoặc không có tài khoản đang hoạt động.
  - `boolean ApproverResolver.canDecide(User user, LeaveRequest r)`: người duyệt được lưu trên đơn, hoặc bất kỳ BGĐ nếu `kind = BGD`, hoặc ADMIN nếu `kind = QUAN_TRI`, hoặc HR/ADMIN duyệt thay.
  - API (`/api/leave/requests`):
    - `POST` (người đang đăng nhập, loại NHAN_VIEN): kiểm tra
      - loại hợp lệ;
      - `days > 0`;
      - không trùng đơn `CHO_DUYET`/`DA_DUYET`/`CHO_HUY`;
      - ngày quá khứ ≤ `past_request_days`;
      - không sau ngày làm việc cuối đã hẹn (`pendingtermination`);
      - `PHEP_NAM`: `days ≤ requestable − tổng đơn PHEP_NAM đang chờ`;
      - `NGHI_BU`: giờ = ngày × 8, `≤ số dư − đang chờ`;
      - `VIEC_RIENG`: `days ≤ PersonalLeaveReason.days`;
      - `OM`: trả cảnh báo `sickOverLimit` nếu vượt hạn mức, không chặn.

      Trả đơn kèm người duyệt; gửi email.
    - `POST /{id}/withdraw` (người gửi, khi `CHO_DUYET`).
    - `POST /{id}/approve` `{note?}`: khóa sổ; trừ quỹ với `DUYET_DON`; số dư không đủ thì 400 `insufficientbalance`, có số còn lại trong detail.
    - `POST /{id}/reject` `{note}`: bắt buộc lý do.
    - `POST /{id}/cancel-request` (người gửi, khi `DA_DUYET` và `from_date` ≥ hôm nay) → `CHO_HUY`.
    - `POST /{id}/cancel-approve`, `POST /{id}/cancel-reject` (người duyệt).
    - `POST /{id}/cancel` (HR/ADMIN, kể cả ngày đã qua) → `DA_HUY` và hoàn quỹ `HUY_DON`.
    - `POST /{id}/sick-document` (multipart, người gửi/HR; PDF/ảnh theo kiểm tra magic bytes đã có).
    - `GET /mine`; `GET /inbox` (các đơn `CHO_DUYET`/`CHO_HUY` mà người đăng nhập `canDecide`); `POST /bulk-approve` `{ids}`.
    - `GET /{id}`: lý do và giấy ốm chỉ cho người gửi, người duyệt của đơn, HR, ADMIN.
  - `LeaveTerminationHook`:
    - `onApplied(Employee e, LocalDate lastWorkingDay)`:
      - đơn `CHO_DUYET` hoặc `CHO_HUY` của người đó → `DA_HUY` (ghi lý do "Nhân viên nghỉ việc");
      - đơn `DA_DUYET` có ngày sau ngày làm việc cuối → `DA_HUY` và hoàn quỹ;
      - chuyển các đơn mà người đó đang là người duyệt lên cấp kế tiếp;
      - chốt số dư bằng một dòng `NGHI_VIEC` (âm toàn bộ số dư cả hai quỹ), lưu số đã chốt để kế toán xem.
    - `onUndone(Employee e)`: ghi dòng `HOAN_TAC` đảo ngược dòng `NGHI_VIEC`. Đơn đã tự hủy không tự khôi phục; thông báo HR.

- [ ] **Step 1: Viết test** (`LeaveRequestFlowIT`, `ApproverResolverTest`).
  - Định tuyến: trưởng nhóm duyệt đơn 3 ngày; 3,5 ngày → BGĐ.
  - Đặc biệt:
    - không có trưởng nhóm → trưởng phòng;
    - trưởng phòng xin nghỉ 1 ngày → BGĐ;
    - `manager` = chính mình → lên trưởng phòng (Review Focus 3);
    - người duyệt đã nghỉ việc → lên cấp kế tiếp;
    - BGĐ xin nghỉ → QUAN_TRI.
  - Vòng đời:
    - gửi đơn: số dư chưa bị trừ;
    - duyệt: có dòng `DUYET_DON`, số dư giảm;
    - từ chối không ghi chú → 400;
    - rút đơn: không có dòng sổ;
    - yêu cầu hủy rồi đồng ý: có `HUY_DON`, số dư trở lại;
    - HR hủy đơn đã qua ngày → 200.
  - Kiểm tra số dư:
    - dư 0,5 → xin 1,5 được; xin 2 → 400;
    - hai đơn 1 ngày đang chờ khi `requestable = 1,5` → đơn thứ hai 400 (tính cả đơn đang chờ);
    - hai lần duyệt song song (2 luồng) cho hai đơn cùng người tổng vượt → đúng một lần thành công (Review Focus 2).
  - Duyệt sau 01/04: phần `NAM_TRUOC` đã hết hạn nên không dùng được (Review Focus 5).
  - Đơn 28/12/2026–05/01/2027 duyệt ngày 20/12 → trừ hết 5 ngày làm việc từ số dư hiện tại, qua 01/01 vẫn đúng (Review Focus 5).
  - Trùng ngày với đơn khác → 400 `overlap`.
  - Đơn sau ngày làm việc cuối đã hẹn → 400 `pendingtermination`.
  - `VIEC_RIENG` KET_HON 4 ngày → 400.
  - `OM` vượt hạn mức → 201 có cờ cảnh báo.
  - THUC_TAP gửi đơn → 403.
  - Người ngoài xem lý do đơn → không thấy lý do, không thấy giấy ốm.
  - Nghỉ việc (Review Focus 4):
    - áp dụng nghỉ việc → đơn tương lai `DA_HUY` và được hoàn quỹ;
    - đơn mà người này duyệt → chuyển người duyệt;
    - có dòng `NGHI_VIEC`;
    - hoàn tác → có dòng `HOAN_TAC`.
  - Email: gửi tới người duyệt khi gửi đơn và tới người gửi khi có quyết định (giả mailer); mailer lỗi vẫn duyệt được.
- [ ] **Step 2:** Chạy. Expected: FAIL.
- [ ] **Step 3:** Cài đặt.
- [ ] **Step 4:** Chạy toàn bộ backend. Expected: PASS.
- [ ] **Step 5:** Commit `"Đơn nghỉ, người duyệt theo cấp, duyệt/từ chối/hủy, nghỉ việc"`.

### Task 4: Đơn làm thêm giờ và quỹ nghỉ bù

**Files:**
- Create: changelog `overtime_request`: `id`, `employee_id`, `work_date`, `start_time`, `end_time`, `planned_hours`, `actual_hours`, `rate`, `night` boolean, `content`, `status` (`CHO_DUYET`, `DA_DUYET`, `TU_CHOI`, `DA_RUT`, `CHO_XAC_NHAN_THEM`, `HOAN_TAT`, `DA_HUY`), `compensation` (`TRA_TIEN`, `NGHI_BU`), `approver_*` (như đơn nghỉ), `decided_by/at`, `confirmed_at`, `auto_confirmed` boolean. Kèm `_aud`.
- Create: `service/leave/OvertimeService.java`, `web/rest/OvertimeResource.java`.
- Modify: `LeaveAccrualJob`: việc 00:30 ngày `ot_confirm_day` hằng tháng tự xác nhận các đơn tháng trước chưa xác nhận, lấy giờ thực tế = giờ đăng ký.

**Interfaces:**
- Consumes: Task 1 (`OvertimeRules`), Task 2 (`LeaveLedgerService`, `LeaveSetting`), Task 3 (`ApproverResolver.resolve(e, 0, true)`, `canDecide`, `LeaveNotifier`), `PayrollPeriodStatus.DA_DUYET` (kỳ lương đã duyệt).
- Produces (`/api/leave/overtime`):
  - `POST` đăng ký `{workDate, startTime, endTime, content}`:
    - tính giờ, hệ số, cờ đêm;
    - kiểm tra giới hạn với tổng giờ đã duyệt/thực tế cùng tháng và năm của ngày làm (`overtimelimit`, message có số liệu);
    - người duyệt là trưởng nhóm theo chuỗi.
  - `POST /{id}/approve` `{compensation}` (bắt buộc); `POST /{id}/reject` `{note}`; `POST /{id}/withdraw`.
  - `POST /{id}/confirm` `{actualHours}` (người gửi, sau ngày làm):
    - `≤ planned` → `HOAN_TAT`;
    - `> planned` → `CHO_XAC_NHAN_THEM`, người duyệt duyệt phần chênh (`/approve-extra`, `/reject-extra` thì giữ số giờ đăng ký).
  - Khi `HOAN_TAT` mà `compensation = NGHI_BU` → ghi `LAM_THEM` +giờ thực tế vào quỹ `NGHI_BU` (ngăn `NAM_NAY`).
  - `POST /{id}/compensation` `{compensation}` (người duyệt, HR): đổi trả tiền ↔ nghỉ bù khi kỳ lương tháng của ngày làm **chưa** `DA_DUYET`. Nếu đổi khỏi `NGHI_BU` sau khi đã cộng thì ghi dòng đảo; nếu số dư nghỉ bù không đủ để đảo thì 400.
  - `GET /mine`, `GET /inbox`.

- [ ] **Step 1: Viết test** (`OvertimeFlowIT`).
  - Đăng ký thứ Năm 18:00–21:00 → 3 giờ, 150%, người duyệt là trưởng nhóm.
  - Duyệt không chọn hình thức → 400.
  - Duyệt nghỉ bù, xác nhận 3 giờ → `HOAN_TAT`, quỹ nghỉ bù +3.
  - Xác nhận 4 giờ → `CHO_XAC_NHAN_THEM`; duyệt thêm → +4.
  - Tháng đã có 38 giờ, đăng ký 3 → 400 `overtimelimit`.
  - Ngày 05 tháng sau: việc tự động xác nhận, `auto_confirmed`.
  - Đổi hình thức sau khi kỳ lương `DA_DUYET` → 400.
  - Đổi từ nghỉ bù sang trả tiền → có dòng đảo.
  - Nghỉ bù 1 ngày khi quỹ 6 giờ → 400; quỹ 8 giờ → được, quỹ còn 0.
  - THUC_TAP → 403.
- [ ] **Step 2:** Chạy. Expected: FAIL.
- [ ] **Step 3:** Cài đặt.
- [ ] **Step 4:** Chạy toàn bộ backend. Expected: PASS.
- [ ] **Step 5:** Commit `"Đăng ký làm thêm giờ, trả tiền hoặc nghỉ bù, giới hạn theo luật"`.

### Task 5: API lịch tháng theo quyền xem

**Files:**
- Create: `service/leave/LeaveCalendarService.java`, `web/rest/LeaveCalendarResource.java`, `service/leave/LeaveVisibility.java`.
- Test: `LeaveCalendarResourceIT`.

**Interfaces:**
- Consumes: Task 3 (đơn), lịch sử vị trí (`WorkAssignment` loại `CONG_TAC`), `TerminationRecord` (`SCHEDULED` và `APPLIED` → "Nghỉ việc từ dd/MM"), `WorkCalendarService` (ngày lễ).
- Produces:
  - `Set<Long> LeaveVisibility.visibleEmployeeIds(User viewer)`:
    - chính mình;
    - người có `manager` là mình;
    - người thuộc phòng có `head` là mình;
    - tất cả nếu là BGĐ, HR hoặc ADMIN.

    Kế toán chỉ thấy chính mình.
  - `GET /api/leave/calendar?month=YYYY-MM&departmentId=&type=` → `{ days: [{date, weekend, holidayName?}], items: [{employeeId, employeeName, department, type /*PHEP_NAM|VIEC_RIENG|OM|KHONG_LUONG|NGHI_BU|CONG_TAC|NGHI_VIEC*/, from, to, firstPart, lastPart, pending /*bool*/}] }`. Chỉ trả người trong tập được xem; **không có lý do**.
  - Đơn `CHO_DUYET` có `pending=true`; đơn `DA_DUYET` và `CHO_HUY` hiện bình thường. Đơn rút, từ chối hoặc đã hủy không hiện.

- [ ] **Step 1: Viết test.**
  - NHAN_VIEN chỉ thấy mình.
  - Trưởng nhóm thấy người trong nhóm, không thấy người khác.
  - Trưởng phòng thấy cả phòng.
  - Người vừa là trưởng nhóm vừa là trưởng phòng thấy hợp hai tập.
  - BGĐ và HR thấy tất cả; Kế toán chỉ thấy mình.
  - JSON không có trường lý do.
  - Có mục `CONG_TAC` từ lịch sử vị trí và `NGHI_VIEC` từ lịch đã hẹn.
  - Ngày lễ có tên.
  - Lọc `departmentId` ngoài tập được xem → chỉ trả phần giao.
- [ ] **Step 2:** Chạy. Expected: FAIL.
- [ ] **Step 3:** Cài đặt.
- [ ] **Step 4:** Chạy. Expected: PASS.
- [ ] **Step 5:** Commit `"Lịch nghỉ tháng theo quyền xem"`.

### Task 6: Giao diện nhân viên và người duyệt

**Files:**
- Create: `app/src/main/webapp/app/modules/leave/`:
  - `my-leave.tsx` (trang "Nghỉ phép"): thẻ số dư, form tạo đơn, bảng đơn của tôi, lịch của tôi (dùng `month-calendar.tsx`), sổ phép của tôi;
  - `leave-form.tsx`: loại, từ–đến, buổi, lý do việc riêng, đính kèm giấy ốm, hiện "Sau khi duyệt còn …";
  - `overtime-form.tsx`;
  - `approval-inbox.tsx` (trang "Duyệt đơn"): đơn nghỉ và làm thêm chờ duyệt, xem số dư người xin, duyệt/từ chối, duyệt nhiều đơn, chọn trả tiền/nghỉ bù;
  - `month-calendar.tsx`: lưới 7 cột kiểu A, gộp "+N người", màu theo loại, đơn chờ hiện mờ, ngày nghỉ và lễ nền xám;
  - `leave-api.ts`; `leave-labels.ts`.
- Modify: `menu-items.ts`:
  - "Nghỉ phép" `/nghi-phep` (người NHAN_VIEN có hồ sơ; xác định bằng `hasEmployeeProfile` + cờ `leaveApplicable` mới trên `GET /api/account`);
  - "Duyệt đơn" `/duyet-don` (khi `GET /api/account` có `isApprover` hoặc vai trò HR/ADMIN/BGD).
- Modify: `routes.tsx`, `AccountResource` (2 cờ trên), `detail-api.ts` (thông báo lỗi tiếng Việt cho các key mới).
- Test: Vitest cho form (số ngày tính ở client chỉ để hiển thị; số liệu chính lấy từ server), inbox, `month-calendar`, menu.

**Interfaces:**
- Consumes: API của Task 2–5.
- Produces: component `MonthCalendar({ month, days, items, onMonthChange, onDayClick })`, Task 7 dùng lại.

- [ ] **Step 1: Viết test.**
  - Form: chọn `VIEC_RIENG` thì hiện chọn lý do, KET_HON → tối đa 3 ngày; chọn `OM` thì hiện ô đính kèm; đơn nhiều ngày thì "buổi" chỉ cho chiều ở ngày đầu, sáng ở ngày cuối.
  - Lỗi `insufficientbalance` hiện số còn lại.
  - Inbox: nút duyệt làm thêm giờ bắt buộc chọn hình thức; từ chối bắt buộc lý do.
  - `MonthCalendar`: 5 người cùng ngày → hiện 3 + "+2 người"; đơn `pending` có class mờ; ngày lễ có tên.
  - Menu: THUC_TAP không có "Nghỉ phép"; người có `isApprover` có "Duyệt đơn".
- [ ] **Step 2:** `cd app && npm test`. Expected: FAIL.
- [ ] **Step 3:** Cài đặt (react-bootstrap, theme hiện có, tiếng Việt, vùng bấm ≥ 44px).
- [ ] **Step 4:** `npm test`, `npx tsc --noEmit`, `npm run lint`. Expected: PASS.
- [ ] **Step 5:** Commit `"Giao diện nghỉ phép, làm thêm giờ, hộp duyệt đơn"`.

### Task 7: Lịch nghỉ, trang HR, tổng hợp kế toán, nhắc việc, README

**Files:**
- Create (frontend):
  - `modules/leave/leave-calendar-page.tsx` ("Lịch nghỉ" `/lich-nghi`, lọc phòng/loại; hiện với BGĐ, HR, ADMIN, người là trưởng nhóm hoặc trưởng phòng);
  - `modules/leave/hr-leave.tsx` (HR/ADMIN: số dư cả công ty, điều chỉnh, nhập số dư đầu kỳ, cài đặt);
  - tab "Nghỉ phép" trong `entities/employee/detail/` (số dư, đơn, sổ của người đó; HR, ADMIN, trưởng nhóm, trưởng phòng);
  - `modules/leave/accounting-summary.tsx` ("Tổng hợp nghỉ & làm thêm" dưới "Bảng lương", Kế toán, HR, ADMIN).
- Create (backend):
  - `service/leave/LeaveAccountingSummaryService.java` (mỗi người NHAN_VIEN một dòng trong tháng: ngày phép năm, việc riêng, ốm, không lương, nghỉ bù; giờ làm thêm trả tiền theo 150/200/300, cờ đêm; số chốt `NGHI_VIEC` trong tháng: dương = trả tiền, âm = trừ lương);
  - `web/rest/LeaveAccountingResource.java` (`GET /api/leave/accounting?month=`, `GET …/export.xlsx` theo kiểu xuất Excel đã có).
- Modify: nhắc việc hằng ngày (`ReminderService`/`ReminderJob`): thêm mục "Đơn chờ bạn duyệt (N)" cho người duyệt (gồm BGĐ với đơn `BGD`, Quản trị với đơn `QUAN_TRI`).
- Modify: `README.md`: mục "Nghỉ phép và đơn từ" gồm quy tắc, luồng duyệt, việc chạy tự động, nhập số dư đầu kỳ, cài đặt, tổng hợp cho kế toán; cập nhật số test.
- Test: `LeaveAccountingResourceIT`, `ReminderJobIT` (mục mới), Vitest cho trang lịch, trang HR và tổng hợp.

**Interfaces:**
- Consumes: Task 2–6 (`MonthCalendar`, các API).

- [ ] **Step 1: Viết test.**
  - Tổng hợp tháng 10/2026:
    - 1 người nghỉ phép 2 ngày, không lương 1 ngày, làm thêm 3 giờ 150% trả tiền và 2 giờ 200% nghỉ bù → dòng có phép 2, không lương 1, giờ 150% = 3, giờ 200% = 0 (vì nghỉ bù);
    - người nghỉ việc trong tháng có số chốt;
    - THUC_TAP không có dòng;
    - BGĐ → 403;
    - Excel có đúng tiêu đề tiếng Việt.
  - Nhắc việc: trưởng nhóm có 2 đơn chờ → email có "Đơn chờ bạn duyệt (2)".
  - Vitest:
    - trang lịch gọi API theo tháng và lọc;
    - trang HR: điều chỉnh bắt buộc ghi chú;
    - tab trên hồ sơ ẩn với người không đủ quyền.
- [ ] **Step 2:** Chạy. Expected: FAIL.
- [ ] **Step 3:** Cài đặt.
- [ ] **Step 4:** Chạy toàn bộ: backend verify, `npm test`, `tsc`, `lint`, build prod. Expected: PASS.
- [ ] **Step 5:** Commit `"Lịch nghỉ, trang HR, tổng hợp cho kế toán, nhắc đơn chờ duyệt"`.

## Kiểm tra cuối

- [ ] Toàn bộ suite xanh.
- [ ] Thử trên `docker compose up` ở máy với dữ liệu mẫu:
  - nhập số dư đầu kỳ;
  - nhân viên xin 2 ngày → trưởng nhóm duyệt → lịch hiện đúng → số dư giảm;
  - xin 4 ngày → BGĐ duyệt;
  - đăng ký làm thêm → duyệt nghỉ bù → xác nhận → dùng nghỉ bù;
  - tổng hợp tháng cho kế toán khớp.

  Gỡ stack sau khi kiểm.
