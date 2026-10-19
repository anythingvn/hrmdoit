package vn.doit.hrm.service.dto;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Dữ liệu nhạy cảm của nhân viên (CCCD, thuế, ngân hàng, BHXH), chỉ trả qua {@code GET /api/employees/{id}/sensitive}.
 */
public class EmployeeSensitiveDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String idNumber;
    private LocalDate idIssueDate;
    private String idIssuePlace;
    private String taxCode;
    private String bankAccount;
    private String bankName;
    private String socialInsuranceNo;

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public LocalDate getIdIssueDate() {
        return idIssueDate;
    }

    public void setIdIssueDate(LocalDate idIssueDate) {
        this.idIssueDate = idIssueDate;
    }

    public String getIdIssuePlace() {
        return idIssuePlace;
    }

    public void setIdIssuePlace(String idIssuePlace) {
        this.idIssuePlace = idIssuePlace;
    }

    public String getTaxCode() {
        return taxCode;
    }

    public void setTaxCode(String taxCode) {
        this.taxCode = taxCode;
    }

    public String getBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(String bankAccount) {
        this.bankAccount = bankAccount;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getSocialInsuranceNo() {
        return socialInsuranceNo;
    }

    public void setSocialInsuranceNo(String socialInsuranceNo) {
        this.socialInsuranceNo = socialInsuranceNo;
    }
}
