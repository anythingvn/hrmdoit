package vn.doit.hrm.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link vn.doit.hrm.domain.EmployeeDocument} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class EmployeeDocumentDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(max = 255)
    private String fileName;

    @NotNull
    @Size(max = 100)
    private String contentType;

    @NotNull
    private Long sizeBytes;

    @NotNull
    @Size(max = 500)
    private String storagePath;

    @Size(max = 50)
    private String uploadedBy;

    @NotNull
    private Instant uploadedAt;

    @NotNull
    private EmployeeDTO employee;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public EmployeeDTO getEmployee() {
        return employee;
    }

    public void setEmployee(EmployeeDTO employee) {
        this.employee = employee;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmployeeDocumentDTO)) {
            return false;
        }

        EmployeeDocumentDTO employeeDocumentDTO = (EmployeeDocumentDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, employeeDocumentDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "EmployeeDocumentDTO{" +
            "id=" + getId() +
            ", fileName='" + getFileName() + "'" +
            ", contentType='" + getContentType() + "'" +
            ", sizeBytes=" + getSizeBytes() +
            ", storagePath='" + getStoragePath() + "'" +
            ", uploadedBy='" + getUploadedBy() + "'" +
            ", uploadedAt='" + getUploadedAt() + "'" +
            ", employee=" + getEmployee() +
            "}";
    }
}
