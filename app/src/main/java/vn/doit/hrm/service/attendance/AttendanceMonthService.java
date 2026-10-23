package vn.doit.hrm.service.attendance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.attendance.DayCredit;
import vn.doit.hrm.domain.AttendanceMonth;
import vn.doit.hrm.domain.AttendanceMonthEvent;
import vn.doit.hrm.domain.AttendanceSnapshot;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.AttendanceMonthAction;
import vn.doit.hrm.domain.enumeration.AttendanceMonthStatus;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.AttendanceMonthEventRepository;
import vn.doit.hrm.repository.AttendanceMonthRepository;
import vn.doit.hrm.repository.AttendanceSnapshotRepository;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.attendance.AttendanceDayService.DayView;
import vn.doit.hrm.service.attendance.AttendanceDayService.MonthView;
import vn.doit.hrm.service.attendance.AttendanceUnresolvedException.Unresolved;

/**
 * Bảng công tháng (kiểu B), bảng công của tôi, chốt và mở lại (spec 3.4–3.5).
 * <ul>
 *   <li>Xem: Quản trị, HR, BGĐ toàn công ty; Kế toán toàn công ty nhưng chỉ tháng đã chốt; trưởng phòng ({@code Department.head}, hồ sơ
 *   còn làm việc, chưa lưu trữ) các phòng mình; còn lại 403. Mọi người xem được bảng của chính mình ({@link #my}).</li>
 *   <li>Dòng trên bảng: NHAN_VIEN chưa lưu trữ có ít nhất một ngày trong khoảng làm việc của tháng. Ảnh chụp chốt công thêm cả hồ sơ đã
 *   lưu trữ có ngày làm trong tháng (bảng lương tháng đó vẫn cần).</li>
 *   <li>Tháng đã chốt: cột tổng lấy từ ảnh chụp; dòng có số tính lại khác ảnh chụp được đánh dấu {@code changedAfterClose}.</li>
 *   <li>Chốt: chỉ khi tháng đã hết; còn ô đỏ (thiếu giờ, chưa nhập) thì phải {@code force}. Mở lại: cần lý do, kỳ lương tháng đó chưa
 *   duyệt. Mỗi lần chốt / mở lại ghi một dòng lịch sử.</li>
 * </ul>
 */
@Service
@Transactional
public class AttendanceMonthService {

    /** Người trên bảng: mã, họ tên, phòng, trưởng nhóm (để lọc). */
    public record EmployeeRef(Long id, String code, String name, Long departmentId, String departmentName, Long managerId, String managerName) {}

    /** Cột tổng của một dòng; trùng các cột của {@code attendance_snapshot}. */
    public record Totals(
        BigDecimal standardDays,
        BigDecimal actualDays,
        BigDecimal workDays,
        BigDecimal paidLeaveDays,
        BigDecimal unpaidLeaveDays,
        BigDecimal sickDays,
        BigDecimal holidayDays,
        BigDecimal tripDays,
        Integer lateCount,
        Integer lateMinutes,
        Integer earlyCount,
        Integer halfDays,
        Integer absentDays,
        Integer missingDays
    ) {
        static Totals of(MonthView v) {
            return new Totals(
                plain(v.standardDays()),
                plain(v.actualDays()),
                plain(v.workDays()),
                plain(v.paidLeaveDays()),
                plain(v.unpaidLeaveDays()),
                plain(v.sickDays()),
                plain(v.holidayDays()),
                plain(v.tripDays()),
                v.lateCount(),
                v.lateMinutes(),
                v.earlyCount(),
                v.halfDays(),
                v.absentDays(),
                v.missingDays()
            );
        }

        static Totals of(AttendanceSnapshot s) {
            return new Totals(
                plain(s.getStandardDays()),
                plain(s.getActualDays()),
                plain(s.getWorkDays()),
                plain(s.getPaidLeaveDays()),
                plain(s.getUnpaidLeaveDays()),
                plain(s.getSickDays()),
                plain(s.getHolidayDays()),
                plain(s.getTripDays()),
                s.getLateCount(),
                s.getLateMinutes(),
                s.getEarlyCount(),
                s.getHalfDays(),
                s.getAbsentDays(),
                s.getMissingDays()
            );
        }

        /** Cùng số công chuẩn, công thực tế và mọi số đếm. */
        boolean sameAs(Totals o) {
            return (
                eq(standardDays, o.standardDays) &&
                eq(actualDays, o.actualDays) &&
                eq(workDays, o.workDays) &&
                eq(paidLeaveDays, o.paidLeaveDays) &&
                eq(unpaidLeaveDays, o.unpaidLeaveDays) &&
                eq(sickDays, o.sickDays) &&
                eq(holidayDays, o.holidayDays) &&
                eq(tripDays, o.tripDays) &&
                Objects.equals(lateCount, o.lateCount) &&
                Objects.equals(lateMinutes, o.lateMinutes) &&
                Objects.equals(earlyCount, o.earlyCount) &&
                Objects.equals(halfDays, o.halfDays) &&
                Objects.equals(absentDays, o.absentDays) &&
                Objects.equals(missingDays, o.missingDays)
            );
        }

        private static boolean eq(BigDecimal a, BigDecimal b) {
            return a == null ? b == null : b != null && a.compareTo(b) == 0;
        }
    }

    /** Một dòng của bảng tháng: các ngày (tính lại), cột tổng, có thay đổi sau khi chốt. */
    public record BoardRow(EmployeeRef employee, List<DayView> days, Totals totals, boolean changedAfterClose) {}

    /** Bảng công tháng {@code month} (yyyy-MM), trạng thái {@code DANG_MO}/{@code DA_CHOT}. */
    public record BoardDTO(String month, String status, List<BoardRow> rows) {}

    public record MonthStatusDTO(String month, String status) {}

    private static final DateTimeFormatter MM_YYYY = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final DateTimeFormatter DD_MM = DateTimeFormatter.ofPattern("dd/MM");
    private static final Set<String> COMPANY_VIEWERS = Set.of(AuthoritiesConstants.ADMIN, AuthoritiesConstants.HR, AuthoritiesConstants.BGD);

    /** Nhãn ô tự điền, như {@code AUTO_LABEL} ở giao diện. */
    static final Map<String, String> AUTO_LABEL = Map.of(
        "PHEP_NAM",
        "Nghỉ phép năm",
        "NGHI_BU",
        "Nghỉ bù",
        "VIEC_RIENG",
        "Nghỉ việc riêng có lương",
        "OM",
        "Nghỉ ốm",
        "KHONG_LUONG",
        "Nghỉ không lương",
        "CONG_TAC",
        "Công tác",
        "LE",
        "Lễ",
        "NGHI",
        "Nghỉ",
        "NGOAI_HD",
        "Ngoài thời gian làm việc"
    );

    private final AttendanceDayService dayService;
    private final AttendanceAccess access;
    private final AttendanceMonthRepository monthRepository;
    private final AttendanceMonthEventRepository eventRepository;
    private final AttendanceSnapshotRepository snapshotRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PayrollPeriodRepository periodRepository;
    private final Clock clock;

    public AttendanceMonthService(
        AttendanceDayService dayService,
        AttendanceAccess access,
        AttendanceMonthRepository monthRepository,
        AttendanceMonthEventRepository eventRepository,
        AttendanceSnapshotRepository snapshotRepository,
        EmployeeRepository employeeRepository,
        DepartmentRepository departmentRepository,
        PayrollPeriodRepository periodRepository,
        Clock clock
    ) {
        this.dayService = dayService;
        this.access = access;
        this.monthRepository = monthRepository;
        this.eventRepository = eventRepository;
        this.snapshotRepository = snapshotRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.periodRepository = periodRepository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ xem

    @Transactional(readOnly = true)
    public BoardDTO board(String login, YearMonth m, Long departmentId, Long siteId) {
        return board(login, m, departmentId, siteId, null);
    }

    /**
     * Bảng tháng trong phạm vi {@code login} được xem (403 nếu không được xem), lọc theo phòng, khách hàng (onsite ở đó ít nhất một ngày
     * trong tháng) và nhóm (trưởng nhóm {@code managerId}).
     */
    @Transactional(readOnly = true)
    public BoardDTO board(String login, YearMonth m, Long departmentId, Long siteId, Long managerId) {
        boolean closed = isClosed(m);
        Predicate<Employee> scope = viewScope(login, closed);
        List<Employee> people = population(m, false)
            .stream()
            .filter(scope)
            .filter(e -> departmentId == null || (e.getDepartment() != null && departmentId.equals(e.getDepartment().getId())))
            .filter(e -> managerId == null || (e.getManager() != null && managerId.equals(e.getManager().getId())))
            .toList();
        List<BoardRow> rows = rows(people, m, closed);
        if (siteId != null) {
            String group = "SITE:" + siteId;
            rows = rows.stream().filter(r -> r.days().stream().anyMatch(d -> group.equals(d.group()))).toList();
        }
        return new BoardDTO(m.toString(), statusOf(closed), rows);
    }

    /** Bảng tháng của chính {@code login}: một dòng, hoặc không dòng nào nếu không có hồ sơ nhân viên làm việc trong tháng. */
    @Transactional(readOnly = true)
    public BoardDTO my(String login, YearMonth m) {
        boolean closed = isClosed(m);
        Employee me = login == null ? null : employeeRepository.findOneByUserLogin(login).orElse(null);
        List<Employee> people = me != null && me.getPersonType() == PersonType.NHAN_VIEN && inMonth(me, m) ? List.of(me) : List.of();
        return new BoardDTO(m.toString(), statusOf(closed), rows(people, m, closed));
    }

    /** {@code login} được xem bảng công tháng (Quản trị, HR, BGĐ, Kế toán, trưởng phòng): cờ menu {@code viewsAttendanceBoard}. */
    @Transactional(readOnly = true)
    public boolean viewsBoard(String login) {
        if (login == null) {
            return false;
        }
        Set<String> roles = access.roles(login);
        if (roles.stream().anyMatch(COMPANY_VIEWERS::contains) || roles.contains(AuthoritiesConstants.KE_TOAN)) {
            return true;
        }
        return !headedDepartments(login).isEmpty();
    }

    /** Hồ sơ gắn {@code login} là NHAN_VIEN: cờ menu {@code ownsAttendance} ("Bảng công của tôi"). */
    @Transactional(readOnly = true)
    public boolean ownsBoard(String login) {
        return login != null && employeeRepository.findOneByUserLogin(login).map(e -> e.getPersonType() == PersonType.NHAN_VIEN).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isClosed(YearMonth m) {
        return monthRepository.existsByMonthAndStatus(m.toString(), AttendanceMonthStatus.DA_CHOT);
    }

    @Transactional(readOnly = true)
    public MonthStatusDTO status(YearMonth m) {
        return new MonthStatusDTO(m.toString(), statusOf(isClosed(m)));
    }

    /** Ảnh chụp chốt công theo id nhân viên; rỗng khi tháng chưa chốt. */
    @Transactional(readOnly = true)
    public Map<Long, AttendanceSnapshot> snapshot(YearMonth m) {
        if (!isClosed(m)) {
            return Map.of();
        }
        Map<Long, AttendanceSnapshot> out = new LinkedHashMap<>();
        snapshotRepository.findByMonth(m.toString()).forEach(s -> out.put(s.getEmployeeId(), s));
        return out;
    }

    // ------------------------------------------------------------------ chốt, mở lại

    /**
     * Chốt bảng công tháng {@code m}: ghi ảnh chụp cho mọi NHAN_VIEN có ngày làm trong tháng (kể cả hồ sơ đã lưu trữ). Tháng chưa hết →
     * {@code attendancemonthnotover}; đã chốt → {@code attendancealreadyclosed}; còn ô đỏ mà không {@code force} →
     * {@code attendanceunresolved} kèm danh sách.
     */
    public void close(YearMonth m, boolean force) {
        String key = m.toString();
        if (m.atEndOfMonth().isAfter(LocalDate.now(clock))) {
            throw new EmployeeRuleException("Tháng " + m.format(MM_YYYY) + " chưa hết, chưa chốt được bảng công", "attendancemonthnotover");
        }
        AttendanceMonth row = lockOrCreate(key);
        if (row.getStatus() == AttendanceMonthStatus.DA_CHOT) {
            throw new EmployeeRuleException("Bảng công tháng " + m.format(MM_YYYY) + " đã chốt", "attendancealreadyclosed");
        }
        List<Employee> people = population(m, true);
        Map<Long, MonthView> views = dayService.months(people, m);
        List<Unresolved> unresolved = new ArrayList<>();
        for (Employee e : people) {
            List<LocalDate> red = views
                .get(e.getId())
                .days()
                .stream()
                .filter(d -> d.status() == DayCredit.Status.THIEU_GIO || d.status() == DayCredit.Status.CHUA_NHAP)
                .map(DayView::day)
                .toList();
            if (!red.isEmpty()) {
                unresolved.add(new Unresolved(e.getId(), e.getCode(), e.getFullName(), red));
            }
        }
        if (!force && !unresolved.isEmpty()) {
            throw new AttendanceUnresolvedException(unresolvedMessage(m, unresolved), unresolved);
        }
        snapshotRepository.deleteByMonthInBulk(key);
        List<AttendanceSnapshot> snaps = new ArrayList<>();
        for (Employee e : people) {
            snaps.add(snapshotOf(key, e.getId(), views.get(e.getId())));
        }
        snapshotRepository.saveAll(snaps);
        Instant now = Instant.now(clock);
        String actor = actor();
        row.setStatus(AttendanceMonthStatus.DA_CHOT);
        row.setClosedBy(actor);
        row.setClosedAt(now);
        monthRepository.save(row);
        event(key, AttendanceMonthAction.CHOT, actor, now, null);
    }

    /**
     * Mở lại bảng công đã chốt. Thiếu lý do → {@code attendancereasonrequired}; chưa chốt → {@code attendancenotclosed}; kỳ lương tháng đó
     * đã duyệt → {@code attendancepayrollapproved} (Quản trị mở lại kỳ lương trước). Ảnh chụp cũ giữ lại tới lần chốt sau (lúc đó bị
     * thay), nhưng {@link #snapshot} trả rỗng khi tháng đang mở.
     */
    public void reopen(YearMonth m, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new EmployeeRuleException("Cần nhập lý do mở lại bảng công", "attendancereasonrequired");
        }
        String key = m.toString();
        AttendanceMonth row = monthRepository.findForUpdate(key).orElse(null);
        if (row == null || row.getStatus() != AttendanceMonthStatus.DA_CHOT) {
            throw new EmployeeRuleException("Bảng công tháng " + m.format(MM_YYYY) + " chưa chốt", "attendancenotclosed");
        }
        periodRepository
            .findByPeriod(key)
            .filter(p -> p.getStatus() == PayrollPeriodStatus.DA_DUYET)
            .ifPresent(p -> {
                throw new EmployeeRuleException(
                    "Kỳ lương tháng " + m.format(MM_YYYY) + " đã duyệt, không mở lại được bảng công; điều chỉnh ở kỳ sau",
                    "attendancepayrollapproved"
                );
            });
        String text = reason.trim();
        Instant now = Instant.now(clock);
        row.setStatus(AttendanceMonthStatus.DANG_MO);
        row.setClosedBy(null);
        row.setClosedAt(null);
        monthRepository.save(row);
        event(key, AttendanceMonthAction.MO_LAI, actor(), now, text.length() > 500 ? text.substring(0, 500) : text);
    }

    // ------------------------------------------------------------------ Excel

    /** Một trang: mỗi người một dòng, mỗi ngày một cột (giờ vào–ra hoặc nhãn tự điền), rồi các cột tổng. Cùng phạm vi xem với bảng. */
    @Transactional(readOnly = true)
    public byte[] export(String login, YearMonth m) {
        BoardDTO board = board(login, m, null, null, null);
        int days = m.lengthOfMonth();
        List<String> totalHeaders = List.of(
            "Công chuẩn",
            "Công thực tế",
            "Công đi làm",
            "Nghỉ có lương",
            "Nghỉ không lương",
            "Nghỉ ốm",
            "Lễ",
            "Công tác",
            "Số lần muộn",
            "Số phút muộn",
            "Số lần về sớm",
            "Ngày nửa công",
            "Ngày vắng",
            "Ô thiếu giờ / chưa nhập",
            "Thay đổi sau khi chốt"
        );
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Bảng công " + m.format(DateTimeFormatter.ofPattern("MM-yyyy")));
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);
            CellStyle red = wb.createCellStyle();
            red.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            red.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle number = wb.createCellStyle();
            number.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("#,##0.##"));

            Row h = sheet.createRow(0);
            int c = 0;
            for (String t : List.of("Mã", "Họ tên", "Phòng")) {
                headCell(h, c++, t, head);
            }
            for (int d = 1; d <= days; d++) {
                headCell(h, c++, m.atDay(d).format(DD_MM), head);
            }
            for (String t : totalHeaders) {
                headCell(h, c++, t, head);
            }
            int r = 1;
            for (BoardRow x : board.rows()) {
                Row row = sheet.createRow(r++);
                int i = 0;
                row.createCell(i++).setCellValue(x.employee().code());
                row.createCell(i++).setCellValue(x.employee().name());
                row.createCell(i++).setCellValue(x.employee().departmentName() == null ? "" : x.employee().departmentName());
                for (DayView v : x.days()) {
                    Cell cell = row.createCell(i++);
                    cell.setCellValue(cellText(v));
                    if (v.status() == DayCredit.Status.THIEU_GIO || v.status() == DayCredit.Status.CHUA_NHAP) {
                        cell.setCellStyle(red);
                    }
                }
                Totals t = x.totals();
                for (BigDecimal v : List.of(
                    t.standardDays(),
                    t.actualDays(),
                    t.workDays(),
                    t.paidLeaveDays(),
                    t.unpaidLeaveDays(),
                    t.sickDays(),
                    t.holidayDays(),
                    t.tripDays()
                )) {
                    num(row, i++, v, number);
                }
                for (Integer v : new Integer[] { t.lateCount(), t.lateMinutes(), t.earlyCount(), t.halfDays(), t.absentDays(), t.missingDays() }) {
                    num(row, i++, v == null ? null : BigDecimal.valueOf(v), number);
                }
                row.createCell(i).setCellValue(x.changedAfterClose() ? "Có" : "");
            }
            sheet.createFreezePane(3, 1);
            sheet.setColumnWidth(1, 28 * 256);
            sheet.setColumnWidth(2, 20 * 256);
            for (int d = 0; d < days; d++) {
                sheet.setColumnWidth(3 + d, 13 * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Chữ trong ô một ngày: nhãn tự điền, "08:00–17:00", "Vắng", "Chưa nhập"; nghỉ nửa ngày thì thêm loại nghỉ và buổi. */
    static String cellText(DayView v) {
        if (v.autoFilled()) {
            return AUTO_LABEL.getOrDefault(v.autoKind(), v.autoKind());
        }
        String base;
        if (v.markedAbsent()) {
            base = "Vắng";
        } else if (v.status() == DayCredit.Status.CHUA_NHAP) {
            base = "Chưa nhập";
        } else if (v.in() == null && v.out() == null) {
            base = "";
        } else {
            base = hhmm(v.in()) + "–" + hhmm(v.out());
        }
        if (v.leavePart() != null && v.autoKind() != null) {
            String leave = AUTO_LABEL.getOrDefault(v.autoKind(), v.autoKind()) + ("SANG".equals(v.leavePart()) ? " sáng" : " chiều");
            return base.isEmpty() ? leave : leave + " · " + base;
        }
        return base;
    }

    // ------------------------------------------------------------------ nội bộ

    private List<BoardRow> rows(List<Employee> people, YearMonth m, boolean closed) {
        if (people.isEmpty()) {
            return List.of();
        }
        Map<Long, MonthView> views = dayService.months(people, m);
        Map<Long, AttendanceSnapshot> snaps = closed ? snapshot(m) : Map.of();
        List<BoardRow> out = new ArrayList<>();
        for (Employee e : people) {
            MonthView v = views.get(e.getId());
            Totals live = Totals.of(v);
            AttendanceSnapshot s = snaps.get(e.getId());
            Totals totals = closed && s != null ? Totals.of(s) : live;
            boolean changed = closed && (s == null || !totals.sameAs(live));
            out.add(new BoardRow(ref(e), v.days(), totals, changed));
        }
        return out;
    }

    /** Phạm vi xem bảng tháng của {@code login}; không được xem thì 403. */
    private Predicate<Employee> viewScope(String login, boolean closed) {
        Set<String> roles = access.roles(login);
        if (roles.stream().anyMatch(COMPANY_VIEWERS::contains) || (closed && roles.contains(AuthoritiesConstants.KE_TOAN))) {
            return e -> true;
        }
        Set<Long> depts = headedDepartments(login);
        if (depts.isEmpty()) {
            throw new AccessDeniedException(
                roles.contains(AuthoritiesConstants.KE_TOAN) ? "Kế toán chỉ xem bảng công tháng đã chốt" : "Không có quyền xem bảng công tháng"
            );
        }
        return e -> e.getDepartment() != null && depts.contains(e.getDepartment().getId());
    }

    /** Các phòng {@code login} làm trưởng phòng; rỗng nếu hồ sơ đã lưu trữ hoặc đã nghỉ việc. */
    private Set<Long> headedDepartments(String login) {
        Employee me = login == null ? null : employeeRepository.findOneByUserLogin(login).orElse(null);
        if (me == null || me.isArchived() || me.getStatus() == EmploymentStatus.DA_NGHI) {
            return Set.of();
        }
        return departmentRepository.findByHeadId(me.getId()).stream().map(Department::getId).collect(Collectors.toSet());
    }

    /** NHAN_VIEN có ít nhất một ngày trong khoảng làm việc của tháng {@code m}, theo mã; {@code withArchived} gồm hồ sơ đã lưu trữ. */
    private List<Employee> population(YearMonth m, boolean withArchived) {
        return employeeRepository
            .findByPersonType(PersonType.NHAN_VIEN)
            .stream()
            .filter(e -> withArchived || !e.isArchived())
            .filter(e -> inMonth(e, m))
            .sorted(Comparator.comparing(Employee::getCode, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    /** Khoảng ngày vào làm – ngày làm việc cuối giao với tháng {@code m} (đã nghỉ mà không có ngày làm việc cuối: không). */
    static boolean inMonth(Employee e, YearMonth m) {
        if (e.getJoinDate() != null && e.getJoinDate().isAfter(m.atEndOfMonth())) {
            return false;
        }
        if (e.getLastWorkingDay() != null) {
            return !e.getLastWorkingDay().isBefore(m.atDay(1));
        }
        return e.getStatus() != EmploymentStatus.DA_NGHI;
    }

    private AttendanceMonth lockOrCreate(String key) {
        return monthRepository
            .findForUpdate(key)
            .orElseGet(() -> {
                AttendanceMonth row = new AttendanceMonth();
                row.setMonth(key);
                row.setStatus(AttendanceMonthStatus.DANG_MO);
                return monthRepository.saveAndFlush(row);
            });
    }

    private static AttendanceSnapshot snapshotOf(String month, Long employeeId, MonthView v) {
        AttendanceSnapshot s = new AttendanceSnapshot();
        s.setMonth(month);
        s.setEmployeeId(employeeId);
        s.setStandardDays(v.standardDays());
        s.setActualDays(v.actualDays());
        s.setWorkDays(v.workDays());
        s.setPaidLeaveDays(v.paidLeaveDays());
        s.setUnpaidLeaveDays(v.unpaidLeaveDays());
        s.setSickDays(v.sickDays());
        s.setHolidayDays(v.holidayDays());
        s.setTripDays(v.tripDays());
        s.setLateCount(v.lateCount());
        s.setLateMinutes(v.lateMinutes());
        s.setEarlyCount(v.earlyCount());
        s.setHalfDays(v.halfDays());
        s.setAbsentDays(v.absentDays());
        s.setMissingDays(v.missingDays());
        return s;
    }

    private static String unresolvedMessage(YearMonth m, List<Unresolved> list) {
        int shown = Math.min(list.size(), 20);
        String people = list
            .subList(0, shown)
            .stream()
            .map(u -> u.code() + " " + u.name() + ": " + u.dates().stream().map(DD_MM::format).collect(Collectors.joining(", ")))
            .collect(Collectors.joining("; "));
        String more = list.size() > shown ? "; và " + (list.size() - shown) + " người khác" : "";
        return "Bảng công tháng " + m.format(MM_YYYY) + " còn ô thiếu giờ / chưa nhập: " + people + more;
    }

    private void event(String month, AttendanceMonthAction action, String actor, Instant at, String reason) {
        AttendanceMonthEvent e = new AttendanceMonthEvent();
        e.setMonth(month);
        e.setAction(action);
        e.setActor(actor);
        e.setAt(at);
        e.setReason(reason);
        eventRepository.save(e);
    }

    private static String actor() {
        String login = SecurityUtils.getCurrentUserLogin().orElse("system");
        return login.length() > 50 ? login.substring(0, 50) : login;
    }

    private static EmployeeRef ref(Employee e) {
        Department d = e.getDepartment();
        Employee mgr = e.getManager();
        return new EmployeeRef(
            e.getId(),
            e.getCode(),
            e.getFullName(),
            d == null ? null : d.getId(),
            d == null ? null : d.getName(),
            mgr == null ? null : mgr.getId(),
            mgr == null ? null : mgr.getFullName()
        );
    }

    private static String statusOf(boolean closed) {
        return (closed ? AttendanceMonthStatus.DA_CHOT : AttendanceMonthStatus.DANG_MO).name();
    }

    /** Số gọn cho JSON: 21.00 → 21, 20.50 → 20.5 (không dạng 2E+1). */
    static BigDecimal plain(BigDecimal v) {
        if (v == null) {
            return null;
        }
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }

    private static String hhmm(java.time.LocalTime t) {
        return t == null ? "?" : t.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private static void headCell(Row row, int col, String text, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(text);
        c.setCellStyle(style);
    }

    private static void num(Row row, int col, BigDecimal v, CellStyle style) {
        if (v == null) {
            return;
        }
        Cell c = row.createCell(col);
        c.setCellValue(v.doubleValue());
        c.setCellStyle(style);
    }
}
