package vn.doit.hrm.service.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.attendance.DayCredit;
import vn.doit.hrm.domain.AttendanceSchedule;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.OnsiteLead;
import vn.doit.hrm.domain.WorkSite;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;
import vn.doit.hrm.domain.enumeration.WorkLocationType;
import vn.doit.hrm.repository.AttendanceMonthRepository;
import vn.doit.hrm.repository.AttendanceScheduleRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.OnsiteLeadRepository;
import vn.doit.hrm.repository.WorkSiteRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.ArchivedEmployeeGuard;
import vn.doit.hrm.service.EmployeeRuleException;

/**
 * Bộ giờ làm theo ngày hiệu lực và trưởng nhóm onsite.
 * <ul>
 *   <li>Ngày D dùng bộ giờ có {@code effectiveFrom} lớn nhất nhưng không sau D.</li>
 *   <li>Khách hàng ({@link WorkLocationType#KHACH_HANG}) có thể có bộ giờ riêng (giờ vào, giờ ra, nghỉ trưa); chưa có thì dùng giờ công
 *   ty. Phút muộn cho phép và mốc nửa ngày luôn lấy theo công ty.</li>
 *   <li>Không thêm, sửa hoặc xóa bộ giờ có ngày hiệu lực rơi vào tháng đã chốt (400 {@code attendancescheduleclosedmonth}); chỉ xóa
 *   được bộ giờ chưa tới ngày hiệu lực (400 {@code attendancescheduledeletepast}).</li>
 * </ul>
 */
@Service
@Transactional
public class AttendanceScheduleService {

    public record ScheduleDTO(
        Long id,
        LocalDate effectiveFrom,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        @JsonFormat(pattern = "HH:mm") LocalTime breakStart,
        @JsonFormat(pattern = "HH:mm") LocalTime breakEnd,
        Integer graceMinutes,
        Integer halfDayThresholdMinutes,
        Long workSiteId
    ) {}

    public record OnsiteLeadDTO(Long employeeId, String code, String fullName) {}

    private static final java.time.format.DateTimeFormatter MONTH = java.time.format.DateTimeFormatter.ofPattern("MM/yyyy");

    private final AttendanceScheduleRepository scheduleRepository;
    private final AttendanceMonthRepository monthRepository;
    private final OnsiteLeadRepository onsiteLeadRepository;
    private final WorkSiteRepository workSiteRepository;
    private final EmployeeRepository employeeRepository;
    private final ArchivedEmployeeGuard archivedGuard;
    private final Clock clock;

    public AttendanceScheduleService(
        AttendanceScheduleRepository scheduleRepository,
        AttendanceMonthRepository monthRepository,
        OnsiteLeadRepository onsiteLeadRepository,
        WorkSiteRepository workSiteRepository,
        EmployeeRepository employeeRepository,
        ArchivedEmployeeGuard archivedGuard,
        Clock clock
    ) {
        this.scheduleRepository = scheduleRepository;
        this.monthRepository = monthRepository;
        this.onsiteLeadRepository = onsiteLeadRepository;
        this.workSiteRepository = workSiteRepository;
        this.employeeRepository = employeeRepository;
        this.archivedGuard = archivedGuard;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ tra cứu

    /** Giờ vào, giờ ra, nghỉ trưa của ngày {@code day}; khách hàng không có bộ giờ riêng hiệu lực ngày đó thì trả giờ công ty. */
    @Transactional(readOnly = true)
    public DayCredit.Hours hoursFor(LocalDate day, Long workSiteIdOrNull) {
        AttendanceSchedule s = null;
        if (workSiteIdOrNull != null) {
            s = scheduleRepository
                .findFirstByWorkSiteIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(workSiteIdOrNull, day)
                .orElse(null);
        }
        if (s == null) {
            s = company(day);
        }
        return new DayCredit.Hours(s.getStartTime(), s.getEndTime(), s.getBreakStart(), s.getBreakEnd());
    }

    /** Phút muộn cho phép và mốc nửa ngày của công ty hiệu lực ngày {@code day}. */
    @Transactional(readOnly = true)
    public DayCredit.Rules rules(LocalDate day) {
        AttendanceSchedule s = company(day);
        return new DayCredit.Rules(s.getGraceMinutes(), s.getHalfDayThresholdMinutes());
    }

    /** Các khách hàng mà {@code employeeId} là trưởng nhóm onsite. */
    @Transactional(readOnly = true)
    public Set<Long> onsiteLeadSiteIds(Long employeeId) {
        if (employeeId == null) {
            return Set.of();
        }
        return onsiteLeadRepository
            .findByEmployeeId(employeeId)
            .stream()
            .map(OnsiteLead::getWorkSiteId)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private AttendanceSchedule company(LocalDate day) {
        return scheduleRepository
            .findFirstByWorkSiteIdIsNullAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(day)
            .orElseThrow(() -> new IllegalStateException("Chưa có bộ giờ làm của công ty hiệu lực ngày " + day));
    }

    // ------------------------------------------------------------------ cài đặt bộ giờ

    /** Bộ giờ của công ty ({@code siteId} null) hoặc của một khách hàng, mới nhất trước. */
    @Transactional(readOnly = true)
    public List<ScheduleDTO> list(Long siteId) {
        List<AttendanceSchedule> rows = siteId == null
            ? scheduleRepository.findByWorkSiteIdIsNullOrderByEffectiveFromDesc()
            : scheduleRepository.findByWorkSiteIdOrderByEffectiveFromDesc(siteId);
        return rows.stream().map(AttendanceScheduleService::toDto).toList();
    }

    /** Thêm bộ giờ; đã có bộ giờ cùng phạm vi, cùng ngày hiệu lực thì sửa bộ giờ đó. */
    public ScheduleDTO save(ScheduleDTO body) {
        if (body == null) {
            throw invalid("Nhập đủ bộ giờ làm");
        }
        Long siteId = body.workSiteId();
        if (siteId != null) {
            WorkSite site = workSiteRepository.findById(siteId).orElseThrow(AttendanceScheduleService::invalidSite);
            if (site.getType() != WorkLocationType.KHACH_HANG) {
                throw invalidSite();
            }
        }
        validate(body, siteId == null);
        requireOpenMonths(siteId, body.effectiveFrom());
        AttendanceSchedule s = (siteId == null
                ? scheduleRepository.findByWorkSiteIdIsNullAndEffectiveFrom(body.effectiveFrom())
                : scheduleRepository.findByWorkSiteIdAndEffectiveFrom(siteId, body.effectiveFrom()))
            .orElseGet(AttendanceSchedule::new);
        s.setWorkSiteId(siteId);
        s.setEffectiveFrom(body.effectiveFrom());
        s.setStartTime(body.startTime());
        s.setEndTime(body.endTime());
        s.setBreakStart(body.breakStart());
        s.setBreakEnd(body.breakEnd());
        // Bộ giờ của khách hàng không có phút muộn cho phép và mốc nửa ngày (luôn theo công ty).
        s.setGraceMinutes(siteId == null ? body.graceMinutes() : null);
        s.setHalfDayThresholdMinutes(siteId == null ? body.halfDayThresholdMinutes() : null);
        s.setUpdatedBy(SecurityUtils.getCurrentUserLogin().orElse("system"));
        s.setUpdatedAt(Instant.now(clock));
        return toDto(scheduleRepository.save(s));
    }

    /** Xóa bộ giờ chưa tới ngày hiệu lực. */
    public void delete(Long id) {
        AttendanceSchedule s = scheduleRepository
            .findById(id)
            .orElseThrow(() -> new EmployeeRuleException("Không tìm thấy bộ giờ làm", "idnotfound"));
        if (!s.getEffectiveFrom().isAfter(LocalDate.now(clock))) {
            throw new EmployeeRuleException(
                "Chỉ xóa được bộ giờ chưa tới ngày hiệu lực; muốn đổi giờ thì thêm bộ giờ mới",
                "attendancescheduledeletepast"
            );
        }
        requireOpenMonths(s.getWorkSiteId(), s.getEffectiveFrom());
        scheduleRepository.delete(s);
    }

    /**
     * Bộ giờ (cùng phạm vi: công ty hoặc cùng khách hàng) bắt đầu từ {@code from} quyết định giờ của mọi tháng từ tháng của
     * {@code from} tới tháng có ngày trước ngày hiệu lực của bộ giờ kế tiếp (không có bộ giờ kế tiếp thì không giới hạn). Thêm, sửa hay xóa nó mà phạm vi đó phủ một tháng
     * đã chốt thì đổi công tháng đã chốt: chặn bằng 400 {@code attendancescheduleclosedmonth}.
     */
    private void requireOpenMonths(Long siteId, LocalDate from) {
        LocalDate next = (siteId == null
                ? scheduleRepository.findFirstByWorkSiteIdIsNullAndEffectiveFromGreaterThanOrderByEffectiveFromAsc(from)
                : scheduleRepository.findFirstByWorkSiteIdAndEffectiveFromGreaterThanOrderByEffectiveFromAsc(siteId, from))
            .map(AttendanceSchedule::getEffectiveFrom)
            .orElse(null);
        List<String> closed = monthRepository.findMonthsInRange(
            AttendanceMonthStatus.DA_CHOT,
            YearMonth.from(from).toString(),
            // Tháng cuối cùng còn ngày dùng bộ giờ này là tháng của (bộ giờ kế tiếp − 1 ngày).
            next == null ? null : YearMonth.from(next.minusDays(1)).plusMonths(1).toString()
        );
        if (!closed.isEmpty()) {
            throw new EmployeeRuleException(
                "Bảng công tháng " + YearMonth.parse(closed.get(0)).format(MONTH) +
                " đã chốt, không đổi được bộ giờ làm đang áp dụng cho tháng này",
                "attendancescheduleclosedmonth"
            );
        }
    }

    private static void validate(ScheduleDTO b, boolean company) {
        if (
            b.effectiveFrom() == null ||
            b.startTime() == null ||
            b.endTime() == null ||
            b.breakStart() == null ||
            b.breakEnd() == null
        ) {
            throw invalid("Nhập đủ ngày hiệu lực, giờ vào, giờ ra và giờ nghỉ trưa");
        }
        if (!b.startTime().isBefore(b.endTime())) {
            throw invalid("Giờ ra phải sau giờ vào");
        }
        if (!b.breakStart().isBefore(b.breakEnd())) {
            throw invalid("Giờ kết thúc nghỉ trưa phải sau giờ bắt đầu nghỉ trưa");
        }
        if (b.breakStart().isBefore(b.startTime()) || b.breakEnd().isAfter(b.endTime())) {
            throw invalid("Giờ nghỉ trưa phải nằm trong giờ làm");
        }
        if (company) {
            if (b.graceMinutes() == null || b.halfDayThresholdMinutes() == null) {
                throw invalid("Nhập số phút muộn cho phép và mốc nửa ngày");
            }
            if (b.graceMinutes() < 0 || b.halfDayThresholdMinutes() <= b.graceMinutes()) {
                throw invalid("Mốc nửa ngày phải lớn hơn số phút muộn cho phép (không âm)");
            }
        }
    }

    private static EmployeeRuleException invalid(String message) {
        return new EmployeeRuleException(message, "attendancescheduleinvalid");
    }

    private static EmployeeRuleException invalidSite() {
        return new EmployeeRuleException(
            "Chỉ nơi làm việc loại khách hàng có bộ giờ riêng và trưởng nhóm onsite",
            "attendancescheduleinvalidsite"
        );
    }

    private static ScheduleDTO toDto(AttendanceSchedule s) {
        return new ScheduleDTO(
            s.getId(),
            s.getEffectiveFrom(),
            s.getStartTime(),
            s.getEndTime(),
            s.getBreakStart(),
            s.getBreakEnd(),
            s.getGraceMinutes(),
            s.getHalfDayThresholdMinutes(),
            s.getWorkSiteId()
        );
    }

    // ------------------------------------------------------------------ trưởng nhóm onsite

    @Transactional(readOnly = true)
    public List<OnsiteLeadDTO> onsiteLeads(Long siteId) {
        Set<Long> ids = onsiteLeadRepository
            .findByWorkSiteId(siteId)
            .stream()
            .map(OnsiteLead::getEmployeeId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return employeeRepository
            .findAllById(ids)
            .stream()
            .sorted(Comparator.comparing(Employee::getFullName, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(e -> new OnsiteLeadDTO(e.getId(), e.getCode(), e.getFullName()))
            .toList();
    }

    /**
     * Thay cả danh sách trưởng nhóm onsite của khách hàng {@code siteId}. Người mới thêm phải là hồ sơ chưa lưu trữ (400
     * {@code employeearchived}); người đã có trong danh sách giữ nguyên.
     */
    public List<OnsiteLeadDTO> setOnsiteLeads(Long siteId, List<Long> employeeIds) {
        WorkSite site = workSiteRepository
            .findById(siteId)
            .orElseThrow(() -> new EmployeeRuleException("Không tìm thấy nơi làm việc", "idnotfound"));
        if (site.getType() != WorkLocationType.KHACH_HANG) {
            throw invalidSite();
        }
        Set<Long> wanted = new LinkedHashSet<>();
        if (employeeIds != null) {
            employeeIds.stream().filter(java.util.Objects::nonNull).forEach(wanted::add);
        }
        Map<Long, OnsiteLead> current = onsiteLeadRepository
            .findByWorkSiteId(siteId)
            .stream()
            .collect(Collectors.toMap(OnsiteLead::getEmployeeId, Function.identity()));
        for (Long id : wanted) {
            if (current.containsKey(id)) {
                continue;
            }
            if (!employeeRepository.existsById(id)) {
                throw new EmployeeRuleException("Không tìm thấy nhân viên", "idnotfound");
            }
            archivedGuard.requireActive(id);
        }
        current
            .values()
            .stream()
            .filter(l -> !wanted.contains(l.getEmployeeId()))
            .forEach(onsiteLeadRepository::delete);
        onsiteLeadRepository.flush();
        for (Long id : wanted) {
            if (!current.containsKey(id)) {
                onsiteLeadRepository.save(new OnsiteLead(siteId, id));
            }
        }
        return onsiteLeads(siteId);
    }
}
