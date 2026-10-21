# DOIT HRM – Đợt 2: Phiếu lương cho nhân viên

Ngày: 24/10/2026 · Trạng thái: chờ duyệt · Bổ sung cho spec đợt 1 (`2026-10-19-hrm-doit-dot1-design.md` §3 "Gửi phiếu lương cho nhân viên") và KH3 (`2026-10-21-hrm-kh3-bang-luong-design.md`)

Ký hiệu: **[Chốt]** người dùng đã quyết định; **[Đề xuất]** lựa chọn của người viết.

## 1. Mục tiêu

Kế toán không còn phải lọc tab "Phiếu lương" trong Excel cho từng người rồi gửi tay. Cụ thể:

- sau khi Quản trị duyệt kỳ lương, Kế toán bấm một nút để gửi mỗi người một email kèm phiếu lương PDF có mật khẩu;
- phiếu có cùng bố cục và cùng số với tab "Phiếu lương" của file bảng lương;
- nhân viên có tài khoản xem lại phiếu của mình ở **Hồ sơ của tôi**.

**Thành công khi:**

- với file mẫu ẩn danh tháng 06/2026, phiếu của từng người có dòng "Thực lĩnh" bằng cột "Lương thực nhận" của người đó;
- mỗi người nhận đúng một email, mở PDF bằng 6 số cuối CCCD của mình;
- không ai xem được phiếu của người khác, và thân email không có số tiền.

## 2. Nội dung phiếu [Chốt]

Theo tab "Phiếu lương" của file Excel bảng lương. Số liệu lấy từ các trường đã lưu của **phiên bản chính thức** của kỳ (`PayrollField`).

**Phần đầu:** tên công ty (cấu hình `APPLICATION_COMPANY_NAME`), "PHIẾU LƯƠNG THÁNG MM/YYYY", họ và tên, mã số nhân viên (theo hồ sơ), số ngày công chuẩn trong tháng (ký hiệu (1)). Đơn vị tiền tệ: Việt Nam đồng.

**Các dòng** (giữ nguyên thứ tự, ký hiệu và công thức của Excel):

| Ký hiệu | Nhãn trên phiếu | Giá trị |
|---|---|---|
| a = a1 + a2 | Mức lương gộp hàng tháng, bao gồm: | a1 + a2 |
| a1 | - Mức lương theo HĐLĐ | `LUONG_CO_BAN` |
| a2 | - Phụ cấp lương tính đóng BHXH | `PHU_CAP_DONG_BH` |
| b | Số ngày công thực tế | `NGAY_CONG_THUC_TE` |
| c = a1/(1)×b + a2 | Tổng lương gộp trong tháng | a1 ÷ `NGAY_CONG_CHUAN` × b + a2 |
| d | Thưởng dự án **[Chốt]** (Excel ghi "13th salary") | `THUONG_DU_AN` |
| e = sum(e1:e6) | Thu nhập khác, bao gồm: | e1 + … + e6 |
| e1 | - Lương chuyên cần | `PC_CHUYEN_CAN` |
| e2 | - Phụ cấp trách nhiệm | `PC_TRACH_NHIEM` |
| e3 | - Phụ cấp xăng xe | `PC_XANG_XE` |
| e4 | - Tiền thưởng KPI | `THUONG_KPI` |
| e5 | - Tiền làm thêm giờ chịu thuế | `OT_CHIU_THUE` |
| e6 | - Trợ cấp thôi việc | `TRO_CAP_THOI_VIEC` |
| f | Tiền lương ngày nghỉ phép chưa sử dụng | `PHEP_CHUA_SU_DUNG` |
| g = sum(c:f) | Tổng thu nhập | c + d + e + f |
| h = h1 + h2 | Khoản không tính thuế, bao gồm: | h1 + h2 |
| h1 | - Phụ cấp điện thoại **[Chốt]** | `THUA_THIEU` (cột "Các khoản thừa/thiếu", như Excel) |
| h2 | - Làm thêm giờ không chịu thuế | `OT_KHONG_CHIU_THUE` |
| i | Bảo hiểm xã hội | `BHXH_NLD` |
| j | Bảo hiểm y tế | `BHYT_NLD` |
| k | Bảo hiểm thất nghiệp | `BHTN_NLD` |
| l | Giảm trừ gia cảnh cho bản thân | `GIAM_TRU_BAN_THAN` |
| m | Giảm trừ gia cảnh cho người phụ thuộc | `GIAM_TRU_NPT` |
| n = g − h − i − j − k − l − m | Thu nhập chịu thuế | như Excel: g − i − j − k − l − m, âm thì 0 |
| o = o1 + o2 | Thuế thu nhập cá nhân phải nộp | o1 + o2 |
| o1 | - Thuế TNCN trong tháng | `THUE_TNCN` |
| o2 | - Khấu trừ bổ sung thuế TNCN tháng trước | `QUYET_TOAN_THUE` |
| p | Các khoản giảm trừ khác | `GIAM_TRU_KHAC` |
| q = g − i − j − k − o + h − p | Thực lĩnh | g + h − i − j − k − o − p |

- **Dòng không có ngày công chuẩn** (thực tập, HĐ dịch vụ: file chỉ ghi "Lương gộp trong tháng"): c = `LUONG_GOP`; các dòng a, a1, a2, b và số ngày công chuẩn để trống. Đã kiểm trên file mẫu ẩn danh: với quy tắc này, q bằng "Lương thực nhận" ở cả 14 dòng.
- Số tiền làm tròn đến đồng khi in (c có thể lẻ); phép cộng dùng số chưa làm tròn, như Excel.
- **Bỏ dòng "Số ngày phép chưa sử dụng"** [Chốt]: hệ thống không nhập tab "Theo dõi ngày phép". Dòng f (tiền) vẫn giữ.
- Không in các cột chi phí của công ty (BH công ty đóng, KPCĐ, tổng chi phí).
- **Phiếu lệch bảng lương:** q tính ra khác `THUC_NHAN` của dòng đó quá 1 đồng (vd ô trong file bị gõ đè) → không gửi người đó, hiện lý do "Phiếu lệch bảng lương N đồng" để Kế toán sửa file và import lại. [Đề xuất]

**Ghi chú cuối phiếu** [Chốt]: in theo mốc luật đã lưu cùng phiên bản bảng lương (`rules_snapshot`), không lấy chữ cũ trong Excel. Luật đổi không tác động ngược: kỳ cũ in mức của kỳ cũ.

1. Tỉ lệ người lao động đóng: BHXH 8%, BHYT 1,5%, BHTN 1%.
2. Mức lương đóng BHXH, BHYT tối đa {trần BHXH} đồng/tháng; mức lương đóng BHTN tối đa {trần BHTN} đồng/tháng.
3. Giảm trừ gia cảnh: bản thân {giảm trừ bản thân} đồng/tháng, người phụ thuộc {giảm trừ NPT} đồng/người/tháng.
4. Căn cứ: {căn cứ văn bản của mốc luật} — chỉ in khi bản chụp có căn cứ.

Bản chụp luật của phiên bản hiện chưa lưu căn cứ văn bản. Từ đợt này, lúc xác nhận phiên bản mới, bản chụp lưu thêm căn cứ văn bản của mốc luật (`legalBasis`). Phiên bản đã lưu trước đó không sửa, và phiếu của chúng không có dòng 4.

**Mẫu Word "Phiếu lương"** [Đề xuất]: thêm loại mẫu `PHIEU_LUONG` trong **Hợp đồng & mẫu → Mẫu văn bản**, có mẫu gốc dựng theo bố cục Excel. Kế toán và Quản trị sửa câu chữ, tải mẫu mới, dùng lại bản cũ như các loại mẫu khác. Thẻ: thông tin người (`hoTen`, `maNhanVien`), `kyLuong`, `tenCongTy`, `ngayCongChuan`, một thẻ cho mỗi ký hiệu (`a`, `a1`, … `q`) và các dòng ghi chú (`ghiChu1` … `ghiChu4`). PDF sinh qua dịch vụ PDF có sẵn (Gotenberg).

## 3. Gửi email

- **Ai gửi** [Đề xuất]: **Kế toán** hoặc **Quản trị**, nút **Gửi phiếu lương** trên trang kỳ lương; chỉ hiện khi kỳ **Đã duyệt**. Phiếu theo phiên bản đã duyệt.
- **Người nhận** [Đề xuất]: mọi người có dòng trong phiên bản đó, kể cả thực tập và HĐ dịch vụ, tới **email trên hồ sơ** (không dùng email tài khoản).
- **Mật khẩu PDF** [Chốt]: 6 chữ số cuối của số CCCD trên hồ sơ. PDF mã hóa AES-256 bằng PDFBox (đã có trong dự án); quyền in và sao chép giữ nguyên.
- **Không gửi được:** thiếu email hoặc email `@localhost`, CCCD ít hơn 6 chữ số, phiếu lệch bảng lương, dịch vụ PDF không chạy, máy chủ mail từ chối. Người đó có trạng thái **Lỗi** kèm lý do; những người khác vẫn được gửi.
- **Email:** từ `JHIPSTER_MAIL_FROM` (email công ty). Tiêu đề "[DOIT] Phiếu lương tháng MM/YYYY". Thân: lời chào theo họ tên, "phiếu lương tháng MM/YYYY đính kèm", "mật khẩu mở file là 6 số cuối CCCD của bạn", liên hệ Kế toán khi có thắc mắc. **Không có số tiền.** Tệp: `PhieuLuong_MM-YYYY_<mã NV>.pdf`.
- **Chạy nền:** bấm gửi thì máy chủ ghi danh sách cần gửi rồi gửi lần lượt ở luồng nền (mỗi kết nối mail có giới hạn 10 giây như nhắc việc); trang kỳ lương tự cập nhật tiến độ.
- **Bảng tình trạng** trên trang kỳ: mỗi người một dòng — email, trạng thái (Chưa gửi / Đang gửi / Đã gửi lúc … / Lỗi: lý do / Theo phiên bản cũ), nút **Gửi lại** từng người; nút **Gửi cho người chưa nhận** (gồm người lỗi và người chưa gửi).
- **Không gửi trùng:** người đã gửi thành công phiếu của phiên bản đó thì nút gửi chung bỏ qua; muốn gửi lại phải bấm **Gửi lại** trên dòng của người đó.
- **Kỳ mở lại rồi duyệt phiên bản mới:** các lượt đã gửi giữ nguyên trong nhật ký và hiện **Theo phiên bản cũ**; người đó tính là chưa nhận phiếu của phiên bản mới. Hệ thống không tự gửi.
- **Chưa cấu hình SMTP** (`SPRING_MAIL_HOST` trống hoặc `localhost`): nút gửi báo "Chưa cấu hình máy chủ gửi email", không tạo lượt gửi.

## 4. Nhân viên tự xem

- **Hồ sơ của tôi** có khối **Phiếu lương**: các kỳ đã gửi thành công phiếu cho người đó (phiên bản đã gửi gần nhất), mới nhất trước; nút **Tải PDF**.
- PDF tải ở đây **không đặt mật khẩu** (người xem đã đăng nhập) và sinh lại từ dữ liệu đã lưu, cùng nội dung với bản đã gửi.
- Chỉ xem được phiếu của hồ sơ gắn với tài khoản đang đăng nhập; mọi vai trò có hồ sơ gắn tài khoản đều dùng được.
- Mỗi lần tải được ghi vào nhật ký truy cập dữ liệu nhạy cảm (`SensitiveAccessLog`) như tải hợp đồng.
- Kế toán, Quản trị tải được phiếu (không mật khẩu) của bất kỳ ai trong kỳ từ bảng tình trạng, để kiểm tra trước khi gửi; cũng ghi nhật ký.

## 5. Lưu trữ

- **Không lưu file PDF.** Phiếu sinh lại từ dòng lương, bản chụp luật và mẫu phiếu đang dùng lúc sinh.
- Bảng mới `payslip_delivery` (thêm bằng changeset mới, có bảng `_aud`):
  `id`, `period_id`, `version`, `employee_id`, `email` (địa chỉ đã gửi), `status` (`PENDING`, `SENT`, `FAILED`), `error` (≤ 500 ký tự), `requested_by`, `requested_at`, `sent_at`.
  Mỗi lần bấm gửi hoặc gửi lại tạo dòng mới; trạng thái hiện của một người là dòng mới nhất của người đó trong kỳ.
- `rules_snapshot` của phiên bản mới có thêm trường `legalBasis`; phiên bản cũ đọc được khi thiếu trường này.

## 6. Phân quyền

| Việc | Quản trị | Kế toán | HR | BGĐ | Nhân viên |
|---|---|---|---|---|---|
| Gửi, gửi lại, xem tình trạng gửi | có | có | không | không | không |
| Tải phiếu của người khác (kiểm tra) | có | có | không | không | không |
| Tải phiếu của chính mình (Hồ sơ của tôi) | có | có | có | có | có |
| Sửa mẫu Phiếu lương | có | có | không | không | không |

HR không thấy số lương ở đâu (giữ nguyên quy tắc KH3).

## 7. Kiểm thử

- **Lõi:** tính các dòng a → q từ một dòng lương; với cả 14 dòng của file mẫu ẩn danh (gồm 3 dòng không có ngày công chuẩn), q bằng `THUC_NHAN` của dòng đó; n âm thì in 0; ghi chú lấy đúng số từ bản chụp luật, không có căn cứ thì không có dòng 4.
- **Mẫu:** mẫu gốc `PHIEU_LUONG` không có thẻ bị cắt hoặc thẻ lạ; sinh xong không còn `{{`.
- **PDF:** mở được bằng đúng 6 số cuối CCCD, mở bằng mật khẩu khác thì lỗi; PDF ở Hồ sơ của tôi mở không cần mật khẩu.
- **Gửi (IT, JavaMailSender giả):**
  - mỗi người một email đúng địa chỉ hồ sơ, có tệp PDF, thân không chứa số tiền của người đó;
  - một người lỗi gửi thì những người khác vẫn `SENT`; **Gửi cho người chưa nhận** chỉ gửi lại người lỗi;
  - thiếu email, thiếu CCCD, phiếu lệch → `FAILED` có lý do, không gọi máy chủ mail;
  - kỳ chưa duyệt → 400; HR, BGĐ, Nhân viên gọi API gửi → 403;
  - mở lại kỳ, duyệt phiên bản 2 → người đã nhận hiện "Theo phiên bản cũ".
- **Tự xem:** nhân viên A tải được phiếu kỳ đã gửi của mình; tải phiếu của B hoặc kỳ chưa gửi → 404; có dòng nhật ký truy cập.
- **Giao diện (Vitest):** nút gửi chỉ hiện khi kỳ Đã duyệt; bảng tình trạng hiện lý do lỗi; khối Phiếu lương ở Hồ sơ của tôi gọi đúng API tải.
- **Thử thật:** gửi bằng máy chủ mail công ty tới một hộp thư thử, mở PDF bằng mật khẩu.

## 8. Không làm ở đợt này

- Phiếu lương cho kỳ chưa duyệt, hoặc gửi tự động khi duyệt.
- Nhập tab "Theo dõi ngày phép" và số ngày phép còn lại (thuộc phần Nghỉ phép).
- Nhân viên phản hồi/khiếu nại phiếu lương trong ứng dụng.
- Gửi qua kênh khác email (Zalo, SMS).
