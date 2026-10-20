package vn.doit.hrm.service.mapper;

import org.mapstruct.*;
import vn.doit.hrm.domain.Department;
import vn.doit.hrm.domain.Employee;
import vn.doit.hrm.domain.User;
import vn.doit.hrm.security.LoginMask;
import vn.doit.hrm.service.dto.DepartmentDTO;
import vn.doit.hrm.service.dto.EmployeeDTO;
import vn.doit.hrm.service.dto.UserDTO;

/**
 * Mapper for the entity {@link Employee} and its DTO {@link EmployeeDTO}.
 */
@Mapper(componentModel = "spring")
public interface EmployeeMapper extends EntityMapper<EmployeeDTO, Employee> {
    @Mapping(target = "department", source = "department", qualifiedByName = "departmentName")
    @Mapping(target = "manager", source = "manager", qualifiedByName = "employeeFullName")
    @Mapping(target = "user", source = "user", qualifiedByName = "userLogin")
    @Mapping(target = "userLogin", source = "user.login")
    // Trường nhạy cảm không bao giờ có trong DTO trả về; đọc qua EmployeeSensitiveDTO.
    @Mapping(target = "idNumber", ignore = true)
    @Mapping(target = "idIssueDate", ignore = true)
    @Mapping(target = "idIssuePlace", ignore = true)
    @Mapping(target = "taxCode", ignore = true)
    @Mapping(target = "bankAccount", ignore = true)
    @Mapping(target = "bankName", ignore = true)
    @Mapping(target = "socialInsuranceNo", ignore = true)
    @Mapping(target = "currentSalary", ignore = true)
    @Mapping(target = "currentContractType", ignore = true)
    @Mapping(target = "currentSiteName", ignore = true)
    @Mapping(target = "scheduledTermination", ignore = true)
    @Mapping(target = "terminationUndoable", ignore = true)
    @Mapping(target = "scheduledTerminationOverdue", ignore = true)
    @Mapping(target = "scheduledTerminationError", ignore = true)
    @Mapping(target = "terminationNotice", ignore = true)
    EmployeeDTO toDto(Employee s);

    @Named("departmentName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    DepartmentDTO toDtoDepartmentName(Department department);

    @Named("employeeFullName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "fullName", source = "fullName")
    EmployeeDTO toDtoEmployeeFullName(Employee employee);

    /**
     * Tên đăng nhập của tài khoản gắn hồ sơ là số CCCD: vai trò không được xem CCCD (BGĐ, Kế toán) thấy dạng che, trừ chính chủ.
     */
    @AfterMapping
    default void maskLogin(@MappingTarget EmployeeDTO dto) {
        if (dto.getUserLogin() != null) {
            dto.setUserLogin(LoginMask.forViewer(dto.getUserLogin()));
        }
        if (dto.getUser() != null && dto.getUser().getLogin() != null) {
            dto.getUser().setLogin(LoginMask.forViewer(dto.getUser().getLogin()));
        }
    }

    @Named("userLogin")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "login", source = "login")
    UserDTO toDtoUserLogin(User user);
}
