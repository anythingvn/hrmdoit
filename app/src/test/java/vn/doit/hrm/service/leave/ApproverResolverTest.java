package vn.doit.hrm.service.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.doit.hrm.domain.Authority;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.LeaveRequest;
import vn.doit.hrm.domain.LeaveSetting;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.ApproverKind;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.UserRepository;
import vn.doit.hrm.security.AuthoritiesConstants;
import vn.doit.hrm.service.leave.ApproverResolver.Approver;

/** Người duyệt theo cấp (Global Constraints, Review Focus 3). */
class ApproverResolverTest {

    private DepartmentRepository departments;
    private UserRepository users;
    private ApproverResolver resolver;

    private long nextId = 1;
    private Department dept;
    private Employee head;
    private Employee lead;
    private Employee staff;
    private User director;

    @BeforeEach
    void init() {
        LeaveSettingService settings = mock(LeaveSettingService.class);
        LeaveSetting s = new LeaveSetting();
        s.setDirectorThresholdDays(3);
        s.setAdvanceDays(1);
        when(settings.current()).thenReturn(s);
        departments = mock(DepartmentRepository.class);
        users = mock(UserRepository.class);
        resolver = new ApproverResolver(settings, departments, users);

        dept = new Department();
        dept.setId(100L);
        dept.setName("Kỹ thuật");
        dept.setActive(true);
        head = person("truongphong");
        head.setDepartment(dept);
        dept.setHead(head);
        lead = person("truongnhom");
        lead.setDepartment(dept);
        staff = person("nhanvien");
        staff.setDepartment(dept);
        staff.setManager(lead);
        director = user("giamdoc", AuthoritiesConstants.BGD);
        when(departments.existsByHeadIdAndActiveTrue(anyLong())).thenAnswer(inv -> head.getId().equals(inv.getArgument(0)));
        when(users.findActiveWithAnyAuthority(any())).thenReturn(List.of(director));
    }

    private User user(String login, String... roles) {
        User u = new User();
        u.setId(nextId++);
        u.setLogin(login);
        u.setActivated(true);
        java.util.HashSet<Authority> auths = new java.util.HashSet<>();
        for (String r : roles) {
            Authority a = new Authority();
            a.setName(r);
            auths.add(a);
        }
        u.setAuthorities(auths);
        return u;
    }

    private Employee person(String login, String... roles) {
        Employee e = new Employee().code(login).fullName(login).personType(PersonType.NHAN_VIEN).status(EmploymentStatus.DANG_LAM_VIEC);
        e.setId(nextId++);
        e.setUser(user(login, roles));
        return e;
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void teamLeadUpToThreeDaysDirectorAbove() {
        assertThat(resolver.resolve(staff, d("3"), false)).isEqualTo(new Approver(ApproverKind.TRUONG_NHOM, lead.getId()));
        assertThat(resolver.resolve(staff, d("3.5"), false)).isEqualTo(new Approver(ApproverKind.BGD, null));
    }

    @Test
    void overtimeAlwaysTeamLead() {
        assertThat(resolver.resolve(staff, BigDecimal.ZERO, true)).isEqualTo(new Approver(ApproverKind.TRUONG_NHOM, lead.getId()));
    }

    @Test
    void noTeamLeadGoesToDepartmentHead() {
        staff.setManager(null);
        assertThat(resolver.resolve(staff, d("1"), false)).isEqualTo(new Approver(ApproverKind.TRUONG_PHONG, head.getId()));
    }

    @Test
    void departmentHeadAskingGoesToDirector() {
        head.setManager(lead);
        assertThat(resolver.resolve(head, d("1"), false)).isEqualTo(new Approver(ApproverKind.BGD, null));
    }

    @Test
    void managerIsSelfGoesToDepartmentHead() {
        staff.setManager(staff);
        assertThat(resolver.resolve(staff, d("1"), false)).isEqualTo(new Approver(ApproverKind.TRUONG_PHONG, head.getId()));
    }

    @Test
    void managerSharingTheSameAccountIsTreatedAsSelf() {
        Employee twin = person("twin");
        twin.setUser(staff.getUser());
        staff.setManager(twin);
        assertThat(resolver.resolve(staff, d("1"), false).kind()).isEqualTo(ApproverKind.TRUONG_PHONG);
    }

    @Test
    void unavailableApproversAreSkipped() {
        lead.setStatus(EmploymentStatus.DA_NGHI);
        assertThat(resolver.resolve(staff, d("1"), false)).isEqualTo(new Approver(ApproverKind.TRUONG_PHONG, head.getId()));
        head.getUser().setActivated(false);
        assertThat(resolver.resolve(staff, d("1"), false)).isEqualTo(new Approver(ApproverKind.BGD, null));
        lead.setStatus(EmploymentStatus.DANG_LAM_VIEC);
        lead.setUser(null);
        assertThat(resolver.resolve(staff, d("1"), false)).isEqualTo(new Approver(ApproverKind.BGD, null));
    }

    @Test
    void directorAskingGoesToAdmin() {
        Employee bgd = person("bgd-xin", AuthoritiesConstants.BGD);
        bgd.setManager(lead);
        assertThat(resolver.resolve(bgd, d("1"), false)).isEqualTo(new Approver(ApproverKind.QUAN_TRI, null));
    }

    @Test
    void noOtherActiveDirectorGoesToAdmin() {
        staff.setManager(null);
        dept.setHead(null);
        when(users.findActiveWithAnyAuthority(any())).thenReturn(List.of());
        assertThat(resolver.resolve(staff, d("1"), false)).isEqualTo(new Approver(ApproverKind.QUAN_TRI, null));
    }

    @Test
    void escalateMovesToNextLevel() {
        assertThat(resolver.escalate(staff, ApproverKind.TRUONG_NHOM)).isEqualTo(new Approver(ApproverKind.TRUONG_PHONG, head.getId()));
        assertThat(resolver.escalate(staff, ApproverKind.TRUONG_PHONG)).isEqualTo(new Approver(ApproverKind.BGD, null));
        assertThat(resolver.escalate(staff, ApproverKind.QUAN_TRI)).isEqualTo(new Approver(ApproverKind.QUAN_TRI, null));
    }

    @Test
    void canDecideNeverOwnRequest() {
        LeaveRequest r = new LeaveRequest();
        r.setEmployee(staff);
        r.setApproverKind(ApproverKind.TRUONG_NHOM);
        r.setApproverEmployee(lead);
        assertThat(resolver.canDecide("truongnhom", Set.of(AuthoritiesConstants.NHAN_VIEN), r)).isTrue();
        assertThat(resolver.canDecide("truongphong", Set.of(AuthoritiesConstants.NHAN_VIEN), r)).isFalse();
        assertThat(resolver.canDecide("giamdoc", Set.of(AuthoritiesConstants.BGD), r)).isFalse();
        assertThat(resolver.canDecide("hr", Set.of(AuthoritiesConstants.HR), r)).isTrue();
        // Người xin là HR / Quản trị cũng không tự duyệt.
        assertThat(resolver.canDecide("nhanvien", Set.of(AuthoritiesConstants.HR, AuthoritiesConstants.ADMIN), r)).isFalse();

        r.setApproverKind(ApproverKind.BGD);
        r.setApproverEmployee(null);
        assertThat(resolver.canDecide("giamdoc", Set.of(AuthoritiesConstants.BGD), r)).isTrue();
        assertThat(resolver.canDecide("truongnhom", Set.of(AuthoritiesConstants.NHAN_VIEN), r)).isFalse();

        r.setApproverKind(ApproverKind.QUAN_TRI);
        assertThat(resolver.canDecide("giamdoc", Set.of(AuthoritiesConstants.BGD), r)).isFalse();
        assertThat(resolver.canDecide("qt", Set.of(AuthoritiesConstants.ADMIN), r)).isTrue();
        assertThat(resolver.canDecide(staff.getUser(), r)).isFalse();
    }
}
