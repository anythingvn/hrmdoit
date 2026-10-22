package vn.doit.hrm.service.payroll;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.doit.hr.payroll.PayrollField;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.PayrollLineRecord;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.PayrollVersion;
import vn.doit.hrm.repository.PayrollLineRecordRepository;
import vn.doit.hrm.repository.PayrollPeriodEventRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.PayrollVersionRepository;
import vn.doit.hrm.service.FileStorageService;
import vn.doit.hrm.service.payroll.dto.IssueDTO;
import vn.doit.hrm.service.payroll.dto.PayrollViewDTOs;

/** Xem bảng lương đã lưu (Kế toán, Quản trị). Mốc luật và cảnh báo đọc từ bản chụp của phiên bản, không kiểm tra lại. */
@Service
@Transactional(readOnly = true)
public class PayrollViewService {

    private final PayrollPeriodRepository periodRepository;
    private final PayrollVersionRepository versionRepository;
    private final PayrollLineRecordRepository lineRepository;
    private final PayrollPeriodEventRepository eventRepository;
    private final PayrollPeriodService periodService;
    private final FileStorageService storage;
    private final ObjectMapper objectMapper;

    public PayrollViewService(
        PayrollPeriodRepository periodRepository,
        PayrollVersionRepository versionRepository,
        PayrollLineRecordRepository lineRepository,
        PayrollPeriodEventRepository eventRepository,
        PayrollPeriodService periodService,
        FileStorageService storage,
        ObjectMapper objectMapper
    ) {
        this.periodRepository = periodRepository;
        this.versionRepository = versionRepository;
        this.lineRepository = lineRepository;
        this.eventRepository = eventRepository;
        this.periodService = periodService;
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    public List<PayrollViewDTOs.PeriodSummary> periods() {
        return periodRepository
            .findAllByOrderByPeriodDesc()
            .stream()
            .map(p -> {
                Integer official = periodService.officialVersion(p);
                Integer latest = versionRepository.maxVersion(p.getId());
                PayrollLineRecordRepository.Totals t = official == null
                    ? new PayrollLineRecordRepository.Totals(0, BigDecimal.ZERO, BigDecimal.ZERO)
                    : lineRepository.totals(version(p, official).getId());
                return new PayrollViewDTOs.PeriodSummary(
                    p.getPeriod(),
                    p.getStatus(),
                    official,
                    latest,
                    t.headcount(),
                    t.totalNet(),
                    t.totalCost(),
                    p.getApprovedBy(),
                    p.getApprovedAt()
                );
            })
            .toList();
    }

    public PayrollViewDTOs.PeriodDetail detail(String periodText) {
        PayrollPeriod p = period(periodText);
        List<PayrollViewDTOs.Version> versions = versionRepository
            .findByPeriodIdOrderByVersionDesc(p.getId())
            .stream()
            .map(v -> {
                PayrollLineRecordRepository.Totals t = lineRepository.totals(v.getId());
                return new PayrollViewDTOs.Version(
                    v.getVersion(),
                    v.getImportedBy(),
                    v.getImportedAt(),
                    v.getFileName(),
                    t.headcount(),
                    t.totalNet(),
                    t.totalCost(),
                    v.getRulesSnapshot() == null ? null : objectMapper.readValue(v.getRulesSnapshot(), PayrollPeriodService.RulesSnapshot.class),
                    v.getIssuesSnapshot() == null ? List.of() : objectMapper.readValue(v.getIssuesSnapshot(), new TypeReference<List<IssueDTO>>() {})
                );
            })
            .toList();
        List<PayrollViewDTOs.Event> events = eventRepository
            .findByPeriodIdOrderByIdAsc(p.getId())
            .stream()
            .map(e -> new PayrollViewDTOs.Event(e.getAction(), e.getVersion(), e.getActor(), e.getEventAt(), e.getReason()))
            .toList();
        return new PayrollViewDTOs.PeriodDetail(
            p.getPeriod(),
            p.getStatus(),
            periodService.officialVersion(p),
            p.getApprovedVersion(),
            p.getApprovedBy(),
            p.getApprovedAt(),
            versions,
            events
        );
    }

    public List<PayrollViewDTOs.Line> lines(String periodText, int versionNo) {
        PayrollVersion v = version(period(periodText), versionNo);
        return lineRepository.findByVersionIdOrderByExcelRowAsc(v.getId()).stream().map(this::toLine).toList();
    }

    /** File Excel gốc của phiên bản. */
    public record OriginalFile(String fileName, byte[] content) {}

    public OriginalFile file(String periodText, int versionNo) {
        PayrollVersion v = version(period(periodText), versionNo);
        try (InputStream in = storage.load(v.getStoragePath()).getInputStream()) {
            return new OriginalFile(v.getFileName(), in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Lương của một nhân viên theo phiên bản chính thức của từng kỳ, kỳ mới nhất trước. */
    public List<PayrollViewDTOs.EmployeePayroll> forEmployee(Long employeeId) {
        List<PayrollViewDTOs.EmployeePayroll> result = new ArrayList<>();
        Map<Long, Integer> officialByPeriod = new java.util.HashMap<>();
        for (PayrollLineRecord l : lineRepository.findByEmployeeId(employeeId)) {
            PayrollVersion v = l.getVersion();
            PayrollPeriod p = v.getPeriod();
            Integer official = officialByPeriod.computeIfAbsent(p.getId(), k -> periodService.officialVersion(p));
            if (official != null && official == v.getVersion()) {
                result.add(
                    new PayrollViewDTOs.EmployeePayroll(p.getPeriod(), p.getStatus(), v.getVersion(), l.getThucNhan(), l.getTongChiPhi(), l.getThueTncn(), l.getTongBhNld())
                );
            }
        }
        result.sort(Comparator.comparing(PayrollViewDTOs.EmployeePayroll::period).reversed());
        return result;
    }

    public List<PayrollViewDTOs.Field> fields() {
        return Arrays.stream(PayrollField.values()).map(f -> new PayrollViewDTOs.Field(f.name(), f.label())).toList();
    }

    private PayrollViewDTOs.Line toLine(PayrollLineRecord l) {
        Map<String, String> raw = objectMapper.readValue(l.getAmounts(), new TypeReference<Map<String, String>>() {});
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        for (PayrollField f : PayrollField.values()) {
            amounts.put(f.name(), raw.containsKey(f.name()) ? new BigDecimal(raw.get(f.name())) : BigDecimal.ZERO);
        }
        Employee e = l.getEmployee();
        return new PayrollViewDTOs.Line(
            l.getExcelRow(),
            e == null ? null : e.getId(),
            e == null ? l.getFileCode() : e.getCode(),
            e == null ? deletedName(l.getFileName()) : e.getFullName(),
            l.getFileCode(),
            l.getFileName(),
            l.getPosition(),
            l.isMappedManually(),
            amounts,
            l.getThucNhan(),
            l.getTongChiPhi(),
            l.getThueTncn(),
            l.getTongBhNld(),
            e != null && e.isArchived()
        );
    }

    /** Hồ sơ đã bị xóa hẳn: hiện họ tên như trong file lương kèm "(đã xóa hồ sơ)". */
    static String deletedName(String fileName) {
        return (fileName == null || fileName.isBlank() ? "" : fileName.trim() + " ") + "(đã xóa hồ sơ)";
    }

    private PayrollPeriod period(String periodText) {
        String key = PayrollImportService.parsePeriod(periodText).toString();
        return periodRepository.findByPeriod(key).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có kỳ lương này"));
    }

    private PayrollVersion version(PayrollPeriod p, int versionNo) {
        return versionRepository
            .findByPeriodIdAndVersion(p.getId(), versionNo)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có phiên bản này"));
    }
}
