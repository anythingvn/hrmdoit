package vn.doit.hrm.domain;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import vn.doit.hrm.domain.enumeration.MailSecurity;

/** Cấu hình gửi email do Quản trị nhập (một dòng, id = 1); mật khẩu lưu dạng mã hóa, không ghi vào lịch sử. */
@Entity
@Audited
@Table(name = "mail_setting")
public class MailSetting implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final long ID = 1L;

    @Id
    @Column(name = "id")
    private Long id = ID;

    @Column(name = "host", nullable = false)
    private String host;

    @Column(name = "port", nullable = false)
    private int port;

    @Enumerated(EnumType.STRING)
    @Column(name = "security", length = 10, nullable = false)
    private MailSecurity security;

    @Column(name = "username", length = 254)
    private String username;

    @NotAudited
    @Column(name = "password_enc", length = 1000)
    private String passwordEnc;

    @Column(name = "from_address", length = 254, nullable = false)
    private String fromAddress;

    @Column(name = "from_name", length = 200)
    private String fromName;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public MailSecurity getSecurity() {
        return security;
    }

    public void setSecurity(MailSecurity security) {
        this.security = security;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordEnc() {
        return passwordEnc;
    }

    public void setPasswordEnc(String passwordEnc) {
        this.passwordEnc = passwordEnc;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
