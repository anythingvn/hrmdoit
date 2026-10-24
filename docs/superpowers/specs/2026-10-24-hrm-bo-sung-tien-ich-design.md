# HRM DO IT – Bổ sung tiện ích (ngày lễ Excel, phép thâm niên, mức luật định, mẫu văn bản, màn chấm công, tiện ích nhỏ)

Ngày 24/10/2026. Trạng thái: chờ duyệt. Làm trên một nhánh, theo một kế hoạch (người dùng chọn cách 1).

## Mục đích
Gom các yêu cầu nhỏ và vừa sau khi dùng thử, để HR và Quản trị đỡ thao tác tay. Riêng phép năm sẽ được cộng thêm theo thâm niên đúng Bộ luật Lao động.

## 1. Ngày lễ: xuất và nhập Excel
- Trong **Cài đặt → Lịch & giờ làm**, khối Ngày lễ của năm đang xem có thêm 2 nút:
  - **Xuất Excel**: file `ngay-le-<năm>.xlsx` gồm cột "Ngày" (dd/MM/yyyy) và cột "Tên ngày lễ".
  - **Nhập Excel**: cùng định dạng, có thể kèm nhiều năm trong một file.
- Nhập theo 2 bước:
  1. Tải file lên. Hệ thống hiện **bản xem trước**: dòng nào thêm mới, dòng nào đổi tên, ngày nào đang có mà file không có sẽ bị xoá, dòng nào lỗi (ngày sai, trùng ngày, tên trống).
  2. Người dùng bấm **Áp dụng**. Còn dòng lỗi thì không cho áp dụng.
- Phạm vi xoá chỉ gồm các năm có trong file. File chỉ có năm 2027 thì năm 2026 không bị đụng tới.
- **Không áp dụng ngược:** ngày nằm trong tháng đã chốt công, hoặc trong tháng có kỳ lương đã duyệt, thì không được thêm, đổi hay xoá. Những dòng đó báo lỗi "tháng đã chốt". Quy tắc này cũng áp dụng cho thêm, sửa, xoá từng ngày.
- Quyền giữ như hiện nay: HR và Quản trị được sửa ngày lễ.

## 2. Phép năm cộng thêm theo thâm niên (Điều 114 BLLĐ 2019)
- **Cài đặt → Nghỉ phép** có thêm 2 ô: **Chu kỳ thâm niên** (năm, mặc định 5, từ 1 đến 10) và **Số ngày cộng mỗi chu kỳ** (mặc định 1, từ 0 đến 5; để 0 là tắt). Chỉ Quản trị sửa, giống các cài đặt nghỉ phép khác.
- **Mức phép năm của một tháng** = `annualLeaveDays` của hồ sơ + ⌊thâm niên tính đến ngày cuối tháng ÷ chu kỳ⌋ × số ngày mỗi chu kỳ.
  - Phép được cộng hằng tháng như hiện nay: mỗi tháng 1/12 mức phép năm, đúng quy tắc làm tròn và cộng tháng đầu/cuối đang có.
  - Nhờ vậy phép tăng **dần từ tháng đủ năm**. Ví dụ: đủ 5 năm ngày 15/06/2027 thì từ tháng 06/2027 mỗi tháng được thêm 1/12 ngày.
- **Thâm niên** = tổng thời gian làm nhân viên chính thức (NHAN_VIEN), gồm lần làm hiện tại (từ `joinDate`) và các lần làm trước (`employment_period` có `person_type` là NHAN_VIEN hoặc null). Thời gian thử việc nằm trong lần làm nên được tính. Thực tập và HĐ dịch vụ không tính.
- **Không áp dụng ngược:** phép đã cộng thì giữ nguyên. Đổi cài đặt chỉ có hiệu lực từ lần cộng phép tiếp theo (tháng chưa cộng). Không tính bù cho các tháng trước.
- Hồ sơ nhân viên (tab Nghỉ phép) và màn Nghỉ phép của nhân viên hiện thêm dòng "Phép năm: 12 + 1 (thâm niên 5 năm) = 13 ngày".

## 3. Mức luật định: sửa và xoá mốc chưa dùng
- Vẫn **thêm mốc** như hiện nay, kể cả thêm trước cho tương lai.
- **Sửa** và **xoá** chỉ được với mốc **chưa dùng**:
  - tháng hiệu lực sau kỳ lương đã duyệt gần nhất;
  - chưa có phiên bản bảng lương nào ghi nhận đã dùng mốc đó.
- Mốc đã dùng thì khoá, có ghi chú "Đã dùng cho kỳ lương đã duyệt – không sửa".
- Khi sửa, tháng hiệu lực mới cũng phải sau kỳ lương đã duyệt gần nhất.
- Không được xoá mốc cuối cùng còn lại, để luôn có ít nhất một mốc.
- Chỉ Quản trị được sửa, xoá. Mọi thay đổi đều ghi lịch sử.

## 4. Mẫu văn bản: tối đa 5 mẫu mỗi loại, xoá mẫu không cần
- Mỗi loại mẫu (`TemplateKind`) giữ **tối đa 5** mẫu. Tải mẫu thứ 6 thì báo "Đã có 5 mẫu, hãy xoá bớt mẫu không cần".
- **Xoá** được mẫu không phải mặc định.
  - Không xoá được mẫu đang là mặc định; phải chọn mẫu khác làm mặc định trước.
  - Văn bản đã sinh từ mẫu bị xoá vẫn giữ nguyên file.
  - Xoá mẫu thì xoá cả file mẫu khỏi bộ nhớ, sau khi giao dịch thành công.
- Không áp giới hạn 5 cho số mẫu đang có nếu đã vượt. Chỉ chặn tải thêm.
- Quyền giữ như hiện nay.

## 5. Màn chấm công của nhân viên: thêm khung Lịch sử
- Màn **Chấm công** của nhân viên chia 2 cột trên máy tính. Trên điện thoại, cột Lịch sử nằm bên dưới.
  - **Bên trái** giữ nguyên phần chấm hôm nay.
  - **Bên phải** là khung **Lịch sử**, gồm:
    - ô chọn tháng, ghi "Tháng 10/2026", mặc định tháng hiện tại;
    - dòng tổng: **Công thực tế / công chuẩn** (tháng đang xem, tính đến hôm nay), **Số ngày đi muộn**, **Số ngày thiếu giờ**, **Chờ duyệt**;
    - mỗi ngày có dữ liệu là một dòng: ngày và thứ, giờ vào → giờ ra, số công, nhãn (Muộn X' / Về sớm X' / Thiếu giờ ra / Chờ duyệt / Bị từ chối / tên loại nghỉ / Làm ngày nghỉ), nơi chấm và khoảng cách nếu có, nút **Giải trình** khi ngày đó thiếu giờ hoặc có lần chấm bị từ chối.
- Số liệu lấy đúng cách tính của **Bảng công của tôi**, không tính lại riêng.
- Hiện **công** chứ không hiện "giờ".
- Không có ảnh chụp (người dùng đã chốt để làm sau).

## 6. Tiện ích nhỏ
- **Bảng công tháng** luôn mở vào **tháng hiện tại**. Trước đây mở tháng trước.
- **Đặt mật khẩu:** thêm nút **Đặt mật khẩu** ngay trên dòng từng người trong Quản trị → Người dùng, mở hộp thoại đặt mật khẩu đang có.
  - Không có nút cho tài khoản `admin`.
  - Quản trị dùng chức năng đổi mật khẩu của chính mình.

## Các cách đã cân nhắc
- **Chia việc:** người dùng chọn một nhánh và một kế hoạch khoảng 5 task. Cách còn lại là tách thành nhiều nhánh nhỏ, bị loại vì tốn thêm vòng review, merge và build.
- **Phép thâm niên:** chọn tính ngay trong phần cộng phép hằng tháng đang có. Không làm cách cộng một lần vào đầu năm, vì người dùng chọn "cộng dần từ tháng đủ năm".
- **Mức luật định:** sửa và xoá chỉ cho mốc chưa dùng. Không mở sửa mốc đã dùng, vì trái quy tắc "luật đổi không áp dụng ngược, kỳ lương đã duyệt giữ nguyên".
- **Ngày lễ:** dùng Excel cho đồng bộ với các chức năng nhập khác. Không dùng CSV.

## Để người làm tự quyết
- Tên endpoint, cách chia task, bố cục chi tiết.

## Làm rõ sau rà soát (quyết định của người viết)
- **Phép thâm niên, tháng đủ chu kỳ:** thâm niên được tính đến **ngày cuối tháng**. Vì vậy ngay tháng đủ chu kỳ đã được cộng trọn 1/12 phần thêm, không chia nhỏ theo ngày. Ví dụ: đủ 5 năm ngày 15/06 thì tháng 06 được cộng trọn. Các quy tắc tháng đầu, tháng cuối và làm tròn đang có áp dụng cho cả mức phép mới.
- **Phép thâm niên, đối tượng:** chỉ nhân viên chính thức (NHAN_VIEN), như toàn bộ phần nghỉ phép. Thực tập và HĐ dịch vụ không được cộng.
- **Phép thâm niên, cách cộng các lần làm:** cộng thẳng độ dài từng lần làm (từ ngày vào đến ngày làm việc cuối). Khoảng nghỉ giữa các lần làm không tính.
- **Nhập ngày lễ:** kiểm tra "tháng đã chốt hoặc có kỳ lương đã duyệt" theo tháng của **từng dòng**, và theo tháng của từng ngày sẽ bị xoá.
  - Bản xem trước có thêm nhóm **Không đổi**. Dòng trùng cả ngày lẫn tên với ngày đang có thì vào nhóm này.
  - Hai dòng cùng ngày trong file là **lỗi**.
- **Mốc luật "đã dùng":** khi một phiên bản bảng lương đã ghi nhận mốc đó, hoặc khi tháng hiệu lực ≤ kỳ lương đã duyệt gần nhất.
  - Chưa có kỳ nào được duyệt thì mọi mốc đều sửa, xoá được, trừ mốc cuối cùng.
  - Người làm kiểm tra xem phiên bản bảng lương đã lưu tham chiếu mốc ở đâu. Nếu không có, chỉ dùng điều kiện tháng hiệu lực.
- **Mẫu văn bản:**
  - Giới hạn 5 tính mọi mẫu đang lưu của loại đó, kể cả mẫu mặc định. Mẫu đã xoá không tính.
  - Nếu xoá file mẫu trong bộ nhớ bị lỗi (sau khi giao dịch đã lưu), chỉ ghi log, không báo lỗi cho người dùng.
- **Khung Lịch sử:** dùng lại đúng service hoặc endpoint của "Bảng công của tôi". Nếu phần tính đó đang nằm ở giao diện thì chuyển về backend để dùng chung.
  - Nút Giải trình mở form giải trình đang có.
  - Tháng đã chốt thì không có nút Giải trình, theo quy tắc hiện có.
- **Nút đặt mật khẩu:** ẩn trên dòng tài khoản đăng nhập `admin` và trên dòng của chính người đang đăng nhập. Tài khoản Quản trị khác vẫn có nút.

## Quyết định của người viết (người dùng kiểm tra khi đọc)
- Ngày lễ trong tháng đã chốt công hoặc có kỳ lương đã duyệt thì không sửa được.
- Thâm niên tính cả các lần làm trước và thời gian thử việc.
- Chu kỳ thâm niên trong khoảng 1–10 năm, số ngày cộng trong khoảng 0–5 ngày.
- Không xoá được mốc luật cuối cùng còn lại.
- Mẫu văn bản: nếu đã có quá 5 mẫu thì giữ nguyên, chỉ chặn tải thêm.
- Không có nút đặt mật khẩu trên dòng tài khoản `admin`.
