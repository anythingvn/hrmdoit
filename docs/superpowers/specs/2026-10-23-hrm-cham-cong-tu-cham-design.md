# HRM DO IT – Chấm công phần 2: Nhân viên tự chấm công

Ngày: 23/10/2026 · Trạng thái: chờ duyệt.

**Tài liệu liên quan**
- Phần 1: `2026-10-23-hrm-cham-cong-design.md` (gõ giờ, chốt công, ngày công cho bảng lương).
- Trang Cài đặt: `2026-10-23-hrm-cai-dat-design.md` (địa điểm chấm công có tọa độ, bán kính, IP văn phòng).
- Bản phác đã duyệt: `docs/superpowers/mockups/2026-10-23-cham-cong-tu-cham.html`.

**Phụ thuộc.** Phần này xây trên nhánh phần 1 (`feature/cham-cong-gio`) và trang Cài đặt (đã có trên main). Chỉ bắt đầu triển khai khi cả hai đã có trên main. Nhánh triển khai được tạo lại từ main lúc đó.

Ký hiệu:
- **[Chốt]** người dùng đã quyết định.
- **[Đề xuất]** người viết đề xuất, người dùng đã đồng ý khi duyệt từng phần hoặc duyệt bản phác.

## 1. Mục đích

Ở phần 1, trưởng nhóm hoặc HR gõ giờ vào/ra cho nhân viên. Phần 2 cho **nhân viên tự bấm Vào/Ra trên điện thoại**. Hệ thống xác minh nhân viên đang ở đúng nơi làm việc bằng IP văn phòng hoặc GPS.

- Lần chấm không chắc chắn chờ người có quyền duyệt.
- Quên chấm hay bị từ chối thì nhân viên gửi giải trình.
- Cách tính công, chốt công và file bảng lương của phần 1 **giữ nguyên**. Phần 2 chỉ thêm một nguồn lần chấm.

**Thành công khi:**
- Nhân viên ở văn phòng bấm một lần là được tính, không cần ai gõ hộ.
- Người onsite ở khách hàng chấm bằng GPS; chấm sai nơi thì bị từ chối kèm lý do rõ ràng.
- Trưởng nhóm chỉ phải xử lý các lần chấm vàng (chờ duyệt) và giải trình.
- Không ai tự duyệt cho mình.
- Tọa độ và IP không bị giữ quá 90 ngày.

## 2. Phạm vi

**Làm:**
- màn tự chấm trên web dùng trên điện thoại;
- API tự chấm mà app sau này dùng lại;
- xác minh vị trí, lần chấm chờ duyệt và duyệt;
- giải trình do nhân viên gửi;
- thông báo và đồng ý thu thập vị trí;
- xóa tọa độ và IP sau thời hạn;
- hiện nguồn và trạng thái lần chấm trên các màn của phần 1;
- mục "Chấm công chờ duyệt" trong hộp Duyệt đơn.

**Không làm (đợt này):**
- app di động;
- gắn thiết bị (mỗi người một điện thoại) và kiểm tra lệch giờ máy: để dành cho app;
- WiFi BSSID, vì trình duyệt không đọc được;
- máy vân tay;
- bắt buộc tự chấm (gõ tay vẫn song song, xem §3.6);
- cách tính công mới.

**Không được để xảy ra:**
- Lần chấm bị từ chối hoặc đang chờ được tính công.
- Nhân viên tự duyệt lần chấm hay giải trình của mình.
- Chấm hoặc giải trình vào tháng đã chốt công.
- Tọa độ hay IP lộ cho người không có quyền, hoặc còn lưu sau thời hạn.

## 3. Quy tắc nghiệp vụ

### 3.1 Ai tự chấm được [Chốt theo phần 1]
- Chỉ nhân viên (`PersonType.NHAN_VIEN`) có trạng thái Đang làm việc, trong khoảng từ ngày vào làm đến ngày làm việc cuối.
- Người khác nhận lỗi 400 `attendancenotapplicable`.
- Hồ sơ đã lưu trữ bị chặn bởi guard hiện có.
- Tài khoản phải gắn với hồ sơ. Không có hồ sơ thì lỗi 404, như các API "của tôi" khác.

### 3.2 Một lần tự chấm [Đề xuất]
- **API** (dùng chung cho web và app sau này): `POST /api/attendance/punches` với `{kind: VAO|RA, latitude?, longitude?, accuracy?}`.
  - Nhân viên lấy từ phiên đăng nhập, không lấy từ body.
  - Giờ chấm là giờ máy chủ, múi giờ Asia/Ho_Chi_Minh. Ngày công là ngày theo giờ đó.
  - IP đọc từ kết nối. Sau reverse proxy thì lấy theo `X-Forwarded-For` của proxy tin cậy (dùng cấu hình forward headers của Spring, không tin header từ nguồn khác).
- **Chặn bấm trùng:** nếu cùng người, cùng loại, đã có lần tự chấm trong vòng **1 phút**, hệ thống không ghi thêm và trả lại kết quả của lần đó.
- Bấm nhiều lần trong ngày đều được ghi. Công ngày vẫn theo phần 1: lần vào sớm nhất và lần ra muộn nhất **trong các lần được tính** (§3.5).
- **Tháng đã chốt công:** lỗi 400, dùng khóa lỗi của phần 1 cho tháng đã chốt.
- **Chưa đồng ý thông báo vị trí** (§3.8): lỗi 400 `attendanceconsentrequired`.

### 3.3 Hôm nay phải chấm ở đâu [Chốt theo phần 1]
Theo phân công ngày đó, dùng đúng cách phần 1 xác định khối Văn phòng / Onsite:
- **Công tác** (`WorkAssignment` loại `CONG_TAC`): nơi công tác. Phần 1 tự điền 1 công cho ngày công tác, nên lần chấm chỉ được lưu, không đổi công.
- **Onsite** (`KHACH_HANG`): `WorkSite` của khách hàng đó.
- **Từ xa** (`TU_XA`): không cần vị trí.
- **Còn lại:** điểm văn phòng chính (`WorkSite` loại văn phòng có trong phân công, nếu không có thì là địa điểm văn phòng mặc định của Cài đặt).
- Không xác định được nơi nào thì lần chấm **chờ duyệt**.

### 3.4 Xác minh [Chốt]
Theo thứ tự, dùng `PunchValidator` có sẵn. Bước thiết bị và lệch giờ máy không áp dụng cho web.

| Trường hợp | Kết quả |
|---|---|
| Làm từ xa | **Chấp nhận**, phương thức "Từ xa" |
| IP khớp một dải trong `officeIps` của điểm chấm | **Chấp nhận**, phương thức "IP" |
| GPS trong bán kính (`radiusM`, mặc định 200 m), sai số ≤ 100 m | **Chấp nhận**, phương thức "GPS" |
| GPS sát ranh giới (trong bán kính + sai số) hoặc sai số > 100 m | **Chờ duyệt**, ghi lý do |
| GPS xa hơn | **Từ chối**: "Bạn đang cách <điểm> <x> m/km. Hôm nay bạn phải chấm tại <điểm>" |
| Không gửi vị trí (người dùng từ chối chia sẻ) và IP không khớp | **Chờ duyệt**: "Không có vị trí" |
| Điểm chấm chưa khai báo tọa độ và IP | **Chờ duyệt**: "Địa điểm chưa khai báo" |

- Lần bị từ chối **vẫn được lưu** để tra soát, nhưng không tính công. Màn kết quả có link "Gửi giải trình".
- Người dùng đã chọn: GPS trong bán kính thì chấp nhận ngay, không bắt duyệt mọi lần chấm bằng GPS.

### 3.5 Lần chấm nào được tính công [Chốt]
Bảng lần chấm của phần 1 có thêm trạng thái. Công ngày chỉ tính:
- lần **gõ tay** (phần 1, luôn được tính);
- lần **tự chấm** có trạng thái *Chấp nhận* hoặc *Đã duyệt*;
- lần sinh từ **giải trình đã duyệt**.

Lần *Chờ duyệt* và *Từ chối* không được tính. Khi chốt tháng, lần còn chờ duyệt được xử lý như ô đỏ của phần 1: có trong danh sách cảnh báo trước khi chốt, và tính như chưa có lần chấm.

### 3.6 Tự chấm và gõ tay song song [Chốt]
- Mọi nhân viên đều tự chấm được, không cần bật riêng.
- Trưởng nhóm và HR vẫn gõ tay như phần 1 khi cần (quên chấm, mất mạng).
- Màn gõ hiện nguồn của từng lần.
- Gõ tay không xóa lần tự chấm. Công lấy theo §3.5.

### 3.7 Duyệt lần chấm chờ duyệt và giải trình [Chốt]
- **Người duyệt** là người được gõ giờ cho nhân viên đó vào ngày đó, theo bảng quyền §3.4 của phần 1:
  - trưởng nhóm (`manager`);
  - trưởng nhóm onsite của khách hàng mà người đó đang onsite đúng ngày đó;
  - HR, Quản trị.
- **Không ai tự duyệt** cho mình, kể cả HR.
- **Duyệt:** lần chấm thành *Đã duyệt* và được tính công ngay.
- **Từ chối:** bắt buộc ghi lý do. Lần chấm thành *Từ chối* (giữ nguyên dữ liệu).
- Lưu người duyệt, thời điểm và lý do. Envers ghi lịch sử như các bảng khác.
- Tháng đã chốt: không duyệt hay từ chối được nữa.

**Giải trình:**
- **Gửi:** nhân viên gửi cho một ngày đã qua, hoặc hôm nay với giờ đã qua, trong tháng chưa chốt.
  - Nội dung: giờ vào và/hoặc giờ ra đề xuất, cùng lý do (bắt buộc).
  - Bỏ trống một giờ nghĩa là giữ lần chấm đã có.
  - Giờ đề xuất phải nằm trong ngày đó và không ở tương lai.
- **Giới hạn:** mỗi người mỗi ngày chỉ một giải trình *Đang chờ*. Gửi thêm khi đang có thì lỗi 400 `explanationpending`.
- **Trạng thái:** *Đang chờ* → *Đã duyệt* | *Từ chối* | *Đã rút*. Nhân viên rút được khi còn *Đang chờ*.
- **Kết quả duyệt:** khi được duyệt, hệ thống sinh lần chấm nguồn `GIAI_TRINH` cho mỗi giờ đề xuất. Các lần này được tính công.
- **Email** (dùng cấu hình email có sẵn; lỗi gửi chỉ ghi log):
  - báo người duyệt khi có giải trình mới: gửi trưởng nhóm, nếu không có thì trưởng nhóm onsite, nếu không có thì HR;
  - báo nhân viên khi có kết quả.

### 3.8 Riêng tư [Chốt]
- **Đồng ý thông báo:**
  - Lần tự chấm đầu tiên, nhân viên phải bấm "Tôi đồng ý" với thông báo thu thập vị trí. Lưu thời điểm đồng ý và phiên bản nội dung.
  - Quản trị soạn nội dung ở Cài đặt.
  - Đổi nội dung thì không bắt đồng ý lại.
- **Thời hạn lưu:** tọa độ, sai số và IP được xóa (đặt về rỗng) sau **90 ngày**, cấu hình được. Job chạy hằng ngày.
  - Giữ mãi: điểm chấm, khoảng cách, phương thức, trạng thái, lý do.
- **Chỉ lấy vị trí đúng lúc bấm.** Không theo dõi nền.
- **Ai xem chi tiết vị trí** (tọa độ, IP): chính người đó, người duyệt của ngày đó, HR, Quản trị.
  - Trưởng phòng, Giám đốc, Kế toán xem bảng công không thấy tọa độ và IP.
- Phần nội dung pháp lý của thông báo cần người phụ trách pháp lý soạn.

## 4. Dữ liệu [Đề xuất – cách A đã chọn]

Mở rộng bảng lần chấm của phần 1 thay vì làm bảng riêng, để một nguồn dữ liệu phục vụ cả màn gõ, bảng công và chốt.

**Bảng lần chấm** `attendance_punch` (đã có `source`: `GO_TAY`, `APP`):
- Thêm nguồn `GIAI_TRINH`.
- Thêm các cột:
  - `status` (`CHAP_NHAN`, `CHO_DUYET`, `TU_CHOI`, `DA_DUYET`); lần gõ tay mặc định `CHAP_NHAN`;
  - `method` (`IP`, `GPS`, `TU_XA`, null cho gõ tay);
  - `site_id`, `distance_m`;
  - `latitude`, `longitude`, `accuracy_m`, `client_ip` (được xóa sau thời hạn);
  - `review_reason` (lý do chờ hoặc từ chối tự động);
  - `decided_by`, `decided_at`, `decision_note`;
  - `explanation_id` cho lần sinh từ giải trình.
- Thêm cột tương ứng vào `_aud`.

**Bảng giải trình** `attendance_explanation`, kèm `_aud`:
- `employee_id`, `work_day`, `proposed_in`, `proposed_out`, `reason`;
- `status` (`DANG_CHO`, `DA_DUYET`, `TU_CHOI`, `DA_RUT`);
- `decided_by`, `decided_at`, `decision_note`, `created_at`.

**Đồng ý thông báo:** bảng `attendance_consent` gồm `employee_id`, `accepted_at`, `notice_version`.

**Cài đặt:** thêm `attendance_notice_text`, `attendance_notice_version` và `location_retention_days` (mặc định 90) vào bảng cài đặt chấm công của phần 1 hoặc của trang Cài đặt.

**Ràng buộc chung:**
- Liquibase chỉ thêm changeset mới.
- Các bảng mới có FK tới `employee` phải nằm trong `EmployeeDeletionService.HANDLED_TABLES` (xóa khi xóa hẳn) và qua được test FK coverage.
- Các danh sách chờ duyệt lọc bỏ hồ sơ đã lưu trữ.

## 5. Màn hình [Chốt theo bản phác đã duyệt]

1. **"Chấm công" trên điện thoại** (menu nhân viên):
   - Giờ hiện tại, ngày, nơi phải chấm hôm nay kèm giờ làm.
   - Hai nút lớn **Vào** và **Ra** (vùng bấm ≥ 44px).
   - Kết quả có màu: xanh là được tính, vàng là chờ duyệt, đỏ là từ chối (kèm link Gửi giải trình).
   - Danh sách lần chấm hôm nay.
   - Lần đầu hiện hộp đồng ý thông báo vị trí.
2. **"Bảng công của tôi"**: màn của phần 1, thêm:
   - nguồn và trạng thái từng lần chấm;
   - ô vàng khi có lần chờ duyệt hoặc giải trình đang chờ;
   - nút **Gửi giải trình** ở ngày thiếu giờ, chưa nhập hoặc bị từ chối;
   - danh sách giải trình của tôi với trạng thái và nút Rút.
   
   Form giải trình gồm giờ vào, giờ ra, lý do và dòng ghi người duyệt.
3. **Màn gõ theo ngày** và **bảng công tháng** của phần 1:
   - dưới mỗi giờ ghi nguồn (Tự chấm · IP/GPS/từ xa, Gõ tay, Giải trình);
   - dòng vàng có lần chờ duyệt, kèm nút **Duyệt / Từ chối** cho người có quyền;
   - lần bị từ chối hiện mờ để tra soát;
   - bộ đếm "N lần chấm chờ duyệt".
4. **Hộp "Duyệt đơn"**: thêm tab **"Chấm công chờ duyệt"** gom lần chấm chờ duyệt và giải trình của những người mình được duyệt.
   - Mỗi dòng có loại, nhân viên, ngày, nội dung, lý do hoặc kết quả kiểm tra, cùng nút Duyệt/Đồng ý và Từ chối.
   - Từ chối mở hộp nhập lý do.
5. **Cài đặt – Địa điểm chấm công:** thêm khối "Tự chấm công" gồm nội dung thông báo vị trí và số ngày giữ tọa độ/IP.

Chữ tiếng Việt, ngày `dd/MM/yyyy`, giờ `HH:mm`, theo màu và font canvas.

## 6. API [Đề xuất]

| Phương thức | Đường dẫn | Ai |
|---|---|---|
| POST | `/api/attendance/punches` | Nhân viên (§3.1) |
| GET | `/api/attendance/punches/today` | Nhân viên: nơi phải chấm hôm nay và các lần chấm hôm nay |
| POST | `/api/attendance/consent` | Nhân viên |
| POST | `/api/attendance/punches/{id}/approve` · `/reject` `{note}` | Người duyệt của ngày đó (§3.7) |
| POST | `/api/attendance/explanations` `{workDay, proposedIn?, proposedOut?, reason}` | Nhân viên |
| POST | `/api/attendance/explanations/{id}/withdraw` | Chính người gửi |
| POST | `/api/attendance/explanations/{id}/approve` · `/reject` `{note}` | Người duyệt của ngày đó |
| GET | `/api/attendance/pending` | Người duyệt: lần chấm chờ duyệt và giải trình đang chờ trong phạm vi của mình |

**Lỗi nghiệp vụ:** dùng `EmployeeRuleException(key)` → 400 `error.<key>`. Mọi key mới có câu tiếng Việt trong `global.json` và `ERROR_TEXT`.

**Quyền:** kiểm ở backend, theo dữ liệu của ngày đó (dùng `AttendanceAccess` của phần 1).

## 7. Kiểm thử [Đề xuất]
- **Xác minh** (§3.4): một test cho mỗi dòng của bảng, cho từng nơi phải chấm (văn phòng, onsite, công tác, từ xa, không xác định).
- **Chấm:**
  - bấm trùng trong 1 phút không ghi thêm;
  - tháng đã chốt bị 400;
  - chưa đồng ý thông báo bị 400;
  - không phải nhân viên bị 400;
  - giờ chấm là giờ máy chủ;
  - IP lấy đúng sau proxy tin cậy và không tin header giả.
- **Tính công:**
  - lần chờ duyệt và từ chối không tính; duyệt xong thì tính ngay;
  - gõ tay và tự chấm cùng ngày thì lấy vào sớm nhất và ra muộn nhất trong các lần được tính;
  - chốt khi còn lần chờ duyệt thì có cảnh báo và tính như chưa chấm.
- **Duyệt:**
  - không tự duyệt;
  - trưởng nhóm onsite chỉ duyệt được người onsite ở khách hàng đó đúng ngày đó;
  - từ chối thiếu lý do bị 400;
  - tháng đã chốt bị 400.
- **Giải trình:**
  - gửi, rút, duyệt sinh lần chấm `GIAI_TRINH`, từ chối;
  - chỉ một giải trình đang chờ mỗi ngày;
  - giờ tương lai hoặc khác ngày bị 400;
  - có email cho người duyệt và cho nhân viên.
- **Riêng tư:**
  - job xóa tọa độ và IP của lần chấm quá 90 ngày, giữ kết quả;
  - trưởng phòng và kế toán không thấy tọa độ.
- **Xóa hẳn và lưu trữ:**
  - test FK coverage có các bảng mới;
  - hồ sơ đã lưu trữ không chấm được và không hiện trong danh sách chờ duyệt.
- **Giao diện (Vitest):**
  - màn chấm hiện đúng ba màu kết quả;
  - hộp đồng ý hiện ở lần đầu;
  - nút Duyệt/Từ chối chỉ hiện cho người có quyền;
  - form giải trình bắt buộc lý do.

## 8. Quyết định của người viết (người dùng kiểm tra khi đọc)
- Lần bị từ chối vẫn được lưu để tra soát (không tính công).
- Không chia sẻ vị trí vẫn gửi được, chỉ kiểm theo IP, không khớp thì chờ duyệt.
- Ngày công tác: lần tự chấm chỉ được lưu, công vẫn là 1 theo phần 1.
- Email giải trình gửi trưởng nhóm, nếu không có thì trưởng nhóm onsite, nếu không có thì HR. Ai trong nhóm người duyệt cũng duyệt được.
- Đổi nội dung thông báo không bắt đồng ý lại.
