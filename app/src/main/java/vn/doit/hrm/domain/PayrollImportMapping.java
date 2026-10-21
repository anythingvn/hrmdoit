package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.envers.Audited;

/** Kế toán gắn tay một dòng Excel của bản nháp với một hồ sơ (dòng thiếu mã, trùng mã, mã chưa có hồ sơ). */
@Entity
@Audited
@Table(name = "payroll_import_mapping")
public class PayrollImportMapping implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "import_id")
    private PayrollImport payrollImport;

    @Column(name = "excel_row", nullable = false)
    private int excelRow;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "mapped_by", length = 50)
    private String mappedBy;

    @Column(name = "mapped_at", nullable = false)
    private Instant mappedAt;

    public Long getId() {
        return id;
    }

    public PayrollImport getPayrollImport() {
        return payrollImport;
    }

    public void setPayrollImport(PayrollImport payrollImport) {
        this.payrollImport = payrollImport;
    }

    public int getExcelRow() {
        return excelRow;
    }

    public void setExcelRow(int excelRow) {
        this.excelRow = excelRow;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public String getMappedBy() {
        return mappedBy;
    }

    public void setMappedBy(String mappedBy) {
        this.mappedBy = mappedBy;
    }

    public Instant getMappedAt() {
        return mappedAt;
    }

    public void setMappedAt(Instant mappedAt) {
        this.mappedAt = mappedAt;
    }
}
