# HRM DO IT – Trang Cài đặt hợp nhất (thiết kế ngắn)

Ngày 23/10/2026. Trạng thái: chờ duyệt. Đây là phần 1 của 2; phần 2 là "Nhân viên tự chấm công", thiết kế sau.

## Mục đích
Gom các cấu hình chung về một trang **Cài đặt** để Quản trị quản lý ở một chỗ. Tên công ty và logo sửa trên web là có hiệu lực ngay, không cần sửa file cấu hình hay build lại. Trang này cũng chuẩn bị sẵn dữ liệu **địa điểm chấm công** (toạ độ, bán kính, IP văn phòng) cho phần 2.

## Phạm vi
- **Trang `/cai-dat`**, có các tab:
  1. **Công ty**:
     - Tên công ty, tên viết tắt (thay "DOIT HRM" ở menu), khẩu hiệu (thay "Do it if you can"), người đại diện ký, chức danh.
     - Logo: PNG, JPG hoặc SVG, tối đa 1 MB, có xem trước. Logo hiện ở menu, màn đăng nhập, biểu tượng tab trình duyệt (favicon) và tiêu đề tab. **Chỉ hiện trên web, không in lên giấy tờ** (người dùng đã chốt).
  2. **Lịch & giờ làm**: chuyển nguyên trang Lịch làm việc hiện có (`/danh-muc/lich-lam-viec`): ngày làm trong tuần, ngày lễ, bộ giờ làm có ngày áp dụng, trạng thái nhắc việc.
  3. **Địa điểm chấm công**: danh sách `Nơi làm việc` loại văn phòng và khách hàng. Mỗi nơi có:
     - vĩ độ và kinh độ;
     - nút **"Lấy vị trí hiện tại"**, dùng GPS hoặc vị trí của trình duyệt trên thiết bị đang dùng, có báo sai số;
     - bán kính cho phép (mặc định 200 m, từ 20 đến 5000 m);
     - danh sách IP public của mạng văn phòng (không bắt buộc).

     Kèm một liên kết "Xem trên bản đồ" mở Google Maps theo toạ độ. Không nhúng bản đồ, để khỏi phụ thuộc dịch vụ ngoài.
  4. **Email**: chuyển trang Cấu hình email hiện có.
  5. **Nghỉ phép**: chuyển phần cài đặt nghỉ phép đang nằm trong trang Quản lý nghỉ phép.
- **Dùng cài đặt mới:** phiếu lương, báo cáo nhân sự, hợp đồng và quyết định cử nhân sự lấy tên công ty và người đại diện từ cài đặt mới thay cho `application.company.*`.
- **Link cũ** tự chuyển sang tab tương ứng: Danh mục → Lịch làm việc, Quản trị → Cấu hình email.

**Không làm:**
- In logo lên phiếu lương, hợp đồng hay Excel.
- Nhúng bản đồ.
- Kiểm tra chấm công theo vị trí. Việc này thuộc phần 2.

## Quyền (giữ như hiện nay)
- **Quản trị:** thấy và sửa mọi tab.
- **HR:** chỉ thấy tab Lịch & giờ làm (sửa ngày lễ và giờ làm như hiện nay; ngày làm trong tuần chỉ Quản trị sửa) và tab Địa điểm chấm công. HR đang sửa được `Nơi làm việc` nên giữ quyền này.
- **Nghỉ phép:** chỉ Quản trị (giữ quy tắc đã chốt "cài đặt nghỉ phép chỉ Quản trị sửa").
- **Email:** chỉ Quản trị.
- **Logo, tên công ty, tên viết tắt, khẩu hiệu:** ai cũng đọc được, kể cả khi chưa đăng nhập, vì màn đăng nhập cần. Người đại diện chỉ trả cho người đã đăng nhập.
- Menu: mục **Cài đặt** cho Quản trị và HR.

## Cách làm (đã chọn)
- **Cài đặt công ty:**
  - Lưu ở bảng một dòng `company_setting`: tên, tên viết tắt, khẩu hiệu, người đại diện, chức danh, `logo` (bytea), `logo_content_type`, `logo_updated_at`.
  - Dòng đầu tiên được tạo khi khởi động nếu chưa có, lấy giá trị từ `application.company.*` hiện tại. Logo mặc định là `doit-logo.png` đang có.
  - **Lý do chọn lưu logo trong DB:** logo nhỏ, đi theo bản backup DB và không phụ thuộc đường dẫn file. Phương án lưu file trong thư mục tài liệu bị loại vì không cần cho file nhỏ. Phương án giữ file cấu hình cũng bị loại vì đổi tên vẫn phải khởi động lại.
- **API công khai:**
  - `GET /api/public/branding` trả tên, tên viết tắt, khẩu hiệu và phiên bản logo.
  - `GET /api/public/branding/logo` trả ảnh, có `Cache-Control` và ETag theo `logo_updated_at`.
  - Logo SVG được trả với `Content-Security-Policy: sandbox` và kiểu `image/svg+xml`, chỉ hiển thị qua thẻ `<img>`, để tránh chạy script trong SVG.
- **Địa điểm chấm công:**
  - Thêm cột vào `work_site`: `latitude`, `longitude` (numeric 9,6), `radius_m` (int, mặc định 200), `office_ips` (text, mỗi dòng một IP hoặc CIDR).
  - Liquibase chỉ thêm, có `_aud` nếu entity được audit.
  - Kiểm tra giá trị: vĩ độ trong khoảng −90..90, kinh độ −180..180, bán kính 20..5000, IP/CIDR hợp lệ.
- **Nút lấy vị trí:** dùng `navigator.geolocation` với độ chính xác cao. Trình duyệt chỉ cho dùng trên HTTPS hoặc `localhost`; nếu không được thì báo rõ lý do. Hiện sai số (m) để người dùng biết vị trí lấy được có đáng tin không.

## Để người làm tự quyết
- Bố cục tab, tên component, cách tách file.
- Có resize logo hay không. Mặc định không resize, chỉ giới hạn kích thước file.

## Quyết định của người viết (người dùng kiểm tra khi đọc)
- HR thấy tab Địa điểm chấm công và sửa được, giống quyền sửa Nơi làm việc hiện nay.
- Tên viết tắt và khẩu hiệu là trường mới. Nếu để trống thì menu hiện tên viết tắt mặc định "DOIT HRM".
- Bán kính trong khoảng 20..5000 m, mặc định 200 m, theo lõi `PunchValidator`.
- Dùng liên kết Google Maps thay vì nhúng bản đồ.
