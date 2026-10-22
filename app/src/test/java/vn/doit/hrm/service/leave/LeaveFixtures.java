package vn.doit.hrm.service.leave;

import java.time.LocalDate;
import org.apache.commons.lang3.RandomStringUtils;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.domain.enumeration.EmploymentStatus;
import vn.doit.hrm.domain.enumeration.PersonType;
import vn.doit.hrm.repository.DepartmentRepository;
import vn.doit.hrm.repository.EmployeeRepository;
import vn.doit.hrm.repository.UserRepository;

/** Dựng hồ sơ, tài khoản và phòng ban giả cho các test nghỉ phép. */
public final class LeaveFixtures {

    private LeaveFixtures() {}

    public static Employee employee(EmployeeRepository repo, String code, PersonType type, LocalDate joinDate) {
        return repo.saveAndFlush(
            new Employee()
                .code(code)
                .personType(type)
                .fullName("Người thử " + code)
                .searchText(code.toLowerCase())
                .dependants(0)
                .status(EmploymentStatus.DANG_LAM_VIEC)
                .joinDate(joinDate)
        );
    }

    public static User user(UserRepository repo, String login) {
        User u = new User();
        u.setLogin(login);
        u.setActivated(true);
        u.setLangKey("vi");
        u.setPassword(RandomStringUtils.insecure().nextAlphanumeric(60));
        return repo.saveAndFlush(u);
    }

    public static Department department(DepartmentRepository repo, String name) {
        Department d = new Department();
        d.setName(name);
        d.setActive(true);
        return repo.saveAndFlush(d);
    }
}
