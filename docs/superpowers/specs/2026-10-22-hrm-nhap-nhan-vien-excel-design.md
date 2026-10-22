# DOIT HRM – Nhập danh sách nhân viên từ Excel

Ngày: 22/10/2026 · Trạng thái: chờ duyệt · Theo khuôn import bảng lương (KH3, `2026-10-21-hrm-kh3-bang-luong-design.md`) và quy tắc mã tự gán `EmployeeCodes.next` (đợt `feature/bh-ma-thuc-tap`)

Ký hiệu: **[Chốt]** người dùng đã quyết định; **[Đề xuất]** lựa chọn của người viết, người dùng đã đồng ý khi duyệt từng phần.

## 1. Mục tiêu

Một công ty mới hoặc một người mới dùng DOIT HRM nhập toàn bộ danh sách nhân viên **một lần** từ file Excel, thay vì tạo từng hồ sơ trên màn hình. Hiện hệ thống chỉ có tạo từng hồ sơ và xuất Excel (`GET /api/employees/export.xlsx`).

**Thành công khi:**

- HR tải file mẫu, điền vài chục đến vài trăm người, tải lên và lưu được trong một lần;
- mọi lỗi được chỉ ra đúng dòng Excel và đúng cột, không phải đoán;
- không bao giờ tạo hai hồ sơ trùng mã hoặc trùng CCCD;
- người không ghi mã nhận mã đúng quy tắc DOIT/TT/HDDV cộng dồn;
- còn lỗi thì không có hồ sơ nào được tạo.

## 2. Phạm vi

**Có [Chốt]:** mỗi dòng tạo **một hồ sơ mới** (`Employee`), cùng các phòng ban chưa có (theo tên).

**Không có:**

- cập nhật hồ sơ đã có (dòng trùng là lỗi, xem §5);
- hợp đồng, lương ban đầu, phụ cấp: nhập sau trên từng hồ sơ;
- tạo tài khoản đăng nhập: làm sau trên từng hồ sơ như hiện nay;
- nơi làm việc (`WorkAssignment`), người đã nghỉ (trạng thái luôn là Đang làm việc);
- file `.xls` hoặc `.csv`.

## 3. Cách làm [Chốt]: kiểm tra rồi lưu, không có bản nháp

Khác import bảng lương: **không** có entity bản nháp và **không** lưu file gốc trên máy chủ. Trình duyệt gửi cùng một file hai lần:

1. `POST /check`: đọc và kiểm tra, trả kết quả, không ghi gì;
2. `POST` (lưu): đọc và kiểm tra lại từ đầu, hợp lệ thì lưu trong một giao dịch.

Lý do: file chứa CCCD và số tài khoản không nằm lại trên đĩa; nhập lần đầu xong trong vài phút nên không cần lưu nháp để quay lại; không cần bảng mới hay Liquibase. Gửi file hai lần (≤ 20 MB) là chấp nhận được.

## 4. File mẫu và cách đọc

### 4.1 Đơn vị mã nguồn [Đề xuất]

Gói mới `core/src/main/java/vn/doit/hr/employee/` (không phụ thuộc Spring, như `vn.doit.hr.payroll`):

- `EmployeeField`: enum các cột, mỗi cột có tiêu đề, tiêu đề phụ trong sheet hướng dẫn, cờ bắt buộc, kiểu (`TEXT`, `DATE`, `INTEGER`, `CHOICE`), độ dài tối đa theo cột cơ sở dữ liệu.
- `EmployeeTemplateWriter`: dựng file mẫu từ `EmployeeField`.
- `EmployeeSheetParser`: đọc `SheetGrid` (dùng lại `PoiSheetGrid`) thành `ParsedEmployees` (cột đã nhận, các dòng `EmployeeRow`, lỗi cấp file/ô).
- `EmployeeIssue`: record lỗi/cảnh báo `(severity, excelRow, fullName, field, message)`, tương tự `vn.doit.hr.payroll.Issue` nhưng gắn `EmployeeField`.

Writer và parser cùng dựng từ một enum nên tiêu đề file mẫu và bộ đọc luôn khớp nhau.

### 4.2 File mẫu [Đề xuất]

`GET /api/employees/import/template.xlsx`, tên tải về `mau-nhap-nhan-vien.xlsx`.

- **Sheet "Danh sách nhân viên":** dòng 1 là tiêu đề cột, dữ liệu từ dòng 2. Cột bắt buộc tô cam, cột khác tô xanh nhạt (như mẫu bảng lương). Cố định dòng tiêu đề.
- Các cột Mã NV, Số CCCD, Điện thoại, MST cá nhân, Số tài khoản, Số sổ BHXH, Quản lý trực tiếp định dạng sẵn là **chữ** (`@`), để Excel không làm mất số 0 ở đầu.
- Danh sách thả xuống (data validation): Loại hồ sơ (`Nhân viên`, `Thực tập`, `HĐ dịch vụ`), Giới tính (`Nam`, `Nữ`), Không thuộc diện BH bắt buộc (5 nhãn của `InsuranceExemption`).
- **Sheet "Hướng dẫn":** mỗi cột một dòng: tên cột, bắt buộc hay không, kiểu, ví dụ (tên và số giả), ghi chú. Có đoạn giải thích quy tắc mã tự gán (§5.3).
- Không có dòng dữ liệu thật trong sheet nhập.

### 4.3 Các cột [Chốt]

Họ và tên và Số CCCD là bắt buộc; các cột khác không bắt buộc.

| Cột trong file | Trường `Employee` | Kiểu, ghi chú |
|---|---|---|
| Mã NV | `code` | Chữ, ≤ 20. Trống thì tự gán (§5.3) |
| Họ và tên * | `fullName` | Chữ, ≤ 100 |
| Loại hồ sơ | `personType` | `Nhân viên` / `Thực tập` / `HĐ dịch vụ`; trống = Nhân viên |
| Giới tính | `gender` | `Nam` / `Nữ` |
| Ngày sinh | `birthDate` | Ngày |
| Số CCCD * | `idNumber` | 12 chữ số (hoặc 9 chữ số CMND cũ) |
| Ngày cấp | `idIssueDate` | Ngày |
| Nơi cấp | `idIssuePlace` | Chữ, ≤ 200 |
| Địa chỉ thường trú | `residence` | Chữ, ≤ 300 |
| Điện thoại | `phone` | Chữ, ≤ 20 |
| Email | `email` | Email, ≤ 100 |
| Trình độ | `education` | Chữ, ≤ 100 |
| MST cá nhân | `taxCode` | Chữ, ≤ 20 |
| Số tài khoản | `bankAccount` | Chữ, ≤ 30 |
| Ngân hàng | `bankName` | Chữ, ≤ 100 |
| Số sổ BHXH | `socialInsuranceNo` | Chữ, ≤ 20 |
| Ngày bắt đầu đóng BHXH | `socialInsuranceStart` | Ngày |
| Lương đóng BHXH | `socialInsuranceSalary` | Số nguyên ≥ 0 (đồng) |
| Không thuộc diện BH bắt buộc | `insuranceExemption` | Nhãn của `InsuranceExemption`; trống = thuộc diện đóng |
| Lý do khác | `insuranceExemptionNote` | Chữ, ≤ 500; bắt buộc khi chọn "Khác" |
| Số người phụ thuộc | `dependants` | Số nguyên ≥ 0; trống = 0 |
| Phòng ban | `department` | Tên phòng ban (§5.4) |
| Vị trí | `position` | Chữ, ≤ 100 |
| Quản lý trực tiếp | `manager` | Mã NV hoặc số CCCD của người quản lý (§5.5) |
| Ngày vào làm | `joinDate` | Ngày |
| Ghi chú | `note` | Chữ |

### 4.4 Cách đọc [Đề xuất]

- Chỉ nhận `.xlsx` (kiểm tra đuôi và chữ ký `PK`), tối đa 20 MB và 2.000 dòng dữ liệu; vượt thì báo lỗi cấp file.
- Đọc sheet "Danh sách nhân viên"; không có thì đọc sheet đầu tiên.
- Dòng tiêu đề: dòng đầu tiên trong 10 dòng đầu có ô "Họ và tên". Cột nhận theo tên, không phân biệt hoa thường, dấu và khoảng trắng (`Text.normalize`), nên thứ tự cột không quan trọng và có thể thiếu cột không bắt buộc. Thiếu cột "Họ và tên" hoặc "Số CCCD" là lỗi cấp file. Cột có tiêu đề lạ thì bỏ qua kèm cảnh báo cấp file.
- Dòng dữ liệu: mọi dòng sau tiêu đề; dòng trống hoàn toàn thì bỏ qua. Số dòng báo lỗi là số dòng trong Excel (bắt đầu từ 1).
- Ô chữ: cắt khoảng trắng hai đầu; chuỗi rỗng là trống. Ô số ở cột chữ (vd CCCD gõ thành số) đổi thành chữ không có phần thập phân.
- Ngày: nhận ô ngày của Excel hoặc chữ `dd/MM/yyyy` (chấp nhận `d/M/yyyy`).
- Lựa chọn (Loại hồ sơ, Giới tính, Lý do BH): so khớp không phân biệt hoa thường hay dấu với nhãn tiếng Việt.

## 5. Kiểm tra từng dòng

Lỗi (`ERROR`) chặn việc lưu. Cảnh báo (`WARNING`) cho lưu nhưng người dùng phải bấm "Đã xem cảnh báo".

### 5.1 Lỗi [Chốt cho các quy tắc trùng và bắt buộc; Đề xuất cho phần còn lại]

- Thiếu Họ và tên hoặc Số CCCD.
- Số CCCD không phải 12 hoặc 9 chữ số (cùng quy tắc `IdNumbers`).
- Số CCCD trùng với hồ sơ đã có (kể cả người đã nghỉ, so theo CCCD đã cắt khoảng trắng như `findAllByTrimmedIdNumber`), hoặc trùng giữa hai dòng trong file (báo ở cả hai dòng).
- Mã NV đã có trong hệ thống, hoặc trùng giữa hai dòng trong file (so sau `EmployeeCodes.normalize`).
- Ngày không đọc được; giá trị lựa chọn không có trong danh sách.
- Số người phụ thuộc hoặc Lương đóng BHXH không phải số nguyên ≥ 0.
- Chuỗi dài quá độ dài cột (§4.3); Email sai dạng.
- Chọn "Khác" ở Không thuộc diện BH bắt buộc mà để trống Lý do khác.
- Quản lý trực tiếp: không tìm thấy, trỏ vào chính dòng đó, hoặc tạo vòng giữa các dòng trong file (§5.5).

### 5.2 Cảnh báo [Đề xuất]

- Ô Số CCCD dạng số có 11 chữ số: hệ thống thêm số 0 ở đầu thành 12 số và báo "đã thêm số 0 ở đầu, kiểm tra lại".
- Phòng ban chưa có trong hệ thống: sẽ được tạo mới (liệt kê một lần trong phần tổng hợp, kèm số dòng dùng).
- Thiếu Ngày vào làm: mã tự gán lấy năm nay.
- Có ghi Mã NV cho dòng HĐ dịch vụ: mã bị bỏ qua, HDDV luôn tự sinh.
- Mã NV ghi tay không theo dạng `DOIT`/`TT` + số: vẫn nhận, nhưng dãy cộng dồn chỉ tính các mã đúng dạng.
- Ngày sinh cho thấy dưới 15 tuổi tính đến ngày vào làm (hoặc hôm nay); Ngày vào làm sau hôm nay.
- Cột lạ trong file (cấp file).

### 5.3 Mã dự kiến [Chốt: trống thì tự gán, có ghi thì giữ]

- Dòng có ghi mã (Nhân viên, Thực tập): giữ nguyên mã đó sau khi chuẩn hóa (viết hoa, cắt khoảng trắng).
- Dòng HĐ dịch vụ: luôn tự sinh `HDDVn` khi lưu; bước kiểm tra hiện "HDDV (tự sinh)".
- Dòng Nhân viên hoặc Thực tập để trống mã: cấp tạm theo thứ tự dòng trong file bằng `EmployeeCodes.next(prefix, năm vào làm (trống thì năm nay), existing)`. `existing` là mọi mã có tiền tố trong hệ thống (`findCodesStartingWith`) cộng các mã đã ghi tay hoặc đã cấp tạm cho các dòng trước trong file. Số đã dùng không cấp lại.
- Mã tạm hiện ở bước kiểm tra kèm nhãn "dự kiến". Khi lưu, hệ thống gọi `lockCodeAssignment()` rồi tính lại theo cùng quy tắc. Nếu có người vừa tạo hồ sơ khác, mã thật có thể lệch mã dự kiến. Kết quả sau khi lưu luôn trả về mã thật.

### 5.4 Phòng ban [Chốt: tự tạo, có cảnh báo]

So khớp tên phòng ban trong file với `Department.name` không phân biệt hoa thường, dấu và khoảng trắng thừa. Có nhiều phòng ban cùng tên sau khi chuẩn hóa thì ưu tiên phòng ban đang hoạt động; vẫn còn nhiều hơn một thì báo lỗi "tên phòng ban không rõ". Tên chưa có: cảnh báo (§5.2) và tạo phòng ban mới (`active = true`, tên theo cách viết ở dòng đầu tiên dùng tên đó) khi lưu.

### 5.5 Quản lý trực tiếp [Chốt: mã NV hoặc CCCD]

Giá trị ô được tìm theo thứ tự:

1. mã NV của hồ sơ đã có;
2. mã NV ghi tay của một dòng trong file;
3. số CCCD của hồ sơ đã có;
4. số CCCD của một dòng trong file.

Trỏ theo CCCD giúp chỉ tới người trong cùng file chưa có mã (mã tự gán). Không tìm thấy, trỏ vào chính mình, hoặc các dòng trong file trỏ nhau thành vòng (A → B → A) là lỗi. Mã dự kiến (§5.3) không dùng để trỏ được, vì có thể đổi khi lưu.

## 6. API [Đề xuất]

`EmployeeImportResource`, gốc `/api/employees/import`, mọi endpoint `@PreAuthorize` cho `ADMIN` và `HR` (giống tạo hồ sơ). Logic nằm ở `EmployeeImportService` (`app/.../service/employee/`).

| Phương thức | Đường dẫn | Vào | Ra |
|---|---|---|---|
| GET | `/template.xlsx` | — | File mẫu |
| POST | `/check` | multipart `file` | `EmployeeImportCheckDTO` |
| POST | `/issues.xlsx` | multipart `file` | Báo cáo lỗi `loi-nhap-nhan-vien.xlsx` (dòng, họ tên, cột, mức, nội dung) |
| POST | `` (gốc) | multipart `file`, `warningsAcknowledged` (boolean) | `EmployeeImportResultDTO` |

`EmployeeImportCheckDTO`: `rowCount`; `rows` (mỗi dòng: `excelRow`, `fullName`, `idNumber`, `personType`, `code`, `codeProvisional`, `departmentName`, `managerRef`); `issues`; `newDepartments` (tên + số dòng dùng); `errorCount`; `warningCount`; `canSave` (= không có lỗi).

Lưu (`POST` gốc):

1. Đọc và kiểm tra lại toàn bộ.
2. Còn lỗi, hoặc có cảnh báo mà `warningsAcknowledged` khác `true`: trả 400 (`EmployeeRuleException`, khóa `importinvalid`) kèm `EmployeeImportCheckDTO` trong thân lỗi. Không ghi gì.
3. Trong **một giao dịch**:
   1. `lockCodeAssignment()`;
   2. tạo các phòng ban mới;
   3. tạo hồ sơ theo thứ tự dòng, áp cùng quy tắc như `EmployeeService` (trạng thái Đang làm việc, `dependants` mặc định 0, cắt CCCD, chuẩn hóa mã, HDDV tự sinh, mã kế tiếp, kiểm tra trùng mã, `searchText`, không gắn tài khoản). Phần quy tắc này tách khỏi `EmployeeService.applyRules` thành phương thức dùng chung, không chép lại;
   4. gán quản lý ở lượt thứ hai, sau khi mọi hồ sơ đã có id.

   Bất kỳ lỗi nào (vd trùng mã do người khác vừa tạo) làm hoàn tác toàn bộ.
4. `EmployeeImportResultDTO`: `createdCount`, danh sách `(excelRow, fullName, code)` với mã thật, `createdDepartments`.

Lịch sử thay đổi (Envers) ghi nhận người nhập như khi tạo tay.

## 7. Dữ liệu nhạy cảm [Đề xuất]

- File chỉ đọc trong bộ nhớ trong thời gian một yêu cầu; không lưu xuống đĩa, không cache.
- Không ghi nội dung dòng (CCCD, số tài khoản, lương) vào log; log chỉ ghi số dòng, số lỗi và người nhập.
- Kết quả kiểm tra chỉ trả về các cột cần để người dùng soát lỗi (§6), không trả lại MST, số tài khoản hay lương đóng BHXH.
- Dữ liệu kiểm thử dựng bằng code với tên và số giả; không đưa file thật của người dùng vào repo.

## 8. Giao diện [Đề xuất]

- Trang Danh sách nhân viên: nút "Nhập từ Excel" cạnh nút Xuất Excel, chỉ hiện với Quản trị và HR. Mở trang `/employee/import`.
- Wizard 3 bước, cùng kiểu thanh bước và bố cục với `modules/payroll/import-wizard.tsx`:
  1. **Tải file:** nút "Tải file mẫu" (`downloadFile`), ô chọn file `.xlsx`. Chọn xong thì gọi `/check`. Trình duyệt giữ đối tượng `File` để gửi lại khi lưu.
  2. **Kiểm tra:**
     - thanh tổng hợp: N dòng, X lỗi, Y cảnh báo, các phòng ban sẽ tạo;
     - bảng các dòng: dòng Excel, họ tên, CCCD, loại, mã (nhãn "dự kiến" hoặc "tự sinh"), phòng ban, quản lý; dòng có lỗi tô đỏ, có cảnh báo tô vàng; bên dưới là danh sách lỗi và cảnh báo theo dòng;
     - các nút: "Tải báo cáo lỗi", "Chọn file khác" (quay về bước 1), ô "Đã xem cảnh báo" (khi có cảnh báo), "Lưu N hồ sơ" (chỉ bật khi `canSave` và đã xác nhận cảnh báo nếu có);
     - nếu lưu trả 400 kèm kết quả kiểm tra mới, hiển thị lại kết quả đó.
  3. **Kết quả:** "Đã tạo N hồ sơ" và các phòng ban mới, bảng dòng → họ tên → mã thật, nút "Về danh sách nhân viên".

## 9. Kiểm thử [Đề xuất]

- **`core` (JUnit):**
  - file mẫu đọc lại được bằng chính `EmployeeSheetParser`, ra đủ cột và không có lỗi;
  - cột đảo thứ tự, thiếu cột không bắt buộc, cột lạ;
  - ngày dạng ô và dạng chữ;
  - CCCD dạng số mất số 0;
  - dòng trống ở giữa và cuối;
  - vượt 2.000 dòng.
- **`app` (đơn vị):**
  - từng quy tắc lỗi và cảnh báo ở §5;
  - mã dự kiến: DOIT và TT theo năm vào làm, mã ghi tay đúng dạng xen giữa làm tăng số kế tiếp, HDDV bỏ qua mã ghi tay;
  - phòng ban trùng tên sau chuẩn hóa;
  - quản lý trỏ theo mã/CCCD trong hệ thống và trong file, vòng quản lý.
- **`app` (tích hợp REST):**
  - 403 cho Kế toán và nhân viên;
  - lưu thành công tạo đúng số hồ sơ, phòng ban và quản lý;
  - còn lỗi thì không tạo gì;
  - cảnh báo chưa xác nhận thì 400;
  - trùng mã phát sinh giữa lúc kiểm tra và lúc lưu thì hoàn tác toàn bộ.
- **Giao diện (Jest):**
  - đủ 3 bước;
  - tải file mẫu;
  - nút Lưu tắt khi còn lỗi hoặc chưa xác nhận cảnh báo;
  - hiển thị kết quả mã thật.
