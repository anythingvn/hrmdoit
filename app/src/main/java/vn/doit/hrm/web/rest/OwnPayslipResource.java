package vn.doit.hrm.web.rest;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.doit.hrm.service.payslip.OwnPayslipService;
import vn.doit.hrm.service.payslip.PayslipDocumentService;
import vn.doit.hrm.service.payslip.dto.OwnPayslipDTO;

/** Phiếu lương của chính mình (mọi tài khoản có hồ sơ gắn), ở Hồ sơ của tôi. */
@RestController
@RequestMapping("/api/account/employee/payslips")
public class OwnPayslipResource {

    private final OwnPayslipService service;
    private final PayslipDocumentService documents;

    public OwnPayslipResource(OwnPayslipService service, PayslipDocumentService documents) {
        this.service = service;
        this.documents = documents;
    }

    @GetMapping("")
    public List<OwnPayslipDTO> list() {
        return service.list();
    }

    @GetMapping("/{period}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String period) {
        PayslipDocumentService.Prepared p = service.prepare(period);
        return PayslipResource.pdfResponse(p.fileName(), PayslipResource.toPdf(documents, p));
    }
}
