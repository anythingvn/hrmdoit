# DOIT HRM – KH4: Nhắc việc, Tổng quan, báo cáo

Ngày: 22/10/2026 · Trạng thái: chờ duyệt · Bổ sung cho spec đợt 1 (`2026-10-19-hrm-doit-dot1-design.md` §5.4, §5.5, §5.6)

Ký hiệu: **[Chốt]** người dùng đã quyết định; **[Đề xuất]** lựa chọn của người viết.

## 1. Mục tiêu

HR không còn phải mở sheet "Thông báo" trong Excel để biết việc đến hạn. Cụ thể:

- hệ thống tự nhắc hết hạn HĐ, hết thử việc, đến hạn xét lương, sinh nhật, qua trang Tổng quan và email mỗi sáng ngày làm việc;
- việc đã xử lý thì dừng nhắc;
- ban lãnh đạo xem nhanh tình hình nhân sự trên trang Tổng quan và tải báo cáo Excel.

**Thành công khi:**

- HĐ hết hạn trong 45 ngày tới xuất hiện trong Nhắc việc và trong email 7:30 của HR, Kế toán, Quản trị và quản lý trực tiếp;
- ký HĐ mới thì hết nhắc;
- báo cáo Excel tải về có số liệu khớp với hồ sơ và bảng lương đã lưu.

## 2. Nhắc việc

- **Lõi:** dùng `vn.doit.hr.reminder` có sẵn (`ReminderEngine`, `ReminderPolicy.defaults()`, `ReminderDigest`). Giữ nguyên các mốc nhắc trước và cách nhắc khi quá hạn:

  | Sự kiện | Nhắc trước | Khi quá hạn |
  |---|---|---|
  | Hết hạn HĐLĐ xác định thời hạn | 45, 30, 15, 7, 0 ngày | nhắc tiếp sau khi quá hạn |
  | Hết thử việc | 7, 3, 0 ngày | nhắc tiếp sau khi quá hạn |
  | Đến hạn xét lương | 30, 7 ngày | nhắc tiếp sau khi quá hạn |
  | Sinh nhật | 3, 0 ngày | không nhắc |

  Nhịp nhắc lại khi quá hạn lấy theo `ReminderPolicy.defaults()`. Các mốc được để ở cấu hình; đợt này chưa có màn hình để sửa.

- **Nguồn dữ liệu cho mỗi nhân viên đang làm việc:**
  - **Hợp đồng:** các HĐLĐ (`HDLD_XDTH`, `HDLD_KXDTH`) đã ký. HĐ tiếp theo đã ký thì HĐ trước hết nhắc.
  - **Thử việc:** HĐ thử việc (`HDTV`) gần nhất có ngày kết thúc. Đã ghi kết quả (mục 4) thì dừng nhắc.
  - **Xét lương:** lần điều chỉnh lương gần nhất (ngày hiệu lực, chu kỳ xét `reviewCycleMonths`, ngày hẹn xét lại `nextReviewOverride`).
  - **Sinh nhật:** ngày sinh trong hồ sơ.
- **Nhân viên đã nghỉ** (hoặc ngày nghỉ đã qua): không nhắc gì nữa.
- **Trang Tổng quan, khối "Nhắc việc sắp tới":**
  - liệt kê sự kiện trong 30 ngày tới và các việc quá hạn chưa xử lý;
  - lọc theo loại;
  - mỗi dòng có nhân viên, sự kiện, ngày, trạng thái (sắp đến / hôm nay / quá hạn N ngày), cùng nút "Mở hồ sơ", và nút xử lý nhanh tùy loại:
    - "Ghi kết quả thử việc";
    - "Hẹn xét lại";
    - "Thêm điều chỉnh lương".
- **Quyền:** HR, Kế toán, Quản trị, Ban giám đốc xem được Nhắc việc. Nhắc xét lương không hiện số tiền.

## 3. Email nhắc việc hằng ngày [Chốt]

- **Thời điểm gửi:** 7:30 (giờ Việt Nam), chỉ vào **ngày làm việc** (mục 5).
- **Người nhận [Chốt]:**
  - **HR, Kế toán, Quản trị:** mọi tài khoản đang hoạt động có vai trò này nhận **mọi** nhắc việc.
  - **Quản lý trực tiếp:** nhận nhắc về **nhân viên do mình quản lý**. Áp dụng cho các loại mà `ReminderPolicy` có người nhận `MANAGER`.
- **Địa chỉ email:**
  - tài khoản HR, Kế toán, Quản trị: email của tài khoản; trống thì lấy email trong hồ sơ gắn với tài khoản đó;
  - quản lý trực tiếp: email trong hồ sơ của họ, không cần có tài khoản.

  Không có địa chỉ thì bỏ qua người đó. Màn Quản trị liệt kê những người bị bỏ qua.
- **Nội dung:**
  - mỗi người nhận một email tổng hợp mỗi ngày (`ReminderDigest`);
  - email gửi quản lý chỉ nêu sự kiện và ngày (vd "HĐLĐ của Trần Minh Quân hết hạn 25/10"), **không có số tiền**;
  - email gửi HR, Kế toán, Quản trị cũng không có số tiền, cho thống nhất.
- **Không gửi trùng, không bỏ sót:**
  - một lần nhắc chỉ được ghi vào nhật ký nhắc (`reminder_log`) **sau khi** gửi thành công;
  - gửi lỗi thì lần chạy sau (ngày làm việc kế tiếp) gửi lại;
  - máy chủ tắt vài ngày thì lần chạy sau chỉ gửi mốc gấp nhất (lõi đã làm);
  - quá 7:30 mà ứng dụng mới khởi động thì chạy bù một lần trong ngày (giống job nghỉ việc).
- **Chưa cấu hình SMTP** (`SPRING_MAIL_HOST` trống hoặc `localhost`): không gửi, không ghi nhật ký; màn Quản trị cảnh báo. Nhắc việc trên trang Tổng quan vẫn hoạt động.
- **Quản trị:** có nút **Gửi thử ngay** (gửi cho chính mình bản tổng hợp hôm nay, không ghi nhật ký) và xem **lần gửi gần nhất** (thời điểm, số người nhận, lỗi nếu có).

## 4. Ghi kết quả để dừng nhắc

- **Thử việc:**
  - HĐ thử việc có thêm **Kết quả thử việc** (`DAT` / `KHONG_DAT`) và **Ngày ghi kết quả**;
  - HR ghi trên tab Hợp đồng & lương, hoặc từ nút nhanh ở Nhắc việc;
  - có kết quả thì dừng nhắc "hết thử việc" và "chưa có kết quả thử việc";
  - kết quả "Không đạt" không tự cho nghỉ việc; HR vẫn làm thủ tục nghỉ việc như cũ.
- **Xét lương:**
  - **Đã điều chỉnh:** thêm một lần điều chỉnh lương (đã có).
  - **Hẹn xét lại:** nhập "Hẹn xét lại ngày …" trên lần điều chỉnh lương gần nhất (trường `nextReviewOverride` đã có trong dữ liệu, nay đưa lên giao diện).
- **Hết hạn HĐ:** ký HĐ mới; hoặc cho nghỉ việc (hết hạn, không tái ký).
- **Quyền:** chỉ HR và Quản trị ghi kết quả.

## 5. Lịch làm việc và ngày lễ [Chốt]

- **Ngày làm việc trong tuần:** mặc định **thứ Hai – thứ Sáu**, thứ Bảy và Chủ nhật nghỉ. Quản trị đổi được trên màn hình.
- **Ngày lễ:** khai báo **theo năm** (ngày, tên ngày lễ). HR và Quản trị thêm, sửa, xóa được. Có nút "Chép từ năm trước" để làm nhanh các ngày lễ cố định.
- **Mốc nhắc rơi vào ngày nghỉ** (cuối tuần, ngày lễ): dời về ngày làm việc liền trước (lõi đã làm qua `WorkCalendar`).
- **Không tác động ngược:** lịch đổi chỉ ảnh hưởng các lần nhắc từ đó về sau, nhắc đã gửi giữ nguyên trong nhật ký.

## 6. Trang Tổng quan

Trang Tổng quan là trang mặc định sau khi đăng nhập cho HR, Kế toán, Quản trị, Ban giám đốc. Nhân viên thường vẫn vào "Hồ sơ của tôi".

- **Ô chỉ số:**
  - **Tổng nhân sự:** đang làm việc, không tính người hợp đồng dịch vụ, như spec đợt 1.
  - **Đang thử việc:** HĐ thử việc đang hiệu lực.
  - **HĐ hết hạn trong 30 ngày.**
  - **Chi phí lương kỳ gần nhất:** tổng chi phí nhân viên theo phiên bản chính thức của kỳ lương gần nhất. Hiện kèm kỳ và trạng thái (Nháp / Đã duyệt). **Chỉ Kế toán và Quản trị thấy** [Chốt spec đợt 1]; HR và Ban giám đốc không thấy ô này, API cũng không trả số này cho họ.
- **Nhắc việc sắp tới:** mục 2.
- **Biến động nhân sự 12 tháng gần nhất:** biểu đồ cột, gồm số người vào, số người nghỉ, và số người cuối tháng (dạng đường). Dùng `HeadcountReport.monthly` của lõi.

## 7. Báo cáo Excel

- **Màn "Báo cáo":** chọn từ tháng đến tháng (mặc định 12 tháng gần nhất), nhóm chi phí theo phòng ban hoặc vị trí, rồi bấm **Tải báo cáo Excel**. Dùng `HrReport` và `ReportExcelWriter` của lõi.
- **Sheet "Tổng hợp", "Biến động", "Nghỉ việc":** HR, Kế toán, Quản trị, Ban giám đốc đều tải được.
- **Sheet "Chi phí lương":** **chỉ khi người tải là Kế toán hoặc Quản trị.** HR và Ban giám đốc tải báo cáo không có sheet này, và trong file không có số tiền nào.
- **Chi phí theo tháng:** lấy từ phiên bản chính thức của từng kỳ lương đã lưu, không tính lại. Kỳ chưa có bảng lương thì để trống, không coi là 0.
- **Dữ liệu nhân viên:**
  - ngày vào: `joinDate`;
  - ngày nghỉ: ngày làm việc cuối;
  - loại nghỉ: theo hồ sơ;
  - phòng ban, vị trí: hiện tại.

  Người hợp đồng dịch vụ không tính vào nhân sự và biến động, nhưng có trong chi phí lương.

## 8. Lưu trữ

| Bảng | Nội dung |
|---|---|
| `reminder_log` | `event_key`, `step`, `sent_at` (khóa chính `event_key` + `step`), như README lõi |
| `reminder_run` | mỗi lần chạy gửi email: ngày, bắt đầu/kết thúc, số người nhận, số nhắc, lỗi (nếu có), chạy tay hay tự động |
| `work_calendar_setting` | 1 dòng: các ngày làm việc trong tuần |
| `holiday` | `date` (duy nhất), `name` |
| `employment_contract` | thêm `probation_result` (`DAT`/`KHONG_DAT`), `probation_result_date` |

Mọi bảng có `_aud`, trừ `reminder_log` và `reminder_run` (nhật ký kỹ thuật). Changeset Liquibase chỉ thêm mới.

## 9. Phân quyền

| | Quản trị | HR | Kế toán | Ban giám đốc | Nhân viên |
|---|---|---|---|---|---|
| Trang Tổng quan, Nhắc việc, biểu đồ | ✔ | ✔ | ✔ | ✔ | — |
| Ô chi phí lương kỳ gần nhất | ✔ | ẩn | ✔ | ẩn | — |
| Ghi kết quả thử việc, hẹn xét lại | ✔ | ✔ | — | — | — |
| Lịch làm việc trong tuần | sửa | xem | — | — | — |
| Ngày lễ | sửa | sửa | xem | — | — |
| Báo cáo Excel (không có chi phí) | ✔ | ✔ | ✔ | ✔ | — |
| Sheet chi phí lương trong báo cáo | ✔ | — | ✔ | — | — |
| Gửi thử email, xem lần gửi, cảnh báo SMTP | ✔ | — | — | — | — |

## 10. Kiểm thử

- **Nhắc việc:**
  - HĐ hết hạn sau 45 ngày → có nhắc hôm nay;
  - ký HĐ mới → hết nhắc;
  - thử việc có kết quả → hết nhắc;
  - hẹn xét lại → nhắc theo ngày hẹn.
- **Lịch:**
  - mốc rơi vào thứ Bảy → nhắc thứ Sáu;
  - mốc rơi vào ngày lễ thứ Hai → nhắc thứ Sáu trước đó;
  - thứ Bảy không chạy gửi email.
- **Email** (SMTP giả trong test):
  - người nhận đúng: HR, Kế toán, Quản trị đều nhận; quản lý A chỉ nhận về nhân viên của A;
  - không có số tiền trong nội dung;
  - gửi lỗi → không ghi nhật ký, lần sau gửi lại; gửi thành công → không gửi trùng;
  - chưa cấu hình SMTP → không gửi.
- **Tổng quan:**
  - các ô chỉ số đúng trên dữ liệu dựng sẵn;
  - HR và Ban giám đốc không nhận số chi phí lương.
- **Báo cáo:**
  - Kế toán tải có 4 sheet, chi phí khớp tổng bảng lương đã lưu;
  - HR tải có 3 sheet.

## 11. Không làm ở KH4

- Gửi phiếu lương.
- Nhắc qua kênh khác ngoài email.
- Sửa mốc nhắc trên màn hình (đã để trong cấu hình).
- Báo cáo tùy biến.
