package vn.doit.hrm.web.rest;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.doit.hrm.service.leave.OvertimeService;
import vn.doit.hrm.service.leave.dto.OvertimeConfirmDTO;
import vn.doit.hrm.service.leave.dto.OvertimeCreateDTO;
import vn.doit.hrm.service.leave.dto.OvertimeDecisionDTO;
import vn.doit.hrm.service.leave.dto.OvertimeRequestDTO;

/**
 * Đơn làm thêm giờ ({@code /api/leave/overtime}). Mọi tài khoản đăng nhập gọi được (quy tắc {@code /api/leave/**}); quyền từng việc
 * kiểm tra theo dữ liệu ở service: người gửi (rút, xác nhận giờ), người duyệt lưu trên đơn, BGĐ / Quản trị theo cấp, HR / Quản trị
 * duyệt thay. Không ai quyết định đơn của chính mình. Người không thuộc loại NHAN_VIEN → 403.
 */
@RestController
@RequestMapping("/api/leave/overtime")
public class OvertimeResource {

    private final OvertimeService service;

    public OvertimeResource(OvertimeService service) {
        this.service = service;
    }

    /** Đăng ký; vượt giới hạn tháng / năm → 400 {@code error.overtimelimit} (câu báo có số giờ). */
    @PostMapping("")
    public ResponseEntity<OvertimeRequestDTO> submit(@Valid @RequestBody OvertimeCreateDTO dto) {
        OvertimeRequestDTO created = service.submit(dto);
        return ResponseEntity.created(URI.create("/api/leave/overtime/" + created.id())).body(created);
    }

    @GetMapping("/mine")
    public List<OvertimeRequestDTO> mine() {
        return service.mine();
    }

    /** Đơn chờ duyệt / chờ xác nhận phần chênh mà người đăng nhập quyết định được. */
    @GetMapping("/inbox")
    public List<OvertimeRequestDTO> inbox() {
        return service.inbox();
    }

    @GetMapping("/{id}")
    public OvertimeRequestDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    /** Duyệt, bắt buộc {@code compensation} (TRA_TIEN / NGHI_BU). */
    @PostMapping("/{id}/approve")
    public OvertimeRequestDTO approve(@PathVariable("id") Long id, @Valid @RequestBody(required = false) OvertimeDecisionDTO body) {
        return service.approve(id, body == null ? null : body.compensation(), note(body));
    }

    /** Từ chối, bắt buộc lý do. */
    @PostMapping("/{id}/reject")
    public OvertimeRequestDTO reject(@PathVariable("id") Long id, @Valid @RequestBody(required = false) OvertimeDecisionDTO body) {
        return service.reject(id, note(body));
    }

    @PostMapping("/{id}/withdraw")
    public OvertimeRequestDTO withdraw(@PathVariable("id") Long id) {
        return service.withdraw(id);
    }

    /** Người gửi xác nhận giờ thực tế sau khi làm. */
    @PostMapping("/{id}/confirm")
    public OvertimeRequestDTO confirm(@PathVariable("id") Long id, @RequestBody(required = false) OvertimeConfirmDTO body) {
        return service.confirm(id, body == null ? null : body.actualHours());
    }

    /** Đồng ý phần giờ thực tế vượt giờ đăng ký. */
    @PostMapping("/{id}/approve-extra")
    public OvertimeRequestDTO approveExtra(@PathVariable("id") Long id, @Valid @RequestBody(required = false) OvertimeDecisionDTO body) {
        return service.approveExtra(id, note(body));
    }

    /** Không đồng ý phần vượt: giữ giờ đăng ký. */
    @PostMapping("/{id}/reject-extra")
    public OvertimeRequestDTO rejectExtra(@PathVariable("id") Long id, @Valid @RequestBody(required = false) OvertimeDecisionDTO body) {
        return service.rejectExtra(id, note(body));
    }

    /** Đổi trả tiền ↔ nghỉ bù (người duyệt, HR, Quản trị) khi kỳ lương tháng của ngày làm chưa duyệt. */
    @PostMapping("/{id}/compensation")
    public OvertimeRequestDTO compensation(@PathVariable("id") Long id, @Valid @RequestBody(required = false) OvertimeDecisionDTO body) {
        return service.changeCompensation(id, body == null ? null : body.compensation(), note(body));
    }

    private static String note(OvertimeDecisionDTO body) {
        return body == null ? null : body.note();
    }
}
