package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.envers.Audited;
import vn.doit.hrm.domain.enumeration.WorkLocationType;

/**
 * Quyết định cử nhân sự đi công tác / làm việc tại khách hàng đã ban hành (tên khác {@code vn.doit.hr.assignment.AssignmentDecision}
 * của lõi). Lưu bản chụp nơi đến và thời gian như trên văn bản; giai đoạn vị trí tương ứng là {@code assignmentId}.
 */
@Entity
@Audited
@Table(name = "assignment_decision")
public class AssignmentDecisionRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "decision_no", length = 50, nullable = false, unique = true)
    private String decisionNo;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "assignment_id")
    private Long assignmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private WorkLocationType type;

    @Column(name = "unit", length = 200)
    private String unit;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date")
    private LocalDate toDate;

    @Column(name = "sign_date", nullable = false)
    private LocalDate signDate;

    @Column(name = "signer_name", length = 100)
    private String signerName;

    @Column(name = "signer_title", length = 100)
    private String signerTitle;

    @Column(name = "task", columnDefinition = "text")
    private String task;

    @Column(name = "benefits", columnDefinition = "text")
    private String benefits;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getDecisionNo() {
        return decisionNo;
    }

    public void setDecisionNo(String decisionNo) {
        this.decisionNo = decisionNo;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(Long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public WorkLocationType getType() {
        return type;
    }

    public void setType(WorkLocationType type) {
        this.type = type;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public LocalDate getSignDate() {
        return signDate;
    }

    public void setSignDate(LocalDate signDate) {
        this.signDate = signDate;
    }

    public String getSignerName() {
        return signerName;
    }

    public void setSignerName(String signerName) {
        this.signerName = signerName;
    }

    public String getSignerTitle() {
        return signerTitle;
    }

    public void setSignerTitle(String signerTitle) {
        this.signerTitle = signerTitle;
    }

    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public String getBenefits() {
        return benefits;
    }

    public void setBenefits(String benefits) {
        this.benefits = benefits;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
