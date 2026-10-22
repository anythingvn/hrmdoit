package vn.doit.hrm.service.leave;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import vn.doit.hr.leave.Bucket;
import vn.doit.hr.leave.Entry;
import vn.doit.hr.leave.Pool;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.enumeration.LedgerReason;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.LeaveLedgerEntryRepository;
import vn.doit.hrm.security.SecurityUtils;
import vn.doit.hrm.service.EmployeeCodes;
import vn.doit.hrm.service.EmployeeRuleException;
import vn.doit.hrm.service.UploadNames;
import vn.doit.hrm.service.leave.dto.LeaveOpeningCheckDTO;
import vn.doit.hrm.service.leave.dto.LeaveOpeningResultDTO;

/**
 * Số dư đầu kỳ từ Excel (HR, Quản trị), theo cách nhập nhân viên: tải mẫu, kiểm tra không ghi gì, lưu thì kiểm tra lại rồi ghi tất
 * cả trong một giao dịch (còn lỗi thì không ghi gì). Mỗi người, mỗi quỹ chỉ có số dư đầu kỳ một lần ({@code DAU_KY}).
 */
@Service
@Transactional
public class LeaveOpeningImportService {

    private static final Logger LOG = LoggerFactory.getLogger(LeaveOpeningImportService.class);

    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final String SHEET = "So du dau ky";
    static final String[] HEADERS = { "Mã NV", "Phép năm còn lại (ngày)", "Trong đó phép năm trước (ngày)", "Giờ nghỉ bù còn lại" };
    private static final BigDecimal MAX_VALUE = new BigDecimal("9999");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Một dòng đã đọc và hợp lệ. */
    private record Planned(int excelRow, Employee employee, BigDecimal annual, BigDecimal previous, BigDecimal hours) {}

    private record Result(List<Planned> rows, List<LeaveOpeningCheckDTO.Issue> issues) {
        LeaveOpeningCheckDTO toDto() {
            List<LeaveOpeningCheckDTO.Row> out = rows
                .stream()
                .map(p ->
                    new LeaveOpeningCheckDTO.Row(
                        p.excelRow(),
                        p.employee().getCode(),
                        p.employee().getFullName(),
                        p.annual(),
                        p.previous(),
                        p.hours()
                    )
                )
                .toList();
            return new LeaveOpeningCheckDTO(
                out.size() + (int) issues.stream().map(LeaveOpeningCheckDTO.Issue::excelRow).distinct().count(),
                out,
                issues,
                issues.size(),
                issues.isEmpty()
            );
        }
    }

    /** Dòng thô đọc từ file (ô trống = null; ô không đọc được số = chuỗi gốc). */
    private record RawRow(int excelRow, String code, Object annual, Object previous, Object hours) {}

    private final EmployeeRepository employeeRepository;
    private final LeaveLedgerEntryRepository entries;
    private final LeaveLedgerService ledger;
    private final LeaveSettingService settings;
    private final Clock clock;

    public LeaveOpeningImportService(
        EmployeeRepository employeeRepository,
        LeaveLedgerEntryRepository entries,
        LeaveLedgerService ledger,
        LeaveSettingService settings,
        Clock clock
    ) {
        this.employeeRepository = employeeRepository;
        this.entries = entries;
        this.ledger = ledger;
        this.settings = settings;
        this.clock = clock;
    }

    /** File mẫu: một dòng tiêu đề. */
    @Transactional(readOnly = true)
    public byte[] template() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(SHEET);
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(bold);
                sheet.setColumnWidth(i, 32 * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional(readOnly = true)
    public LeaveOpeningCheckDTO check(MultipartFile file) {
        return run(read(file)).toDto();
    }

    /** Khóa hồ sơ của mọi người trong file (theo thứ tự id), kiểm tra lại, rồi ghi; còn lỗi → 400 kèm kết quả kiểm tra. */
    public LeaveOpeningResultDTO save(MultipartFile file) {
        List<RawRow> raw = read(file);
        raw.stream()
            .map(r -> EmployeeCodes.normalize(r.code()))
            .filter(java.util.Objects::nonNull)
            .distinct()
            .map(employeeRepository::findOneByCode)
            .flatMap(Optional::stream)
            .map(Employee::getId)
            .sorted()
            .forEach(ledger::lock);
        String login = SecurityUtils.getCurrentUserLogin().orElse(null);
        boolean self = raw
            .stream()
            .map(r -> EmployeeCodes.normalize(r.code()))
            .filter(java.util.Objects::nonNull)
            .map(employeeRepository::findOneByCode)
            .flatMap(Optional::stream)
            .anyMatch(e -> ApproverResolver.isSelf(login, e));
        if (self) {
            throw new LeaveForbiddenException("Không tự nhập số dư đầu kỳ cho chính mình", "leaveselfadjust");
        }
        Result result = run(raw);
        if (!result.issues().isEmpty()) {
            throw new LeaveOpeningInvalidException(result.toDto());
        }
        int saved = 0;
        for (Planned p : result.rows()) {
            Long id = p.employee().getId();
            boolean any = false;
            List<Entry> annual = new ArrayList<>();
            if (p.previous().signum() != 0) {
                annual.add(new Entry(Pool.PHEP_NAM, Bucket.NAM_TRUOC, p.previous()));
            }
            BigDecimal current = p.annual().subtract(p.previous());
            if (current.signum() != 0) {
                annual.add(new Entry(Pool.PHEP_NAM, Bucket.NAM_NAY, current));
            }
            if (!annual.isEmpty()) {
                ledger.post(id, Pool.PHEP_NAM, annual, LedgerReason.DAU_KY, LeaveLedgerService.REF_OPENING, null, "Số dư đầu kỳ");
                any = true;
            }
            if (p.hours().signum() != 0) {
                ledger.post(
                    id,
                    Pool.NGHI_BU,
                    List.of(new Entry(Pool.NGHI_BU, Bucket.NAM_NAY, p.hours())),
                    LedgerReason.DAU_KY,
                    LeaveLedgerService.REF_OPENING,
                    null,
                    "Số dư đầu kỳ"
                );
                any = true;
            }
            if (any) {
                saved++;
            }
        }
        LOG.info("Nhập số dư đầu kỳ cho {} người bởi {}", saved, SecurityUtils.getCurrentUserLogin().orElse(null));
        return new LeaveOpeningResultDTO(saved);
    }

    private Result run(List<RawRow> raw) {
        String login = SecurityUtils.getCurrentUserLogin().orElse(null);
        LocalDate today = LocalDate.now(clock);
        LocalDate expiry = settings.carryExpiry(today.getYear());
        List<Planned> rows = new ArrayList<>();
        List<LeaveOpeningCheckDTO.Issue> issues = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (RawRow r : raw) {
            String code = EmployeeCodes.normalize(r.code());
            String error = null;
            Employee e = null;
            BigDecimal annual = null;
            BigDecimal previous = null;
            BigDecimal hours = null;
            if (code == null) {
                error = "Thiếu mã NV";
            } else if (seen.containsKey(code)) {
                error = "Mã NV trùng với dòng " + seen.get(code);
            } else {
                seen.put(code, r.excelRow());
                e = employeeRepository.findOneByCode(code).orElse(null);
                if (e == null) {
                    error = "Không tìm thấy nhân viên có mã " + code;
                } else if (e.getPersonType() != PersonType.NHAN_VIEN) {
                    error = "Người này không dùng phần nghỉ phép (thực tập, dịch vụ)";
                } else if (ApproverResolver.isSelf(login, e)) {
                    error = "Không tự nhập số dư đầu kỳ cho chính mình — nhờ HR khác nhập";
                }
            }
            if (error == null) {
                try {
                    annual = number(r.annual(), HEADERS[1]);
                    previous = number(r.previous(), HEADERS[2]);
                    hours = number(r.hours(), HEADERS[3]);
                } catch (IllegalArgumentException ex) {
                    error = ex.getMessage();
                }
            }
            if (error == null) {
                error = valueError(annual, previous, hours, today, expiry);
            }
            if (error == null) {
                if (annual.signum() != 0 && entries.existsByEmployeeIdAndPoolAndReason(e.getId(), Pool.PHEP_NAM, LedgerReason.DAU_KY)) {
                    error = "Đã có số dư đầu kỳ phép năm; muốn sửa thì dùng điều chỉnh";
                } else if (
                    hours.signum() != 0 && entries.existsByEmployeeIdAndPoolAndReason(e.getId(), Pool.NGHI_BU, LedgerReason.DAU_KY)
                ) {
                    error = "Đã có số dư đầu kỳ giờ nghỉ bù; muốn sửa thì dùng điều chỉnh";
                }
            }
            if (error != null) {
                issues.add(new LeaveOpeningCheckDTO.Issue(r.excelRow(), code, error));
            } else {
                rows.add(new Planned(r.excelRow(), e, annual, previous, hours));
            }
        }
        return new Result(rows, issues);
    }

    private static String valueError(BigDecimal annual, BigDecimal previous, BigDecimal hours, LocalDate today, LocalDate expiry) {
        if (previous.signum() < 0) {
            return HEADERS[2] + " không được âm";
        }
        if (previous.compareTo(annual.max(BigDecimal.ZERO)) > 0) {
            return HEADERS[2] + " lớn hơn " + HEADERS[1].toLowerCase(Locale.ROOT);
        }
        if (previous.signum() > 0 && today.isAfter(expiry)) {
            return "Đã qua hạn dùng phép năm trước (" + expiry.format(DMY) + "); ghi 0 ở cột phép năm trước";
        }
        if (hours.signum() < 0) {
            return HEADERS[3] + " không được âm";
        }
        return null;
    }

    /** Ô trống = 0; số có tối đa 2 chữ số thập phân, nhận cả dấu phẩy thập phân. */
    private static BigDecimal number(Object cell, String header) {
        BigDecimal v;
        if (cell == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (cell instanceof BigDecimal d) {
            v = d;
        } else {
            String s = cell.toString().trim().replace(" ", "");
            if (s.isEmpty()) {
                return BigDecimal.ZERO.setScale(2);
            }
            try {
                v = new BigDecimal(s.replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(header + ": \"" + cell + "\" không phải số");
            }
        }
        if (v.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(header + ": tối đa 2 chữ số thập phân");
        }
        if (v.abs().compareTo(MAX_VALUE) > 0) {
            throw new IllegalArgumentException(header + ": số quá lớn");
        }
        return v.setScale(2);
    }

    /** Đọc file: chỉ nhận .xlsx (đuôi và chữ ký PK), ≤ 5 MB, dòng 1 là tiêu đề của mẫu. */
    private static List<RawRow> read(MultipartFile file) {
        String name = UploadNames.displayName(file.getOriginalFilename());
        if (!name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw notXlsx();
        }
        if (file.getSize() > MAX_BYTES) {
            throw new MaxUploadSizeExceededException(MAX_BYTES);
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K') {
            throw notXlsx();
        }
        List<RawRow> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = wb.getSheet(SHEET) != null ? wb.getSheet(SHEET) : wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            Object first = header == null ? null : value(header.getCell(0));
            if (first == null || !HEADERS[0].equalsIgnoreCase(first.toString().trim())) {
                throw new EmployeeRuleException(
                    "File không đúng mẫu số dư đầu kỳ: ô A1 phải là \"" + HEADERS[0] + "\"",
                    "leaveopeninglayout"
                );
            }
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                Object code = value(row.getCell(0));
                Object annual = value(row.getCell(1));
                Object previous = value(row.getCell(2));
                Object hours = value(row.getCell(3));
                if (code == null && annual == null && previous == null && hours == null) {
                    continue;
                }
                rows.add(new RawRow(i + 1, code == null ? null : plain(code), annual, previous, hours));
            }
        } catch (EmployeeRuleException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw notXlsx();
        }
        return rows;
    }

    /** Mã NV ghi dạng số trong Excel (vd 1001.0) → "1001". */
    private static String plain(Object v) {
        return v instanceof BigDecimal d ? d.stripTrailingZeros().toPlainString() : v.toString();
    }

    /** Giá trị ô: số → BigDecimal, chữ → String (trống → null). */
    private static Object value(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case NUMERIC -> new BigDecimal(Double.toString(cell.getNumericCellValue()));
            case STRING -> {
                String s = cell.getStringCellValue();
                yield s == null || s.isBlank() ? null : s;
            }
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private static EmployeeRuleException notXlsx() {
        return new EmployeeRuleException("Chỉ nhận file Excel .xlsx lưu từ mẫu số dư đầu kỳ", "leaveopeningnotxlsx");
    }
}
