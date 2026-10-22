package vn.doit.hrm.service.payslip;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.PayrollLineRecord;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.PayslipDelivery;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.domain.enumeration.PayslipDeliveryStatus;
import vn.doit.hrm.repository.PayrollLineRecordRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.PayrollVersionRepository;
import vn.doit.hrm.repository.PayslipDeliveryRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.payroll.PayrollPeriodService;
import vn.doit.hrm.service.payslip.dto.PayslipStatusDTO;
import vn.doit.hrm.service.pdf.PdfUnavailableException;
import vn.doit.hrm.service.reminder.ReminderMailer;
import vn.doit.hrm.service.reminder.ReminderRecipients;

/**
 * Gửi phiếu lương qua email: bấm gửi thì ghi các lượt {@code PENDING} rồi gửi lần lượt ở luồng nền.
 * Lỗi một người (thiếu email/CCCD, phiếu lệch, dịch vụ PDF, máy chủ mail) chỉ làm lượt của người đó {@code FAILED}.
 * Không gửi trùng: người đang chờ hoặc đã nhận phiếu của phiên bản chính thức bị bỏ qua khi gửi chung.
 */
@Service
public class PayslipSendService {

    private static final Logger LOG = LoggerFactory.getLogger(PayslipSendService.class);

    public static final String REOPENED = "Kỳ đã mở lại, chưa gửi";
    public static final String INTERRUPTED = "Gửi bị gián đoạn, hãy gửi lại";
    private static final int MAX_ERROR = 500;

    private final PayslipDocumentService documents;
    private final PayslipDeliveryRepository deliveryRepository;
    private final PayrollPeriodRepository periodRepository;
    private final PayrollVersionRepository versionRepository;
    private final PayrollLineRecordRepository lineRepository;
    private final PayrollPeriodService periodService;
    private final ReminderMailer mailConfig;
    private final PayslipMailer mailer;
    private final TransactionTemplate tx;
    private final Executor executor;
    private final Clock clock;

    public PayslipSendService(
        PayslipDocumentService documents,
        PayslipDeliveryRepository deliveryRepository,
        PayrollPeriodRepository periodRepository,
        PayrollVersionRepository versionRepository,
        PayrollLineRecordRepository lineRepository,
        PayrollPeriodService periodService,
        ReminderMailer mailConfig,
        PayslipMailer mailer,
        TransactionTemplate tx,
        @Qualifier("taskExecutor") Executor executor,
        Clock clock
    ) {
        this.documents = documents;
        this.deliveryRepository = deliveryRepository;
        this.periodRepository = periodRepository;
        this.versionRepository = versionRepository;
        this.lineRepository = lineRepository;
        this.periodService = periodService;
        this.mailConfig = mailConfig;
        this.mailer = mailer;
        this.tx = tx;
        this.executor = executor;
        this.clock = clock;
    }

    /** Mỗi người có dòng trong phiên bản chính thức, kèm lượt gửi gần nhất. */
    public List<PayslipStatusDTO> status(String period) {
        return tx.execute(s -> {
            PayrollPeriod p = periodRepository.findByPeriod(period).orElseThrow(() -> notFound(period));
            Integer official = periodService.officialVersion(p);
            if (official == null) {
                return List.of();
            }
            Map<Long, PayslipDelivery> latest = latestByEmployee(p.getId());
            return employees(p, official)
                .stream()
                .map(e -> {
                    PayslipDelivery d = latest.get(e.getId());
                    return new PayslipStatusDTO(
                        e.getId(),
                        e.getCode(),
                        e.getFullName(),
                        d != null && d.getEmail() != null ? d.getEmail() : e.getEmail(),
                        d == null ? null : d.getStatus(),
                        d == null ? null : d.getVersion(),
                        d == null ? null : d.getSentAt(),
                        d == null ? null : d.getError(),
                        d != null && d.getStatus() == PayslipDeliveryStatus.SENT && d.getVersion() != official,
                        e.isArchived()
                    );
                })
                .toList();
        });
    }

    /**
     * Ghi lượt gửi rồi gửi ở luồng nền. {@code employeeIds == null} (hoặc rỗng): mọi người chưa nhận phiếu của phiên bản chính thức;
     * danh sách cụ thể: gửi lại đúng những người đó. Người đang chờ gửi luôn bị bỏ qua.
     */
    public List<PayslipStatusDTO> request(String period, List<Long> employeeIds) {
        if (!mailConfig.configured()) {
            throw new EmployeeRuleException("Chưa cấu hình máy chủ gửi email (SMTP).", "mailnotconfigured");
        }
        String requestedBy = SecurityUtils.getCurrentUserLogin().orElse(null);
        Long periodId = tx.execute(s -> {
            // Khóa kỳ: hai lần bấm gửi cùng lúc không tạo lượt trùng.
            PayrollPeriod p = periodRepository.findForUpdate(period).orElseThrow(() -> notFound(period));
            if (p.getStatus() != PayrollPeriodStatus.DA_DUYET) {
                throw new EmployeeRuleException("Kỳ lương chưa được duyệt, chưa gửi hoặc xem phiếu lương được.", "payslipnotapproved");
            }
            int version = periodService.officialVersion(p);
            Map<Long, Employee> inPayroll = new LinkedHashMap<>();
            employees(p, version).forEach(e -> inPayroll.put(e.getId(), e));
            Map<Long, PayslipDelivery> latest = latestByEmployee(p.getId());
            boolean all = employeeIds == null || employeeIds.isEmpty();
            List<Employee> targets = new ArrayList<>();
            if (all) {
                for (Employee e : inPayroll.values()) {
                    PayslipDelivery d = latest.get(e.getId());
                    boolean received = d != null && d.getStatus() == PayslipDeliveryStatus.SENT && d.getVersion() == version;
                    if (!received && !pending(d)) {
                        targets.add(e);
                    }
                }
            } else {
                for (Long id : employeeIds) {
                    Employee e = inPayroll.get(id);
                    if (e == null) {
                        throw new EmployeeRuleException("Người này không có trong bảng lương của kỳ.", "payslipnoline");
                    }
                    if (!pending(latest.get(id))) {
                        targets.add(e);
                    }
                }
            }
            Instant now = Instant.now(clock);
            for (Employee e : targets) {
                PayslipDelivery d = new PayslipDelivery();
                d.setPeriod(p);
                d.setVersion(version);
                d.setEmployee(e);
                d.setStatus(PayslipDeliveryStatus.PENDING);
                d.setRequestedBy(requestedBy);
                d.setRequestedAt(now);
                deliveryRepository.save(d);
            }
            return p.getId();
        });
        executor.execute(() -> sendPending(periodId));
        return status(period);
    }

    /** Gửi lần lượt các lượt đang chờ của kỳ; một luồng tại một thời điểm để không gửi một lượt hai lần. */
    public synchronized void sendPending(Long periodId) {
        List<Long> ids = tx.execute(s ->
            deliveryRepository.findByPeriodIdAndStatusOrderByIdAsc(periodId, PayslipDeliveryStatus.PENDING).stream().map(PayslipDelivery::getId).toList()
        );
        for (Long id : ids) {
            try {
                sendOne(id);
            } catch (RuntimeException e) {
                LOG.warn("Lỗi khi gửi phiếu lương (lượt {}): {}", id, e.getMessage());
                finish(id, PayslipDeliveryStatus.FAILED, e.getMessage());
            }
        }
    }

    /** Lúc khởi động: lượt còn chờ là lượt bị gián đoạn (máy chủ tắt giữa chừng). */
    @EventListener(ApplicationReadyEvent.class)
    public void failInterrupted() {
        try {
            tx.executeWithoutResult(s -> deliveryRepository.failPending(null, INTERRUPTED));
        } catch (RuntimeException e) {
            LOG.warn("Không đánh dấu được lượt gửi phiếu lương bị gián đoạn: {}", e.getMessage());
        }
    }

    private record Job(PayrollPeriod period, int version, Long employeeId, String fullName, String email, String password) {}

    private void sendOne(Long deliveryId) {
        Job job = tx.execute(s -> {
            PayslipDelivery d = deliveryRepository.findById(deliveryId).orElse(null);
            if (d == null || d.getStatus() != PayslipDeliveryStatus.PENDING) {
                return null;
            }
            PayrollPeriod p = d.getPeriod();
            if (p.getStatus() != PayrollPeriodStatus.DA_DUYET || !Integer.valueOf(d.getVersion()).equals(periodService.officialVersion(p))) {
                fail(d, REOPENED);
                return null;
            }
            Employee e = d.getEmployee();
            Optional<String> email = ReminderRecipients.usable(e.getEmail());
            if (email.isEmpty()) {
                fail(d, "Chưa có email trên hồ sơ");
                return null;
            }
            d.setEmail(email.orElseThrow());
            Optional<String> password = PdfPassword.fromIdNumber(e.getIdNumber());
            if (password.isEmpty()) {
                fail(d, "CCCD chưa đủ 6 số");
                return null;
            }
            return new Job(p, d.getVersion(), e.getId(), e.getFullName(), email.orElseThrow(), password.orElseThrow());
        });
        if (job == null) {
            return;
        }
        PayslipDocumentService.Prepared prepared = documents.prepare(job.period(), job.version(), job.employeeId());
        BigDecimal diff = prepared.differenceFromNet();
        if (diff.abs().compareTo(BigDecimal.ONE) > 0) {
            finish(deliveryId, PayslipDeliveryStatus.FAILED, "Phiếu lệch bảng lương " + PayslipText.money(diff.abs()) + " đồng");
            return;
        }
        byte[] pdf;
        try {
            pdf = documents.pdf(prepared);
        } catch (PdfUnavailableException e) {
            finish(deliveryId, PayslipDeliveryStatus.FAILED, "Dịch vụ PDF không chạy");
            return;
        }
        YearMonth ym = prepared.period();
        mailer.send(
            job.email(),
            PayslipText.subject(ym),
            PayslipText.body(job.fullName(), ym),
            prepared.fileName(),
            PdfPassword.encrypt(pdf, job.password())
        );
        finish(deliveryId, PayslipDeliveryStatus.SENT, null);
    }

    private void finish(Long deliveryId, PayslipDeliveryStatus status, String error) {
        tx.executeWithoutResult(s ->
            deliveryRepository
                .findById(deliveryId)
                .ifPresent(d -> {
                    d.setStatus(status);
                    d.setError(error == null ? null : error.length() > MAX_ERROR ? error.substring(0, MAX_ERROR - 1) + "…" : error);
                    if (status == PayslipDeliveryStatus.SENT) {
                        d.setSentAt(Instant.now(clock));
                    }
                })
        );
    }

    private static void fail(PayslipDelivery d, String error) {
        d.setStatus(PayslipDeliveryStatus.FAILED);
        d.setError(error);
    }

    private static boolean pending(PayslipDelivery d) {
        return d != null && d.getStatus() == PayslipDeliveryStatus.PENDING;
    }

    private Map<Long, PayslipDelivery> latestByEmployee(Long periodId) {
        Map<Long, PayslipDelivery> latest = new LinkedHashMap<>();
        deliveryRepository.findByPeriodIdOrderByIdAsc(periodId).forEach(d -> latest.put(d.getEmployee().getId(), d));
        return latest;
    }

    private List<Employee> employees(PayrollPeriod p, int version) {
        return versionRepository
            .findByPeriodIdAndVersion(p.getId(), version)
            .map(v -> lineRepository.findByVersionIdOrderByExcelRowAsc(v.getId()).stream().map(PayrollLineRecord::getEmployee).toList())
            .orElse(List.of());
    }

    private static ResponseStatusException notFound(String period) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có kỳ lương " + period);
    }
}
