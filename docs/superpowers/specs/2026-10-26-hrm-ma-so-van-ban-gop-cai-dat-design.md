# Cấu hình mã, số văn bản, mật khẩu mặc định; gộp Cài đặt và Quản trị

Ngày: 26/10/2026. Trạng thái: chờ duyệt.

## 1. Mục đích

Cài DOIT HRM cho công ty khác mà không sửa code hay `.env`: chữ `DOIT` trong số văn bản và mã nhân viên, các mã loại văn bản
và mật khẩu mặc định của tài khoản mới đều đặt được trên web. Đồng thời gom mọi trang cấu hình vào một mục **Cài đặt** (bỏ mục
Quản trị riêng).

**Người dùng đã chọn:** số văn bản đổi được tiền tố + mã từng loại (giữ dạng `<tiền tố>/<năm>/<loại>/<số>`); mã nhân viên đổi
được tiền tố từng loại (giữ cách đánh số tiền tố + 2 số năm + số thứ tự); lưu trong bảng `company_setting`; gộp thành một mục
"Cài đặt"; **mật khẩu mặc định khi không cấu hình đổi từ `Doit@2014` thành `Doit@2024`**.

**Nguyên tắc:** đổi cấu hình chỉ áp dụng cho văn bản, nhân viên, tài khoản **tạo mới sau đó**; số, mã, mật khẩu đã cấp giữ
nguyên (không đổi lại dữ liệu cũ). Chỉ Quản trị sửa ba cấu hình này.

**Ngoài phạm vi:** mẫu số văn bản tự do; mã nhân viên không kèm năm hoặc chọn số chữ số; đổi lại mã/số đã cấp; bốn trang kỹ
thuật JHipster đã ẩn (giữ nguyên).

## 2. Dữ liệu và quy tắc

### Cột mới trong `company_setting` (Liquibase thêm cột, đều cho phép null; `@Audited` sẵn có ghi lịch sử)

| Cột | Ý nghĩa | Khi null |
|---|---|---|
| `document_prefix` | tiền tố số văn bản | `DOIT` |
| `document_code_hdld`, `document_code_hdtv`, `document_code_hddt`, `document_code_hddv`, `document_code_qdcns` | mã loại văn bản | `HĐLĐ`, `HĐTV`, `HĐĐT`, `HĐDV`, `QĐCNS` (giá trị `DocumentType.code()` hiện nay) |
| `staff_code_prefix`, `intern_code_prefix`, `service_code_prefix` | tiền tố mã nhân viên / thực tập / HĐ dịch vụ | `DOIT`, `TT`, `HDDV` |
| `default_password` | mật khẩu mặc định tài khoản mới | `application.account.default-password` (`.env`), không có thì `Doit@2024` |

Giá trị lưu đã `trim()`; chuỗi rỗng lưu thành null (= dùng mặc định).

### Quy tắc kiểm tra khi lưu (400 `error.<mã>`, câu tiếng Việt trong `ERROR_TEXT`)

| Trường | Quy tắc | Mã lỗi | Câu |
|---|---|---|---|
| Tiền tố và mã loại văn bản | 1–10 ký tự, `^[\p{Lu}0-9-]{1,10}$` (chữ in hoa kể cả có dấu, số, gạch ngang; không `/`) | `numberingdocinvalid` | Tiền tố và mã loại văn bản chỉ gồm chữ in hoa, số, gạch ngang, tối đa 10 ký tự, không có dấu "/". |
| 5 mã loại văn bản | khác nhau | `numberingdocduplicate` | Mã các loại văn bản phải khác nhau. |
| Tiền tố mã nhân viên | `^[A-Z][A-Z0-9]{0,9}$` | `numberingcodeinvalid` | Tiền tố mã nhân viên chỉ gồm chữ A–Z và số, bắt đầu bằng chữ, tối đa 10 ký tự. |
| 3 tiền tố mã nhân viên | khác nhau | `numberingcodeduplicate` | Ba tiền tố mã nhân viên phải khác nhau. |
| Mật khẩu mặc định | ≥ 8 ký tự (giống `ApplicationProperties.Account.MIN_LENGTH`), tối đa 100 | `defaultpasswordinvalid` | Mật khẩu mặc định phải có từ 8 đến 100 ký tự. |

### Áp dụng

- **Số văn bản** (`DocumentNumberService.format`): `<tiền tố>/<năm>/<mã loại>/<số>` lấy từ cấu hình. Dãy số vẫn theo loại +
  năm (`document_sequence`) nên đổi tiền tố giữa năm vẫn đếm tiếp: `DOIT/2026/HĐLĐ/12` → `ABC/2026/HĐLĐ/13`. Kiểm tra trùng
  (`isTaken`) dùng số đã định dạng như hiện nay.
- **Mã nhân viên** (`EmployeeCodes` + nơi gọi): tiền tố lấy từ cấu hình; dãy tính theo tiền tố hiện hành
  (`findCodesStartingWith`) nên đổi tiền tố thì dãy mới bắt đầu `<tiền tố mới><yy>01`, mã cũ giữ nguyên. HĐ dịch vụ: tiền tố
  cấu hình + `hddv_code_seq` (bộ đếm giữ nguyên): `HDDV7` → `DV8`.
- **Nhập file nhân viên** (`EmployeeImportCheck`): danh sách tiền tố và tiền tố HĐ dịch vụ lấy từ cấu hình; câu cảnh báo nêu
  tiền tố thực tế.
- **Mật khẩu mặc định:** một chỗ duy nhất tính giá trị hiệu lực (web → `.env` → `Doit@2024`), dùng cho mọi nơi đang đọc
  `ApplicationProperties.Account.getDefaultPassword()` (tạo tài khoản, đặt lại mật khẩu) và cho API hiển thị.
  `ApplicationProperties.Account.DEFAULT_PASSWORD` đổi thành `Doit@2024`.
- **Gợi ý trên giao diện** đang ghi cứng `DOIT/TT/HDDV` (form nhân viên, trang nhập file, ánh xạ dòng bảng lương, bảng số văn
  bản) đọc từ cấu hình.

## 3. API

| Phương thức | Quyền | Body / trả |
|---|---|---|
| `GET /api/company-setting/numbering` | ADMIN, HR, KE_TOAN | `{ documentPrefix, documentCodes: { HDLD, HDTV, HDDT, HDDV, QD_CU_NHAN_SU }, staffCodePrefix, internCodePrefix, serviceCodePrefix }` — giá trị **hiệu lực** (đã thay mặc định), kèm `defaults` cùng dạng để giao diện hiện "mặc định: …" |
| `PUT /api/company-setting/numbering` | ADMIN | cùng dạng (không `defaults`); trường null/rỗng = về mặc định; trả như GET |
| `GET /api/admin/account-settings/default-password` | ADMIN, HR (giữ nguyên) | thêm `source: 'WEB'|'ENV'|'DEFAULT'` bên cạnh `defaultPassword` |
| `PUT /api/admin/account-settings/default-password` | ADMIN | `{ defaultPassword: string|null }` (null = bỏ cấu hình web); trả như GET |

Không ghi log giá trị mật khẩu (resource/service gắn `@NoArgumentLogging` như các chỗ nhạy cảm khác).

## 4. Giao diện và gộp menu

### Menu

Bỏ mục **Quản trị** (`menu-items.ts`) và dãy link con Quản trị trong sidebar (`ADMIN_LINKS`). Xóa file không dùng
`app/shared/layout/menus/admin.tsx`. Mục **Cài đặt** giữ quyền như nay (Quản trị, HR).

### Các tab Cài đặt (`SETTINGS_TABS`, theo thứ tự)

| Tab (path) | Thấy | Sửa |
|---|---|---|
| Công ty (`cong-ty`) | Quản trị | Quản trị |
| Lịch & giờ làm (`lich-lam-viec`) | Quản trị, HR | như nay |
| Địa điểm chấm công (`dia-diem`) | Quản trị, HR | như nay |
| Nghỉ phép (`nghi-phep`) | Quản trị, HR | Quản trị |
| **Mã & số văn bản** (`ma-so-van-ban`, mới) | Quản trị, HR | Quản trị |
| Email (`email`) | Quản trị | Quản trị |
| Sao lưu (`sao-luu`) | Quản trị | Quản trị |
| **Tài khoản** (`tai-khoan/*`, chuyển từ Quản trị) | Quản trị | Quản trị |
| **Nhật ký xóa nhân viên** (`nhat-ky-xoa-nhan-vien`, chuyển từ Quản trị) | tài khoản `admin` (`SYSTEM_ADMIN_LOGIN`) | — |

### Tab "Mã & số văn bản"

- **Thẻ Số văn bản:** ô tiền tố + 5 ô mã loại (nhãn theo `DOCUMENT_TYPE_LABEL`), mỗi ô có ghi "mặc định: …"; dòng xem trước
  "Số tiếp theo: ABC/2026/HĐLĐ/13" (dùng `nextValue` của năm hiện tại từ bảng dãy số, đổi theo ô nhập); nút Lưu. Bên dưới là
  bảng đặt số bắt đầu theo năm (component `DocumentNumbers` hiện có, chuyển từ Hợp đồng & mẫu).
- **Thẻ Mã nhân viên:** 3 ô tiền tố (Nhân viên, Thực tập, HĐ dịch vụ) với "mặc định: …"; ví dụ "Nhân viên vào làm năm 2026:
  ABC26xx", "HĐ dịch vụ: DV + số thứ tự"; nút Lưu.
- Mỗi thẻ ghi: "Chỉ áp dụng cho văn bản và nhân viên tạo mới; số và mã đã cấp giữ nguyên."
- HR thấy dạng chỉ đọc (ô khóa, không có nút Lưu, không có ô sửa số bắt đầu — như `DocumentNumbers canEdit=false`).

### Tab "Tài khoản"

- Đầu trang: thẻ **Mật khẩu mặc định cho tài khoản mới** — giá trị đang dùng, nguồn ("Cài trên web" / "Từ file .env trên máy
  chủ" / "Mặc định Doit@2024"), ô sửa (≥ 8 ký tự), nút Lưu và "Dùng lại mặc định" (gửi null).
- Dưới: danh sách và các màn tài khoản hiện có (`modules/administration/user-management`), đường dẫn đổi sang
  `/cai-dat/tai-khoan/...` (các link nội bộ trong user-management cập nhật theo).

### Chuyển hướng địa chỉ cũ

`/admin/user-management/*` → `/cai-dat/tai-khoan/*` (giữ phần đuôi, vd `/admin/user-management/abc/edit`);
`/admin/nhat-ky-xoa-nhan-vien` → `/cai-dat/nhat-ky-xoa-nhan-vien`; `/hop-dong/so-van-ban` → `/cai-dat/ma-so-van-ban`; tab "Số
văn bản" trong Hợp đồng & mẫu bỏ. Route `/admin/health|metrics|configuration|logs|docs` giữ nguyên (đã ẩn khỏi menu).

## 5. Kiểm thử

- **Máy chủ (unit):** quy tắc kiểm tra từng dòng bảng mục 2 (có `/`, quá 10 ký tự, trùng mã loại, trùng tiền tố, bắt đầu bằng
  số, mật khẩu < 8 / > 100); `DocumentNumberService.format` với cấu hình; `EmployeeCodes.next` với tiền tố mới; thứ tự ưu tiên
  mật khẩu (web → `.env` → `Doit@2024`).
- **Máy chủ (tích hợp):** đổi tiền tố giữa năm → số kế tiếp `ABC/2026/HĐLĐ/<n+1>`; đổi tiền tố nhân viên → mã mới
  `ABC<yy>01`, mã cũ không đổi; HĐ dịch vụ đổi tiền tố → số vẫn tăng tiếp; nhập file nhận tiền tố mới; tạo tài khoản và đặt lại
  mật khẩu dùng giá trị web; GET numbering/default-password: HR được, NHAN_VIEN 403; PUT: HR 403; giá trị null → mặc định.
- **Giao diện (Vitest):** menu không còn Quản trị; tab Tài khoản/Nhật ký chỉ hiện đúng người; chuyển hướng 3 địa chỉ cũ; xem
  trước số văn bản và mã đổi theo ô; HR thấy chỉ đọc; thẻ mật khẩu hiện nguồn, "Dùng lại mặc định" gửi null; gợi ý trên form
  nhân viên/trang nhập file dùng tiền tố cấu hình.

## 6. Triển khai và tài liệu

- Liquibase: changeset mới thêm 10 cột vào `company_setting` và `company_setting_aud`. Không sửa dữ liệu cũ.
- Máy đang chạy giữ nguyên `DOIT`, `HĐLĐ`… đến khi Quản trị sửa trên web. Tài khoản đã tạo bằng `Doit@2014` mà chưa đăng nhập
  vẫn dùng mật khẩu cũ.
- README: mục "Cấu hình mã và số văn bản" (Cài đặt → Mã & số văn bản), mật khẩu mặc định đặt ở Cài đặt → Tài khoản, `.env`
  là giá trị dự phòng; sửa mọi chỗ ghi `Doit@2014` thành `Doit@2024` (README, `.env.example`, `ApplicationProperties`).
