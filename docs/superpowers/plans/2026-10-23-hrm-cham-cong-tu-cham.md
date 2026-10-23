# Chấm công phần 2 – Nhân viên tự chấm công – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nhân viên tự bấm Vào/Ra trên điện thoại, được xác minh bằng IP văn phòng hoặc GPS. Lần chấm không chắc chắn chờ người có quyền duyệt; nhân viên gửi giải trình khi quên chấm hoặc bị từ chối. Công ngày, chốt công và file bảng lương giữ nguyên như phần 1.

**Architecture:**
- Mở rộng bảng `attendance_punch` của phần 1: thêm trạng thái, phương thức, điểm chấm, vị trí và thông tin duyệt. Công ngày chỉ đọc các lần "được tính".
- Xác minh dùng lõi `PunchValidator` có sẵn, nối với `WorkSite` (tọa độ, bán kính, `officeIps`) và phân công theo ngày. Bước thiết bị và bước lệch giờ được tắt cho web.
- Giải trình là bảng riêng. Khi được duyệt, giải trình sinh lần chấm nguồn `GIAI_TRINH`.
- Một job hằng ngày xóa tọa độ và IP quá hạn.
- Giao diện có: màn chấm trên điện thoại, phần bổ sung cho "Bảng công của tôi", nguồn và nút duyệt trên màn gõ theo ngày và bảng công tháng, một mục trong hộp Duyệt đơn, một khối trong Cài đặt.

**Tech Stack:** Java 21 / Spring Boot 4, `core` Java 17 (không Spring), PostgreSQL + Liquibase + Envers, React 19 + Vitest + react-bootstrap.

**Spec:** `docs/superpowers/specs/2026-10-23-hrm-cham-cong-tu-cham-design.md`. Bản phác: `docs/superpowers/mockups/2026-10-23-cham-cong-tu-cham.html`.

## Global Constraints

- **Điều kiện bắt đầu.** Chỉ triển khai khi phần 1 (`feature/cham-cong-gio`) và trang Cài đặt (`WorkSite.latitude/longitude/radiusM/officeIps`, `WorkSiteService.parseOfficeIps`) đã có trên main. Tạo nhánh triển khai từ main lúc đó.
- **Ai được chấm.** Chỉ `NHAN_VIEN` đang làm việc, trong khoảng từ ngày vào làm đến ngày làm việc cuối.
  - Người khác: 400 `attendancenotapplicable`.
  - Tài khoản không có hồ sơ: 404.
  - Hồ sơ đã lưu trữ: chặn bằng `ArchivedEmployeeGuard`.
- **Giờ chấm.** Lấy giờ máy chủ, múi Asia/Ho_Chi_Minh (`Clock` bean). Ngày công = ngày theo giờ đó.
- **IP.** Đọc từ kết nối. Chỉ tin `X-Forwarded-For` qua cấu hình forward headers của Spring (`server.forward-headers-strategy`).
- **Bảng xác minh, theo thứ tự:**
  1. Từ xa → `CHAP_NHAN`, phương thức `TU_XA`.
  2. IP khớp một mục trong `officeIps` (IP hoặc CIDR) → `CHAP_NHAN`, phương thức `IP`.
  3. GPS trong `radiusM` (mặc định 200) và sai số ≤ 100 m → `CHAP_NHAN`, phương thức `GPS`.
  4. Sát ranh giới (trong bán kính + sai số) hoặc sai số > 100 m → `CHO_DUYET`.
  5. Xa hơn → `TU_CHOI`, kèm câu "Bạn đang cách <điểm> <x> m/km. Hôm nay bạn phải chấm tại <điểm>".
  6. Không có vị trí và IP không khớp → `CHO_DUYET`, lý do "Không có vị trí".
  7. Điểm chưa khai báo tọa độ lẫn IP → `CHO_DUYET`, lý do "Địa điểm chưa khai báo".
  8. Không xác định được nơi phải chấm → `CHO_DUYET`.
- **Lưu và chặn trùng.**
  - Lần bị từ chối vẫn được lưu.
  - Cùng người, cùng loại, trong 1 phút → không ghi thêm, trả lại kết quả của lần đó.
  - Tháng đã chốt: 400, dùng khóa lỗi tháng đã chốt của phần 1.
  - Chưa đồng ý thông báo vị trí: 400 `attendanceconsentrequired`.
- **Lần chấm được tính công:** `GO_TAY` (luôn tính), `APP` ở trạng thái `CHAP_NHAN` hoặc `DA_DUYET`, và `GIAI_TRINH`. Lần `CHO_DUYET` và `TU_CHOI` không tính. Khi chốt, còn lần chờ duyệt thì xử lý như ô đỏ: có cảnh báo, tính như chưa chấm.
- **Người duyệt** = người được gõ giờ cho người đó vào ngày đó: `AttendanceAccess.canEnter(login, target, day)`, tức trưởng nhóm, trưởng nhóm onsite đúng ngày onsite, HR, Quản trị.
  - Không tự duyệt.
  - Từ chối bắt buộc ghi lý do.
  - Tháng đã chốt thì không duyệt được.
- **Giải trình.**
  - Chỉ cho ngày đã qua, hoặc hôm nay với giờ đã qua, trong tháng chưa chốt.
  - Có giờ vào và/hoặc giờ ra đề xuất; lý do bắt buộc. Giờ phải không ở tương lai.
  - Mỗi người mỗi ngày tối đa một giải trình `DANG_CHO`; vượt thì 400 `explanationpending`.
  - Trạng thái: `DANG_CHO` → `DA_DUYET` | `TU_CHOI` | `DA_RUT`.
  - Duyệt → sinh lần chấm `GIAI_TRINH` cho mỗi giờ đề xuất.
  - Email báo người duyệt khi có giải trình mới: gửi trưởng nhóm, nếu không có thì trưởng nhóm onsite, nếu không có thì HR. Email báo nhân viên khi có kết quả. Lỗi gửi chỉ ghi log.
- **Riêng tư.**
  - Phải đồng ý thông báo ở lần chấm đầu. Lưu thời điểm và phiên bản. Đổi nội dung thông báo không bắt đồng ý lại.
  - Tọa độ, sai số và IP được đặt về null sau `location_retention_days` (mặc định 90). Giữ lại điểm chấm, khoảng cách, phương thức, trạng thái, lý do.
  - Tọa độ và IP chỉ trả về cho: chính người đó, người duyệt ngày đó, HR, Quản trị.
- **Ràng buộc chung của dự án.**
  - Liquibase chỉ thêm changeset mới, kèm cột `_aud`.
  - Lỗi nghiệp vụ dùng `EmployeeRuleException(message, key)` → 400 `error.<key>`. Mỗi key mới có câu tiếng Việt ở `webapp/i18n/vi/global.json` (khối `error`) và `ERROR_TEXT` trong `entities/employee/detail/detail-api.ts`.
  - Bảng mới có FK tới `employee` phải nằm trong `EmployeeDeletionService.HANDLED_TABLES`.
  - Danh sách chờ duyệt lọc bỏ hồ sơ đã lưu trữ.
  - Giao diện tiếng Việt, ngày `dd/MM/yyyy`, giờ `HH:mm`, vùng bấm ≥ 44px, màu và font canvas.
  - Commit kết thúc bằng `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Không push. Không `git stash`.
- **Lệnh chạy.**
  - Chuẩn bị: `export JAVA_HOME=/opt/homebrew/opt/openjdk@21`; `export DOCKER_HOST=unix://$HOME/.docker/run/docker.sock` (IT cần tắt sandbox).
  - Lõi: `mvn -q -pl core test -Dtest=<Class>`.
  - IT: `mvn -q -pl app -am verify -Dskip.npm -Dskip.installnodenpm -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=<IT> -Dfailsafe.failIfNoSpecifiedTests=false`.
  - Toàn bộ backend: bỏ các cờ `-Dtest`/`-Dit.test`.
  - Frontend: `cd app && npm test && npx tsc --noEmit && npm run lint`.
  - IT cần giờ cố định: dùng một `@TestConfiguration` lồng có `@Primary Clock.fixed(...)`.

## Review Focus

1. **Trưởng nhóm sửa giờ trên màn gõ theo ngày của một người đã tự chấm.** Kỳ vọng: lần tự chấm còn nguyên, không bị nhân đôi thành lần gõ tay, không bị xóa. Ô nhập chỉ chứa giờ gõ tay. Test ở Task 3: `manualSaveKeepsSelfPunches`.
2. **Người onsite có hai phân công chồng ngày**, hoặc phân công onsite kết thúc giữa tháng. Kỳ vọng: nơi phải chấm và người duyệt đều tính theo đúng ngày bấm. Test ở Task 2: `siteFollowsAssignmentOfThatDay`, và ở Task 3: `onsiteLeadApprovesOnlyOnsiteDays`.
3. **Điện thoại bị chặn vị trí nhưng nằm trong mạng văn phòng.** Kỳ vọng: chấp nhận theo IP, không chờ duyệt. Ngoài văn phòng thì chờ duyệt, không từ chối. Test ở Task 2: `noLocationInsideOfficeIpAccepted`, `noLocationOutsideIsPending`.
4. **Giải trình chỉ có giờ ra, cho một ngày đã có giờ vào tự chấm đang chờ duyệt.** Kỳ vọng: duyệt giải trình chỉ thêm giờ ra; lần vào vẫn chờ duyệt riêng. Test ở Task 4: `explanationOutOnlyKeepsPendingIn`.
5. **Hai lần bấm Vào gần như cùng lúc** (bấm đúp, mạng chậm, gửi lại). Kỳ vọng: chỉ có một lần chấm. Test ở Task 2: `doubleTapWithinMinuteStoresOnce`, gồm cả hai request gửi song song.

---

### Task 1: Lõi – IP theo CIDR, xác minh cho web

**Files:**
- Modify:
  - `core/src/main/java/vn/doit/hr/attendance/WorkSite.java`: `hasIp(String ip)` khớp cả IP đơn lẫn CIDR (IPv4/IPv6) trong `publicIps`.
  - `core/src/main/java/vn/doit/hr/attendance/PunchValidator.java`: thêm `Policy.web()`, không kiểm tra thiết bị và không kiểm tra lệch giờ (`maxClockSkew = null` nghĩa là bỏ qua). Câu từ chối theo Global Constraints, với khoảng cách hiển thị "x m" khi dưới 1000 m và "x,y km" khi từ 1000 m.
- Test:
  - `core/src/test/java/vn/doit/hr/attendance/WorkSiteTest.java` (tạo mới nếu chưa có)
  - `core/src/test/java/vn/doit/hr/attendance/PunchValidatorTest.java`

**Interfaces:**
- Produces: `PunchValidator.Policy.web()`. Ngoài ra `PunchVerdict` (`status`, `method`, `site`, `distanceMeters`, `reasons`) giữ nguyên.

- [ ] **Step 1: Viết test.**
  - `cidrMatches`: `"203.0.113.0/24"` khớp `"203.0.113.77"` và không khớp `"203.0.114.1"`. IP đơn khớp đúng chính nó. `"2001:db8::/32"` khớp `"2001:db8::1"`.
  - `webPolicySkipsDeviceAndSkew`: `deviceId` null, `clientTime` cách giờ máy chủ 2 giờ, GPS trong bán kính → `ACCEPTED`.
  - `rejectMessageKm`: cách 2400 m → câu chứa "2,4 km" và tên điểm. Cách 650 m → câu chứa "650 m".
  - `noLocationNoIpIsReview`: không có tọa độ, IP không khớp → `NEEDS_REVIEW`, lý do "Không có vị trí".
- [ ] **Step 2:** `mvn -q -pl core test -Dtest='WorkSiteTest,PunchValidatorTest'` → FAIL.
- [ ] **Step 3:** Cài. Khớp CIDR bằng `InetAddress.getByAddress` trên các byte, không bao giờ gọi DNS: chỉ chấp nhận chuỗi số.
- [ ] **Step 4:** Chạy `mvn -q -pl core test` → PASS (cả lõi).
- [ ] **Step 5:** Commit "Lõi chấm công: IP văn phòng theo CIDR, chính sách xác minh cho web".

### Task 2: Dữ liệu, đồng ý thông báo, API tự chấm

**Files:**
- Create:
  - Changelog `20261101000100_attendance_self_punch.xml`:
    - `attendance_punch` thêm các cột `status varchar(20) not null default 'CHAP_NHAN'`, `method varchar(10)`, `site_id bigint` (FK `work_site`), `distance_m integer`, `latitude numeric(9,6)`, `longitude numeric(9,6)`, `accuracy_m numeric(7,1)`, `client_ip varchar(45)`, `review_reason varchar(500)`, `decided_by varchar(50)`, `decided_at timestamp`, `decision_note varchar(500)`, `explanation_id bigint`. Thêm các cột tương ứng vào `attendance_punch_aud`.
    - Bảng `attendance_consent`: `id`, `employee_id` (FK, unique), `accepted_at`, `notice_version`.
    - Bảng cài đặt: thêm `attendance_notice_text text`, `attendance_notice_version integer default 1`, `location_retention_days integer default 90`. Đặt vào bảng cài đặt chấm công của phần 1; nếu đã có bảng cài đặt chung của trang Cài đặt thì đặt vào đó. Xem `AttendanceSettingResource` / `SettingsService` hiện có và đặt theo chỗ đã chứa cài đặt chấm công.
  - Enum `domain/enumeration/AttendancePunchStatus.java` (`CHAP_NHAN`, `CHO_DUYET`, `TU_CHOI`, `DA_DUYET`) và `AttendancePunchMethod.java` (`IP`, `GPS`, `TU_XA`).
  - `domain/AttendanceConsent.java`, `repository/AttendanceConsentRepository.java`
  - `service/attendance/SelfPunchService.java`
  - `service/attendance/PunchSiteResolver.java`: nơi phải chấm ngày D. Nối `SiteDirectory` và `AssignmentHistory` của lõi với `WorkAssignment` và `WorkSite`, theo cách phần 1 chia khối Văn phòng/Onsite (`AttendanceDayService.groupKey`) và `CurrentSite`.
  - `web/rest/AttendanceSelfResource.java`
- Modify:
  - `domain/enumeration/AttendancePunchSource.java`: thêm `GIAI_TRINH`.
  - `domain/AttendancePunch.java`: thêm các trường mới, có getter/setter.
  - Màn và API cài đặt chấm công: đọc/ghi nội dung thông báo và số ngày lưu (chỉ Quản trị ghi). Khi nội dung thông báo đổi, `attendance_notice_version` tăng 1.
  - `EmployeeDeletionService.HANDLED_TABLES`: thêm `attendance_consent`, xóa khi xóa hẳn.
- Test: `app/src/test/java/vn/doit/hrm/web/rest/AttendanceSelfPunchIT.java`

**Interfaces:**
- Consumes: Task 1 (`Policy.web()`, `hasIp` CIDR). Phần 1: `AttendanceMonthService.isClosed(YearMonth)`, `AttendanceAccess`, `ArchivedEmployeeGuard`.
- Produces:
  - `POST /api/attendance/punches` với `{kind: "VAO"|"RA", latitude?, longitude?, accuracy?}` → `PunchResultDTO(Long id, String kind, @JsonFormat("HH:mm") LocalTime time, String status, String method, String siteName, Integer distanceM, String message)`.
  - `GET /api/attendance/punches/today` → `TodayDTO(LocalDate day, String siteName, String siteKind /*VAN_PHONG|KHACH_HANG|CONG_TAC|TU_XA|null*/, @JsonFormat("HH:mm") LocalTime start, LocalTime end, boolean consentRequired, String noticeText, List<PunchResultDTO> punches)`.
  - `POST /api/attendance/consent` → 204.
  - `SelfPunchService.counts(AttendancePunch p)` (static boolean): quy tắc "được tính" trong Global Constraints. Task 3 dùng hàm này.

- [ ] **Step 1: Viết `AttendanceSelfPunchIT`.** Dùng Clock cố định 2026-10-23 08:03 +07:00. Văn phòng có `officeIps="203.0.113.0/24"`, tọa độ 21.0279,105.8360, bán kính 200. Dùng `MockMvc` với `.with(request -> { request.setRemoteAddr("203.0.113.77"); return request; })`.
  - `consentRequiredFirst`: chưa đồng ý → 400 `error.attendanceconsentrequired`. Sau `POST /consent` → 200.
  - `officeIpAccepted`: trả `status=CHAP_NHAN`, `method=IP`, giờ 08:03 (giờ máy chủ, bỏ qua mọi giờ client).
  - `gpsInsideAccepted`, `gpsNearEdgeOrLowAccuracyPending` (sai số 140 m → `CHO_DUYET`).
  - `gpsFarRejectedButStored`: cách 2,4 km → `TU_CHOI`, message chứa "2,4 km"; lần chấm có trong DB.
  - `noLocationInsideOfficeIpAccepted`, `noLocationOutsideIsPending` (Review Focus 3).
  - `remoteAccepted`: phân công `TU_XA` → `TU_XA`, `CHAP_NHAN`, không cần tọa độ.
  - `siteFollowsAssignmentOfThatDay`: onsite tại khách hàng K từ 01/10 đến 22/10, văn phòng từ 23/10 → hôm nay chấm tại văn phòng. Với Clock 22/10 → tại K.
  - `undeclaredSitePending`: điểm không có tọa độ lẫn IP → `CHO_DUYET` "Địa điểm chưa khai báo".
  - `doubleTapWithinMinuteStoresOnce`: hai `POST VAO` cách nhau 30 giây (Clock tăng) → một dòng; lần thứ hai trả cùng `id`. Thêm hai request song song qua `ExecutorService` → vẫn một dòng (khóa hồ sơ bằng `findByIdForUpdate`).
  - `closedMonthRejected`: tháng đã chốt → 400.
  - `notApplicable`: hồ sơ `THUC_TAP` → 400 `error.attendancenotapplicable`. Tài khoản không có hồ sơ → 404. Hồ sơ đã lưu trữ → 400 `error.employeearchived`.
  - `forwardedHeaderNotTrustedByDefault`: header `X-Forwarded-For: 203.0.113.9` từ `remoteAddr` ngoài dải → không được tính là IP văn phòng (cấu hình test không bật forward headers).
  - `todayEndpoint`: trả nơi chấm, giờ làm, `consentRequired` và danh sách lần chấm trong ngày.
- [ ] **Step 2:** Chạy IT tập trung → FAIL.
- [ ] **Step 3:** Cài.
  - `SelfPunchService.punch(login, kind, lat, lng, acc, ip)`, theo thứ tự:
    1. kiểm tra áp dụng;
    2. kiểm tra đồng ý;
    3. kiểm tra tháng đã chốt;
    4. khóa hồ sơ;
    5. kiểm tra trùng trong 1 phút;
    6. xác định nơi chấm;
    7. chạy `PunchValidator` với `Policy.web()`;
    8. lưu.
  - Gắn `site_id` theo `WorkSite` của lõi được trả về.
  - Thêm key i18n mới.
- [ ] **Step 4:** Chạy IT tập trung, sau đó các IT chấm công của phần 1 → PASS.
- [ ] **Step 5:** Commit "Tự chấm công: xác minh IP/GPS, đồng ý thông báo vị trí, chặn bấm trùng".

### Task 3: Tính công theo lần được tính, duyệt lần chấm, hiện nguồn

**Files:**
- Modify:
  - `service/attendance/AttendanceDayService`:
    - khi đọc lần chấm, chỉ lấy lần `SelfPunchService.counts(p)`;
    - `DayView` thêm `manualIn`, `manualOut` (chỉ `GO_TAY`), `punches: List<PunchView>` và `pendingCount`. Hai trường `in`/`out` hiện có vẫn là lần vào sớm nhất / ra muộn nhất trong các lần được tính;
    - `save` chỉ xóa và ghi lại `GO_TAY`, như hiện nay. Màn gõ dùng `manualIn`/`manualOut` cho ô nhập.
  - `service/attendance/AttendanceMonthService.close`: thêm vào danh sách cảnh báo những ngày có lần `CHO_DUYET`, cùng cơ chế với ô đỏ.
  - `web/rest/AttendanceResource`: thêm `POST /api/attendance/punches/{id}/approve` và `/reject` (`{note}`).
- Create:
  - `service/attendance/PunchReviewService.java`
  - DTO `PunchView(Long id, String kind, LocalTime time, String source, String status, String method, String siteName, Integer distanceM, String reviewReason, BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyM, String clientIp)`. Tọa độ và IP chỉ có giá trị khi người xem là chính người đó, người duyệt ngày đó, HR hoặc Quản trị; ngược lại là null.
- Test: `app/src/test/java/vn/doit/hrm/web/rest/AttendancePunchReviewIT.java`

**Interfaces:**
- Consumes: Task 2 (`counts`, các trường mới); `AttendanceAccess.canEnter`, `isSelf`.
- Produces:
  - `PunchReviewService.approve(String login, Long punchId)`
  - `PunchReviewService.reject(String login, Long punchId, String note)`
  - `DayView.punches`, `manualIn`, `manualOut`, `pendingCount`

- [ ] **Step 1: Viết `AttendancePunchReviewIT`.**
  - `pendingAndRejectedNotCounted`: một lần vào `CHO_DUYET` lúc 08:20 và một lần ra `CHAP_NHAN` lúc 17:05 → ngày "thiếu giờ". Duyệt lần vào → 1 công, muộn 20'.
  - `earliestAmongCounted`: gõ tay vào 08:10, tự chấm vào 07:58 `CHAP_NHAN` → vào 07:58. Thêm tự chấm 07:50 `TU_CHOI` → vẫn 07:58.
  - `manualSaveKeepsSelfPunches` (Review Focus 1): trưởng nhóm `PUT /api/attendance/day` với giờ ra 17:30 cho người đã tự chấm vào → lần `APP` còn nguyên; chỉ có một `GO_TAY` RA; `manualIn` null; `in` lấy từ lần tự chấm.
  - `noSelfApproval`: HR tự chấm `CHO_DUYET`, rồi chính HR đó duyệt → 403 hoặc 400 theo quy ước `canEnter` của phần 1.
  - `onsiteLeadApprovesOnlyOnsiteDays` (Review Focus 2): trưởng nhóm onsite của K duyệt được lần chấm ngày người đó onsite ở K; không duyệt được lần chấm ngày người đó ở văn phòng.
  - `rejectNeedsNote` → 400. `closedMonthNoReview` → 400.
  - `closeWarnsPending`: tháng có một lần `CHO_DUYET` → `close(force=false)` trả danh sách có ngày đó.
  - `locationVisibility`: trưởng phòng và kế toán xem bảng công → `latitude` và `clientIp` null. Trưởng nhóm của ngày đó → có giá trị.
- [ ] **Step 2:** Chạy IT tập trung → FAIL.
- [ ] **Step 3:** Cài. Duyệt hoặc từ chối khóa hồ sơ người được duyệt, kiểm tra lần chấm vẫn `CHO_DUYET`, rồi ghi `decided_by`, `decided_at`, `decision_note`.
- [ ] **Step 4:** Chạy IT tập trung và toàn bộ IT chấm công phần 1 → PASS.
- [ ] **Step 5:** Commit "Chấm công: chỉ tính lần chấm được chấp nhận/đã duyệt, duyệt lần chấm chờ duyệt, nguồn từng lần".

### Task 4: Giải trình, danh sách chờ duyệt, email

**Files:**
- Create:
  - Changelog `20261101000200_attendance_explanation.xml`: bảng `attendance_explanation` gồm `id`, `employee_id` (FK), `work_day`, `proposed_in time`, `proposed_out time`, `reason varchar(500) not null`, `status varchar(10)`, `decided_by`, `decided_at`, `decision_note`, `created_at`; kèm `attendance_explanation_aud`. Thêm FK `attendance_punch.explanation_id` → `attendance_explanation`. Thêm partial unique index `(employee_id, work_day) where status='DANG_CHO'`.
  - `domain/AttendanceExplanation.java` (`@Audited`), `repository/AttendanceExplanationRepository.java`, enum `ExplanationStatus`.
  - `service/attendance/ExplanationService.java`
  - `service/attendance/AttendanceNotifier.java`: dùng `ReminderMailer` sau commit, theo cách `LeaveNotifier` làm.
- Modify:
  - `AttendanceSelfResource`:
    - `POST /api/attendance/explanations`;
    - `POST /api/attendance/explanations/{id}/withdraw`;
    - `GET /api/attendance/explanations/mine?month=`.
  - `AttendanceResource`:
    - `POST /api/attendance/explanations/{id}/approve`;
    - `POST /api/attendance/explanations/{id}/reject`;
    - `GET /api/attendance/pending`.
  - `EmployeeDeletionService.HANDLED_TABLES`: thêm `attendance_explanation`.
- Test: `app/src/test/java/vn/doit/hrm/web/rest/AttendanceExplanationIT.java`

**Interfaces:**
- Consumes: Task 2 và Task 3 (`PunchReviewService`, `canEnter`, `counts`).
- Produces:
  - `ExplanationDTO(Long id, LocalDate workDay, LocalTime proposedIn, LocalTime proposedOut, String reason, String status, String decidedBy, Instant decidedAt, String decisionNote)`
  - `PendingItemDTO(String type /*PUNCH|EXPLANATION*/, Long id, Long employeeId, String employeeCode, String employeeName, LocalDate day, String summary, String detail)`

- [ ] **Step 1: Viết `AttendanceExplanationIT`.** Dùng Clock cố định 2026-10-23 10:00.
  - `submitAndApproveCreatesPunches`: gửi cho 13/10 với ra 17:12 và lý do → `DANG_CHO`. Trưởng nhóm duyệt → có lần `GIAI_TRINH` RA 17:12 gắn `explanation_id`; ngày 13/10 có 1 công.
  - `explanationOutOnlyKeepsPendingIn` (Review Focus 4).
  - `onePendingPerDay` → lần gửi thứ hai trả 400 `error.explanationpending`. Sau khi rút → gửi lại được.
  - `futureOrWrongDayRejected`: giờ ra 11:00 cho hôm nay (Clock 10:00) → 400. Ngày 24/10 → 400. Không có giờ nào → 400. Thiếu lý do → 400.
  - `closedMonthRejected` khi gửi, khi duyệt và khi từ chối.
  - `noSelfApproval`; `rejectNeedsNote`; `withdrawOnlyOwnPending`.
  - `pendingListScopedAndArchivedHidden`: trưởng nhóm thấy lần chấm chờ và giải trình của nhóm mình; không thấy nhóm khác; không thấy hồ sơ đã lưu trữ. HR thấy cả công ty.
  - `emailsSent`: giải trình mới → email tới trưởng nhóm (không có trưởng nhóm thì trưởng nhóm onsite, rồi HR). Có kết quả → email tới nhân viên. Mailer ném lỗi → nghiệp vụ vẫn thành công.
- [ ] **Step 2:** Chạy IT tập trung → FAIL.
- [ ] **Step 3:** Cài. Duyệt chạy trong một giao dịch: khóa hồ sơ, kiểm tra `DANG_CHO` và tháng chưa chốt, sinh lần chấm, đổi trạng thái. Thêm key i18n mới.
- [ ] **Step 4:** Chạy IT tập trung và `EmployeeForeignKeyCoverageIT` → PASS.
- [ ] **Step 5:** Commit "Giải trình chấm công: gửi, rút, duyệt sinh lần chấm, danh sách chờ duyệt, email".

### Task 5: Xóa tọa độ quá hạn

**Files:**
- Create:
  - `service/attendance/PunchLocationPurgeJob.java`: `@Scheduled(cron = "0 40 0 * * *", zone = "Asia/Ho_Chi_Minh")`. Job đặt `latitude`, `longitude`, `accuracy_m`, `client_ip` về null cho các lần chấm có `work_day < hôm nay − location_retention_days`. Chạy bằng một câu `UPDATE` hàng loạt. Bỏ qua bảng `_aud`.
  - Riêng `attendance_punch_aud`: cũng xóa các cột vị trí của những bản ghi đã quá hạn, để lịch sử không giữ lại tọa độ.
- Test: `app/src/test/java/vn/doit/hrm/service/attendance/PunchLocationPurgeJobIT.java`

**Interfaces:**
- Consumes: Task 2 (cột và cài đặt).
- Produces: `int PunchLocationPurgeJob.purge()`, trả về số dòng đã xóa vị trí.

- [ ] **Step 1: Viết test** `purgesOnlyExpiredAndKeepsResult`. Dùng Clock cố định 2026-10-23, giữ 90 ngày. Lần chấm ngày 24/07 bị xóa vị trí; lần chấm ngày 25/07 còn nguyên. Sau khi purge, các cột `status`, `method`, `site_id`, `distance_m`, `review_reason` vẫn còn giá trị. Bản `_aud` của lần chấm 24/07 cũng không còn tọa độ. Đổi cài đặt thành 30 ngày thì purge xóa thêm.
- [ ] **Step 2:** Chạy IT tập trung → FAIL.
- [ ] **Step 3:** Cài.
- [ ] **Step 4:** Chạy IT tập trung → PASS. Chạy toàn bộ backend một lần, ở nền và riêng → BUILD SUCCESS.
- [ ] **Step 5:** Commit "Xóa tọa độ và IP của lần chấm quá hạn lưu".

### Task 6: Giao diện nhân viên – màn Chấm công, Bảng công của tôi, Giải trình

**Files:**
- Create:
  - `app/src/main/webapp/app/modules/attendance/self-punch.tsx` và `self-punch.spec.tsx`
  - `app/src/main/webapp/app/modules/attendance/explanation-form.tsx` và `explanation-form.spec.tsx`
- Modify:
  - `modules/attendance/attendance-api.ts`: thêm các hàm gọi API của Task 2 và Task 4.
  - `modules/attendance/my-attendance.tsx` và spec của nó:
    - ô vàng khi có `pendingCount` hoặc giải trình đang chờ;
    - chi tiết ngày liệt kê `punches` kèm nguồn và trạng thái;
    - nút "Gửi giải trình cho ngày dd/MM" ở ngày `THIEU_GIO`, `CHUA_NHAP` hoặc có lần bị từ chối;
    - danh sách "Giải trình của tôi" có nút Rút.
  - Routes và menu: route `/cham-cong/tu-cham` cho tài khoản có hồ sơ `NHAN_VIEN`; mục menu "Chấm công" theo `menuFor`.
- Test: các file spec ở trên.

**Interfaces:**
- Consumes: REST của Task 2 và Task 4; `DayView.punches` của Task 3.

- [ ] **Step 1: Viết spec, bám theo bản phác.**
  - `self-punch`:
    - Lần đầu (`consentRequired`): hiện hộp thông báo có nội dung `noticeText`. Bấm "Tôi đồng ý" gọi `POST consent`.
    - Bấm "Vào": gọi `navigator.geolocation.getCurrentPosition` (mock) rồi `POST punches` kèm tọa độ. Geolocation bị từ chối → vẫn `POST`, không kèm tọa độ.
    - Kết quả `CHAP_NHAN`, `CHO_DUYET`, `TU_CHOI` hiện lần lượt nền xanh, vàng, đỏ, kèm `message`. Kết quả `TU_CHOI` có link "Gửi giải trình".
    - Hai nút cao ít nhất 44px.
  - `explanation-form`: lý do bắt buộc; ít nhất một giờ; giờ dạng `HH:mm`. Gọi `POST explanations`. Server trả 400 `explanationpending` → hiện câu tiếng Việt.
  - `my-attendance`: ô vàng; nút giải trình ở ngày thiếu giờ; nút Rút gọi `withdraw`.
- [ ] **Step 2:** `npx vitest run src/main/webapp/app/modules/attendance` → FAIL.
- [ ] **Step 3:** Cài.
- [ ] **Step 4:** `npm test`, `npx tsc --noEmit`, `npm run lint` → đạt.
- [ ] **Step 5:** Commit "Giao diện tự chấm công, bảng công của tôi có giải trình".

### Task 7: Giao diện người duyệt và Cài đặt, README

**Files:**
- Modify:
  - `modules/attendance/day-entry.tsx` và spec:
    - ô nhập dùng `manualIn`/`manualOut`;
    - dưới ô ghi nguồn các lần chấm (dạng "Tự chấm · IP", "Tự chấm · GPS ±140 m · chờ duyệt", "Giải trình");
    - dòng có `pendingCount > 0` tô vàng, kèm nút "Duyệt" và "Từ chối". Từ chối mở hộp nhập lý do bắt buộc;
    - lần bị từ chối hiện mờ;
    - bộ đếm "N lần chấm chờ duyệt".
  - `modules/attendance/month-board.tsx` và spec: ô có lần chờ duyệt tô vàng; bấm ô mở chi tiết lần chấm có nút duyệt (khi có quyền).
  - `modules/leave/approval-inbox.tsx` và spec: thêm mục **"Chấm công chờ duyệt (N)"** dùng `GET /api/attendance/pending`, theo cùng kiểu thẻ và mục của nghỉ phép và làm thêm. Lần chấm có nút Duyệt/Từ chối; giải trình có nút Đồng ý/Từ chối. Từ chối có hộp nhập lý do.
  - Trang Cài đặt, mục Địa điểm chấm công: khối "Tự chấm công" gồm ô nội dung thông báo và số ngày giữ tọa độ (chỉ Quản trị sửa được).
  - `README.md`: mục "### Tự chấm công" đặt sau mục chấm công phần 1. Nêu: bảng xác minh, lần nào được tính, ai duyệt, giải trình, đồng ý và thời hạn lưu, cấu hình reverse proxy (`server.forward-headers-strategy`).
- Test: các spec liên quan.

**Interfaces:**
- Consumes: REST của Task 3 và Task 4; `DayView.punches`, `manualIn`, `manualOut`, `pendingCount`.

- [ ] **Step 1: Viết spec.**
  - `day-entry`:
    - ô nhập lấy `manualIn`;
    - dòng vàng có hai nút; bấm Duyệt gọi `POST punches/{id}/approve`;
    - Từ chối không có lý do thì nút xác nhận bị tắt.
  - `month-board`: ô vàng và hộp chi tiết.
  - `approval-inbox`: mục "Chấm công chờ duyệt" có 2 lần chấm và 1 giải trình; Đồng ý gọi đúng endpoint.
  - Cài đặt: chỉ Quản trị thấy ô có thể sửa.
- [ ] **Step 2:** Chạy `npx vitest run` trên các thư mục `modules/attendance`, `modules/leave` và thư mục Cài đặt → FAIL.
- [ ] **Step 3:** Cài, rồi viết mục README.
- [ ] **Step 4:** Chạy `npm test`, `npx tsc --noEmit`, `npm run lint` → đạt. Sau đó chạy toàn bộ backend một lần, ở nền và riêng → BUILD SUCCESS.
- [ ] **Step 5:** Commit "Giao diện duyệt lần chấm và giải trình, cài đặt thông báo vị trí; README tự chấm công".
