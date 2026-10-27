package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import org.hibernate.envers.Audited;

/**
 * Một dòng của bản tính lương nháp (một người). Các cột JSON: {@code input} đầu vào đã gom từ hồ sơ (không kèm ô nhập tay / sửa
 * tay), {@code manual} ô Kế toán nhập, {@code overrides} ô tự tính đã sửa tay, {@code amounts} kết quả 36 khoản, {@code errors},
 * {@code warnings} (đã gồm cảnh báo lúc gom đầu vào), {@code collectWarnings} cảnh báo lúc gom đầu vào.
 */
@Entity
@Audited
@Table(name = "payroll_calc_draft_line")
public class PayrollCalcDraftLine implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "draft_id")
    private PayrollCalcDraft draft;

    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(name = "position", length = 200)
    private String position;

    @Column(name = "input", columnDefinition = "text", nullable = false)
    private String input;

    @Column(name = "manual", columnDefinition = "text", nullable = false)
    private String manual;

    @Column(name = "overrides", columnDefinition = "text", nullable = false)
    private String overrides;

    @Column(name = "amounts", columnDefinition = "text", nullable = false)
    private String amounts;

    @Column(name = "errors", columnDefinition = "text", nullable = false)
    private String errors;

    @Column(name = "warnings", columnDefinition = "text", nullable = false)
    private String warnings;

    @Column(name = "collect_warnings", columnDefinition = "text", nullable = false)
    private String collectWarnings;

    @Column(name = "stale", nullable = false)
    private boolean stale;

    public Long getId() {
        return id;
    }

    public PayrollCalcDraft getDraft() {
        return draft;
    }

    public void setDraft(PayrollCalcDraft draft) {
        this.draft = draft;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getManual() {
        return manual;
    }

    public void setManual(String manual) {
        this.manual = manual;
    }

    public String getOverrides() {
        return overrides;
    }

    public void setOverrides(String overrides) {
        this.overrides = overrides;
    }

    public String getAmounts() {
        return amounts;
    }

    public void setAmounts(String amounts) {
        this.amounts = amounts;
    }

    public String getErrors() {
        return errors;
    }

    public void setErrors(String errors) {
        this.errors = errors;
    }

    public String getWarnings() {
        return warnings;
    }

    public void setWarnings(String warnings) {
        this.warnings = warnings;
    }

    public String getCollectWarnings() {
        return collectWarnings;
    }

    public void setCollectWarnings(String collectWarnings) {
        this.collectWarnings = collectWarnings;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
    }
}
