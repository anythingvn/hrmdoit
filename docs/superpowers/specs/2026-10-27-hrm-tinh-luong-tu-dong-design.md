# Tính lương tự động

Ngày: 27/10/2026. Trạng thái: chờ duyệt. Bổ sung cho KH3 Bảng lương (`2026-10-21-hrm-kh3-bang-luong-design.md`).

## 1. Mục đích

Hệ thống tự tính bảng lương của kỳ (đủ 36 khoản của mẫu DOIT) từ dữ liệu đã có — mức lương, phụ cấp, bảng công đã chốt,
làm thêm giờ đã duyệt, số người phụ thuộc, mức luật định — để Kế toán không phải làm Excel bằng tay. Kế toán chỉ nhập các khoản
không tự tính được, sửa nếu cần, rồi xác nhận thành phiên bản của kỳ; phần duyệt, phiếu lương, báo cáo giữ nguyên.

**Người dùng đã chọn:** tính bản nháp → Kế toán sửa trên web → xác nhận thành phiên bản (nhập file Excel vẫn giữ); áp dụng cho
nhân viên HĐLĐ, thực tập và HĐ dịch vụ; lương NET quy đổi ngược mỗi tháng; tiền làm thêm tự tính từ giờ đã duyệt; ngày công chỉ
lấy từ bảng công đã chốt; **công thức cố định, các thông số cấu hình được theo tháng hiệu lực** (không tự viết công thức).

**Thành công khi:** (1) bộ tính, chạy trên đầu vào của 14 người trong file mẫu `DO it - Bang luong thang 06_2026 them luon.xlsx`,
cho ra đúng từng khoản đã có trong file (lệch ≤ 1 đồng do làm tròn); (2) với một kỳ có bảng công đã chốt, Kế toán bấm "Tính lương",
nhập thưởng, xác nhận, và Quản trị duyệt được như kỳ nhập file.

**Ngoài phạm vi:** tự viết công thức; lương theo giờ cho bán thời gian; quyết toán thuế cuối năm tự động; thưởng/khấu trừ định kỳ tự
động; xuất file chi lương cho ngân hàng.

## 2. Cách tính

### 2.1 Thông số tính lương (cấu hình được, theo tháng hiệu lực)

Bảng mới `payroll_calc_setting` (mỗi bản ghi = một bộ, `effective_from` dạng `YYYY-MM`, duy nhất). Kỳ dùng bộ có `effective_from`
lớn nhất ≤ kỳ. Không có bộ nào thì dùng mặc định trong code. Bộ đã được một phiên bản đã duyệt dùng thì không sửa/xóa được (chỉ
thêm bộ mới) — đổi thông số chỉ áp dụng cho kỳ tính sau.

| Thông số | Mặc định |
|---|---|
| BHXH / BHYT / BHTN người lao động | 8% / 1,5% / 1% |
| BHXH / BHYT / BHTN công ty | 17,5% / 3% / 1% |
| Kinh phí công đoàn (bật/tắt, tỷ lệ) | **tắt** (tỷ lệ 2% trên mức đóng BHXH khi bật) — DOIT hiện không tính |
| Số giờ một ngày công (tính lương giờ làm thêm) | 8 |
| Lương giờ làm thêm tính trên | lương cơ bản (lựa chọn khác: lương cơ bản + phụ cấp đóng BH) |
| Loại ngày tính vào ngày công thực tế | ngày làm, phép có lương, lễ, công tác (tùy chọn thêm: ốm) |
| Ghép phụ cấp vào cột | tên chứa "chuyên cần" → PC chuyên cần; "xăng" → PC xăng xe; "trách nhiệm" → PC trách nhiệm (so không dấu, không phân biệt hoa thường; danh sách từ khóa sửa được) |
| Tiền làm thêm giờ | tách theo luật: phần 100% chịu thuế, phần vượt không chịu thuế (lựa chọn khác: toàn bộ không chịu thuế, như file DOIT hiện tại) |
| Khấu trừ thuế thực tập / HĐ dịch vụ | ngưỡng 2.000.000đ/tháng, tỷ lệ 10% |
| Trả phép chưa dùng khi nghỉ việc trong kỳ | bật |
| Không đóng BH khi nghỉ không lương từ 14 ngày làm việc trở lên trong tháng | bật — ngày không làm việc, không hưởng lương = ngày công chuẩn − ngày công thực tế; ≥ 14 thì BHXH/BHYT/BHTN (người lao động và công ty) và KPCĐ của tháng = 0 |

Mức luật định (giảm trừ gia cảnh, trần bảo hiểm, biểu thuế) vẫn là các mốc hiện có (`PayrollRuleMilestone`), không trùng lặp.

### 2.2 Công thức (cố định, cột theo `PayrollField`)

Nhân viên HĐLĐ (thuộc diện đóng BH khi `insuranceExemption` trống):

| Cột | Cách tính |
|---|---|
| `LUONG_CO_BAN`, `PHU_CAP_DONG_BH` | mức lương hiệu lực trong kỳ (`SalaryChange`; GROSS dùng `amount`, NET xem 2.3) |
| `NGAY_CONG_CHUAN` | ngày công chuẩn của bảng công tháng đã chốt (`AttendanceSnapshot.standardDays`) |
| `NGAY_CONG_THUC_TE` | tổng các loại ngày được chọn trong thông số (từ `AttendanceSnapshot`) |
| `LUONG_GOP` | Σ theo từng đoạn mức lương trong kỳ: lương cơ bản × ngày công thực tế của đoạn ÷ ngày công chuẩn **+ PC đóng BH trả đủ** (theo tỷ lệ số ngày làm việc của đoạn, không trừ theo ngày công). Ngày công thực tế của đoạn chia theo tỷ lệ số ngày làm việc của đoạn trong tháng |
| `PC_CHUYEN_CAN`, `PC_XANG_XE`, `PC_TRACH_NHIEM` | tổng `Allowance.amountPerMonth` đang hiệu lực trong kỳ, ghép theo từ khóa; phụ cấp không khớp từ khóa nào cộng vào `THUA_THIEU` và sinh cảnh báo |
| `OT_CHIU_THUE` | Σ đơn làm thêm đã hoàn tất, trả bằng tiền, `workDate` trong kỳ: giờ thực tế × lương giờ × 100% (thông số "toàn bộ không chịu thuế": 0) |
| `OT_KHONG_CHIU_THUE` | Σ giờ thực tế × lương giờ × (hệ số − 100%) (thông số "toàn bộ không chịu thuế": giờ × lương giờ × hệ số) |
| Lương giờ | (cơ sở theo thông số) ÷ ngày công chuẩn ÷ số giờ một ngày |
| `PHEP_CHUA_SU_DUNG` | khi bật và nhân viên có ngày nghỉ việc trong kỳ: số ngày phép còn lại tại ngày nghỉ × lương ngày (lương cơ bản + PC đóng BH) ÷ ngày công chuẩn |
| `THUONG_DU_AN`, `THUONG_KPI`, `TRO_CAP_THOI_VIEC`, `THUA_THIEU`, `QUYET_TOAN_THUE`, `GIAM_TRU_KHAC` | Kế toán nhập (mặc định 0) |
| `TONG_LUONG_GOP` | như công thức đối chiếu hiện có (`PayrollValidator`) |
| `LUONG_DONG_BHXH_BHYT` | `Employee.socialInsuranceSalary` nếu có, không thì lương cơ bản + PC đóng BH; tối đa trần BHXH của kỳ |
| `LUONG_DONG_BHTN` | như trên, tối đa trần BHTN |
| `BHXH_NLD`, `BHYT_NLD`, `BHTN_NLD`, `TONG_BH_NLD` | mức đóng × tỷ lệ người lao động; không thuộc diện đóng = 0 |
| `GIAM_TRU_BAN_THAN`, `SO_NGUOI_PHU_THUOC`, `GIAM_TRU_NPT` | mức luật của kỳ; số người phụ thuộc = `Employee.dependants` |
| `THU_NHAP_TINH_THUE`, `THUE_TNCN` | như công thức đối chiếu hiện có (`PayrollValidator`, `PayrollRules.monthlyPit`); `OT_KHONG_CHIU_THUE` và **`TRO_CAP_THOI_VIEC`** không vào thu nhập chịu thuế (trợ cấp thôi việc vẫn cộng vào thực nhận; Kế toán sửa tay nếu có phần vượt mức phải chịu thuế) |
| `BHXH_NSDLD`, `BHYT_NSDLD`, `BHTN_NSDLD`, `TONG_BH_NSDLD` | mức đóng × tỷ lệ công ty |
| `KPCD` | khi bật: mức đóng BHXH × tỷ lệ |
| `THUC_NHAN`, `TONG_CHI_PHI` | như công thức đối chiếu hiện có |

Mỗi khoản tiền làm tròn đến đồng (`HALF_UP`) ngay khi tính; khoản sau dùng số đã làm tròn (như Excel đang làm).

### 2.3 Lương NET

`SalaryChange.basis = NET`: lương NET thỏa thuận theo ngày công = NET × ngày công thực tế ÷ ngày công chuẩn. Tìm `LUONG_CO_BAN`
(Gross) nhỏ nhất sao cho, cùng các khoản khác của tháng, phần thực nhận từ lương ≥ NET theo ngày công, dùng `NetGrossConverter`
theo mức luật và số người phụ thuộc của kỳ (chạy cho từng tháng). Thưởng, phụ cấp, OT cộng thêm như người GROSS. Kỳ sau mốc luật đã
biết (quy đổi dùng mức ước tính) → cảnh báo. Tháng có cả đoạn GROSS và NET: mục tiêu = thực nhận từ đoạn GROSS + NET theo ngày công của đoạn NET. Ô `LUONG_GOP`/`LUONG_CO_BAN` bị sửa tay → không quy đổi. Không đạt mục tiêu trong giới hạn hợp lý → dừng với cảnh báo, không lỗi cả bản tính. Ngày công thực tế = 0 → cảnh báo. Cảnh báo thêm khi `THUC_NHAN` < 0 và khi Gross quy đổi > 3 × NET thỏa thuận.

### 2.4 Thực tập và HĐ dịch vụ

Không bảo hiểm, không giảm trừ gia cảnh. `THUE_TNCN` = tổng thu nhập tháng × tỷ lệ khấu trừ nếu tổng ≥ ngưỡng và hồ sơ không có
cam kết 08; ngược lại 0. Trường mới trên hồ sơ: `Employee.pitCommitment08` (boolean, mặc định false). Thu nhập ≥ ngưỡng mà chưa có
mã số thuế → cảnh báo.

## 3. Bản tính nháp

- **Điều kiện:** Kế toán/Quản trị; kỳ chưa duyệt (`periodapproved`); bảng công tháng của kỳ đã chốt (lỗi mới
  `payrollattendancenotclosed`: "Hãy chốt bảng công tháng MM/YYYY trước khi tính lương.").
- **Người trong bảng:** hồ sơ có dòng trong bảng công tháng đã chốt của kỳ và đang làm trong kỳ (ngày vào ≤ cuối tháng, chưa nghỉ
  hoặc ngày nghỉ ≥ đầu tháng). Không có mức lương hiệu lực → dòng lỗi "Chưa có mức lương" (bỏ dòng hoặc bổ sung hồ sơ rồi tính lại).
- **Lưu:** bảng mới `payroll_calc_draft` (một bản đang mở mỗi kỳ; mở lại "Tính lương" tiếp tục bản đó) và `payroll_calc_draft_line`
  (mỗi người một dòng: `amounts` tự tính, `inputs` nhập tay, `overrides` ô đã sửa, cảnh báo/lỗi), Envers ghi lịch sử sửa.
- **Sửa:** cột nhập tay nhập trực tiếp; ô tự tính sửa được (lưu vào `overrides`, có "về số tự tính"); mỗi lần sửa máy chủ tính lại
  các khoản phía sau từ đầu vào + nhập tay + sửa tay.
- **Tính lại từ hồ sơ:** đọc lại lương, phụ cấp, công, OT, người phụ thuộc, thông số; giữ `inputs` và `overrides`; thêm người mới,
  đánh dấu người không còn thuộc kỳ.
- **Khôi phục / hủy:** dòng đã bỏ khôi phục được; bản nháp hủy được (trạng thái `DA_HUY`), sau đó "Tính lương" tạo bản mới.
- **Xóa hẳn nhân viên** đang có dòng (chưa bỏ) trong bản tính nháp đang làm bị chặn, như bản nhập file đang làm.
- **Kiểm tra:** còn lỗi → không xác nhận; còn cảnh báo → phải tích "Đã xem và xác nhận" (như luồng nhập file). Kiểm tra đối chiếu
  hiện có (`PayrollValidator`) chạy trên kết quả.
- **Xác nhận** (máy chủ tính lại mọi dòng với thông số, mức luật, bảng công hiện hành; nếu số nào khác số đang lưu thì lưu số mới, bỏ "Đã xem và xác nhận" và từ chối với câu "Thông số, mức luật hoặc bảng công đã thay đổi, hãy xem lại bản tính."; bảng công phải vẫn đã chốt): tạo `PayrollVersion` mới của kỳ (nguồn `TINH_TOAN`, không gắn `PayrollImport`), dòng lương như phiên bản nhập file,
  sinh file Excel 36 cột (`PayrollTemplateWriter`) làm file của phiên bản; bản nháp đánh dấu đã dùng. Duyệt, phiếu lương, báo cáo
  dùng phiên bản như hiện nay.

## 4. API (Kế toán, Quản trị; HR/BGĐ/Nhân viên 403)

| Phương thức | Đường dẫn | Ý nghĩa |
|---|---|---|
| `POST` | `/api/payroll/periods/{period}/calc` | tạo hoặc mở bản nháp của kỳ |
| `GET` | `/api/payroll/calc/{draftId}` | bản nháp: dòng, cột, cảnh báo, tổng |
| `PUT` | `/api/payroll/calc/{draftId}/lines/{lineId}` | cập nhật nhập tay / sửa tay (body: map trường → số hoặc null = bỏ sửa) → dòng đã tính lại |
| `DELETE` | `/api/payroll/calc/{draftId}/lines/{lineId}` | bỏ dòng khỏi bản nháp |
| `POST` | `/api/payroll/calc/{draftId}/recalculate` | tính lại từ hồ sơ |
| `POST` | `/api/payroll/calc/{draftId}/confirm` | body `{ warningsAcknowledged }` → phiên bản mới |
| `GET`/`POST`/`PUT`/`DELETE` | `/api/payroll/calc-settings` | bộ thông số (Quản trị sửa; Kế toán, HR xem) |

## 5. Giao diện

- **Bảng lương → kỳ:** nút "Tính lương" cạnh "Nhập file"; kỳ chưa chốt bảng công → nút báo lý do kèm link bảng công.
- **Màn bản nháp:** bảng một dòng mỗi người, 36 cột nhóm theo tiêu đề mẫu, cột tự tính nền xám, cột nhập tay nền trắng, ô sửa tay
  tô màu + "↺", dòng tổng; cảnh báo/lỗi theo dòng; nút "Tính lại từ hồ sơ", "Xác nhận"; ô "Đã xem và xác nhận".
- **Cài đặt → tab "Bảng lương"** (Quản trị sửa; Kế toán, HR xem): danh sách bộ thông số theo tháng hiệu lực + form; các mốc mức luật
  định chuyển về tab này.
- **Hồ sơ nhân viên:** ô "Có cam kết 08 (không khấu trừ 10%)" cạnh mã số thuế.

## 6. Kiểm thử

- **Đối chiếu file thật (lõi):** đọc file mẫu 06/2026 (đặt cạnh thư mục project như `PoiIntegrationTest`, bỏ qua nếu không có),
  lấy đầu vào của từng dòng (lương cơ bản, PC đóng BH, mức đóng, ngày công, phụ cấp, OT, thưởng, thừa/thiếu, giảm trừ khác, số người
  phụ thuộc), chạy bộ tính, so từng khoản còn lại (≤ 1 đồng). Dòng lệch được ghi rõ để người dùng quyết định cách tính đúng.
- **Quy tắc (lõi):** đổi lương giữa tháng; không đóng BH; vượt trần; NET (thực nhận đúng); OT 150/200/300; nghỉ việc có trả phép;
  thực tập/HĐDV dưới/trên ngưỡng, có/không cam kết 08; thông số theo tháng hiệu lực; công đoàn tắt.
- **Máy chủ:** chưa chốt bảng công → 400; kỳ đã duyệt → 400; tính lại giữ nhập tay/sửa tay; xác nhận tạo phiên bản + file; còn
  lỗi không xác nhận; quyền (HR 403); bộ thông số đã dùng cho kỳ duyệt không sửa được.
- **Giao diện:** nút Tính lương; bảng nháp ô xám/trắng, sửa và "↺"; cảnh báo phải tích; tab thông số; ô cam kết 08.

## 7. Triển khai

Liquibase thêm bảng `payroll_calc_setting`, `payroll_calc_draft`, `payroll_calc_draft_line` (+ `_aud`), cột
`employee.pit_commitment_08` (+ `_aud`), cột nguồn trên `payroll_version` (mặc định `NHAP_FILE` cho dữ liệu cũ qua `defaultValue`,
không UPDATE tay). Kỳ đã nhập file giữ nguyên; nhập file vẫn dùng song song. README thêm mục "Tính lương tự động".
