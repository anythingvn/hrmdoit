package vn.doit.hrm.domain.enumeration;

/** Kiểu bảo mật kết nối SMTP: không mã hóa, STARTTLS (thường cổng 587) hoặc SSL/TLS (thường cổng 465). */
public enum MailSecurity {
    NONE,
    STARTTLS,
    SSL,
}
