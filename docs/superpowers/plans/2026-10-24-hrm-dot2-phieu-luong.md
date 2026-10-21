# Phiếu lương cho nhân viên – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kế toán/Quản trị gửi mỗi người một email kèm phiếu lương PDF có mật khẩu sau khi kỳ lương được duyệt; nhân viên xem lại phiếu ở Hồ sơ của tôi.

**Architecture:** Lõi `core` tính các dòng a → q của phiếu từ một dòng lương (`Payslip`) và dựng thẻ mẫu (`PayslipFields`). App sinh `.docx` từ mẫu Word loại `PHIEU_LUONG` (mẫu gốc dựng theo tab "Phiếu lương"), chuyển PDF qua dịch vụ PDF có sẵn, mã hóa bằng PDFBox, gửi qua `JavaMailSender` ở luồng nền và ghi `payslip_delivery`.

**Tech Stack:** Java 21 / Spring Boot 4.1, `core` Java 17 không Spring, PostgreSQL + Liquibase + Envers, Apache POI, PDFBox (đã có), Gotenberg, React 19 + Vitest.

**Spec:** `docs/superpowers/specs/2026-10-24-hrm-dot2-phieu-luong-design.md`

## Global Constraints

- Phiếu theo **phiên bản chính thức** của kỳ **Đã duyệt**; không gửi kỳ chưa duyệt (400 `payslipnotapproved`).
- Thân email **không có số tiền**. Tiêu đề `[DOIT] Phiếu lương tháng MM/YYYY`. Tệp `PhieuLuong_MM-YYYY_<mã NV>.pdf`. Người gửi `jhipster.mail.from`.
- Mật khẩu PDF = 6 chữ số cuối của CCCD trên hồ sơ (chỉ lấy chữ số); ít hơn 6 chữ số → không gửi người đó.
- Gửi tới **email trên hồ sơ nhân viên**; trống hoặc đuôi `@localhost` → không gửi người đó.
- q (Thực lĩnh) lệch `THUC_NHAN` quá 1 đồng → không gửi người đó, lỗi "Phiếu lệch bảng lương N đồng".
- Dòng không có ngày công chuẩn: c = `LUONG_GOP`; a, a1, a2, b, ngày công chuẩn để trống.
- Ghi chú in theo `rules_snapshot` của phiên bản; luật đổi không tác động ngược; dòng "Căn cứ" chỉ khi bản chụp có `legalBasis`.
- Quyền: gửi/xem tình trạng/tải phiếu người khác/sửa mẫu Phiếu lương = ADMIN, KE_TOAN. HR không bao giờ thấy số lương. Tự xem: mọi tài khoản có hồ sơ gắn.
- Mỗi lần tải phiếu ghi `SensitiveAccessLog`.
- Không lưu file PDF; Liquibase chỉ thêm changeset mới (kèm bảng `_aud`).
- Lỗi nghiệp vụ dùng `EmployeeRuleException(message, key)` (400 `error.<key>` qua `ExceptionTranslator`).
- Chạy backend `verify` một mình (không chạy npm song song).

## Review Focus

1. **Bấm gửi hai lần liền / hai người cùng bấm** → không ai nhận hai email; người đang `PENDING` bị bỏ qua. Test ở Task 3 (`secondSendSkipsPending`).
2. **Kỳ bị mở lại khi đang gửi** → các dòng còn `PENDING` thành `FAILED` "Kỳ đã mở lại, chưa gửi"; không gửi phiếu của phiên bản cũ. Test ở Task 3 (`reopenDuringSendStopsRemaining`).
3. **Máy chủ khởi động lại khi còn `PENDING`** → lúc khởi động chuyển thành `FAILED` "Gửi bị gián đoạn, hãy gửi lại". Test ở Task 3 (`pendingAfterRestartBecomesFailed`).
4. **CCCD có khoảng trắng, chữ, hoặc CMND 9 số** → chỉ lấy chữ số, lấy 6 số cuối; dưới 6 số → `FAILED`. Test ở Task 2 (`passwordFromIdNumber`).
5. **Nhân viên đoán URL tải phiếu người khác hoặc kỳ chưa gửi** → 404, không ghi lộ thông tin. Test ở Task 4 (`otherEmployeeOrUnsentPeriodIs404`).

---

### Task 1: Lõi tính phiếu lương

**Files:**
- Create: `core/src/main/java/vn/doit/hr/payroll/Payslip.java`, `core/src/main/java/vn/doit/hr/payroll/PayslipFields.java`
- Modify: `core/src/main/java/vn/doit/hr/contract/TemplateTags.java` (thêm `PAYSLIP_TAGS`)
- Test: `core/src/test/java/vn/doit/hr/payroll/PayslipTest.java`, `app/src/test/java/vn/doit/hrm/service/payroll/PayslipFixtureTest.java`

**Interfaces:**
- Produces:
  - `record Payslip(boolean prorated, BigDecimal a, a1, a2, b, standardDays, c, d, e, e1, e2, e3, e4, e5, e6, f, g, h, h1, h2, i, j, k, l, m, n, o, o1, o2, p, q)` với `static Payslip of(Map<PayrollField, BigDecimal> amounts)`; `prorated` = có ngày công chuẩn > 0; khi `!prorated` thì a, a1, a2, b, standardDays là `null`.
  - `BigDecimal Payslip.differenceFromNet(Map<PayrollField, BigDecimal> amounts)` = q − `THUC_NHAN`.
  - `record PayslipFields.Header(String companyName, YearMonth period, String fullName, String employeeCode)`; `record PayslipFields.Notes(BigDecimal socialInsuranceCap, BigDecimal unemploymentInsuranceCap, BigDecimal personalDeduction, BigDecimal dependantDeduction, String legalBasis)`.
  - `static Map<String, String> PayslipFields.toMap(Header, Payslip, Notes)`: khóa `tenCongTy`, `kyLuong` ("06/2026"), `hoTen`, `maNhanVien`, `ngayCongChuan`, `a`, `a1` … `q` (theo bảng spec §2, số dạng `26.215.000`, null → ""), `ghiChu1` … `ghiChu4` (chữ đúng spec §2; `ghiChu4` = "" khi `legalBasis` trống; `notes == null` → cả 4 là "").
  - `TemplateTags.PAYSLIP_TAGS` = tập khóa của `PayslipFields.toMap`.

- [ ] **Step 1: Viết test lõi `PayslipTest`:**
  - `formulasLikeExcel`: a1=20.000.000, a2=1.000.000, standardDays=22, b=20, d=500.000, e1..e6 mỗi cái 100.000, f=0, h1=200.000, h2=0, BHXH/BHYT/BHTN NLĐ = 1.680.000/315.000/210.000, l=15.500.000, m=0, THUE_TNCN=100.000, QUYET_TOAN_THUE=0, GIAM_TRU_KHAC=0 → c = 20.000.000/22×20+1.000.000 (so sánh tới 0,01), g = c+d+600.000, n = max(0, g−i−j−k−l−m), q = g+h−i−j−k−o−p.
  - `negativeTaxableIsZero`: l rất lớn → n = 0.
  - `noStandardDaysUsesMonthlyGross`: `NGAY_CONG_CHUAN` vắng, `LUONG_GOP`=5.000.000 → `prorated` false, c = 5.000.000, a/a1/a2/b null.
  - `fieldsMap`: `toMap` có `kyLuong`=`06/2026`, `q` dạng `x.xxx.xxx`, `ghiChu2` chứa số trần BHXH, `ghiChu4` rỗng khi không có căn cứ; khóa đủ như `PAYSLIP_TAGS`.
- [ ] **Step 2:** `mvn -pl core test -Dtest=PayslipTest` → FAIL (không biên dịch).
- [ ] **Step 3:** Cài `Payslip`, `PayslipFields`, `PAYSLIP_TAGS`. Ghi chú: `ghiChu1` = "1. Tỉ lệ người lao động đóng: BHXH 8%, BHYT 1,5%, BHTN 1%."; `ghiChu2` = "2. Mức lương đóng BHXH, BHYT tối đa {X} đồng/tháng; mức lương đóng BHTN tối đa {Y} đồng/tháng."; `ghiChu3` = "3. Giảm trừ gia cảnh: bản thân {Z} đồng/tháng, người phụ thuộc {W} đồng/người/tháng."; `ghiChu4` = "4. Căn cứ: {legalBasis}.". Làm tròn khi in: `RoundingMode.HALF_UP` về đồng.
- [ ] **Step 4:** `PayslipFixtureTest` (app, unit): đọc `PayrollFixture.RESOURCE` bằng `PayrollSheetParser`, với mọi dòng có `THUC_NHAN` (14 dòng) → `|differenceFromNet| ≤ 1`, và đúng 3 dòng `prorated == false`. Chạy core + test này → PASS.
- [ ] **Step 5:** Commit `"Lõi phiếu lương: các dòng a–q theo tab Phiếu lương, ghi chú theo luật của kỳ"`.

### Task 2: Mẫu Phiếu lương, PDF có mật khẩu, quyền mẫu

**Files:**
- Create: `core/src/main/resources/templates/DOIT_PhieuLuong_template.docx` (dựng bằng script python-docx ngoài repo: bảng 3 cột Nhãn / Ký hiệu / Số tiền theo bảng spec §2, đầu phiếu, khối Ghi chú; font Times New Roman 12), `app/src/main/java/vn/doit/hrm/service/payslip/PayslipDocumentService.java`, `.../payslip/PdfPassword.java`
- Modify: `TemplateKind` (thêm `PHIEU_LUONG("Phiếu lương", TemplateTags.PAYSLIP_TAGS, "templates/DOIT_PhieuLuong_template.docx")`), `DocumentTemplateService` + `DocumentTemplateResource` (quyền theo loại), `PayrollPeriodService.RulesSnapshot` (+`legalBasis`), `RuleBookService` (`Optional<String> legalBasis(YearMonth effectiveFrom)`)
- Test: `core/.../TemplateTagsTest` (mẫu phiếu), `app/.../service/payslip/PdfPasswordTest.java`, `app/.../web/rest/PayslipDocumentIT.java`, `DocumentTemplateResourceIT` (quyền)

**Interfaces:**
- Consumes: Task 1 `Payslip.of`, `PayslipFields.toMap`.
- Produces:
  - `static Optional<String> PdfPassword.fromIdNumber(String idNumber)` — chữ số, 6 số cuối; ít hơn 6 → empty.
  - `static byte[] PdfPassword.encrypt(byte[] pdf, String password)` — PDFBox AES-256, owner password ngẫu nhiên, cho in/sao chép.
  - `PayslipDocumentService.Prepared prepare(PayrollPeriod period, int version, Long employeeId)` → `record Prepared(Employee employee, Payslip payslip, BigDecimal differenceFromNet, byte[] docx)`; ném `EmployeeRuleException` key `payslipnoline` khi người đó không có dòng trong phiên bản.
  - `byte[] PayslipDocumentService.pdf(Prepared p)` — qua `PdfService`; `PdfUnavailableException` để người gọi xử lý.
  - `GET /api/payroll/periods/{period}/payslips/{employeeId}/pdf` (ADMIN, KE_TOAN): PDF không mật khẩu của phiên bản chính thức, ghi `SensitiveAccessLog`; kỳ chưa duyệt → 400 `payslipnotapproved`.
  - Mẫu: loại `PHIEU_LUONG` chỉ ADMIN, KE_TOAN liệt kê/tải/tải lên/dùng lại; các loại khác giữ ADMIN, HR. `GET /api/document-templates` trả các loại người gọi được quản lý. `DocumentTemplateResource` mở cho KE_TOAN ở mức lớp, kiểm tra loại trong service (403 khi sai loại).
  - `rules_snapshot` mới có `legalBasis`; đọc bản chụp cũ thiếu trường vẫn được.

- [ ] **Step 1: Viết test:**
  - `TemplateTagsTest.payslipTemplateHasKnownTagsOnly`: không thẻ bị cắt, `PAYSLIP_TAGS.containsAll(tags)`, có `q`, `ghiChu4`.
  - `PdfPasswordTest.passwordFromIdNumber`: `"001 095 012345"` → `012345`; `"C1234567"` → `234567` (chỉ lấy chữ số); `"12345"` → empty; null → empty. `encryptedNeedsPassword`: PDF một trang tạo bằng PDFBox, mã hóa với `123456` → `Loader.loadPDF(bytes)` ném `InvalidPasswordException`, `Loader.loadPDF(bytes, "123456")` mở được.
  - `PayslipDocumentIT` (dùng `PayrollTestData`, `PayrollFlow`, `@MockitoBean PdfService` trả PDF một trang): kỳ 2026-06 đã xác nhận và duyệt; KE_TOAN `GET …/payslips/{id DOIT2401}/pdf` → 200 `application/pdf`, có 1 dòng `sensitive_access_log` mới; HR → 403; kỳ chưa duyệt → 400 `error.payslipnotapproved`; docx sinh ra (bắt qua `ArgumentCaptor` của `PdfService.toPdf`) chứa họ tên và "Thực lĩnh", không còn `{{`.
  - `DocumentTemplateResourceIT`: KE_TOAN list chỉ có `PHIEU_LUONG`; HR list không có `PHIEU_LUONG`; HR tải lên `PHIEU_LUONG` → 403; KE_TOAN tải lên `HDLD` → 403.
  - `PayrollPeriodIT` (hoặc test mới): xác nhận phiên bản khi mốc luật có căn cứ → `rules_snapshot` có `legalBasis`.
- [ ] **Step 2:** Chạy các test trên → FAIL.
- [ ] **Step 3:** Dựng mẫu `.docx`, cài các phần trên. Mã NV in theo hồ sơ (`Employee.code`), họ tên theo hồ sơ.
- [ ] **Step 4:** Chạy lại → PASS.
- [ ] **Step 5:** Commit `"Mẫu Phiếu lương, PDF có mật khẩu, quyền mẫu theo loại"`.

### Task 3: Gửi email và tình trạng gửi

**Files:**
- Create: changelog `20261024000200_payslip_delivery.xml` (+ `payslip_delivery_aud`), `domain/PayslipDelivery.java` (`@Audited`), `domain/enumeration/PayslipDeliveryStatus.java` (`PENDING`, `SENT`, `FAILED`), `repository/PayslipDeliveryRepository.java`, `service/payslip/PayslipMailer.java`, `service/payslip/PayslipSendService.java`, `service/payslip/dto/PayslipStatusDTO.java`, `web/rest/PayslipResource.java`
- Modify: `PayrollPeriodService.reopen` (đánh dấu `PENDING` còn lại thành `FAILED`)
- Test: `web/rest/PayslipSendIT.java`

**Interfaces:**
- Consumes: Task 2 `PayslipDocumentService.prepare/pdf`, `PdfPassword`, `ReminderRecipients.usable` (đổi sang `public static` để dùng chung), `ReminderMailer.configured()`.
- Produces:
  - `PayslipMailer.send(String to, String subject, String text, String fileName, byte[] pdf)` — `MimeMessageHelper(multipart=true, UTF-8)`, timeout như `ReminderMailer`.
  - `List<PayslipStatusDTO> PayslipSendService.request(String period, List<Long> employeeIds)` — `employeeIds == null` = mọi người chưa nhận phiếu của phiên bản chính thức (bỏ người `SENT` cùng phiên bản và người `PENDING`); danh sách cụ thể = gửi lại đúng những người đó (bỏ người `PENDING`). Tạo dòng `PENDING`, chạy `sendPending(periodId)` trên `taskExecutor` sau khi transaction commit, trả tình trạng.
  - `void PayslipSendService.sendPending(Long periodId)` — lần lượt từng dòng `PENDING`: kỳ không còn `DA_DUYET` hoặc phiên bản khác phiên bản chính thức → `FAILED` "Kỳ đã mở lại, chưa gửi"; thiếu email/CCCD/lệch → `FAILED` có lý do; gửi lỗi → `FAILED` với thông điệp lỗi (≤ 500 ký tự); thành công → `SENT`, `sent_at`.
  - `@EventListener(ApplicationReadyEvent)` `failInterrupted()` — `PENDING` → `FAILED` "Gửi bị gián đoạn, hãy gửi lại".
  - `List<PayslipStatusDTO> status(String period)` — mỗi người có dòng trong phiên bản chính thức: `PayslipStatusDTO(Long employeeId, String employeeCode, String fullName, String email, PayslipDeliveryStatus status /* null = chưa gửi */, Integer version, Instant sentAt, String error, boolean staleVersion)`.
  - API (ADMIN, KE_TOAN): `GET /api/payroll/periods/{period}/payslips` → status; `POST /api/payroll/periods/{period}/payslips/send` body `{"employeeIds": [..] | null}` → 202 + status. Kỳ chưa duyệt → 400 `payslipnotapproved`; chưa cấu hình SMTP → 400 `mailnotconfigured`.
  - Email: tiêu đề `[DOIT] Phiếu lương tháng 06/2026`; thân: "Chào {họ tên}," / "Công ty gửi bạn phiếu lương tháng 06/2026 trong tệp đính kèm." / "Mật khẩu mở tệp là 6 số cuối số CCCD của bạn." / "Có thắc mắc về phiếu lương, vui lòng liên hệ bộ phận Kế toán." / "— Email tự động từ hệ thống HRM DOIT."

- [ ] **Step 1: Viết `PayslipSendIT`** (`@MockitoBean JavaMailSender` giả như `ReminderJobIT`, `spring.mail.host=smtp.test`, `@MockitoBean PdfService`, hồ sơ mẫu có email `<mã>@doit.test` và CCCD 12 số trừ các ca lỗi; chờ luồng nền bằng `Awaitility`-kiểu vòng lặp ≤ 10 giây trên `status`):
  - `sendsOneMailPerPersonWithPdfNoAmounts`: mọi người có email + CCCD → `SENT`; mỗi địa chỉ đúng 1 thư, có 1 tệp `application/pdf` tên `PhieuLuong_06-2026_<mã>.pdf`; thân không chứa số `THUC_NHAN` của người đó (dạng `x.xxx.xxx` và số trần).
  - `missingEmailOrIdOrMismatchFails`: một người không email → `FAILED` "Chưa có email"; một người CCCD 5 số → `FAILED` "CCCD chưa đủ 6 số"; không gọi `send` cho họ.
  - `failedRecipientRetriedOthersNot`: máy chủ ném `MailSendException` cho một địa chỉ → người đó `FAILED`, còn lại `SENT`; gửi lần 2 (`employeeIds=null`) chỉ gửi người đó.
  - `secondSendSkipsPending`: gọi `request` hai lần liền trước khi luồng nền chạy (executor giả chạy tay) → mỗi người đúng 1 thư.
  - `reopenDuringSendStopsRemaining`: tạo `PENDING`, mở lại kỳ, chạy `sendPending` → tất cả `FAILED` "Kỳ đã mở lại, chưa gửi", 0 thư.
  - `pendingAfterRestartBecomesFailed`: chèn dòng `PENDING`, gọi `failInterrupted()` → `FAILED` "Gửi bị gián đoạn, hãy gửi lại".
  - `newVersionMarksStale`: gửi xong, mở lại, import + xác nhận + duyệt phiên bản 2 → `status` có `staleVersion=true` cho người đã nhận.
  - `notApprovedAndRoles`: kỳ chưa duyệt → 400 `error.payslipnotapproved`; HR, BGD, NHAN_VIEN → 403.
- [ ] **Step 2:** Chạy → FAIL.
- [ ] **Step 3:** Cài đặt như Interfaces. `employeeIds` dùng cho nút "Gửi lại" một người.
- [ ] **Step 4:** Chạy `PayslipSendIT` + toàn bộ backend → PASS.
- [ ] **Step 5:** Commit `"Gửi phiếu lương qua email: chạy nền, tình trạng từng người, gửi lại"`.

### Task 4: Nhân viên tự xem phiếu

**Files:**
- Create: `service/payslip/OwnPayslipService.java`, `service/payslip/dto/OwnPayslipDTO.java`
- Modify: `web/rest/PayslipResource.java` (thêm endpoint tài khoản) hoặc `AccountResource`
- Test: `web/rest/OwnPayslipIT.java`

**Interfaces:**
- Consumes: Task 2 `PayslipDocumentService`, Task 3 `PayslipDeliveryRepository`.
- Produces:
  - `GET /api/account/payslips` (đã đăng nhập) → `[OwnPayslipDTO(String period, int version, Instant sentAt)]` — các kỳ có dòng `SENT` cho hồ sơ gắn tài khoản; mỗi kỳ lấy phiên bản đã gửi gần nhất; mới nhất trước; tài khoản không gắn hồ sơ → `[]`.
  - `GET /api/account/payslips/{period}/pdf` → PDF không mật khẩu theo phiên bản trong lượt `SENT` gần nhất; không có lượt `SENT` của chính mình → 404; ghi `SensitiveAccessLog`.

- [ ] **Step 1: Viết `OwnPayslipIT`:** nhân viên A (tài khoản gắn hồ sơ DOIT2401, vai trò NHAN_VIEN) sau khi kỳ 2026-06 được gửi: list có `2026-06`, tải PDF 200 + 1 dòng nhật ký; `otherEmployeeOrUnsentPeriodIs404`: A tải kỳ `2026-05` (không có) → 404, không có endpoint nào nhận id người khác; tài khoản không gắn hồ sơ → `[]`.
- [ ] **Step 2:** Chạy → FAIL.
- [ ] **Step 3:** Cài đặt.
- [ ] **Step 4:** Chạy → PASS.
- [ ] **Step 5:** Commit `"Nhân viên tự xem phiếu lương ở Hồ sơ của tôi"`.

### Task 5: Giao diện, README, kiểm tra cuối

**Files:**
- Create: `modules/payroll/payslip-panel.tsx` (+ spec), `modules/my-profile/own-payslips.tsx` (+ spec)
- Modify: `modules/payroll/period-detail.tsx` (đặt `PayslipPanel` khi kỳ `DA_DUYET`), `modules/payroll/index.tsx` (tab **Mẫu phiếu lương** `/bang-luong/mau-phieu-luong` dùng `DocumentTemplates`), `modules/my-profile/my-profile.tsx` (khối **Phiếu lương**), `README.md` (mục "Phiếu lương")
- Test: Vitest cho hai component mới, cập nhật `period-detail.spec.tsx`

**Interfaces:**
- Consumes: API Task 2–4.

- [ ] **Step 1: Viết test Vitest:**
  - `PayslipPanel`: hiện bảng tình trạng (Chưa gửi / Đã gửi lúc dd/MM/yyyy HH:mm / Lỗi: lý do / "Theo phiên bản cũ"); **Gửi phiếu lương** gọi `POST api/payroll/periods/2026-06/payslips/send` với `{ employeeIds: null }`; **Gửi lại** trên một dòng gọi với `{ employeeIds: [id] }`; **Tải** gọi `downloadFile('api/payroll/periods/2026-06/payslips/{id}/pdf', 'PhieuLuong_06-2026_<mã>.pdf')`; lỗi 400 `error.mailnotconfigured` hiện câu tiếng Việt.
  - `PeriodDetail`: kỳ `NHAP` không có `PayslipPanel`; kỳ `DA_DUYET` có.
  - `OwnPayslips`: liệt kê kỳ "06/2026"; **Tải PDF** gọi `downloadFile('api/account/payslips/2026-06/pdf', 'PhieuLuong_06-2026.pdf')`; danh sách rỗng → "Chưa có phiếu lương nào được gửi."
- [ ] **Step 2:** Chạy → FAIL.
- [ ] **Step 3:** Cài đặt. Bảng tình trạng tự tải lại mỗi 3 giây khi còn dòng `PENDING`.
- [ ] **Step 4:** `npm test`, `tsc`, `lint`, backend `verify` (riêng) → PASS. README: người gửi, mật khẩu, lý do lỗi thường gặp, gửi lại, tự xem, mẫu phiếu.
- [ ] **Step 5:** Commit `"Giao diện phiếu lương: gửi, tình trạng, tự xem; README"`.

## Kiểm tra cuối kế hoạch

- [ ] Backend `verify`, `npm test`, `tsc`, `lint` đạt.
- [ ] Build lại bản chạy trên máy (`docker compose up -d --build`); import file mẫu ẩn danh kỳ thử, duyệt, gửi phiếu tới một hộp thư thử qua máy chủ mail công ty (chỉ khi người dùng đồng ý và cho địa chỉ nhận; hồ sơ thử dùng email đó); mở PDF bằng 6 số cuối CCCD; xem PDF trong trình duyệt.
- [ ] Tự rà soát toàn nhánh (không dùng subagent), ghi ledger.
