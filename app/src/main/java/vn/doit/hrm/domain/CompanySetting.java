package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.envers.Audited;

/**
 * Cài đặt công ty (một dòng, id = 1): tên in trên văn bản, tên viết tắt và khẩu hiệu trên web, người đại diện ký.
 * <p>
 * Cột ảnh {@code logo} cố ý không ánh xạ ở đây: đọc tên công ty không bao giờ tải ảnh, và lịch sử không lưu ảnh. Ảnh đọc / ghi
 * bằng câu lệnh riêng trong {@code CompanySettingService}; entity chỉ giữ kiểu file và thời điểm đổi logo (phiên bản logo).
 * <p>
 * {@code @DynamicUpdate}: UPDATE chỉ ghi cột đã đổi, nên lưu tên công ty chen với đổi logo không ghi đè kiểu file, thời điểm logo
 * bằng giá trị cũ đã nạp.
 */
@Entity
@DynamicUpdate
@Audited
@Table(name = "company_setting")
public class CompanySetting implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final long ID = 1L;

    @Id
    @Column(name = "id")
    private Long id = ID;

    @Column(name = "company_name", length = 255, nullable = false)
    private String companyName;

    @Column(name = "short_name", length = 30)
    private String shortName;

    @Column(name = "tagline", length = 100)
    private String tagline;

    @Column(name = "representative_name", length = 100)
    private String representativeName;

    @Column(name = "representative_title", length = 100)
    private String representativeTitle;

    @Column(name = "logo_content_type", length = 50)
    private String logoContentType;

    @Column(name = "logo_updated_at")
    private Instant logoUpdatedAt;

    public Long getId() {
        return id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getTagline() {
        return tagline;
    }

    public void setTagline(String tagline) {
        this.tagline = tagline;
    }

    public String getRepresentativeName() {
        return representativeName;
    }

    public void setRepresentativeName(String representativeName) {
        this.representativeName = representativeName;
    }

    public String getRepresentativeTitle() {
        return representativeTitle;
    }

    public void setRepresentativeTitle(String representativeTitle) {
        this.representativeTitle = representativeTitle;
    }

    public String getLogoContentType() {
        return logoContentType;
    }

    public void setLogoContentType(String logoContentType) {
        this.logoContentType = logoContentType;
    }

    public Instant getLogoUpdatedAt() {
        return logoUpdatedAt;
    }

    public void setLogoUpdatedAt(Instant logoUpdatedAt) {
        this.logoUpdatedAt = logoUpdatedAt;
    }

    /** Phiên bản logo (epoch millis lúc đổi logo); 0 khi chưa từng đổi. Dùng làm ETag và tham số chống cache phía giao diện. */
    public long logoVersion() {
        return logoUpdatedAt == null ? 0L : logoUpdatedAt.toEpochMilli();
    }
}
