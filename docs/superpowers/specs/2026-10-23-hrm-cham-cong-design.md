# HRM DO IT – Chấm công (gõ giờ vào/ra, chốt công, ngày công cho bảng lương)

Ngày: 23/10/2026 · Trạng thái: chờ duyệt

## 1. Mục đích

Hiện nay quản lý chấm công bằng tay, chỉ đánh dấu có mặt hoặc vắng. Kế toán tự tính ngày công trong file Excel bảng lương.

Phần này đưa chấm công vào hệ thống:
- **Gõ giờ:** người có quyền gõ giờ vào/ra mỗi ngày.
- **Tính công:** hệ thống tự tính đi muộn, nửa ngày và công từng ngày.
- **Chốt công:** HR/Quản trị chốt bảng công cuối tháng.
- **Bảng lương:** kế toán tải file bảng lương đã có sẵn ngày công từ bảng công đã chốt, điền phần còn lại rồi tải lên như hiện nay.

Dữ liệu được tổ chức sẵn để sau này app nhân viên tự chấm công (GPS/WiFi) mà không phải làm lại bảng công.

**Thành công khi:**
- Trưởng nhóm gõ giờ cho cả nhóm trong một màn, hệ thống tính công ngay.
- HR rà và chốt tháng trên một bảng.
- Kế toán không phải gõ lại ngày công.
- Sau khi chốt, số công tháng đó không đổi nữa, kể cả khi sửa cài đặt giờ làm sau này.

## 2. Phạm vi

**Làm:**
- Cài đặt giờ làm chung của công ty, có ngày bắt đầu áp dụng.
- Giờ làm riêng theo từng khách hàng onsite.
- Trưởng nhóm onsite.
- Gõ giờ vào/ra: màn theo ngày cho trưởng nhóm, bảng tháng cho HR.
- Tính công.
- Tự điền các ngày nghỉ, công tác, lễ đã duyệt.
- Chốt và mở lại chốt.
- Xem bảng công theo quyền.
- File bảng lương có sẵn ngày công.
- Đối chiếu ngày công khi nhập bảng lương.

**Không làm (đợt này):**
- App nhân viên tự chấm và kiểm tra GPS/WiFi.
- Giải trình do nhân viên gửi.
- Máy chấm công vân tay.
- Ca làm khác nhau trong công ty (dùng chung một giờ làm).
- Hệ thống tự tính lại lương, thuế, bảo hiểm từ ngày công. Kế toán vẫn tính trong Excel (mức 2 đã chọn, xem mục 4).

**Không được để xảy ra (anti-goal):**
- Sửa cài đặt giờ làm làm thay đổi công của tháng đã chốt hoặc đã trả lương.
- Bảng công đã chốt bị sửa trong khi bảng lương tháng đó đã duyệt.

## 3. Quy tắc nghiệp vụ (đã chốt với người dùng)

### 3.1 Giờ làm
- **Cả công ty dùng chung một bộ giờ làm**, cài đặt được trong màn Cài đặt:
  - giờ vào, giờ ra, nghỉ trưa (mặc định 08:00–17:00, nghỉ trưa 12:00–13:00);
  - số phút được phép muộn (mặc định 5, theo lõi hiện có);
  - **mốc muộn tính nửa ngày** (mặc định 90 phút).
- **Thứ Bảy, Chủ nhật và ngày lễ:** lấy theo cài đặt ngày làm việc và danh sách ngày lễ hiện có (`WorkCalendarService`), không làm cài đặt mới.
- **Người onsite** chấm theo giờ của khách hàng. Mỗi nơi làm `WorkSite` loại `KHACH_HANG` có bộ giờ riêng (giờ vào, giờ ra, nghỉ trưa). Bỏ trống thì dùng giờ công ty.
  - Một người tính là onsite tại khách hàng X vào ngày D khi có `WorkAssignment` loại `KHACH_HANG`, nơi làm X, còn hiệu lực ngày D.
- **Không áp dụng ngược:** mỗi bộ giờ (công ty và từng khách hàng) lưu kèm ngày bắt đầu áp dụng. Công ngày D tính theo bộ giờ có hiệu lực vào ngày D. Không sửa được bộ giờ đã có hiệu lực cho tháng đã chốt.

### 3.2 Tính công một ngày làm việc
- **Giờ vào** = lần chấm vào sớm nhất trong ngày; **giờ ra** = lần chấm ra muộn nhất trong ngày.
- **Đúng giờ hoặc muộn trong mức cho phép:** 1 công.
- **Muộn quá số phút cho phép nhưng chưa quá mốc 90 phút:** vẫn **1 công**. Ghi số phút muộn và tính 1 lần đi muộn để thống kê.
- **Muộn quá 90 phút** (so với giờ vào của bộ giờ áp dụng; với khách hàng làm từ 08:30 là sau 10:00): **0,5 công**.
- **Thiếu giờ vào hoặc thiếu giờ ra:** chưa tính công, đánh dấu đỏ "thiếu giờ".
- **Không có lần chấm nào và không có đơn nghỉ/công tác:**
  - ngày đã qua: ô "chưa nhập" (đỏ);
  - người gõ có thể đánh dấu **Vắng** (0 công, ghi rõ là vắng không phép).
- Làm ngoài giờ không tự tính thêm; làm thêm vẫn đi qua đơn làm thêm giờ hiện có.
- Đây là thay đổi so với lõi `Timesheet`: lõi hiện tính công theo tỷ lệ giờ làm. Phải đổi sang quy tắc ngưỡng ở trên (xem mục 6).

### 3.3 Ô tự điền (không cần gõ)
- **Đơn nghỉ đã duyệt:**
  - phép năm, nghỉ bù, nghỉ có lương theo luật: tính công có lương theo quy tắc nghỉ phép hiện có;
  - nghỉ không lương, nghỉ ốm: ghi loại nghỉ, 0 công đi làm.
- **Công tác** (`WorkAssignment` loại `CONG_TAC`): 1 công, không cần gõ giờ.
- **Ngày lễ:** ghi "Lễ".
- **Ngày nghỉ hằng tuần** (thứ Bảy, Chủ nhật): ghi "Nghỉ".
- **Ngày trước ngày vào làm hoặc sau ngày làm việc cuối:** để trống, không tính.
- **Đơn nghỉ nửa ngày** (buổi sáng/chiều theo `DayPart`): nửa còn lại vẫn phải gõ giờ. Xem 3.2b.

### 3.2b Nửa ngày nghỉ + nửa ngày làm *(quyết định của người viết, cần xác nhận khi đọc)*
- **Nghỉ buổi sáng:** giờ vào chuẩn của ngày đó là giờ hết nghỉ trưa (13:00). Có giờ vào/ra buổi chiều thì được 0,5 công làm cộng 0,5 công nghỉ.
- **Nghỉ buổi chiều:** ra từ 12:00 là đủ.
- Đi muộn buổi còn lại chỉ ghi để thống kê, không trừ thêm, vì đã là nửa công.

### 3.4 Ai gõ, ai xem
| Vai trò | Gõ/sửa giờ | Xem | Chốt / mở lại |
|---|---|---|---|
| Trưởng nhóm (là `manager` của nhân viên) | Người trong nhóm mình, ngày từ đầu tháng chưa chốt đến hôm nay | Nhóm mình | – |
| Trưởng nhóm onsite (gán theo từng khách hàng, có thể nhiều người) | Người đang onsite ở khách hàng đó vào đúng ngày đó | Những người đó | – |
| HR | Cả công ty | Cả công ty | Có |
| Quản trị | Cả công ty | Cả công ty | Có |
| Trưởng phòng, Giám đốc | – | Phòng mình / toàn công ty | – |
| Kế toán | – | Bảng công đã chốt | – |
| Nhân viên | – | Bảng công tháng của mình | – |

- Không ai gõ giờ cho chính mình, kể cả HR. *(Theo lõi `AttendanceCorrections`: "không ai tự duyệt cho mình". Quyết định của người viết.)*
- Không gõ giờ cho thời điểm chưa tới.
- Mọi lần gõ, sửa, xóa đều lưu người làm, thời điểm và giá trị cũ (Envers, như các bảng khác).
- Hồ sơ đã lưu trữ không gõ được (guard hiện có). Thực tập sinh và HĐ dịch vụ: xem mục 9.

### 3.5 Chốt công
- **Chốt:** HR hoặc Quản trị chốt theo tháng, cho toàn công ty.
  - Còn ô đỏ (thiếu giờ, chưa nhập) thì hệ thống hiện danh sách và hỏi lại trước khi chốt. Chốt vẫn được; ô đỏ tính 0 công.
- **Sau khi chốt:**
  - Không ai sửa giờ của tháng đó.
  - Hệ thống lưu ảnh chụp số công từng người: công chuẩn, công thực tế, số lần và số phút đi muộn, số ngày nửa công, số ngày vắng, ngày nghỉ theo loại.
  - Kế toán và bảng lương đọc ảnh chụp này, không tính lại.
- **Mở lại chốt:** chỉ khi **kỳ lương của tháng đó chưa được duyệt**. Kỳ lương đã duyệt thì bảng công tháng đó khóa hẳn; điều chỉnh làm ở kỳ sau. Mỗi lần chốt và mở lại đều ghi lịch sử (người, lúc, lý do khi mở lại).
- **Quy tắc tự khóa sau ngày 05** của lõi (`LockPolicy`) **không dùng**. Khóa là do HR chốt.

### 3.6 Ngày công cho bảng lương (mức 2)
- **Tải file:** khi tháng đã chốt, nút tải file bảng lương của tháng cho ra file mẫu hiện có (`PayrollTemplateWriter`), đã điền sẵn:
  - danh sách nhân viên (mã, họ tên, vị trí, ngày ký HĐLĐ);
  - cột "Số ngày công chuẩn trong tháng" và "Ngày công thực tế trong tháng" lấy từ ảnh chụp đã chốt.
  
  Kế toán điền phần còn lại rồi tải lên như hiện nay.
- **Đối chiếu khi tải lên:** nếu tháng đã chốt công, dòng nào có ngày công thực tế (hoặc công chuẩn) khác ảnh chụp sẽ có **cảnh báo** (không chặn), ví dụ "File ghi 22, bảng công đã chốt 21,5". Cảnh báo lưu cùng bản nhập.
- **Tháng chưa chốt:** tải lên bình thường, kèm một cảnh báo chung "Bảng công tháng này chưa chốt".
- **Công chuẩn** = số ngày làm việc trong tháng theo lịch công ty, trừ ngày lễ. *(Quyết định của người viết; với người vào làm hoặc nghỉ giữa tháng, công chuẩn vẫn là của cả tháng, như file Excel hiện nay.)*

## 4. Các cách đã cân nhắc

1. **Lưu từng lần chấm, công tính ra từ đó** (đã chọn).
   - Mỗi lần gõ là một bản ghi chấm công (`VAO`/`RA`, thời điểm, nguồn `GO_TAY`, người gõ).
   - Công ngày được tính từ các bản ghi và bộ giờ có hiệu lực ngày đó. Khi chốt thì chụp lại.
   - App sau này chỉ thêm nguồn `APP` (cùng trạng thái chờ duyệt/giải trình của lõi).
   - Đánh đổi: làm lâu hơn cách 2 một chút.
2. **Mỗi người mỗi ngày một dòng** (giờ vào, giờ ra, công). Nhanh nhất cho gõ tay, nhưng khi có app phải đổi cấu trúc và chuyển dữ liệu. Không chọn vì người dùng muốn chuẩn bị sẵn cho app.
3. **Trưởng nhóm nhập công bằng Excel.** Loại ngay: không tính được giờ vào/ra, và trái mục tiêu bỏ Excel ở khâu này.

**Về bảng lương:** đã cân nhắc 3 mức. (1) chỉ đối chiếu; (2) điền sẵn ngày công vào file và đối chiếu; (3) hệ thống tự tính lại lương, thuế, bảo hiểm. Người dùng chọn **mức 2**: kế toán vẫn giữ quyền với file lương, tránh hai nơi tính thuế lệch nhau.

## 5. Màn hình (đã xem bản phác)

- **Gõ theo ngày** (trưởng nhóm, trưởng nhóm onsite, HR):
  - Chọn ngày (và nhóm, nếu được xem nhiều nhóm).
  - Danh sách người, chia khối "Văn phòng (giờ công ty)" và từng khối "Onsite · <khách hàng> (giờ khách)".
  - Mỗi dòng: ô Vào, ô Ra, Công (tính ngay), Ghi chú (Đúng giờ / Muộn X' / Muộn → nửa công / Thiếu giờ / tên loại nghỉ tự điền). Có nút đánh dấu Vắng. Nút Lưu.
- **Bảng công tháng** (HR, Quản trị; trưởng phòng, giám đốc, kế toán chỉ xem):
  - Mỗi người một dòng, mỗi ô một ngày (giờ vào/ra hoặc nhãn tự điền), cột tổng công. Bấm ô để sửa nếu có quyền.
  - Lọc theo phòng/nhóm/khách hàng.
  - Trạng thái tháng (Đang mở / Đã chốt), nút Chốt / Mở lại chốt, nút tải Excel bảng công.
- **Bảng công của tôi** (nhân viên): bảng tháng của riêng mình, chỉ đọc.
- **Cài đặt:**
  - Mục "Giờ làm" trong cài đặt hiện có: bộ giờ công ty kèm ngày áp dụng, phút cho phép muộn, mốc nửa ngày.
  - Trong màn nơi làm onsite (`WorkSite` loại khách hàng): bộ giờ riêng và danh sách trưởng nhóm onsite.
- **Bảng lương:** nút "Tải file bảng lương có ngày công" ở kỳ lương; cảnh báo lệch ngày công trong kết quả kiểm tra khi nhập.

## 6. Hiện trạng code liên quan

- **Lõi `core/…/attendance`:** `WorkSchedule`, `Timesheet`, `AttendanceCorrections`, `PunchValidator` (GPS/WiFi, dùng sau).
  - Dùng lại lịch, ca, nghỉ trưa và tổng hợp tháng.
  - **Đổi** cách tính công ngày sang quy tắc ngưỡng (3.2).
  - Bỏ `LockPolicy` tự khóa.
- **App:**
  - `WorkCalendarService` (ngày làm việc, ngày lễ), `WorkSite` (`KHACH_HANG`), `WorkAssignment` (onsite/công tác theo ngày).
  - `LeaveRequest` (đơn nghỉ, `DayPart`), `Employee.manager` (trưởng nhóm), `PayrollPeriod` (trạng thái duyệt kỳ lương).
  - `PayrollTemplateWriter` (file mẫu), kiểm tra khi nhập bảng lương (`PayrollCheck`).
  - `ArchivedEmployeeGuard`.
- **Quy ước:**
  - Liquibase chỉ thêm, không sửa thay đổi cũ.
  - Envers `_aud` viết tay.
  - Lỗi nghiệp vụ là `EmployeeRuleException(key)` → 400 `error.<key>`, có câu tiếng Việt ở `global.json` và `ERROR_TEXT`.
  - Đồng hồ theo giờ Asia/Ho_Chi_Minh.
  - Bảng mới có FK tới `employee` phải được xử lý trong xóa hẳn (`HANDLED_TABLES`, test FK coverage) và lọc hồ sơ lưu trữ.

## 7. Để người làm tự quyết
- Tên bảng/cột, API cụ thể, cách chia task.
- Cách lưu ảnh chụp chốt: bảng riêng theo tháng × người.
- Excel bảng công tháng cho HR: cột nào, định dạng ra sao.
- Hiệu năng bảng tháng (khoảng 100 người × 31 ngày).

## 8. Quyết định của người viết (người dùng kiểm tra khi đọc)
- Nửa ngày nghỉ + nửa ngày làm (3.2b).
- Không ai gõ giờ cho chính mình.
- Ô chưa nhập vẫn cho chốt sau khi hỏi lại, tính 0 công.
- Công chuẩn tính theo cả tháng.
- Bỏ tự khóa sau ngày 05.
- Trưởng phòng, giám đốc xem bảng công phòng mình / toàn công ty; nhân viên xem bảng công của mình.

## 9. Câu hỏi còn mở
(Điền sau khi có kết quả rà soát của người làm.)
