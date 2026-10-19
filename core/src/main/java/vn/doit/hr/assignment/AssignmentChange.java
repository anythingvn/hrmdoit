package vn.doit.hr.assignment;

/**
 * Thay đổi phát sinh khi ghi lịch sử – lưu vào bảng audit để biết ai đổi gì, khi nào.
 * Lịch sử không bao giờ bị xóa; chỉ thêm giai đoạn mới hoặc điều chỉnh ngày.
 *
 * @param before trạng thái trước (null khi tạo mới)
 * @param after trạng thái sau
 */
public record AssignmentChange(Kind kind, Assignment before, Assignment after) {

    public enum Kind {
        /** Thêm giai đoạn mới. */
        CREATED,
        /** Tự chốt ngày kết thúc của nơi làm việc chính trước đó. */
        CLOSED,
        /** Tự tạo giai đoạn quay về nơi cũ sau một đợt cử có thời hạn. */
        RESUMED,
        /** Đổi ngày kết thúc (về sớm hoặc gia hạn). */
        END_CHANGED,
        /** Tự dời ngày bắt đầu của giai đoạn liền sau cho khớp. */
        ADJUSTED
    }

    @Override
    public String toString() {
        return switch (kind) {
            case CREATED -> "Thêm: " + after;
            case CLOSED -> "Chốt: " + before.placeLabel() + " → " + after.periodText();
            case RESUMED -> "Quay về: " + after;
            case END_CHANGED -> "Đổi ngày kết thúc: " + before + " → " + after.periodText();
            case ADJUSTED -> "Dời ngày bắt đầu: " + before + " → " + after.periodText();
        };
    }
}
