package vn.doit.hrm.web.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.payroll.PayrollPeriodService;
import vn.doit.hrm.service.payroll.calc.PayrollCalcDraftService;
import vn.doit.hrm.service.payroll.dto.CalcDraftDTO;
import vn.doit.hrm.service.payroll.dto.CalcEditVM;
import vn.doit.hrm.service.payroll.dto.CalcLineDTO;
import vn.doit.hrm.service.payroll.dto.ConfirmedDTO;

/** Bản tính lương nháp: chỉ Kế toán và Quản trị (HR, BGĐ, Nhân viên 403). */
@RestController
@RequestMapping("/api/payroll")
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.KE_TOAN + "')")
public class PayrollCalcResource {

    private final PayrollCalcDraftService service;
    private final PayrollPeriodService periodService;

    public PayrollCalcResource(PayrollCalcDraftService service, PayrollPeriodService periodService) {
        this.service = service;
        this.periodService = periodService;
    }

    /** Tạo bản nháp của kỳ hoặc mở bản đang làm. */
    @PostMapping("/periods/{period}/calc")
    public CalcDraftDTO open(@PathVariable String period) {
        return service.open(period);
    }

    @GetMapping("/calc/{id}")
    public CalcDraftDTO get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/calc/{id}/lines/{lineId}")
    public CalcLineDTO updateLine(@PathVariable Long id, @PathVariable Long lineId, @RequestBody CalcEditVM body) {
        return service.updateLine(id, lineId, body);
    }

    @DeleteMapping("/calc/{id}/lines/{lineId}")
    public ResponseEntity<Void> deleteLine(@PathVariable Long id, @PathVariable Long lineId) {
        service.deleteLine(id, lineId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/calc/{id}/recalculate")
    public CalcDraftDTO recalculate(@PathVariable Long id) {
        return service.recalculate(id);
    }

    @PostMapping("/calc/{id}/acknowledge")
    public CalcDraftDTO acknowledge(@PathVariable Long id) {
        return service.acknowledge(id);
    }

    /** Khôi phục dòng đã bỏ (tính lại dòng đó, bỏ "Đã xem và xác nhận"). */
    @PostMapping("/calc/{id}/lines/{lineId}/restore")
    public CalcDraftDTO restoreLine(@PathVariable Long id, @PathVariable Long lineId) {
        return service.restoreLine(id, lineId);
    }

    /** Hủy bản nháp; "Tính lương" sau đó tạo bản mới. */
    @PostMapping("/calc/{id}/cancel")
    public CalcDraftDTO cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    /** Thân cũ của xác nhận; {@code warningsAcknowledged} không còn dùng (xác nhận cảnh báo qua {@code /acknowledge}). */
    public record ConfirmVM(Boolean warningsAcknowledged) {}

    /**
     * Xác nhận bản nháp thành phiên bản mới của kỳ (kèm file Excel 36 cột). Máy chủ tính lại trước; số đổi → 400 {@code calcstale}
     * (số mới đã lưu). Cảnh báo phải được xác nhận trước qua {@code /acknowledge}, cờ trong thân yêu cầu bị bỏ qua.
     */
    @PostMapping("/calc/{id}/confirm")
    public ResponseEntity<ConfirmedDTO> confirm(@PathVariable Long id, @RequestBody(required = false) ConfirmVM body) {
        ConfirmedDTO done = periodService.confirmCalc(id);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(done);
    }
}
