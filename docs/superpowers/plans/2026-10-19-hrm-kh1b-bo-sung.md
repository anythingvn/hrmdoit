# DOIT HRM – Kế hoạch 1b: Bổ sung sau review (nâng cấp, tài khoản, nghỉ việc, bảo mật)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Đưa KH1 lên phiên bản framework mới nhất để dễ cập nhật về sau. Gắn mỗi tài khoản đăng nhập với một hồ sơ nhân viên, tạo tài khoản kèm mật khẩu dùng được ngay. Nghỉ việc thì khóa tài khoản nhưng giữ nguyên dữ liệu, cho phép hẹn ngày nghỉ và hoàn tác. Bổ sung biểu thuế nhiều năm và làm hết các mục bảo mật còn nợ.

**Architecture:** Giữ nguyên kiến trúc KH1 (`core/` thuần Java, `app/` JHipster monolith React + PostgreSQL, Docker Compose). Nâng JHipster 8.7.3 → **9.4.0**, kéo theo Spring Boot, React và các thư viện tương ứng. Các tính năng mới làm trên nền đã nâng cấp.

**Tech Stack:** JHipster 9.4.0 (Spring Boot và phiên bản Java theo mặc định của 9.4.0; nếu cần Java 21 thì cài `openjdk@21` bằng Homebrew), Node 26 (đáp ứng `^22.18 || >=24.11`), PostgreSQL 16, Liquibase, Envers, Jest, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-10-19-hrm-doit-dot1-design.md` cùng các quyết định của người dùng ngày 19/10/2026 ghi ở mục "Quyết định mới" dưới đây (các quyết định này đè lên spec nếu khác).

## Quyết định mới của người dùng (19/10/2026)

| # | Người dùng nói | Cách hiểu để làm |
|---|---|---|
| 1 | "dùng phiên bản dễ cập nhật" | Nâng JHipster lên 9.4.0 (bản mới nhất, đã ra > 2 tuần), cùng Spring Boot, Java và React theo mặc định của bản này. Giữ mọi tùy biến của KH1. |
| 2 | "tài khoản đăng nhập của từng nhân viên phải liên kết với data của từng tài khoản đã được gán" | Mỗi tài khoản gắn đúng 1 hồ sơ nhân viên, mỗi hồ sơ tối đa 1 tài khoản. Thêm vai trò `ROLE_NHAN_VIEN` (nhân viên thường): chỉ xem được hồ sơ của chính mình (`GET /api/account/employee`) và trang "Hồ sơ của tôi". |
| 3, 4 | "thêm chức năng hoàn tác"; "cho nghỉ việc ≈ disable tài khoản, thông tin giữ nguyên để không sai lệch báo cáo" | Cho nghỉ việc thì **khóa tài khoản** đã gắn (`activated=false`) và **không xóa hay sửa** dữ liệu lịch sử. Thay quy tắc cũ "xóa kế hoạch vị trí tương lai" bằng quy tắc chỉ **chốt ngày kết thúc**, nên không còn xóa gì. Thêm **Hoàn tác nghỉ việc**: khôi phục đúng trạng thái trước khi cho nghỉ và mở lại tài khoản. |
| 5 | "mặc định là sau ngày làm việc cuối" | Giữ: Ngày nghỉ việc mặc định = ngày làm việc cuối + 1. |
| 6 | "tài khoản quản trị cao nhất có full các quyền" | `ROLE_ADMIN` làm được mọi việc của mọi vai trò khác (gồm xem đủ dữ liệu nhạy cảm, cột lương khi xuất Excel…). Có test kiểm tra toàn bộ ma trận cho ADMIN. |
| 7 | "làm việc tốt nhất cho thuận tiện upgrade" (font) | Font lấy từ gói npm `@fontsource/be-vietnam-pro`, cập nhật bằng npm. Bỏ bước copy thủ công trong webpack nếu bản 9.4.0 cho phép import CSS của gói. |
| 8 | "email của từng nhân viên có thể cấu hình sau; tạo tài khoản + mật khẩu đầu tiên áp dụng ngay" | Form tạo tài khoản (ở trang Quản trị và ngay trên hồ sơ nhân viên) có ô **mật khẩu ban đầu**, đăng nhập được ngay. Email không bắt buộc, không gửi thư kích hoạt. |
| 9 | "ngày nghỉ việc ở tương lai (đã xin phép) thì hệ thống sẽ hiển thị trên lịch luôn (sau khi được duyệt)" | Cho nghỉ việc với ngày tương lai tạo **lịch nghỉ việc đã duyệt**: nhân viên vẫn "Đang làm việc" và tài khoản vẫn mở cho đến hết ngày làm việc cuối; có nhãn "Sắp nghỉ (dd/MM/yyyy)" trên danh sách và hồ sơ. Một job hằng ngày lúc 00:05 (giờ Việt Nam) áp dụng nghỉ việc và khóa tài khoản. **Lịch** (calendar) và **luồng xin phép – duyệt** thuộc tính năng Nghỉ phép/Đơn từ ở kế hoạch sau; dữ liệu "lịch nghỉ việc đã duyệt" hôm nay là nguồn để lịch đó hiển thị. |
| 10 | "làm cho thuận tiện nhất" (gợi ý Gross nhiều năm) | Lõi có sẵn biểu thuế/BH theo **ngày hiệu lực** từ 07/2020 đến nay. Kỳ tương lai chưa có luật mới thì dùng bộ quy tắc mới nhất kèm cờ "ước tính". HR không phải nhập gì. |
| 11 | "hãy làm hết đi" (các mục bảo mật còn nợ) | Làm hết: CSP `script-src` chặt, HSTS, giới hạn đăng nhập sai, mã hóa backup ngoài VPS và dọn bản cũ, chặn số tiền âm, không xóa cứng HĐ đã ký, PUT không chuyển HĐ sang người khác, đặt mật khẩu thì hủy link reset cũ, dọn file docker cũ của JHipster. |

Chưa merge nhánh: người dùng chưa chọn cách tích hợp. Tiếp tục làm trên `feature/hrm-kh1-nen-tang`.

## Global Constraints

- Giữ mọi ràng buộc chung của KH1 (`docs/superpowers/plans/2026-10-19-hrm-kh1-nen-tang-ho-so.md`, mục Global Constraints), trừ phiên bản JHipster/Java. Các ràng buộc gồm: tiếng Việt; `dd/MM/yyyy`; `1.234.567`; `Asia/Ho_Chi_Minh`; màu/font canvas; vùng bấm ≥ 44px; `core/` không phụ thuộc Spring; không xóa cứng nhân viên; quyền kiểm tra ở backend; mã HDDV tự sinh.
- Không xóa hay sửa dữ liệu lịch sử khi cho nghỉ việc (HĐ, lương, phụ cấp, vị trí, tài liệu). Chỉ được chốt ngày kết thúc của bản ghi đang mở và đổi trạng thái HĐ đang hiệu lực sang `DA_KET_THUC`; hoàn tác thì trả đúng giá trị cũ.
- `ROLE_ADMIN` ⊇ mọi quyền.
- Mọi suite phải xanh sau mỗi task: `mvn -q -pl app -am verify -Dskip.npm -Dskip.installnodenpm`, `cd app && npm test`, `npx tsc --noEmit`, `npm run lint`.

## Review Focus

1. **Hoàn tác sau khi đã có thay đổi khác.** Cho nghỉ → HR sửa hồ sơ → hoàn tác: chỉ các trường do việc cho nghỉ đổi được trả lại, sửa của HR giữ nguyên. Hoàn tác hai lần → 400. → Task 3.
2. **Nghỉ việc hẹn trước rồi hủy.** Đặt ngày nghỉ tương lai, rồi hủy trước ngày đó: không có gì bị đổi, tài khoản vẫn mở. → Task 3.
3. **Tài khoản bị khóa đang đăng nhập.** JWT cũ của người đã nghỉ phải bị từ chối ở request kế tiếp, không chờ hết hạn token. → Task 3.
4. **Một tài khoản gắn hai hồ sơ.** Gắn tài khoản đã gắn với người khác → 400. → Task 2.
5. **Kỳ lương tương lai hoặc rất cũ.** Gợi ý Gross cho 01/2027 trả kết quả với cờ ước tính; trước 07/2020 trả rỗng kèm lý do. → Task 4.

---

### Task 1: Nâng cấp JHipster 8.7.3 → 9.4.0

**Files:** `app/.yo-rc.json` (`jhipsterVersion`), toàn bộ file sinh của JHipster, `app/pom.xml`, `app/package.json`, `app/webpack/*`, các file đã tùy biến ở KH1 (giữ nguyên tùy biến), README (phiên bản, yêu cầu Java/Node), `Dockerfile` (image JDK theo phiên bản Java mới).

**Interfaces:** Produces: ứng dụng chạy trên JHipster 9.4.0, mọi API và hành vi của KH1 giữ nguyên.

- [ ] **Step 1:** Ghi lại baseline: số test backend/frontend hiện tại (624 / 202) và danh sách file đã tùy biến so với bản sinh (`git diff --stat e78adfe..HEAD -- app/`).
- [ ] **Step 2:** Nâng cấp bằng chính công cụ của JHipster (`npx -y generator-jhipster@9.4.0 upgrade` hoặc chạy lại generator 9.4.0 với `.yo-rc.json` + `.jhipster/*.json` trên một nhánh tạm rồi merge), xử lý xung đột bằng cách giữ tùy biến KH1. Không sinh lại 6 entity skipClient.
- [ ] **Step 3:** Cập nhật Java nếu 9.4.0 yêu cầu (Java 21: `brew install openjdk@21`, sửa `JAVA_HOME` trong README/hướng dẫn, `maven.compiler.release` của `core` giữ 17 nếu vẫn tương thích, image Docker theo bản Java mới).
- [ ] **Step 4:** Chạy toàn bộ suite. Expected: số test ≥ baseline, 0 lỗi. Kiểm tra build prod (`mvn -q -pl app -am package -Pprod -DskipTests`) và `docker compose build`.
- [ ] **Step 5:** Commit `"Nâng cấp JHipster 9.4.0"`.

### Task 2: Tài khoản gắn hồ sơ nhân viên, mật khẩu ban đầu

**Files:** `AuthoritiesConstants` (+`NHAN_VIEN = "ROLE_NHAN_VIEN"`), `authority.csv`, changelog ràng buộc `employee.user_id` UNIQUE, `UserService`/`UserResource`/`AdminUserDTO` (tạo user với `password` tùy chọn; có mật khẩu → bỏ random + không gửi mail; email không bắt buộc), `EmployeeAccountService` + `EmployeeAccountResource` (`POST /api/employees/{id}/account` {login, password, authorities[]} tạo + gắn; `PUT /api/employees/{id}/account` {userId} gắn tài khoản có sẵn; `DELETE /api/employees/{id}/account` bỏ gắn — ADMIN/HR; HR không được cấp `ROLE_ADMIN`), `AccountResource` (`GET /api/account/employee` → hồ sơ của chính mình, không có trường nhạy cảm trừ khi xem qua `/sensitive` của chính mình), frontend: form tạo user ở Quản trị có mật khẩu ban đầu; tab/ô "Tài khoản đăng nhập" trên hồ sơ (tạo, gắn, bỏ gắn); trang "Hồ sơ của tôi" cho `ROLE_NHAN_VIEN` (menu chỉ có mục này); `menuFor` thêm vai trò.

**Interfaces:** Produces: `EmployeeDTO.userLogin` (read-only); `GET /api/account/employee`; `ROLE_NHAN_VIEN` chỉ đọc hồ sơ của mình (mọi API khác → 403, `/api/employees/{id}/sensitive` chỉ khi id là của mình).

- [ ] Test trước: tạo tài khoản kèm mật khẩu → đăng nhập `/api/authenticate` được ngay, không cần email; gắn tài khoản đã thuộc người khác → 400 `useralreadylinked` (Review Focus 4); HR cấp `ROLE_ADMIN` → 403; `ROLE_NHAN_VIEN` đọc `/api/account/employee` = hồ sơ của mình, `GET /api/employees` → 403, `/api/employees/{id khác}/sensitive` → 403; Jest cho form và menu.
- [ ] Cài đặt, chạy toàn bộ suite, commit `"Tài khoản gắn hồ sơ nhân viên, mật khẩu ban đầu"`.

### Task 3: Nghỉ việc = khóa tài khoản; hẹn ngày nghỉ; hoàn tác

**Files:** changelog bảng `termination_record` (id, employee_id, last_working_day, termination_date, termination_type, reason, status `SCHEDULED|APPLIED|CANCELLED|UNDONE`, created_by/at, applied_at, undone_by/at, `snapshot_json` = các giá trị trước khi áp dụng: status, các trường nghỉ việc, danh sách {assignmentId, oldToDate}, {contractId, oldStatus, oldEndDate}, userActivated) + `_aud`; `TerminationService` (viết lại: không xóa gì; ngày làm việc cuối < hôm nay hoặc = hôm nay → áp dụng ngay, > hôm nay → `SCHEDULED`); `TerminationScheduler` (`@Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")`, áp dụng các bản ghi đến hạn: ngày làm việc cuối < hôm nay); `POST /api/employees/{id}/terminate` (giữ API), `DELETE /api/employees/{id}/termination` (hủy lịch hẹn, ADMIN/HR), `POST /api/employees/{id}/termination/undo` (ADMIN/HR); khóa tài khoản gắn kèm (`activated=false`) khi áp dụng, mở lại khi hoàn tác; JWT của tài khoản bị khóa bị từ chối ngay (kiểm tra `activated` khi xác thực request); `EmployeeDTO.scheduledTermination` (ngày) cho nhãn "Sắp nghỉ"; frontend: dialog nghỉ việc cho chọn ngày tương lai, nhãn "Sắp nghỉ (dd/MM/yyyy)" trên danh sách + hồ sơ, nút "Hủy lịch nghỉ" và "Hoàn tác nghỉ việc" (xác nhận).

**Interfaces:** Thay quy tắc KH1 Task 9 "xóa kế hoạch vị trí tương lai" bằng: mọi giai đoạn vị trí bắt đầu sau ngày làm việc cuối **không bị xóa**; giai đoạn đang mở chốt `toDate = lastWorkingDay`; giai đoạn bắt đầu sau ngày làm việc cuối mà đã bắt đầu → vẫn 400 `terminatebeforeassignment`; giai đoạn chưa bắt đầu nhưng bắt đầu sau ngày làm việc cuối (ví dụ "quay về văn phòng" tự tạo) → cũng **chặn** với 400 `terminatebeforeassignment`, và dialog hướng dẫn HR xóa giai đoạn đó bằng chức năng xóa đã có (có audit). Riêng giai đoạn "quay về văn phòng" do hệ thống tự tạo (RESUMED) và chưa bắt đầu thì dialog có nút "Xóa giai đoạn quay về và cho nghỉ" làm hai bước trong một lần xác nhận. Mọi thay đổi khi áp dụng được lưu vào `snapshot_json` để hoàn tác đúng.

- [ ] Test trước: áp dụng ngay khóa tài khoản, không xóa bản ghi nào (đếm bản ghi trước/sau); JWT cũ bị 401 ngay sau khi áp dụng (Review Focus 3); ngày tương lai → `SCHEDULED`, trạng thái vẫn `DANG_LAM_VIEC`, nhãn "Sắp nghỉ"; job với `Clock.fixed` qua ngày → áp dụng; hủy lịch → không đổi gì (Review Focus 2); hoàn tác → trạng thái, ngày kết thúc vị trí/HĐ, tài khoản trở về như trước, sửa của HR sau khi nghỉ giữ nguyên (Review Focus 1); hoàn tác lần 2 → 400 `nothingtoundo`; KE_TOAN/BGD → 403; báo cáo/xuất Excel vẫn thấy người đã nghỉ với dữ liệu nguyên vẹn; Jest cho các nút/nhãn.
- [ ] Cài đặt, chạy toàn bộ suite, commit `"Nghỉ việc khóa tài khoản, hẹn ngày nghỉ, hoàn tác"`.

### Task 4: Biểu thuế/BH nhiều năm trong lõi; ADMIN toàn quyền; font qua npm

**Files:** `core/.../payroll/PayrollRules.java` (dữ liệu theo ngày hiệu lực; `forPeriod` trả bộ quy tắc hiệu lực; thêm `RulesLookup forPeriodOrLatest(YearMonth) → (PayrollRules rules, boolean estimated)`), test lõi; `SalaryToolsResource` trả `{gross, estimated, reason}`; dialog hiển thị "ước tính theo quy định mới nhất" khi `estimated`; test ma trận quyền ADMIN (`AdminFullAccessIT`: mọi endpoint đọc/ghi/nhạy cảm/xuất có lương/lịch sử/backup-status/tài khoản → 2xx cho ADMIN); font: import CSS của `@fontsource/be-vietnam-pro` nếu webpack 9.4.0 xử lý được `url()` (bỏ bước copy), nếu không thì giữ cách cũ và ghi lý do.

**Mốc dữ liệu (người làm task kiểm lại với văn bản luật trước khi ghi):**

| Hiệu lực | Giảm trừ bản thân / người phụ thuộc | Biểu thuế | Trần BHXH, BHYT (20 × lương cơ sở) | Trần BHTN (20 × lương tối thiểu vùng I) |
|---|---|---|---|---|
| 07/2020 | 11.000.000 / 4.400.000 | 7 bậc: 5/10/15/20/25/30/35% tại 5/10/18/32/52/80 tr | 29.800.000 (1.490.000) | 88.400.000 (4.420.000) |
| 07/2022 | như trên | như trên | như trên | 93.600.000 (4.680.000) |
| 07/2023 | như trên | như trên | 36.000.000 (1.800.000) | 93.600.000 |
| 07/2024 | như trên | như trên | 46.800.000 (2.340.000) | 99.200.000 (4.960.000) |
| 01/2026 | 15.500.000 / 6.200.000 | 5 bậc hiện có trong lõi | 46.800.000 | 106.200.000 (5.310.000) |
| 07/2026 | như trên | như trên | 50.600.000 (2.530.000) | 106.200.000 |

Trước 07/2020 → không có quy tắc (gợi ý trả rỗng, `reason = "Chưa có biểu thuế cho kỳ này"`).

- [ ] Test trước: các mốc trong bảng (mỗi mốc ít nhất một test `netFromGross` tính tay); `forPeriodOrLatest(2027-01)` → bộ 07/2026, `estimated = true`; 2019-12 → rỗng; mọi test lõi cũ vẫn đạt. `AdminFullAccessIT`. Kiểm font sau khi build prod (file woff2 có trong output, CSS tham chiếu đúng).
- [ ] Cài đặt, chạy toàn bộ suite, commit `"Biểu thuế nhiều năm, Quản trị toàn quyền, font qua npm"`.

### Task 5: Bảo mật và dọn dẹp còn nợ

**Files:** `application.yml` (CSP: `script-src 'self'` — bỏ `'unsafe-inline' 'unsafe-eval' https://storage.googleapis.com`, kiểm prod build không cần inline script; `style-src` giữ `'unsafe-inline'` nếu React cần), `server.forward-headers-strategy: framework` (prod), `Caddyfile` (HSTS `max-age=31536000; includeSubDomains`, giới hạn tần suất `/api/authenticate` nếu dùng module có sẵn; nếu Caddy chuẩn không có rate-limit thì làm ở app), giới hạn đăng nhập sai trong app (`LoginAttemptService`: 5 lần sai / 15 phút theo login + IP → 429, có test), `ops/backup` (mã hóa bản đẩy ra ngoài bằng `age` với public key trong `.env` `BACKUP_AGE_RECIPIENT`; thiếu key thì không đẩy và `status.json` báo `offsiteOk=false, reason`; dọn bản ngoài VPS giữ 30 bản qua rsync `--delete` trên thư mục gốc theo ngày hoặc lệnh ssh; hướng dẫn giải mã trong `restore.md`), DTO tiền: `@Min(0)` cho `salary`, `grossAmount`, `amount`, `amountPerMonth`, `socialInsuranceSalary`, `EmploymentContractService`: `DELETE` chỉ cho HĐ `NHAP` (khác → 400 `cannotdeletesigned`), PUT/PATCH không đổi `employee` của HĐ, lương, phụ cấp, vị trí (→ 400 `cannotmoverecord`), `UserService.setPasswordByAdmin` xóa `resetKey/resetDate`, xóa `app/src/main/docker/jhipster-control-center.yml` và các yml cũ chứa khóa/mật khẩu mẫu (giữ file dev auto-start nếu còn dùng, thay mật khẩu mẫu bằng biến), ghim `caddy` theo bản vá, xử lý các mục "minor (deferred)" của KH1 có ảnh hưởng tới người dùng: lỗi `month` sai → 400, file thiếu MIME `application/octet-stream` chấp nhận nếu magic bytes đúng, `server.tomcat.max-swallow-size` để >21MB trả 413 sạch, nút menu `aria-label` đổi theo trạng thái, `:focus-visible`.
- [ ] Test trước cho từng mục có hành vi (CSP header, HSTS qua cấu hình, 429 sau 5 lần sai, backup không key → không đẩy, `@Min(0)` → 400, xóa HĐ đã ký → 400, đổi employee của HĐ → 400, reset key bị xóa, month sai → 400, octet-stream PDF → 201).
- [ ] Cài đặt, chạy toàn bộ suite, `docker compose build`, commit `"Bảo mật: CSP, HSTS, chặn đăng nhập sai, mã hóa backup, ràng buộc dữ liệu"`.

## Kiểm tra cuối

- [ ] Toàn bộ suite xanh; `docker compose up` trên máy, đăng nhập admin, tạo nhân viên + tài khoản mật khẩu ban đầu, đăng nhập bằng tài khoản đó (thấy "Hồ sơ của tôi"), cho nghỉ hẹn ngày mai → "Sắp nghỉ", hủy; cho nghỉ hôm nay → tài khoản bị khóa, đăng nhập bị từ chối; hoàn tác → đăng nhập lại được; backup có mã hóa (thử giải mã bằng private key tạm). Gỡ stack sau khi kiểm.
