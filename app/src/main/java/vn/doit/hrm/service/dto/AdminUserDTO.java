package vn.doit.hrm.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import vn.doit.hrm.config.Constants;
import vn.doit.hrm.domain.Authority;
import vn.doit.hrm.domain.User;

/**
 * A DTO representing a user, with his authorities.
 */
public class AdminUserDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @NotBlank
    @Pattern(regexp = Constants.LOGIN_REGEX)
    @Size(min = 1, max = 50)
    private String login;

    @Size(max = 50)
    private String firstName;

    @Size(max = 50)
    private String lastName;

    @Email
    @Size(min = 5, max = 254)
    private String email;

    @Size(max = 256)
    private String imageUrl;

    private boolean activated = false;

    @Size(min = 2, max = 10)
    private String langKey;

    private String createdBy;

    private Instant createdDate;

    private String lastModifiedBy;

    private Instant lastModifiedDate;

    private Set<String> authorities;

    /**
     * Số CCCD khi Quản trị tạo tài khoản (bắt buộc khi tạo): tên đăng nhập = số này; trùng CCCD của hồ sơ nhân viên chưa có tài
     * khoản thì tài khoản được gắn vào hồ sơ đó. Chỉ nhận vào, không trả ra (tên đăng nhập đã là số này).
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Size(max = 20)
    private String idNumber;

    /**
     * Mật khẩu ban đầu khi Quản trị tạo tài khoản (tùy chọn, 8–100 ký tự; để trống = mật khẩu mặc định). Chỉ nhận vào, không bao
     * giờ trả ra và không có trong {@link #toString()}.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @jakarta.validation.constraints.Pattern(regexp = AdminPasswordDTO.OPTIONAL_PATTERN)
    private String password;

    /** Đang dùng mật khẩu mặc định, phải đổi trước khi dùng các chức năng khác. Chỉ đọc. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Boolean mustChangePassword;

    /** Chỉ có ở {@code GET /api/account}: tài khoản đã gắn hồ sơ nhân viên (hiện mục "Hồ sơ của tôi"). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean hasEmployeeProfile;

    /** Chỉ có ở {@code GET /api/account}: hồ sơ gắn tài khoản là NHAN_VIEN đang làm việc (hiện mục "Nghỉ phép"). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean leaveApplicable;

    /** Chỉ có ở {@code GET /api/account}: BGĐ/HR/Quản trị, trưởng nhóm, trưởng phòng hoặc người duyệt của đơn đang chờ (mục "Duyệt đơn"). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean isApprover;

    /** Chỉ có ở {@code GET /api/account}: BGĐ/HR/Quản trị, trưởng nhóm hoặc trưởng phòng (mục "Lịch nghỉ"). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean seesLeaveCalendar;

    /** Chỉ có ở {@code GET /api/account}: Quản trị, HR, trưởng nhóm hoặc trưởng nhóm onsite (mục "Chấm công"). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean entersAttendance;

    /** Chỉ có ở {@code GET /api/account}: Quản trị, HR, BGĐ, Kế toán hoặc trưởng phòng (mục "Bảng công tháng"). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean viewsAttendanceBoard;

    /** Chỉ có ở {@code GET /api/account}: hồ sơ gắn tài khoản là NHAN_VIEN (mục "Bảng công của tôi"). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean ownsAttendance;

    public AdminUserDTO() {
        // Empty constructor needed for Jackson.
    }

    public AdminUserDTO(User user) {
        this.id = user.getId();
        this.login = user.getLogin();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.email = user.getEmail();
        this.activated = user.isActivated();
        this.imageUrl = user.getImageUrl();
        this.langKey = user.getLangKey();
        this.createdBy = user.getCreatedBy();
        this.createdDate = user.getCreatedDate();
        this.lastModifiedBy = user.getLastModifiedBy();
        this.lastModifiedDate = user.getLastModifiedDate();
        this.authorities = user.getAuthorities().stream().map(Authority::getName).collect(Collectors.toSet());
        this.mustChangePassword = user.isMustChangePassword();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public Boolean getMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(Boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public Boolean getHasEmployeeProfile() {
        return hasEmployeeProfile;
    }

    public void setHasEmployeeProfile(Boolean hasEmployeeProfile) {
        this.hasEmployeeProfile = hasEmployeeProfile;
    }

    public Boolean getLeaveApplicable() {
        return leaveApplicable;
    }

    public void setLeaveApplicable(Boolean leaveApplicable) {
        this.leaveApplicable = leaveApplicable;
    }

    public Boolean getIsApprover() {
        return isApprover;
    }

    public void setIsApprover(Boolean isApprover) {
        this.isApprover = isApprover;
    }

    public Boolean getSeesLeaveCalendar() {
        return seesLeaveCalendar;
    }

    public void setSeesLeaveCalendar(Boolean seesLeaveCalendar) {
        this.seesLeaveCalendar = seesLeaveCalendar;
    }

    public Boolean getEntersAttendance() {
        return entersAttendance;
    }

    public void setEntersAttendance(Boolean entersAttendance) {
        this.entersAttendance = entersAttendance;
    }

    public Boolean getViewsAttendanceBoard() {
        return viewsAttendanceBoard;
    }

    public void setViewsAttendanceBoard(Boolean viewsAttendanceBoard) {
        this.viewsAttendanceBoard = viewsAttendanceBoard;
    }

    public Boolean getOwnsAttendance() {
        return ownsAttendance;
    }

    public void setOwnsAttendance(Boolean ownsAttendance) {
        this.ownsAttendance = ownsAttendance;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public String getLangKey() {
        return langKey;
    }

    public void setLangKey(String langKey) {
        this.langKey = langKey;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(Instant lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public Set<String> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(Set<String> authorities) {
        this.authorities = authorities;
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AdminUserDTO{" +
            "login='" + login + '\'' +
            ", firstName='" + firstName + '\'' +
            ", lastName='" + lastName + '\'' +
            ", email='" + email + '\'' +
            ", imageUrl='" + imageUrl + '\'' +
            ", activated=" + activated +
            ", langKey='" + langKey + '\'' +
            ", createdBy=" + createdBy +
            ", createdDate=" + createdDate +
            ", lastModifiedBy='" + lastModifiedBy + '\'' +
            ", lastModifiedDate=" + lastModifiedDate +
            ", authorities=" + authorities +
            "}";
    }
}
