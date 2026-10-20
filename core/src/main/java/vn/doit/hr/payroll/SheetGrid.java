package vn.doit.hr.payroll;

/**
 * Một sheet Excel đã được đọc thành lưới giá trị (chỉ số 0-based).
 *
 * <p>Giá trị trả về là một trong: {@code null} (ô trống), {@link String}, {@link java.math.BigDecimal},
 * {@link java.time.LocalDate}, hoặc {@link CellError} (ô lỗi như #N/A, #REF!).
 * Ô nằm trong vùng gộp (merged) phải trả về giá trị của ô trên-trái của vùng đó.
 *
 * <p>Tách interface này ra để toàn bộ logic đọc cột/kiểm tra không phụ thuộc Apache POI
 * (dễ test, dễ thay nguồn dữ liệu).
 */
public interface SheetGrid {

    int rowCount();

    int columnCount();

    Object get(int row, int col);

    /** Giá trị lỗi của ô (vd "#N/A"). */
    record CellError(String code) {
        @Override
        public String toString() {
            return code;
        }
    }
}
