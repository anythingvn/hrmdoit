package vn.doit.hrm.service.payslip;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.PayslipDelivery;
import vn.doit.hrm.domain.enumeration.PayslipDeliveryStatus;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.PayslipDeliveryRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.payslip.dto.OwnPayslipDTO;

/** Phiếu lương của hồ sơ gắn với tài khoản đang đăng nhập: chỉ các kỳ đã gửi thành công, tải PDF không mật khẩu. */
@Service
public class OwnPayslipService {

    private final EmployeeRepository employeeRepository;
    private final PayslipDeliveryRepository deliveryRepository;
    private final PayslipDocumentService documents;

    private final TransactionTemplate tx;

    public OwnPayslipService(
        EmployeeRepository employeeRepository,
        PayslipDeliveryRepository deliveryRepository,
        PayslipDocumentService documents,
        TransactionTemplate tx
    ) {
        this.employeeRepository = employeeRepository;
        this.deliveryRepository = deliveryRepository;
        this.documents = documents;
        this.tx = tx;
    }

    private record Sent(PayrollPeriod period, int version, Long employeeId) {}

    /** Mới nhất trước; tài khoản không gắn hồ sơ → rỗng. */
    @Transactional(readOnly = true)
    public List<OwnPayslipDTO> list() {
        return sentByPeriod(me()).values().stream().map(d -> new OwnPayslipDTO(d.getPeriod().getPeriod(), d.getVersion(), d.getSentAt())).toList();
    }

    /** Phiếu của kỳ theo phiên bản đã gửi gần nhất; chưa gửi cho mình → 404. Ghi nhật ký truy cập. */
    public PayslipDocumentService.Prepared prepare(String period) {
        Sent sent = tx.execute(s -> {
            Employee e = me().orElseThrow(OwnPayslipService::notFound);
            PayslipDelivery d = Optional.ofNullable(sentByPeriod(Optional.of(e)).get(period)).orElseThrow(OwnPayslipService::notFound);
            return new Sent(d.getPeriod(), d.getVersion(), e.getId());
        });
        PayslipDocumentService.Prepared prepared = documents.prepare(sent.period(), sent.version(), sent.employeeId());
        documents.logAccess(sent.employeeId());
        return prepared;
    }

    private Optional<Employee> me() {
        return SecurityUtils.getCurrentUserLogin().flatMap(employeeRepository::findOneByUserLogin);
    }

    /** Kỳ (yyyy-MM) → lượt gửi thành công gần nhất. */
    private Map<String, PayslipDelivery> sentByPeriod(Optional<Employee> e) {
        Map<String, PayslipDelivery> out = new LinkedHashMap<>();
        e.ifPresent(emp ->
            deliveryRepository
                .findByEmployeeIdAndStatusOrderByIdDesc(emp.getId(), PayslipDeliveryStatus.SENT)
                .stream()
                .sorted((a, b) -> b.getPeriod().getPeriod().compareTo(a.getPeriod().getPeriod()))
                .forEach(d -> out.putIfAbsent(d.getPeriod().getPeriod(), d))
        );
        return out;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có phiếu lương này");
    }
}
