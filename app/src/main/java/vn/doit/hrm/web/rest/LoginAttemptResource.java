package vn.doit.hrm.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.security.LoginAttemptService;

/** Quản trị mở khóa đăng nhập cho một tài khoản bị chặn vì nhập sai quá nhiều lần. */
@RestController
@RequestMapping("/api/admin/login-attempts")
public class LoginAttemptResource {

    private static final Logger LOG = LoggerFactory.getLogger(LoginAttemptResource.class);

    private final LoginAttemptService loginAttemptService;

    public LoginAttemptResource(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    /** {@code DELETE /api/admin/login-attempts/:login}: xóa bộ đếm đăng nhập sai của tài khoản (mọi IP). 204. */
    @DeleteMapping("/{login}")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
    public ResponseEntity<Void> unlock(@PathVariable("login") String login) {
        int removed = loginAttemptService.unlock(login);
        LOG.info("Quản trị mở khóa đăng nhập cho '{}' ({} bộ đếm)", login, removed);
        return ResponseEntity.noContent().build();
    }
}
