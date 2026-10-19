# DOIT HRM

## Cấu trúc repo

- `core/` – lõi Java thuần (package `vn.doit.hr`, không phụ thuộc Spring): import bảng lương, sinh hợp đồng, nhắc hạn, chấm công... Mô tả chi tiết ở phần dưới.
- `app/` – ứng dụng JHipster (Spring Boot + Angular) dùng lõi `core` làm thư viện; được sinh ở các bước sau.
- `docs/` – tài liệu thiết kế và kế hoạch.

Build từ thư mục gốc: `mvn -pl core test` (hoặc `mvn test` để chạy mọi module).

## Chạy local

Cần JDK 17, Node (npm chạy qua Maven) và Docker (PostgreSQL cho test tích hợp).

```bash
export JAVA_HOME=$(brew --prefix openjdk@17)
mvn -pl core test                      # lõi
cd app && ./mvnw                        # chạy dev (profile dev, H2/Postgres theo cấu hình), http://localhost:8080
npm test                               # test giao diện
```

Tài khoản mặc định `admin` / `admin` chỉ dùng khi phát triển; trên máy thật phải đổi ngay.

## Triển khai lên VPS

Cần Docker + Docker Compose trên VPS.

1. `cp .env.example .env`, rồi điền trong `.env`:
   - `POSTGRES_PASSWORD`: mật khẩu mạnh.
   - `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`: sinh bằng `openssl rand -base64 64 | tr -d '\n'`. Thiếu biến này ứng dụng không khởi động.
   - `BACKUP_OFFSITE_RSYNC`, `DOMAIN`: xem các mục dưới.
2. `docker compose up -d --build` (lần đầu build mất vài phút).
3. Kiểm tra: `curl -fsS http://127.0.0.1:8080/management/health` trả `{"status":"UP",...}`.
4. **Đăng nhập `admin` / `admin` và đổi mật khẩu `admin` ngay lần đầu** (Tài khoản > Mật khẩu).
5. Ứng dụng chỉ lắng nghe `127.0.0.1:8080`. Nếu VPS chưa có reverse proxy, đặt `DOMAIN=ten-mien.example.com` trong `.env`, trỏ DNS về VPS rồi chạy `docker compose --profile proxy up -d`; Caddy tự cấp HTTPS (cổng 80/443). Nếu VPS đã có nginx/Caddy riêng, trỏ nó tới `127.0.0.1:8080`.

Cập nhật phiên bản: `git pull && docker compose up -d --build`.

## Backup & khôi phục

Container `backup` chạy `ops/backup/backup.sh` mỗi ngày lúc 02:00 (giờ Việt Nam):

- `pg_dump -Fc` + `tar czf` thư mục file vào volume `backups`, thư mục `/backups/YYYY-MM-DD/`; giữ 14 bản mới nhất.
- Ghi `status.json` (volume `backup-status`) – ứng dụng đọc để hiện **băng cảnh báo cho Quản trị** khi chưa cấu hình sao chép ngoài VPS hoặc bản backup cũ hơn 36 giờ. API: `GET /api/admin/backup-status`.
- Chạy tay: `docker compose exec backup /ops/backup/backup.sh`.

**Sao chép ra ngoài VPS (nên bật):** đặt `BACKUP_OFFSITE_RSYNC=user@host:/duong/dan` trong `.env`. Container dùng rsync qua SSH với khóa nằm trong thư mục `ops/backup/ssh/` (hoặc thư mục khác đặt bằng `BACKUP_SSH_DIR`), gắn **chỉ đọc** vào `/ssh`:

```bash
ssh-keygen -t ed25519 -N '' -f ops/backup/ssh/id_ed25519     # tạo khóa riêng cho backup
ssh-copy-id -i ops/backup/ssh/id_ed25519.pub user@host        # cài khóa công khai lên máy nhận
ssh-keyscan host > ops/backup/ssh/known_hosts                 # (khuyến nghị) ghim host key
chmod 600 ops/backup/ssh/id_ed25519
```

Không có `known_hosts` thì lần đầu tự chấp nhận host key. Thư mục `ops/backup/ssh/` đã được git bỏ qua; không commit khóa.

**Khôi phục:** xem [`ops/backup/restore.md`](ops/backup/restore.md). Nên diễn tập khôi phục vào database trống định kỳ.

---

## DOIT HR Extension – core (`core/`)

Module Java (không phụ thuộc Spring) cho 2 chức năng:

1. **Import bảng lương Excel** theo đúng mẫu "BẢNG LƯƠNG NHÂN VIÊN" của DOIT, kèm kiểm tra lỗi trước khi lưu.
2. **Sinh hợp đồng lao động** từ mẫu Word có thẻ `{{...}}`, xuất PDF.

Đưa thẳng vào project JHipster (copy package `vn.doit.hr`) hoặc dùng như một thư viện.

## Trạng thái kiểm thử

`mvn test` (JDK 17, JUnit 5): **29 test đạt, 0 lỗi, 0 bỏ qua** – chạy ngày 18/10/2026 (trước khi thêm mục 5).

| Phần | Test |
|---|---|
| Đọc cột, kiểm tra nghiệp vụ, thuế TNCN, số → chữ, map trường HĐ, nhắc hạn, lịch sử vị trí | 26 test, gồm bảng lương 06/2026 thật (họ tên đã ẩn danh, `core/src/test/resources`) |
| `PoiSheetGrid` – đọc file .xlsx gốc bằng Apache POI | `PoiIntegrationTest`: 14 dòng, ô ngày → `LocalDate`, giá trị công thức cache, tổng thực nhận khớp GRAND TOTAL |
| `ContractGenerator`, `DocxTemplateRenderer` – điền mẫu bằng poi-tl | `PoiIntegrationTest`: không còn thẻ `{{...}}`, file ra ở `target/HDLD_test.docx`, `target/QD_test.docx` |

Test đọc file Excel gốc tự bỏ qua nếu không có file (mặc định tìm `../DO it - Bang luong thang 06_2026 them luon.xlsx`, đổi bằng `-Dpayroll.file=...`). File lương thật **không** nằm trong project.

## 1. Import bảng lương

```java
SheetGrid grid = PoiSheetGrid.read(uploadedFile.getInputStream());     // sheet "Bảng lương"
ParsedPayroll parsed = new PayrollSheetParser().parse(grid);
List<Issue> issues = new ArrayList<>(parsed.issues());
issues.addAll(new PayrollValidator().validate(parsed, YearMonth.of(2026, 6),
        code -> employeeRepository.findByCode(code).map(Employee::getFullName)));

boolean blocked = issues.stream().anyMatch(i -> i.severity() == Issue.Severity.ERROR);
// ERROR  → không cho lưu, hiển thị danh sách cho kế toán sửa file
// WARNING → cho lưu sau khi người duyệt bấm "Đã xem và xác nhận"
```

**Cách nhận diện cột:** theo *tên tiêu đề* (dòng 6 + dòng tiêu đề phụ 7), không theo chữ cái cột → kế toán chèn thêm cột vẫn import được. Danh sách 36 cột trong `PayrollField`.

**Giá trị đọc:** giá trị công thức đã lưu sẵn trong file (đúng số kế toán nhìn thấy). File phải được lưu bằng Excel/LibreOffice.

**Kỳ lương:** người upload chọn kỳ trên giao diện; tiêu đề "Tháng MM/YYYY" trong file chỉ dùng để cảnh báo lệch.

### Các kiểm tra

| Mức | Kiểm tra |
|---|---|
| ERROR | Thiếu mã NV · Trùng mã trong file · Mã không có trong hệ thống · Ô lỗi công thức (#N/A, #REF!) · Ô số chứa chữ · Thiếu cột bắt buộc |
| WARNING | Kỳ trong file ≠ kỳ chọn · Họ tên khác hồ sơ · Cột tổng không khớp các cột thành phần (ô công thức bị gõ đè) · Có ngày ký HĐLĐ nhưng không đóng BH · Lương đóng BHXH/BHTN sai trần · Tỉ lệ BH người LĐ ≠ 8% / 1,5% / 1% · Giảm trừ gia cảnh ≠ mức luật · Thuế TNCN ≠ biểu lũy tiến hiện hành |

Mức luật định nằm trong `PayrollRules` (hiện khai báo cho năm 2026). **Khi đưa vào hệ thống thật, chuyển thành bảng cấu hình có ngày hiệu lực** để HR tự cập nhật khi luật thay đổi.

### Gợi ý lưu trữ (JPA)

```
payroll_period (id, ky YYYY-MM, trang_thai [NHAP|DA_DUYET|DA_GUI_PHIEU], file_goc_id, nguoi_import, thoi_gian)
payroll_line   (id, period_id, employee_id, version, field_code, so_tien)   -- hoặc 1 cột/field nếu muốn query dễ
```
Import lại cùng kỳ → tạo `version` mới, giữ bản cũ. Lưu luôn file Excel gốc để đối chiếu.

## 2. Sinh hợp đồng

```java
try (InputStream tpl = getClass().getResourceAsStream("/templates/DOIT_HDLD_template.docx");
     OutputStream out = Files.newOutputStream(docxPath)) {
    new ContractGenerator().render(tpl, contractData, out);
}
Path pdf = new PdfConverter("soffice").toPdf(docxPath);
```

Server cần LibreOffice và font Times New Roman (gói `ttf-mscorefonts-installer` trên Ubuntu), nếu không PDF sẽ bị thay font.

### Các thẻ trong mẫu

| Thẻ | Nguồn | Thẻ | Nguồn |
|---|---|---|---|
| `soHopDong` | sinh tự động `DOIT/{năm}/HĐLĐ/{số}` | `loaiHopDong` | HĐ |
| `hoTen` | hồ sơ (in hoa) | `thoiHan`, `tuNgay`, `denNgay` | HĐ (ngày kết thúc = bắt đầu + thời hạn − 1 ngày) |
| `gioiTinh`, `ngaySinh`, `trinhDo` | hồ sơ | `boPhan`, `viTri` | hồ sơ / HĐ |
| `noiCuTru`, `dienThoai`, `email` | hồ sơ | `mucLuong`, `mucLuongBangChu` | HĐ (bằng chữ tự sinh) |
| `soCanCuoc`, `ngayCap`, `noiCap` | hồ sơ | `phuCap` | HĐ (danh sách phụ cấp) |
| `soTaiKhoan`, `nganHang` | hồ sơ | `cheDoNangLuong` | HĐ |
| `nguoiDaiDien` | cấu hình công ty | | |

HR tự sửa câu chữ trong Word thoải mái, chỉ cần **gõ lại cả thẻ trong một lần** (không định dạng một nửa thẻ) để Word không cắt thẻ ra nhiều đoạn.

## Chạy test

```bash
mvn -pl core test   # từ thư mục gốc repo
```

## 3. Nhắc hạn (package `vn.doit.hr.reminder`)

Nhắc 4 loại sự kiện. Mỗi người nhận chỉ nhận **một email tổng hợp mỗi ngày**.

| Sự kiện | Nhắc trước (mặc định) | Khi quá hạn mà chưa xử lý | Người nhận |
|---|---|---|---|
| Hết hạn HĐLĐ xác định thời hạn | 45, 30, 15, 7, 0 ngày | Ngày 1, rồi cứ 3 ngày một lần, đến ngày 30 (hạn ký HĐ mới theo Điều 20.2 BLLĐ 2019) | HR, quản lý (+ giám đốc khi quá hạn) |
| Hết thử việc | 7, 3, 0 ngày | Mỗi ngày, tối đa 14 ngày (Điều 27: phải thông báo kết quả) | HR, quản lý (+ giám đốc) |
| Đến hạn xét tăng lương | 30, 7 ngày | Mỗi 7 ngày, tối đa 28 ngày | HR, quản lý / giám đốc |
| Sinh nhật | 3, 0 ngày | — | HR, quản lý |

Quy tắc đáng chú ý (đều có test):
- **Ngày nhắc rơi vào Chủ nhật/ngày lễ** → nhắc vào ngày làm việc liền trước (lịch thứ Hai–thứ Bảy theo HĐLĐ của DOIT).
- **Server nghỉ vài ngày** → lần chạy sau chỉ gửi **một** mốc gấp nhất, không dồn cả loạt.
- **Đã ký 2 HĐ xác định thời hạn liên tiếp** → lời nhắc ghi rõ HĐ tiếp theo phải là HĐ không xác định thời hạn.
- **Dừng nhắc** khi: ký HĐ mới, ghi nhận kết quả thử việc, ghi nhận lần điều chỉnh lương (hoặc hẹn ngày xét lại), hoặc nhân viên nghỉ việc.
- Gia hạn HĐ (đổi ngày kết thúc) được coi là sự kiện mới, chu kỳ nhắc tự chạy lại từ đầu.

### Dữ liệu cần lưu thêm (HR Extension)

OrangeHRM có sẵn ngày sinh và trạng thái nghỉ việc. Lịch sử nhiều HĐ, thử việc và tăng lương thì nên lưu ở HR Extension. HĐ sinh từ module ở mục 2 sẽ tự ghi vào `employee_contract`.

```sql
CREATE TABLE employee_contract (
  id BIGSERIAL PRIMARY KEY, employee_code VARCHAR(20) NOT NULL,
  contract_no VARCHAR(50) NOT NULL, start_date DATE NOT NULL, end_date DATE,   -- NULL = không xác định thời hạn
  file_id BIGINT, created_at TIMESTAMP NOT NULL DEFAULT now());
CREATE TABLE probation (
  employee_code VARCHAR(20) PRIMARY KEY, start_date DATE, end_date DATE NOT NULL,
  result VARCHAR(10), result_date DATE);                                       -- result NULL = chưa có kết quả
CREATE TABLE salary_change (
  id BIGSERIAL PRIMARY KEY, employee_code VARCHAR(20) NOT NULL, effective_date DATE NOT NULL,
  base_salary BIGINT, review_cycle_months INT DEFAULT 12, next_review_override DATE, note TEXT);
CREATE TABLE reminder_log (
  event_key VARCHAR(120) NOT NULL, step INT NOT NULL, sent_at TIMESTAMP NOT NULL DEFAULT now(),
  PRIMARY KEY (event_key, step));
```

`ReminderLog` trên bảng này:
- `smallestLeadSent(k)` = `SELECT MIN(step) FROM reminder_log WHERE event_key = ? AND step >= 0`
- `overdueSent(k, d)` = có dòng `step = -d`

### Job hằng ngày (Spring – mẫu, chưa compile)

```java
@Scheduled(cron = "0 30 7 * * MON-SAT", zone = "Asia/Ho_Chi_Minh")
@SchedulerLock(name = "hr-reminders")            // ShedLock, nếu chạy nhiều instance
public void sendDailyReminders() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    List<Reminder> reminders = engine.run(today, snapshotService.activeEmployees(), reminderLogRepo);
    ReminderDigest.byEmail(reminders, recipientResolver).forEach((email, items) -> {
        mailSender.send(email, ReminderDigest.subject(today, items), ReminderDigest.renderText(today, items));
        items.forEach(r -> reminderLogRepo.save(r.eventKey(), r.step()));   // ghi SAU khi gửi thành công
    });
}
```

Lưu ý: chỉ ghi `reminder_log` sau khi email gửi thành công. Nếu gửi lỗi, lần chạy sau sẽ tự gửi lại.

## 4. Lịch sử vị trí làm việc (package `vn.doit.hr.assignment`)

Lưu nhân viên làm ở đâu theo từng giai đoạn: văn phòng, onsite tại khách hàng, làm từ xa, đi công tác.

### Hai lớp vị trí

| Lớp | Loại | Quy tắc |
|---|---|---|
| Nơi làm việc chính | `VAN_PHONG`, `KHACH_HANG`, `TU_XA` | Tại mỗi thời điểm chỉ có 1 nơi chính, không chồng lấn |
| Tạm thời | `CONG_TAC` | Bắt buộc có ngày về; đè lên nơi chính trong những ngày đi; các đợt công tác không chồng lấn nhau |

### Ghi lịch sử – hệ thống tự làm

- **Cử đi onsite có thời hạn** (vd 01/11/2026 – 31/01/2027): tự chốt văn phòng đến 31/10/2026, rồi tự tạo giai đoạn **quay về văn phòng** từ 01/02/2027. HR chỉ cần nhập 1 dòng.
- **Điều chuyển hẳn** (không có ngày kết thúc): chỉ chốt nơi cũ, không tạo giai đoạn quay về.
- **Về sớm hoặc gia hạn** (`changeEnd`): ngày quay về văn phòng được dời theo, không để hở ngày nào.
- **Từ chối** khi: chồng lấn thời gian, ngày kết thúc trước ngày bắt đầu, công tác không có ngày về, thiếu tên đơn vị, hoặc chèn giai đoạn chính vào quá khứ đè lên dữ liệu đã có.
- **Không bao giờ xóa.** Mỗi thao tác trả về danh sách `AssignmentChange` (CREATED / CLOSED / RESUMED / END_CHANGED / ADJUSTED) để ghi audit.

### Tra cứu và báo cáo

| Hàm | Dùng cho |
|---|---|
| `locationOn(ngày)` | Hôm đó nhân viên làm ở đâu (đang công tác thì trả về nơi công tác). Sau này dùng cho **chấm công GPS**: biết vị trí hợp lệ để chấm công |
| `timeline(từ, đến)` | Dòng thời gian theo giai đoạn để hiển thị trên hồ sơ |
| `workingDaysByPlace(từ, đến, lịch)` | Số ngày làm việc tại từng nơi: đối soát với khách hàng, tính công tác phí |
| `gaps(từ, đến)` | Các khoảng chưa có dữ liệu, để HR bổ sung |
| `AssignmentReport.whoIsWhere(ds, ngày)` | Ai đang ở đâu trong một ngày |

### Quyết định cử nhân sự

Mẫu `templates/DOIT_QD_CuNhanSu_template.docx` dùng chung cho 2 trường hợp: đi công tác và làm việc tại khách hàng (19 thẻ, lấy từ `AssignmentDecision.toFields()`). Luồng xử lý:

```java
AssignmentHistory.Result r = history.add(decision.assignment());   // kiểm tra trước, lỗi thì dừng
try (InputStream tpl = getClass().getResourceAsStream("/templates/DOIT_QD_CuNhanSu_template.docx")) {
    new DocxTemplateRenderer().render(tpl, decision.toFields(), out);
}
repository.saveAll(r.changes());                                   // cùng 1 transaction với việc lưu quyết định
```

Mẫu được sinh bằng `core/tools/make_decision_template.py`. HR có thể sửa thẳng trong Word.

```sql
CREATE TABLE work_assignment (
  id BIGSERIAL PRIMARY KEY, employee_code VARCHAR(20) NOT NULL,
  type VARCHAR(20) NOT NULL,            -- VAN_PHONG | KHACH_HANG | TU_XA | CONG_TAC
  unit VARCHAR(200), address VARCHAR(300),
  from_date DATE NOT NULL, to_date DATE, -- NULL = đến nay
  decision_no VARCHAR(50), decision_file_id BIGINT, note TEXT);
CREATE INDEX ix_assignment_emp ON work_assignment(employee_code, from_date);
CREATE TABLE work_assignment_audit (
  id BIGSERIAL PRIMARY KEY, assignment_id BIGINT, kind VARCHAR(20) NOT NULL,
  before_json JSONB, after_json JSONB, changed_by VARCHAR(100), changed_at TIMESTAMP NOT NULL DEFAULT now());
```

**Nhập dữ liệu ban đầu:** mỗi nhân viên tạo 1 dòng `VAN_PHONG` (hoặc `KHACH_HANG`) bắt đầu từ ngày vào làm, rồi nhập các đợt onsite/công tác đã biết theo thứ tự thời gian. `gaps()` sẽ chỉ ra chỗ còn thiếu.

## 5. Báo cáo nhân sự (package `vn.doit.hr.report`)

Có 3 báo cáo, xuất chung ra một file Excel 4 sheet (`ReportExcelWriter`): **Tổng hợp**, **Biến động**, **Nghỉ việc**, **Chi phí lương**.

| Báo cáo | Nội dung | Nguồn dữ liệu |
|---|---|---|
| `HeadcountReport` – biến động | Theo từng tháng: đầu kỳ, tuyển mới, nghỉ việc (trong đó tự nghỉ), cuối kỳ, bình quân, tỷ lệ nghỉ việc; nhân sự cuối kỳ theo phòng ban | Ngày vào làm, ngày nghỉ việc, phòng ban (OrangeHRM) |
| `TurnoverReport` – nghỉ việc | Phân theo hình thức nghỉ, thâm niên khi nghỉ, phòng ban, lý do; số người nghỉ trong 6 tháng đầu; danh sách người nghỉ | Như trên, kèm hình thức và lý do nghỉ |
| `PayrollCostReport` – chi phí lương | Theo tháng: lương theo ngày công, thưởng, phụ cấp, làm thêm giờ, BH công ty đóng, KPCĐ, tổng chi phí, bình quân/người, % so với tháng trước; chia theo vị trí hoặc phòng ban | Bảng lương đã import (mục 1) |

### Quy ước tính

- **Ngày nghỉ việc** là ngày làm việc cuối cùng. Người nghỉ đúng ngày cuối tháng không được tính vào cuối kỳ của tháng đó. Nhờ vậy luôn có: **Cuối kỳ = Đầu kỳ + Tuyển mới − Nghỉ việc** (có test kiểm tra).
- **Tỷ lệ nghỉ việc** = số người nghỉ ÷ nhân sự bình quân, với nhân sự bình quân của tháng = (đầu kỳ + cuối kỳ) / 2. Tỷ lệ cả kỳ dùng trung bình các tháng.
- **Tổng chi phí lương** = lương theo ngày công + thưởng + phụ cấp + làm thêm giờ + khoản khác + BH công ty đóng + KPCĐ + thừa/thiếu. Công thức này khớp cột "Tổng chi phí nhân viên" của bảng lương DOIT. Đã đối chiếu với bảng lương 06/2026 thật: 245.709.000 đ, đúng bằng dòng GRAND TOTAL.
- Bảng lương có cả HĐ dịch vụ và thực tập sinh thì số liệu chi phí cũng gồm họ, được tách thành nhóm riêng khi chia theo vị trí.

```java
HrReport report = HrReport.build("CÔNG TY TNHH CÔNG NGHỆ VÀ TRUYỀN THÔNG DO IT",
        YearMonth.of(2026, 1), YearMonth.of(2026, 6),
        employmentRecords,                     // từ OrangeHRM
        payrollsByMonth,                       // SortedMap<YearMonth, List<PayrollLine>> từ các lần import
        line -> departmentOf(line.employeeCode()), "Phòng ban");
try (OutputStream out = Files.newOutputStream(Path.of("BaoCaoNhanSu_2026H1.xlsx"))) {
    new ReportExcelWriter().write(report, out);
}
```

**Dữ liệu cần thêm:** để báo cáo nghỉ việc có ý nghĩa, mỗi lần cho nhân viên nghỉ, HR phải chọn **hình thức nghỉ** (tự nghỉ / hết hạn HĐ / không đạt thử việc / công ty chấm dứt / khác) và ghi **lý do**. OrangeHRM có sẵn màn hình nghỉ việc với ngày và lý do; danh sách lý do nên cấu hình khớp với 5 hình thức trên.

## 6. Chấm công GPS/WiFi – phần server (package `vn.doit.hr.attendance`)

Phần lõi kiểm tra từng lần chấm công và tính bảng công. App Android sẽ gọi vào phần này (bước tiếp theo).

### Kiểm tra một lần chấm công (`PunchValidator`)

Các bước theo thứ tự. Bước nào từ chối thì dừng tại đó.

| # | Kiểm tra | Kết quả |
|---|---|---|
| 1 | Thiết bị: mỗi nhân viên gắn 1 điện thoại | Thiết bị lạ → từ chối · Thiết bị đầu tiên → chờ HR xác nhận |
| 2 | Vị trí giả lập (Android báo `isMock`) | Từ chối |
| 3 | Giờ điện thoại lệch giờ server > 5 phút | Từ chối. Giờ ghi nhận luôn là giờ server |
| 4 | Hôm nay phải làm ở đâu: lấy `locationOn(ngày)` từ lịch sử vị trí (mục 4) | Chưa có dữ liệu → chờ duyệt |
| 5 | Làm từ xa | Chấp nhận, không cần vị trí |
| 6 | Đúng WiFi (BSSID) hoặc đúng IP public của điểm chấm công | Chấp nhận (tin cậy hơn GPS trong tòa nhà) |
| 7 | GPS (bán kính mặc định **200 m**, đặt riêng cho từng điểm được) | Trong bán kính → chấp nhận · Sát ranh giới (trong bán kính + sai số) hoặc sai số > 100 m → chờ duyệt · Xa hơn → từ chối, báo rõ đang cách bao nhiêu mét và hôm nay phải làm ở đâu |

Nhờ bước 4, nhân viên đang onsite ở khách hàng mà chấm công tại văn phòng công ty sẽ bị từ chối. Ngày đi công tác thì điểm hợp lệ tự chuyển sang nơi công tác. Nơi chưa khai báo điểm chấm công thì chờ duyệt chứ không từ chối, để nhân viên không bị mất công vì lỗi dữ liệu.

**Giới hạn cần biết:** máy đã root hoặc jailbreak vẫn có thể giả GPS, giả BSSID mà không bị báo `isMock`. Kiểm tra theo IP public do server tự thấy nên khó giả nhất. Với văn phòng công ty, nên ưu tiên WiFi/IP và chỉ dùng GPS làm phương án dự phòng.

### Tính công (`WorkSchedule`, `Timesheet`)

- **Lịch làm việc:** thứ Hai–thứ Sáu 08:00–17:00 (nghỉ trưa 12:00–13:00), cho phép muộn 5 phút. Ngày lễ khai báo riêng.
- **Thứ Bảy có 2 chế độ** (`SaturdayMode`):
  - `NGHI` – **mặc định, đúng thực tế hiện nay**: thứ Bảy là ngày nghỉ, ai đi làm được ghi "Làm ngày nghỉ" và tính qua quy trình OT;
  - `LAM_NUA_NGAY` – theo HĐLĐ mẫu: thứ Bảy 08:00–12:00, đủ giờ = 0,5 công.
- **Giờ vào / giờ ra:** lần chấm VÀO sớm nhất / lần chấm RA muộn nhất trong ngày. Chỉ tính phần nằm trong ca, trừ giờ nghỉ trưa. Làm ngoài ca không tự tính, phải đăng ký OT.
- **Công ngày:** trọng số ngày × giờ làm ÷ giờ chuẩn, làm tròn xuống 0,01. Thứ Hai–Sáu đủ giờ = 1 công. Vào muộn trong 5 phút cho phép thì coi như vào đúng giờ, không bị trừ công.
- **Thiếu giờ vào hoặc giờ ra:** tạm tính 0 công và đưa vào danh sách cần giải trình.
- **Tổng hợp tháng:** công chuẩn, công thực tế, số lần và số phút đi muộn, số lần về sớm, ngày vắng, ngày thiếu chấm công.

### Giải trình và duyệt chấm công (`AttendanceCorrections`)

Có 3 đường để một lần chấm công được tính, ngoài trường hợp app chấp nhận ngay:

| Đường | Ai làm | Khi nào |
|---|---|---|
| **Duyệt lần chấm chờ duyệt** | Quản lý bấm Duyệt / Từ chối | Lần chấm trên app ở trạng thái chờ duyệt (GPS kém, sát ranh giới, thiết bị mới, nơi chưa khai báo) |
| **Giải trình** | Nhân viên gửi giờ vào/ra kèm lý do → quản lý duyệt | Lần chấm bị từ chối, quên chấm, app lỗi |
| **Chấm tay** | Quản lý ghi trực tiếp giờ vào/ra, bắt buộc có lý do | Điện thoại hỏng, hết pin, nhân viên không dùng được app |

Quy tắc (đều có test):
- Chỉ **quản lý trực tiếp hoặc HR** được duyệt hoặc chấm tay; **không ai tự duyệt cho mình**.
- Từ chối hoặc chấm tay **bắt buộc ghi lý do**. Mọi quyết định lưu người duyệt và thời điểm.
- Không giải trình hay chấm tay trước cho thời điểm chưa tới. Giờ đề xuất phải nằm đúng ngày được giải trình.
- **Khóa công:** sau ngày 05 của tháng sau (`LockPolicy`, đổi được), công tháng trước không sửa được nữa, để bảng lương không bị thay đổi sau khi đã chốt.
- Bảng công ghi rõ nguồn của từng lần chấm: chấm trên app, giải trình đã duyệt, hay quản lý chấm tay.

### API cho app (đề xuất)

```
POST /api/attendance/punches
{ "type": "IN", "clientTime": "2026-10-19T00:58:12Z", "deviceId": "…",
  "latitude": 21.0279, "longitude": 105.8360, "accuracy": 15.0, "mock": false, "wifiBssid": "aa:bb:cc:dd:ee:01" }
→ 200 { "status": "ACCEPTED", "method": "GPS", "serverTime": "…", "site": "Văn phòng DOIT", "message": "Đã chấm công tại Văn phòng DOIT" }
```
- Mã nhân viên lấy từ token đăng nhập, không lấy từ body.
- `clientIp` do server tự đọc (nếu sau reverse proxy thì lấy từ `X-Forwarded-For` của proxy tin cậy).

### Dữ liệu vị trí là dữ liệu cá nhân

Luật Bảo vệ dữ liệu cá nhân và Nghị định 356/2025/NĐ-CP (thay Nghị định 13/2023) có hiệu lực từ 01/01/2026. Dữ liệu vị trí thuộc nhóm phải thông báo cho người bị ảnh hưởng trong 72 giờ nếu bị lộ. Thiết kế hiện tại:
- **Chỉ lấy vị trí đúng lúc bấm chấm công,** không theo dõi nền, không chạy dịch vụ định vị liên tục.
- **Bảng công chỉ cần kết quả** (điểm chấm công, khoảng cách, trạng thái). Nên **xóa tọa độ gốc sau một thời hạn** (vd 90 ngày, sau khi chốt công và hết hạn khiếu nại), và xóa dữ liệu chấm công khi nhân viên nghỉ việc theo quy định.
- **Cần văn bản thông báo / xin đồng ý** của nhân viên về việc thu thập vị trí khi chấm công (mục đích, thời hạn lưu, ai được xem). Phần này nên nhờ người phụ trách pháp lý soạn. Tôi không phải luật sư.
