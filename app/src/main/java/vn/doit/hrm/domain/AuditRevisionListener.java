package vn.doit.hrm.domain;

import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Ghi tên đăng nhập hiện tại vào mỗi revision; không có phiên đăng nhập thì là "system".
 * (Lớp domain không được phụ thuộc lớp security của ứng dụng nên đọc thẳng SecurityContext.)
 */
public class AuditRevisionListener implements RevisionListener {

    static final String SYSTEM = "system";

    @Override
    public void newRevision(Object revisionEntity) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String login = authentication != null && authentication.isAuthenticated() ? authentication.getName() : null;
        if (login == null || login.isBlank() || "anonymousUser".equals(login)) {
            login = SYSTEM;
        }
        ((AuditRevision) revisionEntity).setLogin(login.length() > 50 ? login.substring(0, 50) : login);
    }
}
