# DOIT HRM – Kế hoạch 2: Hợp đồng & văn bản từ mẫu

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HR tạo được HĐLĐ và Quyết định cử nhân sự từ dữ liệu hồ sơ bằng vài cú bấm, ra file `.docx` và `.pdf`. Văn bản được đánh số theo loại và năm, lưu theo phiên bản trên từng HĐ hoặc quyết định. Bản đã ký được tải lên lại. HR và Quản trị tự thay mẫu Word, hệ thống kiểm tra thẻ trước khi nhận.

**Architecture:** Lõi `core/` đã có `ContractFields`, `AssignmentDecision.toFields()` và `DocxTemplateRenderer` (poi-tl). Phần `app/` thêm:
- **Kho mẫu** (`document_template`, có phiên bản, mặc định lấy 2 mẫu có sẵn trong `core`).
- **Đánh số** (`document_sequence`, khóa dòng).
- **Sinh văn bản:** dựng `ContractData` từ hồ sơ + HĐ + phụ cấp + cấu hình công ty, render `.docx`, gọi dịch vụ PDF, lưu file qua `FileStorageService`.
- **Quyết định cử nhân sự:** ghi giai đoạn vị trí bằng `WorkAssignmentService.add` trong cùng transaction.

PDF do một container **Gotenberg 8** riêng (LibreOffice qua HTTP) chuyển đổi, thêm font Times New Roman.

**Tech Stack:** JHipster 9.4.0 / Spring Boot 4.1 / Java 21, React 19 + Vite/Vitest + react-bootstrap, PostgreSQL 16, Liquibase, Envers, poi-tl (đã có), Gotenberg 8.x (bản đã ra ≥ 2 tuần), Testcontainers.

**Spec:** `docs/superpowers/specs/2026-10-19-hrm-doit-dot1-design.md` §5.2 (Hợp đồng), cùng các quyết định đã chốt sau đó:
- Mức lương in trên HĐ: chỉ in số Net khi lương Net.
- Nghỉ việc khóa tài khoản và không sửa lịch sử.
- Tên đăng nhập = CCCD.

## Global Constraints

- Mọi ràng buộc chung của KH1/KH1b vẫn áp dụng:
  - tiếng Việt; `dd/MM/yyyy`; tiền `1.234.567`; múi giờ `Asia/Ho_Chi_Minh`;
  - vùng bấm ≥ 44px;
  - `core/` không phụ thuộc Spring;
  - quyền kiểm tra ở backend (`@PreAuthorize` + quy tắc URL); Quản trị ⊇ mọi quyền;
  - changeset Liquibase chỉ thêm mới;
  - lỗi nghiệp vụ ném `EmployeeRuleException(key)`, resource đổi sang `BadRequestAlertException`.
- Đánh số [Chốt theo spec]:
  - Mỗi loại văn bản một dãy, chạy lại từ đầu mỗi năm, dạng `DOIT/{năm}/{mã loại}/{số}`.
  - Mã loại: `HĐLĐ` (HDLD_XDTH, HDLD_KXDTH), `HĐTV`, `HĐĐT`, `HĐDV`, `QĐCNS` (quyết định cử nhân sự).
  - Quản trị đặt được số bắt đầu cho từng dãy. Không cấp trùng số; văn bản bị hủy vẫn giữ số.
  - Số được cấp lần đầu khi sinh văn bản (nếu HĐ chưa có số); HR sửa tay được số trên HĐ (vẫn kiểm trùng).
- In lương [Chốt]: in đúng số đã thỏa thuận (`salary`); lương Net chỉ in số Net, không in Gross.
- Ngày kết thúc = ngày bắt đầu + thời hạn − 1 ngày. HĐ không xác định thời hạn in như `ContractFields` hiện có.
- Văn bản sinh ra và bản đã ký chứa CCCD và tài khoản ngân hàng: **chỉ ADMIN, HR** tạo/tải/xóa. KE_TOAN, BGD, NHAN_VIEN → 403. Riêng nhân viên được tải bản **đã ký** HĐ của chính mình.
- PDF: font Times New Roman nhúng trong PDF, không bị thay font khác.

## Review Focus

1. **HR sửa mẫu trong Word làm cắt thẻ** (định dạng nửa thẻ `{{hoTen}}`). Upload mẫu bị từ chối và nêu rõ thẻ nào bị cắt. → Task 3.
2. **Hồ sơ thiếu dữ liệu bắt buộc** (thiếu CCCD, nơi cư trú…). Sinh HĐ trả về danh sách trường thiếu bằng tiếng Việt, không lặng lẽ in ô trống. HR có thể bấm "Vẫn tạo" (`force=true`). → Task 4.
3. **Hai HR sinh văn bản cùng lúc.** Hai số khác nhau, liên tiếp, không trùng. → Task 2.
4. **Dịch vụ PDF không chạy.** Vẫn có `.docx`, báo rõ "Chưa tạo được PDF", có nút "Tạo lại PDF". → Task 4.
5. **PDF giữ đúng dấu tiếng Việt và font.** Trích chữ từ PDF ra đúng "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM", font nhúng là Times New Roman. → Task 1.

---

### Task 1: Dịch vụ PDF (Gotenberg) và `PdfService`

**Files:**
- Create: `ops/pdf/Dockerfile` (FROM `gotenberg/gotenberg:<8.x ra ≥ 2 tuần>`, cài `ttf-mscorefonts-installer`, thêm nguồn `contrib` nếu cần, chấp nhận EULA không tương tác; ghim đúng phiên bản)
- Modify: `docker-compose.yml` (dịch vụ `pdf`, build `./ops/pdf`, không publish cổng, `app` phụ thuộc `pdf`, env `APPLICATION_PDF_URL=http://pdf:3000`), `README.md` (mục PDF)
- Create: `app/src/main/java/vn/doit/hrm/service/pdf/PdfService.java` (interface), `GotenbergPdfService.java`
- Modify: `ApplicationProperties` (`pdf.url`, `pdf.timeoutSeconds` = 60)
- Test: `app/src/test/java/vn/doit/hrm/service/pdf/GotenbergPdfServiceIT.java` (Testcontainers `GenericContainer` dựng từ `ops/pdf/Dockerfile`, `@Tag("pdf")`)

**Interfaces:**
- Produces: `byte[] PdfService.toPdf(byte[] docx, String fileName)`. Ném `PdfUnavailableException` (unchecked, ở `service.pdf`) khi không kết nối được hoặc quá thời gian; message tiếng Việt "Chưa tạo được PDF — dịch vụ chuyển đổi không phản hồi".

- [ ] **Step 1: Viết test.** `convertsVietnameseDocx`: render `DOIT_HDLD_template.docx` bằng `ContractGenerator` với dữ liệu mẫu, chuyển sang PDF, rồi kiểm tra bằng PDFBox:
  - chữ trích ra chứa `CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM` và họ tên in hoa có dấu;
  - danh sách font nhúng chứa một font tên chứa `TimesNewRoman` (Review Focus 5).

  `unavailableThrows`: URL trỏ cổng không mở → `PdfUnavailableException`.
- [ ] **Step 2:** Chạy IT. Expected: FAIL (chưa có lớp).
- [ ] **Step 3:** Cài đặt.
  - Gọi `POST {url}/forms/libreoffice/convert` (multipart `files`) bằng `RestClient`, có timeout.
  - Thêm `org.apache.pdfbox:pdfbox` vào **test scope** (phiên bản ổn định ≥ 2 tuần).
- [ ] **Step 4:** Chạy IT + `docker compose build pdf`. Expected: PASS.
- [ ] **Step 5:** Commit `"Dịch vụ PDF Gotenberg có font Times New Roman"`.

### Task 2: Đánh số văn bản theo loại và năm

**Files:**
- Create: changelog `document_sequence` (`id`, `type_code` varchar(10), `year` int, `next_value` int, unique(type_code, year)) + `_aud`
- Create: `domain/DocumentSequence.java`, `repository/DocumentSequenceRepository.java` (`findForUpdate(typeCode, year)` với `PESSIMISTIC_WRITE`)
- Create: `service/DocumentNumberService.java`, `web/rest/DocumentSequenceResource.java`
- Create: frontend `modules/administration/document-numbers/` (bảng dãy số theo năm, sửa số bắt đầu — chỉ ADMIN)
- Test: `DocumentNumberServiceIT`, `DocumentSequenceResourceIT`, Vitest cho trang

**Interfaces:**
- Produces:
  - `enum DocumentType { HDLD("HĐLĐ"), HDTV("HĐTV"), HDDT("HĐĐT"), HDDV("HĐDV"), QD_CU_NHAN_SU("QĐCNS") }` trong `domain.enumeration`; có `static DocumentType forContract(ContractType)` (HDLD_XDTH/HDLD_KXDTH → HDLD).
  - `String DocumentNumberService.next(DocumentType type, int year)`: trả `DOIT/{year}/{code}/{n}`, dãy chưa có thì tạo với `next_value = 1`.
  - `boolean DocumentNumberService.isTaken(String number)`: tra trên `employment_contract.contract_no` và `assignment_decision.decision_no` (bảng của Task 5; trước Task 5 chỉ tra HĐ).
- API:
  - `GET /api/admin/document-sequences?year=` (ADMIN, HR): danh sách dãy số.
  - `PUT /api/admin/document-sequences/{type}/{year}` `{nextValue}` (chỉ ADMIN). Không cho đặt `nextValue` nhỏ hơn hoặc bằng số lớn nhất đã cấp trong năm → 400 `sequencebelowused`.

- [ ] **Step 1: Viết test.**
  - `firstNumberOfYear`: `next(HDLD, 2026)` = `DOIT/2026/HĐLĐ/1`, lần sau `/2`.
  - `separateSequences`: HĐTV có dãy riêng.
  - `newYearRestarts`: năm 2027 bắt đầu lại từ 1.
  - `adminSetsStart`: đặt 112 → lần cấp kế tiếp là `/112`; đặt nhỏ hơn số đã cấp → 400.
  - `concurrentNext` (Review Focus 3): 10 luồng song song → 10 số khác nhau, liên tiếp 1..10.
  - `hrCannotSetStart` → 403.
- [ ] **Step 2:** Chạy. Expected: FAIL.
- [ ] **Step 3:** Cài đặt. Nếu HR cũng cần thấy dãy số thì trang nằm dưới "Hợp đồng & mẫu" (đặt route trong Task 3); quy tắc URL `/api/admin/document-sequences` cho GET của HR phải đặt trước `/api/admin/**`.
- [ ] **Step 4:** Chạy lại cùng toàn bộ backend. Expected: PASS.
- [ ] **Step 5:** Commit `"Đánh số văn bản theo loại và năm"`.

### Task 3: Kho mẫu văn bản và kiểm tra thẻ

**Files:**
- Create (core): `core/src/main/java/vn/doit/hr/contract/TemplateTags.java`
  - `static TemplateScan scan(InputStream docx)`.
  - `record TemplateScan(Set<String> tags, List<String> brokenTags)`. `brokenTags` là các đoạn `{{`/`}}` không khép được trong cùng một run, hoặc thẻ bị tách qua nhiều run có định dạng khác nhau.
  - Hai hằng: `CONTRACT_TAGS` = khóa của `ContractFields.toMap`; `DECISION_TAGS` = khóa của `AssignmentDecision.toFields`.
- Test (core): `TemplateTagsTest` (dùng 2 mẫu có sẵn và mẫu hỏng tạo bằng POI trong test).
- Create (app):
  - changelog `document_template` (`id`, `kind` varchar(30), `version` int, `file_name`, `storage_path`, `uploaded_by`, `uploaded_at`, `active` boolean; unique `kind` + `version`) + `_aud`;
  - `domain/DocumentTemplate.java`, `repository`, `service/DocumentTemplateService.java`, `web/rest/DocumentTemplateResource.java`.
- Create (frontend): `entities/document-template/` (trang "Mẫu văn bản" dưới menu "Hợp đồng & mẫu": từng loại mẫu, phiên bản đang dùng, tải về, tải lên bản mới, lịch sử phiên bản; ADMIN/HR); `menu-items.ts` thêm `hop-dong` "Hợp đồng & mẫu" `/hop-dong` (ADMIN, HR).
- Test: `DocumentTemplateResourceIT`, Vitest.

**Interfaces:**
- Produces:
  - `enum TemplateKind`, mỗi giá trị có tập thẻ cho phép và mẫu mặc định:
    - `HDLD` (thẻ `CONTRACT_TAGS`, mặc định `templates/DOIT_HDLD_template.docx` từ classpath của `core`);
    - `HDTV`, `HDDT`, `HDDV` (thẻ `CONTRACT_TAGS`, chưa có mặc định);
    - `QD_CU_NHAN_SU` (thẻ `DECISION_TAGS`, mặc định `templates/DOIT_QD_CuNhanSu_template.docx`).
  - `TemplateKind TemplateKind.forContract(ContractType)`.
  - `Optional<byte[]> DocumentTemplateService.activeTemplate(TemplateKind kind)`: phiên bản active mới nhất; nếu chưa có thì lấy mẫu mặc định; loại không có mặc định → rỗng.
  - `DocumentTemplateDTO upload(TemplateKind kind, MultipartFile file)`:
    - chỉ nhận `.docx` (magic bytes ZIP + có `word/document.xml`), ≤ 5 MB;
    - `brokenTags` không rỗng → 400 `templatebrokentags`, `detail` liệt kê thẻ (Review Focus 1);
    - thẻ ngoài tập cho phép → 400 `templateunknowntags`, liệt kê;
    - hợp lệ → `version + 1`, active, bản cũ vẫn lưu.
  - `GET /api/document-templates` (ADMIN, HR): danh sách loại kèm phiên bản active (mặc định ghi "Mẫu gốc").
  - `GET /api/document-templates/{kind}/download?version=`.
  - `POST /api/document-templates/{kind}` (multipart).
  - `POST /api/document-templates/{kind}/versions/{v}/activate`: quay lại phiên bản cũ.

- [ ] **Step 1: Viết test.**
  - Core: hai mẫu có sẵn quét ra đúng tập thẻ (HĐLĐ có `hoTen`, `mucLuongBangChu`; QĐ có `soQuyetDinh`, `donVi`), `brokenTags` rỗng. Mẫu có `{{hoT` + `en}}` ở hai run khác định dạng → `brokenTags = ["hoTen"]`. Thẻ `{{abc}}` → nằm trong `tags`.
  - App: upload mẫu hỏng → 400, `detail` chứa `hoTen`; upload thẻ lạ → 400; upload hợp lệ → version 1, `activeTemplate` trả bản mới; activate version 0 (mẫu gốc) → trả lại mặc định; KE_TOAN/BGD → 403; file `.doc` hoặc file giả → 400.
- [ ] **Step 2:** Chạy. Expected: FAIL.
- [ ] **Step 3:** Cài đặt.
  - Quét trực tiếp `document.xml`, header và footer bằng XWPF.
  - Ghép text các run trong mỗi đoạn để tìm `{{…}}`; thẻ trải qua nhiều run thì so `rPr` của các run: khác nhau → hỏng. poi-tl không gộp được run khác định dạng; ghép như vậy giống cách poi-tl xử lý.
- [ ] **Step 4:** Chạy toàn bộ suite. Expected: PASS.
- [ ] **Step 5:** Commit `"Kho mẫu văn bản, kiểm tra thẻ khi tải mẫu lên"`.

### Task 4: Sinh văn bản HĐ, bản đã ký

**Files:**
- Create: changelog `generated_document` (`id`, `owner_type` varchar(20) [`CONTRACT`|`DECISION`], `owner_id`, `kind` varchar(10) [`DOCX`|`PDF`|`SIGNED`], `storage_path`, `file_name`, `template_kind`, `template_version`, `created_by`, `created_at`) + `_aud`
- Create: `service/ContractDataFactory.java`, `service/ContractDocumentService.java`, `web/rest/ContractDocumentResource.java`
- Modify: `ApplicationProperties` (`company.name` = "CÔNG TY TNHH CÔNG NGHỆ VÀ TRUYỀN THÔNG DO IT", `company.representativeName`, `company.representativeTitle`; lấy từ env, có trong `.env.example`)
- Modify (frontend): `contracts-tab.tsx` (mỗi HĐ: "Tạo văn bản", tải .docx/.pdf, "Tạo lại PDF", "Tải bản đã ký lên", "Đánh dấu đã ký"), `employee-detail.tsx` (nút "Tạo văn bản từ mẫu" trên thẻ đầu → mở tab Hợp đồng và chọn HĐ), `detail-api.ts` (thông báo lỗi tiếng Việt cho các key mới), trang "Hồ sơ của tôi" (nhân viên tải bản đã ký của mình)
- Test: `ContractDataFactoryTest`, `ContractDocumentResourceIT` (dùng `PdfService` giả bằng `@MockitoBean`; có 1 test thật với Gotenberg `@Tag("pdf")`), Vitest

**Interfaces:**
- Consumes: `DocumentNumberService.next`, `DocumentTemplateService.activeTemplate`, `PdfService.toPdf`, `FileStorageService.store/load`, `ContractGenerator`/`DocxTemplateRenderer`, `ContractRules`.
- Produces:
  - `ContractData ContractDataFactory.build(Employee e, EmploymentContract c, List<Allowance> allowances)`:
    - `termMonths` lấy từ HĐ;
    - `baseSalary = c.getSalary()`;
    - phụ cấp lấy các khoản hiệu lực tại `startDate`;
    - `department` lấy từ HĐ, nếu không có thì từ hồ sơ;
    - `raisePolicy` lấy từ HĐ;
    - `companyRepresentative` lấy từ cấu hình.
  - `List<String> ContractDataFactory.missingFields(ContractData d)`: tên tiếng Việt các trường bắt buộc bị trống. Danh sách bắt buộc: Họ tên, Ngày sinh, Số CCCD, Ngày cấp, Nơi cấp, Nơi cư trú, Chức danh, Ngày bắt đầu, Mức lương.
  - `POST /api/employment-contracts/{id}/documents?force=false` (ADMIN, HR):
    - loại HĐ chưa có mẫu → 400 `notemplate` ("Chưa có mẫu cho loại hợp đồng này — tải mẫu ở Hợp đồng & mẫu");
    - thiếu trường và `force=false` → 400 `missingfields`, `detail` liệt kê (Review Focus 2);
    - HĐ chưa có `contractNo` → cấp số và lưu;
    - render `.docx`, lưu; gọi PDF:
      - được → lưu PDF;
      - `PdfUnavailableException` → vẫn trả 201, kèm `pdfError` (Review Focus 4).
  - `POST /api/employment-contracts/{id}/documents/pdf`: tạo lại PDF từ `.docx` mới nhất.
  - `GET /api/employment-contracts/{id}/documents`: danh sách phiên bản.
  - `GET /api/generated-documents/{docId}/download`.
  - `POST /api/employment-contracts/{id}/signed` (multipart PDF, kiểm magic bytes, ≤ 20 MB): lưu `SIGNED`, đặt `signedFileId`.
  - `POST /api/employment-contracts/{id}/sign` (ADMIN, HR): `NHAP` → `DA_KY`. Áp dụng `PendingTerminationGuard` và quy tắc HĐ hiện có.
  - Nhân viên: `GET /api/account/employee/contracts/{id}/signed` chỉ cho HĐ của chính mình.
  - Sửa tay `contractNo` (PUT/PATCH HĐ) trùng số đã có (`DocumentNumberService.isTaken`, trừ chính HĐ đó) → 400 `numbertaken`.

- [ ] **Step 1: Viết test.**
  - `ContractDataFactoryTest`:
    - lương Net 26.215.000 → `mucLuong` = "26.215.000", `mucLuongBangChu` có "hai mươi sáu triệu";
    - phụ cấp hết hạn trước ngày bắt đầu không được in;
    - HĐ 24 tháng từ 25/10/2026 → `denNgay` "24/10/2028";
    - thiếu CCCD và nơi cư trú → `missingFields` = ["Số CCCD", "Nơi cư trú"].
  - `ContractDocumentResourceIT`:
    - sinh lần đầu: số `DOIT/2026/HĐLĐ/1` gán vào HĐ, có DOCX + PDF;
    - sinh lần 2: giữ nguyên số, phiên bản DOCX thứ 2;
    - thiếu trường → 400 liệt kê; `force=true` → 201;
    - HĐTV chưa có mẫu → 400 `notemplate`;
    - `PdfService` ném lỗi → 201 có DOCX, `pdfError`; sau đó "Tạo lại PDF" với service chạy được → có PDF;
    - tải bản đã ký → `signedFileId` có giá trị; `sign` → `DA_KY`;
    - KE_TOAN/BGD tải DOCX → 403;
    - nhân viên tải bản đã ký của mình → 200, của người khác → 403.
    - sửa `contractNo` thành số của HĐ khác → 400 `numbertaken`.
  - Vitest:
    - nút "Tạo văn bản" gọi đúng API;
    - lỗi `missingfields` hiện danh sách và nút "Vẫn tạo";
    - `pdfError` hiện cảnh báo và nút "Tạo lại PDF".
- [ ] **Step 2:** Chạy. Expected: FAIL.
- [ ] **Step 3:** Cài đặt. Đọc dữ liệu nhạy cảm ngay trong service; không đi qua DTO.
- [ ] **Step 4:** Chạy toàn bộ suite. Expected: PASS.
- [ ] **Step 5:** Commit `"Sinh văn bản hợp đồng từ mẫu, PDF, bản đã ký"`.

### Task 5: Quyết định cử nhân sự

**Files:**
- Create: changelog `assignment_decision` (`id`, `decision_no` unique, `employee_id`, `assignment_id`, `sign_date`, `signer_name`, `signer_title`, `task` text, `benefits` text, `created_by`, `created_at`) + `_aud`
- Create: `domain/AssignmentDecisionRecord.java` (tránh trùng tên với `vn.doit.hr.assignment.AssignmentDecision` của core), repository, `service/AssignmentDecisionService.java`, `web/rest/AssignmentDecisionResource.java`
- Modify: `DocumentNumberService.isTaken` (tra cả `decision_no`)
- Modify (frontend): `assignments-tab.tsx` (nút "Cử đi công tác / làm tại khách hàng" mở `decision-dialog.tsx`; danh sách quyết định kèm tải `.docx`/`.pdf`)
- Test: `AssignmentDecisionResourceIT`, Vitest

**Interfaces:**
- Consumes: `WorkAssignmentService.add`, `DocumentNumberService.next(QD_CU_NHAN_SU, year)`, `DocumentTemplateService.activeTemplate(QD_CU_NHAN_SU)`, `PdfService`, `generated_document` (owner_type `DECISION`).
- Produces: `POST /api/employees/{id}/assignment-decisions` (ADMIN, HR).
  - Body:
    - `{type: CONG_TAC|KHACH_HANG, siteId?, unit, address, fromDate, toDate, task, benefits, signDate?, signerName?, signerTitle?}`;
    - `signDate` mặc định hôm nay; người ký mặc định theo cấu hình công ty.
  - Trong **một transaction**:
    - thêm giai đoạn vị trí (lỗi chồng lấn → 400 với `detail` của lõi, không cấp số);
    - cấp số `DOIT/{năm}/QĐCNS/{n}`;
    - lưu quyết định;
    - render `.docx` từ `AssignmentDecision.toFields()` với `contractNo` của HĐ đang hiệu lực.
  - PDF làm sau khi commit (lỗi → `pdfError`, có "Tạo lại PDF" như Task 4).
  - Thêm: `GET /api/employees/{id}/assignment-decisions`, `POST /api/assignment-decisions/{id}/documents/pdf`.

- [ ] **Step 1: Viết test.**
  - Cử onsite "LP Bank" 01/11/2026–31/01/2027:
    - tạo giai đoạn và có giai đoạn quay về văn phòng từ 01/02/2027;
    - số `DOIT/2026/QĐCNS/1`;
    - DOCX chứa "làm việc tại" và "LP Bank" (đọc lại bằng XWPF).
  - Chồng lấn → 400, không có quyết định, dãy số không tăng.
  - Có lịch nghỉ việc chờ áp dụng → 400 `pendingtermination`.
  - KE_TOAN → 403.
  - Vitest: dialog điền sẵn người ký; lỗi chồng lấn hiện nguyên câu của server.
- [ ] **Step 2:** Chạy. Expected: FAIL.
- [ ] **Step 3:** Cài đặt.
- [ ] **Step 4:** Chạy toàn bộ suite. Expected: PASS.
- [ ] **Step 5:** Commit `"Quyết định cử nhân sự gắn lịch sử vị trí"`.

## Kiểm tra cuối kế hoạch

- [ ] Backend verify, `cd app && npm test`, `tsc`, `lint`, build prod, `docker compose build` đều đạt.
- [ ] Thử end-to-end trên `docker compose up` ở máy (gồm dịch vụ `pdf`):
  - tạo nhân viên, HĐLĐ 12 tháng lương Net;
  - "Tạo văn bản" → tải PDF, mở thấy đúng font và dấu;
  - tải bản đã ký lên → đánh dấu đã ký;
  - cử đi onsite có quyết định;
  - thay mẫu HĐLĐ bằng bản sửa câu chữ → sinh lại dùng mẫu mới.

  Gỡ stack sau khi kiểm.
- [ ] README: mục "Hợp đồng & mẫu văn bản" (cách sửa mẫu trong Word: gõ lại cả thẻ trong một lần; danh sách thẻ; đánh số; dịch vụ PDF).
