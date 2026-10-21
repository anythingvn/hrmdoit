package vn.doit.hrm.web.rest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.payslip.PayslipDocumentService;
import vn.doit.hrm.service.pdf.PdfUnavailableException;

/** Phiếu lương của kỳ đã duyệt (Quản trị, Kế toán): tải để kiểm tra. */
@RestController
@RequestMapping("/api/payroll/periods/{period}/payslips")
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.KE_TOAN + "')")
public class PayslipResource {

    private final PayslipDocumentService documentService;

    public PayslipResource(PayslipDocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/{employeeId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String period, @PathVariable Long employeeId) {
        PayslipDocumentService.Prepared p = documentService.forReview(period, employeeId);
        return pdfResponse(p.fileName(), toPdf(documentService, p));
    }

    static byte[] toPdf(PayslipDocumentService documentService, PayslipDocumentService.Prepared p) {
        try {
            return documentService.pdf(p);
        } catch (PdfUnavailableException e) {
            throw new EmployeeRuleException("Dịch vụ PDF đang không chạy, chưa tạo được phiếu lương. Thử lại sau.", "pdfunavailable");
        }
    }

    static ResponseEntity<byte[]> pdfResponse(String fileName, byte[] pdf) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, FileResponses.attachment(fileName))
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf);
    }
}
