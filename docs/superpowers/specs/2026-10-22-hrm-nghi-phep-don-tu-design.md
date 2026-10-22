# DOIT HRM – Nghỉ phép & đơn từ

Ngày: 22/10/2026 · Trạng thái: bản nháp chờ duyệt

Ký hiệu: **[Chốt]** là người dùng đã quyết định. **[Đề xuất]** là lựa chọn của người viết, người dùng chưa xác nhận riêng; người xây dựng có thể đổi nếu có lý do. **[Luật]** là quy định pháp luật đã tra (xem Nguồn ở cuối).

## 1. Mục đích

Nhân viên DO IT tự gửi đơn nghỉ và đơn làm thêm giờ trên hệ thống thay cho giấy hay tin nhắn. Người có thẩm quyền duyệt ngay trên hệ thống. Số phép còn lại được tính tự động, đúng luật, minh bạch, nên HR không phải tự đếm. Lịch cho biết ai vắng ngày nào, gồm cả lịch nghỉ việc đã duyệt và ngày đi công tác. Kế toán có số liệu hằng tháng để đưa vào bảng lương.

## 2. Phạm vi

**Làm:** sổ phép năm và quỹ nghỉ bù, 6 loại đơn, luồng duyệt, lịch tháng, trang "Nghỉ phép" cho nhân viên, hộp duyệt, tổng hợp cho kế toán, email thông báo.

**Không làm (để sau):**
- Chấm công trên web và app; tự đẩy số liệu vào bảng công (chấm công vẫn thủ công).
- Tự tính lương (bảng lương vẫn import Excel).
- App mobile (API viết sao cho app dùng lại được).
- Thai sản và tai nạn lao động (chưa có đơn; nếu cần thì HR ghi bằng "nghỉ không lương" kèm ghi chú).

**Không được xảy ra:**
- Số dư thay đổi mà không biết vì sao.
- Đổi quy tắc làm số liệu năm cũ thay đổi theo.
- Nhân viên thấy lịch hoặc lý do nghỉ của người khác.
- Đơn bị trừ phép vào ngày lễ hoặc ngày nghỉ hằng tuần.
- Duyệt rồi mà lịch hoặc số dư không khớp.

## 3. Quy tắc phép năm

### 3.1 Theo luật [Luật]
- 12 ngày làm việc/năm khi làm đủ 12 tháng (BLLĐ 2019 Điều 113). Người chưa thành niên, khuyết tật hoặc làm nghề nặng nhọc, độc hại được 14 ngày; nghề đặc biệt nặng nhọc được 16 ngày.
- Đủ 5 năm làm cho DO IT thì thêm 1 ngày (Điều 114).
- Ngày lễ, Tết và nghỉ hằng tuần không tính vào phép.
- Tháng có số ngày đi làm cộng ngày nghỉ có lương đạt ≥ 50% số ngày làm việc bình thường thì tính là 1 tháng làm việc (NĐ 145/2020 Điều 66).
- Thời gian vẫn được tính để hưởng phép (NĐ 145/2020 Điều 65):
  - thử việc, nếu sau đó làm tiếp;
  - nghỉ việc riêng có lương;
  - nghỉ không lương được đồng ý, cộng dồn ≤ 1 tháng/năm;
  - nghỉ ốm, cộng dồn ≤ 2 tháng/năm;
  - nghỉ thai sản.
- Nghỉ việc mà chưa nghỉ hết phép thì công ty phải trả tiền những ngày đó (Điều 113).

### 3.2 DO IT
- **[Chốt] Cộng dồn mỗi tháng:** mức năm ÷ 12 mỗi tháng, tức 1 ngày/tháng với 12 ngày/năm.
- **[Chốt] Ứng trước tối đa 1 ngày:** số dư phép năm thấp nhất là −1.
- **[Chốt] Chuyển phép sang năm sau:** phép còn thừa cuối năm chuyển sang năm sau, **dùng đến hết 31/03**, sau đó hết hạn. Khi nghỉ thì trừ phép cũ trước.
- **[Đề xuất] Thời điểm cộng:** việc tự động chạy 00:10 ngày 01 mỗi tháng (giờ Việt Nam) cộng phần của tháng đó, để đầu tháng đã có phép dùng.
- **[Đề xuất] Tháng đầu và tháng cuối:**
  - Tháng vào làm: được cộng nếu số ngày làm việc từ ngày vào đến cuối tháng ≥ 50% số ngày làm việc của tháng.
  - Tháng nghỉ việc: cộng hoặc thu hồi theo cùng quy tắc 50% khi áp dụng nghỉ việc.
- **[Chốt] Ai dùng phần nghỉ phép và đơn từ:** chỉ người loại NHAN_VIEN (gồm cả thời gian thử việc). THUC_TAP và DICH_VU **không dùng phần này**: không có sổ phép, không gửi đơn (kể cả nghỉ ốm, không lương, làm thêm giờ), không thấy trang "Nghỉ phép", không có trong tổng hợp cho kế toán.
- **[Đề xuất] Mức năm của từng người:** mặc định 12; HR đặt được 14 hoặc 16 trên hồ sơ (ô "Mức phép năm"). Đổi mức thì áp dụng từ lần cộng phép tháng kế tiếp; các tháng trước không được cộng bù. Nếu cần thì HR ghi dòng điều chỉnh.
- **[Đề xuất] Thâm niên:**
  - Số năm làm việc tính từ ngày vào công ty; đủ mỗi 5 năm thì mức năm +1.
  - Thay đổi có hiệu lực **từ tháng kế tiếp** sau ngày đủ năm, không truy hồi.
- **[Đề xuất] Đơn vị:**
  - Phép được tính theo nửa ngày (0,5).
  - Phần cộng hằng tháng có thể lẻ (13/12 ngày), nên sổ lưu chính xác 2 chữ số thập phân.
  - Số dư hiển thị làm tròn xuống 0,5; chỉ được xin nghỉ trong phần đã làm tròn đó, cộng thêm 1 ngày được ứng.
- **[Đề xuất] Các giai đoạn không được tính để hưởng phép:**
  - nghỉ không lương vượt 1 tháng/năm;
  - nghỉ ốm vượt 2 tháng/năm.

  Đơn vượt các ngưỡng này làm tháng đó không đạt 50% ngày làm việc. Hệ thống tính lại tháng đó khi chạy cộng phép của **tháng kế tiếp** (không sửa dòng đã ghi, mà ghi một dòng điều chỉnh có lý do). Người xây dựng có thể giữ đơn giản: chỉ kiểm tra quy tắc 50% với ngày nghỉ không lương và nghỉ ốm của chính tháng đó.
- **[Đề xuất] Khi nghỉ việc** (lúc nghỉ việc được áp dụng):
  - Số dư phép năm (gồm phần chuyển sang chưa hết hạn) được chốt thành số "Phép năm chưa sử dụng" trong tổng hợp cho kế toán.
  - Số dư âm thì báo kế toán trừ lương.
  - Đơn đã duyệt có ngày nghỉ sau ngày làm việc cuối thì tự hủy và hoàn lại số dư.
  - Hoàn tác nghỉ việc thì ghi dòng ngược lại.
- **[Đề xuất] Số dư ban đầu:**
  - HR nhập số phép còn lại và giờ nghỉ bù còn lại của từng người tại ngày bắt đầu dùng (một lần, có lý do "Số dư đầu kỳ").
  - Có thể nhập hàng loạt bằng file Excel theo mẫu, tương tự phần nhập nhân viên đã có.
- **[Đề xuất] HR điều chỉnh tay:** cộng hoặc trừ, bắt buộc lý do, ghi vào sổ.

### 3.3 Sổ phép [Chốt cách 1]
Mỗi biến động là một dòng không sửa, không xóa: người, loại quỹ (`PHEP_NAM`, `NGHI_BU`), số lượng (+/−), ngày hiệu lực, lý do, liên kết (đơn, tháng cộng phép, lần nghỉ việc…), người tạo, thời điểm. Muốn sửa sai thì ghi một dòng đảo ngược.

Các lý do:
- `CONG_THANG` — cộng phép tháng
- `DAU_KY` — số dư đầu kỳ
- `DUYET_DON` — trừ khi duyệt đơn
- `HUY_DON` — hoàn lại khi hủy đơn
- `HET_HAN` — hết hạn phép chuyển sang
- `CHUYEN_NAM` — chuyển phép sang năm sau
- `DIEU_CHINH` — HR điều chỉnh
- `NGHI_VIEC` — chốt khi nghỉ việc
- `HOAN_TAC` — hoàn tác nghỉ việc
- `LAM_THEM` — cộng giờ nghỉ bù

Phép chuyển sang năm sau được theo dõi riêng để biết phần nào hết hạn ngày 31/03, ví dụ ghi thành hai dòng: `CHUYEN_NAM` −x của năm cũ và +x "phép năm trước" của năm mới.

Số dư bằng tổng các dòng. Việc tự động chạy lại không cộng trùng: mỗi (người, tháng) chỉ có một dòng `CONG_THANG`.

**[Đề xuất] Hai ngăn trong quỹ phép năm.** Mỗi dòng ghi thêm ngăn `NAM_TRUOC` (phép chuyển sang, hết hạn 31/03) hoặc `NAM_NAY`. Quỹ nghỉ bù dùng cùng cơ chế.
- **Ngày 01/01:**
  - Phần dương còn lại chuyển hết sang ngăn `NAM_TRUOC` của năm mới.
  - Số dư âm (đã ứng) **không** xóa, mà chuyển thành số âm ở ngăn `NAM_NAY` của năm mới; lần cộng phép tháng 1 sẽ bù vào.
- **Khi duyệt đơn:**
  - Trừ ngăn `NAM_TRUOC` trước, nhưng chỉ đến hết phần dương của ngăn đó.
  - Phần còn lại trừ vào `NAM_NAY`; ngăn này có thể xuống tới −1.
  - Ví dụ: chuyển sang 3 ngày, tháng 1 nghỉ 5 ngày thì trừ 3 ở `NAM_TRUOC` và 2 ở `NAM_NAY`.
- **Ngày 01/04:**
  - Phần dương còn lại của `NAM_TRUOC` ghi `HET_HAN` để về 0.
  - Đơn đã duyệt rơi vào ngày trước 01/04 thì đã trừ xong lúc duyệt, nên không bị ảnh hưởng.

Quy tắc mới (đổi mức năm, thâm niên) chỉ ảnh hưởng các dòng **ghi sau thời điểm đổi**, dòng cũ giữ nguyên. Điều này khớp nguyên tắc "thay đổi quy định không áp dụng ngược" của bảng lương.

## 4. Các loại đơn

| Loại | Trừ quỹ | Quy tắc |
|---|---|---|
| Nghỉ phép năm | Phép năm | Cả ngày, sáng hoặc chiều. Không vượt số dư (đã làm tròn) + 1 ngày ứng. |
| Nghỉ việc riêng có lương (Điều 115) [Luật] | Không | Chọn lý do: bản thân kết hôn 3 ngày; con kết hôn 1 ngày; cha, mẹ (cả bên vợ/chồng), vợ/chồng, con mất 3 ngày. Không vượt số ngày của lý do. |
| Nghỉ ốm (BHXH trả 75%) [Luật] | Không | Đính kèm giấy chứng nhận nghỉ hưởng BHXH (PDF/ảnh), tải lên sau cũng được. Có thể nửa ngày. Đếm ngày đã nghỉ trong năm so với mức 30/40/60 ngày theo số năm đóng BHXH (<15 / 15–<30 / ≥30); vượt thì cảnh báo, không chặn. **[Đề xuất]** Hồ sơ thêm ô "Số năm đóng BHXH trước khi vào DO IT" (mặc định 0). Số năm đóng BHXH = ô đó + thời gian từ "Tháng bắt đầu đóng BHXH" (đã có trên hồ sơ) đến nay. Vượt mức chỉ cảnh báo, không chặn. |
| Nghỉ không lương | Không | Kế toán thấy trong tổng hợp để trừ lương. Gồm cả 1 ngày nghỉ không lương theo Điều 115 (ông bà, anh chị em ruột mất; cha mẹ, anh chị em kết hôn). |
| Nghỉ bù | Quỹ nghỉ bù | Cả ngày = 8 giờ, nửa ngày = 4 giờ. Không vượt số giờ trong quỹ (không cho ứng). |
| Làm thêm giờ | Cộng quỹ nghỉ bù hoặc trả tiền | Xem 4.2. |

**[Chốt] Đi công tác không phải đơn nghỉ.** Đi công tác dùng Quyết định cử nhân sự hoặc lịch sử vị trí đã có, chỉ hiển thị trên lịch.

### 4.1 Đơn nghỉ (chung)
- Chọn từ ngày – đến ngày. Nghỉ nửa ngày (sáng/chiều) chỉ áp dụng khi từ ngày = đến ngày. **[Đề xuất]** Đơn nhiều ngày thì nửa ngày chỉ được ở ngày đầu (chiều) hoặc ngày cuối (sáng).
- Số ngày bị trừ = số ngày làm việc trong khoảng, theo lịch làm việc và ngày lễ đã có (`WorkCalendarService`).
- Không cho trùng ngày với một đơn nghỉ khác đang chờ hoặc đã duyệt của cùng người.
- **[Đề xuất]** Được xin cho ngày đã qua (quên xin), tối đa 30 ngày về trước; đơn quá khứ vẫn qua duyệt như thường.
- Không cho xin sau ngày làm việc cuối nếu đã có lịch nghỉ việc.
- **[Đề xuất] Thời điểm trừ quỹ:** quỹ chỉ bị trừ khi đơn **được duyệt**. Khi gửi đơn, hệ thống kiểm tra số dư trừ đi các đơn đang chờ duyệt để không xin vượt.
- **[Đề xuất] Kiểm tra lại khi duyệt:** lúc duyệt, hệ thống kiểm tra lại số dư, có khóa sổ của người đó trong lúc ghi.
  - Nếu số dư đã giảm (HR điều chỉnh, phép hết hạn 01/04) đến mức duyệt sẽ xuống dưới −1, thì **chặn duyệt**.
  - Khi đó báo người duyệt và người gửi số còn lại, để người gửi sửa đơn hoặc HR điều chỉnh.
- **[Đề xuất] Không tính lại tháng cũ:**
  - Phép tháng được cộng theo dữ liệu có tại thời điểm cộng.
  - Đơn quá khứ được duyệt sau đó không làm tính lại tháng đã cộng, đúng nguyên tắc không áp dụng ngược.
  - Nếu cần sửa thì HR ghi một dòng điều chỉnh.

### 4.2 Làm thêm giờ
1. **Đăng ký trước:** ngày, giờ bắt đầu – kết thúc, nội dung công việc. Quản lý duyệt và **[Chốt] quyết định "Trả tiền" hay "Nghỉ bù"**.
2. **Xác nhận sau khi làm:** nhân viên nhập số giờ thực tế, mặc định bằng số giờ đăng ký.
   - **[Đề xuất]** Số giờ thực tế ≤ số giờ đăng ký thì tự chấp nhận; lớn hơn thì quản lý duyệt lại phần chênh.
   - **[Đề xuất]** Giờ nghỉ bù được cộng vào quỹ **khi xác nhận xong**, không phải lúc duyệt đăng ký.
   - **[Đề xuất]** Hạn xác nhận là ngày 05 tháng sau. Quá hạn thì tính bằng số giờ đăng ký.
   - **[Đề xuất]** Quản lý được đổi "Trả tiền" ↔ "Nghỉ bù" cho đến khi kỳ lương của tháng đó được duyệt. Đổi sau khi đã cộng giờ nghỉ bù thì ghi dòng đảo ngược.
3. **Hệ số [Luật]:** tự phân loại theo ngày: thường 150%, nghỉ hằng tuần 200%, lễ 300% (BLLĐ Điều 98). Làm thêm ban đêm (22:00–06:00) **[Đề xuất]** chỉ đánh dấu "có giờ đêm" để kế toán xử lý, chưa tính hệ số riêng.
4. **Giới hạn [Luật]:** **[Đề xuất]** tính trên số giờ đã đăng ký và duyệt (dùng số thực tế khi đã xác nhận), theo **ngày làm**. ≤ 40 giờ/tháng, ≤ 200 giờ/năm (ngành thông thường; 300 giờ/năm chỉ cho một số ngành, DO IT không thuộc diện này **[Đề xuất]**, cấu hình được). Vượt thì chặn ở bước đăng ký.
5. **Nghỉ bù [Chốt]:** cộng giờ thực tế vào quỹ nghỉ bù, không nhân hệ số; **đủ 8 giờ = 1 ngày**. **[Đề xuất]** Giờ nghỉ bù còn thừa chuyển sang năm sau và hết hạn 31/03 như phép năm.
6. **Trả tiền:** vào tổng hợp hằng tháng cho kế toán, tách theo hệ số. **[Đề xuất]** Giờ làm thêm thuộc tháng của **ngày làm**, không phải ngày xác nhận.

## 5. Luồng duyệt

### 5.1 Ai duyệt [Chốt]
| Đơn | Người duyệt |
|---|---|
| Nghỉ ≤ 3 ngày làm việc; làm thêm giờ | **Trưởng nhóm**, tức "Quản lý trực tiếp" trong hồ sơ |
| Nghỉ > 3 ngày làm việc | **Giám đốc** (vai trò BGĐ) |

**[Đề xuất] Các trường hợp đặc biệt:**
- Không có trưởng nhóm → trưởng phòng của phòng người đó.
- Người xin nghỉ là trưởng phòng → giám đốc.
- Người xin nghỉ có vai trò BGĐ → Quản trị ghi nhận.
- Trưởng nhóm không có tài khoản hoặc đã nghỉ việc → lên trưởng phòng, rồi giám đốc.
- **Nhiều người có vai trò BGĐ:** đơn hiện trong hộp duyệt của tất cả, một người duyệt là xong.
- Quản trị và HR **duyệt thay** được mọi đơn, có ghi "duyệt thay".

Người duyệt được tính lúc gửi đơn và lưu trên đơn. Đổi tổ chức sau đó không làm đổi người duyệt của đơn đang chờ, trừ khi HR chuyển tay.

**[Đề xuất]** Khi người duyệt bị cho nghỉ việc (nghỉ việc được áp dụng) hoặc tài khoản bị khóa, việc tự động hằng ngày chuyển các đơn đang chờ của họ lên cấp kế tiếp theo chuỗi trên, ghi lại lý do chuyển.

### 5.2 Trạng thái
`CHO_DUYET` → `DA_DUYET` | `TU_CHOI` (bắt buộc lý do) | `DA_RUT` (người gửi rút khi còn chờ).

Đơn đã duyệt muốn hủy:
- Người gửi tạo yêu cầu hủy (`CHO_HUY`), người duyệt ban đầu đồng ý thì thành `DA_HUY` và hoàn quỹ.
- Ngày nghỉ đã qua thì chỉ HR hủy được.
- Mọi bước ghi người, thời điểm, ghi chú; có lịch sử (Envers).

### 5.3 Thông báo [Đề xuất]
- Email cho người duyệt khi có đơn mới hoặc yêu cầu hủy.
- Email cho người gửi khi đơn được duyệt hoặc bị từ chối.
- Dùng cấu hình email trên giao diện Quản trị đã có. Lỗi gửi thư không làm hỏng việc duyệt.
- **[Chốt]** Trưởng phòng **không** nhận email; chỉ thấy trên lịch.
- **[Đề xuất]** Email tổng hợp nhắc việc hằng ngày (đã có) thêm mục "Đơn chờ bạn duyệt".

## 6. Xem lịch và quyền

### 6.1 Lịch tháng [Chốt kiểu A]
- Lưới 7 cột (T2–CN), mỗi ô một ngày, ghi "Tên · Loại". Ngày đông người thì gộp "+N người", bấm vào xem danh sách.
- Màu theo loại: phép/việc riêng, ốm, không lương, nghỉ bù, đi công tác (từ lịch sử vị trí), nghỉ việc đã duyệt (từ lịch nghỉ việc đã hẹn). Đơn chờ duyệt hiện mờ.
- Thứ Bảy, Chủ nhật và ngày lễ tô nền xám.
- Lọc theo phòng ban, loại; chuyển tháng.
- **[Đề xuất]** Lịch chỉ hiện loại nghỉ, **không hiện lý do**, kể cả với người xem được.

### 6.2 Ai xem lịch của ai [Chốt]
| Người xem | Thấy |
|---|---|
| Nhân viên | Chỉ lịch của chính mình |
| Trưởng nhóm | Người có "Quản lý trực tiếp" là mình |
| Trưởng phòng (ô mới "Trưởng phòng" trên phòng ban) | Cả phòng mình |
| Giám đốc (BGĐ), HR, Quản trị | Toàn công ty |
| Kế toán | **[Đề xuất]** Không xem lịch; xem tổng hợp tháng (6.4) |

Một người vừa là trưởng nhóm vừa là trưởng phòng thì thấy hợp hai tập.

### 6.3 Trang và quyền thao tác
- **"Nghỉ phép"** (mọi người có tài khoản gắn hồ sơ), theo canvas:
  - thẻ số dư: phép năm còn lại (kèm phần năm trước, hạn 31/03), quỹ nghỉ bù (giờ), số ngày ốm đã nghỉ trong năm / mức, số giờ làm thêm tháng này / 40;
  - form tạo đơn, hiện "Sau khi duyệt còn …";
  - bảng đơn đã gửi;
  - lịch của mình;
  - sổ phép của mình (xem được vì sao còn bao nhiêu).
- **"Duyệt đơn"** (ai đang là người duyệt của ít nhất một đơn, cùng HR, Quản trị): đơn chờ duyệt, duyệt hoặc từ chối hàng loạt, xem số dư người xin trước khi duyệt.
- **"Lịch nghỉ"** (theo 6.2).
- **HR/Quản trị:**
  - số dư cả công ty;
  - nhập số dư đầu kỳ;
  - điều chỉnh;
  - xem sổ phép từng người;
  - cài đặt (mức phép mặc định, ngưỡng 3 ngày, giới hạn làm thêm, hạn 31/03);
  - đặt "Trưởng phòng" trên phòng ban;
  - "Mức phép năm" trên hồ sơ.
- **Tab "Nghỉ phép"** trên hồ sơ nhân viên (HR, Quản trị, trưởng nhóm và trưởng phòng của người đó): số dư, đơn, sổ.
- **Lý do nghỉ và giấy ốm:** chỉ người gửi, người duyệt của đơn, HR và Quản trị xem được.
- Quyền kiểm tra ở backend như các phần trước. Trưởng nhóm và trưởng phòng là quan hệ trong dữ liệu, không phải vai trò, nên kiểm tra theo dữ liệu từng đơn.

### 6.4 Tổng hợp cho kế toán [Đề xuất]
Trang và file Excel theo tháng, mỗi người một dòng, gồm:
- ngày nghỉ phép năm, việc riêng có lương, ốm, không lương, nghỉ bù;
- giờ làm thêm được trả tiền theo hệ số 150/200/300 (kèm đánh dấu giờ đêm);
- phép năm chưa dùng chốt khi nghỉ việc (dương: trả tiền; âm: trừ lương).

Quyền: Kế toán, HR, Quản trị.

## 7. Ràng buộc kỹ thuật
- Giữ kiến trúc hiện có: JHipster 9.4.0 / Spring Boot 4 / Java 21, React 19 + react-bootstrap, PostgreSQL + Liquibase (chỉ thêm changeset), Envers cho đơn và cài đặt, `core/` thuần Java (đặt các quy tắc tính toán thuần ở đây để test được), quyền ở backend.
- Ngày làm việc và ngày lễ lấy từ `WorkCalendarService` (đã có). **[Đề xuất]** Dùng một lịch chung cho cả công ty, kể cả khi tính quy tắc 50% của tháng. Hiện không có thứ Bảy làm nửa ngày hay lịch riêng từng người, và chưa cần thêm.
- Việc tự động: cộng phép đầu tháng, hết hạn ngày 01/04, chuyển năm ngày 01/01. Các việc này chạy lại được, không ghi trùng, an toàn khi chạy nhiều bản.
- Lịch tháng tự vẽ bằng lưới, không dùng thư viện lịch ngoài **[Đề xuất]**.
- API đủ cho app mobile sau này dùng: tạo đơn, xem số dư, xem lịch của mình, duyệt.
- Giao diện tiếng Việt, ngày `dd/MM/yyyy`, vùng bấm ≥ 44px, theo màu và font canvas.

## 8. Cách làm đã chọn
- **[Chốt] Sổ phép ghi từng biến động** (mục 3.3), thay vì tính lại mỗi lần xem hoặc một ô số dư sửa đè. Lý do: minh bạch, HR sửa có dấu vết, đổi quy tắc không ảnh hưởng số cũ, dễ đưa số dư đầu kỳ và hết hạn 31/03 vào.
- **[Chốt] Lịch kiểu tháng** (kiểu A), thay vì dòng thời gian theo người.

## 9. Để người xây dựng tự quyết
- Tên bảng, cột, API; cách chia gói trong `core` và `app`.
- Giao diện chi tiết của form, hộp duyệt và trang HR, miễn bám canvas.
- Cách gộp email thông báo.
- Thứ tự chia nhỏ công việc.

## Nguồn
- [luatvietnam – cách tính ngày phép (BLLĐ 113–115, NĐ 145/2020 Điều 65–67)](https://luatvietnam.vn/lao-dong-tien-luong/cach-tinh-ngay-phep-562-19640-article.html)
- [lsvn.vn – cách tính ngày nghỉ hằng năm trường hợp đặc biệt](https://lsvn.vn/cach-tinh-ngay-nghi-hang-nam-trong-mot-so-truong-hop-dac-biet-a94326.html)
- [baolaocai – chế độ ốm đau từ 01/7/2025 (Luật BHXH 2024)](https://baolaocai.vn/tu-172025-nghi-om-nua-ngay-duoc-huong-che-do-om-dau-post399114.html)

Người xây dựng nên đối chiếu văn bản gốc trước khi chốt các con số luật; chúng cũng cần cấu hình được để sửa khi luật đổi.
