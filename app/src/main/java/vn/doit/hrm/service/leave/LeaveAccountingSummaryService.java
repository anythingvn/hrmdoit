package vn.doit.hrm.service.leave;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import vn.doit.hrm.domain.OvertimeRequest;
import vn.doit.hrm.domain.TerminationRecord;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.LeaveRequestStatus;
import vn.doit.hrm.domain.enumeration.OvertimeCompensation;
import vn.doit.hrm.domain.enumeration.OvertimeStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;
import vn.doit.hrm.repository.LeaveRequestRepository;
import vn.doit.hrm.repository.OvertimeRequestRepository;
import vn.doit.hrm.repository.TerminationRecordRepository;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.leave.dto.LeaveAccountingDTO;
import vn.doit.hrm.service.reminder.WorkCalendarService;

/**
 * Tổng hợp nghỉ và làm thêm một tháng cho kế toán (spec 6.4). Mỗi người NHAN_VIEN có làm việc trong tháng (hoặc có số liệu) một dòng:
 * <ul>
 *   <li>ngày nghỉ theo loại: ngày làm việc (theo lịch công ty, nửa ngày = 0,5) của đơn đã duyệt hoặc đang chờ hủy, phần rơi vào tháng;</li>
 *   <li>giờ làm thêm trả tiền: đơn hoàn tất, hình thức trả tiền, theo tháng của ngày làm, tách 150/200/300, đếm đơn có giờ đêm; đơn
 *       nghỉ bù không vào đây (đã cộng quỹ nghỉ bù);</li>
 *   <li>số chốt khi nghỉ việc: các dòng {@code NGHI_VIEC} trừ dòng {@code HOAN_TAC} của lần nghỉ việc có ngày làm việc cuối trong tháng,
 *       đổi dấu (dòng chốt đưa số dư về 0): dương = trả tiền phép chưa nghỉ, âm = trừ lương phép đã ứng.</li>
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

    private static final List<LeaveRequestStatus> TAKEN = List.of(LeaveRequestStatus.DA_DUYET, LeaveRequestStatus.CHO_HUY);
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository requestRepository;
    private final OvertimeRequestRepository overtimeRepository;
    private final LeaveLedgerEntryRepository entries;
    private final TerminationRecordRepository terminationRepository;
    private final WorkCalendarService workCalendar;

    public LeaveAccountingSummaryService(
        EmployeeRepository employeeRepository,
        LeaveRequestRepository requestRepository,
        OvertimeRequestRepository overtimeRepository,
        LeaveLedgerEntryRepository entries,
        TerminationRecordRepository terminationRepository,
        WorkCalendarService workCalendar
    ) {
        this.employeeRepository = employeeRepository;
        this.requestRepository = requestRepository;
        this.overtimeRepository = overtimeRepository;
        this.entries = entries;
        this.terminationRepository = terminationRepository;
        this.workCalendar = workCalendar;
    }

    /** Số liệu của tháng {@code monthText} ("yyyy-MM"); sai định dạng → 400 leaveaccountingmonth. */
    public LeaveAccountingDTO summary(String monthText) {
        YearMonth month = parseMonth(monthText);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        Map<Long, Acc> acc = new HashMap<>();

        WorkCalendar cal = workCalendar.calendar(month.atDay(15));
        for (LeaveRequest r : requestRepository.findNhanVienOverlapping(TAKEN, from, to)) {
            BigDecimal days = daysInMonth(r, from, to, cal);
            if (days.signum() > 0) {
                acc.computeIfAbsent(r.getEmployee().getId(), k -> new Acc()).addLeave(r, days);
            }
        }
        for (OvertimeRequest o : overtimeRepository.findByStatusAndCompensationAndWorkDateBetween(
            OvertimeStatus.HOAN_TAT,
            OvertimeCompensation.TRA_TIEN,
            from,
            to
        )) {
            acc.computeIfAbsent(o.getEmployee().getId(), k -> new Acc()).addOvertime(o);
        }
        settlements(month, acc);

        List<Employee> people = employeeRepository.findNhanVienWithDepartmentByIdIn(employeeRepository.findIdsByPersonType(PersonType.NHAN_VIEN));
        List<LeaveAccountingDTO.Row> rows = people
            .stream()
            .filter(e -> acc.containsKey(e.getId()) || employedDuring(e, from, to))
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
                text(row, i++, x.status());
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
                text(row, i++, settlementAction(x.settlementDays()));
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

    /** Ngày làm việc của đơn rơi vào [from, to]; ngày đầu / cuối của đơn theo buổi đã chọn. */
    static BigDecimal daysInMonth(LeaveRequest r, LocalDate from, LocalDate to, WorkCalendar cal) {
        LocalDate start = r.getFromDate().isAfter(from) ? r.getFromDate() : from;
        LocalDate end = r.getToDate().isBefore(to) ? r.getToDate() : to;
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!cal.isWorkingDay(d)) {
                continue;
            }
            DayPart part = d.equals(r.getFromDate()) ? r.getFirstPart() : d.equals(r.getToDate()) ? r.getLastPart() : DayPart.CA_NGAY;
            total = total.add(part == null || part == DayPart.CA_NGAY ? BigDecimal.ONE : HALF);
        }
        return total;
    }

    /** Dòng chốt / hoàn tác khi nghỉ việc, theo tháng của ngày làm việc cuối của lần nghỉ việc (không có thì theo ngày ghi). */
    private void settlements(YearMonth month, Map<Long, Acc> acc) {
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
        for (LeaveLedgerEntry e : rows) {
            TerminationRecord t = e.getRefId() == null ? null : records.get(e.getRefId());
            LocalDate day = t != null && t.getLastWorkingDay() != null ? t.getLastWorkingDay() : e.getEffectiveDate();
            if (YearMonth.from(day).equals(month)) {
                acc.computeIfAbsent(e.getEmployeeId(), k -> new Acc()).addSettlement(e);
            }
        }
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

        void addSettlement(LeaveLedgerEntry e) {
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
                nonZero(settledCompensatory)
            );
        }

        private static BigDecimal nonZero(BigDecimal v) {
            return v == null || v.signum() == 0 ? null : scale(v);
        }
    }
}
