package vn.doit.hrm.service.leave;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.doit.hr.leave.DayPart;
import vn.doit.hr.leave.Pool;
import vn.doit.hr.reminder.WorkCalendar;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveLedgerEntry;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.LeaveRequestTerminationChange;
import vn.doit.hrm.domain.OvertimeRequest;
import vn.doit.hrm.domain.PayrollPeriod;
import vn.doit.hrm.domain.TerminationRecord;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.OvertimeCompensation;
import vn.doit.hrm.domain.enumeration.OvertimeStatus;
import vn.doit.hrm.domain.enumeration.PayrollPeriodStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;
import vn.doit.hrm.repository.LeaveRequestRepository;
import vn.doit.hrm.repository.LeaveRequestTerminationChangeRepository;
import vn.doit.hrm.repository.OvertimeRequestRepository;
import vn.doit.hrm.repository.PayrollPeriodRepository;
import vn.doit.hrm.repository.TerminationRecordRepository;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.leave.dto.LeaveAccountingDTO;
import vn.doit.hrm.service.reminder.WorkCalendarService;

/**
 * Tổng hợp nghỉ và làm thêm một tháng cho kế toán (spec 6.4). Mỗi người NHAN_VIEN có làm việc trong tháng (hoặc có số liệu) một dòng:
 * <ul>
 *   <li>ngày nghỉ theo loại: ngày làm việc (theo lịch công ty, nửa ngày = 0,5) của đơn đã duyệt hoặc đang chờ hủy, phần rơi vào tháng.
 *       Đơn bị nghỉ việc rút ngắn / hủy (có thể nhiều lần: cho nghỉ, hoàn tác, cho nghỉ lại) sau khi kỳ lương của một tháng đã duyệt:
 *       tháng đó giữ số theo khoảng ngày lúc duyệt (đã trả lương), phần chênh (âm) của từng lần đổi hiện ở tháng ghi của lần đó (I2,
 *       F3);</li>
 *   <li>giờ làm thêm trả tiền: đơn hoàn tất, hình thức trả tiền, theo tháng của ngày làm, tách 150/200/300, đếm đơn có giờ đêm; đơn
 *       nghỉ bù không vào đây (đã cộng quỹ nghỉ bù);</li>
 *   <li>số chốt khi nghỉ việc: các dòng {@code NGHI_VIEC} trừ dòng {@code HOAN_TAC} của lần nghỉ việc có ngày làm việc cuối trong tháng,
 *       đổi dấu (dòng chốt đưa số dư về 0): dương = trả tiền phép chưa nghỉ, âm = trừ lương phép đã ứng. Chốt lùi ngày vào tháng đã
 *       duyệt lương thì số chốt hiện ở tháng ghi (I2). Hoàn tác sau khi kỳ lương của tháng hiện số chốt đã duyệt thì số chốt giữ nguyên
 *       ở tháng đó, dòng đảo ngược hiện ở tháng hoàn tác.</li>
 * </ul>
 * Thực tập, dịch vụ không có dòng.
 */
@Service
@Transactional(readOnly = true)
public class LeaveAccountingSummaryService {

    static final String SHEET = "Tổng hợp";

    static final List<String> HEADERS = List.of(
        "Mã NV",
        "Họ và tên",
        "Phòng ban",
        "Tình trạng",
        "Phép năm (ngày)",
        "Việc riêng có lương (ngày)",
        "Nghỉ ốm (ngày)",
        "Không lương (ngày)",
        "Nghỉ bù (ngày)",
        "Làm thêm 150% (giờ)",
        "Làm thêm 200% (giờ)",
        "Làm thêm 300% (giờ)",
        "Số lần có giờ đêm",
        "Phép năm chốt khi nghỉ việc (ngày)",
        "Xử lý phép khi nghỉ việc",
        "Giờ nghỉ bù chốt khi nghỉ việc"
    );

    public static final String PAY_UNUSED = "Trả tiền phép chưa nghỉ";
    public static final String DEDUCT_ADVANCE = "Trừ lương phép ứng";
    public static final String REVERSAL = "Hoàn tác nghỉ việc: đảo số đã chốt ở kỳ lương đã duyệt";

    private static final List<LeaveRequestStatus> TAKEN = List.of(LeaveRequestStatus.DA_DUYET, LeaveRequestStatus.CHO_HUY);
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository requestRepository;
    private final OvertimeRequestRepository overtimeRepository;
    private final LeaveLedgerEntryRepository entries;
    private final TerminationRecordRepository terminationRepository;
    private final WorkCalendarService workCalendar;
    private final PayrollPeriodRepository periodRepository;
    private final LeaveRequestTerminationChangeRepository changeRepository;

    public LeaveAccountingSummaryService(
        EmployeeRepository employeeRepository,
        LeaveRequestRepository requestRepository,
        OvertimeRequestRepository overtimeRepository,
        LeaveLedgerEntryRepository entries,
        TerminationRecordRepository terminationRepository,
        WorkCalendarService workCalendar,
        PayrollPeriodRepository periodRepository,
        LeaveRequestTerminationChangeRepository changeRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.requestRepository = requestRepository;
        this.overtimeRepository = overtimeRepository;
        this.entries = entries;
        this.terminationRepository = terminationRepository;
        this.workCalendar = workCalendar;
        this.periodRepository = periodRepository;
        this.changeRepository = changeRepository;
    }

    /** Số liệu của tháng {@code monthText} ("yyyy-MM"); sai định dạng → 400 leaveaccountingmonth. */
    public LeaveAccountingDTO summary(String monthText) {
        YearMonth month = parseMonth(monthText);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        Map<Long, Acc> acc = new HashMap<>();

        Map<YearMonth, Optional<Instant>> approvedAt = new HashMap<>();
        WorkCalendar cal = workCalendar.calendar(month.atDay(15));
        Map<Long, LeaveRequest> requests = new LinkedHashMap<>();
        requestRepository.findNhanVienOverlapping(TAKEN, from, to).forEach(r -> requests.put(r.getId(), r));
        requestRepository.findNhanVienChangedByTerminationOverlapping(from, to).forEach(r -> requests.putIfAbsent(r.getId(), r));
        Map<Long, List<LeaveRequestTerminationChange>> changes = changesOf(requests.keySet());
        for (LeaveRequest r : requests.values()) {
            BigDecimal days = daysInMonth(shownRange(r, changes.getOrDefault(r.getId(), List.of()), month, approvedAt), from, to, cal);
            if (days.signum() != 0) {
                acc.computeIfAbsent(r.getEmployee().getId(), k -> new Acc()).addLeave(r, days);
            }
        }
        terminationChanges(month, approvedAt, acc);
        for (OvertimeRequest o : overtimeRepository.findByStatusAndCompensationAndWorkDateBetween(
            OvertimeStatus.HOAN_TAT,
            OvertimeCompensation.TRA_TIEN,
            from,
            to
        )) {
            acc.computeIfAbsent(o.getEmployee().getId(), k -> new Acc()).addOvertime(o);
        }
        settlements(month, approvedAt, acc);

        List<Employee> people = employeeRepository.findNhanVienWithDepartmentByIdIn(employeeRepository.findIdsByPersonType(PersonType.NHAN_VIEN));
        List<LeaveAccountingDTO.Row> rows = people
            .stream()
            // Hồ sơ đã lưu trữ chỉ hiện ở tháng nó có số liệu (không thêm dòng trống cho hồ sơ tạo nhầm).
            .filter(e -> acc.containsKey(e.getId()) || (!e.isArchived() && employedDuring(e, from, to)))
            .sorted(Comparator.comparing(Employee::getCode, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(e -> acc.getOrDefault(e.getId(), new Acc()).toRow(e))
            .toList();
        return new LeaveAccountingDTO(month.toString(), rows);
    }

    /** File Excel của {@link #summary(String)}, tiêu đề tiếng Việt. */
    public byte[] export(String monthText) {
        LeaveAccountingDTO data = summary(monthText);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(SHEET);
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);
            CellStyle number = wb.createCellStyle();
            number.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("#,##0.##"));
            Row h = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                Cell c = h.createCell(i);
                c.setCellValue(HEADERS.get(i));
                c.setCellStyle(head);
            }
            int r = 1;
            for (LeaveAccountingDTO.Row x : data.rows()) {
                Row row = sheet.createRow(r++);
                int i = 0;
                text(row, i++, x.code());
                text(row, i++, x.fullName());
                text(row, i++, x.departmentName());
                text(row, i++, x.archived() ? x.status() + " (Lưu trữ)" : x.status());
                for (BigDecimal v : List.of(
                    x.annualDays(),
                    x.personalDays(),
                    x.sickDays(),
                    x.unpaidDays(),
                    x.compensatoryDays(),
                    x.ot150Hours(),
                    x.ot200Hours(),
                    x.ot300Hours()
                )) {
                    num(row, i++, v, number);
                }
                num(row, i++, BigDecimal.valueOf(x.nightCount()), number);
                num(row, i++, x.settlementDays(), number);
                text(row, i++, x.settlementReversal() ? REVERSAL : settlementAction(x.settlementDays()));
                num(row, i++, x.settlementCompensatoryHours(), number);
            }
            sheet.createFreezePane(2, 1);
            for (int i = 0; i < HEADERS.size(); i++) {
                sheet.setColumnWidth(i, i == 1 || i == 14 ? 28 * 256 : 16 * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Cách xử lý số chốt phép năm khi nghỉ việc. */
    public static String settlementAction(BigDecimal days) {
        if (days == null || days.signum() == 0) {
            return null;
        }
        return days.signum() > 0 ? PAY_UNUSED : DEDUCT_ADVANCE;
    }

    /**
     * Ngày của đơn rơi vào [from, to]. Đơn nằm trọn trong tháng: đúng số ngày đã lưu trên đơn (số sổ phép đã trừ khi duyệt), không tính
     * lại theo lịch hiện tại (ngày lễ thêm sau không làm đổi số). Đơn bắc qua tháng: chia theo ngày làm việc của lịch, ngày đầu / cuối
     * của đơn theo buổi đã chọn.
     */
    static BigDecimal daysInMonth(LeaveRequest r, LocalDate from, LocalDate to, WorkCalendar cal) {
        return daysInMonth(new Range(r.getFromDate(), r.getToDate(), r.getFirstPart(), r.getLastPart(), r.getDays()), from, to, cal);
    }

    /** Khoảng ngày nghỉ của một đơn (khoảng hiện tại hoặc khoảng trước khi nghỉ việc đổi đơn); {@code null} = không có ngày nào. */
    record Range(LocalDate fromDate, LocalDate toDate, DayPart firstPart, DayPart lastPart, BigDecimal days) {}

    static BigDecimal daysInMonth(Range r, LocalDate from, LocalDate to, WorkCalendar cal) {
        if (r == null || r.fromDate().isAfter(to) || r.toDate().isBefore(from)) {
            return BigDecimal.ZERO;
        }
        if (!r.fromDate().isBefore(from) && !r.toDate().isAfter(to) && r.days() != null) {
            return r.days();
        }
        LocalDate start = r.fromDate().isAfter(from) ? r.fromDate() : from;
        LocalDate end = r.toDate().isBefore(to) ? r.toDate() : to;
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!cal.isWorkingDay(d)) {
                continue;
            }
            DayPart part = d.equals(r.fromDate()) ? r.firstPart() : d.equals(r.toDate()) ? r.lastPart() : DayPart.CA_NGAY;
            total = total.add(part == null || part == DayPart.CA_NGAY ? BigDecimal.ONE : HALF);
        }
        return total;
    }

    /** Khoảng hiện tại của đơn nếu đơn còn được tính (đã duyệt / chờ hủy), không thì không có ngày nào. */
    private static Range after(LeaveRequest r) {
        return TAKEN.contains(r.getStatus()) ? new Range(r.getFromDate(), r.getToDate(), r.getFirstPart(), r.getLastPart(), r.getDays()) : null;
    }

    /** Khoảng của đơn ngay trước lần nghỉ việc đổi đơn {@code c} (ngày bắt đầu và buổi đầu không đổi khi rút ngắn). */
    private static Range before(LeaveRequest r, LeaveRequestTerminationChange c) {
        return new Range(r.getFromDate(), c.getBeforeToDate(), r.getFirstPart(), c.getBeforeLastPart(), c.getBeforeDays());
    }

    /** Khoảng ngay sau lần đổi thứ {@code k}: khoảng trước của lần kế tiếp, hoặc khoảng hiện tại nếu là lần cuối. */
    private static Range afterChange(LeaveRequest r, List<LeaveRequestTerminationChange> changes, int k) {
        return k + 1 < changes.size() ? before(r, changes.get(k + 1)) : after(r);
    }

    /** Các lần nghỉ việc đổi đơn của những đơn này, theo đơn, theo thứ tự thời gian. */
    private Map<Long, List<LeaveRequestTerminationChange>> changesOf(Collection<Long> requestIds) {
        Map<Long, List<LeaveRequestTerminationChange>> out = new HashMap<>();
        if (requestIds.isEmpty()) {
            return out;
        }
        for (LeaveRequestTerminationChange c : changeRepository.findByLeaveRequestIdInOrderByChangedAtAscIdAsc(requestIds)) {
            out.computeIfAbsent(c.getLeaveRequestId(), k -> new ArrayList<>()).add(c);
        }
        return out;
    }

    /**
     * Khoảng ngày của đơn tính cho tháng {@code m}: khoảng hiện tại, bỏ ngược từ lần đổi cuối các lần đổi mà tháng {@code m} không thấy
     * — lần đổi ghi ở tháng khác, khi kỳ lương tháng {@code m} đã duyệt trước lúc đổi. Tức là tháng đã duyệt lương giữ khoảng ngày lúc
     * duyệt; tháng chưa duyệt và tháng ghi của lần đổi thấy khoảng mới.
     */
    private Range shownRange(LeaveRequest r, List<LeaveRequestTerminationChange> changes, YearMonth m, Map<YearMonth, Optional<Instant>> approvedAt) {
        Range range = after(r);
        for (int k = changes.size() - 1; k >= 0; k--) {
            LeaveRequestTerminationChange c = changes.get(k);
            boolean hidden = !m.equals(YearMonth.from(c.getChangedAt().atZone(VN))) && approvedBefore(m, c.getChangedAt(), approvedAt);
            if (!hidden) {
                break;
            }
            range = before(r, c);
        }
        return range;
    }

    /**
     * Tháng {@code month} là tháng ghi của một hoặc nhiều lần nghỉ việc đổi đơn: với mỗi lần đổi, phần chênh (khoảng ngay sau − khoảng
     * ngay trước lần đó) của các tháng khác đã duyệt lương trước lúc đổi được đưa vào đây (số âm: ngày nghỉ đã trả lương nay hoàn lại quỹ
     * và được chốt). Mỗi lần đổi chỉ hiện phần chênh của chính nó (F3).
     */
    private void terminationChanges(YearMonth month, Map<YearMonth, Optional<Instant>> approvedAt, Map<Long, Acc> acc) {
        Instant start = month.atDay(1).atStartOfDay(VN).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(VN).toInstant();
        Map<Long, LeaveRequest> requests = new LinkedHashMap<>();
        requestRepository.findNhanVienChangedByTerminationBetween(start, end).forEach(r -> requests.put(r.getId(), r));
        Map<Long, List<LeaveRequestTerminationChange>> changes = changesOf(requests.keySet());
        for (LeaveRequest r : requests.values()) {
            List<LeaveRequestTerminationChange> list = changes.getOrDefault(r.getId(), List.of());
            for (int k = 0; k < list.size(); k++) {
                LeaveRequestTerminationChange c = list.get(k);
                if (c.getChangedAt().isBefore(start) || !c.getChangedAt().isBefore(end)) {
                    continue;
                }
                Range before = before(r, c);
                Range after = afterChange(r, list, k);
                YearMonth last = YearMonth.from(before.toDate());
                for (YearMonth m = YearMonth.from(before.fromDate()); !m.isAfter(last); m = m.plusMonths(1)) {
                    if (m.equals(month) || !approvedBefore(m, c.getChangedAt(), approvedAt)) {
                        continue;
                    }
                    WorkCalendar cal = workCalendar.calendar(m.atDay(15));
                    LocalDate from = m.atDay(1);
                    LocalDate to = m.atEndOfMonth();
                    BigDecimal delta = daysInMonth(after, from, to, cal).subtract(daysInMonth(before, from, to, cal));
                    if (delta.signum() != 0) {
                        acc.computeIfAbsent(r.getEmployee().getId(), x -> new Acc()).addLeave(r, delta);
                    }
                }
            }
        }
    }

    /** Kỳ lương tháng {@code m} đã duyệt trước {@code at}. */
    private boolean approvedBefore(YearMonth m, Instant at, Map<YearMonth, Optional<Instant>> approvedAt) {
        return at != null && approvedAt.computeIfAbsent(m, this::approvedAt).filter(a -> a.isBefore(at)).isPresent();
    }

    /**
     * Dòng chốt / hoàn tác khi nghỉ việc. Số chốt hiện ở tháng của ngày làm việc cuối (không có thì theo ngày ghi), trừ khi kỳ lương
     * tháng đó đã duyệt trước lúc ghi chốt (cho nghỉ lùi ngày): khi đó hiện ở tháng ghi (giờ Việt Nam, I2). Dòng hoàn tác hiện cùng tháng
     * với số chốt của lần nghỉ đó; nếu kỳ lương tháng đó đã duyệt trước lúc hoàn tác (số chốt đã được trả / trừ) thì giữ số chốt và hiện
     * dòng đảo ngược ở tháng hoàn tác.
     */
    private void settlements(YearMonth month, Map<YearMonth, Optional<Instant>> approvedAt, Map<Long, Acc> acc) {
        List<LeaveLedgerEntry> rows = entries.findByReasonInAndRefType(
            List.of(LedgerReason.NGHI_VIEC, LedgerReason.HOAN_TAC),
            LeaveTerminationHook.REF_TERMINATION
        );
        if (rows.isEmpty()) {
            return;
        }
        Map<Long, TerminationRecord> records = terminationRepository
            .findAllById(rows.stream().map(LeaveLedgerEntry::getRefId).filter(java.util.Objects::nonNull).distinct().toList())
            .stream()
            .collect(Collectors.toMap(TerminationRecord::getId, Function.identity()));
        // Tháng hiện số chốt của từng lần cho nghỉ (theo dòng NGHI_VIEC của lần đó).
        Map<Long, YearMonth> settledIn = new HashMap<>();
        for (LeaveLedgerEntry e : rows) {
            if (e.getReason() == LedgerReason.NGHI_VIEC && e.getRefId() != null) {
                settledIn.putIfAbsent(e.getRefId(), settlementMonth(e, records.get(e.getRefId()), approvedAt));
            }
        }
        for (LeaveLedgerEntry e : rows) {
            YearMonth shownIn = e.getRefId() != null && settledIn.containsKey(e.getRefId())
                ? settledIn.get(e.getRefId())
                : settlementMonth(e, e.getRefId() == null ? null : records.get(e.getRefId()), approvedAt);
            boolean reversal = false;
            if (e.getReason() == LedgerReason.HOAN_TAC && e.getCreatedAt() != null && approvedBefore(shownIn, e.getCreatedAt(), approvedAt)) {
                shownIn = YearMonth.from(e.getCreatedAt().atZone(VN));
                reversal = true;
            }
            if (shownIn.equals(month)) {
                acc.computeIfAbsent(e.getEmployeeId(), k -> new Acc()).addSettlement(e, reversal);
            }
        }
    }

    /** Tháng của ngày làm việc cuối; nếu kỳ lương tháng đó đã duyệt trước lúc ghi dòng chốt thì tháng ghi. */
    private YearMonth settlementMonth(LeaveLedgerEntry e, TerminationRecord t, Map<YearMonth, Optional<Instant>> approvedAt) {
        YearMonth natural = YearMonth.from(t != null && t.getLastWorkingDay() != null ? t.getLastWorkingDay() : e.getEffectiveDate());
        if (e.getReason() == LedgerReason.NGHI_VIEC && e.getCreatedAt() != null && approvedBefore(natural, e.getCreatedAt(), approvedAt)) {
            return YearMonth.from(e.getCreatedAt().atZone(VN));
        }
        return natural;
    }

    /** Thời điểm duyệt kỳ lương của tháng, nếu kỳ đang ở trạng thái đã duyệt. */
    private Optional<Instant> approvedAt(YearMonth m) {
        return periodRepository
            .findByPeriod(m.toString())
            .filter(p -> p.getStatus() == PayrollPeriodStatus.DA_DUYET && p.getApprovedAt() != null)
            .map(PayrollPeriod::getApprovedAt);
    }

    /** Có làm việc ít nhất một ngày trong tháng: đã vào trước cuối tháng và chưa nghỉ trước đầu tháng. */
    private static boolean employedDuring(Employee e, LocalDate from, LocalDate to) {
        if (e.getJoinDate() != null && e.getJoinDate().isAfter(to)) {
            return false;
        }
        if (e.getStatus() == EmploymentStatus.DA_NGHI) {
            return e.getLastWorkingDay() != null && !e.getLastWorkingDay().isBefore(from);
        }
        return true;
    }

    private static YearMonth parseMonth(String text) {
        try {
            return YearMonth.parse(text);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new EmployeeRuleException("Tháng không hợp lệ (YYYY-MM)", "leaveaccountingmonth");
        }
    }

    private static void text(Row row, int col, String v) {
        if (v != null) {
            row.createCell(col).setCellValue(v);
        }
    }

    private static void num(Row row, int col, BigDecimal v, CellStyle style) {
        if (v == null) {
            return;
        }
        Cell c = row.createCell(col);
        c.setCellValue(v.doubleValue());
        c.setCellStyle(style);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** Cộng dồn số liệu của một người. */
    private static final class Acc {

        BigDecimal annual = BigDecimal.ZERO;
        BigDecimal personal = BigDecimal.ZERO;
        BigDecimal sick = BigDecimal.ZERO;
        BigDecimal unpaid = BigDecimal.ZERO;
        BigDecimal compensatory = BigDecimal.ZERO;
        BigDecimal ot150 = BigDecimal.ZERO;
        BigDecimal ot200 = BigDecimal.ZERO;
        BigDecimal ot300 = BigDecimal.ZERO;
        int night;
        BigDecimal settledAnnual;
        BigDecimal settledCompensatory;
        boolean reversed;

        void addLeave(LeaveRequest r, BigDecimal days) {
            switch (r.getType()) {
                case PHEP_NAM -> annual = annual.add(days);
                case VIEC_RIENG -> personal = personal.add(days);
                case OM -> sick = sick.add(days);
                case KHONG_LUONG -> unpaid = unpaid.add(days);
                case NGHI_BU -> compensatory = compensatory.add(days);
            }
        }

        void addOvertime(OvertimeRequest o) {
            BigDecimal h = o.getActualHours() != null ? o.getActualHours() : o.getPlannedHours();
            switch (o.getRate()) {
                case 200 -> ot200 = ot200.add(h);
                case 300 -> ot300 = ot300.add(h);
                default -> ot150 = ot150.add(h);
            }
            if (o.isNight()) {
                night++;
            }
        }

        void addSettlement(LeaveLedgerEntry e, boolean reversal) {
            reversed |= reversal;
            // Dòng chốt đưa số dư về 0, nên số đã chốt là số đối của dòng.
            BigDecimal v = e.getAmount().negate();
            if (e.getPool() == Pool.PHEP_NAM) {
                settledAnnual = settledAnnual == null ? v : settledAnnual.add(v);
            } else {
                settledCompensatory = settledCompensatory == null ? v : settledCompensatory.add(v);
            }
        }

        LeaveAccountingDTO.Row toRow(Employee e) {
            return new LeaveAccountingDTO.Row(
                e.getId(),
                e.getCode(),
                e.getFullName(),
                e.getDepartment() == null ? null : e.getDepartment().getName(),
                e.getStatus() == EmploymentStatus.DA_NGHI ? "Đã nghỉ" : "Đang làm việc",
                scale(annual),
                scale(personal),
                scale(sick),
                scale(unpaid),
                scale(compensatory),
                scale(ot150),
                scale(ot200),
                scale(ot300),
                night,
                nonZero(settledAnnual),
                nonZero(settledCompensatory),
                reversed && (nonZero(settledAnnual) != null || nonZero(settledCompensatory) != null),
                e.isArchived()
            );
        }

        private static BigDecimal nonZero(BigDecimal v) {
            return v == null || v.signum() == 0 ? null : scale(v);
        }
    }
}
