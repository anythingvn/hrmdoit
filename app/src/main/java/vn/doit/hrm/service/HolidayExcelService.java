package vn.doit.hrm.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.doit.hrm.domain.Holiday;
import vn.doit.hrm.repository.HolidayRepository;
import vn.doit.hrm.service.HolidayImportPreview.Item;
import vn.doit.hrm.service.HolidayImportPreview.Problem;
import vn.doit.hrm.service.HolidayImportPreview.Renamed;
import vn.doit.hrm.service.reminder.WorkCalendarService;

/**
 * Ngày lễ ↔ Excel. Cột A "Ngày" (dd/MM/yyyy hoặc ô kiểu ngày), cột B "Tên ngày lễ" (tối đa 100 ký tự), dòng 1 là tiêu đề.
 * Nhập chỉ đụng tới các năm có trong file; ngày thuộc tháng đã chốt công / đã duyệt lương thì báo lỗi, không ghi gì.
 * File chỉ đọc trong bộ nhớ.
 */
@Service
@Transactional
public class HolidayExcelService {

    static final long MAX_BYTES = 20L * 1024 * 1024;
    static final int MAX_NAME = 100;

    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private final HolidayRepository holidayRepository;
    private final WorkCalendarService calendarService;

    public HolidayExcelService(HolidayRepository holidayRepository, WorkCalendarService calendarService) {
        this.holidayRepository = holidayRepository;
        this.calendarService = calendarService;
    }

    @Transactional(readOnly = true)
    public byte[] export(int year) {
        try (Workbook wb = WorkbookFactory.create(true); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Ngày lễ");
            CellStyle date = wb.createCellStyle();
            date.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            Row head = sheet.createRow(0);
            head.createCell(0).setCellValue("Ngày");
            head.createCell(1).setCellValue("Tên ngày lễ");
            int r = 1;
            for (Holiday h : forYear(year)) {
                Row row = sheet.createRow(r++);
                Cell a = row.createCell(0);
                a.setCellValue(h.getDay());
                a.setCellStyle(date);
                row.createCell(1).setCellValue(h.getName());
            }
            sheet.setColumnWidth(0, 14 * 256);
            sheet.setColumnWidth(1, 40 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional(readOnly = true)
    public HolidayImportPreview preview(MultipartFile file) {
        return compute(read(file)).preview();
    }

    /** Tính lại từ file (không tin dữ liệu xem trước của client); còn lỗi thì 400, không ghi gì. */
    public HolidayImportPreview apply(MultipartFile file) {
        Computed c = compute(read(file));
        if (!c.preview().errors().isEmpty()) {
            throw new EmployeeRuleException("File còn lỗi, xem lại kết quả xem trước", "holidayimporterrors");
        }
        for (Holiday h : c.toDelete()) {
            holidayRepository.delete(h);
        }
        for (Map.Entry<Holiday, String> e : c.toRename().entrySet()) {
            e.getKey().setName(e.getValue());
        }
        for (Item i : c.preview().added()) {
            Holiday h = new Holiday();
            h.setDay(i.day());
            h.setName(i.name());
            holidayRepository.save(h);
        }
        return c.preview();
    }

    private record FileRow(int row, LocalDate day, String name) {}

    private record Parsed(List<FileRow> rows, List<Problem> errors) {}

    private record Computed(HolidayImportPreview preview, List<Holiday> toDelete, Map<Holiday, String> toRename) {}

    private List<Holiday> forYear(int year) {
        return holidayRepository.findByDayBetweenOrderByDayAsc(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    private Computed compute(Parsed parsed) {
        List<Problem> errors = new ArrayList<>(parsed.errors());
        Map<LocalDate, FileRow> byDay = new TreeMap<>();
        for (FileRow r : parsed.rows()) {
            FileRow first = byDay.putIfAbsent(r.day(), r);
            if (first != null) {
                errors.add(new Problem(r.row(), "holidayexists", "Ngày " + vn(r.day()) + " trùng với dòng " + first.row() + " trong file"));
            }
        }
        TreeSet<Integer> years = new TreeSet<>();
        byDay.keySet().forEach(d -> years.add(d.getYear()));

        List<Item> added = new ArrayList<>();
        List<Renamed> renamed = new ArrayList<>();
        List<Item> removed = new ArrayList<>();
        List<Item> unchanged = new ArrayList<>();
        List<Holiday> toDelete = new ArrayList<>();
        Map<Holiday, String> toRename = new HashMap<>();
        Map<YearMonth, Boolean> lockCache = new HashMap<>();

        Map<LocalDate, Holiday> existing = new TreeMap<>();
        for (int y : years) {
            forYear(y).forEach(h -> existing.put(h.getDay(), h));
        }
        for (FileRow r : byDay.values()) {
            Holiday h = existing.get(r.day());
            if (h == null) {
                if (locked(r.day(), lockCache)) {
                    errors.add(lockedProblem(r.row(), r.day()));
                } else {
                    added.add(new Item(r.day(), r.name()));
                }
            } else if (h.getName().equals(r.name())) {
                unchanged.add(new Item(r.day(), r.name()));
            } else if (locked(r.day(), lockCache)) {
                errors.add(lockedProblem(r.row(), r.day()));
            } else {
                renamed.add(new Renamed(r.day(), h.getName(), r.name()));
                toRename.put(h, r.name());
            }
        }
        for (Holiday h : existing.values()) {
            if (byDay.containsKey(h.getDay())) {
                continue;
            }
            if (locked(h.getDay(), lockCache)) {
                errors.add(lockedProblem(0, h.getDay()));
            } else {
                removed.add(new Item(h.getDay(), h.getName()));
                toDelete.add(h);
            }
        }
        errors.sort(Comparator.comparingInt(Problem::row));
        return new Computed(new HolidayImportPreview(new ArrayList<>(years), added, renamed, removed, unchanged, errors), toDelete, toRename);
    }

    private boolean locked(LocalDate day, Map<YearMonth, Boolean> cache) {
        return cache.computeIfAbsent(YearMonth.from(day), calendarService::isMonthLocked);
    }

    private static Problem lockedProblem(int row, LocalDate day) {
        return new Problem(row, "holidaymonthlocked", WorkCalendarService.monthLockedMessage(YearMonth.from(day)));
    }

    private static String vn(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private Parsed read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new EmployeeRuleException("Chọn file Excel .xlsx có ngày lễ", "holidayfileinvalid");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new EmployeeRuleException("File vượt quá dung lượng cho phép (20 MB)", "holidayfileinvalid");
        }
        List<FileRow> rows = new ArrayList<>();
        List<Problem> errors = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(file.getBytes()))) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                int excelRow = i + 1;
                Cell a = row.getCell(0);
                String name = text(row.getCell(1));
                if (isBlank(a) && name.isEmpty()) {
                    continue;
                }
                LocalDate day = day(a);
                if (day == null) {
                    errors.add(new Problem(excelRow, "holidayinvalid", "Dòng " + excelRow + ": ngày phải có dạng dd/MM/yyyy"));
                    continue;
                }
                if (name.isEmpty()) {
                    errors.add(new Problem(excelRow, "holidayinvalid", "Dòng " + excelRow + ": thiếu tên ngày lễ"));
                    continue;
                }
                if (name.length() > MAX_NAME) {
                    errors.add(new Problem(excelRow, "holidayinvalid", "Dòng " + excelRow + ": tên ngày lễ tối đa " + MAX_NAME + " ký tự"));
                    continue;
                }
                rows.add(new FileRow(excelRow, day, name));
            }
        } catch (IOException | RuntimeException e) {
            throw new EmployeeRuleException("Không đọc được file; hãy dùng file Excel .xlsx", "holidayfileinvalid");
        }
        if (rows.isEmpty() && errors.isEmpty()) {
            errors.add(new Problem(0, "holidayinvalid", "File không có ngày lễ nào"));
        }
        return new Parsed(rows, errors);
    }

    private static boolean isBlank(Cell c) {
        return c == null || c.getCellType() == CellType.BLANK || (c.getCellType() == CellType.STRING && c.getStringCellValue().isBlank());
    }

    private static String text(Cell c) {
        if (c == null) {
            return "";
        }
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue().trim();
            case NUMERIC -> new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(c).trim();
            default -> "";
        };
    }

    /** Ô kiểu ngày hoặc chuỗi dd/MM/yyyy; null nếu không đọc được. */
    private static LocalDate day(Cell c) {
        if (c == null) {
            return null;
        }
        try {
            if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
                return c.getLocalDateTimeCellValue().toLocalDate();
            }
            if (c.getCellType() == CellType.STRING) {
                return LocalDate.parse(c.getStringCellValue().trim(), VN_DATE);
            }
        } catch (DateTimeParseException | IllegalStateException e) {
            return null;
        }
        return null;
    }
}
