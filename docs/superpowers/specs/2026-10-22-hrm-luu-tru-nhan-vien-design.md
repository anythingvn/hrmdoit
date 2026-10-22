# DOIT HRM – Lưu trữ, khôi phục, nhận làm lại và xóa hẳn nhân viên

Ngày: 22/10/2026 · Trạng thái: chờ duyệt · Bổ sung cho phần Nghỉ việc (README "Nghỉ việc / hẹn ngày / hủy / hoàn tác") và Nghỉ phép (`2026-10-22-hrm-nghi-phep-don-tu-design.md`)

Ký hiệu: **[Chốt]** người dùng đã quyết định; **[Đề xuất]** người viết đề xuất và người dùng đã đồng ý khi duyệt từng phần.

## 1. Mục tiêu

Hiện hệ thống chưa có chức năng xóa nhân viên. Người đã nghỉ việc vẫn nằm trong danh sách với trạng thái "Đã nghỉ". Đợt này thêm bốn việc:

- **Vùng lưu trữ.** Người đã nghỉ việc được đưa vào vùng lưu trữ, và không còn xuất hiện ở các màn làm việc hằng ngày.
- **Khôi phục.** Hồ sơ trong vùng lưu trữ được đưa lại vào danh sách.
- **Nhận làm lại.** Người đã nghỉ quay lại làm việc mà vẫn giữ một hồ sơ và một mã nhân viên.
- **Xóa hẳn.** Chỉ tài khoản quản trị cao nhất được xóa hẳn. Mọi thao tác "xóa" khác đều chỉ là đưa vào lưu trữ.

**Ràng buộc chính [Chốt]:** lưu trữ, khôi phục hay xóa hẳn đều **không làm thay đổi báo cáo lương các năm trước**.

**Thành công khi:**

- HR không thể làm mất dữ liệu: nút "xóa" của HR và của Quản trị thường chỉ đưa hồ sơ vào lưu trữ.
- Sau khi lưu trữ rồi khôi phục, hồ sơ giống hệt trước khi lưu trữ.
- Sau khi xóa hẳn một người, bảng lương, tổng lương và file xuất Excel của mọi kỳ đã duyệt ra đúng số như trước khi xóa.
- Người đi làm lại có một hồ sơ, một mã. Lịch sử của lần làm việc trước được giữ, và sổ phép tính lại từ ngày vào làm mới.

## 2. Phạm vi

**Có:** lưu trữ, vùng lưu trữ, khôi phục, nhận làm lại, xóa hẳn, nhật ký xóa.

**Không có:**

- tự động lưu trữ sau một thời gian [Chốt: HR lưu trữ tay];
- lưu trữ người đang làm việc (phải cho nghỉ việc trước), trừ hồ sơ tạo nhầm (§3.1);
- bản chụp tổng hợp nghỉ phép/làm thêm cho các tháng đã duyệt lương [Chốt: không cần giữ, xem §6.4];
- thâm niên phép cộng dồn qua nhiều lần làm việc [Chốt: tính từ ngày vào làm mới].

## 3. Lưu trữ

### 3.1 Điều kiện [Chốt]

Quản trị và HR đưa được hồ sơ vào lưu trữ khi hồ sơ thỏa một trong hai điều kiện:

- trạng thái là **Đã nghỉ** (việc cho nghỉ đã được áp dụng, không chỉ hẹn lịch); hoặc
- **hồ sơ tạo nhầm:** chưa có hợp đồng, thay đổi lương, phụ cấp, quyết định, tài liệu, đơn nghỉ, đơn làm thêm, dòng sổ phép, dòng lương, phiếu lương và lịch sử nghỉ việc nào. Hồ sơ loại này lưu trữ thẳng được dù đang ở trạng thái "Đang làm việc"; tài khoản gắn kèm, nếu có, bị khóa.

Trường hợp khác bị từ chối với lỗi 400 `archivenotterminated`, thông báo "Chỉ lưu trữ được nhân viên đã nghỉ việc. Hãy cho nghỉ việc trước". Lưu trữ một hồ sơ đã nằm trong vùng lưu trữ bị từ chối với lỗi 400 `alreadyarchived`.

### 3.2 Thao tác [Đề xuất]

- Trên hồ sơ có nút **"Đưa vào lưu trữ"**, với hộp xác nhận và ô ghi chú không bắt buộc. Hồ sơ lưu `archived_at`, `archived_by` và `archive_note`, kèm `_aud`.
- **Không xóa hay sửa dữ liệu nào khác.** Tài khoản đăng nhập đã bị khóa từ lúc cho nghỉ việc và vẫn khóa.
- Nếu hồ sơ đang là quản lý trực tiếp (`manager`) của người khác hoặc trưởng phòng của một phòng ban, việc lưu trữ vẫn được làm. Kết quả trả về danh sách những người và phòng ban đó, kèm câu "Hãy đổi quản lý/trưởng phòng cho các hồ sơ sau". Người đã nghỉ không còn được chọn làm người duyệt, theo quy tắc nghỉ phép có sẵn.

### 3.3 Hồ sơ đã lưu trữ hiện ở đâu [Đề xuất]

| Nơi | Hồ sơ đã lưu trữ |
|---|---|
| Danh sách nhân viên, tìm kiếm, bộ lọc, xuất Excel danh sách, đếm quân số | Không hiện |
| Ô chọn người (quản lý, trưởng phòng, người duyệt, gắn hồ sơ khi import bảng lương) | Không hiện |
| Nhắc việc, email tổng hợp, Tổng quan, lịch nghỉ, số dư phép của công ty, hộp duyệt đơn | Không hiện |
| Bảng lương và phiếu lương của các kỳ cũ, tổng hợp nghỉ phép cho kế toán của tháng cũ | Hiện bình thường, có nhãn "Lưu trữ" |
| Mở hồ sơ theo đường dẫn trực tiếp (`/nhan-vien/{id}`) | Xem được ở chế độ chỉ xem, có băng "Hồ sơ đã lưu trữ" kèm nút Khôi phục; mọi API sửa trả 400 `employeearchived` |
| Vùng lưu trữ (§4) | Hiện |

Chặn ở backend: các truy vấn danh sách và ô chọn lọc `archived_at is null` theo mặc định. API sửa hồ sơ và các bản ghi con (hợp đồng, lương, phụ cấp, tài liệu, nơi làm, quyết định, đơn, điều chỉnh phép) từ chối hồ sơ đã lưu trữ.

## 4. Vùng lưu trữ [Đề xuất]

- Trang **"Nhân viên đã lưu trữ"** trong menu Nhân viên, dành cho Quản trị và HR.
- Có tìm theo mã, họ tên, CCCD và phân trang.
- Các cột: mã, họ tên, phòng ban cuối, ngày làm việc cuối, ngày lưu trữ, người lưu trữ, ghi chú.
- Mỗi dòng có nút **Xem** và **Khôi phục**. Nút **Xóa hẳn** chỉ hiện khi người đăng nhập là tài khoản `admin`.
- API: `GET /api/employees/archived` cho Quản trị và HR.

## 5. Khôi phục và nhận làm lại

### 5.1 Khôi phục [Chốt]

- Quản trị và HR bấm **"Khôi phục"** để đưa hồ sơ về danh sách: xóa trắng `archived_at`, `archived_by` và `archive_note`.
- Trạng thái giữ nguyên (thường là **Đã nghỉ**), dữ liệu không đổi. Tài khoản đăng nhập vẫn khóa.
- Ghi vào lịch sử hồ sơ.
- API: `POST /api/employees/{id}/restore`.

### 5.2 Nhận làm lại [Chốt]

Quản trị và HR dùng nút **"Nhận làm lại"** trên hồ sơ "Đã nghỉ" chưa lưu trữ. API: `POST /api/employees/{id}/rehire` với `{joinDate, departmentId?, position?, personType?}`.

**Điều kiện:**

- Ngày vào làm mới phải sau ngày làm việc cuối của lần trước. Nếu không, lỗi 400 `rehiredatebeforeleave`.
- Không có lịch nghỉ việc nào đang hẹn.

**Kết quả** (một giao dịch):

1. Tạo bản ghi **lần làm việc** (`employment_period`, kèm `_aud`) cho lần trước: ngày vào làm, ngày làm việc cuối, ngày nghỉ việc, hình thức, lý do và id của lịch sử nghỉ việc tương ứng.
2. Trên hồ sơ:
   - trạng thái chuyển thành **Đang làm việc**;
   - `joinDate` lấy ngày mới;
   - các trường nghỉ việc (`lastWorkingDay`, `terminationType`, `terminationReason`, `terminationDate`) được xóa trắng;
   - phòng ban, vị trí và loại hồ sơ cập nhật nếu có nhập.
3. **Giữ mã nhân viên cũ.** HR vẫn sửa tay được mã như bình thường.
4. **Hoàn tác nghỉ việc cũ** bị chặn từ đây: lỗi `undoconflict` với lý do "Nhân viên đã được nhận làm lại".
5. **Sổ phép:** dòng NGHI_VIEC đã đưa số dư về 0. Cộng phép tiếp tục theo ngày vào làm mới, kể cả quy tắc tháng vào làm (R7 của phần nghỉ phép). Thâm niên +1 ngày mỗi 5 năm tính từ ngày vào làm mới. "Số năm đóng BHXH trước khi vào DO IT" giữ nguyên để HR tự cập nhật.
6. **Tài khoản đăng nhập** đã gắn được mở lại, trừ tài khoản `admin` hệ thống.
7. Hợp đồng mới do HR thêm như bình thường. Hệ thống không tự tạo.

Tab "Lịch sử" của hồ sơ có thêm mục **"Các lần làm việc"**.

## 6. Xóa hẳn

### 6.1 Ai và khi nào [Chốt]

- **Chỉ tài khoản đăng nhập `admin`** (`IdNumbers.SYSTEM_ADMIN_LOGIN`) được xóa hẳn. Mọi người khác, kể cả các tài khoản có vai trò Quản trị, nhận lỗi 403 `deleterequiressystemadmin`.
- Chỉ xóa được hồ sơ **đang ở vùng lưu trữ**. Nếu không, lỗi 400 `deletenotarchived`.
- Bị chặn (lỗi 400 `deleteblocked`, kèm danh sách lý do) nếu người này còn có trong:
  - một kỳ lương chưa duyệt, hoặc bản nháp import bảng lương đang làm;
  - một lượt gửi phiếu lương đang chờ gửi.
- Không xóa được hồ sơ có gắn tài khoản `admin`.

### 6.2 Thao tác [Đề xuất]

- Hộp xác nhận bắt gõ lại **mã nhân viên** và nhập **lý do** (bắt buộc).
- API: `DELETE /api/employees/{id}` với `{confirmCode, reason}`. Nếu `confirmCode` không khớp mã, lỗi 400 `deleteconfirmmismatch`.
- Mọi bước chạy trong **một giao dịch**; lỗi ở bất kỳ bước nào thì không xóa gì. File tài liệu trên đĩa chỉ được xóa sau khi giao dịch thành công. Nếu xóa file lỗi thì ghi log, không báo lỗi cho người dùng.

### 6.3 Giữ lại [Chốt]

- **Dòng lương của các kỳ:** số tiền, `file_code` và `file_name` giữ nguyên như trong file lương. Liên kết `employee_id` đặt về trống: thêm changeset bỏ ràng buộc not-null của `payroll_line.employee_id`. Bảng lương, tổng lương, phiếu lương tổng hợp và file xuất Excel của kỳ cũ ra đúng số như trước. Người này hiện là "Mã – Họ tên (đã xóa hồ sơ)".
- **Nhật ký xóa** (`employee_deletion_log`, chỉ thêm dòng mới): người xóa, thời điểm, lý do, mã và họ tên. Nhật ký **không** lưu CCCD, tài khoản ngân hàng, địa chỉ hay dữ liệu cá nhân khác. Chỉ tài khoản `admin` xem được, ở mục Quản trị.

### 6.4 Xóa [Chốt]

**Dữ liệu của người này:**

- hồ sơ và mọi dữ liệu cá nhân trên hồ sơ;
- hợp đồng, thay đổi lương, phụ cấp, nơi làm việc, quyết định cử nhân sự, văn bản đã sinh;
- tài liệu: bản ghi và file;
- đơn nghỉ, đơn làm thêm, sổ phép, cùng các bản ghi thay đổi đơn do nghỉ việc;
- lịch sử nghỉ việc và các lần làm việc;
- lịch sử gửi phiếu lương;
- nhật ký xem dữ liệu nhạy cảm *của người này* (dòng ghi ai đã xem hồ sơ này);
- các dòng `_aud` (lịch sử thay đổi Envers) của mọi bản ghi trên;
- tài khoản đăng nhập đã gắn, trừ `admin`.

**Tham chiếu từ người khác:**

- `employee.manager` và `department.head` trỏ tới người này được đặt về trống.
- Đơn của người khác mà người này là người duyệt: `approver_employee_id` đặt về trống. `decided_by` lưu bằng chữ nên giữ nguyên.
- Gắn hồ sơ trong bản nháp import đã đóng: đặt về trống hoặc xóa dòng gắn.

**Hệ quả người dùng đã chấp nhận [Chốt]:** tổng hợp nghỉ phép và làm thêm giờ cho kế toán ở các tháng cũ sẽ **không còn người đã xóa**. Báo cáo lương chính thức là các dòng lương đã duyệt, và các dòng này giữ nguyên.

## 7. Quyền [Chốt cho xóa hẳn; Đề xuất cho phần còn lại]

| Việc | Quản trị | HR | `admin` hệ thống | Khác |
|---|---|---|---|---|
| Đưa vào lưu trữ, khôi phục, nhận làm lại | ✓ | ✓ | ✓ | 403 |
| Xem vùng lưu trữ | ✓ | ✓ | ✓ | 403 |
| Xóa hẳn, xem nhật ký xóa | 403 | 403 | ✓ | 403 |

Mọi kiểm tra quyền nằm ở backend. Giao diện chỉ ẩn nút.

## 8. Dữ liệu và mã nguồn [Đề xuất]

- **Liquibase:** chỉ thêm changeset mới, gồm:
  - `employee.archived_at/archived_by/archive_note` (thêm vào cả `employee_aud`);
  - bảng `employment_period` và `employment_period_aud`;
  - bảng `employee_deletion_log`;
  - changeset bỏ not-null ở `payroll_line.employee_id`.
- **Service:**
  - `EmployeeArchiveService`: lưu trữ, khôi phục, vùng lưu trữ, kiểm tra "hồ sơ tạo nhầm".
  - `EmployeeRehireService`: nhận làm lại.
  - `EmployeeDeletionService`: kiểm tra điều kiện, xóa theo thứ tự khóa ngoại, xóa `_aud`, xóa file sau commit, ghi nhật ký.
  - Truy vấn danh sách hiện có (`EmployeeQueryService`, các repository dùng cho ô chọn và nhắc việc) thêm điều kiện mặc định `archived_at is null`.
- **Cảnh giác:** mỗi bảng mới có khóa ngoại tới `employee` mà sau này được thêm vào cũng phải được `EmployeeDeletionService` xử lý. Một test kiểm tra bằng metadata cơ sở dữ liệu rằng mọi khóa ngoại trỏ tới `employee` đều nằm trong danh sách xử lý của dịch vụ xóa. Thiếu thì test đỏ.

## 9. Giao diện [Đề xuất]

- **Hồ sơ:**
  - nút "Đưa vào lưu trữ" (khi đủ điều kiện) và "Nhận làm lại" (khi Đã nghỉ, chưa lưu trữ);
  - hồ sơ đã lưu trữ có băng "Hồ sơ đã lưu trữ (dd/MM/yyyy, người lưu trữ)" kèm nút Khôi phục, và mọi nút sửa bị ẩn.
- **Trang "Nhân viên đã lưu trữ"** (§4). Hộp thoại "Xóa hẳn" gồm cảnh báo không hoàn tác được, liệt kê dữ liệu sẽ bị xóa và dữ liệu được giữ (dòng lương đã duyệt), ô gõ lại mã và ô lý do.
- **Bảng lương và phiếu lương kỳ cũ:** nhãn "Lưu trữ" hoặc "(đã xóa hồ sơ)" cạnh tên.
- Chữ tiếng Việt, ngày `dd/MM/yyyy`, vùng bấm ≥ 44px, theo màu và font canvas.

## 10. Kiểm thử [Đề xuất]

- **Lưu trữ:**
  - người Đã nghỉ thì lưu trữ được; người Đang làm việc có dữ liệu bị 400;
  - hồ sơ tạo nhầm lưu trữ thẳng được;
  - hồ sơ đã lưu trữ không còn trong danh sách, ô chọn, nhắc việc, xuất Excel, lịch nghỉ, số dư phép;
  - hồ sơ đã lưu trữ vẫn hiện trong bảng lương kỳ cũ;
  - API sửa trả 400 `employeearchived`.
- **Khôi phục:** dữ liệu trước và sau khi khôi phục giống hệt, kể cả số dư phép.
- **Nhận làm lại:**
  - trạng thái, `joinDate` mới và các trường nghỉ việc được xóa trắng;
  - có bản ghi lần làm việc; hoàn tác nghỉ việc cũ bị `undoconflict`;
  - phép cộng từ tháng vào làm mới;
  - tài khoản được mở lại;
  - ngày vào làm mới trước ngày làm việc cuối bị 400.
- **Xóa hẳn:**
  - Quản trị thường và HR nhận 403; hồ sơ chưa lưu trữ bị 400; người còn trong kỳ lương chưa duyệt bị 400; gõ sai mã bị 400.
  - Thành công:
    - tổng lương và file xuất Excel của kỳ đã duyệt giống hệt trước khi xóa;
    - dòng lương còn, với `employee_id` trống và tên như trong file;
    - không còn bản ghi con và dòng `_aud` của người này;
    - file tài liệu bị xóa;
    - manager và trưởng phòng của người khác được đặt về trống;
    - nhật ký xóa có đủ người xóa, thời điểm, lý do, mã và họ tên, không có CCCD.
  - Lỗi giữa chừng thì không mất gì.
- **Khóa ngoại:** test metadata ở §8.
- **Giao diện (Vitest):** các nút theo vai trò, hộp thoại xóa bắt gõ đúng mã, trang vùng lưu trữ.
