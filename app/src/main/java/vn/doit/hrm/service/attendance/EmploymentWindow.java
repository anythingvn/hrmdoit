package vn.doit.hrm.service.attendance;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.EmploymentPeriod;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmploymentPeriodRepository;

/**
 * Những khoảng ngày một hồ sơ đang trong hợp đồng: lần làm việc hiện tại (ngày vào làm – ngày làm việc cuối; đã nghỉ mà không có ngày
 * làm việc cuối thì không có) cộng các lần làm việc trước là nhân viên ({@link EmploymentPeriod} loại NHAN_VIEN hoặc chưa ghi loại,
 * ghi khi nhận làm lại). Nhận làm lại không xóa công
 * của lần làm trước: ngày thuộc lần trước vẫn chấm, vẫn tính, vẫn vào ảnh chụp; khoảng nghỉ giữa hai lần là "ngoài hợp đồng".
 *
 * <p>Dùng chung cho quyền gõ ({@link AttendanceAccess}), ô "ngoài hợp đồng" ({@link AttendanceDayService}) và người có trong bảng / ảnh
 * chụp tháng ({@link AttendanceMonthService}).
 */
@Component
@Transactional(readOnly = true)
public class EmploymentWindow {

    /** Một khoảng làm việc; {@code from} hoặc {@code to} null là không giới hạn phía đó. */
    public record Span(LocalDate from, LocalDate to) {
        public boolean covers(LocalDate d) {
            return (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to));
        }

        public boolean overlaps(LocalDate a, LocalDate b) {
            return (from == null || !from.isAfter(b)) && (to == null || !to.isBefore(a));
        }
    }

    /** Các khoảng làm việc của một nhóm người, nạp một lần. */
    public static final class Spans {

        private final Map<Long, List<Span>> previous;

        private Spans(Map<Long, List<Span>> previous) {
            this.previous = previous;
        }

        /** Mọi khoảng làm việc của {@code e}: các lần trước rồi lần hiện tại. */
        public List<Span> of(Employee e) {
            List<Span> out = new ArrayList<>(previous.getOrDefault(e.getId(), List.of()));
            Span current = current(e);
            if (current != null) {
                out.add(current);
            }
            return out;
        }

        /** Ngày {@code d} nằm trong một lần làm việc của {@code e}. */
        public boolean covers(Employee e, LocalDate d) {
            return of(e).stream().anyMatch(s -> s.covers(d));
        }

        /** {@code e} có ít nhất một ngày làm việc trong tháng {@code m}. */
        public boolean worksIn(Employee e, YearMonth m) {
            return of(e).stream().anyMatch(s -> s.overlaps(m.atDay(1), m.atEndOfMonth()));
        }
    }

    private final EmploymentPeriodRepository periodRepository;

    public EmploymentWindow(EmploymentPeriodRepository periodRepository) {
        this.periodRepository = periodRepository;
    }

    /** Nạp các lần làm việc trước của {@code employees} một lần. */
    public Spans of(Collection<Employee> employees) {
        List<Long> ids = employees.stream().map(Employee::getId).filter(Objects::nonNull).distinct().toList();
        Map<Long, List<Span>> previous = new HashMap<>();
        if (!ids.isEmpty()) {
            for (EmploymentPeriod p : periodRepository.findByEmployeeIdIn(ids)) {
                // Chỉ lần làm việc là nhân viên (dòng cũ chưa ghi loại: coi là nhân viên); thực tập, HĐ dịch vụ không có bảng công (R27).
                if (p.getPersonType() != null && p.getPersonType() != PersonType.NHAN_VIEN) {
                    continue;
                }
                previous.computeIfAbsent(p.getEmployeeId(), k -> new ArrayList<>()).add(new Span(p.getJoinDate(), p.getLastWorkingDay()));
            }
        }
        return new Spans(previous);
    }

    /** Ngày {@code d} nằm trong một lần làm việc của {@code e}. */
    public boolean covers(Employee e, LocalDate d) {
        return of(List.of(e)).covers(e, d);
    }

    /** {@code e} có ít nhất một ngày làm việc trong tháng {@code m}. */
    public boolean worksIn(Employee e, YearMonth m) {
        return of(List.of(e)).worksIn(e, m);
    }

    /** Lần làm việc hiện tại; null khi đã nghỉ mà không có ngày làm việc cuối. */
    static Span current(Employee e) {
        if (e.getLastWorkingDay() != null) {
            return new Span(e.getJoinDate(), e.getLastWorkingDay());
        }
        return e.getStatus() == EmploymentStatus.DA_NGHI ? null : new Span(e.getJoinDate(), null);
    }
}
