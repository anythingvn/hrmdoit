package vn.doit.hrm.service.reminder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.doit.hr.reminder.WorkCalendar;
import vn.doit.hrm.domain.Holiday;
import vn.doit.hrm.domain.WorkCalendarSetting;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.repository.AttendanceMonthRepository;
import vn.doit.hrm.repository.HolidayRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.WorkCalendarSettingRepository;
import vn.doit.hrm.service.EmployeeRuleException;

/** Lịch làm việc (ngày làm việc trong tuần) và ngày lễ theo năm; đổi lịch chỉ ảnh hưởng các lần nhắc sau. */
@Service
@Transactional
public class WorkCalendarService {

    /** Ngày lễ trong lịch nhắc được nạp quanh "hôm nay" (đủ cho mốc nhắc 45 ngày và nhắc quá hạn 30 ngày). */
    private static final int HOLIDAY_WINDOW_DAYS = 400;

    public record HolidayDTO(Long id, LocalDate day, String name) {}

    private final WorkCalendarSettingRepository settingRepository;
    private final HolidayRepository holidayRepository;

    private final AttendanceMonthRepository attendanceMonthRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;

    public WorkCalendarService(
        WorkCalendarSettingRepository settingRepository,
        HolidayRepository holidayRepository,
        AttendanceMonthRepository attendanceMonthRepository,
        PayrollPeriodRepository payrollPeriodRepository
    ) {
        this.settingRepository = settingRepository;
        this.holidayRepository = holidayRepository;
        this.attendanceMonthRepository = attendanceMonthRepository;
        this.payrollPeriodRepository = payrollPeriodRepository;
    }

    /** Tháng đã chốt công hoặc có kỳ lương đã duyệt: ngày lễ trong tháng này không thêm, đổi tên hay xoá. */
    @Transactional(readOnly = true)
    public boolean isMonthLocked(YearMonth month) {
        String key = month.toString();
        return (
            attendanceMonthRepository.existsByMonthAndStatus(key, AttendanceMonthStatus.DA_CHOT) ||
            payrollPeriodRepository.findByPeriod(key).map(p -> p.getStatus() == PayrollPeriodStatus.DA_DUYET).orElse(false)
        );
    }

    /** Ném 400 {@code holidaymonthlocked} nếu tháng của {@code day} đã khóa. */
    public void assertMonthOpen(LocalDate day) {
        YearMonth month = YearMonth.from(day);
        if (isMonthLocked(month)) {
            throw monthLocked(month);
        }
    }

    public static EmployeeRuleException monthLocked(YearMonth month) {
        return new EmployeeRuleException(monthLockedMessage(month), "holidaymonthlocked");
    }

    public static String monthLockedMessage(YearMonth month) {
        return "Tháng " + month.format(java.time.format.DateTimeFormatter.ofPattern("MM/yyyy")) + " đã chốt công hoặc đã duyệt lương – không sửa ngày lễ";
    }

    @Transactional(readOnly = true)
    public List<DayOfWeek> workingDays() {
        return Arrays.stream(setting().getWorkingDays().split(","))
            .filter(s -> !s.isBlank())
            .map(String::trim)
            .map(DayOfWeek::valueOf)
            .sorted()
            .toList();
    }

    public List<DayOfWeek> setWorkingDays(List<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            throw new EmployeeRuleException("Chọn ít nhất một ngày làm việc trong tuần", "workingdaysrequired");
        }
        setting().setWorkingDays(EnumSet.copyOf(days).stream().map(Enum::name).collect(Collectors.joining(",")));
        return workingDays();
    }

    /** Lịch hiện hành, ngày lễ trong khoảng ±{@value #HOLIDAY_WINDOW_DAYS} ngày quanh {@code around}. */
    @Transactional(readOnly = true)
    public WorkCalendar calendar(LocalDate around) {
        Set<LocalDate> holidays = holidayRepository
            .findByDayBetweenOrderByDayAsc(around.minusDays(HOLIDAY_WINDOW_DAYS), around.plusDays(HOLIDAY_WINDOW_DAYS))
            .stream()
            .map(Holiday::getDay)
            .collect(Collectors.toSet());
        return WorkCalendar.of(EnumSet.copyOf(workingDays()), holidays);
    }

    @Transactional(readOnly = true)
    public boolean isWorkingDay(LocalDate day) {
        return calendar(day).isWorkingDay(day);
    }

    /** {@code day} là ngày lễ đã khai báo (kể cả khi rơi vào ngày nghỉ hằng tuần). */
    @Transactional(readOnly = true)
    public boolean isHoliday(LocalDate day) {
        return holidayRepository.existsByDay(day);
    }

    @Transactional(readOnly = true)
    public List<HolidayDTO> holidays(int year) {
        return holidayRepository
            .findByDayBetweenOrderByDayAsc(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
            .stream()
            .map(WorkCalendarService::toDto)
            .toList();
    }

    public HolidayDTO addHoliday(HolidayDTO dto) {
        check(dto);
        assertMonthOpen(dto.day());
        if (holidayRepository.existsByDay(dto.day())) {
            throw exists(dto.day());
        }
        Holiday h = new Holiday();
        h.setDay(dto.day());
        h.setName(dto.name().trim());
        return toDto(holidayRepository.save(h));
    }

    public HolidayDTO updateHoliday(Long id, HolidayDTO dto) {
        check(dto);
        Holiday h = holidayRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ngày lễ"));
        assertMonthOpen(h.getDay());
        assertMonthOpen(dto.day());
        if (holidayRepository.existsByDayAndIdNot(dto.day(), id)) {
            throw exists(dto.day());
        }
        h.setDay(dto.day());
        h.setName(dto.name().trim());
        return toDto(h);
    }

    public void deleteHoliday(Long id) {
        holidayRepository.findById(id).ifPresent(h -> {
            assertMonthOpen(h.getDay());
            holidayRepository.delete(h);
        });
    }

    /** Chép các ngày lễ của {@code fromYear} sang {@code toYear} (cùng ngày/tháng); bỏ qua ngày đã có và 29/02 năm không nhuận. */
    public List<HolidayDTO> copy(int fromYear, int toYear) {
        List<HolidayDTO> created = new ArrayList<>();
        for (HolidayDTO h : holidays(fromYear)) {
            if (h.day().getMonthValue() == 2 && h.day().getDayOfMonth() == 29 && !java.time.Year.isLeap(toYear)) {
                continue;
            }
            LocalDate target = h.day().withYear(toYear);
            if (!holidayRepository.existsByDay(target)) {
                created.add(addHoliday(new HolidayDTO(null, target, h.name())));
            }
        }
        return created;
    }

    private WorkCalendarSetting setting() {
        return settingRepository.findById(1L).orElseThrow(() -> new IllegalStateException("Thiếu dòng work_calendar_setting id = 1"));
    }

    private static void check(HolidayDTO dto) {
        if (dto.day() == null || dto.name() == null || dto.name().isBlank()) {
            throw new EmployeeRuleException("Cần ngày và tên ngày lễ", "holidayinvalid");
        }
    }

    private static EmployeeRuleException exists(LocalDate day) {
        return new EmployeeRuleException("Ngày " + day.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " đã có trong danh sách ngày lễ", "holidayexists");
    }

    private static HolidayDTO toDto(Holiday h) {
        return new HolidayDTO(h.getId(), h.getDay(), h.getName());
    }
}
