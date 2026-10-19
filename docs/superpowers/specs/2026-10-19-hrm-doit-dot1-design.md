# DOIT HRM – Đợt 1: Hồ sơ nhân viên, Hợp đồng, Bảng lương

Ngày: 19/10/2026 · Trạng thái: bản nháp chờ duyệt

Ký hiệu: **[Chốt]** là điều người dùng đã quyết định. **[Đề xuất]** là lựa chọn của người viết, người dùng chưa xác nhận riêng, người xây dựng có thể đổi nếu có lý do.

---

## 1. Mục đích và lý do

DO IT (Công ty TNHH Công nghệ và Truyền thông DO IT, khoảng 100 người, phần lớn là dev/tester/BA, nhiều người làm onsite tại ngân hàng khách hàng) cần **một hệ thống nhân sự riêng**. **[Chốt]** Hệ thống không dựa trên OrangeHRM và không fork mã nguồn OrangeHRM.

Hiện nay:
- Hồ sơ nhân viên nằm trong một file Excel `DOIT_Quản lý nhân sự.xlsm` (48 cột, xem mục 5.1). Lương, hợp đồng và review lương đều là cột cố định, công thức hay bị `#REF!`, phụ cấp ghi trong comment ô.
- Hợp đồng soạn tay bằng Word.
- Bảng lương làm trên Excel theo mẫu "BẢNG LƯƠNG NHÂN VIÊN".
- Chấm công làm thủ công: quản lý tự đánh dấu.

Repo này đã có **lõi nghiệp vụ Java** (package `vn.doit.hr`, 29+ test đạt) cho: import và kiểm tra bảng lương, sinh hợp đồng từ mẫu Word, nhắc hạn, lịch sử vị trí làm việc, báo cáo nhân sự, chấm công GPS/WiFi. Lõi này viết thuần Java, không phụ thuộc framework, và được thiết kế để gắn vào một ứng dụng Spring/JHipster. README mô tả chi tiết từng phần.

Thiết kế giao diện: canvas "DOIT HRM – Giao diện" (https://claude.ai/artifact/BAX6ZRSNp1KHCj8QBVFGzN), gồm 5 màn hình: Tổng quan, Hồ sơ nhân viên, Chấm công (mobile), Nghỉ phép, Import bảng lương.

**Đợt 1** đưa vào dùng thật 3 việc: lưu hồ sơ nhân viên, quản lý và sinh hợp đồng, import bảng lương. **[Chốt]**

## 2. Mục tiêu

1. HR bỏ hẳn được file Excel quản lý nhân sự: mọi thông tin trong file đó (mục 5.1) nhập được vào hệ thống, có lịch sử thay vì cột cố định.
2. HR tạo được HĐLĐ (và quyết định cử nhân sự) từ dữ liệu hồ sơ bằng vài cú bấm, ra file .docx và .pdf, đồng thời hệ thống ghi nhận hợp đồng đó vào lịch sử của nhân viên.
3. Kế toán upload được bảng lương Excel hằng tháng, xem lỗi/cảnh báo, lưu theo kỳ, có phiên bản khi import lại.
4. Người phụ trách thấy các việc sắp đến hạn (hết hạn HĐ, hết thử việc, xét tăng lương, sinh nhật) trên trang Tổng quan và nhận email tổng hợp hằng ngày (người nhận: mục 5.4).
5. Hệ thống chạy được trên VPS Docker có sẵn của DO IT, và dev trong công ty tự vận hành, bảo trì được.

## 3. Không làm ở đợt 1 (để sau)

- **Chấm công:** vẫn làm thủ công như hiện tại. Lõi `vn.doit.hr.attendance` để nguyên trong code, chưa nối vào giao diện hay API. **[Chốt]**
- **App mobile cho nhân viên:** làm sau. **[Chốt]** Màn hình Chấm công (mobile) trên canvas chỉ để tham khảo.
- **Nghỉ phép, đơn từ, duyệt đơn** (màn Nghỉ phép, khối "Đơn chờ duyệt" trên Tổng quan): để sau. **[Đề xuất]** (Đợt 1 không nhắc đến nghỉ phép.)
- **Nhân viên tự đăng nhập xem hồ sơ/phiếu lương:** chưa có. Đợt 1 chỉ gồm người dùng nội bộ phòng HR, kế toán, ban giám đốc. **[Chốt]**
- **Tính lương trong hệ thống:** không làm. Lương vẫn tính trên Excel; hệ thống chỉ nhận file, kiểm tra và lưu.
- **Gửi phiếu lương cho nhân viên:** để sau.

## 4. Những điều không được xảy ra (dù hệ thống chạy được)

- Người không có quyền xem được lương, số CCCD, số tài khoản. Đây là dữ liệu cá nhân nhạy cảm (Luật Bảo vệ dữ liệu cá nhân, Nghị định 356/2025/NĐ-CP).
- Mất dữ liệu: không có backup, hoặc sửa và xóa không để lại dấu vết.
- Sau khi chuyển sang hệ thống, HR vẫn phải giữ file Excel song song vì thiếu một trường nào đó.
- Hợp đồng sinh ra sai số tiền, sai ngày, mất dấu tiếng Việt hoặc bị thay font so với mẫu Word.
- Import lại bảng lương ghi đè mất bản cũ.
- Dev của công ty không đọc hiểu được code, hoặc không tự dựng lại được môi trường.

## 5. Phạm vi chức năng

### 5.1 Hồ sơ nhân viên

**Nguồn trường dữ liệu:** file `DOIT_Quản lý nhân sự.xlsm`, sheet "Quản lý nhân sự" (dòng tiêu đề là dòng 2). HR **tự nhập tay** khi có hệ thống. **[Chốt]** Không cần chức năng import file này.

Các trường được nhóm lại như sau. Trường tính toán trong Excel thì hệ thống tự tính, không cho nhập.

| Nhóm | Trường (theo cột Excel) |
|---|---|
| Định danh | Mã nhân viên (duy nhất), Họ và tên, Giới tính (Nam/Nữ), Ngày sinh |
| Liên hệ | Email, SĐT, *địa chỉ/nơi cư trú* (cần cho HĐ, Excel chưa có) |
| Giấy tờ | Số CCCD, Ngày cấp, Nơi cấp, Mã số thuế *(trên canvas)*, *Trình độ* (cần cho HĐ) |
| Ngân hàng | Số tài khoản, Ngân hàng |
| BHXH | Số sổ BHXH, Tháng bắt đầu đóng BHXH, Mức lương đóng BHXH hiện tại |
| Công việc | Chức danh, Phòng ban *(trên canvas, Excel chưa có)*, Quản lý trực tiếp *(trên canvas)*, Site làm việc |
| Trạng thái | Tình trạng làm việc (Đang làm việc / Đã nghỉ), Ngày vào công ty, Ngày làm việc cuối cùng, Ngày nghỉ việc, Hình thức nghỉ + lý do (theo README mục 5) |
| Ghi chú | Ghi chú tự do |
| Tính tự động | Lương hiện tại + Net/Gross, Sinh nhật năm nay, Còn bao ngày đến sinh nhật, Thời gian intern/thử việc (tháng), STT |

**Giai đoạn và lương: lưu thành lịch sử, không thành cột.** Excel có các nhóm cột cố định: intern, thử việc, HĐLĐ 1 năm, HĐLĐ 2 năm, lương chính thức ban đầu, review 1, review 2, lương hiện tại. Hệ thống thay bằng:

- **Hợp đồng / giai đoạn** (mục 5.2): mỗi giai đoạn là một bản ghi có loại, từ ngày, đến ngày và mức lương.
- **Điều chỉnh lương** (`salary_change` trong README): mỗi lần review là một bản ghi gồm ngày hiệu lực, mức lương Net/Gross, ghi chú, chu kỳ xét lại.
- **Lương hiện tại** = mức của bản ghi lương/hợp đồng mới nhất có ngày hiệu lực ≤ hôm nay. Logic giống công thức cột AO của Excel. Người đã nghỉ thì để trống.

**Lương Net / Gross [Chốt]:** mỗi mức lương (intern, thử việc, chính thức, mỗi lần điều chỉnh) lưu **cờ Net/Gross**. Nếu là Net thì lưu **thêm số Gross tương ứng**, để kế toán dễ nhìn và thống kê. Báo cáo chi phí và thống kê dùng số Gross.
- **[Đề xuất]** Khi HR nhập lương Net, hệ thống gợi ý số Gross quy đổi bằng `PayrollRules` (BH người lao động 8%/1,5%/1%, giảm trừ gia cảnh, biểu thuế lũy tiến). Muốn quy đổi đúng cần thêm trường **số người phụ thuộc**. HR/kế toán sửa được con số gợi ý; hệ thống lưu số cuối cùng người dùng chấp nhận.
- Lương thử việc thường bằng 85% lương chính thức (Excel có công thức `85%*AG`). **[Đề xuất]** Gợi ý mặc định 85% khi tạo giai đoạn thử việc, cho sửa.

**Phụ cấp:** hiện ghi trong comment ô Excel ("Hỗ trợ thêm 2M/tháng"). Hệ thống có danh sách phụ cấp theo nhân viên (tên, số tiền/tháng, từ ngày, đến ngày), dùng để điền thẻ `phuCap` trong HĐ.

**Site làm việc:** Excel có danh sách Văn phòng, Việt Á, LP Bank, SeAbank. **[Đề xuất]** Ba nơi sau là khách hàng onsite. Site được quản lý thành danh mục (HR thêm được), và nơi làm việc của nhân viên ghi vào **lịch sử vị trí** (`vn.doit.hr.assignment`, bảng `work_assignment` trong README) chứ không phải một ô duy nhất. Hồ sơ hiển thị nơi hiện tại = `locationOn(hôm nay)` cùng dòng thời gian (khối "Lịch sử công tác" trên canvas).

**Hồ sơ scan:** tải lên PDF hoặc ảnh (bằng cấp, CCCD, HĐ đã ký...), xem và tải về. Lưu trên ổ của server (volume Docker).

**Danh sách nhân viên:** tìm theo tên/mã; lọc theo tình trạng, phòng ban, site, loại HĐ; xuất Excel danh sách.

**Không xóa cứng nhân viên.** Người nghỉ thì chuyển trạng thái. Mọi thay đổi trên hồ sơ, lương, hợp đồng đều ghi nhật ký (ai sửa, lúc nào, trước và sau).

### 5.2 Hợp đồng

**Loại hợp đồng [Chốt]:**

| Mã | Tên | Thời hạn |
|---|---|---|
| HDDT | Hợp đồng đào tạo (giai đoạn intern) | Có ngày bắt đầu/kết thúc |
| HDTV | Hợp đồng thử việc | Có ngày bắt đầu/kết thúc |
| HDLD_XDTH | HĐLĐ xác định thời hạn | **Chọn thời hạn khi tạo: 1 năm / 2 năm / 3 năm** |
| HDLD_KXDTH | HĐLĐ không xác định thời hạn | Không có ngày kết thúc |
| HDDV | Hợp đồng dịch vụ | Có ngày bắt đầu, ngày kết thúc tùy chọn |

**Người ký HĐ dịch vụ [Chốt]:** hệ thống **tự sinh mã** `HDDV1`, `HDDV2`, `HDDV3`... (tăng dần, không dùng lại) để bảng lương hằng tháng khớp được theo mã. **[Đề xuất]** Họ là một hồ sơ rút gọn trong cùng danh sách người: bắt buộc họ tên, mã tự sinh, ngày bắt đầu; các trường khác tùy chọn (CCCD, tài khoản ngân hàng, MST). Họ không tính vào chỉ số nhân sự (tổng nhân sự, biến động, tỷ lệ nghỉ việc) nhưng vẫn có mặt trong chi phí lương, đúng như README đã ghi. Nhân viên chính thức vẫn dùng mã nhân viên hiện có do HR nhập.

Excel đang ghi lẫn lộn "HĐLĐ 2 năm" ở tiêu đề cột và "HĐLĐ 3 Năm" ở danh sách chọn. Người dùng muốn **chọn (tích) thời hạn** khi tạo HĐ. **[Đề xuất]** Danh sách thời hạn (1/2/3 năm) là cấu hình, Quản trị sửa được. Ngày kết thúc tự tính = ngày bắt đầu + thời hạn − 1 ngày (đã có trong `ContractData.endDate()`), cho sửa tay.

**Mức lương in trên hợp đồng [Chốt]:** in đúng số đã thỏa thuận. Với lương Net thì **chỉ in số Net** (thẻ `mucLuong`, `mucLuongBangChu`); số Gross quy đổi chỉ lưu trong hệ thống cho kế toán, không in ra.

**Mỗi hợp đồng lưu:** số HĐ (tự sinh `DOIT/{năm}/HĐLĐ/{số}`, cho sửa), loại, thời hạn, từ ngày, đến ngày, chức danh, phòng ban, mức lương + Net/Gross (+ Gross quy đổi), phụ cấp, chế độ nâng lương, trạng thái (Nháp / Đã ký / Đã kết thúc / Đã thanh lý), file .docx sinh ra, file PDF bản đã ký (tải lên sau khi ký).

**Sinh văn bản từ mẫu:**
- Đợt 1 dùng 2 mẫu đã có: `DOIT_HDLD_template.docx` (HĐLĐ) và `DOIT_QD_CuNhanSu_template.docx` (QĐ cử đi công tác / làm việc tại khách hàng). Thẻ và nguồn dữ liệu: xem README mục 2 và 4.
- Xuất .docx và PDF. PDF chuyển bằng LibreOffice trong container và cần font Times New Roman.
- **[Đề xuất]** Có màn hình **quản lý mẫu**: Quản trị/HR tải lên bản .docx mới để thay mẫu hiện tại, kèm kiểm tra mẫu không chứa thẻ lạ hoặc thẻ bị cắt. Các mẫu khác trên canvas (Phụ lục gia hạn, QĐ tăng lương, QĐ điều động, Thanh lý HĐ, HĐ thử việc, HĐ đào tạo) **chưa có file mẫu**. Khi DO IT có mẫu Word thì chỉ cần thêm mẫu và bộ thẻ, không phải sửa kiến trúc.
- Tạo QĐ cử nhân sự thì đồng thời ghi lịch sử vị trí trong cùng một transaction (README mục 4).

**Đánh số văn bản [Đề xuất]:** mỗi loại văn bản (HĐLĐ, HĐTV, HĐĐT, QĐ cử nhân sự...) có dãy số riêng, chạy lại từ đầu mỗi năm, theo dạng `DOIT/{năm}/{mã loại}/{số}`. Quản trị đặt được số bắt đầu cho từng dãy, để nối tiếp các số đã ký tay trong năm 2026. Hệ thống không cấp trùng số; HĐ bị hủy vẫn giữ số đó.

**Quy tắc pháp lý đã có trong lõi:** đã ký 2 HĐ xác định thời hạn liên tiếp thì HĐ tiếp theo phải là HĐ không xác định thời hạn. Màn tạo HĐ cảnh báo trường hợp này.

### 5.3 Bảng lương

Dùng nguyên lõi `vn.doit.hr.payroll` (README mục 1).
- Luồng 4 bước như canvas: **Tải file → Nhận diện cột → Kiểm tra → Xác nhận**.
- Cột được nhận diện theo tên tiêu đề của mẫu bảng lương DOIT (36 trường, `PayrollField`). Màn "Ghép cột" chỉ hiển thị kết quả nhận diện, và báo rõ cột bắt buộc nào bị thiếu. **[Đề xuất]** Đợt 1 chưa cho người dùng ghép cột tay.
- Có lỗi (ERROR) thì không cho lưu và cho tải báo cáo lỗi. Có cảnh báo (WARNING) thì cho lưu sau khi bấm "Đã xem và xác nhận".
- Lưu theo kỳ (YYYY-MM). Import lại cùng kỳ thì tạo phiên bản mới, giữ bản cũ, lưu kèm file Excel gốc.
- **Quyền xem [Chốt]:** chỉ **Kế toán** và **Quản trị** được xem bảng lương. HR và Ban giám đốc không xem được, kể cả trên hồ sơ nhân viên và trong báo cáo.
- Trạng thái kỳ: Nháp → Đã duyệt. **[Đề xuất]** Kế toán import, Quản trị bấm duyệt. Kỳ đã duyệt thì không import đè được nữa, trừ khi Quản trị mở lại.
- Xem bảng lương đã lưu theo kỳ và theo nhân viên (khối "Lương & phụ cấp" trên hồ sơ).
- Mức luật định (`PayrollRules`, hiện cố định cho năm 2026) chuyển thành **bảng cấu hình có ngày hiệu lực** để cập nhật khi luật đổi, như README đã ghi.

### 5.4 Nhắc việc

Dùng lõi `vn.doit.hr.reminder` (README mục 3): hết hạn HĐ, hết thử việc, đến hạn xét tăng lương, sinh nhật.
- Hiện trên trang Tổng quan, khối "Nhắc việc sắp tới", lọc được theo loại.
- Email tổng hợp mỗi ngày làm việc lúc 7:30 (giờ Việt Nam). Cần cấu hình SMTP.
- **Người nhận email [Chốt]:** Kế toán, Quản trị, và **quản lý trực tiếp** của nhân viên liên quan. Quản lý trực tiếp là một nhân viên khác trong hệ thống; email gửi tới địa chỉ email trong hồ sơ của người đó, **không cần họ có tài khoản**. **[Đề xuất]** Email gửi quản lý chỉ nêu sự kiện và ngày (ví dụ "HĐLĐ của Trần Minh Quân hết hạn 25/10"), không có số tiền. Mỗi quản lý chỉ nhận nhắc về nhân viên do mình quản lý; Kế toán và Quản trị nhận tất cả.
- Thay thế sheet "Thông báo" trong Excel (danh sách đến hạn trong 30 ngày).
- **Màn ghi nhận kết quả để dừng nhắc [Đề xuất]:** ghi kết quả thử việc (đạt / không đạt, ngày); ghi kết quả xét lương (đã điều chỉnh, tức tạo bản ghi điều chỉnh lương, hoặc hẹn ngày xét lại); ký HĐ mới; cho nghỉ việc. Không có các màn này thì nhắc việc sẽ lặp đến hết hạn tối đa.
- **Lịch làm việc [Đề xuất]:** ngày làm việc mặc định là thứ Hai–thứ Sáu, đúng thực tế hiện nay (thứ Bảy nghỉ), đổi được trong cấu hình. Quản trị/HR khai báo danh sách ngày lễ theo năm trên một màn đơn giản. Email gửi vào các ngày làm việc. Mốc nhắc rơi vào ngày nghỉ thì dời về ngày làm việc liền trước.

### 5.5 Trang Tổng quan

Theo canvas, giữ lại các khối có dữ liệu ở đợt 1:
- Ô chỉ số: tổng nhân sự, đang thử việc, HĐ hết hạn trong 30 ngày, tổng chi phí lương tháng gần nhất (lấy từ kỳ lương đã import; **chỉ Kế toán và Quản trị thấy ô này**).
- Nhắc việc sắp tới.
- Biến động nhân sự theo tháng và xuất báo cáo Excel, dùng `vn.doit.hr.report`. Báo cáo chi phí lương lấy từ các kỳ đã import, và chỉ Kế toán và Quản trị mở được.

Bỏ ở đợt 1: "Đơn chờ duyệt" và "Chấm công hôm nay".

### 5.6 Người dùng và phân quyền [Chốt]

Đăng nhập bằng email + mật khẩu. Quản trị tạo tài khoản; không có tự đăng ký.

| Vai trò | Quyền |
|---|---|
| **Quản trị** | **Toàn quyền**: mọi chức năng dưới đây, cộng quản lý tài khoản, phân quyền, danh mục, mẫu văn bản, cấu hình. |
| HR | Xem và sửa hồ sơ, hợp đồng (gồm mức lương trên HĐ), phụ cấp, điều chỉnh lương, lịch sử vị trí, hồ sơ scan; sinh văn bản; xem nhắc việc. **Không xem bảng lương hằng tháng.** |
| Kế toán | Import và xem bảng lương, báo cáo chi phí lương; nhận email nhắc việc; xem hồ sơ ở mức cần cho lương (mã, tên, phòng ban, mức lương, BHXH, tài khoản ngân hàng); không sửa hồ sơ. |
| Ban giám đốc | Xem hồ sơ, hợp đồng, nhắc việc, báo cáo nhân sự; **không xem bảng lương** và báo cáo chi phí lương; không sửa hồ sơ. |

**[Đề xuất]** Một người có thể giữ nhiều vai trò. Quyền được kiểm tra ở backend (API), không chỉ ẩn nút trên giao diện. Xem số CCCD/tài khoản cũng được ghi nhật ký truy cập.

## 6. Kỹ thuật

### 6.1 Đã quyết

- **[Chốt]** Hệ thống riêng, không dùng mã nguồn OrangeHRM.
- **[Chốt]** Toàn bộ chạy bằng **Docker** (Docker Compose) trên **VPS đang chạy Docker** của DO IT.
- **[Chốt]** Công ty có dev và sẽ tự bảo trì: code phải dễ đọc, có README hướng dẫn chạy local, build và triển khai.

### 6.2 Lựa chọn của người viết (người dùng chưa phản đối, có thể đổi)

- **Backend:** Java 17, Spring Boot, dựng khung bằng **JHipster** (monolith). Mục đích: có sẵn đăng nhập, phân quyền, CRUD, Liquibase, cấu hình Docker, và cắm thẳng được lõi `vn.doit.hr`.
- **Database:** PostgreSQL. Lược đồ phác trong README (`employee_contract`, `probation`, `salary_change`, `reminder_log`, `work_assignment`, `work_assignment_audit`, `payroll_period`, `payroll_line`) là điểm xuất phát, không bắt buộc giữ nguyên.
- **Frontend web:** React (lựa chọn React của JHipster). Giao diện bám canvas: font Be Vietnam Pro, nền #EFEEE6, thanh trái #1F3240, nhấn #E84424 / #B8341A, logo DOIT. Lý do chọn React: sau này app mobile có thể dùng React Native.
- **Lưu file:** ổ đĩa server, mount volume Docker. Một thư mục riêng cho mẫu, file sinh ra và hồ sơ scan.
- **PDF:** LibreOffice headless trong image backend (hoặc container riêng), cài font Times New Roman.
- **Email:** SMTP cấu hình qua biến môi trường.
- **Backup [Đề xuất]:** một container backup chạy hằng ngày, `pg_dump` + nén thư mục file, giữ 14 bản trên VPS, **và** đẩy thêm một bản ra ngoài VPS. Đích là cấu hình (rsync/SSH sang máy khác, hoặc S3-compatible); chưa cấu hình thì hệ thống cảnh báo trên màn Quản trị. Kèm hướng dẫn khôi phục và thử khôi phục một lần trước khi dùng thật.
- **HTTPS:** reverse proxy (Caddy hoặc Nginx) trước ứng dụng. Nếu VPS đã có proxy thì dùng lại. File compose để proxy thành dịch vụ tùy chọn.
- **Triển khai [Đề xuất]:** LibreOffice chạy thành container riêng, để image backend nhỏ và giới hạn được RAM riêng. Toàn hệ thống cần khoảng 2–3 GB RAM. Image build ngay trên VPS bằng `docker compose build`, chưa cần CI/registry. Thông số VPS (RAM trống, proxy, tên miền) kiểm tra lúc triển khai.
- **Ngôn ngữ giao diện:** tiếng Việt. Định dạng ngày dd/MM/yyyy, tiền `1.234.567`, múi giờ Asia/Ho_Chi_Minh.

### 6.3 Tổ chức repo

Repo hiện tại (`hr-extension-core`, Maven) chứa lõi và test. Người xây dựng quyết định cách tổ chức. Ràng buộc:
- Lõi `vn.doit.hr` và toàn bộ test hiện có vẫn chạy và vẫn đạt.
- Lõi không bị kéo phụ thuộc Spring vào trong. Phần Spring (repository, service, controller) nằm ở lớp ngoài.
- Một lệnh `docker compose up` dựng được toàn hệ thống trên máy dev.

## 7. Để người xây dựng tự quyết

- Cách chia module Maven/Gradle, tên package cho phần ứng dụng.
- Lược đồ bảng chi tiết, cách lưu `payroll_line` (dòng-theo-trường hay cột-theo-trường).
- Cách hiển thị và các trạng thái giao diện chi tiết, miễn đúng tinh thần canvas và dùng được ở màn hình hẹp.
- Thư viện bảng, form, biểu đồ phía React.
- Chiến lược test giao diện (tối thiểu: test API cho phân quyền và các luồng chính).

## 8. Phụ lục: đối chiếu cột Excel → hệ thống

| Cột Excel | Đi đâu |
|---|---|
| A STT | Không lưu (hiển thị) |
| B–M | Hồ sơ: định danh, liên hệ, giấy tờ, ngân hàng |
| N Chức danh | Hồ sơ (+ lưu trên từng hợp đồng) |
| O Site làm việc | Lịch sử vị trí |
| P Tình trạng | Hồ sơ |
| Q Loại HĐ | Suy ra từ hợp đồng hiện hành |
| R Ngày vào công ty | Hồ sơ |
| S–W Intern | Hợp đồng loại HDDT |
| X–AA Thử việc | Hợp đồng loại HDTV (+ bảng `probation` để nhắc) |
| AB–AE HĐLĐ 1 năm / 2 năm | Hợp đồng HDLD_XDTH, thời hạn tương ứng |
| AF Ngày làm việc cuối cùng, AQ Ngày nghỉ việc | Hồ sơ (nghỉ việc) |
| AG–AH Lương chính thức ban đầu | Lương trên HĐLĐ đầu tiên |
| AI–AN Review 1, 2 | Điều chỉnh lương |
| AO–AP Lương hiện tại | Tính tự động |
| AR–AS Sinh nhật | Tính tự động |
| AT Ghi chú | Hồ sơ |
| AU–AV BHXH | Hồ sơ |
| Comment "Hỗ trợ thêm…" | Phụ cấp |
