package vn.doit.hrm.web.rest;

import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.EmployeeDocumentService;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.leave.LeaveRequestService;
import vn.doit.hrm.service.leave.dto.LeaveBulkApproveDTO;
import vn.doit.hrm.service.leave.dto.LeaveBulkRejectDTO;
import vn.doit.hrm.service.leave.dto.LeaveDecisionDTO;
import vn.doit.hrm.service.leave.dto.LeaveRequestCreateDTO;
import vn.doit.hrm.service.leave.dto.LeaveRequestDTO;

/**
 * Đơn nghỉ ({@code /api/leave/requests}). Mọi tài khoản đăng nhập gọi được (quy tắc {@code /api/leave/**}); quyền từng việc kiểm
 * tra theo dữ liệu ở service: người gửi, người duyệt lưu trên đơn (trưởng nhóm / trưởng phòng), BGĐ với đơn cấp BGĐ, Quản trị với
 * đơn cấp Quản trị, HR / Quản trị duyệt thay. Không ai quyết định đơn của chính mình.
 */
@RestController
@RequestMapping("/api/leave/requests")
public class LeaveRequestResource {

    private static final String HR_OR_ADMIN = "hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR + "')";

    @Value("${spring.servlet.multipart.max-file-size:20MB}")
    private DataSize maxFileSize;

    private final LeaveRequestService service;

    public LeaveRequestResource(LeaveRequestService service) {
        this.service = service;
    }

    /** Gửi đơn (người đăng nhập, loại NHAN_VIEN). Nghỉ ốm vượt hạn mức: {@code sickOverLimit = true}, không chặn. */
    @PostMapping("")
    public ResponseEntity<LeaveRequestDTO> submit(@Valid @RequestBody LeaveRequestCreateDTO dto) {
        LeaveRequestDTO created = service.submit(dto);
        return ResponseEntity.created(URI.create("/api/leave/requests/" + created.id())).body(created);
    }

    @GetMapping("/mine")
    public List<LeaveRequestDTO> mine() {
        return service.mine();
    }

    /** Đơn chờ duyệt / chờ hủy mà người đăng nhập quyết định được. */
    @GetMapping("/inbox")
    public List<LeaveRequestDTO> inbox() {
        return service.inbox();
    }

    @GetMapping("/{id}")
    public LeaveRequestDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/withdraw")
    public LeaveRequestDTO withdraw(@PathVariable("id") Long id) {
        return service.withdraw(id);
    }

    /** Duyệt: trừ quỹ; số dư không đủ → 400 {@code error.insufficientbalance} kèm {@code remaining}. */
    @PostMapping("/{id}/approve")
    public LeaveRequestDTO approve(@PathVariable("id") Long id, @Valid @RequestBody(required = false) LeaveDecisionDTO body) {
        return service.approve(id, note(body));
    }

    /** Từ chối, bắt buộc lý do. */
    @PostMapping("/{id}/reject")
    public LeaveRequestDTO reject(@PathVariable("id") Long id, @Valid @RequestBody(required = false) LeaveDecisionDTO body) {
        return service.reject(id, note(body));
    }

    /** Người gửi yêu cầu hủy đơn đã duyệt (ngày nghỉ chưa bắt đầu). */
    @PostMapping("/{id}/cancel-request")
    public LeaveRequestDTO cancelRequest(@PathVariable("id") Long id) {
        return service.cancelRequest(id);
    }

    @PostMapping("/{id}/cancel-approve")
    public LeaveRequestDTO cancelApprove(@PathVariable("id") Long id, @Valid @RequestBody(required = false) LeaveDecisionDTO body) {
        return service.cancelApprove(id, note(body));
    }

    @PostMapping("/{id}/cancel-reject")
    public LeaveRequestDTO cancelReject(@PathVariable("id") Long id, @Valid @RequestBody(required = false) LeaveDecisionDTO body) {
        return service.cancelReject(id, note(body));
    }

    /** HR / Quản trị hủy đơn, kể cả ngày đã qua; hoàn quỹ nếu đã trừ. */
    @PreAuthorize(HR_OR_ADMIN)
    @PostMapping("/{id}/cancel")
    public LeaveRequestDTO cancel(@PathVariable("id") Long id, @Valid @RequestBody(required = false) LeaveDecisionDTO body) {
        return service.cancelByHr(id, note(body));
    }

    /** Giấy chứng nhận nghỉ hưởng BHXH (multipart {@code file}: PDF / JPEG / PNG, kiểm tra byte đầu file). */
    @PostMapping(value = "/{id}/sick-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LeaveRequestDTO sickDocument(@PathVariable("id") Long id, @RequestParam("file") MultipartFile file) throws IOException {
        if (file.getSize() > maxFileSize.toBytes()) {
            throw new MaxUploadSizeExceededException(maxFileSize.toBytes());
        }
        return service.attachSickDocument(id, file.getOriginalFilename(), file.getContentType(), file.getInputStream());
    }

    /** Tải giấy nghỉ ốm: người gửi, người duyệt của đơn, HR, Quản trị. */
    @GetMapping("/{id}/sick-document")
    public ResponseEntity<Resource> downloadSickDocument(@PathVariable("id") Long id) {
        EmployeeDocumentService.StoredFile f = service.sickDocument(id);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, FileResponses.attachment(f.fileName()))
            .contentType(MediaType.parseMediaType(f.contentType()))
            .contentLength(f.sizeBytes())
            .body(f.resource());
    }

    /** Duyệt nhiều đơn; mỗi đơn một transaction, đơn lỗi không chặn đơn khác. */
    @PostMapping("/bulk-approve")
    public List<LeaveBulkApproveDTO.Result> bulkApprove(@Valid @RequestBody LeaveBulkApproveDTO dto) {
        return each(dto.ids(), id -> service.approve(id, dto.note()));
    }

    /** Từ chối nhiều đơn với cùng lý do (bắt buộc); mỗi đơn một transaction, đơn lỗi không chặn đơn khác. */
    @PostMapping("/bulk-reject")
    public List<LeaveBulkApproveDTO.Result> bulkReject(@Valid @RequestBody LeaveBulkRejectDTO dto) {
        if (dto.note() == null || dto.note().isBlank()) {
            throw new EmployeeRuleException("Phải ghi lý do từ chối", "leavenoterequired");
        }
        return each(dto.ids(), id -> service.reject(id, dto.note()));
    }

    private static List<LeaveBulkApproveDTO.Result> each(List<Long> ids, java.util.function.Consumer<Long> action) {
        List<LeaveBulkApproveDTO.Result> out = new ArrayList<>();
        for (Long id : ids) {
            try {
                action.accept(id);
                out.add(new LeaveBulkApproveDTO.Result(id, true, null, null));
            } catch (EmployeeRuleException e) {
                out.add(new LeaveBulkApproveDTO.Result(id, false, "error." + e.getErrorKey(), e.getMessage()));
            } catch (AccessDeniedException e) {
                out.add(new LeaveBulkApproveDTO.Result(id, false, "error.http.403", e.getMessage()));
            } catch (org.springframework.web.server.ResponseStatusException e) {
                out.add(new LeaveBulkApproveDTO.Result(id, false, "error.http." + e.getStatusCode().value(), e.getReason()));
            }
        }
        return out;
    }

    private static String note(LeaveDecisionDTO body) {
        return body == null ? null : body.note();
    }
}
