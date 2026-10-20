package vn.doit.hr.assignment;

/**
 * Loại vị trí làm việc.
 *
 * <p>Có 2 lớp:
 * <ul>
 *   <li><b>PRIMARY</b> – nơi làm việc chính. Tại mọi thời điểm chỉ có <i>một</i> nơi chính (không chồng lấn).</li>
 *   <li><b>TEMPORARY</b> – đi công tác ngắn ngày, <i>đè lên</i> nơi chính trong thời gian công tác
 *       (vd đang onsite ở khách hàng A, đi công tác Đà Nẵng 3 ngày rồi về lại A).</li>
 * </ul>
 */
public enum WorkLocationType {
    VAN_PHONG("Văn phòng công ty", Layer.PRIMARY),
    KHACH_HANG("Làm việc tại khách hàng/dự án", Layer.PRIMARY),
    TU_XA("Làm việc từ xa", Layer.PRIMARY),
    CONG_TAC("Đi công tác", Layer.TEMPORARY);

    public enum Layer { PRIMARY, TEMPORARY }

    private final String label;
    private final Layer layer;

    WorkLocationType(String label, Layer layer) {
        this.label = label;
        this.layer = layer;
    }

    public String label() {
        return label;
    }

    public Layer layer() {
        return layer;
    }
}
