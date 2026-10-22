package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import org.hibernate.envers.Audited;

/**
 * Một dòng lương của phiên bản (tên khác {@code vn.doit.hr.payroll.PayrollLine} của lõi): hồ sơ đã gắn, mã/tên như trong file,
 * 36 khoản (JSON) và các cột tổng dùng cho báo cáo. Số liệu lưu đúng như file, không bao giờ tính lại.
 */
@Entity
@Audited
@Table(name = "payroll_line")
public class PayrollLineRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "version_id")
    private PayrollVersion version;

    /** Trống khi hồ sơ đã bị xóa hẳn: dòng giữ nguyên số tiền, {@code fileCode}, {@code fileName}. */
    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "excel_row", nullable = false)
    private int excelRow;

    @Column(name = "file_code", length = 50)
    private String fileCode;

    @Column(name = "file_name", length = 200)
    private String fileName;

    @Column(name = "position", length = 200)
    private String position;

    @Column(name = "mapped_manually", nullable = false)
    private boolean mappedManually;

    @Column(name = "amounts", columnDefinition = "text", nullable = false)
    private String amounts;

    @Column(name = "thuc_nhan")
    private BigDecimal thucNhan;

    @Column(name = "tong_chi_phi")
    private BigDecimal tongChiPhi;

    @Column(name = "thue_tncn")
    private BigDecimal thueTncn;

    @Column(name = "tong_bh_nld")
    private BigDecimal tongBhNld;

    public Long getId() {
        return id;
    }

    public PayrollVersion getVersion() {
        return version;
    }

    public void setVersion(PayrollVersion version) {
        this.version = version;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public int getExcelRow() {
        return excelRow;
    }

    public void setExcelRow(int excelRow) {
        this.excelRow = excelRow;
    }

    public String getFileCode() {
        return fileCode;
    }

    public void setFileCode(String fileCode) {
        this.fileCode = fileCode;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public boolean isMappedManually() {
        return mappedManually;
    }

    public void setMappedManually(boolean mappedManually) {
        this.mappedManually = mappedManually;
    }

    public String getAmounts() {
        return amounts;
    }

    public void setAmounts(String amounts) {
        this.amounts = amounts;
    }

    public BigDecimal getThucNhan() {
        return thucNhan;
    }

    public void setThucNhan(BigDecimal thucNhan) {
        this.thucNhan = thucNhan;
    }

    public BigDecimal getTongChiPhi() {
        return tongChiPhi;
    }

    public void setTongChiPhi(BigDecimal tongChiPhi) {
        this.tongChiPhi = tongChiPhi;
    }

    public BigDecimal getThueTncn() {
        return thueTncn;
    }

    public void setThueTncn(BigDecimal thueTncn) {
        this.thueTncn = thueTncn;
    }

    public BigDecimal getTongBhNld() {
        return tongBhNld;
    }

    public void setTongBhNld(BigDecimal tongBhNld) {
        this.tongBhNld = tongBhNld;
    }
}
