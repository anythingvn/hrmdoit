# DOIT HRM – Kế hoạch 1: Nền tảng & Hồ sơ nhân viên

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dựng ứng dụng DOIT HRM chạy bằng Docker. HR đăng nhập, nhập và tra cứu toàn bộ hồ sơ nhân viên (thay file `DOIT_Quản lý nhân sự.xlsm`): giai đoạn hợp đồng, lương Net/Gross, phụ cấp, lịch sử vị trí, hồ sơ scan. Phân quyền theo 4 vai trò, có nhật ký thay đổi và backup.

**Architecture:** Repo thành Maven multi-module: `core/` là lõi `vn.doit.hr` hiện có (thuần Java, giữ nguyên test), còn `app/` là ứng dụng JHipster monolith (Spring Boot + React, package `vn.doit.hrm`) phụ thuộc `core`. Lõi không bị kéo Spring vào. `app` gọi lõi qua các service mỏng. Database PostgreSQL với Liquibase, audit bằng Hibernate Envers. Triển khai bằng Docker Compose.

**Tech Stack:** Java 17, Spring Boot (theo JHipster 8.7.3), JHipster 8.7.3 (JWT, React, Maven), PostgreSQL 16, Liquibase, Hibernate Envers, MapStruct, Testcontainers, Jest + React Testing Library, Apache POI (đã có trong core), Docker Compose.

**Spec:** `docs/superpowers/specs/2026-10-19-hrm-doit-dot1-design.md`

### Lộ trình 4 kế hoạch (đợt 1)

| KH | Nội dung | Phụ thuộc |
|---|---|---|
| **1 (tài liệu này)** | Nền tảng, đăng nhập/vai trò, hồ sơ nhân viên, giai đoạn HĐ (chỉ lưu bản ghi), lương Net/Gross, phụ cấp, lịch sử vị trí, hồ sơ scan, audit, Docker + backup | — |
| 2 | Hợp đồng: sinh .docx/.pdf từ mẫu, đánh số theo loại, quản lý mẫu, container LibreOffice, QĐ cử nhân sự gắn lịch sử vị trí | KH1 |
| 3 | Bảng lương: import 4 bước, phiên bản theo kỳ, duyệt, quyền xem (chỉ Kế toán + Quản trị), mức luật theo ngày hiệu lực | KH1 |
| 4 | Nhắc việc + email, lịch làm việc/ngày lễ, màn ghi kết quả thử việc/xét lương, trang Tổng quan, báo cáo Excel | KH1, KH3 (ô chi phí lương) |

KH2 và KH3 độc lập với nhau, làm song song được sau KH1.

## Global Constraints

- Java **17**. Trên máy dev: `export JAVA_HOME=$(brew --prefix openjdk@17)` (máy có sẵn openjdk@17; `java` mặc định không có trên PATH).
- JHipster **8.7.3** chính xác: `npx -y generator-jhipster@8.7.3 …`.
- Lõi `core/` (package `vn.doit.hr`) **không phụ thuộc Spring**; mọi test hiện có của lõi phải còn đạt.
- Không xóa cứng nhân viên. Không có tự đăng ký tài khoản; chỉ Quản trị tạo tài khoản.
- Vai trò (authority): `ROLE_ADMIN` = Quản trị (**toàn quyền**), `ROLE_HR`, `ROLE_KE_TOAN`, `ROLE_BGD`. Quyền kiểm tra ở backend.
- Giao diện tiếng Việt. Ngày `dd/MM/yyyy`, tiền `1.234.567`, múi giờ `Asia/Ho_Chi_Minh`.
- Nhận diện giao diện theo canvas: font **Be Vietnam Pro**; nền `#EFEEE6`; thẻ `#FFFEF8` viền `#DAD8CB`; thanh trái `#1F3240`; chữ `#0F1B27`, chữ phụ `#4E5A66`; nhấn `#E84424`; nút chính / link `#B8341A`; vùng bấm ≥ 44px.
- Loại HĐ: `HDDT` (đào tạo), `HDTV` (thử việc), `HDLD_XDTH` (thời hạn chọn 12/24/36 tháng, cấu hình được), `HDLD_KXDTH` (không có ngày kết thúc), `HDDV` (dịch vụ).
- Người HĐ dịch vụ: mã tự sinh `HDDV1`, `HDDV2`… tăng dần, không dùng lại.
- Lương: mỗi mức có cờ `NET|GROSS`; nếu `NET` thì lưu thêm `grossAmount`. Thống kê dùng Gross.
- Ngày kết thúc HĐ có thời hạn = ngày bắt đầu + thời hạn − 1 ngày (giống `ContractData.endDate()`).
- File dữ liệu thật (`*.xlsx`, `*.xlsm`, file trong volume) không commit.
- Lệnh test dùng trong kế hoạch (chạy ở thư mục gốc repo):
  - `UT <Lớp>` = `mvn -q -pl app -am test -Dskip.npm -Dskip.installnodenpm -Dsurefire.failIfNoSpecifiedTests=false -Dtest=<Lớp>`
  - `IT <Lớp>` = `mvn -q -pl app -am verify -Dskip.npm -Dskip.installnodenpm -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=<Lớp> -Dfailsafe.failIfNoSpecifiedTests=false` (cần Docker đang chạy, vì Testcontainers)
  - `CORE` = `mvn -q -pl core test`
  - `FE <mẫu>` = `cd app && npm test -- --testPathPattern=<mẫu>`

## Review Focus

1. **Tìm kiếm không dấu.** Gõ `quan` hoặc `tran minh` phải ra "Trần Minh Quân", gõ `nv0142` phải ra mã `NV0142`. → test ở Task 6.
2. **Mã nhân viên gõ lệch.** `" nv0142 "` phải được chuẩn hóa thành `NV0142`, và báo trùng nếu mã đã tồn tại. → test ở Task 6.
3. **Cho nghỉ việc khi còn bản ghi đang mở.** Vị trí đang mở bị chốt ở ngày làm việc cuối, HĐ đang hiệu lực chuyển `DA_KET_THUC`, lương hiện tại trống. → test ở Task 9.
4. **Tên file tiếng Việt và tên file độc.** `Bằng đại học.pdf` tải về giữ nguyên tên. `../../etc/passwd.pdf` không thoát được khỏi thư mục lưu. File > 20 MB bị từ chối. → test ở Task 11.
5. **Ô nhập tiền kiểu Việt Nam.** Gõ `15.000.000`, `15000000` hay `15,000,000` đều thành 15000000; gõ chữ thì báo lỗi. → test ở Task 14.

---

### Task 1: Tách repo thành multi-module, lõi vào `core/`

**Files:**
- Move: `pom.xml` → `core/pom.xml`; `src/` → `core/src/`; `tools/` → `core/tools/`
- Create: `pom.xml` (aggregator, `groupId vn.doit`, `artifactId hrm-parent`, `packaging pom`, `<modules>core</modules>`)
- Modify: `core/pom.xml` (thêm `<parent>` trỏ `hrm-parent`, `relativePath ../pom.xml`; giữ `artifactId hr-extension-core`)
- Modify: `README.md` (thêm đoạn đầu: cấu trúc repo `core/`, `app/`, `docs/`; nội dung lõi giữ nguyên, sửa đường dẫn `src/...` → `core/src/...`)
- Modify: `.gitignore` (thêm `*.xlsm`, `app/node_modules/`, `app/target/`, `.env`)

**Interfaces:**
- Produces: artifact `vn.doit.hr:hr-extension-core:0.1.0-SNAPSHOT` để `app` khai báo phụ thuộc.

- [ ] **Step 1:** `git mv pom.xml src tools core/` rồi tạo aggregator pom gốc và thêm `<parent>` cho `core/pom.xml`. Test `PoiIntegrationTest` tìm file lương ở `../DO it - Bang luong…xlsx`: sửa đường dẫn mặc định thành `../../DO it - Bang luong thang 06_2026 them luon.xlsx` (test vẫn tự bỏ qua khi không có file).
- [ ] **Step 2:** Chạy `CORE`. Expected: BUILD SUCCESS, số test bằng trước khi chuyển (xem `target/surefire-reports` cũ: 0 lỗi).
- [ ] **Step 3:** Commit `"Tách repo multi-module: lõi chuyển vào core/"`.

---

### Task 2: Sinh ứng dụng JHipster `app/`

**Files:**
- Create: `app/.yo-rc.json` với đúng nội dung:

```json
{
  "generator-jhipster": {
    "applicationType": "monolith",
    "baseName": "hrm",
    "packageName": "vn.doit.hrm",
    "authenticationType": "jwt",
    "buildTool": "maven",
    "databaseType": "sql",
    "prodDatabaseType": "postgresql",
    "devDatabaseType": "postgresql",
    "cacheProvider": "no",
    "enableHibernateCache": false,
    "clientFramework": "react",
    "clientTheme": "none",
    "enableTranslation": true,
    "nativeLanguage": "vi",
    "languages": ["vi"],
    "testFrameworks": [],
    "serverPort": 8080,
    "jhipsterVersion": "8.7.3"
  }
}
```

- Generated: toàn bộ `app/` (pom, `src/main/java/vn/doit/hrm/**`, `src/main/webapp/**`, `src/main/docker/**`)
- Modify: `pom.xml` gốc (thêm module `app`); `app/pom.xml` (thêm dependency `vn.doit.hr:hr-extension-core:${project.version của core}`)
- Modify: `app/src/main/resources/config/application.yml` (`spring.jackson.time-zone: Asia/Ho_Chi_Minh`, `user.timezone` qua `-Duser.timezone` trong Dockerfile ở Task 15)

**Interfaces:**
- Produces: ứng dụng chạy được, có đăng nhập JWT; package gốc `vn.doit.hrm`; lớp `vn.doit.hrm.security.AuthoritiesConstants`.

- [ ] **Step 1:** `cd app && npx -y generator-jhipster@8.7.3 --skip-git --skip-checks --force`. Generator đọc `.yo-rc.json`, không hỏi lại.
- [ ] **Step 2:** Thêm `app` vào aggregator và thêm dependency `core`. Viết test `app/src/test/java/vn/doit/hrm/CoreWiringTest.java` với nội dung `assertThat(PayrollRules.forPeriod(YearMonth.of(2026, 10))).isPresent()` để chứng minh `app` thấy được lõi.
- [ ] **Step 3:** Chạy `UT CoreWiringTest`. Expected: PASS.
- [ ] **Step 4:** Chạy `UT TechnicalStructureTest` (ArchUnit do JHipster sinh). Expected: PASS.
- [ ] **Step 5:** Commit `"Sinh ứng dụng JHipster app/ (React, JWT, PostgreSQL)"`.

---

### Task 3: Vai trò DOIT, tắt tự đăng ký

**Files:**
- Modify: `app/src/main/java/vn/doit/hrm/security/AuthoritiesConstants.java` (thêm `HR = "ROLE_HR"`, `KE_TOAN = "ROLE_KE_TOAN"`, `BGD = "ROLE_BGD"`)
- Modify: `app/src/main/resources/config/liquibase/data/authority.csv` (thêm 3 dòng)
- Modify: `app/src/main/java/vn/doit/hrm/web/rest/AccountResource.java` (xóa `registerAccount`, `activateAccount`)
- Modify: `app/src/main/java/vn/doit/hrm/config/SecurityConfiguration.java` (bỏ `permitAll` cho `/api/register`, `/api/activate`)
- Modify: các trang React `register`, `activate` (xóa route và link "Đăng ký")
- Create: `app/src/main/resources/config/liquibase/changelog/20261019000100_seed_admin.xml` (chỉ giữ user `admin` với `ROLE_ADMIN` + `ROLE_USER`; xóa user mẫu `user`; mật khẩu admin đổi bắt buộc khi triển khai, ghi trong README)
- Test: `app/src/test/java/vn/doit/hrm/web/rest/AccountResourceIT.java` (sửa), `app/src/test/java/vn/doit/hrm/security/DoitRolesIT.java`

**Interfaces:**
- Produces: hằng số `AuthoritiesConstants.ADMIN|HR|KE_TOAN|BGD`; các task sau dùng `@PreAuthorize("hasAnyAuthority(...)")` với các hằng này.

- [ ] **Step 1:** Viết test `DoitRolesIT.authoritiesSeeded()`: `authorityRepository.findAll()` chứa `ROLE_ADMIN, ROLE_HR, ROLE_KE_TOAN, ROLE_BGD`. Viết `AccountResourceIT.registerIsDisabled()`: `POST /api/register` với body hợp lệ → status **401** (không còn permitAll, chưa đăng nhập). Xóa các test đăng ký/kích hoạt cũ.
- [ ] **Step 2:** Chạy `IT DoitRolesIT` và `IT AccountResourceIT`. Expected: FAIL.
- [ ] **Step 3:** Thực hiện các thay đổi trong Files.
- [ ] **Step 4:** Chạy lại hai IT. Expected: PASS.
- [ ] **Step 5:** Commit `"Thêm vai trò HR/Kế toán/BGĐ, tắt tự đăng ký"`.

---

### Task 4: Quy đổi Net → Gross trong lõi

**Files:**
- Create: `core/src/main/java/vn/doit/hr/payroll/NetGrossConverter.java`
- Test: `core/src/test/java/vn/doit/hr/payroll/NetGrossConverterTest.java`

**Interfaces:**
- Produces:
  - `public static Optional<BigDecimal> NetGrossConverter.suggestGross(BigDecimal net, int dependants, YearMonth period)`: rỗng nếu `PayrollRules.forPeriod(period)` rỗng.
  - `public static BigDecimal NetGrossConverter.netFromGross(BigDecimal gross, int dependants, PayrollRules rules)`

- [ ] **Step 1: Viết test**

```java
@Test void netFromGross_belowCap() {   // 10/2026: trần BHXH 50,6tr
    var r = PayrollRules.forPeriod(YearMonth.of(2026, 10)).orElseThrow();
    assertThat(NetGrossConverter.netFromGross(bd(30_000_000), 0, r)).isEqualByComparingTo(bd(26_215_000));
}
@Test void suggestGross_inverse()        { assertThat(suggest(26_215_000, 0)).contains(bd(30_000_000)); }
@Test void suggestGross_withDependant()  { assertThat(suggest(17_900_000, 1)).contains(bd(20_000_000)); }
@Test void suggestGross_aboveBhxhCap()   { assertThat(suggest(50_274_400, 0)).contains(bd(60_000_000)); }
@Test void suggestGross_unknownYear()    { assertThat(NetGrossConverter.suggestGross(bd(10_000_000), 0, YearMonth.of(2030, 1))).isEmpty(); }
// suggest(n, d) = NetGrossConverter.suggestGross(bd(n), d, YearMonth.of(2026, 10)) và so sánh bằng compareTo
```

- [ ] **Step 2:** Chạy `CORE`. Expected: FAIL (lớp chưa có).
- [ ] **Step 3:** Cài đặt. `netFromGross`: BH người lao động = `min(gross, socialInsuranceCap) × (8% + 1,5%) + min(gross, unemploymentInsuranceCap) × 1%`; thu nhập tính thuế = `max(0, gross − BH − personalDeduction − dependants × dependantDeduction)`; net = `gross − BH − rules.monthlyPit(tính thuế)`. `suggestGross` tìm nhị phân trên số nguyên đồng trong `[net, net × 2]`, trả về **Gross nhỏ nhất** có `netFromGross(g) ≥ net` (net tăng đơn điệu theo gross).
- [ ] **Step 4:** Chạy `CORE`. Expected: PASS toàn bộ.
- [ ] **Step 5:** Commit `"Lõi: quy đổi lương Net sang Gross"`.

---

### Task 5: Mô hình dữ liệu (JDL) và danh mục

**Files:**
- Create: `app/hrm.jdl` với nội dung:

```jdl
enum Gender { NAM, NU }
enum PersonType { NHAN_VIEN, DICH_VU }
enum EmploymentStatus { DANG_LAM_VIEC, DA_NGHI }
enum TerminationType { TU_NGHI, HET_HAN_HD, KHONG_DAT_THU_VIEC, CONG_TY_CHAM_DUT, KHAC }
enum ContractType { HDDT, HDTV, HDLD_XDTH, HDLD_KXDTH, HDDV }
enum ContractStatus { NHAP, DA_KY, DA_KET_THUC, DA_THANH_LY }
enum SalaryBasis { NET, GROSS }
enum WorkLocationType { VAN_PHONG, KHACH_HANG, TU_XA, CONG_TAC }

entity Department { name String required unique maxlength(100), active Boolean required }
entity WorkSite { name String required unique maxlength(200), type WorkLocationType required, address String maxlength(300), active Boolean required }

entity Employee {
  code String required unique maxlength(20), personType PersonType required,
  fullName String required maxlength(100), searchText String maxlength(400),
  gender Gender, birthDate LocalDate, email String maxlength(100), phone String maxlength(20),
  residence String maxlength(300), education String maxlength(100),
  idNumber String maxlength(20), idIssueDate LocalDate, idIssuePlace String maxlength(200),
  taxCode String maxlength(20), bankAccount String maxlength(30), bankName String maxlength(100),
  socialInsuranceNo String maxlength(20), socialInsuranceStart LocalDate, socialInsuranceSalary Long,
  dependants Integer required min(0), position String maxlength(100),
  status EmploymentStatus required, joinDate LocalDate, lastWorkingDay LocalDate,
  terminationType TerminationType, terminationReason String maxlength(500), note TextBlob
}
entity EmploymentContract {
  contractNo String maxlength(50), type ContractType required, termMonths Integer,
  startDate LocalDate required, endDate LocalDate, position String maxlength(100),
  salary Long, salaryBasis SalaryBasis, grossAmount Long, raisePolicy String maxlength(300),
  status ContractStatus required, signedFileId Long
}
entity SalaryChange {
  effectiveDate LocalDate required, amount Long required, basis SalaryBasis required, grossAmount Long,
  reviewCycleMonths Integer, nextReviewOverride LocalDate, note String maxlength(500)
}
entity Allowance { name String required maxlength(100), amountPerMonth Long required, fromDate LocalDate required, toDate LocalDate }
entity WorkAssignment {
  type WorkLocationType required, unit String maxlength(200), address String maxlength(300),
  fromDate LocalDate required, toDate LocalDate, decisionNo String maxlength(50), note String maxlength(500)
}
entity EmployeeDocument {
  fileName String required maxlength(255), contentType String required maxlength(100),
  sizeBytes Long required, storagePath String required maxlength(500), uploadedBy String maxlength(50), uploadedAt Instant required
}

relationship ManyToOne {
  Employee{department(name)} to Department, Employee{manager(fullName)} to Employee,
  Employee{user(login)} to User with builtInEntity,
  EmploymentContract{employee(code) required} to Employee, EmploymentContract{department(name)} to Department,
  SalaryChange{employee(code) required} to Employee, Allowance{employee(code) required} to Employee,
  WorkAssignment{employee(code) required} to Employee, WorkAssignment{site(name)} to WorkSite,
  EmployeeDocument{employee(code) required} to Employee
}
dto * with mapstruct
service * with serviceClass
paginate Employee with pagination
filter Employee
skipClient Employee, EmploymentContract, SalaryChange, Allowance, WorkAssignment, EmployeeDocument
```

- Generated: entity, repository, service, DTO, mapper, resource, Liquibase changelog cho 8 entity; trang React CRUD cho `Department`, `WorkSite`.
- Create: `app/src/main/resources/config/liquibase/changelog/20261019000200_seed_catalog.xml`: WorkSite `Văn phòng` (VAN_PHONG), `Việt Á`, `LP Bank`, `SeAbank` (KHACH_HANG), tất cả `active=true`.
- Modify: `DepartmentResource`, `WorkSiteResource`: GET cho mọi vai trò đã đăng nhập; ghi chỉ `ADMIN`, `HR`. Xóa endpoint DELETE (danh mục chỉ ngừng dùng bằng `active=false`).

**Interfaces:**
- Produces: các lớp sinh ra `vn.doit.hrm.domain.{Employee, EmploymentContract, SalaryChange, Allowance, WorkAssignment, EmployeeDocument, Department, WorkSite}`, enum trong `vn.doit.hrm.domain.enumeration`, DTO `*DTO`, service `*Service`, repository `*Repository`, `EmployeeQueryService` + `EmployeeCriteria`.

- [ ] **Step 1:** `cd app && npx -y generator-jhipster@8.7.3 jdl hrm.jdl --skip-git --force`.
- [ ] **Step 2:** Thêm changelog seed và sửa quyền cho hai resource danh mục. Sửa `WorkSiteResourceIT` sinh sẵn: thêm `catalogSeeded()` (có đúng 4 site sau khởi động, test chạy trước các test tạo mới hoặc đếm theo tên), `hrCanCreate()` (`@WithMockUser(authorities="ROLE_HR")` → 201), `accountantCannotCreate()` (`ROLE_KE_TOAN` → 403), `deleteNotAllowed()` (DELETE → 405 hoặc 404). Xóa các test DELETE sinh sẵn.
- [ ] **Step 3:** Chạy `IT WorkSiteResourceIT` và `IT DepartmentResourceIT`. Expected: PASS.
- [ ] **Step 4:** Commit `"Mô hình dữ liệu hồ sơ nhân viên và danh mục"`.

---

### Task 6: Quy tắc tạo/sửa nhân viên: mã, HDDV, tìm kiếm không dấu, không xóa

**Files:**
- Create: `app/src/main/java/vn/doit/hrm/service/EmployeeCodes.java`
- Create: `app/src/main/java/vn/doit/hrm/service/VietnameseText.java`
- Create: `app/src/main/resources/config/liquibase/changelog/20261019000300_hddv_sequence.xml` (sequence `hddv_code_seq` start 1, increment 1)
- Modify: `app/src/main/java/vn/doit/hrm/service/EmployeeService.java`, `EmployeeQueryService.java`, `web/rest/EmployeeResource.java`
- Test: `app/src/test/java/vn/doit/hrm/service/VietnameseTextTest.java`, `app/src/test/java/vn/doit/hrm/web/rest/EmployeeResourceIT.java` (sửa bản sinh sẵn)

**Interfaces:**
- Produces:
  - `static String VietnameseText.fold(String s)`: bỏ dấu (gồm `đ→d`), chữ thường, gộp khoảng trắng; `null → ""`.
  - `static String EmployeeCodes.normalize(String raw)`: trim + chữ hoa.
  - `EmployeeCriteria` có thêm `StringFilter q` (tìm theo `searchText` chứa `fold(q)`); `searchText = fold(code + " " + fullName + " " + email)`, tính lại mỗi lần lưu.
  - Lỗi trùng mã: `BadRequestAlertException` với errorKey `"codeexists"`.
  - `EmployeeCriteria` thêm `LongFilter currentSiteId` và `StringFilter currentContractType`, cài bằng subquery trong `EmployeeQueryService` (vị trí có `fromDate ≤ hôm nay` và `toDate` null hoặc ≥ hôm nay; HĐ trạng thái `DA_KY` đang hiệu lực).

- [ ] **Step 1: Viết test**
  - `VietnameseTextTest`: `fold("Trần Minh Quân") = "tran minh quan"`; `fold("ĐỖ  Văn Bình") = "do van binh"`; `fold(null) = ""`.
  - `EmployeeResourceIT` (`@WithMockUser(authorities="ROLE_HR")`):
    - `createServicePerson_autoCodes`: tạo 2 người `personType=DICH_VU`, không gửi `code` → mã lần lượt `HDDV1`, `HDDV2`.
    - `createServicePerson_ignoresClientCode`: gửi `code="X1"` cho `DICH_VU` → vẫn nhận `HDDV…`.
    - `createStaff_requiresCode`: `NHAN_VIEN` không có mã → 400.
    - `createStaff_normalizesCode`: gửi `" nv0142 "` → lưu `NV0142`; gửi tiếp `"NV0142"` → 400, errorKey `codeexists`.
    - `searchIgnoresAccents`: có "Trần Minh Quân" mã `NV0142`; `GET /api/employees?q.contains=tran minh`, `?q.contains=quan`, `?q.contains=nv0142` → đều trả về người đó.
    - `deleteNotAllowed`: `DELETE /api/employees/{id}` → 405 hoặc 404, bản ghi còn nguyên.
    - `filterBySiteAndContract`: A đang ở site "LP Bank" với HĐ `HDLD_XDTH` đang hiệu lực, B ở "Văn phòng" với `HDTV` → `?currentSiteId.equals=<LP Bank>` chỉ ra A; `?currentContractType.equals=HDTV` chỉ ra B. (Bản ghi vị trí và HĐ tạo trực tiếp bằng repository trong test.)
    - `newEmployeeDefaults`: không gửi `status`, `dependants` → `DANG_LAM_VIEC`, `0`.
- [ ] **Step 2:** Chạy `UT VietnameseTextTest`, `IT EmployeeResourceIT`. Expected: FAIL.
- [ ] **Step 3:** Cài đặt. `fold` dùng `java.text.Normalizer` NFD + bỏ `\p{M}` + thay `đ/Đ`. Mã HDDV lấy bằng `SELECT nextval('hddv_code_seq')` qua native query trong `EmployeeRepository`. Xóa endpoint DELETE và `EmployeeService.delete`.
- [ ] **Step 4:** Chạy lại. Expected: PASS.
- [ ] **Step 5:** Commit `"Nhân viên: chuẩn hóa mã, tự sinh mã HDDV, tìm không dấu"`.

---

### Task 7: Phân quyền theo vai trò và dữ liệu nhạy cảm

**Files:**
- Create: `app/src/main/java/vn/doit/hrm/service/dto/EmployeeSensitiveDTO.java`
- Create: `app/src/main/java/vn/doit/hrm/domain/SensitiveAccessLog.java` + repository + changelog `20261019000400_sensitive_access_log.xml` (cột `id, employee_id, viewer_login, viewed_at`)
- Modify: `EmployeeMapper`/`EmployeeService` (DTO thường **không** chứa `idNumber, idIssueDate, idIssuePlace, taxCode, bankAccount, bankName, socialInsuranceNo`)
- Modify: `EmployeeResource` và resource của 5 entity con (`EmploymentContract, SalaryChange, Allowance, WorkAssignment, EmployeeDocument`)
- Test: `app/src/test/java/vn/doit/hrm/web/rest/EmployeePermissionIT.java`

**Interfaces:**
- Produces:
  - `GET /api/employees/{id}/sensitive` → `EmployeeSensitiveDTO {idNumber, idIssueDate, idIssuePlace, taxCode, bankAccount, bankName, socialInsuranceNo}`. Mỗi lần gọi thành công ghi một dòng `SensitiveAccessLog`.
  - Ma trận quyền (mọi task sau theo bảng này):

| Endpoint | ADMIN | HR | KE_TOAN | BGD |
|---|---|---|---|---|
| GET hồ sơ, danh sách, HĐ, điều chỉnh lương, phụ cấp, vị trí, tài liệu | ✓ | ✓ | ✓ | ✓ |
| POST/PUT/PATCH các mục trên | ✓ | ✓ | ✗ | ✗ |
| GET `/sensitive` | ✓ | ✓ | ✓ (chỉ `bankAccount, bankName, socialInsuranceNo`, các trường khác `null`) | ✗ |

  Quyết định của người lập kế hoạch: BGĐ không xem CCCD/tài khoản, vì không cần cho công việc. Kế toán xem toàn bộ trường hồ sơ không nhạy cảm, vì DTO thường đã bỏ các trường nhạy cảm.

- [ ] **Step 1:** Viết `EmployeePermissionIT` theo ma trận. Ví dụ tên test: `bgdCanRead`, `bgdCannotUpdate` (PUT → 403), `accountantCannotCreateContract` (POST `/api/employment-contracts` → 403), `listOmitsSensitive` (JSON danh sách không có khóa `idNumber`), `sensitiveForHrIsFullAndLogged` (đủ trường + `sensitiveAccessLogRepository.count()` tăng 1), `sensitiveForAccountantIsPartial` (`idNumber` null, `bankAccount` có), `sensitiveForbiddenForBgd` (403).
- [ ] **Step 2:** Chạy `IT EmployeePermissionIT`. Expected: FAIL.
- [ ] **Step 3:** Cài đặt bằng `@PreAuthorize` trên từng phương thức resource.
- [ ] **Step 4:** Chạy lại cùng `IT EmployeeResourceIT`. Expected: PASS.
- [ ] **Step 5:** Commit `"Phân quyền hồ sơ theo vai trò, tách dữ liệu nhạy cảm có ghi nhật ký"`.

---

### Task 8: Giai đoạn hợp đồng, lương hiện tại, Net/Gross, cho nghỉ việc

**Files:**
- Create: `app/src/main/java/vn/doit/hrm/service/CurrentSalary.java` (record)
- Create: `app/src/main/java/vn/doit/hrm/service/CurrentSalaryCalculator.java`
- Create: `app/src/main/java/vn/doit/hrm/service/ContractRules.java`
- Create: `app/src/main/java/vn/doit/hrm/config/ClockConfiguration.java`
- Create: `app/src/main/java/vn/doit/hrm/web/rest/SalaryToolsResource.java`
- Modify: `EmploymentContractService`, `SalaryChangeService`, `EmployeeService` (DTO chi tiết có `currentSalary`, `currentContractType`), `EmployeeResource`
- Modify: `application.yml` (`application.contract.term-options-months: [12, 24, 36]`) + `ApplicationProperties`
- Test: `app/src/test/java/vn/doit/hrm/service/CurrentSalaryCalculatorTest.java`, `ContractRulesTest.java`, `app/src/test/java/vn/doit/hrm/web/rest/ContractLifecycleIT.java`

**Interfaces:**
- Produces:
  - `record CurrentSalary(long amount, SalaryBasis basis, Long grossAmount, LocalDate effectiveDate, String source)`, với `source ∈ {"CONTRACT","SALARY_CHANGE"}`.
  - `static Optional<CurrentSalary> CurrentSalaryCalculator.of(EmploymentStatus status, List<EmploymentContract> contracts, List<SalaryChange> changes, LocalDate asOf)`: lấy mức có ngày hiệu lực ≤ `asOf` mới nhất (HĐ: `startDate`, chỉ HĐ có `salary` và trạng thái khác `NHAP`); trùng ngày thì `SALARY_CHANGE` thắng; `DA_NGHI` thì rỗng.
  - `static LocalDate ContractRules.defaultEndDate(ContractType t, Integer termMonths, LocalDate start)`: `HDLD_KXDTH → null`; có `termMonths` → `start + termMonths − 1 ngày`; còn lại `null`.
  - `static void ContractRules.validate(EmploymentContractDTO c, List<Integer> termOptions)`: ném `BadRequestAlertException` khi `HDLD_XDTH` có `termMonths` ngoài danh sách (key `badterm`), `HDLD_KXDTH` có `endDate` (key `indefinitehasend`), hoặc `endDate < startDate` (key `endbeforestart`).
  - `static boolean ContractRules.nextMustBeIndefinite(List<EmploymentContract> signed)`: `true` khi 2 HĐ gần nhất có trạng thái `DA_KY`/`DA_KET_THUC` đều là `HDLD_XDTH`.
  - Khi lưu `EmploymentContract`/`SalaryChange` có basis `NET` mà `grossAmount == null`: điền `NetGrossConverter.suggestGross(net, employee.dependants, YearMonth.from(ngày hiệu lực))` nếu có.
  - `GET /api/salary-tools/gross-suggestion?net=&dependants=&month=YYYY-MM` → `{ "gross": 30000000 }` hoặc `{ "gross": null }`.
  - `GET /api/salary-tools/contract-terms` → `[12,24,36]`.
  - `GET /api/employees/{id}/contract-advice` → `{ "nextMustBeIndefinite": true|false }`.
  - Bean `Clock` (`ClockConfiguration`: `Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"))`). Mọi chỗ cần "hôm nay" trong `app` đều lấy từ bean này; test ghi đè bằng `Clock.fixed`.

- [ ] **Step 1: Viết test**
  - `CurrentSalaryCalculatorTest`: HĐTV 10tr từ 01/01/2026 + HĐLĐ 12tr từ 01/03/2026 + điều chỉnh 14tr từ 01/09/2026 → `asOf 15/08/2026` = 12tr `CONTRACT`; `asOf 01/09/2026` = 14tr `SALARY_CHANGE`; điều chỉnh tương lai 01/12/2026 bị bỏ qua khi `asOf` 19/10/2026; trùng ngày 01/03 giữa HĐ và điều chỉnh → `SALARY_CHANGE`; `DA_NGHI` → rỗng; HĐ `NHAP` bị bỏ qua.
  - `ContractRulesTest`: `defaultEndDate(HDLD_XDTH, 12, 2026-01-01) = 2026-12-31`; `(HDLD_XDTH, 24, 2026-10-25) = 2028-10-24`; `(HDLD_KXDTH, null, …) = null`; validate `termMonths=18` → `badterm`; `nextMustBeIndefinite` với [XDTH 12, XDTH 24] = true, [HDTV, XDTH] = false.
  - `ContractLifecycleIT` (ROLE_HR):
    - `netContractGetsGrossSuggestion`: tạo HĐ `HDLD_XDTH` 12 tháng từ 01/10/2026, salary 26215000 `NET`, nhân viên 0 người phụ thuộc → `grossAmount = 30000000`, `endDate = 2027-09-30`.
    - `userGrossIsKept`: gửi `grossAmount=31000000` → giữ nguyên 31000000.
    - `employeeDetailShowsCurrentSalary`: `Clock.fixed` ngày 19/10/2026, HĐ ở trạng thái `DA_KY` → GET `/api/employees/{id}` có `currentSalary.amount = 26215000`, `basis = NET`, `grossAmount = 30000000`.
- [ ] **Step 2:** Chạy `UT CurrentSalaryCalculatorTest`, `UT ContractRulesTest`, `IT ContractLifecycleIT`. Expected: FAIL.
- [ ] **Step 3:** Cài đặt theo Interfaces.
- [ ] **Step 4:** Chạy lại. Expected: PASS.
- [ ] **Step 5:** Commit `"Hợp đồng/lương: lương hiện tại, gợi ý Gross, quy tắc thời hạn"`.

---

### Task 9: Lịch sử vị trí làm việc và cho nghỉ việc

**Files:**
- Create: `app/src/main/java/vn/doit/hrm/service/AssignmentMapping.java` (chuyển `WorkAssignment` ↔ `vn.doit.hr.assignment.Assignment`)
- Modify: `app/src/main/java/vn/doit/hrm/service/WorkAssignmentService.java`
- Create: `app/src/main/java/vn/doit/hrm/web/rest/EmployeeAssignmentResource.java`
- Modify: `WorkAssignmentResource` (bỏ POST/PUT/PATCH/DELETE chung; ghi chỉ qua resource mới)
- Create: `app/src/main/java/vn/doit/hrm/service/TerminationService.java`
- Modify: `EmployeeResource` (thêm `/terminate`; chặn PUT đổi sang `DA_NGHI`)
- Test: `app/src/test/java/vn/doit/hrm/web/rest/EmployeeAssignmentResourceIT.java`

**Interfaces:**
- Consumes: `AssignmentHistory.of(code, entries)`, `.add(Assignment)`, `.changeEnd(Assignment, LocalDate)`, `.locationOn(LocalDate)`, `.timeline(from, to)`, `.gaps(from, to)`; `Result.changes()` là `List<AssignmentChange>` (kind `CREATED|CLOSED|RESUMED|END_CHANGED|ADJUSTED`); `AssignmentConflictException`.
- Produces:
  - `WorkAssignmentService.add(Long employeeId, WorkAssignmentDTO a)`: nạp lịch sử, gọi lõi, lưu mọi thay đổi trong `Result.changes()` trong một transaction (CREATED → insert; CLOSED/END_CHANGED/ADJUSTED/RESUMED → update hoặc insert theo `after`).
  - `WorkAssignmentService.changeEnd(Long employeeId, Long assignmentId, LocalDate newEnd)`.
  - `POST /api/employees/{id}/terminate` body `{lastWorkingDay, terminationType, terminationReason}` (ADMIN, HR), chạy trong một transaction: đặt `DA_NGHI` và các trường nghỉ việc; vị trí đang mở (`toDate` null hoặc `> lastWorkingDay`) chốt về `lastWorkingDay` qua `changeEnd`; HĐ `DA_KY` có `endDate` null hoặc `> lastWorkingDay` chuyển `DA_KET_THUC`, `endDate = lastWorkingDay`.
  - PUT hồ sơ đổi `status` thành `DA_NGHI` → 400, key `useterminate`.
  - `POST /api/employees/{id}/assignments`, `PATCH /api/employees/{id}/assignments/{aid}/end` body `{toDate}`, `GET /api/employees/{id}/assignments/timeline` → `[{from, to, type, unit, label}]`, `GET /api/employees/{id}/assignments/current` → 1 phần tử hoặc 204, `GET /api/employees/{id}/assignments/gaps?from=&to=`.
  - `AssignmentConflictException` → 400, `detail` = message tiếng Việt của lõi.
  - Khi chọn `site`, `type`/`unit`/`address` lấy từ `WorkSite`.

- [ ] **Step 1: Viết test**
  - `onsiteWithEndCreatesReturn`: có VAN_PHONG từ 01/01/2026 mở; thêm KHACH_HANG "LP Bank" 01/11/2026–31/01/2027 → timeline gồm VAN_PHONG đến 31/10/2026, LP Bank, VAN_PHONG từ 01/02/2027.
  - `overlapRejected`: thêm CONG_TAC trùng một đợt CONG_TAC đã có → 400, `detail` không rỗng.
  - `businessTripWithoutEndRejected` → 400.
  - `currentReturnsTripDuringTrip`: CONG_TAC 26–30/10/2026, `Clock.fixed` ngày 27/10/2026 → `current` trả về CONG_TAC.
  - `accountantCannotAdd` → 403.
  - `terminateClosesOpenRecords` (Review Focus 3): có vị trí VAN_PHONG mở từ 01/01/2026, HĐ DA_KY đến 31/12/2026; terminate với lastWorkingDay 31/10/2026 → vị trí `toDate = 2026-10-31`, HĐ `DA_KET_THUC` + `endDate = 2026-10-31`, `status = DA_NGHI`, `currentSalary` null.
  - `putCannotSetDaNghi`: PUT với `status=DA_NGHI` → 400 `useterminate`.
- [ ] **Step 2:** Chạy `IT EmployeeAssignmentResourceIT`. Expected: FAIL.
- [ ] **Step 3:** Cài đặt.
- [ ] **Step 4:** Chạy lại cùng `IT ContractLifecycleIT`. Expected: PASS.
- [ ] **Step 5:** Commit `"Lịch sử vị trí làm việc và cho nghỉ việc"`.

---

### Task 10: Nhật ký thay đổi (Envers)

**Files:**
- Modify: `app/pom.xml` (thêm `org.hibernate.orm:hibernate-envers`, version theo BOM Spring Boot)
- Create: `app/src/main/java/vn/doit/hrm/domain/AuditRevision.java` (`@RevisionEntity`, thêm cột `login`), `AuditRevisionListener.java` (lấy login từ `SecurityUtils.getCurrentUserLogin()`, không có thì `"system"`)
- Modify: 8 entity của Task 5 (thêm `@Audited`; quan hệ tới `User` dùng `@Audited(targetAuditMode = NOT_AUDITED)`)
- Create: changelog `20261019000500_envers.xml` (bảng `audit_revision` và `<bảng>_aud` cho 8 bảng, cột `rev`, `revtype` + cột dữ liệu)
- Create: `app/src/main/java/vn/doit/hrm/web/rest/EmployeeHistoryResource.java`
- Test: `app/src/test/java/vn/doit/hrm/web/rest/EmployeeHistoryResourceIT.java`

**Interfaces:**
- Produces: `GET /api/employees/{id}/history` (ADMIN, HR) → `[{at: Instant, login, entity: "Employee"|"EmploymentContract"|…, action: "TẠO"|"SỬA"|"XÓA", changes: [{field, before, after}]}]`, mới nhất trước, gồm thay đổi của hồ sơ và các bản ghi con. Trường nhạy cảm hiện `"***"` trong `before`/`after`.

- [ ] **Step 1:** Viết test: tạo nhân viên → sửa `position` "Tester" → "BA" → history có 2 mục, mục đầu `action = "SỬA"`, `changes` có `{field:"position", before:"Tester", after:"BA"}`, `login` = user test; sửa `bankAccount` → giá trị hiện `"***"`; `ROLE_BGD` gọi → 403.
- [ ] **Step 2:** Chạy `IT EmployeeHistoryResourceIT`. Expected: FAIL.
- [ ] **Step 3:** Cài đặt. Đặt `spring.jpa.properties.org.hibernate.envers.audit_table_suffix: _aud`; cho Envers không tự sinh DDL (Liquibase quản lý).
- [ ] **Step 4:** Chạy lại; chạy thêm `IT EmployeeResourceIT`. Expected: PASS.
- [ ] **Step 5:** Commit `"Nhật ký thay đổi hồ sơ bằng Envers"`.

---

### Task 11: Hồ sơ scan (tải lên / tải về)

**Files:**
- Create: `app/src/main/java/vn/doit/hrm/service/FileStorageService.java`
- Create: `app/src/main/java/vn/doit/hrm/web/rest/EmployeeDocumentUploadResource.java`
- Modify: `EmployeeDocumentResource` (bỏ POST/PUT/PATCH JSON chung)
- Modify: `application.yml` (`application.storage.root: ${STORAGE_ROOT:./data/files}`, `spring.servlet.multipart.max-file-size: 20MB`, `max-request-size: 21MB`)
- Test: `app/src/test/java/vn/doit/hrm/service/FileStorageServiceTest.java`, `app/src/test/java/vn/doit/hrm/web/rest/EmployeeDocumentUploadIT.java`

**Interfaces:**
- Produces:
  - `FileStorageService.store(String folder, String originalName, InputStream in) → String storagePath` (đường dẫn tương đối dạng `employees/{id}/{uuid}.{ext}`; tên gốc chỉ lưu trong DB); `Resource load(String storagePath)` (ném lỗi nếu đường dẫn sau khi chuẩn hóa nằm ngoài `root`). KH2 dùng lại lớp này cho file hợp đồng.
  - `POST /api/employees/{id}/documents` (multipart `file`, ADMIN/HR): chỉ nhận `application/pdf`, `image/jpeg`, `image/png` (kiểm tra cả đuôi file và magic bytes), trả `EmployeeDocumentDTO`.
  - `GET /api/employees/{id}/documents` (mọi vai trò); `GET /api/employee-documents/{docId}/download` với `Content-Disposition: attachment; filename*=UTF-8''<tên gốc mã hóa URL>`.
  - `DELETE /api/employee-documents/{docId}` (ADMIN/HR): xóa bản ghi và file.

- [ ] **Step 1: Viết test**
  - `FileStorageServiceTest` (dùng `@TempDir`): `store` với tên `../../etc/passwd.pdf` → file nằm trong root, đường dẫn không chứa `..`; `load("../x")` → ném `IllegalArgumentException`.
  - `EmployeeDocumentUploadIT`: tải `Bằng đại học.pdf` (nội dung bắt đầu `%PDF-`) → 201; download có header `filename*=UTF-8''B%E1%BA%B1ng%20%C4%91%E1%BA%A1i%20h%E1%BB%8Dc.pdf` và đúng byte; file `.exe` đổi tên thành `.pdf` (không có `%PDF-`) → 400; file 21 MB → 413 hoặc 400; `ROLE_KE_TOAN` upload → 403.
- [ ] **Step 2:** Chạy `UT FileStorageServiceTest`, `IT EmployeeDocumentUploadIT`. Expected: FAIL.
- [ ] **Step 3:** Cài đặt.
- [ ] **Step 4:** Chạy lại. Expected: PASS.
- [ ] **Step 5:** Commit `"Hồ sơ scan: tải lên, tải về, kiểm tra loại file"`.

---

### Task 12: Xuất danh sách nhân viên ra Excel

**Files:**
- Create: `app/src/main/java/vn/doit/hrm/service/EmployeeExcelExporter.java`
- Modify: `EmployeeResource` (thêm `GET /api/employees/export.xlsx`, nhận cùng tham số lọc với danh sách)
- Test: `app/src/test/java/vn/doit/hrm/web/rest/EmployeeExportIT.java`

**Interfaces:**
- Produces: file .xlsx, sheet `Nhân viên`, dòng 1 là tiêu đề: `Mã NV | Họ và tên | Giới tính | Ngày sinh | Email | SĐT | Phòng ban | Chức danh | Nơi làm việc hiện tại | Loại HĐ hiện tại | Tình trạng | Ngày vào công ty | Lương hiện tại | Net/Gross | Lương Gross`. Ba cột lương chỉ có khi người gọi có vai trò ADMIN, HR hoặc BGD. Không xuất trường nhạy cảm.

- [ ] **Step 1:** Viết test: 2 nhân viên, lọc `status=DANG_LAM_VIEC` → file có đúng 1 dòng dữ liệu; ô ngày có kiểu ngày, định dạng `dd/mm/yyyy`; tiêu đề đúng như trên; `ROLE_KE_TOAN` → không có cột `Lương hiện tại`. (Quyết định của người lập kế hoạch: kế toán xem lương qua bảng lương ở KH3, không qua danh sách nhân viên.)
- [ ] **Step 2:** Chạy `IT EmployeeExportIT`. Expected: FAIL.
- [ ] **Step 3:** Cài đặt bằng POI `XSSFWorkbook`, dùng `EmployeeQueryService` với cùng criteria, không phân trang.
- [ ] **Step 4:** Chạy lại. Expected: PASS.
- [ ] **Step 5:** Commit `"Xuất danh sách nhân viên ra Excel"`.

---

### Task 13: Khung giao diện theo canvas

**Files:**
- Create: `app/src/main/webapp/app/shared/layout/sidebar/sidebar.tsx`, `sidebar.scss`
- Create: `app/src/main/webapp/app/shared/layout/sidebar/menu-items.ts`
- Create: `app/src/main/webapp/app/config/theme.scss` (biến màu và font theo Global Constraints; import Be Vietnam Pro từ Google Fonts trong `index.html`)
- Modify: `app/src/main/webapp/app/app.tsx` (sidebar trái thay thanh header mặc định; khối người dùng dưới cùng hiện tên + vai trò, có nút Đăng xuất)
- Modify: `app/src/main/webapp/app/routes.tsx` (`/` chuyển hướng tới `/nhan-vien`; route danh mục `/danh-muc/phong-ban`, `/danh-muc/noi-lam-viec`)
- Test: `app/src/main/webapp/app/shared/layout/sidebar/menu-items.spec.ts`

**Interfaces:**
- Produces: `function menuFor(authorities: string[]): MenuItem[]` với `MenuItem = { key: string; label: string; path: string; icon: string }`. KH2–KH4 thêm mục vào đây.
  - Mọi vai trò: `nhan-vien` "Nhân viên" `/nhan-vien`.
  - ADMIN, HR: `danh-muc` "Danh mục" `/danh-muc/phong-ban`.
  - ADMIN: `quan-tri` "Quản trị" `/admin/user-management`.
- Trang đăng nhập tiếng Việt, logo DOIT. Logo lấy từ ảnh logo trên canvas; người làm task xin file logo từ HR rồi lưu vào `app/src/main/webapp/content/images/doit-logo.png`. Chưa có file thì dùng chữ "DOIT HRM".

- [ ] **Step 1:** Viết test: `menuFor(['ROLE_KE_TOAN'])` chỉ có `nhan-vien`; `menuFor(['ROLE_HR'])` có `nhan-vien`, `danh-muc`; `menuFor(['ROLE_ADMIN'])` có đủ 3 mục.
- [ ] **Step 2:** Chạy `FE menu-items`. Expected: FAIL.
- [ ] **Step 3:** Cài đặt. Sidebar dùng thẻ `<nav aria-label="Điều hướng chính">` với link `<a>` thật. Màn hình rộng < 768px thì sidebar thu thành thanh trên cùng có nút mở menu.
- [ ] **Step 4:** Chạy lại, rồi chạy cả `cd app && npm test`. Expected: PASS.
- [ ] **Step 5:** Commit `"Giao diện: khung sidebar và theme DOIT"`.

---

### Task 14: Trang danh sách và form nhân viên

**Files:**
- Create: `app/src/main/webapp/app/entities/employee/` gồm `employee-list.tsx`, `employee-form.tsx`, `employee.reducer.ts`, `money-input.tsx`, `money.ts`, `index.tsx`
- Modify: `routes.tsx` (`/nhan-vien`, `/nhan-vien/moi`, `/nhan-vien/:id`, `/nhan-vien/:id/sua`)
- Test: `money.spec.ts`, `employee-form.spec.tsx`, `employee-list.spec.tsx`

**Interfaces:**
- Consumes: API Task 6–8, 12; `menuFor`.
- Produces:
  - `parseMoney(s: string): number | null` và `formatMoney(n: number): string` (`15000000 → "15.000.000"`), dùng lại ở KH2/KH3.
  - `<MoneyInput value onChange />` hiển thị theo `formatMoney`.
- Danh sách: ô tìm (gửi `q.contains`), lọc tình trạng / phòng ban / nơi làm việc / loại HĐ hiện tại / loại người (`NHAN_VIEN`/`DICH_VU`), bảng gồm Mã, Họ tên, Phòng ban, Chức danh, Nơi làm việc, Tình trạng, có phân trang. Nút "Xuất Excel" và nút "Thêm nhân viên" (chỉ ADMIN/HR).
- Form chia nhóm như spec mục 5.1: Định danh, Liên hệ, Giấy tờ, Ngân hàng, BHXH, Công việc (phòng ban, chức danh, quản lý trực tiếp chọn từ nhân viên, ngày vào, số người phụ thuộc), Ghi chú. Chọn `DICH_VU` thì ẩn ô Mã và hiện dòng "Mã HDDV sẽ được tự sinh". Form sửa không có trường Tình trạng; cho nghỉ việc nằm ở trang chi tiết (Task 15).

- [ ] **Step 1: Viết test**
  - `money.spec.ts` (Review Focus 5): `parseMoney("15.000.000") = 15000000`; `parseMoney("15000000") = 15000000`; `parseMoney("15,000,000") = 15000000`; `parseMoney("15tr") = null`; `parseMoney("") = null`; `formatMoney(26215000) = "26.215.000"`.
  - `employee-form.spec.tsx`: chọn "HĐ dịch vụ" thì không còn ô "Mã nhân viên"; để trống Họ tên rồi bấm Lưu → hiện "Bắt buộc nhập".
  - `employee-list.spec.tsx`: với vai trò `ROLE_BGD` không có nút "Thêm nhân viên"; gõ "quan" vào ô tìm → request có `q.contains=quan` (mock axios).
- [ ] **Step 2:** Chạy `FE employee`. Expected: FAIL.
- [ ] **Step 3:** Cài đặt.
- [ ] **Step 4:** Chạy lại. Expected: PASS.
- [ ] **Step 5:** Commit `"Giao diện: danh sách và form nhân viên"`.

---

### Task 15: Trang chi tiết nhân viên (theo artboard "Hồ sơ nhân viên")

**Files:**
- Create: `app/src/main/webapp/app/entities/employee/detail/` gồm `employee-detail.tsx` (khung + tab), `info-tab.tsx`, `contracts-tab.tsx`, `contract-dialog.tsx`, `salary-change-dialog.tsx`, `allowance-dialog.tsx`, `documents-tab.tsx`, `assignments-tab.tsx`, `history-tab.tsx`, `terminate-dialog.tsx`
- Test: `contract-dialog.spec.tsx`, `info-tab.spec.tsx`, `assignments-tab.spec.tsx`

**Interfaces:**
- Consumes: mọi API của Task 6–11; `MoneyInput`, `formatMoney`.
- Bố cục:
  - **Thẻ đầu** nền `#1F3240`: chữ viết tắt tên, họ tên, nhãn tình trạng và loại HĐ hiện tại, chức danh · phòng ban · mã; ngày vào, quản lý, nơi làm việc hiện tại; nút "Sửa hồ sơ", "Cho nghỉ việc" (ADMIN/HR). Chưa có nút "Tạo văn bản từ mẫu" (để KH2).
  - **Tab:** Thông tin · Hợp đồng & lương · Hồ sơ scan · Lịch sử công tác · Lịch sử thay đổi (chỉ ADMIN/HR).
  - **Thông tin:** các nhóm trường. Trường nhạy cảm hiện `••••` kèm nút "Hiện" gọi `/sensitive` (nút ẩn với BGD).
  - **Hợp đồng & lương:** bảng HĐ (loại, số, hiệu lực, lương + Net/Gross, trạng thái); dòng "Lương hiện tại"; bảng điều chỉnh lương; bảng phụ cấp. Hộp thoại HĐ: chọn loại; với `HDLD_XDTH` hiện nhóm chọn thời hạn (radio từ `/salary-tools/contract-terms`, nhãn "1 năm / 2 năm / 3 năm"); ngày kết thúc tự điền theo `start + tháng − 1 ngày`, cho sửa; chọn `NET` thì hiện ô "Lương Gross (cho kế toán)" tự điền từ `/gross-suggestion`, cho sửa; chọn `HDTV` và đã có HĐLĐ hoặc nhập lương chính thức tham chiếu thì gợi ý 85%; `contract-advice.nextMustBeIndefinite = true` thì hiện cảnh báo "Đã ký 2 HĐ xác định thời hạn liên tiếp — HĐ tiếp theo phải là HĐ không xác định thời hạn".
  - **Hồ sơ scan, Lịch sử công tác:** như artboard (dòng thời gian, chấm cam là giai đoạn hiện tại; nút "+ Thêm điều động" mở form vị trí với chọn site; lỗi chồng lấn hiện nguyên câu báo của server).

- [ ] **Step 1: Viết test**
  - `contract-dialog.spec.tsx`: chọn `HDLD_XDTH`, chọn "2 năm", nhập ngày bắt đầu 25/10/2026 → ô ngày kết thúc = 24/10/2028; chọn `NET`, nhập 26.215.000 → sau khi mock `/gross-suggestion` trả 30000000 thì ô Gross = "30.000.000"; mock advice `true` → thấy câu cảnh báo.
  - `info-tab.spec.tsx`: vai trò `ROLE_BGD` không thấy nút "Hiện"; `ROLE_HR` bấm "Hiện" → gọi `/api/employees/1/sensitive` và hiển thị số CCCD.
  - `assignments-tab.spec.tsx`: mock POST trả 400 `detail: "Trùng thời gian với đợt công tác …"` → câu đó hiện trên form.
- [ ] **Step 2:** Chạy `FE detail`. Expected: FAIL.
- [ ] **Step 3:** Cài đặt.
- [ ] **Step 4:** Chạy lại, rồi chạy cả `cd app && npm test`. Expected: PASS.
- [ ] **Step 5:** Commit `"Giao diện: trang chi tiết nhân viên"`.

---

### Task 16: Docker Compose, backup, hướng dẫn vận hành

**Files:**
- Create: `Dockerfile` (gốc repo, multi-stage: stage build `maven:3.9-eclipse-temurin-17` chạy `mvn -pl app -am -Pprod -DskipTests package` (gồm build npm); stage chạy `eclipse-temurin:17-jre`, user không phải root, `-Duser.timezone=Asia/Ho_Chi_Minh`, `STORAGE_ROOT=/data/files`)
- Create: `docker-compose.yml`: dịch vụ `db` (`postgres:16`, volume `pgdata`), `app` (build `.`, phụ thuộc `db` healthy, volume `files:/data/files`, `backup-status:/data/backup-status:ro`, cổng `127.0.0.1:8080:8080`), `backup` (image `postgres:16`, chạy `ops/backup/backup.sh` theo vòng lặp 24h lúc 02:00, volume `files:ro`, `backups`, `backup-status`), `caddy` (profile `proxy`, tùy chọn)
- Create: `ops/backup/backup.sh`: `pg_dump -Fc` + `tar czf` thư mục files vào `/backups/YYYY-MM-DD/`; giữ 14 bản mới nhất; nếu `BACKUP_OFFSITE_RSYNC` (dạng `user@host:/path`) có giá trị thì `rsync -a` bản mới nhất sang đó; ghi `/data/backup-status/status.json` = `{"lastBackupAt": "...", "offsiteConfigured": true|false, "offsiteOk": true|false|null}`
- Create: `ops/backup/restore.md` (các bước khôi phục: dừng app, `pg_restore --clean`, giải nén files, chạy lại)
- Create: `.env.example` (`POSTGRES_PASSWORD`, `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`, `BACKUP_OFFSITE_RSYNC=`, `DOMAIN=`)
- Create: `app/src/main/java/vn/doit/hrm/web/rest/BackupStatusResource.java` (`GET /api/admin/backup-status`, ADMIN, đọc `status.json`; không có file → `{lastBackupAt:null, offsiteConfigured:false}`)
- Modify: giao diện Quản trị: hiện băng cảnh báo khi `offsiteConfigured=false` hoặc `lastBackupAt` cũ hơn 36 giờ
- Modify: `README.md`: mục "Chạy local", "Triển khai lên VPS" (copy `.env.example` → `.env`, `docker compose up -d --build`, đổi mật khẩu `admin` ngay lần đầu, bật `--profile proxy` nếu VPS chưa có reverse proxy), "Backup & khôi phục"
- Test: `app/src/test/java/vn/doit/hrm/web/rest/BackupStatusResourceIT.java`

- [ ] **Step 1:** Viết `BackupStatusResourceIT`: không có file → `offsiteConfigured=false`; có file mẫu → trả đúng giá trị; `ROLE_HR` → 403.
- [ ] **Step 2:** Chạy `IT BackupStatusResourceIT`. Expected: FAIL, rồi cài đặt, chạy lại. Expected: PASS.
- [ ] **Step 3:** Kiểm tra cả hệ thống: `cp .env.example .env` (điền giá trị thử) → `docker compose up -d --build` → `curl -fsS http://127.0.0.1:8080/management/health` trả `{"status":"UP"…}`; đăng nhập `admin` trên trình duyệt, tạo 1 nhân viên.
- [ ] **Step 4:** Kiểm tra backup và khôi phục: `docker compose exec backup /ops/backup/backup.sh` → có thư mục ngày hôm nay trong volume `backups` và có `status.json`; làm theo `restore.md` trên một database trống → nhân viên vừa tạo vẫn còn.
- [ ] **Step 5:** Commit `"Docker Compose, backup hằng ngày, hướng dẫn triển khai"`.

---

## Kiểm tra cuối kế hoạch

- [ ] `CORE` đạt; `mvn -pl app -am verify` đạt (toàn bộ UT + IT); `cd app && npm test` đạt.
- [ ] Đối chiếu bảng phụ lục mục 8 của spec: mỗi cột Excel từ B đến AV đều nhập được trên form hoặc được tính tự động (kiểm bằng tay trên giao diện với 1 nhân viên giả lập đi đủ các giai đoạn intern → thử việc → HĐLĐ 1 năm → HĐLĐ 2 năm → 2 lần điều chỉnh lương).
