# DOIT HRM – KH3: Bảng lương

Ngày: 21/10/2026 · Trạng thái: chờ duyệt · Bổ sung cho spec đợt 1 (`2026-10-19-hrm-doit-dot1-design.md` §5.3, §5.6)

Ký hiệu như spec đợt 1: **[Chốt]** người dùng đã quyết định; **[Đề xuất]** lựa chọn của người viết.

## 1. Mục tiêu

Kế toán import file bảng lương Excel hằng tháng (mẫu DOIT, 36 cột) vào hệ thống. Có các ràng buộc:

- Mỗi dòng lương gắn với đúng một hồ sơ nhân viên.
- Mỗi kỳ lưu theo phiên bản, kèm file gốc.
- Quản trị duyệt kỳ.
- Chỉ Kế toán và Quản trị xem được.

Mức luật định dùng để đối chiếu (giảm trừ, trần bảo hiểm, biểu thuế) chuyển từ code ra bảng cấu hình theo ngày hiệu lực.

**Thành công khi:** file mẫu `DO it - Bang luong thang 06_2026 them luon.xlsx` import được cho kỳ 06/2026 sau khi Kế toán gắn tay các dòng lỗi. Kết quả đúng 14 dòng, tổng thực nhận 219.821.925, tổng chi phí nhân viên 245.709.000.

## 2. Kết quả chạy thử file mẫu (lõi hiện có)

- Nhận đủ 36/36 cột; 14 dòng; không lệch giữa cột tổng và các cột thành phần.
- 7 lỗi:
  - mã trùng giữa hai người: `DOIT2508` ở dòng 14 và 18, `DOIT2513` ở dòng 15 và 19;
  - 3 dòng thiếu mã: dòng 21 (thực tập), dòng 22 và 23 (hợp đồng dịch vụ).
- Cảnh báo:
  - tiêu đề file ghi "Tháng 08/2025", khác kỳ đang import (06/2026);
  - 2 người có ngày ký HĐLĐ nhưng không đóng bảo hiểm.

## 3. Luồng import 4 bước

Màn **Bảng lương** chỉ dành cho Kế toán và Quản trị. Lần import là một **bản nháp lưu trên máy chủ** **[Đề xuất]**: đóng màn hình rồi mở lại vẫn tiếp tục được.

1. **Tải file.** Chọn kỳ `YYYY-MM` rồi tải file `.xlsx` (tối đa 20 MB).
   - Kỳ đã duyệt → từ chối (`periodapproved`).
   - File không đọc được, hoặc không có sheet "Bảng lương" → từ chối, câu báo tiếng Việt.
2. **Nhận diện cột.** Hiện danh sách 36 trường với cột tìm được.
   - Thiếu cột bắt buộc là lỗi, không đi tiếp được.
   - Đợt này không cho ghép cột tay (spec đợt 1).
3. **Kiểm tra.** Hiện lỗi và cảnh báo theo dòng, kèm cảnh báo cấp file.
   - **[Chốt]** Dòng thiếu mã, trùng mã, hoặc mã chưa có hồ sơ có ô **Chọn hồ sơ**.
     - Gợi ý trước các hồ sơ có họ tên khớp với tên trong file: so không phân biệt dấu và hoa thường, khớp hoàn toàn đứng trước, khớp một phần đứng sau.
     - Gắn xong thì lỗi của dòng đó mất.
     - Hệ thống ghi lại người gắn và thời điểm.
   - **[Chốt]** Mỗi dòng phải gắn với một hồ sơ. Không có hồ sơ phù hợp thì bấm **Tạo hồ sơ rút gọn**:
     - các trường: họ tên (lấy sẵn từ file), loại người, mã;
     - HĐ dịch vụ: mã tự sinh `HDDVn`; nhân viên hoặc thực tập: nhập mã, không được trùng;
     - tạo xong thì gắn luôn vào dòng.
   - Không được gắn **một hồ sơ cho hai dòng** trong cùng một lần import → lỗi.
   - Sau khi gắn tay, các kiểm tra tên và đối chiếu luật được chạy lại theo hồ sơ đã gắn.
   - Còn lỗi thì không đi tiếp. Còn cảnh báo thì phải tích **Đã xem và xác nhận**.
   - Có nút **Tải báo cáo lỗi**: file Excel liệt kê dòng, mã, họ tên, mức độ, nội dung.
4. **Xác nhận.** Lưu thành phiên bản mới của kỳ: phiên bản đầu là 1, import lại cùng kỳ thì số tăng dần. Bản cũ vẫn giữ và xem được.
   - Kỳ ở trạng thái **Nháp**.
   - Bản nháp import được đánh dấu đã dùng.

Một kỳ có thể có nhiều bản nháp import đang dở, nhưng chỉ bước Xác nhận mới tạo phiên bản.

## 4. Duyệt và mở lại [Chốt]

- **Quản trị** bấm **Duyệt** cho kỳ. Hệ thống ghi người duyệt, thời điểm, và phiên bản mới nhất lúc duyệt; phiên bản đó là phiên bản chính thức.
- Kỳ đã duyệt: Kế toán không tải file mới và không xác nhận bản nháp cũ được (`periodapproved`).
- Chỉ **Quản trị** **Mở lại**, bắt buộc nhập lý do. Kỳ về **Nháp**, sau đó Kế toán import được phiên bản mới. Lịch sử duyệt và mở lại được lưu, có thể xem lại.
- Kế toán không duyệt và không mở lại được (403).

## 5. Xem bảng lương

- **Danh sách kỳ:** kỳ, trạng thái, phiên bản chính thức hoặc mới nhất, số người, tổng thực nhận, tổng chi phí nhân viên, người duyệt.
- **Chi tiết một kỳ:** chọn phiên bản; bảng các dòng có đủ 36 khoản, đánh dấu dòng gắn tay; tải file Excel gốc của phiên bản.
- **Hồ sơ nhân viên:** khối **Bảng lương** liệt kê các kỳ của người đó (lấy phiên bản chính thức hoặc mới nhất), gồm thực nhận, tổng chi phí, thuế TNCN và bảo hiểm của người lao động.

## 6. Phân quyền [Chốt]

| | Quản trị | Kế toán | HR | Ban giám đốc | Nhân viên |
|---|---|---|---|---|---|
| Xem kỳ, dòng lương, khối Bảng lương trên hồ sơ, file gốc | ✔ | ✔ | 403, ẩn menu và khối | 403, ẩn | 403 |
| Import (tải file, gắn hồ sơ, xác nhận) | ✔ | ✔ | 403 | 403 | 403 |
| Tạo hồ sơ rút gọn (chỉ qua màn Kiểm tra) | ✔ | ✔ | (HR tạo hồ sơ như cũ) | — | — |
| Duyệt, mở lại kỳ | ✔ | 403 | 403 | 403 | 403 |
| Thêm mốc mức luật định | ✔ | xem | xem | — | — |

Kế toán vẫn không sửa được hồ sơ có sẵn. Hồ sơ rút gọn do Kế toán tạo về sau HR sửa và bổ sung như mọi hồ sơ khác.

## 7. Mức luật định theo ngày hiệu lực

- Bảng `payroll_rule_milestone`. Mỗi mốc gồm:
  - tháng hiệu lực;
  - giảm trừ bản thân và giảm trừ người phụ thuộc;
  - trần đóng BHXH/BHYT và trần BHTN;
  - các bậc thuế (đến mức, thuế suất);
  - căn cứ văn bản;
  - người thêm và thời điểm thêm.
- Bảng được nạp sẵn đúng 6 mốc đang có trong `PayrollRules`: 07/2020, 07/2022, 07/2023, 07/2024, 01/2026, 07/2026.
- Quản trị **thêm mốc mới**. Không sửa và không xóa mốc đã có **[Đề xuất]**: nhập sai thì thêm mốc mới cùng tháng hiệu lực; mốc thêm sau sẽ thay thế mốc trước.
- Thêm một mốc **"đã biết luật đến tháng"** (thay hằng số `KNOWN_THROUGH`). Quản trị chỉnh được mốc này. Kỳ sau mốc này thì đối chiếu luật bị bỏ qua (bảng lương) hoặc gắn cờ "ước tính" (gợi ý Gross), như hiện nay.
- Lõi `core` không gắn cứng mức luật nữa. `PayrollValidator` và `NetGrossConverter` nhận danh sách mốc từ bên ngoài, lõi vẫn không phụ thuộc Spring. App nạp mốc từ cơ sở dữ liệu cho:
  - kiểm tra bảng lương;
  - gợi ý Gross trên hợp đồng và điều chỉnh lương.

  Danh sách 6 mốc trong code chỉ còn làm dữ liệu mặc định cho test của lõi và dữ liệu nạp ban đầu.

## 8. Lưu trữ

| Bảng | Nội dung |
|---|---|
| `payroll_period` | kỳ (`YYYY-MM`, duy nhất), trạng thái `NHAP`/`DA_DUYET`, phiên bản chính thức, người duyệt, thời điểm duyệt |
| `payroll_period_event` | lịch sử duyệt và mở lại: hành động, người làm, thời điểm, lý do |
| `payroll_import` | bản nháp import: kỳ, file gốc (đường dẫn lưu), người tạo, trạng thái `DANG_LAM`/`DA_XAC_NHAN`/`HUY`, đã xác nhận cảnh báo hay chưa. Kết quả đọc không lưu lại: mỗi lần cần thì đọc lại từ file gốc (file không đổi nên kết quả luôn như nhau) |
| `payroll_import_mapping` | các dòng gắn tay của bản nháp: dòng Excel → hồ sơ, người gắn, thời điểm |
| `payroll_version` | phiên bản của kỳ: số thứ tự, bản nháp nguồn, file gốc, người import, thời điểm |
| `payroll_line` | một dòng của phiên bản: hồ sơ, mã và họ tên như trong file, dòng Excel, gắn tay hay không; 36 khoản lưu JSON; tách riêng thành cột `thuc_nhan`, `tong_chi_phi`, `thue_tncn`, `tong_bh_nld` để báo cáo |
| `payroll_rule_milestone` (+ `payroll_rule_bracket`) | mức luật định (mục 7) |

- Mọi bảng có `_aud` (Envers), trừ JSON tạm của bản nháp. Changeset Liquibase chỉ thêm mới.
- File gốc lưu qua `FileStorageService` trong thư mục `payroll/{kỳ}`.

## 9. Lỗi và trường hợp biên

- Import cho kỳ khác với kỳ ghi trên tiêu đề file: cảnh báo, như lõi hiện có.
- Hồ sơ đã gắn bị HR đổi mã trước khi xác nhận: dòng lương vẫn gắn theo id hồ sơ. Mã và tên lưu đúng như trong file.
- Hai Kế toán xác nhận hai bản nháp cùng kỳ cùng lúc: số phiên bản không trùng (khóa dòng kỳ). Quản trị duyệt trong lúc Kế toán đang xác nhận: bên nào khóa sau thì thấy trạng thái mới.
- File quá 20 MB: 413, câu báo như phần hồ sơ scan.
- Dòng không có số nào (dòng trống giữa bảng): lõi đã bỏ qua, giữ nguyên.

## 10. Kiểm thử

- **Lõi:** validator và gợi Gross nhận danh sách mốc từ ngoài vào, kết quả bằng hiện nay với 6 mốc mặc định. Thêm một mốc 2027 thì kỳ 2027 đối chiếu theo mốc mới.
- **Tích hợp**, chạy với **bản ẩn danh** của file mẫu (xem dưới):
  - đọc 14 dòng, đúng 7 lỗi;
  - gắn tay 7 dòng, trong đó có 1 hồ sơ rút gọn HĐDV tạo mới, thì xác nhận được;
  - tổng thực nhận 219.821.925, tổng chi phí 245.709.000;
  - import lại tạo phiên bản 2;
  - duyệt xong thì import bị chặn; mở lại thì import được;
  - HR và Ban giám đốc nhận 403, Kế toán duyệt nhận 403;
  - một hồ sơ gắn hai dòng thì báo lỗi.
- **Giao diện:** 4 bước, ô Chọn hồ sơ có gợi ý theo tên, tạo hồ sơ rút gọn, tích xác nhận cảnh báo, duyệt và mở lại.
- **Dữ liệu test [Đề xuất]:** file mẫu chứa họ tên và lương thật nên **không đưa vào repo**. Repo chỉ có một bản sinh ra từ nó:
  - họ tên được thay bằng tên giả, mã giữ nguyên dạng;
  - số liệu giữ nguyên để các phép cộng tổng vẫn đúng;
  - lỗi trùng mã và thiếu mã giữ nguyên.

  Bản này sinh bằng một script nằm ngoài repo. Một test tùy chọn chạy với file thật khi đặt biến môi trường `PAYROLL_SAMPLE_FILE`.

## 11. Không làm ở KH3

- Ghép cột tay.
- Gửi phiếu lương cho nhân viên (spec đợt 1 §3).
- Báo cáo chi phí lương và ô chi phí trên trang Tổng quan: thuộc KH4, dùng các cột tổng ở `payroll_line`.
